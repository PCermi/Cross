package Eseguibili.Server;

/* Classe che rappresenta le statistiche giornaliere inviate al client che ha richiesto lo storico dei prezzi. Mantiene traccia dei prezzi di apertura, chiusura, massimo e minimo, insieme ai timestamp della prima e ultima transazione. */

public class DailyParameters{
    public final String date;
    public int openPrice;
    public int highPrice;
    public int lowPrice;
    public int closePrice;
    public long firstTimestamp;
    public long lastTimestamp;

    public DailyParameters(String date, int price, long timestamp){
        this.date = date;
        this.openPrice = price;
        this.highPrice = price;
        this.lowPrice = price;
        this.closePrice = price;
        this.firstTimestamp = timestamp;
        this.lastTimestamp = timestamp;
    }

    // Metodo che Aggiorna i parametri giornalieri con una nuova transazione
    public void updatePrices(int price, long timestamp){
        // Aggiorno high e low
        if(price > highPrice){
            highPrice = price;
        }
        if(price < lowPrice){
            lowPrice = price;
        }

        // Aggiorno open se questo trade è più vecchio del primo conosciuto
        if(timestamp < firstTimestamp){
            firstTimestamp = timestamp;
            openPrice = price;
        }

        // Aggiorno close se questo trade è più recente dell'ultimo conosciuto
        if(timestamp > lastTimestamp){
            lastTimestamp = timestamp;
            closePrice = price;
        }
    }
}
