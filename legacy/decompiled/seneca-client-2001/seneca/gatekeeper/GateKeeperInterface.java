package seneca.gatekeeper;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Vector;

public interface GateKeeperInterface extends Remote {
   boolean register(String var1) throws RemoteException;

   Vector getHosts(int var1) throws RemoteException;

   String getVersion() throws RemoteException;

   String getHTMLStatusReport() throws RemoteException;

   String getFullServerList() throws RemoteException;
}
