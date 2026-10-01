/* Judge.java
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
import seneca.structgen.*;
import java.util.Vector;
import java.io.*;

/** Base-class for evaluating the score of a particular structure with respect to
    agreement to a given parameter. This class is to be subclassed and customized.
    */

public abstract class Judge implements Serializable, Cloneable{


	/* A name identifying the scope of the Judge */
	public String name = "nop"; 

	/* Should this Judge be used during the evaluation process? */
	private boolean enabled = false; 

	/* 
	 * Important property of a Judge. Tells a score summarizing 
	 * entity if it is possible to calculate a maximum score for this judge
	 * This is important, for example, for letting a Simulated Annealing
	 * run converge to a maximum achivable score.
	 */
	 
	boolean hasMaxScore = false;
	boolean ringSetRequired = false;
	protected Attic attic = null;
	int[][] connectionTable;
	int score, maxScore, scoreSum;
	String resultString;
	int multiplicator; 
	transient JudgeListener judgeListener = null;

	static boolean debug = false;
	static boolean report = true;

	public Judge(String name)
	{
		this.name = name;
		this.attic = new Attic();
	}


	public abstract JudgeResult evaluate(int[][] connectionTable);

	public abstract void init();
	
	public abstract void calcMaxScore();

	public int getMultiplicator()
	{
		return this.multiplicator;
	}

	public void setMultiplicator(int multiplicator)
	{
		this.multiplicator = multiplicator;
	}

	
	public boolean getEnabled()
	{
		return this.enabled;
	}

	public void setEnabled(boolean enabled)
	{
		this.enabled = enabled;
		fireChanged();
	}

	public void setJudgeListener(JudgeListener jl)
	{
		this.judgeListener = jl;
	}
	
	public void fireChanged()
	{
		if (judgeListener != null) judgeListener.judgeDataChanged();
	}
	
	public Attic getAttic()
	{
		return this.attic;
	}

	public void setAttic(Attic attic)
	{
		this.attic = attic;
	}
	
	
	public boolean isRingSetRequired()
	{
		return this.ringSetRequired;
	}

	public void setRingSetRequired(boolean ringSetRequired)
	{
		this.ringSetRequired = ringSetRequired;
	}
	

	
	public boolean getHasMaxScore()
	{
		return this.hasMaxScore;
	}

	public void setHasMaxScore(boolean hasMaxScore)
	{
		this.hasMaxScore = hasMaxScore;
	}

	
	public int getMaxScore()
	{
		return this.maxScore;
	}

	public void setMaxScore(int maxScore)
	{
		this.maxScore = maxScore;
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
	
