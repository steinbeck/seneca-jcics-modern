/* ServerInfo.java
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

package seneca.gatekeeper;

import seneca.core.*;

/** 
 *  an annealing schedule. 
**/

public class ServerInfo implements java.io.Serializable
{
	public static long UNDEFINED = -1;

		private String hostname = null;;
	private long responseTime = UNDEFINED;
	private long speed = UNDEFINED;

	public boolean verbose = false;

	public int unsuccessfulContactCount = 0;

	/**
	 * Constructs a new ServerInfo
	 *
	 * @param   hostname  of this server
	 * @param   speed  
	 */
	public ServerInfo(String hostname, long speed)
	{
		this.hostname = hostname;
		this.speed = speed;
	}

	/**
	 * Constructs a new ServerInfo
	 *
	 * @param   hostname  of this server
	 * @param   speed  
	 * @param   responseTime  
	 */
	public ServerInfo(String hostname, long speed, long responseTime)
	{
		this.hostname = hostname;
		this.speed = speed;
		this.responseTime = responseTime;
	}


	/**
	 *
	 *
	 * @return     
	 */
	public String getHostname()
	{
		return this.hostname;
	}


	/**
	 *
	 *
	 * @param   hostname  
	 */
	public void setHostname(String hostname)
	{
		this.hostname = hostname;
	}



	/**
	 *
	 *
	 * @return     
	 */
	public long getResponseTime()
	{
		return this.responseTime;
	}


	/**
	 *
	 *
	 * @param   responseTime  
	 */
	public void setResponseTime(long responseTime)
	{
		this.responseTime = responseTime;
	}



	/**
	 *
	 *
	 * @return     
	 */
	public long getSpeed()
	{
		return this.speed;
	}


	/**
	 *
	 *
	 * @param   speed  
	 */
	public void setSpeed(long speed)
	{
		this.speed = speed;
	}

	public String toString()
	{
		String s = new String("ServerInfo for host " + hostname + ":\n");
		s += "Speed: " + speed + "\n";
		s += "Speedfactor: " + (70 / speed) + " (please consult docs for reference.) \n";
		s += "Responsetime: " + responseTime + ". \n";
		return s;
	}


	public int getUnsuccessfulContactCount()
	{
		return this.unsuccessfulContactCount;
	}

	public void setUnsuccessfulContactCount(int unsuccessfulContactCount)
	{
		this.unsuccessfulContactCount = unsuccessfulContactCount;
	}
}
