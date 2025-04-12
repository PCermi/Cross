package Eseguibili;

import java.io.BufferedReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
                printer.printMessage("Ricevuto pacchetto da "+ receivePacket.getAddress() + " " + receivePacket.getPort());
                
                // Converto i dati ricevuti in una stringa
                String jsonString = new String(
                        receivePacket.getData(), 
                        0, 
                        receivePacket.getLength());

                // Estraggo il JsonObject dal pacchetto e stampo il messaggio
                //JsonObject obj = JsonParser.parseString(jsonString).getAsJsonObject();
                
                printer.printMessage("Messaggio UDP ricevuto: " + jsonString);

                //Estraggo i valori
                //if((obj.get("type")) != null){}

            } catch(Exception e){
                printer.printMessage("[UDPRECEIVER]: Error: " + e.getMessage() + "\nCause: %s" + e.getCause());
                break;
            }
        }
    }
}
