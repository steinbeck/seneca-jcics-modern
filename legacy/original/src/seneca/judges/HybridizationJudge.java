/* HybridizationJudge.java
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

public class HybridizationJudge extends Judge implements java.io.Serializable, Cloneable
{

	public int[][] scores;
	public static int SP3 = 0, SP2 = 1, SP = 2;
	public static String[] HYBRIDIZATIONSTRINGS = {"SP3","SP2","SP"};

	public HybridizationJudge()
	{
		super("HybridizationJudge");
		multiplicator = 100;	
		hasMaxScore = true;         
	}
	
	public void initScoreArray(int arrayLength)
	{
		scores = new int[arrayLength][3];
	}

	/* The score is handled as a value between 0 and 1 by the client. We multiply it by multiplicator */
	public void addHybridizationScore(int nodeNumber, int hybridization, double score)
	{
		scores[nodeNumber - 1][hybridization] = (int)(score * multiplicator);
	}
	
	public void init()
	{
		if (attic.atomSet == null) return;
		scores = new int[attic.atomSet.length][3];
	}

	public void calcMaxScore()
	{
		int max = 0;
		maxScore = 0;

		for (int f = 0; f < scores.length; f ++)
		{
			max = scores[f][0];
			for (int g = 1; g < 3; g++)
			{
				if (scores[f][g] > max) max = scores[f][g];
			}
			maxScore += max;
			//System.out.println("HyJudge.init(): maxScore=" + maxScore + ", max=" + max);
		}
		
	}


	public JudgeResult evaluate(int[][] bm)
	{
		scoreSum = 0;
		int bondCount = 0;
		for (int f = 0; f < attic.atomSet.length;f++)
		{
			bondCount = attic.atomSet[f].getBondCount();
			if (attic.atomSet[f].symbol.equals("C"))
			{  
				if (attic.atomSet[f].degree == 1)
				{
					if (bondCount == 1) scoreSum += scores[f][SP3];
					if (bondCount == 2) scoreSum += scores[f][SP2];
					if (bondCount == 3) scoreSum += scores[f][SP];					
				}
				if (attic.atomSet[f].degree == 2)
				{
					if (bondCount == 2) scoreSum += scores[f][SP3];
					if (bondCount == 3) scoreSum += scores[f][SP2];
					if (bondCount == 4) scoreSum += scores[f][SP];					
				}
				if (attic.atomSet[f].degree == 3)
				{
					if (bondCount == 3) scoreSum += scores[f][SP3];
					if (bondCount == 4) scoreSum += scores[f][SP2];
				}
				if (attic.atomSet[f].degree == 4)
				{
					scoreSum += scores[f][SP3];
				}
			}
		}
		return new JudgeResult(maxScore, scoreSum, 0, "<p>" + score + " from a possible " + maxScore + " points for hybridization state were given.</p>");
	}
}	
