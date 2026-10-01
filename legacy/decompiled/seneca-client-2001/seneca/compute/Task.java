package seneca.compute;

import compchem.Molecule;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Vector;

public interface Task extends Remote {
   void execute() throws RemoteException;

   void setMolecule(Molecule var1) throws RemoteException;

   void setJudges(Vector var1) throws RemoteException;

   void setDatasetName(String var1) throws RemoteException;

   void setSASchedules(Vector var1) throws RemoteException;

   Object getStatus() throws RemoteException;

   Object getResult() throws RemoteException;

   String getVersion() throws RemoteException;

   void end() throws RemoteException;

   void reset() throws RemoteException;

   long getSpeed() throws RemoteException;
}
