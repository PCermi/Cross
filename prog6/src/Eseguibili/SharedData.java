package Eseguibili;

import java.net.InetAddress;

public class SharedData {
    public volatile static boolean isLogged = false;
    public volatile static boolean isClosed = false;
    public volatile static int UDPport = 0;
}
