package GsonClasses;

public class GsonLimitStopOrder extends Values{
    public String type;
    public int size;
    public int price;

    public GsonLimitStopOrder(String type, int size,int price){
        this.type = type;
        this.size = size;
        this.price = price;
    }

    public int getPrice(){
        return this.price;
    }

    public String toString() {
        return "{type ='" + this.type + "', size ='" + this.size + "', price ='" + this.price + "}";
    }
    
}
