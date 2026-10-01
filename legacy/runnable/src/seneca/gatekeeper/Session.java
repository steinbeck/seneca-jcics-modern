/* Session.java
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

import java.util.Vector;

/** Put instances of this class in a vector and pass it to a StochasticGenerator to assign
 *  an annealing schedule. 
**/

public class Session implements java.io.Serializable
{
	public static long UNDEFINED = -1;
	
	public long timeStarted = UNDEFINED;
	public long timeStopped = UNDEFINED;
	public String owner = null;
	public Vector serverList;
	private String id = null;
			
	public Session(long timeStarted, String owner) throws InvalidSessionException
	{
		if (timeStarted <= 0) throw new InvalidSessionException("Invalid start time assignment");
		if (owner == null) throw new InvalidSessionException("Invalid owner assignment");
		if (owner.equals("")) throw new InvalidSessionException("Invalid owner assignment");				
		this.timeStarted = timeStarted;
		this.owner = owner;
	}
	
	public String getID()
	{
		return new String(owner + timeStarted);
	}
}