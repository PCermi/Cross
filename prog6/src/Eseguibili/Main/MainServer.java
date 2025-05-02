package Eseguibili.Main;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

import Eseguibili.Server.*;
import OrderBook.*;  

/* Classe principale del server che gestisce le connessioni dei client. Si occupa di accettare connessioni TCP,
creare thread worker per ogni client e gestire lo shutdown del sistema. */

public class MainServer{
    // Socket e stream
    public static final String configFile = "../../Configurazione/server.properties";
    public static int TCPport;              // Porta TCP per le connessioni client
    public static int UDPport;              // Porta base UDP per le comunicazioni asincrone
    public static String hostname;          // Nome host del server
    public static int maxDelay;             // Ritardo massimo per timeout
    public static ServerSocket serverSocket;
    
    // Strutture dati condivise per la gestione dei client
    public static ConcurrentLinkedQueue <Worker> workerList = new ConcurrentLinkedQueue<>();
    public static ConcurrentHashMap <String, Tupla> userMap = new ConcurrentHashMap<>();
    private static final ConcurrentSkipListMap<String, SockMapValue> socketMap = new ConcurrentSkipListMap<>();

    // Strutture dati per l'OrderBook
    // askMap: ordini di vendita, ordinati in modo crescente (dal prezzo più basso al più alto)
    public static ConcurrentSkipListMap <Integer, BookValue> askMap = new ConcurrentSkipListMap<>();
    // bidMap: ordini di acquisto, ordinati in modo decrescente (dal prezzo più alto al più basso)
    public static ConcurrentSkipListMap <Integer, BookValue> bidMap = new ConcurrentSkipListMap<>(Collections.reverseOrder());
    public static ConcurrentLinkedQueue<StopValue> stopOrders; // Coda che tiene traccia degli stop orders
    public static OrderBook orderBook = new OrderBook(askMap, 0, new ConcurrentLinkedQueue<StopValue>(), bidMap);

    // ThreadPool
    public static final ExecutorService pool = Executors.newCachedThreadPool();

    public static void main(String[] args) throws Exception {
        readConfig(); // Lettura dei parametri di configurazione

        try{
            serverSocket = new ServerSocket(TCPport);

            // Viene associato un handler per gestire la terminazione con ctrl-C
            Runtime.getRuntime().addShutdownHook(new Thread(){
                public void run(){
                    System.out.println("\nShutting down...");
                    shutdown();
                }
            });

            // Lettura della userMap dalla memoria
            loadUserMap();

            // Lettura dell'orderBook dalla memoria
            loadOrderBook();

            System.out.printf("[MAIN SERVER]: listening on port %d\n",TCPport);
            int i = 1;
            while(true){
                Socket receivedSocket = serverSocket.accept();
                // Creazione del worker che si occuperà di gestire il client
                Worker worker = new Worker(receivedSocket,userMap,orderBook,socketMap,UDPport+i);
                workerList.add(worker); // Aggiunta del thread alla lista che mantiene tutti worker
                pool.execute(worker);
                i++;
            }
        }catch (SocketException e){
            // La accept() è stata interrotta: questa eccezione viene ignorata
        } catch (Exception e){
            System.err.println("[MAINSERVER] Error: " + e.getMessage());
        }
    }
    // Metodo che aggiorna tutti i gestori dei timeout per tutti i worker attivi
    public static void updateAllTimeoutHandlers(){
        for(Worker worker : workerList){
            worker.updateTimeoutHandler();
        }
    }
    
    // Metodo che carica la mappa degli utenti dal file JSON
    public static void loadUserMap(){
        try(JsonReader reader = new JsonReader(new FileReader("src/JsonFile/userMap.json"))){
            reader.beginObject();

            while(reader.hasNext()){
                String name = reader.nextName();
                Tupla cred = new Gson().fromJson(reader, Tupla.class);
                userMap.put(name,cred);
            }
            reader.endObject();
        }
        catch(EOFException e){
            System.out.println("File utenti vuoto");
            return;
        }
        catch (Exception e){
            System.out.println("Error while loading UserMap: " + e.getMessage());
            System.exit(0);
        }
    }

    // Metodo che carica l'orderBook dal file JSON
    public static void loadOrderBook(){
        try (JsonReader reader = new JsonReader(new FileReader("src/JsonFile/orderBook.json"))) {
            Gson gson = new Gson();
            reader.beginObject();
            
            while (reader.hasNext()){
                String name = reader.nextName();
                
                if (name.equals("askMap")){
                    reader.beginObject();
                    ConcurrentSkipListMap<Integer, BookValue> askMap = orderBook.askMap;
                    
                    while (reader.hasNext()){
                        int price = Integer.parseInt(reader.nextName());
                        BookValue value = gson.fromJson(reader, BookValue.class);
                        askMap.put(price, value);
                    }
                    reader.endObject();

                } else if (name.equals("bidMap")){
                    reader.beginObject();
                    ConcurrentSkipListMap<Integer, BookValue> bidMap = orderBook.bidMap;
                    
                    while (reader.hasNext()){
                        int price = Integer.parseInt(reader.nextName());
                        BookValue value = gson.fromJson(reader, BookValue.class);
                        bidMap.put(price, value);
                    }
                    reader.endObject();
                } else if (name.equals("spread")){
                    orderBook.spread = reader.nextInt();
                } else if(name.equals("lastOrderID")){
                    orderBook.lastOrderID = reader.nextInt();
                } else if(name.equals("stopOrders")){
                    reader.beginArray();
                    orderBook.stopOrders = new ConcurrentLinkedQueue<StopValue>();
                    
                    // Gli elementi della lista di stopOrders vengono saltati
                    while(reader.hasNext()) {
                        reader.skipValue();
                    }
                    reader.endArray();
                }
            }
            reader.endObject();
        } catch (EOFException e){
            System.out.println("File orderBook vuoto");
            return;

        } catch (Exception e){
            System.out.println("Error while loading OrderBook: " + e.getMessage());
            System.exit(0);
        }
    }

    // Metodo che gestisce lo spegnimento controllato del server
    private static void shutdown(){
        // Chiusura del ServerSocket per interrompere il ciclo accept()
        try{
            if(serverSocket != null && !serverSocket.isClosed()){
                serverSocket.close();
            }
        } catch (IOException e){
            System.err.println("[MAINSERVER] Error during socket closure: " + e.getMessage());
        }

        // Si notificano tutti i worker della chiusura imminente
        if(!workerList.isEmpty()){
            System.out.println("[MAINSERVER] Notifying all users...");
            for(Worker worker : workerList){
                worker.shutdown();
            }
        }

        // Si fa terminare il pool di thread
        pool.shutdown();
        try{
            if(!pool.awaitTermination(maxDelay,TimeUnit.MILLISECONDS))
            pool.shutdownNow();
        } catch (InterruptedException e) {pool.shutdownNow();}

        System.out.println("[MAINSERVER] Server terminated correctly");
    }

    // Metodo che legge il file di configurazione del server
    public static void readConfig() throws FileNotFoundException, IOException{
        InputStream input = MainServer.class.getResourceAsStream(configFile);
        Properties prop = new Properties();
        prop.load(input);
        TCPport = Integer.parseInt(prop.getProperty("TCPport"));
        maxDelay = Integer.parseInt(prop.getProperty("maxDelay"));
        UDPport = Integer.parseInt(prop.getProperty("UDPport"));
        hostname = prop.getProperty("hostname");
        input.close();
    }
}