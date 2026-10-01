/* FastResolverThread.java
 *
 * Copyright (C) 1997, 1998, 1999  Dr. Christoph Steinbeck
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

package seneca.client;

import java.net.*;
import java.lang.Thread;
import java.util.Vector;
import java.rmi.*;
import seneca.structgen.*;
import seneca.compute.*;

public class FastResolver extends Thread
{

	Vector addresses;
	String hostName;
	public InetAddress inetAddress = null;
	public boolean isDone = false;

	public FastResolver(String str)
	{
		super(str);
	}


	public FastResolver(String hostName, Vector addresses)
	{
		this(hostName);
		this.hostName = hostName;
		this.addresses = addresses;
	}


	public void run()
	{
		isDone = false;
		String namingLookup;
		String leftPart = null;
		String rightPart =  null;
		String localHostName = null;
		try{
			localHostName = InetAddress.getLocalHost().getHostName();
			String address = InetAddress.getByName(hostName).toString();
			leftPart = address.substring(0, address.lastIndexOf("/")).trim();
			rightPart = address.substring(address.lastIndexOf("/") + 1).trim();
			namingLookup = "//" + rightPart + "/StoIcGentServer";
			Compute stoicGent = (Compute)Naming.lookup(namingLookup);
			System.out.println("This is FastResolver for hostname " + hostName);
			if (stoicGent.willingToManage())
			{
				System.out.println(hostName + " is willing to manage.");				
				addresses.addElement(stoicGent);
			}
		}
		catch(Exception e)
       {
           isDone = true;
		   System.out.println(e);
		}
     isDone = true;
	System.out.println("FastResolver for hostname " + hostName + " finnished its job");
	}	
}
