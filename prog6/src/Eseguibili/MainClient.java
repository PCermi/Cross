package Eseguibili;

import java.io.*;
import java.util.*;
import java.net.*;
import com.google.gson.Gson;

import GsonClasses.*;

public class MainClient {
    //socket e stream
    public static final String configFile = "../Configurazione/client.properties";
    public static String hostname;
    public static int TCPport;
    public static int UDPport;
    private static Socket TCPsocket;
    private static BufferedReader in;
    private static PrintWriter out;
    private static Scanner scanner = new Scanner(System.in);
    private static Gson gson = new Gson();
    private static GsonMessage<Values> mes;

    //flags
    private static boolean udpMessage = false; //flag per mandare il messaggio UDP del login una sola volta
    
    
    //variabili
    private static final String[] validCommands = {
        "register\\s*\\(\\s*[a-zA-Z0-9]+\\s*,\\s*.*?\\s*\\)",
        "updateCredentials\\s*\\(\\s*[a-zA-Z0-9]+\\s*,\\s*.*?\\s*,\\s*.*?\\s*\\)",
        "login\\s*\\(\\s*[a-zA-Z0-9]+\\s*,\\s*.*?\\s*\\)",
        "logout\\s*\\(\\s*\\)",
        "insertMarketOrder\\s*\\(\\s*[a-zA-Z]+\\s*,\\s*\\d+\\s*\\)",
        "insertLimitOrder\\s*\\(\\s*[a-zA-Z]+\\s*,\\s*\\d+\\s*,\\s*\\d+(\\.\\d+)?\\s*\\)",
        "insertStopOrder\\s*\\(\\s*[a-zA-Z]+\\s*,\\s*\\d+\\s*,\\s*\\d+(\\.\\d+)?\\s*\\)",
        "cancelOrder\\s*\\(\\s*\\d+\\s*\\)",
        "getPriceHistory\\s*\\(\\s*\\d+\\s*\\)",
        "help"
    };
    private static String helpMessage = "- register(username, password)\n" +
                "- updateCredentials(username, currentPassword, newPassword)\n" +
                "- login(username, password)\n" + 
                "- logout()\n" + 
                "- insertLimitOrder(type, size, limitPrice)\n" +
                "- insertMarketOrder(type, size)\n" + 
                "- insertStopOrder(type, size, stopPrice)\n" + 
                "- cancelOrder(orderID)\n" + 
                "- getPriceHistory(month)\n" + 
                "- help - Show this help message";
    private static String userName;
    private static String password;
    private static String message;

    public static class SharedData{
        public volatile boolean isLogged = false;
        public volatile boolean isClosed = false;
        public volatile boolean loginError = false;
        public volatile int UDPport = 0;
    }

    public static void main(String[] args) throws Exception{
        SharedData sharedData = new SharedData();

        readConfig(); //leggo le variabili dal file di configurazione

        //creo il thread per stampare sulla CLI
        Printer printer = new Printer();

        try(DatagramSocket UDPsocket = new DatagramSocket(0)){

            //apro il socket TCP per connettermi al server
            TCPsocket = new Socket(hostname,TCPport);

            // apro lo stream per ricevere/mandare i messaggi dal server: associo un wrapper BufferedReader/PrintWriter perchè devo leggere/scrivere caratteri (non bytes)
            in = new BufferedReader(new InputStreamReader(TCPsocket.getInputStream()));
            out = new PrintWriter(TCPsocket.getOutputStream(),true);
    
            //creo il thread per ricevere i messaggi TCP
            Thread receiver = new Thread (new ReceiverClient(TCPsocket,in,printer,sharedData));
            receiver.start();
            
            System.out.println("Welcome, here's what you can do\n" + helpMessage);

            sharedData.isLogged = false;
            
            while(true){

                try{
                    //leggo da riga di comando e suddivido il comando letto inserendo ogni parola in una cella di command
                    String input = scanner.nextLine();
                    System.out.println("input: " + input);
                    printer.inputReceived(); // Indica che l'input è stato ricevuto

                    if (isValidCommand(input)){
                        //divido la stringa
                        String command[] = input.split("[(),\\s]+");
                        
                        //fare lo switch
                        switch(command[0]){
                            
                            case "register":
                                userName = command[1];
                                password = command[2];
                                
                                //creazione file GSON da inviare al server

                                //definisco il messaggio
                                mes = new GsonMessage<>("register", new GsonUser(userName,password));
                                message = gson.toJson(mes); // definisco l'oggetto Gson da mandare

                                //mando al server il messaggio dell'utente sullo stream out
                                out.println(message);
                            break;

                            case "updateCredentials":
                                userName = command[1];
                                String oldPassword = command[2];
                                String newPassword = command[3];

                                //creazione file GSON da inviare al server

                                //definisco il messaggio
                                mes = new GsonMessage<Values>("updateCredentials",new GsonCredentials(userName,oldPassword,newPassword));
                                message = gson.toJson(mes); // definisco l'oggetto Gson da mandare

                                //mando al server il messaggio dell'utente sullo stream out
                                out.println(message);
                            break;

                            case "login":
                                userName = command[1];
                                password = command[2];

                                //creazione file GSON da inviare al server
                                //definisco il messaggio
                                mes = new GsonMessage<>("login", new GsonUser(userName,password));
                                message = gson.toJson(mes); // definisco l'oggetto Gson da mandare

                                //mando al server il messaggio dell'utente sullo stream out
                                out.println(message);

                                //mando al server il messaggio UDP per fargli avere la porta del client
                                while(!udpMessage){
                                    if(sharedData.isLogged == true){
                                        sendUDPmessage(UDPsocket,printer,sharedData);
                                        System.out.println("messaggio UDP mandato");
                                        udpMessage = true;
                                    }
                                    
                                    if(sharedData.loginError == true){
                                        System.out.println("ricevuto messaggio errore");
                                        break;
                                    }
                                }
                            break;

                            case "logout":
                                //creazione file GSON da inviare al server
                                //definisco il messaggio
                                mes = new GsonMessage<>("logout", new Values());
                                message = gson.toJson(mes); // definisco l'oggetto Gson da mandare
                                //mando al server il messaggio dell'utente sullo stream out
                                out.println(message);
                                
                                //quando il receiver mette la variabile a true chiudo il client
                                while(!sharedData.isClosed){}
                                System.exit(0);
                            break;

                            case "insertLimitOrder":
                                String type = command[1].toLowerCase();
                                int size = Integer.parseInt(command[2]);
                                int limitPrice = Integer.parseInt(command[3]);

                                if(limitPrice <= 0 || limitPrice > Math.pow(2, 31)-1){
                                    printer.printMessage("invalid LimitPrice");
                                    printer.promptUser();
                                } else if(size >  Math.pow(2, 31)-1){
                                    printer.printMessage("invalid Size");
                                    printer.promptUser();
                                } else {
                                    if(!sharedData.isLogged){
                                        printer.printMessage("You're not logged");
                                        printer.promptUser();
                                    } else {
                                        //creazione file GSON da inviare al server
                                        mes = new GsonMessage<>("insertLimitOrder", new GsonLimitStopOrder(type, size, limitPrice));
                                        message = gson.toJson(mes); // definisco l'oggetto Gson da mandare

                                        //mando al server il messaggio dell'utente sullo stream out
                                        out.println(message);
                                    }
                                }
                            break;

                            case "insertMarketOrder":
                                type = command[1].toLowerCase();
                                size = Integer.parseInt(command[2]);

                                if(size >  Math.pow(2, 31)-1){
                                    printer.printMessage("invalid Size");
                                    printer.promptUser();
                                } else{
                                    if(!sharedData.isLogged){
                                        printer.printMessage("You're not logged");
                                        printer.promptUser();
                                    } else{
                                        //creazione file GSON da inviare al server
                                        //definisco il messaggio
                                        mes = new GsonMessage<>("insertMarketOrder", new GsonMarketOrder(type, size));
                                        message = gson.toJson(mes); // definisco l'oggetto Gson da mandare

                                        //mando al server il messaggio dell'utente sullo stream out
                                        out.println(message);
                                    }
                                }
                            break;
                            
                            case "insertStopOrder":
                                type = command[1].toLowerCase();
                                size = Integer.parseInt(command[2]);
                                int stopPrice = Integer.parseInt(command[3]);

                                if(stopPrice <= 0 || stopPrice > Math.pow(2, 31)-1){
                                    printer.printMessage("invalid stopPrice");
                                    printer.promptUser();
                                } else if(size >  Math.pow(2, 31)-1){
                                    printer.printMessage("invalid Size");
                                    printer.promptUser();
                                } else {
                                    if(!sharedData.isLogged){
                                        printer.printMessage("You're not logged");
                                        printer.promptUser();
                                    } else{
                                        //creazione file GSON da inviare al server
                                        //definisco il messaggio
                                        mes = new GsonMessage<>("insertStopOrder", new GsonLimitStopOrder(type, size, stopPrice));
                                        message = gson.toJson(mes); // definisco l'oggetto Gson da mandare

                                        //mando al server il messaggio dell'utente sullo stream out
                                        out.println(message);
                                    }  
                                }
                            break;

                            case "cancelOrder":
                                int orderID = Integer.parseInt(command[1]);
                                GsonResponseOrder obj = new GsonResponseOrder();
                                obj.setResponseOrder(orderID);

                                if(!sharedData.isLogged){
                                    printer.printMessage("You're not logged");
                                    printer.promptUser();
                                } else{
                                    //creazione file GSON da inviare al server
                                    //definisco il messaggio
                                    mes = new GsonMessage<>("cancelOrder", obj);
                                    message = gson.toJson(mes); // definisco l'oggetto Gson da mandare

                                    //mando al server il messaggio dell'utente sullo stream out
                                    out.println(message); 
                                }
                            break;

                            case "getPriceHistory":
                                String date = command[1];
                                //month deve essere una stringa di numeri della forma MMYYYY dove MM è il numero del mese e YYYY è l'anno

                                if(date.length() == 6){
                                    int month = Integer.parseInt(date.substring(0, 2));
                                    int year = Integer.parseInt(date.substring(2));

                                    if(month > 0 || month <= 12 || year <= 2025){
                                       
                                        if(!sharedData.isLogged){
                                            printer.printMessage("You're not logged");
                                            printer.promptUser();
                                        } else{
                                            //creazione file GSON da inviare al server
                                            //definisco il messaggio
                                            mes = new GsonMessage<>("getPriceHistory", new GsonAskHistory(date));
                                            message = gson.toJson(mes); // definisco l'oggetto Gson da mandare
        
                                            //mando al server il messaggio dell'utente sullo stream out
                                            out.println(message); 
                                        }

                                    } else{
                                        printer.printMessage("incorrect month or year");
                                        printer.promptUser();
                                    }
                                } else{
                                    printer.printMessage("incorrect date format: use MMYYYY");
                                    printer.promptUser();
                                }
                            break;

                            case "help":
                                printer.printMessage(helpMessage);
                                printer.promptUser();
                            break;
                        }
                    } else{
                        System.out.println("Comando non valido. Riprova.");
                        printer.promptUser();
                    }
                }catch (Exception e){
                    System.err.println("[MAINCLIENT]" + e.getMessage() + e.getClass());
                    printer.promptUser();
                }
                
            }
        }catch(Exception e){
            System.out.println("[MAINCLIENT]: Error " + e.getMessage());
            printer.promptUser();
        }
    }

    public static void sendUDPmessage(DatagramSocket UDPsocket, Printer printer,SharedData sharedData){
        //ogni client apre la connessione UDP e manda un messaggio al server in modo possa estrarre la porta
        try{
            InetAddress address = InetAddress.getByName(hostname);

            // Creo il pacchetto UDP e lo invio al server
            DatagramPacket packet = new DatagramPacket(new byte[1], 1, address, sharedData.UDPport);
            UDPsocket.send(packet);

            //creo il thread per ricevere i messaggi UDP
            Thread UDPreceiver = new Thread (new UDPReceiverClient(UDPsocket,in,printer));
            UDPreceiver.start();

        }catch (IOException e) {
            System.err.println("Errore nell'invio del messaggio UDP: " + e.getMessage());
            printer.promptUser();
        }
    }

    //funzione che verifica la validità dei comandi passati
    public static boolean isValidCommand(String input) {
        for (String pattern : validCommands) {
            if (input.matches(pattern)) {
                return true;
            }
        }
        return false;
    }

    //metodo che legge il file di configurazione del client
    public static void readConfig() throws FileNotFoundException, IOException{
        InputStream input = MainClient.class.getResourceAsStream(configFile);
        Properties prop = new Properties();
        prop.load(input);
        TCPport = Integer.parseInt(prop.getProperty("TCPport"));
        UDPport = Integer.parseInt(prop.getProperty("UDPport"));
        hostname = prop.getProperty("hostname");
        input.close();
    }
}
