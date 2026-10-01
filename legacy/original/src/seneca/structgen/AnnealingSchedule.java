/* AnnealingSchedule.java
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

package seneca.structgen;

/** Put instances of this class in a vector and pass it to a StochasticGenerator to assign
 *  an annealing schedule. 
**/

public class AnnealingSchedule extends Object implements java.io.Serializable, Cloneable{
	
	/** The algorithm performs an initial search for a temperature where
	 *  the acceptance probability for a given structure 
	 *  equals initialAcceptanceProbability 
	**/
	public double initialAcceptanceProbability = 0.8;
	
	/** The maximum number of cooling-steps to be performed.
	 *  Not used by default since convergence is the stop criteria
	**/
	public long maxUphillSteps = 50;
	
	/** Number of steps to be performed on each temperature plateau 
	**/
	public long plateauSteps = 500;
	

	/** The factor for asymptotic cooling
	**/
	public double asymptoticCoolingFactor = 0.95;

   public String toString()
   {
		String out = "";
        out = "A-F:" + asymptoticCoolingFactor + "-IAP:" + initialAcceptanceProbability + "-PS:" + plateauSteps + "-UHS: " + maxUphillSteps;
		return out;
   }
   
	public Object clone() 
	{
		Object o = null;
		try 
		{
			o = super.clone();
		} 
		catch(CloneNotSupportedException e) 
		{
			System.err.println("MyObject can't clone");
		}
		return o;
	}
}