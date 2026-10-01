/* Compute.java
 *
 * Copyright (C) 1997, 1998, 1999, 2000  Dr. Christoph Steinbeck
 *
 * Contact: steinbeck@ice.mpg.de
 *
 * This software is published and distributed under artistic license.
 * The intent of this license is to state the conditions under which this Package 
 * may be copied, such that the Copyright Holder maintains some semblance
 * of artistic control over the development of the package, while giving the 
 * users of the package the right to use and distribute the Package in a
 * more-or-less customary fashion, plus the right to make reasonable modifications.
 *
 * THIS PACKAGE IS PROVIDED "AS IS" AND WITHOUT ANY EXPRESS OR IMPLIED WARRANTIES, 
 * INCLUDING, WITHOUT LIMITATION, THE IMPLIED WARRANTIES OF MERCHANTIBILITY AND 
 * FITNESS FOR A PARTICULAR PURPOSE.
 * 
 * The complete text of the license can be found in a file called LICENSE 
 * accompanying this package.
 */

package seneca.compute;

import java.rmi.Remote;
import java.rmi.RemoteException;
import compchem.*;
import java.util.*;


/** 
  * Interface defining the methods of a Compute object 
  **/

public interface Compute extends Remote
{
	public void executeTask() throws RemoteException;

	public void setMolecule(Molecule molecule)  throws RemoteException;;
	public void setJudges(Vector judges) throws RemoteException;;
	public void setDatasetName(String name) throws RemoteException;;
	public void setSASchedules(Vector saSchedules) throws RemoteException;;

	
	public Object getTaskStatus() throws RemoteException;
	public Object getTaskResult() throws RemoteException;
	public String getTaskVersion() throws RemoteException;
	public void endTask() throws RemoteException;
	public void resetTask() throws RemoteException;
	public boolean willingToManage() throws RemoteException;
	public String getHostName() throws RemoteException;
	public String getID() throws RemoteException;
	public void setID(String id) throws RemoteException;	
	public String getVersion() throws RemoteException;	
	public long getSpeed() throws RemoteException;	
}