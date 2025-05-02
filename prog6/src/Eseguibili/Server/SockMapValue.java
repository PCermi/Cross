package Eseguibili.Server;

import java.net.InetAddress;

/* Classe utilizzata per mantenere la porta e l'indirizzo del cliente caricato nella mappa che tiene traccia di tutti gli utenti attivi. */
public class SockMapValue{
    public int port;
    public InetAddress address;

    public SockMapValue(int port, InetAddress address){
        this.port = port;
        this.address = address;
    }

    public String toString(){
        return "{ port= " + port + ", address= " + address + " }";
    }
    
}
