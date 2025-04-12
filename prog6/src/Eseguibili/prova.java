package Eseguibili;

import java.util.concurrent.ConcurrentLinkedQueue;

import OrderBook.UserBook;

public class prova {
    public static void main(String[] args){
        ConcurrentLinkedQueue<UserBook> askList = new ConcurrentLinkedQueue<UserBook>();
        ConcurrentLinkedQueue<UserBook> bidList = new ConcurrentLinkedQueue<UserBook>();

        askList.add(new UserBook(3, "gino", 7));
        askList.add(new UserBook(4, "sara", 15));

        bidList.add(new UserBook(1, "gianni", 5));
        bidList.add(new UserBook(2, "paola", 5));

        checkLists(askList,bidList);

        System.out.println("askList: ");
        for(UserBook askUser : askList){
            System.out.printf(askUser.toString() + " ; ");
            System.out.println("");
        }
            
        System.out.println("bidList: ");
        for(UserBook bidUser : bidList){
            System.out.printf(bidUser.toString() + " ; ");
            System.out.println("");
        }
    }

    public static void checkLists(ConcurrentLinkedQueue<UserBook> askList, ConcurrentLinkedQueue<UserBook> bidList){
        while (!askList.isEmpty() && !bidList.isEmpty()) {
            UserBook askUser = askList.peek();
            UserBook bidUser = bidList.peek();
    
            if (askUser.size < bidUser.size){ // bid vuole comprare più di quando offre ask
                // askUser soddisfatto completamente
                bidUser.size -= askUser.size;
                askList.poll(); // rimuovo askUser
    
            } else if (askUser.size > bidUser.size){ // bid vuole comprare meno di quanto offre ask
                // bidUser soddisfatto completamente
                askUser.size -= bidUser.size;
                bidList.poll(); // rimuovo bidUser
    
            } else{
                // size uguali, entrambi soddisfatti
                askList.poll();
                bidList.poll();
            }
        }
    }

    public static ConcurrentLinkedQueue<UserBook> modifySize(ConcurrentLinkedQueue<UserBook> userList, UserBook modifyUser,int newSize){
        // copio la vecchia lista su una nuova  dopo aver aggiornato la size
        ConcurrentLinkedQueue<UserBook> result = new ConcurrentLinkedQueue<UserBook>();

        for(UserBook user : userList){
            if(modifyUser.username.equals(user.username))
                result.add(new UserBook(user.orderID, user.username, newSize));
            else
                result.add(new UserBook(user.orderID, user.username, user.size));
        }
        return result;
    }
    
}
