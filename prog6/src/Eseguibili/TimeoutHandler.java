package Eseguibili;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentLinkedQueue;

import Eseguibili.Worker.SharedState;
import OrderBook.StopValue;

public class TimeoutHandler implements Runnable{
    private static final int TIMEOUT_MINUTES = 1;
    private static final long TIMEOUT_MILLIS = TIMEOUT_MINUTES * 60 * 1000;
    public ConcurrentLinkedQueue<StopValue> stopOrders;
    public String user = null;
    private final SharedState sharedState;
        

    //costruttore
    public TimeoutHandler(SharedState sharedState){
        this.sharedState = sharedState;
        System.out.println("[HANDLER] costruttore con timestamp");
        printTime(sharedState.lastActivity);
    }

    public void setTimestamp(long timestamp){
        sharedState.lastActivity = timestamp;
        System.out.printf("[HANDLER] orario aggiornato: ");
        printTime(sharedState.lastActivity);
    }

    public void setUsername(String user){
        this.user = user;
    }

    public void printTime(long millis){
        LocalTime orario = Instant.ofEpochMilli(millis)
                .atZone(ZoneId.systemDefault())
                .toLocalTime();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");

        System.out.println(orario.format(formatter));
    }

    public void updateStopOrders(ConcurrentLinkedQueue<StopValue> stopOrders){
        System.out.println("StopOrders updated");
        this.stopOrders = new ConcurrentLinkedQueue<>(stopOrders);
    }

    public void run(){
        System.out.println("[HANDLER] handler has been activated");
        while(sharedState.runningHandler){
            try{  
                System.out.printf("[HANDLER] checking timeout of " + user + " with time ");
                printTime(sharedState.lastActivity);

                // verifico se il client è inattivo
                long currentTime = System.currentTimeMillis();
                if(currentTime - sharedState.lastActivity > TIMEOUT_MILLIS){
                    //l'utente ha superato il timeout
                    if(user == null){ //utente non loggato
                        System.out.println("[HANDLER] Client inattivo per piu' di " + TIMEOUT_MINUTES + " minuti. Chiusura connessione.");
                        sharedState.activeUser = false;
                        break;
                    } else{
                        // utente loggato, controllo se è nella lista degli stopOrders
                        boolean contains = false;
                        if(stopOrders != null){
                            for(StopValue f_user : stopOrders){
                                if(f_user.username.equals(user)){
                                    contains = true;
                                    break;
                                }
                            }
                        }
                        if(!contains){
                            System.out.println("[HANDLER] " + user + " inattivo per piu' di " + TIMEOUT_MINUTES + " minuti. Chiusura connessione.");
                            sharedState.activeUser = false;
                            break;
                        } else{
                            System.out.println("[HANDLER] " + user + " ha piazzato uno stopOrder. Non chiudo la connessione.");
                        }
                    }
                }

                // faccio il controllo ogni 10 secondi
                Thread.sleep(10000);

            } catch (Exception e){
                System.err.println("[TIMEOUTHANDLER]: " + e.getMessage() + " " + e.getCause());
            }
        }
        System.out.println("[HANDLER] Termino Handler");
    } 
}
