package GsonClasses;

// Classe utilizzata per definire la struttura dei messaggi che il client invia al server
public class GsonMessage <T extends Values>{
    public String operation;
    public T values;

    public GsonMessage(String operation, T values){
        this.operation = operation;
        this.values = values;
    }
    
    public String toString(){
        return "{ operation = " + this.operation + ", values = " + this.values.toString() + " }";
    }
}