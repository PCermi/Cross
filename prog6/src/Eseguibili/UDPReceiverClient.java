package Eseguibili;

import java.io.BufferedReader;
import java.net.*;
import com.google.gson.Gson;
import GsonClasses.GsonTrade;

public class UDPReceiverClient implements Runnable{
    public DatagramSocket socket;
    public BufferedReader in;
    public Printer printer;

    public UDPReceiverClient(DatagramSocket servSock,BufferedReader in, Printer printer){
        this.socket = servSock;
        this.in = in;
        this.printer = printer;
    }
    
    public void run(){
        while(true){
            try{
                //creo il pacchetto in cui viene caricata il messaggio del server
                byte[] receiveData = new byte[1024];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                
                // Attendo la ricezione di un pacchetto
                socket.receive(receivePacket);
                
                // Converto i dati ricevuti in una stringa
                String jsonString = new String(receivePacket.getData(), 0, receivePacket.getLength());

                // Deserializzao la stringa JSON in un oggetto GsonTrade
                Gson gson = new Gson();
                GsonTrade receivedTrade = gson.fromJson(jsonString, GsonTrade.class);

                // Estraggo i valori
                int orderID = receivedTrade.getOrderID();
                String type = receivedTrade.getType();
                String orderType = receivedTrade.getOrderType();
                int size = receivedTrade.getSize();
                int price = receivedTrade.getPrice();

                // Stampo la stringa di notifica
                if(size == 0 && price == 0){
                    printer.printMessage("Your " + orderType + " order with ID "+ orderID + " of type " + type + " has been processed but has failed");
                } else{
                    printer.printMessage("Your " + orderType + " order with ID "+ orderID + " of type " + type + " has been processed with size " + size + " and price " + price);
                }

            } catch(Exception e){
                printer.printMessage("[UDPRECEIVER]: Error: " + e.getMessage() + "\nCause: %s" + e.getCause());
                break;
            }
        }
    }
}
