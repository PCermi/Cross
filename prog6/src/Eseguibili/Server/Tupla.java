package Eseguibili.Server;

// Classe che rappresenta una coppia di valori contenente una password e lo stato di login dell'utente memorizzato sulla UserMap
public class Tupla{
    public String password;
    public Boolean isLogged;

    public Tupla(String password,Boolean isLogged){
        this.password = password;
        this.isLogged = isLogged;
    }

    public void setLogged(boolean isLogged){
        this.isLogged = isLogged;
    }

    public boolean getLogged() {
        return this.isLogged;
    }

    public void setPassword(String newPsw){
        this.password = newPsw;
    }

    public String getPassword(){
        return this.password;
    }

    public String toString() {
        return "{password='" + this.password + "',isLogged='" + this.isLogged + "}";
    }
}
