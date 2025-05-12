package Eseguibili.Main;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.net.*;
import com.google.gson.Gson;

import Eseguibili.Client.Printer;
import Eseguibili.Client.ReceiverClient;
import Eseguibili.Client.UDPReceiverClient;
import GsonClasses.*;
import GsonClasses.Commands.GsonAskHistory;
import GsonClasses.Commands.GsonCredentials;
import GsonClasses.Commands.GsonLimitStopOrder;
import GsonClasses.Commands.GsonMarketOrder;
import GsonClasses.Commands.GsonUser;
import GsonClasses.Responses.GsonResponseOrder;

/* Classe principale del client che implementa un'interfaccia a riga di comando per interagire con il server. Gestisce le connessioni TCP e UDP, l'invio di comandi e la ricezione di risposte. */
             

public class MainClient {
    // Configurazione del client
    public static final String configFile = "client.properties";
    public static String hostname;          // Nome host del server
    public static int TCPport;              // Porta TCP del server
    
    // Socket e stream
    private static Socket TCPsocket;
    private static BufferedReader in;
    private static PrintWriter out;
    private static Thread UDPreceiver = null;   // Thread per ricevere messaggi UDP
    private static Thread receiver = null;      // Thread per ricevere messaggi TCP
    private static Scanner scanner = new Scanner(System.in);
    private static Gson gson = new Gson();
    private static GsonMessage<Values> mes;

    // Flags
    private static boolean udpMessage = false; // Flag per mandare il messaggio UDP del login una sola volta
    
    
    // Comandi validi e messaggio di aiuto
    private static final String[] validCommands = {
        "register\\s*\\(\\s*[a-zA-Z0-9]+\\s*,\\s*\\S(?:.*\\S)?\\s*\\)",
        "updateCredentials\\s*\\(\\s*[a-zA-Z0-9]+\\s*,\\s*\\S(?:.*\\S)?\\s*,\\s*\\S(?:.*\\S)?\\s*\\)",
        "login\\s*\\(\\s*[a-zA-Z0-9]+\\s*,\\s*\\S(?:.*\\S)?\\s*\\)",
        "logout\\s*\\(\\s*\\)",
        "insertMarketOrder\\s*\\(\\s*[a-zA-Z]+\\s*,\\s*\\d+\\s*\\)",
        "insertLimitOrder\\s*\\(\\s*[a-zA-Z]+\\s*,\\s*\\d+\\s*,\\s*\\d+(\\.\\d+)?\\s*\\)",
        "insertStopOrder\\s*\\(\\s*[a-zA-Z]+\\s*,\\s*\\d+\\s*,\\s*\\d+(\\.\\d+)?\\s*\\)",
        "cancelOrder\\s*\\(\\s*\\d+\\s*\\)",
        "getPriceHistory\\s*\\(\\s*\\d+\\s*\\)",
        "showOrderBook\\s*\\(\\s*\\)",
        "showStopOrders\\s*\\(\\s*\\)",
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
                "- showOrderBook()\n" + 
                "- showStopOrders()\n" + 
                "- help - Show this help message";
    
    // Dati utente    
    private static String userName;
    private static String password;
    private static String message;

    // Classe per condividere dati tra il thread principale e i thread di ricezione
    public static class SharedData{
        public AtomicBoolean isLogged = new AtomicBoolean(false);          // Flag per indicare se l'utente è loggato
        public AtomicBoolean isClosed = new AtomicBoolean(false); ;        // Flag per indicare se la connessione è chiusa
        public AtomicBoolean loginError = new AtomicBoolean(false); ;      // Flag per indicare errori di login
        public AtomicBoolean isShuttingDown = new AtomicBoolean(false);    // Flag atomico per gestire la chiusura
        public volatile int UDPport = 0;                                                // Porta UDP assegnata dal server
    }

    public static void main(String[] args) throws Exception{
        SharedData sharedData = new SharedData();

        readConfig(); // Lettura dei parametri di configurazione

        // Creazione del thread per stampare sulla CLI
        Printer printer = new Printer();

        try(DatagramSocket UDPsocket = new DatagramSocket()){

            // Apertura socket TCP per connettersi al server
            TCPsocket = new Socket(hostname,TCPport);

            // Apertura degli stream per ricevere/mandare i messaggi dal/al server
            in = new BufferedReader(new InputStreamReader(TCPsocket.getInputStream()));
            out = new PrintWriter(TCPsocket.getOutputStream(),true);
    
            // Creazione del thread per ricevere i messaggi TCP
            receiver = new Thread (new ReceiverClient(TCPsocket,in,printer,sharedData));
            receiver.start();

            // Viene associato un handler per gestire la terminazione con ctrl-C
            Runtime.getRuntime().addShutdownHook(new Thread(){
                public void run(){
                    if(sharedData.isShuttingDown.compareAndSet(false, true)){
                        //creazione file GSON da inviare al server
                        mes = new GsonMessage<>("logout", new Values()); // Creazione messaggio
                        message = gson.toJson(mes); // Creazione dell'oggetto Gson da mandare
                        out.println(message); // Invio del messaggio sullo stream
                        
                        closeConnection(printer);
                    }
                }
            });
            
            System.out.println("Welcome, here's what you can do\n" + helpMessage);

            while(!sharedData.isShuttingDown.get()){
                try{
                    if (System.in.available() <= 0 && !scanner.hasNextLine()) {
                        Thread.sleep(100);
                        continue;
                    }
                    
                    // Si legge da riga di comando
                    String input = scanner.nextLine();
                    if (input == null || input.isEmpty()){
                        continue;
                    }
                    printer.inputReceived(); // Indica che l'input è stato ricevuto

                    if (isValidCommand(input)){
                        // La stringa viene suddivisa
                        String command[] = input.split("[(),\\s]+");
                        
                        switch(command[0]){
                            case "register":
                                userName = command[1];
                                password = command[2];
                                
                                // Creazione file GSON da inviare al server
                                // Creazione messaggio
                                mes = new GsonMessage<>("register", new GsonUser(userName,password));
                                message = gson.toJson(mes); // Creazione dell'oggetto Gson da mandare
                                out.println(message); // Invio del messaggio sullo stream
                            break;

                            case "updateCredentials":
                                userName = command[1];
                                String oldPassword = command[2];
                                String newPassword = command[3];

                                //creazione file GSON da inviare al server
                                mes = new GsonMessage<Values>("updateCredentials",new GsonCredentials(userName,oldPassword,newPassword));  // Creazione messaggio
                                message = gson.toJson(mes); // Creazione dell'oggetto Gson da mandare
                                out.println(message); // Invio del messaggio sullo stream
                            break;

                            case "login":
                                userName = command[1];
                                password = command[2];

                                // Creazione file GSON da inviare al server
                                // Creazione messaggio
                                mes = new GsonMessage<>("login", new GsonUser(userName,password));
                                message = gson.toJson(mes); // Creazione dell'oggetto Gson da mandare
                                out.println(message); // Invio del messaggio sullo stream

                                // Invio del messaggio UDP per comunicare al server la porta del client
                                while(!udpMessage){
                                    // Il messaggio viene mandato solo se l'utente si è loggato con succcesso
                                    if(sharedData.isLogged.get() == true){
                                        sendUDPmessage(UDPsocket,printer,sharedData);
                                        udpMessage = true;
                                    }
                                    
                                    if(sharedData.loginError.get() == true){ //ricevuto messaggio di errore
                                        break;
                                    }
                                }
                            break;

                            case "logout":
                                if(sharedData.isShuttingDown.compareAndSet(false, true)){
                                    // Creazione file GSON da inviare al server
                                    // Creazione messaggio
                                    mes = new GsonMessage<>("logout", new Values());
                                    message = gson.toJson(mes); // Creazione dell'oggetto Gson da mandare
                                    out.println(message); // Invio del messaggio sullo stream
                                    
                                    // Quando il receiver mette la variabile a true chiudo il client
                                    while(!sharedData.isClosed.get()){Thread.sleep(100);}

                                    closeConnection(printer);
                                    return;
                                }
                            break;

                            case "insertLimitOrder":
                                try{
                                    String type = command[1].toLowerCase();
                                    int size = Integer.parseInt(command[2]);
                                    int limitPrice = Integer.parseInt(command[3]);
                                    if(type.equals("ask") || type.equals("bid")){

                                        if(!sharedData.isLogged.get()){
                                            printer.printMessage("You're not logged");
                                            printer.promptUser();
                                        } else {
                                            // Creazione file GSON da inviare al server
                                            mes = new GsonMessage<>("insertLimitOrder", new GsonLimitStopOrder(type, size, limitPrice)); // Creazione messaggio
                                            message = gson.toJson(mes); // Creazione dell'oggetto Gson da mandare
                                            out.println(message); // Invio del messaggio sullo stream
                                        }
                                    } else{
                                        printer.printMessage("Unknown order type: use 'ask' or 'bid' ");
                                        printer.promptUser();
                                    }
                                } catch (NumberFormatException e){
                                    printer.printMessage("invalid size or LimitPrice");
                                    printer.promptUser();
                                }
                            break;

                            case "insertMarketOrder":
                                try{
                                    String type = command[1].toLowerCase();
                                    int size = Integer.parseInt(command[2]);

                                    if(type.equals("ask") || type.equals("bid")){
                                        if(!sharedData.isLogged.get()){
                                            printer.printMessage("You're not logged");
                                            printer.promptUser();
                                        } else{
                                            // Creazione file GSON da inviare al server
                                            mes = new GsonMessage<>("insertMarketOrder", new GsonMarketOrder(type, size)); // Creazione messaggio
                                            message = gson.toJson(mes); // Creazione dell'oggetto Gson da mandare
                                            out.println(message); // Invio del messaggio sullo stream
                                        }
                                    } else{
                                        printer.printMessage("Unknown order type: use 'ask' or 'bid' ");
                                        printer.promptUser();
                                    }
                                    
                                } catch (NumberFormatException e){
                                    printer.printMessage("invalid size");
                                    printer.promptUser();
                                }
                            break;
                            
                            case "insertStopOrder":
                                try{
                                    String type = command[1].toLowerCase();
                                    int size = Integer.parseInt(command[2]);
                                    int stopPrice = Integer.parseInt(command[3]);

                                    if(type.equals("ask") || type.equals("bid")){
                                        if(!sharedData.isLogged.get()){
                                            printer.printMessage("You're not logged");
                                            printer.promptUser();
                                        } else{
                                            // Creazione file GSON da inviare al server
                                            mes = new GsonMessage<>("insertStopOrder", new GsonLimitStopOrder(type, size, stopPrice)); // Creazione messaggio
                                            message = gson.toJson(mes); // Creazione dell'oggetto Gson da mandare
                                            out.println(message); // Invio del messaggio sullo stream
                                        }  
                                    } else{
                                        printer.printMessage("Unknown order type: use 'ask' or 'bid' ");
                                        printer.promptUser();
                                    }
                                } catch (NumberFormatException e){
                                    printer.printMessage("invalid size or StopPrice");
                                    printer.promptUser();
                                }
                            break;

                            case "cancelOrder":
                                int orderID = Integer.parseInt(command[1]);
                                GsonResponseOrder obj = new GsonResponseOrder();
                                obj.setResponseOrder(orderID);

                                if(!sharedData.isLogged.get()){
                                    printer.printMessage("You're not logged");
                                    printer.promptUser();
                                } else{
                                    // Creazione file GSON da inviare al server
                                    mes = new GsonMessage<>("cancelOrder", obj); // Creazione messaggio
                                    message = gson.toJson(mes); // Creazione dell'oggetto Gson da mandare
                                    out.println(message); // Invio del messaggio sullo stream
                                }
                            break;

                            case "getPriceHistory":
                                String date = command[1];

                                // Validazione della data passata
                                if(date.length() == 6){
                                    int month = Integer.parseInt(date.substring(0, 2));
                                    int year = Integer.parseInt(date.substring(2));

                                    if(month > 0 && month <= 12 && year <= 2025){
                                       
                                        if(!sharedData.isLogged.get()){
                                            printer.printMessage("You're not logged");
                                            printer.promptUser();
                                        } else{
                                            // Creazione file GSON da inviare al server
                                            mes = new GsonMessage<>("getPriceHistory", new GsonAskHistory(date)); // Creazione messaggio
                                            message = gson.toJson(mes); // Creazione dell'oggetto Gson da mandare
                                            out.println(message); // Invio del messaggio sullo stream
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

                            
                            case "showOrderBook":
                                if(!sharedData.isLogged.get()){
                                    printer.printMessage("You're not logged");
                                    printer.promptUser();
                                } else{
                                    // Creazione file GSON da inviare al server
                                    mes = new GsonMessage<>("showOrderBook", new Values()); // Creazione messaggio
                                    message = gson.toJson(mes); // Creazione dell'oggetto Gson da mandare
                                    out.println(message); // Invio del messaggio sullo stream
                                }
                            break;

                            case "showStopOrders":
                                if(!sharedData.isLogged.get()){
                                    printer.printMessage("You're not logged");
                                    printer.promptUser();
                                } else{
                                    // Creazione file GSON da inviare al server
                                    mes = new GsonMessage<>("showStopOrders", new Values()); // Creazione messaggio
                                    message = gson.toJson(mes); // Creazione dell'oggetto Gson da mandare
                                    out.println(message); // Invio del messaggio sullo stream
                                }
                            break;
                            
                            
                            case "help":
                                printer.printMessage(helpMessage);
                                printer.promptUser();
                            break;
                        }
                    } else{
                        System.out.println("Invalid Command: try again.");
                        printer.promptUser();
                    }
                } catch (NoSuchElementException e){
                    // Eccezione lanciata quando scanner.nextLine() è interrotto da CTRL+C
                    if(sharedData.isShuttingDown.get()){
                        // Se si sta già chiudendo questa eccezione viene ignorata
                        break;
                    }
                    if(!sharedData.isClosed.get()){
                        System.err.println("[MAINCLIENT] Input interrotto: " + e.getMessage());
                        printer.promptUser();
                    }
                } catch (Exception e){
                    if(sharedData.isShuttingDown.get() || sharedData.isClosed.get()){
                        // Se si sta già chiudendo questa eccezione viene ignorata
                        break;
                    }
                    System.err.println("[MAINCLIENT] Error internal catch: " + e.getMessage() + " Cause: " + e.getCause());
                    printer.promptUser();
                }
                
            }
        } catch (SocketException e){
            if(!sharedData.isShuttingDown.get() && !sharedData.isClosed.get()){
                System.err.println("[MAINCLIENT] Socket error: " + e.getMessage());
            }
            printer.promptUser();
        } catch(Exception e){
            if(!sharedData.isShuttingDown.get() && !sharedData.isClosed.get()){
                System.out.println("[MAINCLIENT] Error external catch " + e.getMessage() + " Cause: " + e.getCause());
            }
            printer.promptUser();
        }
        
        if(!sharedData.isShuttingDown.get()){
            System.out.println("Closing Connection");
            sharedData.isShuttingDown.set(true);
            closeConnection(printer);
        }
        
    }

    // Metodo che chiude tutti i socket e i thread in modo sicuro
    public static void closeConnection(Printer printer){
        try{
            // Si interrompe il thread receiver TCP
            if(receiver != null && receiver.isAlive()){
                receiver.interrupt();
                // Si da al thread un po' di tempo per terminare in modo pulito
                try { receiver.join(500); } catch (InterruptedException ignored){}
            }

            // Si interrompe il thread receiver UDP
            if(UDPreceiver != null && UDPreceiver.isAlive()){
                UDPreceiver.interrupt();
                // Si da al thread un po' di tempo per terminare in modo pulito
                try { UDPreceiver.join(500); } catch (InterruptedException ignored){}
            }
            
            // Vengono chiusi il socket e gli stream
            if (TCPsocket != null && !TCPsocket.isClosed()) TCPsocket.close();
            if (in != null) in.close();
            if (out != null){
                out.flush();
                out.close();
            }

        } catch (Exception e) {
            System.err.println("[MAINCLIENT] Error while closing connection: " + e.getMessage());
        }
    }

    // Metodo che invia un messaggio UDP al server per stabilire la comunicazione asincrona
    public static void sendUDPmessage(DatagramSocket UDPsocket, Printer printer,SharedData sharedData){
        try{
            InetAddress address = InetAddress.getByName(hostname);

            // Creazione del pacchetto UDP e invio al server
            DatagramPacket packet = new DatagramPacket(new byte[1], 1, address, sharedData.UDPport);
            UDPsocket.send(packet);

            // Creazione del thread per ricevere i messaggi UDP
            UDPreceiver = new Thread (new UDPReceiverClient(UDPsocket,in,printer));
            UDPreceiver.start();

        }catch (IOException e){
            System.err.println("Error while sending the UDP message: " + e.getMessage());
            printer.promptUser();
        }
    }

    // Metodo che verifica la validità dei comandi passati usando espressioni regolari
    public static boolean isValidCommand(String input) {
        for (String pattern : validCommands) {
            if (input.matches(pattern)) {
                return true;
            }
        }
        return false;
    }

    // Metodo che legge il file di configurazione del client
    public static void readConfig() throws FileNotFoundException, IOException{
        InputStream input = new FileInputStream(configFile);
        Properties prop = new Properties();
        prop.load(input);
        TCPport = Integer.parseInt(prop.getProperty("TCPport"));
        hostname = prop.getProperty("hostname");
        input.close();
    }
}
