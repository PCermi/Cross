package GsonClasses;
import java.io.PrintWriter;

import com.google.gson.Gson;

public class GsonResponse{
    public String type;
    public int response;
    public String errorMessage;

    public void setResponse(String type, int response, String errorMessage){
        this.type = type;
        this.response = response;
        this.errorMessage = errorMessage;
    }

    public String getResponseType(){
        return type;
    }

    public Integer getResponseNumber(){
        return response;
    }

    public String getResponseMessage(){
        return errorMessage;
    }

    // serializzo il messaggio di risposta e lo mando sullo stream out 
    public void sendMessage(Gson gson,PrintWriter out){
        String respMessage = gson.toJson(this);
        out.println(respMessage);
    }

    public String toString() {
        return "{response='" + this.response + "', errorMessage='" + this.errorMessage + "}";
    }
    
}
