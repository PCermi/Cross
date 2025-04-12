package Eseguibili;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
//import java.lang.*;
import com.google.gson.*;
import com.google.gson.stream.JsonReader;

import GsonClasses.*;
import OrderBook.*;



public class Worker implements Runnable{
    private Socket clientSocket;
    private ConcurrentSkipListMap<String,SockMapValue> socketMap;
    public String hostname;
    public int UDPport;
    public int clientPort;
    public InetAddress clientAddress;

    public ConcurrentHashMap <String,Tupla> userMap;
    public OrderBook orderBook;


    private String username = null;
    private String password = null;
    private String onlineUser = null;
    private String type;
    private int size;
    private int price;

    public GsonResponse response = new GsonResponse();
    public GsonResponseOrder responseOrder = new GsonResponseOrder();
    private static Gson gson = new Gson();

    //FLAGS
    public int countReg = 0; //numero di account registrati dal client
    

    public Worker(Socket socket, ConcurrentHashMap <String,Tupla> userMap, OrderBook orderBook, ConcurrentSkipListMap<String,SockMapValue> socketMap, int UDPport){
        this.clientSocket = socket;
        this.userMap = userMap;
        this.orderBook = orderBook;
        this.socketMap = socketMap;
        this.UDPport = UDPport;
        updateJsonOrderBook(orderBook);
    }

    public void run(){
        System.out.printf("[WORKER %s] - serving a client\n",Thread.currentThread().getName());

        //apro la comunicazione UDP per poter fare la receive nella sezione di login per estrarre porta e IP del cliente loggato
        try(DatagramSocket UDPsocket = new DatagramSocket(UDPport)){
            System.out.println("UDPport: " + UDPport);
        
            //definisco i canali di input e output TCP e il datagramSocket per UDP
            try( BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));PrintWriter out = new PrintWriter(clientSocket.getOutputStream(),true)){
                
                //mando un messaggio TCP al client contenente la porta e l'address del suo worker: riuso la struttura json GsonResponse
                response.setResponse("UDP",UDPport,"");
                response.sendMessage(gson,out);

                while(true){
                    //aspetto un messaggio dal client
                    String line = in.readLine();
                    //leggo l'oggetto e lo parso ad un JsonObject
                    JsonObject obj = JsonParser.parseString(line).getAsJsonObject();
                    
                    // Estraggo l'operazione
                    String operation = obj.get("operation").getAsString();

                    JsonObject valuesObj;
                    Values values;

                    // Creiamo il tipo di valori appropriato in base all'operazione
                    switch(operation){
                        case "register":
                            // Per registrazione, sappiamo che i valori sono di tipo GsonUser
                            valuesObj = obj.getAsJsonObject("values");
                            values = new Gson().fromJson(valuesObj, GsonUser.class);
                            
                            //estraggo i valori da values
                            username = valuesObj.get("username").getAsString();
                            password = valuesObj.get("password").getAsString();
                            try{
                                if(onlineUser != null){
                                    response.setResponse("register",103," you can't register while logged in! You are logged with username " + onlineUser);
                                    response.sendMessage(gson,out);
                                } else if(!isValid(password)){
                                    response.setResponse("register",101,"invalid password");
                                    response.sendMessage(gson,out);
                                } else if((userMap.putIfAbsent(username,new Tupla(password,false))) == null){
                                    //incremento il numero di account registrati da questo client
                                    countReg++;

                                    // modifico userMap.json
                                    updateJsonUsermap(userMap);
                                    
                                    // comunico al client
                                    response.setResponse("register",100,"OK");
                                    response.sendMessage(gson,out);
                                    
                                } else{
                                    // username già esistente
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
                            // Per updateCredentials, sappiamo che i valori sono di tipo GsonCredentials
                            valuesObj = obj.getAsJsonObject("values");
                            values = new Gson().fromJson(valuesObj, GsonCredentials.class);
                            //estraggo i valori da values
                            username = valuesObj.get("username").getAsString();
                            String oldPassword = valuesObj.get("old_password").getAsString();
                            String newPassword = valuesObj.get("new_password").getAsString();
                            try{
                                if(onlineUser != null){
                                    // l'utente è loggato con qualche account
                                    response.setResponse("updateCredentials",104,"can't update: user currently logged in");
                                    response.sendMessage(gson,out);

                                } else if(userMap.containsKey(username)){
                                    //l'username esiste
                                    if((userMap.get(username)).getPassword().equals(oldPassword)){
                                        // password vecchia corretta
                                        if(oldPassword.equals(newPassword)){
                                            // la nuova e la vecchia password sono uguali
                                            response.setResponse("updateCredentials",103,"new password equal to old one");
                                            response.sendMessage(gson,out);
                                        } else if(isValid(newPassword)){
                                            // nuova password è valida

                                            // modifico la userMap
                                            userMap.replace(username, new Tupla(newPassword, false));
                                        
                                            //modifico userMap.json
                                            updateJsonUsermap(userMap);

                                            // comunico al client
                                            response.setResponse("updateCredentials",100,"OK");
                                            response.sendMessage(gson,out);

                                        } else{
                                            response.setResponse("updateCredentials",101,"invalid new password");
                                            response.sendMessage(gson,out);
                                        }

                                    } else{
                                        response.setResponse("updateCredentials",102,"incorrect password");
                                        response.sendMessage(gson,out);
                                    }
                                } else {
                                    response.setResponse("updateCredentials",102,"username not found");
                                    response.sendMessage(gson,out);
                                }
                            } catch (Exception e){
                                response.setResponse("updateCredentials",105,e.getMessage());
                                response.sendMessage(gson,out);
                            }
                        break;
                    

                        case "login":
                            // Per login, sappiamo che i valori sono di tipo GsonUser
                            valuesObj = obj.getAsJsonObject("values");
                            values = new Gson().fromJson(valuesObj, GsonUser.class);

                            //estraggo i valori da values
                            username = valuesObj.get("username").getAsString();
                            password = valuesObj.get("password").getAsString();
                            
                            //loggo l'utente
                            try{
                                if(userMap.containsKey(username)){
                                    //l'username esiste
                                    if((userMap.get(username)).getPassword().equals(password)){
                                        //password corretta
                                        if((userMap.get(username)).getLogged()){
                                            //l'utente è già loggato
                                            response.setResponse("login",102,"user already logged in");
                                            response.sendMessage(gson,out);
                                        } else{
                                            //l'utente non è già loggato
                                            if(onlineUser == null){
                                                // il client non ha già fatto il login con un altro account

                                                //registro l'utente loggato
                                                onlineUser = username;

                                                // modifico la userMap
                                                userMap.replace(username, new Tupla(password, true));

                                                //modifico userMap.json
                                                updateJsonUsermap(userMap);

                                                // comunico al client
                                                response.setResponse("login",100,"OK");
                                                response.sendMessage(gson,out);
                                            } else{
                                                response.setResponse("login",103,"you are already logged in with another account with username " + onlineUser);
                                                response.sendMessage(gson,out);
                                            }
                                        }
                                    } else{ //password errata
                                        response.setResponse("login",101,"incorrect password");
                                        response.sendMessage(gson,out);
                                    }
                                } else{ //username non trovato
                                    response.setResponse("login",101,"username not found");
                                    response.sendMessage(gson,out);
                                }
                            }catch (Exception e){
                                response.setResponse("login",103,e.getMessage());
                                response.sendMessage(gson,out);
                            }

                            //Ricevo il pacchetto UDP dal client ed estraggo la porta e l'indirizzo
                            byte[] buffer = new byte[1];
                            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                            UDPsocket.receive(packet);

                            //estraggo indirizzo e porta del cliente e li aggiungo alla mappa
                            clientPort = packet.getPort();
                            clientAddress = packet.getAddress();

                            SockMapValue newValue = new SockMapValue(clientPort, clientAddress);
                            if(socketMap.containsKey(onlineUser))
                                socketMap.replace(onlineUser, newValue);
                            else
                                socketMap.put(onlineUser,newValue);

                            System.out.println("SocketMap: " + socketMap.toString());
                        break;

                        case "logout":
                            // Per logout, values non avrà nessun valore
                            valuesObj = obj.getAsJsonObject("values");
                            values = new Gson().fromJson(valuesObj, Values.class);

                            try{
                                if(onlineUser == null){
                                    response.setResponse("logout",103,"you are not logged in");
                                    response.sendMessage(gson,out);
                                } else if((userMap.get(onlineUser)).getLogged()){
                                    //l'utente è loggato

                                    //modifico la userMap
                                    userMap.replace(onlineUser, new Tupla(password, false));

                                    //modifico userMap.json
                                    updateJsonUsermap(userMap);

                                    //comunico al client
                                    response.setResponse("logout",101,"OK");
                                    response.sendMessage(gson,out);

                                    //CHIUDO COMUNICAZIONE
                                    clientSocket.close();
                                    return;
                                } else{
                                    response.setResponse("logout",103,"you are not logged in");
                                    response.sendMessage(gson,out);
                                }
                            } catch (Exception e){
                                response.setResponse("logout",103,e.getMessage());
                                response.sendMessage(gson,out);
                            }
                        break;

                        case "insertLimitOrder":
                            try{
                                // Per limitOrder sappiamo che i valori sono di tipo GsonLimitStopOrder
                                valuesObj = obj.getAsJsonObject("values");
                                values = new Gson().fromJson(valuesObj, GsonLimitStopOrder.class);

                                //estraggo i valori da values
                                type = valuesObj.get("type").getAsString();
                                size = valuesObj.get("size").getAsInt();
                                price = valuesObj.get("price").getAsInt();

                                //l'utente è già loggato
                                int orderID;
                                if(type.equals("ask")) // ORDINE DI ASK: VENDITA
                                    orderID = orderBook.newTryAskOrder(size,price,onlineUser,socketMap);
                                else // ORDINE DI BID: ACQUISTO
                                    orderID = orderBook.newTryBidOrder(size, price, onlineUser, socketMap);
                                
                                System.out.println("orderBook: " + orderBook.toString());

                                if(!orderBook.stopOrders.isEmpty())
                                    System.out.println("stopOrders prima del check: " + orderBook.stopOrders.toString());

                                //controllo la lista degli StopOrder
                                orderBook.checkStopOrders(socketMap);

                                if(!orderBook.stopOrders.isEmpty())
                                    System.out.println("stopOrders dopo il check: " + orderBook.stopOrders.toString());

                                // aggiorno orderBook.json
                                updateJsonOrderBook(orderBook);

                                //mando il messaggio al client con l'orderID
                                responseOrder.setResponseOrder(orderID);
                                responseOrder.sendMessage(gson,out);
                            } catch (Exception e){

                                System.out.println("[WORKER]: Error in LimitOrder");
                                System.err.printf("[Worker]: Error: %s\n Cause: %s",e.getMessage(),e.getCause());

                                //mando il messaggio al client con il codice di errore
                                responseOrder.setResponseOrder(-1);
                                responseOrder.sendMessage(gson,out);
                            }

                            System.out.println("\n\nORDER BOOK: " + orderBook.toString()+ "\n\n");
                        break;

                        case "insertMarketOrder":
                            // Per marketOrder sappiamo che i valori sono di tipo GsonMarketOrder
                            valuesObj = obj.getAsJsonObject("values");
                            values = new Gson().fromJson(valuesObj, GsonMarketOrder.class);

                            //estraggo i valori da values
                            type = valuesObj.get("type").getAsString();
                            size = valuesObj.get("size").getAsInt();

                            //eseguo il market order
                            int res = orderBook.tryMarketOrder(type,size,onlineUser,socketMap);

                            //controllo la lista degli StopOrder
                            orderBook.checkStopOrders(socketMap);

                            // aggiorno orderBook.json
                            updateJsonOrderBook(orderBook);

                            //mando il messaggio al client con l'orderID oppure il codice di errore
                            responseOrder.setResponseOrder(res);
                            responseOrder.sendMessage(gson,out);
                        break;
                        
                        case "insertStopOrder":
                            // Per stopOrder sappiamo che i valori sono di tipo GsonLimitStopOrder
                            valuesObj = obj.getAsJsonObject("values");
                            values = new Gson().fromJson(valuesObj, GsonLimitStopOrder.class);

                            //estraggo i valori da values
                            type = valuesObj.get("type").getAsString();
                            size = valuesObj.get("size").getAsInt();
                            price = valuesObj.get("price").getAsInt();

                            //aggiungo l'ordine nella lista degli stop orders
                            int orderID = orderBook.updateLastOrderID();
                            orderBook.stopOrders.add(new StopValue(type,size,onlineUser,orderID,price));

                            System.out.println("stopOrders prima del check: " + orderBook.stopOrders.toString());

                            //controllo se ci sono ordini da eseguire
                            orderBook.checkStopOrders(socketMap);

                            System.out.println("stopOrders dopo il check: " + orderBook.stopOrders.toString());

                            // aggiorno orderBook.json
                            updateJsonOrderBook(orderBook);

                            //mando il messaggio al client con l'orderID oppure il codice di errore
                            responseOrder.setResponseOrder(orderID);
                            responseOrder.sendMessage(gson,out);
                        break;

                        case "cancelOrder":
                            try{
                                // Per cancelOrder sappiamo che i valori sono di tipo GsonResposeOrder
                                valuesObj = obj.getAsJsonObject("values");
                                values = new Gson().fromJson(valuesObj, GsonResponseOrder.class);

                                //estraggo i valori da values
                                orderID = valuesObj.get("orderID").getAsInt();

                                res = orderBook.cancelOrder(orderID, onlineUser);

                                //controllo se ci sono ordini da eseguire
                                orderBook.checkStopOrders(socketMap);

                                //modifico userBook.json
                                updateJsonOrderBook(orderBook);

                                String message = "";
                                if(res==100)
                                    message = "OK";

                                //comunico al client
                                response.setResponse("cancelOrder",res,message);
                                response.sendMessage(gson,out);

                            } catch (Exception e){
                                System.err.printf("[WORKER]: cancelOrder: %s %s\n",e.getMessage(),e.getCause());
                            }
                        break;

                        case "getPriceHistory":
                            try{
                                // Per getPriceHistory sappiamo che i valori sono di tipo GsonHistory
                                valuesObj = obj.getAsJsonObject("values");
                                values = new Gson().fromJson(valuesObj, GsonAskHistory.class);
                                
                                //estraggo i valori da values
                                String date = valuesObj.get("month").getAsString();
                                System.out.printf("ricevuto getPriceHistory con date: %s\n",date);

                                String tradeInfo = readHistory(date);

                                //comunico al client
                                response.setResponse("getPriceHistory",0,tradeInfo);
                                response.sendMessage(gson,out);

                            } catch(Exception e){
                                System.err.printf("[WORKER]: getPriceHistory: %s %s\n",e.getMessage(),e.getCause());
                            }
                        break;
                    } //fine switch
                    //System.out.printf("\n[WORKER %s] userMap: " + userMap,Thread.currentThread().getName()); 
                }
            } catch (Exception e){ //fine try with resource TCP
                System.err.printf("[WORKER]: Errore Messaggio ricevuto: %s %s\n",e.getMessage(),e.getCause());
            }
        } catch (IOException e) { //fine try with resource UDP
            System.err.println("Errore nella ricezione dell'intero via UDP: " + e.getMessage());
        }
    }

    public String readHistory(String date){
        String monthPassed = date.substring(0, 2);

        int openingPrice;
        int closingPrice;
        int maxPrice;
        int minimumPrice;

        try(JsonReader reader = new JsonReader(new FileReader("src/JsonFile/storicoOrdini.json"))) {
            Gson gson = new Gson();

            // Creo un formato per la data
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
            // Formato per estrarre solo il mese
            SimpleDateFormat monthFormat = new SimpleDateFormat("MM");
            
            // Leggiamo l'oggetto JSON principale
            reader.beginObject();
            
            while(reader.hasNext()){
                String name = reader.nextName();
                
                if(name.equals("trades")){
                    // Leggiamo l'array "trades"
                    reader.beginArray();
                    
                    while (reader.hasNext()){
                        // Deserializziamo ciascun trade come un oggetto Trade
                        GsonHistoryOrder trade = gson.fromJson(reader, GsonHistoryOrder.class);

                        // Creiamo un oggetto Date dal timestamp
                        Date tradeDate = new Date(trade.timestamp * 1000L);
                        
                        // Estraiamo il mese (formato "MM")
                        String month = monthFormat.format(tradeDate);
                        
                        // Filtriamo solo gli ordini del mese desiderato
                        if (month.equals(monthPassed)){

                            // Convertiamo il timestamp in formato data leggibile
                            String formattedDate = sdf.format(tradeDate);
                            
                            // Stampiamo le informazioni del trade con la data formattata
                            String tradeInfo = String.format("OrderID: %d, Type: %s, OrderType: %s, Size: %d, Price: %d, Date: %s",trade.orderId,trade.type,trade.orderType,trade.size,trade.price,formattedDate);

                            return tradeInfo;
                        }
                    }
                    reader.endArray();
                } else {
                    // Salta altri campi se presenti
                    reader.skipValue();
                }
            }
            reader.endObject();
    
        } catch (Exception e){
            System.err.printf("[WORKER]: Errore Messaggio ricevuto: %s %s\n",e.getMessage(),e.getCause());
        }
        return "";
    }

    //funzione per mandare la notifica UDP al cliente il cui ordine è stato processato
    /*public static void sendUDPmessage(UserBook user, DatagramSocket sock,GsonTrade obj){
        try{
            //estraggo la porta e l'address dal socket passato
            int port = sock.getLocalPort();
            InetAddress address = sock.getLocalAddress();

            // Converti JSON in byte array
            byte[] sendData = obj.toString().getBytes();

            // Crea e invia il pacchetto UDP
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, address, port);
            sock.send(sendPacket);
        } catch(Exception e){
            System.err.println("[SEND_UDP_MESSAGE]: " + e.getMessage());
        }
    }*/

    public static boolean isValid(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        
        return str.matches("^[a-zA-Z0-9]+$");
    }

    public static void updateJsonUsermap(ConcurrentHashMap<String,Tupla> userMap){
        try(BufferedWriter writer = new BufferedWriter(new FileWriter("src/JsonFile/userMap.json"))){
            Gson g = new GsonBuilder().setPrettyPrinting().create();
            writer.write(g.toJson(userMap));

        } catch (Exception e){
            System.err.printf("[WORKER]: updateJsonUsermap %s \n",e.getMessage());
        }
    }

    public static void updateJsonOrderBook(OrderBook orderBook){
        try(BufferedWriter writer = new BufferedWriter(new FileWriter("src/JsonFile/orderBook.json"))){
            Gson g = new GsonBuilder().setPrettyPrinting().create();
            writer.write(g.toJson(orderBook));

        } catch (Exception e){
            System.err.printf("[WORKER]: updateJsonOrderBook %s \n",e.getMessage());
        }
    }
}
