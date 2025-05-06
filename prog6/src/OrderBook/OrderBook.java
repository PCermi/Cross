package OrderBook;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.net.*;
import java.nio.charset.StandardCharsets;
import com.google.gson.Gson;

import Eseguibili.Server.SockMapValue;

/* Classe che rappresenta l'order book del mercato, gestendo ordini di bid e ask. Fornisce funzionalità per processare marketOrders stopOrders e LimitOrders
*/
public class OrderBook {
    public ConcurrentSkipListMap<Integer,BookValue> askMap; // Mappa per gli ordini di vendita: crescente
    public int spread; // Differenza tra il miglior prezzo di acquisto e quello di vendita
    public int lastOrderID = 0; // Contatore per assegnare ID univoci agli ordini
    public ConcurrentLinkedQueue<StopValue> stopOrders; // Lista per tenere traccia degli stopOrders
    public ConcurrentSkipListMap<Integer,BookValue> bidMap; // Mappa per gli ordini di acquisto: decrescente
    

    public OrderBook(ConcurrentSkipListMap<Integer,BookValue> askMap, int spread, ConcurrentLinkedQueue<StopValue> stopOrders, ConcurrentSkipListMap<Integer,BookValue> bidMap){
        this.askMap = askMap;
        this.spread = spread;
        this.bidMap = bidMap;
        this.stopOrders = stopOrders;
        updateOrderBook();
    }

    /* Metodo che notifica un utente sullo stato del proprio ordine tramite UDP. Parametri:
     * socketMap: Mappa degli utenti connessi e delle relative informazioni socket
     * user: Nome utente da notificare
     * orderID: ID dell'ordine
     * type: Tipo di ordine ("bid" o "ask")
     * orderType: Categoria dell'ordine ("limit", "market", "stop")
     * size: Dimensione dell'ordine che è stato eseguito
     * price: Prezzo al quale l'ordine è stato eseguito
    */
    public void notifyUser(ConcurrentSkipListMap<String,SockMapValue> socketMap, String user, int orderID, String type,String orderType,int size, int price){
        System.out.println("Notifying user: " + user);

        // Estrazione della porta dell'utente a cui mandare la notifica dalla socketMap
        int port = 0;
        InetAddress address = null;
        for(Map.Entry<String,SockMapValue> entry : socketMap.entrySet()){
            if(entry.getKey().equals(user)){
                port = entry.getValue().port;
                address = entry.getValue().address;
                break;
            }
        }
        if(port != 0 && address != null){
            try(DatagramSocket sock = new DatagramSocket()){
                TradeUDP trade = new TradeUDP(orderID,type,orderType,size,price);

                // Serializzazione dell'oggetto TradeUDP in formato JSON
                Gson gson = new Gson();
                String json = gson.toJson(trade);

                // Conversione del JSON in un array di byte
                byte[] data = json.getBytes(StandardCharsets.UTF_8);

                // Creazione di un pacchetto Datagram con i dati e indirizzo del server
                DatagramPacket packet = new DatagramPacket(data, data.length,address, port);

                // Invio del pacchetto
                sock.send(packet);

            } catch (Exception e){
                System.err.println("NotifyUser() Error: " + e.getMessage());
            }
        } else{
            System.out.println("User not online, message not sent");
        }
    }

    // Metodo che restituisce tutti gli username presenti nella userList passata
    public ConcurrentLinkedQueue<String> getUsers(ConcurrentLinkedQueue<UserBook> list){
        ConcurrentLinkedQueue<String> result = new ConcurrentLinkedQueue<>();
        for(UserBook user : list){
            result.add(user.username);
        }
        return result;
    } 

    // Metodo che controlla tutti gli stopOrder per verificare se qualcuno di essi debba essere eseguito in base ai prezzi di mercato correnti.
    public synchronized void checkStopOrders(ConcurrentSkipListMap<String,SockMapValue> socketMap){
        Iterator<StopValue> iterator = stopOrders.iterator();
        while(iterator.hasNext()){
            StopValue order = iterator.next();

            if(order.type.equals("ask")){ // Controllo della bidMap
                if(!bidMap.isEmpty()){
                    if(order.stopPrice <= bidMap.firstKey()){ //raggiunto stopPrice: esecuzione dell'ordine come un MarketOrder
                        // Si verifica che la lista della bidmap non contenga solo ordini inseriti dall'utente dello stopOrder
                        ConcurrentLinkedQueue<String> list = getUsers(bidMap.get(bidMap.firstKey()).userList);
                        if(list.stream().anyMatch(s -> !s.equals(order.username))){

                            int res = tryMarketOrder(order.type,order.size,order.username,"stop", socketMap);
                            lastOrderID--; // Si decrementa lastOrderID perchè si ha già un orderID per lo stopOrder
                            if(res != -1){// L'ordine è stato elaborato con successo
                                System.out.printf("%s's StopOrder processed successfully. Order: %s\n",order.username, order.toString());
                            } else{
                                System.out.printf("%s's StopOrder was processed but failed. Order: %s\n",order.username, order.toString());
                                
                                notifyUser(socketMap, order.username, order.orderID, order.type, "stop", 0, 0);
                            }
                            iterator.remove();
                        }
                    }
                }
            } else{ // Controllo della askMap
                if(!askMap.isEmpty()){
                    if(order.stopPrice >= askMap.firstKey()){ //raggiunto stopPrice: esecuzione dell'ordine come un MarketOrder
                        // Si verifica che la lista della askmap non contenga solo ordini inseriti dall'utente dello stopOrder
                        ConcurrentLinkedQueue<String> list = getUsers(askMap.get(askMap.firstKey()).userList);
                        if(list.stream().anyMatch(s -> !s.equals(order.username))){

                            int res = tryMarketOrder(order.type,order.size,order.username,"stop", socketMap);
                            lastOrderID--; // Si decrementa lastOrderID perchè si ha già un orderID per lo stopOrder
                            if(res != -1){// L'ordine è stato elaborato con successo
                                System.out.printf("%s's StopOrder processed successfully. Order: %s\n",order.username, order.toString());
                            } else{
                                System.out.printf("%s's StopOrder was processed but failed. Order: %s\n",order.username, order.toString());
                                
                                notifyUser(socketMap, order.username, order.orderID, order.type, "stop", 0, 0);
                            }
                            iterator.remove();
                        }
                    }
                }
            }
        }
    }

    // Metodo per caricare un ordine di acquisto (bid)
    public void loadBidOrder(int size, int price, String user, int orderID){
        UserBook newUser = new UserBook(size, user, orderID); // Creazione del nuovo utente

        if(bidMap.containsKey(price)){ // Il prezzo già esiste
            BookValue oldValue = bidMap.get(price); 
            // Creazione di una nuova lista a cui viene aggiunto il nuovo utente
            ConcurrentLinkedQueue<UserBook> newList = new ConcurrentLinkedQueue<>(oldValue.userList);
            newList.add(newUser);
            // Calcolo della nuova size
            int newSize = oldValue.size + size;
            // Creazione del nuovo valore per sostituire quello esistente
            BookValue newValue = new BookValue(newSize,newSize*price,newList);
            bidMap.replace(price, newValue);

        } else{ // La chiave non esiste, si crea una nuova coppia price-value
            ConcurrentLinkedQueue<UserBook> newList = new ConcurrentLinkedQueue<>();
            newList.add(newUser);
            BookValue value = new BookValue(size, price*size, newList);
            bidMap.put(price,value);
        }
    }

    /* Metodo per elaborare un limitOrder di tipo bid.
    Il metodo cerca corrispondenze nella askMap eseguendo l'algoritmo tryMatch e carica parzialmente o totalmente l'ordine nella bidMap se non viene soddisfatto.*/
    public int tryBidOrder(int size, int price, String user, ConcurrentSkipListMap<String,SockMapValue> socketMap){
        int remainingSize = size;
        int orderID = updateLastOrderID();

        for(Map.Entry<Integer, BookValue> entry : askMap.entrySet()){
            int askPrice = entry.getKey();
            BookValue askValue = entry.getValue();

            if(askPrice <= price){// Il prezzo della askMap fa match
                remainingSize = tryMatch(remainingSize,user,"bid",askValue.userList,"ask","limit",askPrice,orderID,socketMap);
            }

            if(remainingSize == 0){// Ordine completato
                System.out.println("Order number "+ orderID + " has been completed");
                updateOrderBook();
                return orderID; // Viene restituito il numero dell'ordine
            }
        }
        // L'ordine non è stato evaso completamente: deve essere caricato sull'orderBook
        if(remainingSize > 0){
            loadBidOrder(remainingSize, price, user, orderID);
            if(remainingSize == size){
                // L'ordine non è stato matchato
                System.out.println("Order number "+ orderID + " unmatched: " + remainingSize + " placed in the orderBook");
            } else{
                // L'ordine è stato evaso parzialmente
                System.out.println("Order number "+ orderID + " was partially completed; the remaining size of " + remainingSize + " was added to the orderBook");
            }
        }
        updateOrderBook();
        return orderID;
    }

    // Metodo per caricare un ordine di vendita (ask)
    public void loadAskOrder(int size, int price, String user, int orderID){
        UserBook newUser = new UserBook(size, user, orderID); // Creazione del nuovo utente

        if(askMap.containsKey(price)){ // Il prezzo già esiste
            BookValue oldValue = askMap.get(price); 
            // Creazione di una nuova lista a cui viene aggiunto il nuovo utente
            ConcurrentLinkedQueue<UserBook> newList = new ConcurrentLinkedQueue<>(oldValue.userList);
            newList.add(newUser);
            // Calcolo della nuova size
            int newSize = oldValue.size + size;
            // Creazione del nuovo valore per sostituire quello esistente
            BookValue newValue = new BookValue(newSize,newSize*price,newList);
            askMap.replace(price, newValue);

        } else{ // La chiave non esiste, si crea una nuova coppia price-value
            ConcurrentLinkedQueue<UserBook> newList = new ConcurrentLinkedQueue<>();
            newList.add(newUser);
            BookValue value = new BookValue(size, price*size, newList);
            askMap.put(price,value);
        }
    }

    /* Metodo per elaborare un limitOrder di tipo ask.
    Il metodo cerca corrispondenze nella bidMap eseguendo l'algoritmo tryMatch e carica parzialmente o totalmente l'ordine nella askMap se non viene soddisfatto.*/
    public int tryAskOrder(int size, int price, String user, ConcurrentSkipListMap<String,SockMapValue> socketMap){
        int remainingSize = size;
        int orderID = updateLastOrderID();

        for(Map.Entry<Integer, BookValue> entry : bidMap.entrySet()){
            int bidPrice = entry.getKey();
            BookValue bidValue = entry.getValue();

            if(bidPrice >= price){// Il prezzo della bidMap fa match
                remainingSize = tryMatch(remainingSize,user, "ask" ,bidValue.userList,"bid","limit",bidPrice, orderID, socketMap);
            }

            if(remainingSize == 0){// Ordine completato
                System.out.println("Order number "+orderID + " has been completed");
                updateOrderBook();
                return orderID; // Viene restituito il numero dell'ordine
            }
        }
        // L'ordine non è stato evaso completamente: deve essere caricato sull'orderBook
        if(remainingSize > 0){
            loadAskOrder(remainingSize, price, user, orderID);
            if(remainingSize == size){
                // L'ordine non è stato matchato
                System.out.println("Order number "+ orderID + " unmatched: " + remainingSize + " placed in the orderBook");
            } else{
                // L'ordine è stato evaso parzialmente
                System.out.println("Order number "+ orderID + " was partially completed; the remaining size of " + remainingSize + " was added to the orderBook");
            }
        }
        updateOrderBook();
        return orderID;
    }

    /* Algoritmo per eseguire il matching tra ordini ask-bid. Restituisce la dimensione rimanente dopo aver eseguito i match possibili. Parametri:
     * remainingSize: Dimensione dell'ordine da abbinare
     * user: Username del proprietario dell'ordine
     * userType: Tipo dell'ordine dell'utente ("bid" o "ask")
     * list: Lista degli ordini di controparte
     * listType: Tipo degli ordini di controparte ("bid" o "ask")
     * orderType: Categoria dell'ordine ("limit", "market", "stop")
     * price: Prezzo al quale gli ordini sono abbinati
     * orderID: ID dell'ordine dell'utente
     * socketMap: Mappa degli utenti connessi e delle relative informazioni socket usate per inviare la notifica UDP
    */
    public int tryMatch(int remainingSize, String user,String userType, ConcurrentLinkedQueue<UserBook> list, String listType,String orderType, int price, int orderID,ConcurrentSkipListMap<String,SockMapValue> socketMap){
        
        Iterator<UserBook> iterator = list.iterator();
        while(iterator.hasNext() && remainingSize>0){
            UserBook IUser = iterator.next();
            if(!IUser.username.equals(user)){

                if(IUser.size > remainingSize){ // Ordine di user completato con parte dell'ordine dell'utente nella mappa (IUser)
                    IUser.size -= remainingSize; // Si decrementa la size dell'utente della lista
                    
                    // Si notifica IUser con messaggio UDP: l'ordine è stato soddisfatto parzialmente (remainingSize)
                    notifyUser(socketMap, IUser.username,IUser.orderID,listType,"limit",remainingSize,price);
                    
                    // Si notifica user con messaggio UDP: l'ordine è stato soddisfatto completamente
                    notifyUser(socketMap,user,orderID,userType,orderType,remainingSize,price);
                    
                    //Si azzera la size perchè l'ordine è stato completato
                    remainingSize = 0;
                } else if(IUser.size < remainingSize){ // Ordine di user non completato: ordine (di IUser) completato
                    remainingSize -= IUser.size;

                    // Si notifica IUser con messaggio UDP: l'ordine è stato soddisfatto completamente
                    notifyUser(socketMap, IUser.username,IUser.orderID,listType,"limit",IUser.size,price);

                    // Si notifica user con messaggio UDP: l'ordine è stato soddisfatto parzialmente (IUser.size)
                    notifyUser(socketMap,user,orderID,userType,orderType,IUser.size,price);

                    iterator.remove(); // Si rimuove l'utente il cui ordine è stato completato dalla lista
                } else{ // ordine completato esattamente con la size dell'utente della lista
                    
                    iterator.remove(); // Si rimuove l'utente dall lista
                    
                    // Si notifica IUser con messaggio UDP: l'ordine è stato soddisfatto completamente
                    notifyUser(socketMap, IUser.username,IUser.orderID,listType,"limit",IUser.size,price);

                    // Si notifica user con messaggio UDP: l'ordine è stato soddisfatto completamente
                    notifyUser(socketMap,user,orderID,userType,orderType,remainingSize,price);

                    remainingSize = 0;
                }
            }
        }
        return remainingSize;
    }

    /* Metodo che elabora un MarketOrder abbinandolo con limitOrders. Restituisce il nuovo orderID oppure -1. Parametri:
     * type: Tipo di ordine ("bid" o "ask")
     * size: Dimensione dell'ordine
     * user: username del proprietario dell'ordine
     * orderType: Categoria dell'ordine ("market", "stop")
     * socketMap: Mappa degli utenti connessi e delle relative informazioni socket
    */
    public int tryMarketOrder(String type, int size, String user, String orderType ,ConcurrentSkipListMap<String,SockMapValue> socketMap){
        int remainingSize = size;
        
        if(type.equals("ask")){ // Ricevuto ask: bisogna matchare con bid
            
            // Si controlla se la mappa contiene abbastanza size per soddisfare l'ordine (escludendo la size degli ordini inseriti dall'utente che ha fatto il marketOrder)
            if(totalMapSize(bidMap)-totalUserSize(bidMap, user) < size){
                return -1;
            }

            int orderID = updateLastOrderID();
            
            for(Map.Entry<Integer, BookValue> entry : bidMap.entrySet()){ // Scorrimento della bidMap
                int price = entry.getKey();
                BookValue value = entry.getValue();
                
                remainingSize = tryMatch(remainingSize,user,"ask",value.userList,"bid",orderType,price,orderID,socketMap);

                if(remainingSize == 0){// Ordine completato
                    updateOrderBook();
                    return orderID; // Viene restituito il numero dell'ordine
                }
            }

        } else{ // Ricevuto bid: bisogna matchare con ask

            // Si controlla se la mappa contiene abbastanza size per soddisfare l'ordine (escludendo la size degli ordini inseriti dall'utente che ha fatto il marketOrder)
            if(totalMapSize(askMap)-totalUserSize(askMap, user) < size){
                return -1;
            }

            int orderID = updateLastOrderID();
            
            for(Map.Entry<Integer, BookValue> entry : askMap.entrySet()){ // Scorrimento della askMap
                int price = entry.getKey();
                BookValue value = entry.getValue();
                
                remainingSize = tryMatch(remainingSize,user,"bid",value.userList,"ask",orderType,price,orderID,socketMap);

                if(remainingSize == 0){// Ordine completato
                    updateOrderBook();
                    return orderID; // Viene restituito il numero dell'ordine
                }
            }
        }
        return -1;
    }

    // Metodo che permette di cancellare un ordine. Restituisce 100 se l'ordine è stato eliminato correttamente, 101 altrimenti
    public int cancelOrder(int orderID, String onlineUser){
        // Controllo della askMap
        for(Map.Entry<Integer, BookValue> entry : askMap.entrySet()){
            BookValue value = entry.getValue();
            
            Iterator<UserBook> iterator = value.userList.iterator();
            while(iterator.hasNext()){
                UserBook user = iterator.next();
                // Si rimuove l'ordine se l'ID corrisponde e l'ordine è stato inserito dall'utente che vuole cancellarlo
                if(user.orderID == orderID && user.username.equals(onlineUser)){
                    iterator.remove();
                    updateOrderBook();
                    return 100;
                }
            }
        }
        // Controllo della bidMap
        for(Map.Entry<Integer, BookValue> entry : bidMap.entrySet()){
            BookValue value = entry.getValue();

            Iterator<UserBook> iterator = value.userList.iterator();
            while(iterator.hasNext()){
                UserBook user = iterator.next();
                // Si rimuove l'ordine se l'ID corrisponde e l'ordine è stato inserito dall'utente che vuole cancellarlo
                if(user.orderID == orderID && user.username.equals(onlineUser)){
                    iterator.remove();
                    updateOrderBook();
                    return 100;
                }
            }
        }

        // Controllo degli stopOrders
        Iterator<StopValue> iterator = stopOrders.iterator();
        while(iterator.hasNext()){
            StopValue user = iterator.next();

            // Si rimuove l'ordine se l'ID corrisponde e l'ordine è stato inserito dall'utente che vuole cancellarlo
            if(user.orderID == orderID && user.username.equals(onlineUser)){
                iterator.remove();
                updateOrderBook();
                return 100;
            }
        }

        return 101;
    }

    // Metodo per aggiornare la size e i totali delle ask e bid map, lo spread ed eventualmente elimina i prezzi la cui userList è vuota
    public void updateOrderBook(){

        // Controllo e rimozione dalle askMap
        Iterator<Map.Entry<Integer, BookValue>> askIterator = askMap.entrySet().iterator();
        while(askIterator.hasNext()){
            Map.Entry<Integer, BookValue> entry = askIterator.next();
            int price = entry.getKey();
            BookValue value = entry.getValue();
            
            if(value.userList.isEmpty()){
                askIterator.remove();
            } else{
                // Ricalcolo della size e del totale
                int newSize = 0;
                
                for(UserBook user : value.userList){
                    newSize += user.size;
                }
                
                // Aggiornamento dei valori di BookValue
                value.size = newSize;
                value.total = newSize*price;
            }
        }

        // Controllo e rimozione dalle bidMap
        Iterator<Map.Entry<Integer, BookValue>> bidIterator = bidMap.entrySet().iterator();
        while(bidIterator.hasNext()){
            Map.Entry<Integer, BookValue> entry = bidIterator.next();
            int price = entry.getKey();
            BookValue value = entry.getValue();
            
            if(value.userList.isEmpty()){
                bidIterator.remove();
            } else{
                // Ricalcolo della size e del totale
                int newSize = 0;
                
                for(UserBook user : value.userList){
                    newSize += user.size;
                }
                
                // Aggiornamento dei valori di BookValue
                value.size = newSize;
                value.total = newSize*price;
            }
        }
        // Aggiornamento dello spread
        updateSpread();
    }

    // Metodo per incrementare il contatore degli ID degli ordini
    public int updateLastOrderID(){
        lastOrderID++;
        return lastOrderID;
    }

    // Metodo per aggiornare il valore dello spread
    public void updateSpread(){
        if(!bidMap.isEmpty() && !askMap.isEmpty()){
            int maxBid = bidMap.firstKey();
            int minAsk = askMap.firstKey();
            this.spread = maxBid - minAsk;
        } else if(bidMap.isEmpty() && !askMap.isEmpty()){
            this.spread = -1 * askMap.firstKey();
        } else if(!bidMap.isEmpty() && askMap.isEmpty()){
            this.spread = bidMap.firstKey();
        } else this.spread = 0;
    }

    // Metodo che restituisce la size totale della askMap oppure della bidMap
    public int totalMapSize(ConcurrentSkipListMap<Integer,BookValue> map){
        int res = 0;
        for(Map.Entry<Integer, BookValue> entry : map.entrySet()){
            res += entry.getValue().size;
        }
        return res;
    }

    // Metodo che restituisce la size totale che un utente ha inserito nella askMap oppure nella bidMap
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

    public ConcurrentSkipListMap<Integer,BookValue> getAskMap(){
        return this.askMap;
    }

    public ConcurrentSkipListMap<Integer,BookValue> getBidMap(){
        return this.bidMap;
    }

    public int getSpread(){
        return this.spread;
    }

    public int getLastOrderID(){
        return lastOrderID;
    }

    public ConcurrentLinkedQueue<StopValue> getStopOrders(){
        return this.stopOrders;
    }

    public String toString(){
        return "{ask_Section =" + askMap.toString() + ", spread =" + spread + ", stopOrders =" + stopOrders.toString() + ", bid_Section =" + bidMap.toString() + "}";
    }
}
