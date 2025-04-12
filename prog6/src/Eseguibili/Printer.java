package Eseguibili;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Printer{
    private final BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();
    private final Thread printerThread;
    private volatile boolean readyForPrompt = true;
    
    public Printer(){
        // Thread dedicato alla stampa dei messaggi
        printerThread = new Thread(() -> {
            try {
                while(!Thread.currentThread().isInterrupted()){
                    String message = messageQueue.take();
                    System.out.println(message);
                    
                    // Dopo aver stampato un messaggio, verifica se mostrare il prompt
                    if(readyForPrompt){
                        System.out.print("> ");
                        System.out.flush(); // Assicura che il prompt venga visualizzato subito
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

    // Metodo per indicare che stiamo per leggere l'input utente
    public void promptUser(){
        readyForPrompt = true;
        // Assicurati che venga visualizzato il prompt
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
