package seneca.compute;

import compchem.Molecule;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Vector;

public interface Compute extends Remote {
   void executeTask() throws RemoteException;

   void setMolecule(Molecule var1) throws RemoteException;

   void setJudges(Vector var1) throws RemoteException;

   void setDatasetName(String var1) throws RemoteException;

   void setSASchedules(Vector var1) throws RemoteException;

   Object getTaskStatus() throws RemoteException;

   Object getTaskResult() throws RemoteException;

   String getTaskVersion() throws RemoteException;

   void endTask() throws RemoteException;

   void resetTask() throws RemoteException;

   boolean willingToManage() throws RemoteException;

   String getHostName() throws RemoteException;

   String getID() throws RemoteException;

   void setID(String var1) throws RemoteException;

   String getVersion() throws RemoteException;

   long getSpeed() throws RemoteException;
}
