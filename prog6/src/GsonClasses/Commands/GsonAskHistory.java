package GsonClasses.Commands;

import GsonClasses.Values;

// Classe utilizzata per passare al server la data di cui l'utente ha richiesto lo storico
public class GsonAskHistory extends Values{
    public String date;

    public GsonAskHistory(String date){
        this.date = date;
    }

    public String getDate(){
        return this.date;
    }
    
}
