package GsonClasses;

import java.time.Instant;

public class GsonTrade{
    private int orderId;
    private String type;
    private String orderType;
    private int size;
    private int price;
    private long timestamp;

    public GsonTrade(int orderId, String type, String orderType, int size, int price){
        this.orderId = orderId;
        this.type = type;
        this.orderType = orderType;
        this.size = size;
        this.price = price;
        this.timestamp = Instant.now().getEpochSecond();
    }
}
