/* GateKeeper.java
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

package seneca.gatekeeper;

import java.rmi.*;
import java.rmi.server.*;
import java.net.*;
import java.util.*;

import java.io.*;

import seneca.compute.*;
import seneca.core.*;

import org.xml.sax.*;
import org.xml.sax.helpers.ParserFactory;
import com.sun.xml.parser.Resolver;

import javax.xml.parsers.DocumentBuilderFactory;  
import javax.xml.parsers.FactoryConfigurationError;  
import javax.xml.parsers.ParserConfigurationException;

 
import javax.xml.parsers.DocumentBuilder;  

import org.w3c.dom.Document;
import org.w3c.dom.DOMException;




/**
 *
 **/
public class GateKeeper extends UnicastRemoteObject implements GateKeeperInterface, SpecMLConstants
{
	private String version = "1.0";
	private Vector servers = null;
	public boolean verbose = true;
	public Timer timer = null;
	
	static final int YOUNGEST = 1, OLDEST = 2;
	
	
	public GateKeeper() throws java.rmi.RemoteException
    {
        super();
		servers = new Vector();
		
		try
		{
			rollIn();
		}
		catch(Exception exc)
		{
			if (verbose)
			{
				System.out.println("There was a problem handling potential checkpoint files at startup.");
				exc.printStackTrace();
			}
			
			
		}
		timer = new Timer();
		timer.scheduleAtFixedRate((TimerTask)new RollOutTask(), (long)60000, (long)60000);
		timer.scheduleAtFixedRate((TimerTask)new CleanUpTask(), (long)5000, (long)5000);
		
    }
	
    /**
     *
     **/
    public static void main(String args[])
    {
        if (System.getSecurityManager() == null) 
        {
            System.setSecurityManager(new RMISecurityManager());
        }
        try 
        {
            GateKeeper gateKeeper = new GateKeeper();
            Naming.rebind("GateKeeper", gateKeeper);
            for (int f = 0; f < args.length; f ++)
            {
            	if (args[f].equals("--verbose")) gateKeeper.verbose = true;
            }

    		if (gateKeeper.verbose)		
    		{
                System.out.println("This is " + gateKeeper);
                System.out.println("Version " + gateKeeper.getVersion());
            }
         } 
         catch (Exception e) 
         {
              System.err.println("GateKeeper exception: " + e.getMessage());
              e.printStackTrace();
         }
       }



    public boolean register(String hostname)
    {
		if (verbose) System.out.println("Attempt to register from " + hostname);
		try
		{
			servers.addElement(checkRegistration(hostname));
		}
		catch(ServerAllreadyRegisteredException sare)
		{
			if (verbose) System.out.println("Server already registered." );

			return true;
		}
		catch(Exception exc)
		{
			return false;
		}
		if (verbose) System.out.println("Registration successfull. Server data are: ");
		if (verbose) System.out.println(servers.lastElement());
		return true;
    }
	

	/**
	 * Returns an array of available servers. The amount is
	 * indicated by the parameter number. The actual amount 
	 * returned might be less if less are available.
	 *
	 * @param   number  Amount of servers to be returned.
	 * @return An array of available servers    
	 */
    public Vector getHosts(int number)
    {
		ServerInfo serverInfo = null;
		if (number < 1) return null;
		Vector serverInfos = new Vector();
		boolean isAlive = false;
		for (int f = 0; f < servers.size(); f++)
		{
			/* Check for Server Availablility */
			serverInfo = (ServerInfo) servers.elementAt(f);
			if (isAlive(serverInfo.getHostname()))
			{
				serverInfos.addElement(serverInfo);
			}
			if (serverInfos.size() == number) break;
		}
		return serverInfos;	
    }
	
    private void rollOut() throws RollOutFailedException
    {
		File f1 = null, f2 = null, recentFile = null;
		FileWriter fw = null;
		try
		{
			f1 = new File("gatekeeper.checkpoint.1");
		}
		catch (Exception exc)
		{
				
		}
		
		try
		{
			f2 = new File("gatekeeper.checkpoint.2");
		}
		catch (Exception exc)
		{
	
		}
		if (!f1.exists()) recentFile = new File("gatekeeper.checkpoint.1");
		else if (!f2.exists()) recentFile = new File("gatekeeper.checkpoint.2");
		else if (f1.lastModified() > f2.lastModified()) recentFile = new File("gatekeeper.checkpoint.2");
		else if (f2.lastModified() > f1.lastModified()) recentFile = new File("gatekeeper.checkpoint.1");
		else if (f2.lastModified() == f1.lastModified()) recentFile = new File("gatekeeper.checkpoint.1");		
		try
		{
			fw = new FileWriter(recentFile);
		}
		catch(Exception exc)
		{
			exc.printStackTrace();
			throw new RollOutFailedException("RollOut failed. Could not create file " + recentFile);
		}
		StringBuffer xml = new StringBuffer();
		SpecMLGenerator sg = new SpecMLGenerator();
		int indentLevel = 0;
		xml.append("<?xml version=\"1.0\" ?>\n");
		xml.append("<gatekeepercheckpointfile>\n");
		indentLevel ++;
		for (int f = 0; f < servers.size(); f++)
		{
			xml.append(SpecMLGenerator.convert((ServerInfo)servers.elementAt(f), indentLevel));
		}
		indentLevel --;
		xml.append("</gatekeepercheckpointfile>\n");
		try
		{
			fw.write(xml.toString());
			fw.flush();
			fw.close();
		}
		catch(Exception exc)
		{
			throw new RollOutFailedException("RollOut failed.\n" + exc);
		}
	}
	
	/* Gatekeeper keeps two Checkpoint files, gatekeeper.checkpoint.1 and
	 * gatekeeper.checkpoint.2, written in half hour intervals. 
	 * So, in this method, it tries to read in the latest of the two. 
	 */
    private void rollIn() throws NoCheckPointFileException
    {
	    org.w3c.dom.Document document = null;
	    org.w3c.dom.Node node = null;
	    String nodeName = null;
	    org.w3c.dom.Node newNode = null;
		File mostRecentFile = getCheckpointFileByTime(YOUNGEST);
		document = SpecMLReader.openDocument(mostRecentFile);
		if (verbose) System.out.println("rollIn() -> document: " + document);				
		node = SpecMLReader.getDocStart(document, "gatekeepercheckpointfile");						
		if (verbose) System.out.println("rollIn() -> first document node: " + node);		
		nodeName = null;
		newNode = null;
		for (int f = 0; f < node.getChildNodes().getLength(); f++)
		{
			newNode = node.getChildNodes().item(f);
			if (typeName[newNode.getNodeType()].equals("Element"))
			{
				nodeName = newNode.getNodeName();
				if (nodeName.toLowerCase().equals("serverinfo"))				
				{
					servers.addElement(SpecMLReader.readServerInfo(newNode));
				}				

			}
		}
    }
	

	private File getCheckpointFileByTime(int time) throws NoCheckPointFileException
	{
		File rightFile = null;
		File f1 = null; 
		File f2 = null;
		
		if (verbose)
		{
			if (time == YOUNGEST)
			{
				System.out.println("getCheckpointFileByTime() -> Youngest file requested");
			}
			if (time == OLDEST)
			{
				System.out.println("getCheckpointFileByTime() -> Oldest file requested");
			}
		}
		try
		{
			f1 = new File("gatekeeper.checkpoint.1");
		}
		catch(Exception exc)
		{
			if (verbose) System.out.println("getCheckpointFileByTime() -> gatekeeper.checkpoint.1 does not exist");
			/* No need for any action here, we'll check for "null" later */
		}
		try
		{
			f2 = new File("gatekeeper.checkpoint.2");
		}
		catch(Exception exc)
		{
			if (verbose) System.out.println("getCheckpointFileByTime() -> gatekeeper.checkpoint.2 does not exist");
			/* No need for any action here, we'll check for "null" later */
		}
		if (!f1.exists() && !f2.exists()) throw new NoCheckPointFileException("No checkpoint file found");
		if (!f1.exists() && f2.exists()) rightFile = f2;
		if (f1.exists() && !f2.exists()) rightFile = f1;		
		if (f1.exists() && f2.exists())
		{
			
			if (verbose)
			{
				System.out.println("getCheckpointFileByTime() -> f1.lastModified() = " + f1.lastModified());
				System.out.println("getCheckpointFileByTime() -> f2.lastModified() = " + f2.lastModified());			
			}
			/* Gotta check the file dates now */
			if (f1.lastModified() == f2.lastModified())
			{
				System.out.println("getCheckpointFileByTime() -> rightFile = f1");
				rightFile = f1;
			}
			else if (time == YOUNGEST && f1.lastModified() < f2.lastModified())
			{
				System.out.println("getCheckpointFileByTime() -> rightFile = f2");
			 	rightFile = f2;
			}
			else if (time == OLDEST && f1.lastModified() < f2.lastModified())
			{
				System.out.println("getCheckpointFileByTime() -> rightFile = f1");
				rightFile = f1;
			}
			else if (time == YOUNGEST && f1.lastModified() > f2.lastModified())
			{
				System.out.println("getCheckpointFileByTime() -> rightFile = f1");
				rightFile = f1;			
			}
			else if (time == OLDEST && f1.lastModified() > f2.lastModified())
			{
				 System.out.println("getCheckpointFileByTime() -> rightFile = f1");
				 rightFile = f2;
			}
		}
		return rightFile;	
	}
	
	private ServerInfo checkRegistration(String hostname) throws InvalidRegistrationException, ServerBusyException, ServerAllreadyRegisteredException
	{
		long stop = 0;
		long responseTime = ServerInfo.UNDEFINED;				
	    long speed = ServerInfo.UNDEFINED;
		long start = System.currentTimeMillis();
		try{
			String namingLookup = "//" + hostname + "/StoIcGentServer";
			System.out.println("Recontacting " + namingLookup);
			Compute stoicGent = (Compute)Naming.lookup(namingLookup);
			if (stoicGent.willingToManage())
			{
				System.out.println(namingLookup + " is willing to manage.");
				stop = System.currentTimeMillis();
				responseTime = stop - start;				
				speed = stoicGent.getSpeed();	
			}
			else throw new InvalidRegistrationException("Server process busy");
		}
		catch(Exception exc)
		{
			System.out.println(exc);
			throw new InvalidRegistrationException("Unable to establish connection to registering server process");
		}
		for (int f = 0; f < servers.size(); f++)
		{
			if (((ServerInfo)servers.elementAt(f)).getHostname().equals(hostname))
			{
				throw new ServerAllreadyRegisteredException("Server " + hostname + " is already registered");
			}
		}
		ServerInfo serverInfo = new ServerInfo(hostname, speed, responseTime);
		return serverInfo;
	}
	
	private int getIdleHostCount()
	{
		int count = 0;
		ServerInfo serverInfo = null;
		for (int f = 0; f < servers.size(); f++)
		{
			serverInfo = (ServerInfo) servers.elementAt(f);
			try{
				String namingLookup = "//" + serverInfo.getHostname() + "/StoIcGentServer";
				Compute stoicGent = (Compute)Naming.lookup(namingLookup);
				if (stoicGent.willingToManage())
				{
					count ++;
				}
			}
			catch(Exception exc)
			{
			}
		}
		return count;		
	}
	

	private boolean isAlive(String hostname)
	{
		try{
			String namingLookup = "//" + hostname + "/StoIcGentServer";
			//System.out.println("Recontacting " + namingLookup);
			Compute stoicGent = (Compute)Naming.lookup(namingLookup);		
			if (stoicGent.willingToManage())
			{	
				return true;
			}
			if (stoicGent.getTaskStatus() != null)
			{
				return true;
			}
		}
		catch(Exception exc)
		{
			//System.out.println(exc);
			return false;
		}
		// We should never get here :-)
		return false;
	}


	public String getHTMLStatusReport()
	{
		StringBuffer s = new StringBuffer();
		s.append("<b>SENECA Gatekeeper Statusreport generated on ");
		s.append(new java.util.Date().toString() + "</b>");
		s.append("<p>Number of registered hosts: " + servers.size() + "</p>");
		s.append("<p>Number of idle hosts: " + getIdleHostCount() + "</p>");
		return s.toString();
	}


	public String getFullServerList()
	{
		ServerInfo si = null;
		StringBuffer s = new StringBuffer();
		s.append(getHTMLStatusReport());
		for (int f = 0; f < servers.size();f++)
		{
			s.append("<p>" + (ServerInfo)servers.elementAt(f) + "<p>");		
		}
		return s.toString();
		
	}


	public String getVersion()
	{
	return this.version;
	}

	public void setVersion(String version)
	{
	this.version = version;
	}

	class RollOutTask extends TimerTask
	{
			
		public void run()
		{
			try
			{
				rollOut();
			}
			catch(Exception exc)
			{
				if (verbose) System.out.println("RollOut failed.\n" + exc);
			}
		}
	}


	class CleanUpTask extends TimerTask
	{
		int position = 0;	
			
		public void run()
		{
			ServerInfo si = null;
			if (position < servers.size())
			{
				si = (ServerInfo)servers.elementAt(position);
				//System.out.println("Checking if " + si.getHostname() + ", entry number " + position + " is alive:");
				if (!isAlive(si.getHostname()))
				{
					si.setUnsuccessfulContactCount(si.getUnsuccessfulContactCount() + 1);
					System.out.println(si.getHostname() + " is not responding for the " + si.getUnsuccessfulContactCount() + ". time.");					
					if (si.getUnsuccessfulContactCount() > 3)
					{
						servers.removeElementAt(position);
						System.out.println("Removing " + si.getHostname());											
					}
					position ++;
				}
				else
				{
					si.setUnsuccessfulContactCount(0);
					//System.out.println(si.getHostname() + " has responded.");										
				}
			}
			else
			{
				position = 0;
			}
		}
	}

}