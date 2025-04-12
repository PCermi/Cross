package OrderBook;

import java.util.concurrent.ConcurrentLinkedQueue;

// Oggetto che descrive i campi della askMap e della bidMap
public class BookValue{
    public int size;
    public int total;
    public ConcurrentLinkedQueue<UserBook> userList;

    public BookValue(int size, int total, ConcurrentLinkedQueue<UserBook> userList){
        this.size = size;
        this.total = total;
        this.userList = userList;
    }

    public String toString(){
        return "{size =" + size + ", total =" + total + ", userList =" + userList + "}";
    }
}
