package Eseguibili.Client;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/* Classe responsabile della gestione della stampa dei messaggi in modo asincrono. Utilizza una BlockingQueue per gestire i messaggi e un thread dedicato per la stampa. */

public class Printer{
    private final BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();
    private final Thread printerThread;
    private volatile boolean readyForPrompt = true;
    
    // Costruttore che inizializza e avvia il thread dedicato alla stampa.
    public Printer(){
        printerThread = new Thread(() -> {
            try{
                while(!Thread.currentThread().isInterrupted()){
                    // Estrazione del messaggio dalla coda e stampa
                    String message = messageQueue.take();
                    System.out.println(message);
                    
                    // Dopo aver stampato il messaggio, si verifica se mostrare il prompt
                    if(readyForPrompt){
                        System.out.print("> ");
                        System.out.flush();
                    }
                }
            } catch(InterruptedException e){
                Thread.currentThread().interrupt();
            }
        });
        printerThread.setDaemon(true);
        printerThread.start();
    }

    // Metodo per aggiungere messaggi alla coda
    public void printMessage(String message){
        try{
            messageQueue.put(message);
        } catch(InterruptedException e){
            Thread.currentThread().interrupt();
        }
    }

    // Metodo per indicare che si attende l'input utente: stampa '>'
    public void promptUser(){
        readyForPrompt = true;
        if(messageQueue.isEmpty()){
            System.out.print("> ");
            System.out.flush();
        }
    }
    
    // Metodo per indicare che l'input utente è stato ricevuto
    public void inputReceived(){
        readyForPrompt = false;
    }
}
