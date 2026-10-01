package seneca.gatekeeper;

import java.io.File;
import java.io.FileWriter;
import java.io.OutputStreamWriter;
import java.rmi.Naming;
import java.rmi.RMISecurityManager;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import org.w3c.dom.Node;
import seneca.compute.Compute;
import seneca.core.SpecMLConstants;
import seneca.core.SpecMLGenerator;
import seneca.core.SpecMLReader;

public class GateKeeper extends UnicastRemoteObject implements GateKeeperInterface, SpecMLConstants {
   private String version = "1.0";
   private Vector servers = null;
   public boolean verbose = true;
   public Timer timer = null;
   static final int YOUNGEST = 1;
   static final int OLDEST = 2;

   public GateKeeper() throws RemoteException {
      this.servers = new Vector();

      try {
         this.rollIn();
      } catch (Exception var2) {
         if (this.verbose) {
            System.out.println("There was a problem handling potential checkpoint files at startup.");
            var2.printStackTrace();
         }
      }

      this.timer = new Timer();
      this.timer.scheduleAtFixedRate(new GateKeeper.RollOutTask(), 60000L, 60000L);
      this.timer.scheduleAtFixedRate(new GateKeeper.CleanUpTask(), 5000L, 5000L);
   }

   public static void main(String[] var0) {
      if (System.getSecurityManager() == null) {
         System.setSecurityManager(new RMISecurityManager());
      }

      try {
         GateKeeper var1 = new GateKeeper();
         Naming.rebind("GateKeeper", var1);

         for (int var2 = 0; var2 < var0.length; var2++) {
            if (var0[var2].equals("--verbose")) {
               var1.verbose = true;
            }
         }

         if (var1.verbose) {
            System.out.println("This is " + var1);
            System.out.println("Version " + var1.getVersion());
         }
      } catch (Exception var3) {
         System.err.println("GateKeeper exception: " + var3.getMessage());
         var3.printStackTrace();
      }
   }

   public boolean register(String var1) {
      if (this.verbose) {
         System.out.println("Attempt to register from " + var1);
      }

      try {
         this.servers.addElement(this.checkRegistration(var1));
      } catch (ServerAllreadyRegisteredException var4) {
         if (this.verbose) {
            System.out.println("Server already registered.");
         }

         return true;
      } catch (Exception var5) {
         return false;
      }

      if (this.verbose) {
         System.out.println("Registration successfull. Server data are: ");
      }

      if (this.verbose) {
         System.out.println(this.servers.lastElement());
      }

      return true;
   }

   public Vector getHosts(int var1) {
      Object var2 = null;
      if (var1 < 1) {
         return null;
      } else {
         Vector var3 = new Vector();
         boolean var4 = false;

         for (int var5 = 0; var5 < this.servers.size(); var5++) {
            var2 = (ServerInfo)this.servers.elementAt(var5);
            if (this.isAlive(((ServerInfo)var2).getHostname())) {
               var3.addElement(var2);
            }

            if (var3.size() == var1) {
               break;
            }
         }

         return var3;
      }
   }

   private void rollOut() throws RollOutFailedException {
      File var1 = null;
      File var2 = null;
      File var3 = null;
      OutputStreamWriter var4 = null;

      try {
         var1 = new File("gatekeeper.checkpoint.1");
      } catch (Exception var13) {
      }

      try {
         var2 = new File("gatekeeper.checkpoint.2");
      } catch (Exception var12) {
      }

      if (!var1.exists()) {
         var3 = new File("gatekeeper.checkpoint.1");
      } else if (!var2.exists()) {
         var3 = new File("gatekeeper.checkpoint.2");
      } else if (var1.lastModified() > var2.lastModified()) {
         var3 = new File("gatekeeper.checkpoint.2");
      } else if (var2.lastModified() > var1.lastModified()) {
         var3 = new File("gatekeeper.checkpoint.1");
      } else if (var2.lastModified() == var1.lastModified()) {
         var3 = new File("gatekeeper.checkpoint.1");
      }

      try {
         var4 = new FileWriter(var3);
      } catch (Exception var11) {
         var11.printStackTrace();
         throw new RollOutFailedException("RollOut failed. Could not create file " + var3);
      }

      StringBuffer var5 = new StringBuffer();
      new SpecMLGenerator();
      int var7 = 0;
      var5.append("<?xml version=\"1.0\" ?>\n");
      var5.append("<gatekeepercheckpointfile>\n");
      var7++;

      for (int var8 = 0; var8 < this.servers.size(); var8++) {
         var5.append(SpecMLGenerator.convert((ServerInfo)this.servers.elementAt(var8), var7));
      }

      var7--;
      var5.append("</gatekeepercheckpointfile>\n");

      try {
         var4.write(var5.toString());
         var4.flush();
         var4.close();
      } catch (Exception var10) {
         throw new RollOutFailedException("RollOut failed.\n" + var10);
      }
   }

   private void rollIn() throws NoCheckPointFileException {
      Object var1 = null;
      Object var2 = null;
      Object var3 = null;
      Node var4 = null;
      File var5 = this.getCheckpointFileByTime(1);
      var1 = SpecMLReader.openDocument(var5);
      if (this.verbose) {
         System.out.println("rollIn() -> document: " + var1);
      }

      var2 = SpecMLReader.getDocStart((Node)var1, "gatekeepercheckpointfile");
      if (this.verbose) {
         System.out.println("rollIn() -> first document node: " + var2);
      }

      var3 = null;
      var4 = null;

      for (int var6 = 0; var6 < var2.getChildNodes().getLength(); var6++) {
         var4 = var2.getChildNodes().item(var6);
         if (SpecMLConstants.typeName[var4.getNodeType()].equals("Element")) {
            var3 = var4.getNodeName();
            if (var3.toLowerCase().equals("serverinfo")) {
               this.servers.addElement(SpecMLReader.readServerInfo(var4));
            }
         }
      }
   }

   private File getCheckpointFileByTime(int var1) throws NoCheckPointFileException {
      File var2 = null;
      File var3 = null;
      File var4 = null;
      if (this.verbose) {
         if (var1 == 1) {
            System.out.println("getCheckpointFileByTime() -> Youngest file requested");
         }

         if (var1 == 2) {
            System.out.println("getCheckpointFileByTime() -> Oldest file requested");
         }
      }

      try {
         var3 = new File("gatekeeper.checkpoint.1");
      } catch (Exception var7) {
         if (this.verbose) {
            System.out.println("getCheckpointFileByTime() -> gatekeeper.checkpoint.1 does not exist");
         }
      }

      try {
         var4 = new File("gatekeeper.checkpoint.2");
      } catch (Exception var6) {
         if (this.verbose) {
            System.out.println("getCheckpointFileByTime() -> gatekeeper.checkpoint.2 does not exist");
         }
      }

      if (!var3.exists() && !var4.exists()) {
         throw new NoCheckPointFileException("No checkpoint file found");
      } else {
         if (!var3.exists() && var4.exists()) {
            var2 = var4;
         }

         if (var3.exists() && !var4.exists()) {
            var2 = var3;
         }

         if (var3.exists() && var4.exists()) {
            if (this.verbose) {
               System.out.println("getCheckpointFileByTime() -> f1.lastModified() = " + var3.lastModified());
               System.out.println("getCheckpointFileByTime() -> f2.lastModified() = " + var4.lastModified());
            }

            if (var3.lastModified() == var4.lastModified()) {
               System.out.println("getCheckpointFileByTime() -> rightFile = f1");
               var2 = var3;
            } else if (var1 == 1 && var3.lastModified() < var4.lastModified()) {
               System.out.println("getCheckpointFileByTime() -> rightFile = f2");
               var2 = var4;
            } else if (var1 == 2 && var3.lastModified() < var4.lastModified()) {
               System.out.println("getCheckpointFileByTime() -> rightFile = f1");
               var2 = var3;
            } else if (var1 == 1 && var3.lastModified() > var4.lastModified()) {
               System.out.println("getCheckpointFileByTime() -> rightFile = f1");
               var2 = var3;
            } else if (var1 == 2 && var3.lastModified() > var4.lastModified()) {
               System.out.println("getCheckpointFileByTime() -> rightFile = f1");
               var2 = var4;
            }
         }

         return var2;
      }
   }

   private ServerInfo checkRegistration(String var1) throws InvalidRegistrationException, ServerBusyException, ServerAllreadyRegisteredException {
      long var2 = 0L;
      long var4 = ServerInfo.UNDEFINED;
      long var6 = ServerInfo.UNDEFINED;
      long var8 = System.currentTimeMillis();

      try {
         String var10 = "//" + var1 + "/StoIcGentServer";
         System.out.println("Recontacting " + var10);
         Compute var11 = (Compute)Naming.lookup(var10);
         if (!var11.willingToManage()) {
            throw new InvalidRegistrationException("Server process busy");
         }

         System.out.println(var10 + " is willing to manage.");
         var2 = System.currentTimeMillis();
         var4 = var2 - var8;
         var6 = var11.getSpeed();
      } catch (Exception var12) {
         System.out.println(var12);
         throw new InvalidRegistrationException("Unable to establish connection to registering server process");
      }

      for (int var16 = 0; var16 < this.servers.size(); var16++) {
         if (((ServerInfo)this.servers.elementAt(var16)).getHostname().equals(var1)) {
            throw new ServerAllreadyRegisteredException("Server " + var1 + " is already registered");
         }
      }

      return new ServerInfo(var1, var6, var4);
   }

   private int getIdleHostCount() {
      int var1 = 0;
      ServerInfo var2 = null;

      for (int var3 = 0; var3 < this.servers.size(); var3++) {
         var2 = (ServerInfo)this.servers.elementAt(var3);

         try {
            String var4 = "//" + var2.getHostname() + "/StoIcGentServer";
            Compute var5 = (Compute)Naming.lookup(var4);
            if (var5.willingToManage()) {
               var1++;
            }
         } catch (Exception var6) {
         }
      }

      return var1;
   }

   private boolean isAlive(String var1) {
      try {
         String var2 = "//" + var1 + "/StoIcGentServer";
         Compute var3 = (Compute)Naming.lookup(var2);
         return var3.willingToManage() ? true : var3.getTaskStatus() != null;
      } catch (Exception var4) {
         return false;
      }
   }

   public String getHTMLStatusReport() {
      StringBuffer var1 = new StringBuffer();
      var1.append("<b>SENECA Gatekeeper Statusreport generated on ");
      var1.append(new Date().toString() + "</b>");
      var1.append("<p>Number of registered hosts: " + this.servers.size() + "</p>");
      var1.append("<p>Number of idle hosts: " + this.getIdleHostCount() + "</p>");
      return var1.toString();
   }

   public String getFullServerList() {
      Object var1 = null;
      StringBuffer var2 = new StringBuffer();
      var2.append(this.getHTMLStatusReport());

      for (int var3 = 0; var3 < this.servers.size(); var3++) {
         var2.append("<p>" + (ServerInfo)this.servers.elementAt(var3) + "<p>");
      }

      return var2.toString();
   }

   public String getVersion() {
      return this.version;
   }

   public void setVersion(String var1) {
      this.version = var1;
   }

   class CleanUpTask extends TimerTask {
      int position = 0;

      public void run() {
         ServerInfo var1 = null;
         if (this.position < GateKeeper.this.servers.size()) {
            var1 = (ServerInfo)GateKeeper.this.servers.elementAt(this.position);
            if (!GateKeeper.this.isAlive(var1.getHostname())) {
               var1.setUnsuccessfulContactCount(var1.getUnsuccessfulContactCount() + 1);
               System.out.println(var1.getHostname() + " is not responding for the " + var1.getUnsuccessfulContactCount() + ". time.");
               if (var1.getUnsuccessfulContactCount() > 3) {
                  GateKeeper.this.servers.removeElementAt(this.position);
                  System.out.println("Removing " + var1.getHostname());
               }

               this.position++;
            } else {
               var1.setUnsuccessfulContactCount(0);
            }
         } else {
            this.position = 0;
         }
      }
   }

   class RollOutTask extends TimerTask {
      public void run() {
         try {
            GateKeeper.this.rollOut();
         } catch (Exception var2) {
            if (GateKeeper.this.verbose) {
               System.out.println("RollOut failed.\n" + var2);
            }
         }
      }
   }
}
