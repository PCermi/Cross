package Eseguibili;
import java.io.*;
import java.net.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import Eseguibili.MainClient.SharedData;


public class ReceiverClient implements Runnable{
    public Socket serverSock;
    public BufferedReader in;
    public Printer printer;
    public SharedData sharedData;

    public ReceiverClient(Socket servSock,BufferedReader in, Printer printer, SharedData sharedData){
        this.serverSock = servSock;
        this.in = in;
        this.printer = printer;
        this.sharedData = sharedData;
    }

    public void run(){
        while(true){
            try{

                //aspetto un messaggio dal client
                String line = in.readLine();
                //leggo l'oggetto e lo parso ad un JsonObject
                JsonObject obj = JsonParser.parseString(line).getAsJsonObject();
                
                //Estraggo i valori
                if((obj.get("type")) != null){ // ho ricevuto un messaggio di tipo GsonResponse
                    String type = obj.get("type").getAsString();
                    String errorMessage = obj.get("errorMessage").getAsString();
                    switch(type){
                        case "register":
                            if(errorMessage.equals("OK"))
                                printer.printMessage("[RECEIVER]: Registration completed successfully");
                            else
                                printer.printMessage("[RECEIVER]: " + errorMessage);
                        break;

                        case "login":
                            if(errorMessage.equals("OK")){
                                sharedData.isLogged = true;
                                sharedData.loginError = false;
                                printer.printMessage("[RECEIVER]: Login completed successfully");
                            } else{
                                sharedData.loginError = true;
                                printer.printMessage("[RECEIVER]: " + errorMessage);
                            }
                        break;

                        case "updateCredentials":
                            if(errorMessage.equals("OK"))
                                printer.printMessage("[RECEIVER]: Your credentials have been successfully updated.");
                            else
                                printer.printMessage("[RECEIVER]: " + errorMessage);
                        break;

                        case "logout":
                            if(errorMessage.equals("OK"))
                                System.out.println("[RECEIVER]: Logout completed. Thank you for using our service");
                            else
                                System.out.println(errorMessage);
                            
                            sharedData.isClosed = true;
                            System.exit(0);
                        break;

                        case "cancelOrder":
                            if(errorMessage.equals("OK"))
                                printer.printMessage("[RECEIVER]: Cancellation completed for this order");
                            else
                                printer.printMessage("[RECEIVER]: Cancellation not available for this order");
                        break;

                        case "getPriceHistory":
                            printer.printMessage(errorMessage);
                        break;

                        case "UDP":
                            int response = obj.get("response").getAsInt();
                            sharedData.UDPport = response;
                        break;

                        case "disconnection":
                            System.out.println(errorMessage);
                            sharedData.isClosed = true;
                            System.exit(0);
                        break;
                    }
                    printer.promptUser(); // Mostro il prompt per il comando successivo

                } else if((obj.get("orderID")) != null){
                    // ho ricevuto un messaggio di tipo GsonResponseOrder

                    int orderID = obj.get("orderID").getAsInt();
                    if(orderID != -1)
                        printer.printMessage("[RECEIVER]: your order ID is: " + orderID);
                    else
                        printer.printMessage("[RECEIVER]: Ops! Something went wrong");
                    printer.promptUser();
                }

            }catch (Exception e){
                printer.printMessage("[RECEIVER]: Error: %s\n" + e.getMessage() + "Cause: %s" + e.getCause());
                break;
            }
        }
    }
    
}
