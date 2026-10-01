/* WienerIndexJudge.java
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

package seneca.judges;

import compchem.*;
import java.util.Vector;

/** Calculates the Wiener Index for a structure and returns the absolute value
    of the difference to a given target index as the score    */

public class WienerIndexJudge extends Judge implements java.io.Serializable, Cloneable
{
	
	/** The Wiener Index that is targeted in the current constitutional search */
	private long targetIndex;
		
	public WienerIndexJudge(){
		super("WienerIndexJudge");
	}
		
	public WienerIndexJudge(Atom[] son){
		this();
	}
	
	public void setTargetIndex(long thisIndex){
		this.targetIndex = thisIndex;
		
	}
	
	public void init()
	{
	
	
	}
	
	public void calcMaxScore()
	{
	
	}

	public JudgeResult evaluate(int[][] ct){
		long wienerIndex = 0;
		connectionTable = ct;
		long N = connectionTable.length;
		/* for this I didn't find a better solution. It would be nice to have a better 
		   formula for the highest possible Wiener Index for a given molecular formula */
 		for (int f = 0; f < connectionTable.length - 1; f++){
 			for (int g = f + 1; g < connectionTable.length; g++){
 				wienerIndex += attic.apsp[f][g];
 			}
 		}
 		scoreSum = (int)Math.min(targetIndex - Math.abs(targetIndex - wienerIndex), targetIndex);
 		scoreSum = (int)Math.max(0, scoreSum);
 		return new JudgeResult(targetIndex, scoreSum, wienerIndex, "");
	}
	
	public JudgeResult evaluate(Atom[] son){
		int[][] ct = DataStructureTools.createConnectionTable(son);
		return 	evaluate(ct);
	}
}	
	