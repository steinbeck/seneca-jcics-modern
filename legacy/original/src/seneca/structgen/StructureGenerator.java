/* StructureGenerator.java
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
import seneca.compute.*;

public abstract class StructureGenerator extends Thread implements java.io.Serializable, Cloneable
{
	int nrOfAtoms;
	public int[][] bondMatrix;
	int [][] apsp;
	public boolean go = true;
	public long slowness = 100;
	public int[] sumOfBonds;
	public boolean running = false;
	protected Atom[] atomSet = null;
	protected Atom[] latestResult = null;
	protected Atom[] bestStructure = null;
	protected ScoreSummary bestScore = null;
	protected ScoreSummary recentScore = null;
	protected ScoreSummary lastScore = null;
	public boolean debug = false;
	public boolean display = false;
	public boolean found = false;
	protected StructureGeneratorResult structureGeneratorResult;
	protected long iteration = 0;
	protected String name = null;
	protected String datasetName = null;
	public Vector judges;
	protected String version = null;
	boolean goOn = false;
	boolean pleaseStop = false;
	Attic attic = null;
	
	protected ShortestPathFinder spf = new ShortestPathFinder();	

	public StructureGenerator()
	{
		this("StructGen");
	}
	
	
	public StructureGenerator(String name)
	{
		structureGeneratorResult = new StructureGeneratorResult();
		this.name = name;
		attic = new Attic();
	}

	/*
	 ******* First we declare the methods needed to implement compute.Task *******
	 *
	 */

	public abstract void execute();

	/** Returns a StructureGeneratorResult-Object with the best structures */
	public Object getResult()
	{
		return structureGeneratorResult;
	}

	/** Method to be called by the client to follow the structure generation process. */
	public abstract Object getStatus();


	/** returns the Version-Message of this structure generator */
	public String getVersion()
	{
		return version;
	}
	
	/** sets a signal for this structure generator to stop */
	public void end()
	{
		pleaseStop = true;;
	}
	


	/*
	 ******* End of method declarations for compute.Task *******
	 *
	 /

	/** Assigns the set of Nodes based on which the structure generation is performed */
	public void setAtomSet(Atom[] atSet)
	{
		this.atomSet = DataStructureTools.cloneAtomSet(atSet);
		attic.atomSet = DataStructureTools.cloneAtomSet(atSet);
		attic.normalized = DataStructureTools.cloneAtomSet(atSet);
		
	}


	/** Hands over a list of score evaluators for evaluation generated structure */
	public void setJudges(Vector v)
	{
		this.judges = v;

	}

	/** Returns the Vector containing the collection of score evaluators on which the scoring
	of generated structures is based */

	public Vector getJudges()
	{
		if (this.judges != null)
			return this.judges;
		else
			return new Vector();
	}

	/** Sets the name of this dataset */
	public void setDatasetName(String s)
	{
		this.datasetName = s;
	}

	/** Returns the name of this dataset */
	public String getDatasetName(String s)
	{
		return this.datasetName;
	}


	/** Toggles the debug variable that determines whether output
	is generated during structure generation or not  */
	public void setVerbose(boolean v)
	{
		if (running)
			return;
		debug = v;
	}


	/** debugging output of a connectivity matrix */
	protected void printMatrix()
	{
		String line;
		for (int f = 0; f < bondMatrix.length; f++)
		{
			line = "";
			for (int g = 0; g < bondMatrix.length; g++)
			{
				line += bondMatrix[f][g] + " ";
			}
			line += " - " + sumOfBonds[f];
			System.out.println(line);
		}
	}



	/** Checks whether
	- each atom has the correct number of bonds
	- the structure is a connected graph
	*/
	protected boolean structureConnected(int[][] bm)
	{
		/* Check if this structure is a connected graph by computing All-pairs of Shortest-paths
		and checking if a shortest path exists for each pair of atoms.
		*/
		if (debug) System.out.println("StructureGenerator->structureConnected()->start");	
		int N = attic.apsp.length;
		if (debug) DataStructureTools.printInt2D(attic.apsp);
		for (int i = 0; i < N; i++)
		{
			for (int j = i + 1; j < N; j++)
			{
				if (attic.apsp[i][j] > attic.apsp.length)
				{
					if (debug) System.out.println("StructureGenerator->structureConnected()->not connected");	
					return false;
				}
			}
		}
		if (debug) System.out.println("StructureGenerator->structureConnected()->conntected");	
		return true;
	}	

	public void initJudges()
	{
		for (int f = 0; f < judges.size(); f++)
		{
			if (((Judge)judges.elementAt(f)).getEnabled())
			{
				((Judge)judges.elementAt(f)).setAttic(this.attic);
				((Judge)judges.elementAt(f)).init();
			}
		}
	}
	public abstract long getSpeed();
}
