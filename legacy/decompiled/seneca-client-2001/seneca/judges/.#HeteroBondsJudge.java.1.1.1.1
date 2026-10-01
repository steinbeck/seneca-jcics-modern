/* HeteroBondsJudge.java
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

/** Based on inspection of e.g. a 13C-NMR spectrum certain bond orders for each 
    node can be favoured by assigning positive scores for them and negative (or no) scores for
    unwanted bond orders  */

public class HeteroBondsJudge extends Judge implements java.io.Serializable, Cloneable{

	public int score = 100;
	public boolean[] isHeteroBonded;
	
	private int maxScore = 0;
	private int trueFlagsCount = 0;

	public HeteroBondsJudge()
	{
		super("HeteroBondsJudge");
		hasMaxScore = true;
	}

	public void setScore(int s)
	{
		score = s;
	}
	
	public void init()
	{
		if (attic.atomSet == null) return;
		isHeteroBonded = new boolean[attic.atomSet.length];
	}

	public void calcMaxScore()
	{
		maxScore = 0;
		trueFlagsCount = 0;
		for (int f = 0; f < isHeteroBonded.length; f++)
		{
			if (isHeteroBonded[f])
			{
				maxScore += score;
				trueFlagsCount ++;
			}
		}
	}

	public JudgeResult evaluate(int[][] bm)
	{
		scoreSum = 0;
		int hetAttachedNo = 0;
		for (int f = 0; f < isHeteroBonded.length;f++)
		{
			if (isHeteroBonded[f] && attic.atomSet[f].symbol.equals("C")){
				for (int h = 0; h < attic.atomSet[f].degree; h++)
				{
					if (!attic.atomSet[f].nodeTable[h].symbol.equals("C"))
					{
						scoreSum += score;
						hetAttachedNo ++;
						break;
					}
				}				
			}
		}
		String message = "<p>" + hetAttachedNo + " of " + trueFlagsCount + " carbons assigned to be heterobonded are indeed hetero-bonded in the structure.</p>";
		return new JudgeResult(maxScore,scoreSum, 0, message);
	}

}	
	
