/* StructureGeneratorServer.java
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

package seneca.engine;

import java.rmi.*;
import java.rmi.server.*;
import java.net.*;
import seneca.compute.*;
import seneca.gatekeeper.*;
import seneca.structgen.*;
import seneca.core.*;
import seneca.judges.*;
import compchem.*;
import java.util.*;
import java.io.*;
import org.apache.log4j.*;
/**
 *
 **/
public class StructureGeneratorServer extends UnicastRemoteObject implements Compute
{

	StochasticGenerator stoicGent; 
	String id = null;
	String hostName = null;
	String status = null; 
	String version = "2.0";
	long speed = -1;
	boolean debug = true;
	Molecule molecule = null;
	Vector judges = null;
	Vector saSchedules = null;
	String dataSetName = null;
	
	/** For log4j logging */
	static Category senecalog;
	
	
	
	/**
	 *
	 **/
	public static void main(String args[])
	{
	    String name = "StoIcGentServer";
	    StructureGeneratorServer structGentServ;
	    long startTime = System.currentTimeMillis();
	    long currentTime = startTime;
	    if (System.getSecurityManager() == null) 
	    {
	        System.setSecurityManager(new RMISecurityManager());
	    }
	    try 
	    {
	        structGentServ = new StructureGeneratorServer(name);
	        for (int f = 0; f < args.length; f ++)
	        {
	        	if (args[f].equals("--debug")) structGentServ.debug = false;
				if (args[f].equals("--id")) structGentServ.setID(args[f + 1]);
	        }
	        Naming.rebind(name,structGentServ);
			if (structGentServ.debug) System.out.println("Benchmarking this machine");
			long speed = benchmarker.getBenchmark();
			structGentServ.setSpeed(speed);
			if (structGentServ.debug) System.out.println("Benchmarking done: " + speed);
			if (!structGentServ.registerWithGateKeeper())
			{
				if (structGentServ.debug)
				{
					System.out.println(structGentServ.getIdentMessage());
					System.out.println("Registration with Nmrshiftdb.org failed.");
					System.out.println("Exiting");
				}
				System.exit(1);
				
			}
			if (structGentServ.debug)
			{
				System.out.println(structGentServ.getIdentMessage());
				if (structGentServ.getTaskStatus() == null) System.out.println("Server status: No task running");
				else System.out.println("Server status: " + structGentServ.getTaskStatus());
	        }
	     } 
	     catch (Exception e) 
	     {
	          System.err.println("ComputeEngine exception: " + e.getMessage());
	          e.printStackTrace();
	     }
	    try
	    {
	    }
	    catch(Exception e)
	    {
	        System.out.println("An exception occurred while trying to create Structure Elucidation Server.\n");
	        System.out.println(e.toString());
	    }
	}

    public StructureGeneratorServer(String id) throws java.rmi.RemoteException
    {
        super();
        this.id = id;
		this.status = "idle";
		Properties logprops = new Properties();
		try
		{
			logprops.load(this.getClass().getClassLoader().getResourceAsStream("seneca/engine/logging.server.properties"));
		}
		catch(Exception exc)
		{
			exc.printStackTrace();	
		}
		PropertyConfigurator.configure(logprops);
		senecalog = Category.getInstance(this.getClass().getName() + ".log");		
    }
	
    /** Starts the structure generation process. Initializes any essential array
       and calls recursive bond generator routine */
	   
    public void executeTask() throws java.rmi.RemoteException
	{
		stoicGent = new StochasticGenerator(true);
		senecalog.info("New StochasticGenerator created: " + stoicGent);		
		stoicGent.setMolecule(molecule);
		senecalog.info("Received molecule: " + molecule);		
		stoicGent.setJudges(judges);
		senecalog.info("Received judges: " + judges);		
		stoicGent.setDatasetName(dataSetName);
		senecalog.info("Dataset name set to: " + dataSetName);		
		stoicGent.setSASchedules(saSchedules);
		senecalog.info("Received Annealing Schedules: " + saSchedules);		
		senecalog.info("Starting calculation...");		
		stoicGent.execute();
		
    }


    /** Returns a Vector with the best structures */

    public Object getTaskResult() throws java.rmi.RemoteException
	{
		if (debug) System.out.println("Request for taskresult");
		return stoicGent.getResult();
    }

	public String getTaskVersion() throws java.rmi.RemoteException
	{
		return stoicGent.getVersion();
	}
	
	public String getID() throws java.rmi.RemoteException
	{
		return this.id;
	}

	public void setID(String id) throws java.rmi.RemoteException
	{
		this.id = id;
	}

    /**
     * 
     **/
    public String getHostName() throws java.rmi.RemoteException
    {
		if (hostName == null)
		{
			try
	        {
				hostName = InetAddress.getLocalHost().getHostName();
				if (debug) System.out.println("Request for hostname: " + hostName);
	        }
	        catch(Exception e)
	        {
	            System.err.println(e.toString());
	        }
		}
		
        return hostName;
    }


    /**
     * 
     **/
    public String getHostAddress() throws java.rmi.RemoteException
    {
		String address = null;
			try
	        {
				address = InetAddress.getLocalHost().getHostAddress();
	        }
	        catch(Exception e)
	        {
	            System.err.println(e.toString());
	        }

        return address;
    }

    /**
     *
     **/
    public Object getTaskStatus() throws java.rmi.RemoteException
    {
        if(stoicGent != null)
        {
			return stoicGent.getStatus();
		}
		return null;
    }

    /**
     *
     **/
    public boolean willingToManage() throws java.rmi.RemoteException
    {
        return true;
    }

    /**
     *
     **/
    public void endTask() throws java.rmi.RemoteException
    {
		stoicGent.end();
    }
	
    /**
     *
     **/
    public void resetTask() throws java.rmi.RemoteException
    {
    	stoicGent = null;
    }



    /**
     *
     **/
    public String getVersion() throws java.rmi.RemoteException
    {
    	return version;
    }
	

    /**
     *
     **/
    public long getSpeed() throws java.rmi.RemoteException
    {
    	return speed;	
    }
	
	public void setSpeed(long speed)
	{
		this.speed = speed;
	}
	
	private boolean registerWithGateKeeper()
	{
		try{
			String namingLookup = "//www.nmrshiftdb.org/GateKeeper";
			GateKeeperInterface gateKeeper = (GateKeeperInterface)Naming.lookup(namingLookup);
			if (gateKeeper.register(getHostName()))
			{
				return true;
			}
			else if (gateKeeper.register(getHostAddress()))
			{
				return true;
			}
		}
		catch(Exception exc)
		{
			System.out.println(exc);
			exc.printStackTrace();
		}
		
		return false;
		
	}
	
	public void setJudges(Vector judges)  throws java.rmi.RemoteException
	{
		this.judges = judges;
	}

	public void setSASchedules(Vector saSchedules)  throws java.rmi.RemoteException
	{
		this.saSchedules = saSchedules;
	}
	
	
	public void setMolecule(Molecule mol)  throws java.rmi.RemoteException
	{
		this.molecule = mol;
	}
	
	public void setDatasetName(String name)  throws java.rmi.RemoteException
	{
		this.dataSetName = name;
	}
	
	public String getIdentMessage()  throws java.rmi.RemoteException
	{
		String s = ("This is " + id + "\n");
		try
		{
			s += "Version " + getVersion() + "\n";
			s += "running on " + getHostName();			
		}
		catch(Exception exc)
		{
			s += "Can't get identifikation info";
		}
		return s;
	}



}
