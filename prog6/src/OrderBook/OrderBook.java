package OrderBook;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;

import GsonClasses.GsonTrade;
import com.google.gson.Gson;

import Eseguibili.SockMapValue;

//import GsonClasses.GsonTrade;

import java.net.*;
import java.nio.ByteBuffer;
import java.time.Instant;

public class OrderBook {
    public ConcurrentSkipListMap<Integer,BookValue> askMap;
    public int spread;
    public int last_exchange;
    public int lastOrderID = 0;
    public ConcurrentLinkedQueue<StopValue> stopOrders;
    public ConcurrentSkipListMap<Integer,BookValue> bidMap;
    

    public OrderBook(ConcurrentSkipListMap<Integer,BookValue> askMap, int spread, int last_exchange, ConcurrentLinkedQueue<StopValue> stopOrders, ConcurrentSkipListMap<Integer,BookValue> bidMap){
        this.askMap = askMap;
        this.spread = spread;
        this.last_exchange = last_exchange;
        this.bidMap = bidMap;
        this.stopOrders = stopOrders;
        updateOrderBook();
    }


    public void notifyUser(ConcurrentSkipListMap<String,SockMapValue> socketMap, String user, int orderID, String type,String orderType,int size, int price){
        System.out.println("notifyUser chiamata per user: " + user);

        //estraggo la porta dell'utente a cui mandare la notifica
        int port = 0;
        InetAddress address = null;
        for(Map.Entry<String,SockMapValue> entry : socketMap.entrySet()){
            if(entry.getKey().equals(user)){
                port = entry.getValue().port;
                address = entry.getValue().address;
                break;
            }
        }
        
        System.out.printf("socket non creato. Porta: %d\n",port);
        try(DatagramSocket sock = new DatagramSocket(port)){
            System.out.println("socket creato");

            GsonTrade trade = new GsonTrade(orderID,type,orderType,size,price);

            // Serializzo l'oggetto GsonTrade in JSON
            Gson gson = new Gson();
            String json = gson.toJson(trade);

            // Converto il JSON in un array di byte
            byte[] data = json.getBytes("StandardCharsets.UTF_8");

            // Creo un pacchetto Datagram con i dati e indirizzo del server
            DatagramPacket packet = new DatagramPacket(data, data.length,address, port);

            // Invia il pacchetto
            sock.send(packet);

            System.out.println("Messaggio JSON inviato: " + json);
        } catch (Exception e){
            System.err.println("NotifyUser() Error: " + e.getMessage());
        }
    }

    //metodo che restituisce tutti gli username presenti nella userList passata
    public ConcurrentLinkedQueue<String> getUsers(ConcurrentLinkedQueue<UserBook> list){
        ConcurrentLinkedQueue<String> result = new ConcurrentLinkedQueue<>();
        for(UserBook user : list){
            result.add(user.username);
        }
        return result;
    }
    
    public synchronized void checkStopOrders(ConcurrentSkipListMap<String,SockMapValue> socketMap){
        Iterator<StopValue> iterator = stopOrders.iterator();
        while(iterator.hasNext()){
            StopValue order = iterator.next();

            if(order.type.equals("ask")){ //controllo la bidMap
                if(!bidMap.isEmpty()){
                    if(order.stopPrice <= bidMap.firstKey()){ //raggiunto stopPrice: eseguo l'ordine come un MarketOrder
                        //controllo che la lista della bidmap non contenga solo ordini inseriti dall'utente dello stopOrder
                        ConcurrentLinkedQueue<String> list = getUsers(bidMap.get(bidMap.firstKey()).userList);
                        if(list.stream().anyMatch(s -> !s.equals(order.username))){

                            int res = tryMarketOrder(order.type,order.size,order.username, socketMap);
                            lastOrderID--; //decremento lastOrderID perchè ho già un orderID per lo stopOrder
                            if(res != -1){//l'ordine è stato processato
                                System.out.printf("StopOrder di %s processato correttamente: %s\n",order.username, order.toString());
                                //mando la notifica UDP al client che aveva inserito lo stopOrder (oppure la mando già quando chiamo la tryMarketOrder? Da decidere)
                            } else{
                                //mando la notifica UDP al client che l'ordine non è andato a buon fine
                                System.out.printf("StopOrder di %s processato ma fallito: %s\n",order.username, order.toString());
                            }
                            iterator.remove();
                        }
                    }
                }
            } else{ //controllo la askMap
                if(!askMap.isEmpty()){
                    if(order.stopPrice >= askMap.firstKey()){ //raggiunto stopPrice: eseguo l'ordine come un MarketOrder
                        //controllo che la lista della askmap non contenga solo ordini inseriti dall'utente dello stopOrder
                        ConcurrentLinkedQueue<String> list = getUsers(askMap.get(askMap.firstKey()).userList);
                        if(list.stream().anyMatch(s -> !s.equals(order.username))){

                            int res = tryMarketOrder(order.type,order.size,order.username, socketMap);
                            lastOrderID--; //decremento lastOrderID perchè ho già un orderID per lo stopOrder
                            if(res != -1){//l'ordine è stato processato
                                System.out.println("StopOrder processato correttamente: " + order.toString());
                                //mando la notifica UDP al client che aveva inserito lo stopOrder (oppure la mando già quando chiamo la tryMarketOrder? Da decidere)
                            } else{
                                //mando la notifica UDP al client che l'ordine non è andato a buon fine
                                System.out.println("StopOrder processato ma fallito: " + order.toString());
                            }
                            iterator.remove();
                        }
                    }
                }
            }
        }
    }

    public void loadBidOrder(int size, int price, String user, int orderID){
        UserBook newUser = new UserBook(size, user, orderID); //creo il nuovo utente

        if(bidMap.containsKey(price)){ // chiave esiste
            BookValue oldValue = bidMap.get(price); 
            //creo una nuova lista a cui aggiungo il nuovo utente
            ConcurrentLinkedQueue<UserBook> newList = new ConcurrentLinkedQueue<>(oldValue.userList);
            newList.add(newUser);
            //calcolo la nuova size
            int newSize = oldValue.size + size;
            //cre il nuovo valore da sostituire al valore della chiave askPrice
            BookValue newValue = new BookValue(newSize,newSize*price,newList);
            bidMap.replace(price, newValue);

        } else{ // chiave non esiste, creo una nuova coppia price-value
            ConcurrentLinkedQueue<UserBook> newList = new ConcurrentLinkedQueue<>();
            newList.add(newUser);
            BookValue value = new BookValue(size, price*size, newList);
            bidMap.put(price,value);
        }
    }

    public int newTryBidOrder(int size, int price, String user, ConcurrentSkipListMap<String,SockMapValue> socketMap){
        // arrivato LimitOrder bid: cerco un match con la askMap
        int remainingSize = size;
        int orderID = updateLastOrderID();

        for(Map.Entry<Integer, BookValue> entry : askMap.entrySet()){
            int askPrice = entry.getKey();
            BookValue askValue = entry.getValue();

            if(askPrice <= price){// Questo prezzo fa match
                System.out.printf("match tra map price: %d e price: %d\n",askPrice,price);
                remainingSize = tryMatch(remainingSize,user,"bid",askValue.userList,"ask","limit",askPrice,orderID,socketMap);
                System.out.printf("remainingSize: %d\n",remainingSize);
            }

            if(remainingSize == 0){// ordine completato
                System.out.println("Order number "+orderID + "has been completed");
                updateOrderBook();
                return orderID; //restituisco il numero dell'ordine
            }
        }
        //se sono qui l'ordine non è stato evaso completamente: devo caricarlo sull'orderBook
        if(remainingSize>0){
            loadBidOrder(remainingSize, price, user, orderID);
            System.out.println("Order number "+orderID + "has been partially completed: the rest is in the orderBook");
        }
        updateOrderBook();
        return orderID;
    }

    public void loadAskOrder(int size, int price, String user, int orderID){
        UserBook newUser = new UserBook(size, user, orderID); //creo il nuovo utente

        if(askMap.containsKey(price)){ // chiave esiste
            BookValue oldValue = askMap.get(price); 
            //creo una nuova lista a cui aggiungo il nuovo utente
            ConcurrentLinkedQueue<UserBook> newList = new ConcurrentLinkedQueue<>(oldValue.userList);
            newList.add(newUser);
            //calcolo la nuova size
            int newSize = oldValue.size + size;
            //cre il nuovo valore da sostituire al valore della chiave askPrice
            BookValue newValue = new BookValue(newSize,newSize*price,newList);
            askMap.replace(price, newValue);

        } else{ // chiave non esiste, creo una nuova coppia price-value
            ConcurrentLinkedQueue<UserBook> newList = new ConcurrentLinkedQueue<>();
            newList.add(newUser);
            BookValue value = new BookValue(size, price*size, newList);
            askMap.put(price,value);
        }
    }

    public int newTryAskOrder(int size, int price, String user, ConcurrentSkipListMap<String,SockMapValue> socketMap){
        // arrivato LimitOrder ask: cerco un match con la bidMap
        int remainingSize = size;
        int orderID = updateLastOrderID();

        for(Map.Entry<Integer, BookValue> entry : bidMap.entrySet()){
            int bidPrice = entry.getKey();
            BookValue bidValue = entry.getValue();

            if(bidPrice >= price){// Questo prezzo fa match
                System.out.printf("match tra map price: %d e price: %d\n",bidPrice,price);
                remainingSize = tryMatch(remainingSize,user, "ask" ,bidValue.userList,"bid","limit",bidPrice, orderID, socketMap);
                System.out.printf("remainingSize: %d\n",remainingSize);
            }

            if(remainingSize == 0){// ordine completato
                System.out.println("Order number "+orderID + "has been completed");
                updateOrderBook();
                return orderID; //restituisco il numero dell'ordine
            }
        }
        //se sono qui l'ordine non è stato evaso completamente: devo caricarlo sull'orderBook
        if(remainingSize>0){
            loadAskOrder(remainingSize, price, user, orderID);
            System.out.println("Order number "+orderID + "has been partially completed: the rest is in the orderBook");
        }
        updateOrderBook();
        return orderID;
    }

    public int tryMatch(int remainingSize, String user,String userType, ConcurrentLinkedQueue<UserBook> list, String listType,String orderType, int price, int orderID,ConcurrentSkipListMap<String,SockMapValue> socketMap){
        
        Iterator<UserBook> iterator = list.iterator();
        while(iterator.hasNext() && remainingSize>0){
            UserBook IUser = iterator.next();
            if(!IUser.username.equals(user)){

                if(IUser.size > remainingSize){ //ordine (di user) completato con parte della size dell'utente nella map (IUser)
                    IUser.size -= remainingSize; //decremento la size dell'utente della lista
                    
                    //notifico IUser con messaggio UDP: l'ordine è stato evaso parzialmente (remainingSize)
                    notifyUser(socketMap, IUser.username,IUser.orderID,listType,orderType,remainingSize,price);
                    
                    //notifico user con messaggio UDP: l'ordine è stato evaso tutto
                    notifyUser(socketMap,user,orderID,userType,orderType,remainingSize,price);
                    
                    //azzero la size perchè l'ordine è stato completato
                    remainingSize = 0;
                } else if(IUser.size < remainingSize){ //ordine (di user) non completato: ordine (di IUser) completato
                    remainingSize -= IUser.size;

                    //notifico IUser con messaggio UDP: l'ordine è stato evaso tutto
                    notifyUser(socketMap, IUser.username,IUser.orderID,listType,orderType,IUser.size,price);

                    //notifico user con messaggio UDP: l'ordine è stato evaso parzialmente (IUser.size)
                    notifyUser(socketMap,user,orderID,userType,orderType,IUser.size,price);

                    iterator.remove(); // rimuovo l'utente dalla lista
                } else{ // ordine completato con tutta la size dell'utente della lista
                    
                    iterator.remove(); //rimuovo l'utente dall lista
                    
                    //notifico IUser con messaggio UDP: l'ordine è stato evaso tutto
                    notifyUser(socketMap, IUser.username,IUser.orderID,listType,orderType,IUser.size,price);

                    //notifico user con messaggio UDP: l'ordine è stato evaso tutto
                    notifyUser(socketMap,user,orderID,userType,orderType,remainingSize,price);

                    remainingSize = 0;
                }
            }
        }
        return remainingSize;
    }

    // restituisce il nuovo orderID oppure -1
    public int tryMarketOrder(String type, int size, String user, ConcurrentSkipListMap<String,SockMapValue> socketMap){
        int remainingSize = size;
        
        if(type.equals("ask")){ //ricevuto ask: devo matchare con bid
            
            //controllo se la mappa contiene abbastanza size per soddisfare l'ordine (Escludendo la size degli ordini inseriti dall'utente che ha fatto il marketOrder)
            if(totalMapSize(bidMap)-totalUserSize(bidMap, user) < size){
                return -1;
            }

            int orderID = updateLastOrderID();
            
            for(Map.Entry<Integer, BookValue> entry : bidMap.entrySet()){ //scorro la bidMap
                int price = entry.getKey();
                BookValue value = entry.getValue();
                
                remainingSize = tryMatch(remainingSize,user,"ask",value.userList,"bid","market",price,orderID,socketMap);

                if(remainingSize == 0){// ordine completato
                    updateOrderBook();
                    return orderID; //restituisco il numero dell'ordine
                }
            }

        } else{ // ricevuto bid: devo matchare con ask. Quindi scorro la askMask in modo crescente

            //controllo se la mappa contiene abbastanza size per soddisfare l'ordine (Escludendo la size degli ordini inseriti dall'utente che ha fatto il marketOrder)
            if(totalMapSize(askMap)-totalUserSize(askMap, user) < size){
                return -1;
            }

            int orderID = updateLastOrderID();
            
            for(Map.Entry<Integer, BookValue> entry : askMap.entrySet()){ // Scorro la askMap
                int price = entry.getKey();
                BookValue value = entry.getValue();
                
                remainingSize = tryMatch(remainingSize,user,"bid",value.userList,"ask","market",price,orderID,socketMap);

                //controllo comunque remainingSize perchè gli ordini caricati dallo stesso utente vengono ignorati
                if(remainingSize == 0){// ordine completato
                    updateOrderBook();
                    return orderID; //restituisco il numero dell'ordine
                }
            }
        }
        return -1;
    }

    // ritorna 100 se l'ordine è stato evaso correttamente, 101 altrimenti
    public int cancelOrder(int orderID, String onlineUser){
        //scorro la askMap
        for(Map.Entry<Integer, BookValue> entry : askMap.entrySet()){
            BookValue value = entry.getValue();
            
            Iterator<UserBook> iterator = value.userList.iterator();
            while(iterator.hasNext()){
                UserBook user = iterator.next();
                //rimuovo l'ordine se l'ID corrisponde e l'ordine è stato fatto dall'utente che vuole cancellarlo
                if(user.orderID == orderID && user.username.equals(onlineUser)){
                    iterator.remove();
                    updateOrderBook();
                    return 100;
                }
            }
        }
        //scorro la bidMap
        for(Map.Entry<Integer, BookValue> entry : bidMap.entrySet()){
            BookValue value = entry.getValue();

            Iterator<UserBook> iterator = value.userList.iterator();
            while(iterator.hasNext()){
                UserBook user = iterator.next();
                //rimuovo l'ordine se l'ID corrisponde e l'ordine è stato fatto dall'utente che vuole cancellarlo
                if(user.orderID == orderID && user.username.equals(onlineUser)){
                    iterator.remove();
                    updateOrderBook();
                    return 100;
                }
            }
        }

        //scorro gli stopOrders
        Iterator<StopValue> iterator = stopOrders.iterator();
        while(iterator.hasNext()){
            StopValue user = iterator.next();

            //rimuovo l'ordine se l'ID corrisponde e l'ordine è stato fatto dall'utente che vuole cancellarlo
            if(user.orderID == orderID && user.username.equals(onlineUser)){
                iterator.remove();
                updateOrderBook();
                return 100;
            }
        }

        return 101;
    }

    //metodo per aggiornare la size delle ask e bid map, i relativi totali, lo spread ed eventualmente elimina i prezzi dove la userList è vuota
    public void updateOrderBook(){

        // Controllo e rimozione dalle askMap
        Iterator<Map.Entry<Integer, BookValue>> askIterator = askMap.entrySet().iterator();
        while (askIterator.hasNext()) {
            Map.Entry<Integer, BookValue> entry = askIterator.next();
            int price = entry.getKey();
            BookValue value = entry.getValue();
            
            if (value.userList.isEmpty()) {
                askIterator.remove();
            } else{
                // Ricalcola size e total in base agli utenti nella lista
                int newSize = 0;
                
                for (UserBook user : value.userList) {
                    newSize += user.size;
                }
                
                // Aggiorna i valori di BookValue
                value.size = newSize;
                value.total = newSize*price;
            }
        }

        // Controllo e rimozione dalle bidMap
        Iterator<Map.Entry<Integer, BookValue>> bidIterator = bidMap.entrySet().iterator();
        while (bidIterator.hasNext()) {
            Map.Entry<Integer, BookValue> entry = bidIterator.next();
            int price = entry.getKey();
            BookValue value = entry.getValue();
            
            if (value.userList.isEmpty()) {
                bidIterator.remove();
            } else{
                // Ricalcola size e total in base agli utenti nella lista
                int newSize = 0;
                
                for (UserBook user : value.userList) {
                    newSize += user.size;
                }
                
                // Aggiorna i valori di BookValue
                value.size = newSize;
                value.total = newSize*price;
            }
        }
        //aggiorno lo spread
        updateSpread();
    }

    public int updateLastOrderID(){
        lastOrderID++;
        return lastOrderID;
    }

    public void updateSpread(){
        if(!bidMap.isEmpty() && !askMap.isEmpty()){
            int maxBid = bidMap.firstKey();
            int maxAsk = askMap.firstKey();
            System.out.println("maxBid: " + maxBid + " - minAsk: " + maxAsk + " = spread");
            this.spread = maxBid - maxAsk;
        }
    }

    // restituisce la size totale della askMap oppure della bidMap
    public int totalMapSize(ConcurrentSkipListMap<Integer,BookValue> map){
        int res = 0;
        for(Map.Entry<Integer, BookValue> entry : map.entrySet()){
            res += entry.getValue().size;
        }
        return res;
    }

    // restituisce la size totale che un utente ha inserito nella askMap oppure nella bidMap
    public int totalUserSize(ConcurrentSkipListMap<Integer,BookValue> map, String username){
        int res = 0;
        for(Map.Entry<Integer, BookValue> entry : map.entrySet()){
            for(UserBook user : entry.getValue().userList){
                if(user.username.equals(username)){
                    res += user.size;
                }
            }
        }
        return res;
    }

    // restituisce la size totale di tutti gli utenti in una lista
    public int totalUserListSize(ConcurrentLinkedQueue<UserBook> list){
        int res = 0;
        for(UserBook user : list){
            res += user.size;
        }

        return res;
    }

    public String toString(){
        return "{ask_Section =" + askMap.toString() + ", spread =" + spread + ", last_exchange =" + last_exchange + ", bid_Section =" + bidMap.toString() + "}";
    }
}
