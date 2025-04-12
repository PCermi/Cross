package OrderBook;
import java.time.Instant;

// Oggetto che descrive i campi della lista di utenti della askMap e della bidMap
public class UserBook{
   public int size;
   public String username;
   public int orderID;
   public long timestamp;

   public UserBook(int size, String username, int orderID){
      this.size = size;
      this.username = username;
      this.orderID = orderID;
      this.timestamp = Instant.now().getEpochSecond();
   }

   public int compareSize(int size){
      return Integer.compare(this.size, size);
   }

   public boolean equals(UserBook user){
      return orderID == user.orderID && size == user.size && username.equals(user.username);
   }

   public String toString() {
      return "UserBook{" +
              "orderID='" + orderID + '\'' +
              ", username='" + username + '\'' +
              ", size=" + size +
              '}';
  }
}
