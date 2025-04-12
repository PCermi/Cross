package Eseguibili;

public class Tupla implements Comparable<Tupla>{
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

    public int compareTo(Tupla t){
        return 0;
    }

    public String toString() {
        return "{password='" + this.password + "',isLogged='" + this.isLogged + "}";
    }
}
