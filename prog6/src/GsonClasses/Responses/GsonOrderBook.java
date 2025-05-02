package GsonClasses.Responses;

import java.io.PrintWriter;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import OrderBook.OrderBook;

/* Classe utilizzata per creare un oggetto JSON contenente l'orderBook oppure gli stopOrders. Tale oggetto è poi inviato al client sullo stream */
public class GsonOrderBook{
    String type;
    OrderBook orderBook;

    public void setOrderBook(String type, OrderBook orderBook){
        this.type = type;
        this.orderBook = orderBook;
    }

    // Serializzazione del messaggio di risposta e invio sullo stream out 
    public void sendMessage(Gson gson, PrintWriter out){
        if("showOrderBook".equals(type)){
            // Creazione di un oggetto JSON personalizzato per l'OrderBook
            JsonObject orderBookJson = new JsonObject();
            orderBookJson.addProperty("type", type);
            
            // Si aggiunge l'OrderBook come JsonObject
            JsonObject bookObj = new JsonObject();
            
            // Si aggiungono askMap, bidMap come oggetti JSON separati
            bookObj.add("askMap", gson.toJsonTree(orderBook.getAskMap()));
            bookObj.add("bidMap", gson.toJsonTree(orderBook.getBidMap()));
            
            // Si aggiunge lo spread
            bookObj.addProperty("spread", orderBook.getSpread());
            
            // Si aggiunge l'intero oggetto OrderBook
            orderBookJson.add("orderBook", bookObj);
            
            // Invio del JSON personalizzato
            out.println(gson.toJson(orderBookJson));
        } 
        else if ("showStopOrders".equals(type)){
            // Creazione del JSON contenente solo la lista degli stop orders
            JsonObject stopOrdersJson = new JsonObject();
            stopOrdersJson.addProperty("type", type);
            
            // Aggiunta degli stop orders
            stopOrdersJson.add("stopOrders", gson.toJsonTree(orderBook.getStopOrders()));
            
            // Invio del JSON personalizzato
            out.println(gson.toJson(stopOrdersJson));
        }
    }
}
