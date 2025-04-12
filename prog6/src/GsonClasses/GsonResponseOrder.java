package GsonClasses;

import java.io.PrintWriter;

import com.google.gson.Gson;

public class GsonResponseOrder extends Values{
    public int orderID;

    public void setResponseOrder(int orderID){
        this.orderID = orderID;
    }

    // serializzo il messaggio di risposta e lo mando sullo stream out 
    public void sendMessage(Gson gson,PrintWriter out){
        String respMessage = gson.toJson(this);
        out.println(respMessage);
    }

    public String toString() {
        return "{orderID=" + this.orderID + "}";
     }
}
