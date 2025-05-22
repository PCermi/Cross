package OrderBook;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

// Oggetto che descrive i campi della lista di utenti della askMap e della bidMap
public class UserBook{
   public int size;
   public String username;
   public int orderID;
   public String date;

   public UserBook(int size, String username, int orderID){
      this.size = size;
      this.username = username;
      this.orderID = orderID;
      long time = System.currentTimeMillis();

      LocalDateTime dataOra = Instant.ofEpochMilli(time)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime();

      DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

      this.date = dataOra.format(formatter);
   }

   public int compareSize(int size){
      return Integer.compare(this.size, size);
   }

   public boolean equals(UserBook user){
      return orderID == user.orderID && size == user.size && username.equals(user.username);
   }

   public String toString(){
      return "UserBook{ " + " orderID=" + orderID + ", username=" + username + ", size=" + size + ", date=" + date + " }";
  }
}
