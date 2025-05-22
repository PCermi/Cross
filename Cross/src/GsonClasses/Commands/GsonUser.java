package GsonClasses.Commands;

import GsonClasses.Values;

// Classe utilizzata per passare al server i dati relativi alle operazioni di registrazione e login
public class GsonUser extends Values{
   public String username;
   public String password;

   public GsonUser(String username, String password) {
      this.username = username;
      this.password = password;
   }

   public void setPassword(String password) {
      this.password = password;
   }

   public void setUsername(String username) {
      this.username = username;
   }

   public String getPassword() {
      return this.password;
   }

   public String getUsername() {
      return this.username;
   }

   public String toString() {
      return "{username= " + this.username + ", password= " + this.password + "}";
   }
}
