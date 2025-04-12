package GsonClasses;

public class GsonMarketOrder extends Values{
    public String type;
    public int size;

    public GsonMarketOrder(String type, int size){
        this.type = type;
        this.size = size;
    }

    public void setType(String type){
        this.type = type;
    }
  
    public void setsize(int size){
        this.size = size;
    }
  
    public String gettype(){
        return this.type;
    }
  
    public int getsize(){
        return this.size;
    }

    public String toString() {
        return "{type ='" + this.type + "', size ='" + this.size + "}";
    }
}
