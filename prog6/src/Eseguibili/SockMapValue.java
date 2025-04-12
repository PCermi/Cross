package Eseguibili;

import java.net.InetAddress;

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
