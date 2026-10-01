package seneca.engine;

import compchem.Molecule;
import java.net.InetAddress;
import java.rmi.Naming;
import java.rmi.RMISecurityManager;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Properties;
import java.util.Vector;
import org.apache.log4j.Category;
import org.apache.log4j.PropertyConfigurator;
import seneca.compute.Compute;
import seneca.gatekeeper.GateKeeperInterface;
import seneca.structgen.StochasticGenerator;

public class StructureGeneratorServer extends UnicastRemoteObject implements Compute {
   StochasticGenerator stoicGent;
   String id = null;
   String hostName = null;
   String status = null;
   String version = "2.0";
   long speed = -1L;
   boolean debug = true;
   Molecule molecule = null;
   Vector judges = null;
   Vector saSchedules = null;
   String dataSetName = null;
   static Category senecalog;

   public static void main(String[] var0) {
      String var1 = "StoIcGentServer";
      long var3 = System.currentTimeMillis();
      if (System.getSecurityManager() == null) {
         System.setSecurityManager(new RMISecurityManager());
      }

      try {
         StructureGeneratorServer var2 = new StructureGeneratorServer(var1);

         for (int var7 = 0; var7 < var0.length; var7++) {
            if (var0[var7].equals("--debug")) {
               var2.debug = false;
            }

            if (var0[var7].equals("--id")) {
               var2.setID(var0[var7 + 1]);
            }
         }

         Naming.rebind(var1, var2);
         if (var2.debug) {
            System.out.println("Benchmarking this machine");
         }

         long var8 = benchmarker.getBenchmark();
         var2.setSpeed(var8);
         if (var2.debug) {
            System.out.println("Benchmarking done: " + var8);
         }

         if (!var2.registerWithGateKeeper()) {
            if (var2.debug) {
               System.out.println(var2.getIdentMessage());
               System.out.println("Registration with Nmrshiftdb.org failed.");
               System.out.println("Exiting");
            }

            System.exit(1);
         }

         if (var2.debug) {
            System.out.println(var2.getIdentMessage());
            if (var2.getTaskStatus() == null) {
               System.out.println("Server status: No task running");
            } else {
               System.out.println("Server status: " + var2.getTaskStatus());
            }
         }
      } catch (Exception var10) {
         System.err.println("ComputeEngine exception: " + var10.getMessage());
         var10.printStackTrace();
      }
   }

   public StructureGeneratorServer(String var1) throws RemoteException {
      this.id = var1;
      this.status = "idle";
      Properties var2 = new Properties();

      try {
         var2.load(this.getClass().getClassLoader().getResourceAsStream("seneca/engine/logging.server.properties"));
      } catch (Exception var4) {
         var4.printStackTrace();
      }

      PropertyConfigurator.configure(var2);
      senecalog = Category.getInstance(this.getClass().getName() + ".log");
   }

   public void executeTask() throws RemoteException {
      this.stoicGent = new StochasticGenerator(true);
      senecalog.info("New StochasticGenerator created: " + this.stoicGent);
      this.stoicGent.setMolecule(this.molecule);
      senecalog.info("Received molecule: " + this.molecule);
      this.stoicGent.setJudges(this.judges);
      senecalog.info("Received judges: " + this.judges);
      this.stoicGent.setDatasetName(this.dataSetName);
      senecalog.info("Dataset name set to: " + this.dataSetName);
      this.stoicGent.setSASchedules(this.saSchedules);
      senecalog.info("Received Annealing Schedules: " + this.saSchedules);
      senecalog.info("Starting calculation...");
      this.stoicGent.execute();
   }

   public Object getTaskResult() throws RemoteException {
      if (this.debug) {
         System.out.println("Request for taskresult");
      }

      return this.stoicGent.getResult();
   }

   public String getTaskVersion() throws RemoteException {
      return this.stoicGent.getVersion();
   }

   public String getID() throws RemoteException {
      return this.id;
   }

   public void setID(String var1) throws RemoteException {
      this.id = var1;
   }

   public String getHostName() throws RemoteException {
      if (this.hostName == null) {
         try {
            this.hostName = InetAddress.getLocalHost().getHostName();
            if (this.debug) {
               System.out.println("Request for hostname: " + this.hostName);
            }
         } catch (Exception var2) {
            System.err.println(var2.toString());
         }
      }

      return this.hostName;
   }

   public String getHostAddress() throws RemoteException {
      String var1 = null;

      try {
         var1 = InetAddress.getLocalHost().getHostAddress();
      } catch (Exception var3) {
         System.err.println(var3.toString());
      }

      return var1;
   }

   public Object getTaskStatus() throws RemoteException {
      return this.stoicGent != null ? this.stoicGent.getStatus() : null;
   }

   public boolean willingToManage() throws RemoteException {
      return true;
   }

   public void endTask() throws RemoteException {
      this.stoicGent.end();
   }

   public void resetTask() throws RemoteException {
      this.stoicGent = null;
   }

   public String getVersion() throws RemoteException {
      return this.version;
   }

   public long getSpeed() throws RemoteException {
      return this.speed;
   }

   public void setSpeed(long var1) {
      this.speed = var1;
   }

   private boolean registerWithGateKeeper() {
      try {
         String var1 = "//www.nmrshiftdb.org/GateKeeper";
         GateKeeperInterface var2 = (GateKeeperInterface)Naming.lookup(var1);
         if (var2.register(this.getHostName())) {
            return true;
         }

         if (var2.register(this.getHostAddress())) {
            return true;
         }
      } catch (Exception var3) {
         System.out.println(var3);
         var3.printStackTrace();
      }

      return false;
   }

   public void setJudges(Vector var1) throws RemoteException {
      this.judges = var1;
   }

   public void setSASchedules(Vector var1) throws RemoteException {
      this.saSchedules = var1;
   }

   public void setMolecule(Molecule var1) throws RemoteException {
      this.molecule = var1;
   }

   public void setDatasetName(String var1) throws RemoteException {
      this.dataSetName = var1;
   }

   public String getIdentMessage() throws RemoteException {
      String var1 = "This is " + this.id + "\n";

      try {
         var1 = var1 + "Version " + this.getVersion() + "\n";
         var1 = var1 + "running on " + this.getHostName();
      } catch (Exception var3) {
         var1 = var1 + "Can't get identifikation info";
      }

      return var1;
   }
}
