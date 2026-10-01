/* StructureGeneratorResult.java
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

package seneca.structgen;

import compchem.*;
import seneca.judges.*;
import java.util.Vector;
import java.io.*;


/** Class to store the result of a Structure Generation **/
public class StructureGeneratorResult implements java.io.Serializable, Cloneable{

	public LIFOQueue structures = new LIFOQueue(30);
	public LIFOQueue scoreSummaries = new LIFOQueue(30);
	int size = 30;

	public StructureGeneratorResult()
	{

	}

	public StructureGeneratorResult(int size)
	{
		if (size > 0)
		{
			this.size = size;
			structures = new LIFOQueue(this.size);
			scoreSummaries = new LIFOQueue(this.size);
		} 
	}


	/** Sorts the structures in descending order with respect to the score **/
	public void sort()
	{
		boolean somethingsChanged = false;
		Object o1, o2; 
		do{
			somethingsChanged = false;
			for (int f = 0; f < structures.size() - 1; f++)
			{
				if (((ScoreSummary)scoreSummaries.elementAt(f)).score < 
					((ScoreSummary)scoreSummaries.elementAt(f + 1)).score)
				{
					o1 = structures.elementAt(f + 1);			
					o2 = scoreSummaries.elementAt(f + 1);		
					structures.removeElementAt(f + 1);			
					scoreSummaries.removeElementAt(f + 1);	
					structures.insertElementAt(o1, f);			
					scoreSummaries.insertElementAt(o2, f);	
					somethingsChanged = true;
				}
			}	
		}while(somethingsChanged);		
	}

	public int size()
	{
		return structures.size();	
	}

	public void removeIsomorphism()
	{
		sort();
		boolean removed = false;
		boolean stop = false;
		boolean isIsomorphic;
		Atom[] n1;
		Atom[] n2;
		IsomorphismTester isoTest;
		int s = 0;
		do
		{
			removed = false;
			s = structures.size();
			//System.out.println(s);
			try
			{
				n1 = ChemFileUtils.readMDLMolFile(new BufferedReader(new StringReader((String)structures.elementAt(--s))));
			}
			catch(Exception e)
			{
				removed = true;
			}
		}while(removed);
		structures.setSize(s - 1);
		int i = 0, j = 0;
		do
		{  
			removed = false;
			isoTest = new IsomorphismTester();
			n1 = ChemFileUtils.readMDLMolFile(new BufferedReader(new StringReader((String)structures.elementAt(i))));
			s = structures.size();
			//System.out.println(s);
			j = i + 1;

			do{ 
				if (i != j && ((ScoreSummary)scoreSummaries.elementAt(i)).score == ((ScoreSummary)scoreSummaries.elementAt(j)).score)
				{
					isIsomorphic = false;
					try
					{
						isIsomorphic = isoTest.isIsomorph(n1, ChemFileUtils.readMDLMolFile(new BufferedReader(new StringReader((String)structures.elementAt(j)))));
					}
					catch(Exception exc)
					{
					
					}
					
					if (isIsomorphic)
					{
						structures.removeElementAt(j);
						scoreSummaries.removeElementAt(j);
						removed = true;
						System.out.println("Removing structure " + j + ", which is isomorphic with " + i);
					}
					else
					{
						j++;
					}
				}
				else
				{
					j++;					
				} 
			}while(structures.size() > 0 && j < structures.size());

			System.out.println("i = " + i);
			i++;
		}while(structures.size() > 0 && i < structures.size());
	}

	public void merge(StructureGeneratorResult sgr)
	{
		this.size += sgr.size();
		this.structures.size += sgr.size();
		this.scoreSummaries.size += sgr.size();
		for (int f = 0; f < sgr.size(); f++)
		{
			this.structures.push(sgr.structures.elementAt(f));
			this.scoreSummaries.push(sgr.scoreSummaries.elementAt(f));
		}
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


