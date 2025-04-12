package OrderBook;

public class Trades{
    private int orderId;
    private String type;
    private String orderType;
    private int size;
    private int price;
    private long timestamp;

    public Trades(int orderId, int size, int price, String type, String orderType, long timestamp){
        this.orderId = orderId;
        this.type = type;
        this.orderType = orderType;
        this.size = size;
        this.price = price;
        this.timestamp = timestamp;
    }
}
