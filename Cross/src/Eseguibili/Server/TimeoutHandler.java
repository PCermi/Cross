package Eseguibili.Server;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentLinkedQueue;

import Eseguibili.Server.Worker.SharedState;
import OrderBook.OrderBook;
import OrderBook.StopValue;

/* Thread che gestisce il timeout dei client connessi al server. Monitora l'attività degli utenti e chiude le connessioni inattive, gestendo le eccezioni per gli utenti che attendono uno o più stop order. */

public class TimeoutHandler implements Runnable{
    private static final int TIMEOUT_MINUTES = 10;
    private static final long TIMEOUT_MILLIS = TIMEOUT_MINUTES * 60 * 1000;
    public String user = null;
    private final SharedState sharedState; // Stato condiviso con il thread Worker
        
    // Costruttore
    public TimeoutHandler(SharedState sharedState){
        this.sharedState = sharedState;
    }

    // Imposta il timestamp dell'ultima attività dell'utente
    public void setTimestamp(long timestamp){
        sharedState.lastActivity = timestamp;
    }

    // Imposta il nome utente associato a questo handler
    public void setUsername(String user){
        this.user = user;
    }

    // Converte e stampa un timestamp in formato orario leggibile.
    public void printTime(long millis){
        LocalTime orario = Instant.ofEpochMilli(millis)
                .atZone(ZoneId.systemDefault())
                .toLocalTime();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");

        System.out.println(orario.format(formatter));
    }

    // Aggiorna la lista degli stop orders nello stato condiviso.
    public void updateStopOrders(ConcurrentLinkedQueue<StopValue> stopOrders){
        if(sharedState.stopOrders == null){
            sharedState.stopOrders = new ConcurrentLinkedQueue<>(stopOrders);
        } else{
            sharedState.stopOrders.clear();
            sharedState.stopOrders.addAll(stopOrders);
        }
    }

    // Sincronizza lo stato corrente con l'OrderBook
    public void syncWithOrderBook(OrderBook orderBook){
        updateStopOrders(orderBook.stopOrders);
    }

    public void run(){
        while(sharedState.runningHandler.get()){
            try{
                // Verifico se il client è inattivo
                long currentTime = System.currentTimeMillis();
                if(currentTime - sharedState.lastActivity > TIMEOUT_MILLIS){
                    // L'utente ha superato il timeout
                    if(user == null){ // Utente non loggato
                        System.out.println("[TIMEOUTHANDLER] Client inactive for more than " + TIMEOUT_MINUTES + " minutes. Closing connection.");
                        sharedState.activeUser.set(false);
                        break;
                    } else{
                        // Utente loggato, si controlla se è nella lista degli stopOrders
                        boolean contains = false;
                        if(sharedState.stopOrders != null){
                            for(StopValue f_user : sharedState.stopOrders){
                                if(f_user.username.equals(user)){
                                    contains = true;
                                    break;
                                }
                            }
                        }
                        if(!contains){
                            System.out.println("[TIMEOUTHANDLER] " + user + " inactive for more than " + TIMEOUT_MINUTES + " minutes. Closing connection.");
                            sharedState.activeUser.set(false);
                            break;
                        } else{
                            System.out.println("[TIMEOUTHANDLER] " + user + " is waiting a stopOrder. Connection remaining open.");
                        }
                    }
                }

                // Controllo eseguito ogni 5 secondi
                Thread.sleep(5000);

            } catch (Exception e){
                System.err.println("[TIMEOUTHANDLER] Error: " + e.getMessage() + " - Cause: " + e.getCause());
            }
        }
        System.out.println("[TIMEOUTHANDLER] Handler of " + user + " Terminated");
    } 
}
