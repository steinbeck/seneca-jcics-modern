package seneca.client;

import java.net.InetAddress;
import java.rmi.Naming;
import java.util.Vector;
import seneca.compute.Compute;

public class FastResolver extends Thread {
   Vector addresses;
   String hostName;
   public InetAddress inetAddress = null;
   public boolean isDone = false;

   public FastResolver(String var1) {
      super(var1);
   }

   public FastResolver(String var1, Vector var2) {
      this(var1);
      this.hostName = var1;
      this.addresses = var2;
   }

   public void run() {
      this.isDone = false;
      Object var2 = null;
      Object var3 = null;
      Object var4 = null;

      try {
         var4 = InetAddress.getLocalHost().getHostName();
         String var5 = InetAddress.getByName(this.hostName).toString();
         var2 = var5.substring(0, var5.lastIndexOf("/")).trim();
         var3 = var5.substring(var5.lastIndexOf("/") + 1).trim();
         String var1 = "//" + var3 + "/StoIcGentServer";
         Compute var6 = (Compute)Naming.lookup(var1);
         System.out.println("This is FastResolver for hostname " + this.hostName);
         if (var6.willingToManage()) {
            System.out.println(this.hostName + " is willing to manage.");
            this.addresses.addElement(var6);
         }
      } catch (Exception var7) {
         this.isDone = true;
         System.out.println(var7);
      }

      this.isDone = true;
      System.out.println("FastResolver for hostname " + this.hostName + " finnished its job");
   }
}
