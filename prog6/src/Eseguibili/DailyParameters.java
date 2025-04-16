package Eseguibili;

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
