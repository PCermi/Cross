package Eseguibili.Server;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;

import Eseguibili.Main.MainServer;
import GsonClasses.Commands.*;
import GsonClasses.Responses.*;
import OrderBook.*;

// Thread che si occupa di gestire la connessione di un client e le relative richieste.

public class Worker implements Runnable{
    private Socket clientSocket;
    private ConcurrentSkipListMap<String,SockMapValue> socketMap; // Mappa contenente username, porta e indirizzo
    public String hostname;
    public int UDPport; // Porta UDP del client
    public int clientPort; // Porta TCP del client
    public InetAddress clientAddress; // Indirizzo IP del client

    public ConcurrentHashMap <String,Tupla> userMap; // Mappa contenente username, password e stato di log
    public OrderBook orderBook;
    public TimeoutHandler handler;

    private String username = null;
    private String password = null;
    private String onlineUser = null;
    private String type;
    private int size;
    private int price;

    // Oggetti JSON per la comunicazione
    public GsonResponse response = new GsonResponse();
    public GsonOrderBook responseOB = new GsonOrderBook();
    public GsonResponseOrder responseOrder = new GsonResponseOrder();
    private static Gson gson = new Gson();

    // Flags
    public static AtomicBoolean running = new AtomicBoolean(true); //variabile usata per interrompere il while del worker in seguito alla chiusura del server
    

    public Worker(Socket socket, ConcurrentHashMap <String,Tupla> userMap, OrderBook orderBook, ConcurrentSkipListMap<String,SockMapValue> socketMap, int UDPport){
        this.clientSocket = socket;
        this.userMap = userMap;
        this.orderBook = orderBook;
        this.socketMap = socketMap;
        this.UDPport = UDPport;
        updateJsonOrderBook(orderBook);
    }

    // Classe usata per condividere dati tra il worker e il TimeoutHandler 
    public class SharedState{
        public AtomicBoolean activeUser = new AtomicBoolean(true);      // Dice se un utente è attivo o meno
        public AtomicBoolean runningHandler = new AtomicBoolean(true);  // Usata per interrompere l'esecuzione dell'Handler
        public volatile long lastActivity = System.currentTimeMillis();
        public volatile ConcurrentLinkedQueue<StopValue> stopOrders = new ConcurrentLinkedQueue<>();
    }

    public void run(){
        System.out.printf("[WORKER %s] serving a client\n",Thread.currentThread().getName());

        SharedState sharedState = new SharedState();

        // Creazione del thread che gestisce il timeout
        handler = new TimeoutHandler(sharedState);
        Thread timeout = new Thread(handler);
        timeout.start();

        // Sincronizzazione degli stopOrder dell'handler con quelli dell'orderBook
        handler.syncWithOrderBook(orderBook);

        // Apertura della comunicazione UDP per ricevere porta e IP del cliente loggato
        try(DatagramSocket UDPsocket = new DatagramSocket(UDPport)){

            // Timeout sulla socket per interrompere la readLine in caso di inattività del client
            clientSocket.setSoTimeout(5000);
        
            // Definizione dei canali di input e output TCP
            try(BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));PrintWriter out = new PrintWriter(clientSocket.getOutputStream(),true)){
                
                // Viene mandato un messaggio TCP al client, in modo che esso possa estrarre la porta e l'address del suo worker: si riutilizza la struttura json GsonResponse
                response.setResponse("UDP",UDPport,"");
                response.sendMessage(gson,out);

                while(sharedState.activeUser.get() && running.get()){
                    // Try-catch per catturare l'eccezione del timeout del socket
                    try{
                        // Attesa messaggio dal client
                        String line = in.readLine();

                        // Si setta il timestamp per il controllo del timeout
                        long time = System.currentTimeMillis();
                        handler.setTimestamp(time);
                        // Sincronizzazione degli stopOrder dell'handler con quelli dell'orderBook   
                        handler.syncWithOrderBook(orderBook);           

                        // Lettura dell'oggetto e parsing ad un JsonObject
                        JsonObject obj = JsonParser.parseString(line).getAsJsonObject();
                        
                        // Estrazione dell'operazione
                        String operation = obj.get("operation").getAsString();
        
                        JsonObject valuesObj;

                        // Creazione del tipo di valore appropriato in base all'operazione
                        switch(operation){
                            case "register":
                                // Per la registrazione i valori sono di tipo GsonUser
                                valuesObj = obj.getAsJsonObject("values");
                                GsonUser valuesR = new Gson().fromJson(valuesObj, GsonUser.class);

                                // Estrazione dei valori da values
                                username = valuesR.getUsername();
                                password = valuesR.getPassword();
                                try{
                                    if(onlineUser != null){
                                        // Utente è loggato
                                        response.setResponse("register",103," you can't register while logged in! You are logged with username " + onlineUser);
                                        response.sendMessage(gson,out);
                                    } else if(!isValid(password)){
                                        // Password invalida
                                        response.setResponse("register",101,"invalid password");
                                        response.sendMessage(gson,out);
                                    } else if((userMap.putIfAbsent(username,new Tupla(password,false))) == null){

                                        // Modifica del file userMap.json
                                        updateJsonUsermap(userMap);
                                        
                                        // Comunicazione al client: registrazione completata
                                        response.setResponse("register",100,"OK");
                                        response.sendMessage(gson,out);
                                        
                                    } else{
                                        // Username già esistente
                                        response.setResponse("register",102,"username not available");
                                        response.sendMessage(gson,out);
                                    }
                                }catch (Exception e){
                                    response.setResponse("register",103,e.getMessage());
                                    response.sendMessage(gson,out);
                                }
                                
                                username = null;
                                
                            break;

                            case "updateCredentials":
                                // Per updateCredentials i valori sono di tipo GsonCredentials
                                valuesObj = obj.getAsJsonObject("values");
                                GsonCredentials valuesC = new Gson().fromJson(valuesObj, GsonCredentials.class);

                                // Estrazione dei valori da values
                                username = valuesC.getUsername();
                                String oldPassword = valuesC.getOldPassword();
                                String newPassword = valuesC.getNewPassword();
                                try{
                                    if(onlineUser != null){
                                        // L'utente è loggato
                                        response.setResponse("updateCredentials",104,"Can't update: user currently logged in");
                                        response.sendMessage(gson,out);

                                    } else if(userMap.containsKey(username)){
                                        // L'username esiste
                                        if((userMap.get(username)).getPassword().equals(oldPassword)){
                                            // Password vecchia corretta
                                            if(oldPassword.equals(newPassword)){
                                                // La nuova e la vecchia password sono uguali
                                                response.setResponse("updateCredentials",103,"Can't update: new password equal to old one");
                                                response.sendMessage(gson,out);
                                            } else if(isValid(newPassword)){
                                                // Nuova password è valida

                                                // Modifica della userMap
                                                userMap.replace(username, new Tupla(newPassword, false));
                                            
                                                // Modifica del file userMap.json
                                                updateJsonUsermap(userMap);

                                                // Comunicazione al client
                                                response.setResponse("updateCredentials",100,"OK");
                                                response.sendMessage(gson,out);
                                            } else{
                                                response.setResponse("updateCredentials",101,"Can't update: invalid new password");
                                                response.sendMessage(gson,out);
                                            }

                                        } else {
                                            response.setResponse("updateCredentials",102,"Can't update: incorrect old password");
                                            response.sendMessage(gson,out);
                                        }
                                    } else {
                                        response.setResponse("updateCredentials",102,"Can't update: username not found");
                                        response.sendMessage(gson,out);
                                    }
                                } catch (Exception e){
                                    response.setResponse("updateCredentials",105,e.getMessage());
                                    response.sendMessage(gson,out);
                                }
                            break;                     

                            case "login":
                                // Per login i valori sono di tipo GsonUser
                                valuesObj = obj.getAsJsonObject("values");
                                GsonUser valuesLI = new Gson().fromJson(valuesObj, GsonUser.class);

                                // Estrazione dei valori da values
                                username = valuesLI.getUsername();
                                password = valuesLI.getPassword();
                                
                                // Procedura di login
                                try{
                                    if(userMap.containsKey(username)){
                                        // L'username esiste
                                        if((userMap.get(username)).getPassword().equals(password)){
                                            // Password corretta
                                            if((userMap.get(username)).getLogged()){
                                                // L'utente è già loggato
                                                response.setResponse("login",102,"User already logged in");
                                                response.sendMessage(gson,out);
                                            } else{
                                                // L'utente non è loggato
                                                if(onlineUser == null){
                                                    // Il client non ha già fatto il login con un altro account

                                                    // Memorizzazione dell'utente loggato
                                                    onlineUser = username;

                                                    // Passaggio dell'username al thread Handler del timeout
                                                    handler.setUsername(onlineUser);

                                                    // Modifica della userMap
                                                    userMap.replace(username, new Tupla(password, true));

                                                    // Modifica del file userMap.json
                                                    updateJsonUsermap(userMap);

                                                    // Comunicazione al client
                                                    response.setResponse("login",100,"OK");
                                                    response.sendMessage(gson,out);

                                                    // Si attende il pacchetto UDP dal client per estrarre porta e indirizzo
                                                    byte[] buffer = new byte[1];
                                                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                                                    UDPsocket.receive(packet);

                                                    // Estrazione indirizzo e porta del cliente
                                                    clientPort = packet.getPort();
                                                    clientAddress = packet.getAddress();

                                                    // Aggiunta di indirizzo e porta alla socketMap
                                                    SockMapValue newValue = new SockMapValue(clientPort, clientAddress);
                                                    if(socketMap.containsKey(onlineUser))
                                                        socketMap.replace(onlineUser, newValue);
                                                    else
                                                        socketMap.put(onlineUser,newValue);

                                                } else {
                                                    response.setResponse("login",103,"You are already logged in with another account with username " + onlineUser);
                                                    response.sendMessage(gson,out);
                                                }
                                            }
                                        } else{ // Password errata
                                            response.setResponse("login",101,"Incorrect password");
                                            response.sendMessage(gson,out);
                                        }
                                    } else{ // Username non trovato
                                        response.setResponse("login",101,"Username not found");
                                        response.sendMessage(gson,out);
                                    }
                                }catch (Exception e){
                                    response.setResponse("login",103,e.getMessage());
                                    response.sendMessage(gson,out);
                                }
                            break;

                            case "logout":
                                // Per logout values non avrà nessun valore
                                valuesObj = obj.getAsJsonObject("values");
                                
                                try{
                                    if(onlineUser == null){
                                        System.out.println("User not logged in has requested logout");
                                        response.setResponse("logout",101,"Closing comunication...Bye!");
                                        response.sendMessage(gson,out);
                                    } else {
                                        // L'utente è loggato
                                        System.out.println(onlineUser + " has requested logout");

                                        // Modifica della userMap
                                        userMap.replace(onlineUser, new Tupla(password, false));

                                        // Modifica del file userMap.json
                                        updateJsonUsermap(userMap);

                                        // Comunicazione al client
                                        response.setResponse("logout",100,"OK");
                                        response.sendMessage(gson,out);
                                    } 
                                    // Terminazione del thread handler del timeout
                                    sharedState.runningHandler.set(false);
                                    timeout.join(); // Si attende la terminazione del TimeoutHandler

                                    // Si rimuove il worker dalla lista di MainServer
                                    MainServer.workerList.remove(this);

                                    // Chiusura comunicazione
                                    clientSocket.close();
                                    return;

                                } catch (Exception e){
                                    response.setResponse("logout",103,e.getMessage());
                                    response.sendMessage(gson,out);
                                }
                            break;

                            case "insertLimitOrder":
                                System.out.println(onlineUser + " has placed a Limit Order");
                                try{
                                    // Per limitOrder i valori sono di tipo GsonLimitStopOrder
                                    valuesObj = obj.getAsJsonObject("values");
                                    GsonLimitStopOrder valuesL = new Gson().fromJson(valuesObj, GsonLimitStopOrder.class);

                                    // Estrazione dei valori da values
                                    type = valuesL.getType();
                                    size = valuesL.getSize();
                                    price = valuesL.getPrice();

                                    // L'utente è già loggato
                                    int orderID;
                                    if(type.equals("ask")){
                                        // ORDINE DI ASK: VENDITA
                                        orderID = orderBook.tryAskOrder(size,price,onlineUser,socketMap);
                                    } else{
                                        // ORDINE DI BID: ACQUISTO
                                        orderID = orderBook.tryBidOrder(size, price, onlineUser, socketMap);
                                    }
                                    // Controllo della lista degli StopOrder
                                    orderBook.checkStopOrders(socketMap);

                                    // Aggiornamento della lista degli stopOrder del TimeoutHandler
                                    handler.updateStopOrders(orderBook.stopOrders);

                                    if(!orderBook.stopOrders.isEmpty())
                                        System.out.println("stopOrders dopo il check: " + orderBook.stopOrders.toString());

                                    // Aggiornamento del file orderBook.json
                                    updateJsonOrderBook(orderBook);

                                    // Incio del messaggio al client con l'orderID
                                    responseOrder.setResponseOrder(orderID);
                                    responseOrder.sendMessage(gson,out);
                                } catch (Exception e){
                                    System.out.println("[WORKER] Error in LimitOrder: " + e.getMessage());

                                    // Invio del messaggio al client con il codice di errore
                                    responseOrder.setResponseOrder(-1);
                                    responseOrder.sendMessage(gson,out);
                                }

                                System.out.println("\n\nORDER BOOK: " + orderBook.toString()+ "\n\n");
                            break;

                            case "insertMarketOrder":
                                System.out.println(onlineUser + " has placed a Market Order");
                                
                                // Per marketOrder i valori sono di tipo GsonMarketOrder
                                valuesObj = obj.getAsJsonObject("values");
                                GsonMarketOrder valuesM = new Gson().fromJson(valuesObj, GsonMarketOrder.class);

                                // Estrazione dei valori da values
                                type = valuesM.getType();
                                size = valuesM.getSize();

                                // Esecuzione del market order
                                int res = orderBook.tryMarketOrder(type,size,onlineUser,"market",socketMap);

                                // Controllo della lista degli StopOrder
                                orderBook.checkStopOrders(socketMap);

                                // Aggiornamento della lista degli stopOrder del TimeoutHandler
                                handler.updateStopOrders(orderBook.stopOrders);

                                // Aggiornamento del file orderBook.json
                                updateJsonOrderBook(orderBook);

                                // Invio del messaggio al client con l'orderID oppure il codice di errore
                                responseOrder.setResponseOrder(res);
                                responseOrder.sendMessage(gson,out);
                            break;
                            
                            case "insertStopOrder":
                                System.out.println(onlineUser + " has placed a Stop Order");

                                // Per stopOrder i valori sono di tipo GsonLimitStopOrder
                                valuesObj = obj.getAsJsonObject("values");
                                GsonLimitStopOrder valuesS = new Gson().fromJson(valuesObj, GsonLimitStopOrder.class);

                                // Estrazione dei valori da values
                                type = valuesS.getType();
                                size = valuesS.getSize();
                                price = valuesS.getPrice();

                                // Aggiunta dell'ordine nella lista degli stop orders
                                int orderID = orderBook.updateLastOrderID();
                                orderBook.stopOrders.add(new StopValue(type,size,onlineUser,orderID,price));

                                // Si controlla se ci sono ordini da eseguire
                                orderBook.checkStopOrders(socketMap);
                                                                
                                // Aggiornamento della lista degli stopOrder del TimeoutHandler
                                handler.updateStopOrders(orderBook.stopOrders);

                                // Aggiornamento del file orderBook.json
                                updateJsonOrderBook(orderBook);

                                // Invio del messaggio al client con l'orderID oppure il codice di errore
                                responseOrder.setResponseOrder(orderID);
                                responseOrder.sendMessage(gson,out);
                            break;

                            case "cancelOrder":
                                try{
                                    // Per cancelOrder i valori sono di tipo GsonResposeOrder
                                    valuesObj = obj.getAsJsonObject("values");
                                    GsonResponseOrder valuesCO = new Gson().fromJson(valuesObj, GsonResponseOrder.class);

                                    // Estrazione dei valori da values
                                    orderID = valuesCO.getOrderID();

                                    res = orderBook.cancelOrder(orderID, onlineUser);

                                    // Controllo della lista degli StopOrder
                                    orderBook.checkStopOrders(socketMap);

                                    // Aggiornamento della lista degli stopOrder del TimeoutHandler
                                    handler.updateStopOrders(orderBook.stopOrders);

                                    // Modifica del file userBook.json
                                    updateJsonOrderBook(orderBook);

                                    String message = "";
                                    if(res==100)
                                        message = "OK";

                                    // Comunicazione al client
                                    response.setResponse("cancelOrder",res,message);
                                    response.sendMessage(gson,out);

                                } catch (Exception e){
                                    System.err.println("[WORKER] Error in cancelOrder: " + e.getMessage() + e.getCause());
                                }
                            break;

                            case "showOrderBook":
                                // Per showOrderBook values non avrà nessun valore
                                valuesObj = obj.getAsJsonObject("values");

                                // Setting dell'orderBook e invio al client
                                responseOB.setOrderBook("showOrderBook",orderBook);
                                responseOB.sendMessage(gson,out);
                            break;

                            case "showStopOrders":
                                // Per showStopOrders values non avrà nessun valore
                                valuesObj = obj.getAsJsonObject("values");
                                
                                // Setting degli stopOrders e invio al client
                                responseOB.setOrderBook("showStopOrders",orderBook);
                                responseOB.sendMessage(gson,out);
                            break;

                            case "getPriceHistory":
                                try{
                                    // Per getPriceHistory i valori sono di tipo GsonHistory
                                    valuesObj = obj.getAsJsonObject("values");
                                    GsonAskHistory valuesH = new Gson().fromJson(valuesObj, GsonAskHistory.class);
                                    
                                    // Estrazione dei valori da values
                                    String date = valuesH.getDate();
                                    System.out.println(onlineUser + " asked for the price history from " + date);

                                    String tradeInfo = readHistory(date);

                                    // Comunicazione al client
                                    response.setResponse("getPriceHistory",0,tradeInfo);
                                    response.sendMessage(gson,out);

                                } catch(Exception e){
                                    System.err.println("[WORKER] getPriceHistory: " + e.getMessage() + e.getCause());
                                }
                            break;

                            default:
                                System.out.println("[Worker] Command received not found");
                        } // Fine switch
                    } catch (SocketTimeoutException e){
                        // readLine() è scaduto, si verifica se il TimeoutHandler ha segnalato un timeout
                        if(!sharedState.activeUser.get()){
                            break;
                        }
                        // Altrimenti si continua il ciclo
                        continue;
                    }
                }
                // Procedura di terminazione del worker

                // Terminazione del thread handler
                sharedState.runningHandler.set(false);
                timeout.join(); // Si attende la terminazione del TimeoutHandler

                String closingMessage = "";
                if(!sharedState.activeUser.get()) // Inattività del client
                    closingMessage = "Closing connection due to inactivity timeout.";
                
                if(!running.get()) // Shutdown del server
                    closingMessage = "Closing connection due to server shutdown.";
                
                if(onlineUser == null){
                    System.out.println("Disconecting user not logged");
                    response.setResponse("disconnection",100,closingMessage);
                    response.sendMessage(gson,out);
                } else {
                    // L'utente è loggato
                    System.out.println("Disconnecting " + onlineUser);

                    // Modifica della userMap
                    userMap.replace(onlineUser, new Tupla(password, false));

                    // Modifica del file userMap.json
                    updateJsonUsermap(userMap);

                    // Comunicazione al client
                    response.setResponse("disconnection",100,closingMessage);
                    response.sendMessage(gson,out);
                }
                // Si rimuove il worker dalla lista di MainServer
                MainServer.workerList.remove(this);

                // Chiusura comunicazione
                clientSocket.close();
                return;

            } catch (Exception e){ // Fine try with resource TCP
                System.err.println("[WORKER] Error in try TCP: " + e.getMessage() + " - Cause: " + e.getCause());
            }
        } catch (IOException e) { // Fine try with resource UDP
            System.err.println("[Worker] Error in try UDP: " + e.getMessage());
        }
    }

    public void shutdown(){
        running.set(false);
    }

    // Metodo per sincronizzare L'orderbook
    public void updateTimeoutHandler(){
        if(this.handler != null){
            this.handler.syncWithOrderBook(orderBook);
        }
    }

    // Metodo per leggere i dati giornalieri dallo storico
    public String readHistory(String date){
        String monthPassed = date.substring(0, 2);
        String yearPassed = date.substring(2);

        StringBuilder result = new StringBuilder();

        // Definizione di una mappa per memorizzare i dati di ogni giorno
        ConcurrentSkipListMap<String,DailyParameters> daysMap = new ConcurrentSkipListMap<>();

        try(JsonReader reader = new JsonReader(new FileReader("src/JsonFile/storicoOrdini.json"))) {
            Gson gson = new Gson();

            // Formato per estrarre solo la data senza ora
            SimpleDateFormat dayFormat = new SimpleDateFormat("dd/MM/yyyy");

            // Formato per estrarre solo il mese
            SimpleDateFormat monthFormat = new SimpleDateFormat("MM");
            // Formato per estrarre solo l'anno
            SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy");
            
            // Lettura dell'oggetto JSON principale
            reader.beginObject();
            
            while(reader.hasNext()){
                String name = reader.nextName();
                
                if(name.equals("trades")){
                    // Lettura dell'array "trades"
                    reader.beginArray();
                    
                    while(reader.hasNext()){
                        // Deserializzazione di ciascun trade come un oggetto GsonTrade
                        GsonTrade trade = gson.fromJson(reader, GsonTrade.class);

                        // Creazione di un oggetto Date dal timestamp
                        Date tradeDate = new Date(trade.getTime() * 1000L);
                        
                        // Estrazione del mese
                        String month = monthFormat.format(tradeDate);
                        
                        // Estrazione dell'anno
                        String year = yearFormat.format(tradeDate);
                        
                        // Filtraggio degli ordini del mese e anno desiderato
                        if(month.equals(monthPassed) && year.equals(yearPassed)){

                            // Conversione del timestamp in formato data leggibile
                            String dayKey = dayFormat.format(tradeDate);
                            
                            // Aggiornamento dei dati giornalieri
                            if(!daysMap.containsKey(dayKey)){
                                // Primo trade del giorno
                                daysMap.put(dayKey, new DailyParameters(dayKey, trade.getPrice(), trade.getTime()));
                            } else {
                                // Aggiornamento dei dati esistenti
                                daysMap.get(dayKey).updatePrices(trade.getPrice(), trade.getTime());
                            }
                        }
                    }
                    reader.endArray();
                } else {
                    // Salta altri campi se presenti
                    reader.skipValue();
                }
            }
            reader.endObject();

            // Creazione della stringa risultato contenente i dati giornalieri
            result.append("\n=== DATI GIORNALIERI ===\n");
            for(Map.Entry<String,DailyParameters> entry : daysMap.entrySet()){
                DailyParameters param = entry.getValue();
                result.append(String.format("Date: %s, OpenPrice: %d, MaxPrice: %d, MinPrice: %d, ClosePrice: %d\n", 
                entry.getKey(), param.openPrice, param.highPrice, 
                param.lowPrice, param.closePrice));
            }
        } catch (Exception e){
            System.err.println("[WORKER] Error: " + e.getMessage() + e.getCause());
        }
        return result.toString();
    }

    // Metodo per verificare se una stringa passata è valida
    public static boolean isValid(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        return str.matches("^[a-zA-Z0-9]+$");
    }

    // Metodo per modificare il file JSON che mostra la userMap
    public static void updateJsonUsermap(ConcurrentHashMap<String,Tupla> userMap){
        try(BufferedWriter writer = new BufferedWriter(new FileWriter("src/JsonFile/userMap.json"))){
            Gson g = new GsonBuilder().setPrettyPrinting().create();
            writer.write(g.toJson(userMap));

        } catch (Exception e){
            System.err.printf("[WORKER] updateJsonUsermap %s \n",e.getMessage());
        }
    }

    // Metodo per modificare il file JSON che mostra l'orderBook
    public static void updateJsonOrderBook(OrderBook orderBook){
        try(BufferedWriter writer = new BufferedWriter(new FileWriter("src/JsonFile/orderBook.json"))){
            Gson g = new GsonBuilder().setPrettyPrinting().create();
            writer.write(g.toJson(orderBook));

            // Sincronizzazione di tutti i worker quando l'OrderBook cambia
            for(Worker worker : MainServer.workerList){
                if(worker != null && worker.handler != null){
                    worker.handler.syncWithOrderBook(orderBook);
                }
            }

        } catch (Exception e){
            System.err.println("[WORKER] updateJsonOrderBook " + e.getMessage());
        }
    }
}