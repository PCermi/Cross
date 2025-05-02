package OrderBook;

import java.time.Instant;

// Classe utilizzata per mandare la notifica UDP ai clienti
public class TradeUDP{
    public int orderId;
    public String type;
    public String orderType;
    public int size;
    public int price;
    public long timestamp;

    public TradeUDP(int orderId, String type, String orderType, int size, int price){
        this.orderId = orderId;
        this.type = type;
        this.orderType = orderType;
        this.size = size;
        this.price = price;
        this.timestamp = Instant.now().getEpochSecond();
    }

    public int getOrderID(){
        return orderId;
    }

    public String getType(){
        return type;
    }

    public String getOrderType(){
        return orderType;
    }

    public int getSize(){
        return size;
    }

    public int getPrice(){
        return price;
    }

    public long getTime(){
        return timestamp;
    }
}
