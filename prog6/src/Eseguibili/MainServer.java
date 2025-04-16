package Eseguibili;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

import OrderBook.*;

public class MainServer{
    //socket e stream
    public static final String configFile = "../Configurazione/server.properties";
    public static int TCPport;
    public static int UDPport;
    public static String hostname;
    public static int maxDelay;
    public static ServerSocket serverSocket;
    
    // Clienti
    public static ConcurrentHashMap <String, Tupla> userMap = new ConcurrentHashMap<>();
    private static final ConcurrentSkipListMap<String, SockMapValue> socketMap = new ConcurrentSkipListMap<>();

    //OrderBook
    public static ConcurrentSkipListMap <Integer, BookValue> askMap = new ConcurrentSkipListMap<>(); // le chiavi sono in ordine crescente: dal più basso al più alto
    public static ConcurrentSkipListMap <Integer, BookValue> bidMap = new ConcurrentSkipListMap<>(Collections.reverseOrder()); // le chiavi sono in ordine decrescente: dal più alto al più basso
    public static ConcurrentLinkedQueue<StopValue> stopOrders;
    public static OrderBook orderBook = new OrderBook(askMap, 0, new ConcurrentLinkedQueue<StopValue>(), bidMap);


    
    
    //thread
    public static final ExecutorService pool = Executors.newCachedThreadPool();
    
    //flags
    //private static volatile boolean running = true;


    public static void main(String[] args) throws Exception {
        readConfig(); //leggo le variabili dal file di configurazione

        try (ServerSocket serverSocket = new ServerSocket(TCPport)){
            // Associo un handler per gestire la terminazione con ctrl-C e in caso di timeout
            //Runtime.getRuntime().addShutdownHook(new TerminationHandler(maxDelay,pool, serverSocket));

            // Aggiungi un hook di shutdown per gestire Control-C
            /*Runtime.getRuntime().addShutdownHook(new Thread() {
                public void run() {
                    System.out.println("\nShutdown in corso...");
                    shutdown();
                }
            });*/

            //Carico la UserMap in memoria: così se il server viene chiudo la userMap persiste
            loadUserMap();

            //carico l'orderBook in memoria
            loadOrderBook();


            System.out.printf("[MAIN SERVER]: In ascolto sulla porta %d\n",TCPport);
            int i = 1;
            while(true){ //running
                //associo al pool i worker
                Socket receivedSocket = serverSocket.accept();
                
                //creo il pool
                pool.execute(new Worker(receivedSocket,userMap,orderBook,socketMap,UDPport+i));
                i++;
            }
        }catch (Exception e){
            System.err.printf("[MAINSERVER]: %s\n",e.getMessage());
        }
    }

    // Metodo per caricare i dati della userMap in memoria alla struttura dati passata ai server 
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
        catch (Exception e) {
            System.out.println(e.getClass());
            System.exit(0);
        }
    }

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
                    
                    // Consumiamo tutti i valori dell'array senza aggiungerli alla coda
                    while(reader.hasNext()) {
                        reader.skipValue();  // Salta ogni valore nell'array stopOrders
                    }
                    reader.endArray();


                }
            }
            reader.endObject();
        } catch (EOFException e){
            System.out.println("File orderBook vuoto");
            return;

        } catch (Exception e){
            System.out.println("classe: " + e.getClass() + " cause: " + e.getCause()+ " message: " + e.getMessage());
            System.exit(0);
        }
    }

    private static void shutdown(){
        //chiudo la ServerSocket in modo che non possono essere più accettate nuove richieste
        try{
            serverSocket.close();
        } catch (IOException e){
            System.err.printf("[SERVER] Errore: %s\n", e.getMessage());
        }

        //faccio terminare il pool di thread
        pool.shutdown();
        try{
            if(!pool.awaitTermination(maxDelay,TimeUnit.MILLISECONDS));
            pool.shutdownNow();
        } catch (InterruptedException e) {pool.shutdownNow();}

        System.out.println("[SERVER] Terminato.");
    }
        
    /*private static void shutdown1(){
        synchronized (socketList){
                for(Socket client : socketList){
                    try{
                        PrintWriter out = new PrintWriter(client.getOutputStream(), true);
                        out.println("Il server sta per chiudersi. Bye!");
                        client.close();
                    } catch (IOException e) {
                        System.out.printf("Errore nella chiusura del client.\n%s\n%s" + e.getMessage(), e.getCause());
                    }
                }
            }

            pool.shutdownNow(); // Ferma subito i worker
            System.out.println("Server terminato.");
        }*/

    /* 
    // Metodo per lo shutdown ordinato del server: Claude
    private static void shutdown2() {
        running = false;
        
        System.out.println("Salvataggio dati in corso...");
        
        // Notifica a tutti i client connessi
        notifyAllClients();
        
        // Chiudi il thread pool
        System.out.println("Chiusura thread pool...");
        pool.shutdown();
        try {
            // Attendo fino a 5 secondi che i thread terminino
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                // Forza chiusura se non terminano normalmente
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        // Chiudi il server socket
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                System.out.println("Server socket chiuso.");
            }
        } catch (IOException e) {
            System.err.println("Errore durante la chiusura del server socket: " + e.getMessage());
        }
        
        System.out.println("Server terminato correttamente.");
    }

    // Metodo per notificare tutti i client della chiusura
    private static void notifyAllClients() {
        System.out.println("Notifica di chiusura a tutti i client connessi...");
        // Se mantieni un elenco di gestori client o socket client attivi
        // puoi iterare su di essi e inviare un messaggio di shutdown a ciascuno
        
        // Esempio (supponendo che Tupla contenga una referenza al ClientHandler):
        for (Map.Entry<String, Tupla> entry : userMap.entrySet()) {
            try {
                Tupla userData = entry.getValue();
                // Esempio (supponendo che Tupla abbia un metodo per ottenere l'output stream o il client handler):
                // userData.getClientHandler().sendServerShutdownNotification();
                // oppure
                // PrintWriter out = userData.getOutputStream();
                // out.println("SERVER_SHUTDOWN");
                // out.flush();
            } catch (Exception e) {
                System.err.println("Errore durante la notifica al client " + entry.getKey() + ": " + e.getMessage());
            }
        }
    }*/

    //metodo che legge il file di configurazione del server
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
