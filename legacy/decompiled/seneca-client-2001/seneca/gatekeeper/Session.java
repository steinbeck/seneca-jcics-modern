package seneca.gatekeeper;

import java.io.Serializable;
import java.util.Vector;

public class Session implements Serializable {
   public static long UNDEFINED = -1L;
   public long timeStarted = UNDEFINED;
   public long timeStopped = UNDEFINED;
   public String owner = null;
   public Vector serverList;
   private String id = null;

   public Session(long var1, String var3) throws InvalidSessionException {
      if (var1 <= 0L) {
         throw new InvalidSessionException("Invalid start time assignment");
      } else if (var3 == null) {
         throw new InvalidSessionException("Invalid owner assignment");
      } else if (var3.equals("")) {
         throw new InvalidSessionException("Invalid owner assignment");
      } else {
         this.timeStarted = var1;
         this.owner = var3;
      }
   }

   public String getID() {
      return new String(this.owner + this.timeStarted);
   }
}
