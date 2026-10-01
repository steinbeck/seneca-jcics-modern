package seneca.gatekeeper;

import java.io.Serializable;

public class ServerInfo implements Serializable {
   public static long UNDEFINED = -1L;
   private String hostname = null;
   private long responseTime = UNDEFINED;
   private long speed = UNDEFINED;
   public boolean verbose = false;
   public int unsuccessfulContactCount = 0;

   public ServerInfo(String var1, long var2) {
      this.hostname = var1;
      this.speed = var2;
   }

   public ServerInfo(String var1, long var2, long var4) {
      this.hostname = var1;
      this.speed = var2;
      this.responseTime = var4;
   }

   public String getHostname() {
      return this.hostname;
   }

   public void setHostname(String var1) {
      this.hostname = var1;
   }

   public long getResponseTime() {
      return this.responseTime;
   }

   public void setResponseTime(long var1) {
      this.responseTime = var1;
   }

   public long getSpeed() {
      return this.speed;
   }

   public void setSpeed(long var1) {
      this.speed = var1;
   }

   public String toString() {
      String var1 = new String("ServerInfo for host " + this.hostname + ":\n");
      var1 = var1 + "Speed: " + this.speed + "\n";
      var1 = var1 + "Speedfactor: " + 70L / this.speed + " (please consult docs for reference.) \n";
      return var1 + "Responsetime: " + this.responseTime + ". \n";
   }

   public int getUnsuccessfulContactCount() {
      return this.unsuccessfulContactCount;
   }

   public void setUnsuccessfulContactCount(int var1) {
      this.unsuccessfulContactCount = var1;
   }
}
