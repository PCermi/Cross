package GsonClasses.Commands;

import GsonClasses.Values;

/* Classe utilizzata per passare al server i dati per inserire un LimitOrder oppure uno StopOrder.
Il campo price corrisponde al limitPrice se si sta inviando un limitOrder oppure allo stopPrice se si sta inviando uno stopOrder */
public class GsonLimitStopOrder extends Values{
    public String type;
    public int size;
    public int price;

    public GsonLimitStopOrder(String type, int size,int price){
        this.type = type;
        this.size = size;
        this.price = price;
    }

    public String getType(){
        return this.type;
    }
    
    public int getSize(){
        return this.size;
    }
    
    public int getPrice(){
        return this.price;
    }

    public String toString() {
        return "{type ='" + this.type + "', size ='" + this.size + "', price ='" + this.price + "}";
    }
    
}
