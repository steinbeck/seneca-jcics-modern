/* StochasticGenerator.java
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
 * accompanying this package.
 */

package seneca.structgen;

import compchem.*;
import seneca.judges.*;
import seneca.compute.*;

import org.apache.log4j.*;
import java.util.*;
import java.rmi.*;
import java.rmi.server.UnicastRemoteObject;
import java.net.*;
import java.io.*;


/**
 *
 **/
public class StochasticGenerator extends Thread implements java.io.Serializable, Cloneable
{

	int nrOfAtoms;
	public int[][] bondMatrix;
	int [][] apsp;
	public boolean go = true;
	public int[] sumOfBonds;
	public boolean running = false;
	protected Atom[] atomSet = null;
	protected Atom[] latestResult = null;
	protected String bestStructure = null;
	protected ScoreSummary bestScore = null;
	protected ScoreSummary recentScore = null;
	protected ScoreSummary lastScore = null;
	public boolean report = true;
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
	int uphill  = 0;
	long maxUphillSteps = 50;
	String reportString;
	boolean hasMaxScore = false;
	long maxScore = -1;

	/** For log4j logging */
	static Category senecalog;
	static Category senecaresult;
//	static Category senecaannealing;
	
	int lastAnnealingCounter = 0;
	
	long starttime = -1, endtime = -1, runtime = -1;

	/**
	*
	**/
	double start_kT = 0;

	/**
	*
	**/
	double end_kT = 0;
	

	/**
	*
	**/
	long numberOfSteps = 0;


	/**
	*
	**/
	double coolingRate = 0;

	/**
	*
	**/
	double current_kT = 0;


	/**
	*
	**/
	long annealingStepCounter;

	/**
	*
	**/
	Vector schedules = null;

	/**
	* Determines the current schedule out of Vector schedules that is used for cooling
	**/
	int currentScheduleNo = 0;

	/**
	* A counter that determines how long the systems stays at each given temperature
	**/
	long plateauSteps = 0;

	/**
	*
	**/
	int plateauStepCounter = 0;

	/**
	* Determins after how many step without yielding a better solution the calculation is aborted
	**/
	long convergenceStopCount = 0;

	/**
	*
	**/
	long convergenceCounter = 0;

	/**
	*
	**/
	AnnealingSchedule currentSchedule = null;
	SetOfRingsFinder sorf = new SetOfRingsFinder();
	AnnealingLog al = null;
	int alStepsize = 500;

	public StochasticGenerator(boolean standAlone)
	{
		senecalog = Category.getInstance(this.getClass().getName() + ".log");
		senecaresult = Category.getInstance(this.getClass().getName() + ".results");		
//		senecaannealing = Category.getInstance(this.getClass().getName() + ".annealing");		
		structureGeneratorResult = new StructureGeneratorResult();
		this.name = "StoIcGent";
		attic = new Attic();
		al = new AnnealingLog();
		alStepsize = al.getIterationAxisStepsize();
		this.version = "StoIcGenT V 0.6 beta, (c) 1998, 1999, 2000, Christoph Steinbeck";	
	
	}


	public void execute()
	{
		start();
	}

	/**
	*
	**/
	public void run()
	{
		senecalog.info("Start of StoicGent\n");
		maxScore = getMaxScore();
		boolean isConnected;
		starttime = System.currentTimeMillis();
		senecalog.info("Start time: " + new java.util.Date(starttime) + "\n");
		running = true;
		pleaseStop = false;
		double reheatFactor = 0.3;

		setPriority(Thread.MIN_PRIORITY);
		try
		{
			int[][] bm;
			lastScore = null;
			recentScore = null;
			bestScore = null;
			long deltaE = 0;
			double rnd = 0;
			double exp = 0;
			int repeatCount, repeats = 1;
			String structure;
			iteration = 0;
			//         Atom[] latestResult;
			senecalog.debug(version);
			senecalog.debug(attic);
			senecalog.debug(attic.atomSet);
				
			senecalog.debug("Structure generation started: " +
					new java.util.Date().toString());
			senecalog.debug("Molecular formula: " +
					(new MFAnalyser(atomSet)).getMolecularFormula());
     		senecalog.debug("Running Random Structure Generation to obtain a starting structure.");			
			SingleRandomStructureGenerator dg =
				new SingleRandomStructureGenerator();

			dg.setAtomSet(atomSet);
			dg.attic = attic;
			atomSet = dg.generate();
			latestResult = atomSet;
			bondMatrix = DataStructureTools.createConnectionTable(atomSet);
			DataStructureTools.synchronizeConnectionTable(bondMatrix);
			sumOfBonds = new int[bondMatrix.length];
			repeatCount = 0;
			lastScore = new ScoreSummary(0, "");
			bestScore = new ScoreSummary(0, "");
			senecalog.debug("Random Structure Generation done...");			
			initJudges();
			do
			{
				senecalog.debug("InitAnnealing...");			
				initAnnealing();
			    senecalog.debug("InitAnnealing done...");			
				senecalog.info("start_kT = " + start_kT);
				current_kT = start_kT;
				annealingStepCounter = 0;
				do
				{
					// simply duplicates bondMatrix
					bm = DataStructureTools.cloneInt2D(bondMatrix);
					senecalog.debug("Mutating...");
					mutate(bm);
					//attic.apsp = spf.computeFloydAPSP(bm); 
					DataStructureTools.synchronizeBonds(attic.atomSet, bm);
					isConnected = PathUtils.isConnected(attic.atomSet);
					if (isConnected)	
					{
						senecalog.debug("New Structure is connected.");
					}
					else
					{												
						senecalog.debug("New Structure is not connected.");
					}
					if (isConnected)
					{
					
						recentScore = getScore(bm);
						senecalog.debug("New Score is: " + recentScore.score);
						deltaE = lastScore.score - recentScore.score;
						rnd = Math.random();
						exp = ((double) deltaE / (double) current_kT);
						senecalog.debug("Last score was: " + lastScore.score);
						senecalog.debug("deltaE is: " + deltaE);
						if (deltaE <= 0)
						{
							senecalog.debug("Accepted due to a better score.");
						}
						else
						{
							senecalog.debug("Lower Score. Exp: " + exp +"; rnd: " + rnd + "; exp(-de/kt):" + Math.exp(-exp));
							if (rnd < Math.exp(-exp))
							{
								senecalog.debug(
										"Accepted because " + rnd +
										" < " + Math.exp(-exp));
							}
							else
							{
								senecalog.debug(
										"Rejected because " + rnd +
										" > " + Math.exp(-exp));
							}
							
						}
						if (deltaE <= 0 || rnd < Math.exp(-exp))
						{
							if (deltaE <= 0) uphill++;
							lastScore = recentScore;
							bondMatrix = bm;
							if (deltaE < 0)
							{
								/* This should be <= 0 and be done if the result is not in stoch
										i.e. new. Do as soon as isomorphism check works again.							 
								 */
								convergenceCounter = 0;
							}
							if (recentScore.score >= bestScore.score)
							{
								latestResult = DataStructureTools.cloneAtomSet(atomSet);
								DataStructureTools.synchronizeBonds(latestResult, bm);
								if (notInStock(latestResult))
								{
									sumBonds(bm);
									structure = reportMDLStructure(latestResult, "Score: " + recentScore.score);
									senecaresult.info(structure + "\n$$$$\n");
									senecalog.info("New best Structure: \n" + bestScore + "\n");
									bestScore = recentScore;
									bestStructure = structure;
									structureGeneratorResult.structures.push(structure);
									structureGeneratorResult.scoreSummaries.push(recentScore);
								}
							}
						}
						convergenceCounter++;
						annealingStepCounter++;
						iteration++;						
						cool();
						if ((double)iteration/(double)alStepsize == (int)((double)iteration/(double)alStepsize))
						{
							al.addEntry((float)current_kT, (float)bestScore.score);
	//						senecaannealing.info(iteration + "; " + bestScore.score + "; " + current_kT + "\n");
						}
						if (((double)iteration/(double)1000) == (int)(iteration/1000))
						{
							senecalog.info("Iteration: " + iteration + " ");
							senecalog.info("Current kT = " + current_kT + "\n");			
						}
						senecalog.debug("UphillMoves: " + uphill + "/" + maxUphillSteps);		
						senecalog.debug("ConvergenceCounter: " + convergenceCounter + "/" + convergenceStopCount);
						senecalog.debug("PlateauStepCounter: " + plateauStepCounter + "/" + plateauSteps + "\n");
						goOn = true;		
						if (convergenceCounter >= convergenceStopCount)
						{
							if (bestScore.score < maxScore && reheatFactor < 1)
							{
								current_kT = current_kT + ((start_kT - current_kT) * reheatFactor);
								convergenceCounter = 0;
								annealingStepCounter = 0;
								plateauSteps = (long)((double)plateauSteps * 1.5);
								maxUphillSteps = (long)((double)maxUphillSteps * 1.5);
								if (reheatFactor < 1)
								{
									reheatFactor += 0.1;
								}
								senecalog.info("Calculation converged.\n");
								senecalog.info("Best score achieved in this run: " + bestScore.score + "\n");
								senecalog.info("Maximum possible score: " + maxScore + "\n");
								senecalog.info("Maximum score not reached: Reheating by " + (reheatFactor * 100) + "%\n");
								senecalog.info("Increasing plateauSteps to : " + plateauSteps + "\n");
								senecalog.info("Increasing maximum uphill moves to : " + maxUphillSteps + "\n");
							}
							else if (reheatFactor >= 1)
							{
								senecalog.info("Calculation did not converge despite successive parameter modifications.\n");
								senecalog.info("Best score achieved in this run: " + bestScore.score + "\n");
								senecalog.info("Maximum possible score: " + maxScore + "\n");
								senecalog.info("Maximum score not reached: Aborting.\n");
							    goOn = false;
							}
							else if (bestScore.score >= maxScore)
							{
								senecalog.info("Calculation converged.\n");
								senecalog.info("Best score achieved in this run: " + bestScore.score + "\n");
								senecalog.info("Maximum possible score: " + maxScore + "\n");
								senecalog.info("Maximum score reached. Regular end of calculation.\n");
							    goOn = false;
							}
						}
						
					}
					yield();	
				}
				while (goOn && !pleaseStop);
				goOn = false;
				repeatCount++;
				senecalog.debug("currentScheduleNo: " +
					currentScheduleNo + "; schedules.size(): " +
					schedules.size());
			}
			while (currentScheduleNo < schedules.size());
			endtime = System.currentTimeMillis();
			senecalog.info("Stop: " + new java.util.Date(endtime) + "\n");
			senecalog.info("Overall runtime: " + ((endtime - starttime) / 1000) + " seconds.\n");
			senecalog.info("Simulated annealing run finished.\n");
			running = false;
		}
		catch (Exception e)
		{
				senecalog.fatal("Error in StoicGent.run()");
				e.printStackTrace();
		}
	}

   /**
	* Reset the structure generator, deletes the result queue
	**/
	public void reset()
	{
		this.iteration = 0;
		this.structureGeneratorResult = null;
		this.structureGeneratorResult = new StructureGeneratorResult();
		this.schedules = null;
		this.atomSet = null;
		this.name = null;
		this.currentScheduleNo = 0;
	}
	
	/** Method to be called by the client to follow the structure generation process. */
	public Object getStatus()
	{
		Float[] entry;
		StructureGeneratorStatus sgs = new StructureGeneratorStatus();
		sgs.iteration = this.iteration;
		if (atomSet == null) sgs.molecularFormula = "not available";
		else sgs.molecularFormula = (new MFAnalyser(atomSet)).getMolecularFormula();
		sgs.bestStructure = new String(this.bestStructure);
		bestScore.maxScore = maxScore;
		sgs.bestEvaluation = bestScore;
		sgs.datasetName = this.datasetName;
		
		sgs.al = new AnnealingLog();
		int currentCount = this.al.getItemCount(0);
		for (int f = lastAnnealingCounter; f < currentCount; f++)
		{
			entry = this.al.getEntry(0, f);
			sgs.al.addEntry(entry[0], entry[1], entry[2]);
		}
		lastAnnealingCounter = currentCount;
		
		sgs.status = StructureGeneratorStatus.statusStrings[StructureGeneratorStatus.IDLE];		
		if (pleaseStop) sgs.status = StructureGeneratorStatus.statusStrings[StructureGeneratorStatus.STOPPED];
		if (!pleaseStop && goOn) sgs.status = StructureGeneratorStatus.statusStrings[StructureGeneratorStatus.RUNNING];		
		if (!pleaseStop && !goOn) sgs.status = StructureGeneratorStatus.statusStrings[StructureGeneratorStatus.HOLDING];				
		return sgs;
	}
	

	/**
	* Randomly breaks a bond and forms another to mutate the structure
	* The rules for this method are described in "Faulon, JCICS 1996, 36, 731"
	**/

	protected void mutate(int[][] bm)
	{
		int x1, x2, y1, y2;
		int a11, a12, a22, a21, b11;
		int min, max;
		String tempString;
		int nrOfAtoms = bm.length;
		do
		{
			/* Randomly choose four distinct atoms */
			do
			{
				x1 = (int)(Math.random() * nrOfAtoms);
				x2 = (int)(Math.random() * nrOfAtoms);
				y1 = (int)(Math.random() * nrOfAtoms);
				y2 = (int)(Math.random() * nrOfAtoms);
				yield();
			}
			while (!(x1 != x2 && x1 != y1 && x1 != y2 && x2 != y1 &&
				x2 != y2 && y1 != y2))
				;
			/* Get four bonds for these for atoms */
			a11 = bm[x1][y1];
			a12 = bm[x1][y2];
			a22 = bm[x2][y2];
			a21 = bm[x2][y1];
			/* Compute the range for b11 (see Faulons formulae for details) */
			int[] cmax = {0, a11 - a22, a11 + a12 - 3, a11 + a21 - 3};
			int[] cmin = {3, a11 + a12, a11 + a21, a11 - a22 + 3};
			min = max(cmax);
			max = min(cmin);
			/* Randomly choose b11 != a11 in the range max >= b11 >= min */
			b11 = a11;
			if (max - min > 0 || a11 != min)
			{
				do
				{
					b11 = ((int)(Math.random() * (max - min + 1))) + min;
				}
				while(a11 == b11);
			}
			yield();
		}
		while (!(b11 != a11 && (b11 >= min && b11 <= max)));
		bm[x1][y1] = b11;
		bm[x1][y2] = a11 + a12 - b11;
		bm[x2][y2] = a22 - a11 + b11;
		bm[x2][y1] = a11 + a21 - b11;
		bm[y1][x1] = b11;
		bm[y2][x1] = a11 + a12 - b11;
		bm[y2][x2] = a22 - a11 + b11;
		bm[y1][x2] = a11 + a21 - b11;
	}

	/**
	* Analog of Math.max that returns the largest int value in an array of ints
	**/
	protected int max(int[] values)
	{
		int max = values[0];
		for (int f = 0; f < values.length; f++)
		{
			if (values[f] > max)
			{
				max = values[f];
			}
		}
		return max;
	}

	/**
	* Analog of Math.max that returns the largest int value in an array of ints
	**/
	protected int min(int[] values)
	{
		int min = values[0];
		for (int f = 0; f < values.length; f++)
		{
			if (values[f] < min)
			{
				min = values[f];
			}
		}
		return min;
	}

	/**
	* Analog of Math.min that returns the largest int value in an array of ints
	**/
	public void setSASchedules(Vector schedules)
	{
		this.schedules = schedules;
	}

	/**
	* Initializes the annealing procedure each time a new instance of AnnealingSchedule
	* is popped of the schedules Vector.
	**/
	private void initAnnealing()
	{
		senecalog.debug("Start of initAnnealing");
		currentSchedule = (AnnealingSchedule) schedules.elementAt(
			currentScheduleNo);
		currentScheduleNo ++;
//		maxUphillSteps = (int)(currentSchedule.plateauSteps * 0.1);
		ScoreSummary bestScore = null;
		ScoreSummary recentScore = null;
		ScoreSummary lastScore = new ScoreSummary(0, "");
		long changes = 0;
		int[][] bm;
		int f = 0;
		senecalog.debug("InitAnnealing: Start of do-loop");
		do
		{
			yield();
			// simply duplicates bondMatrix
			//DataStructureTools.printInt2D(bondMatrix);
			bm = DataStructureTools.cloneInt2D(bondMatrix);
			mutate(bm);
//			attic.apsp = spf.computeFloydAPSP(bm);
			
			senecalog.debug("InitAnnealing: Mid of do-loop");			
			DataStructureTools.synchronizeBonds(attic.atomSet, bm);
//			if (structureConnected(bm))
			if (PathUtils.isConnected(attic.atomSet))
			{
//				DataStructureTools.synchronizeBonds(attic.atomSet, bm);
//				attic.setOfAllRings = new SetOfRingsFinder().getAllRings(atomSet);
//				DataStructureTools.normalize(attic.atomSet, attic.setOfAllRings);
				recentScore = getScore(bm);
				changes += Math.abs(lastScore.score - recentScore.score);
				lastScore = recentScore;
				f++;
			}
		}
		while (f < 500);
//		senecalog.debug("InitAnnealing: End of do-loop");
		plateauSteps = currentSchedule.plateauSteps;
		maxUphillSteps = currentSchedule.maxUphillSteps;
		convergenceCounter = 0;
		convergenceStopCount = plateauSteps * 3;
		changes = (long)(changes / 500);
		start_kT = -changes /
			Math.log(currentSchedule.initialAcceptanceProbability);
		current_kT = start_kT;
		coolingRate = currentSchedule.asymptoticCoolingFactor;
		senecalog.debug("initialAcceptanceProbability: " +
				currentSchedule.initialAcceptanceProbability);
		senecalog.debug("start_kT: " + start_kT);
		senecalog.debug("plateauSteps: " + plateauSteps);
		senecalog.debug("coolingRate: " + coolingRate);
		senecalog.debug("maxUphillSteps: " + maxUphillSteps);
	}

	/**
	* Cool down according to the given annealing schedule
	**/
	private void cool()
	{
		senecalog.debug("Cooling:");
		if (plateauStepCounter > plateauSteps || uphill > maxUphillSteps)
		{
			current_kT *= coolingRate;
			plateauStepCounter = 0;
			uphill = 0;
		}
		plateauStepCounter++;
	}


	/** Performs an Isomorphism-Check with all structures in Stock */
	private boolean notInStock(Atom[] atomSet)
	{
		senecalog.debug("Isomorphism-Check started...");
		
		IsomorphismTester isoTest = new IsomorphismTester(atomSet);
		for (int f = 0; f < structureGeneratorResult.structures.size(); f++)
		{
			if (isoTest.isIsomorph( ChemFileUtils.readMDLMolFile(new BufferedReader(new StringReader((String)structureGeneratorResult.structures.elementAt(f))))))
			{
				senecalog.debug("Isomorphism-Check finished: In stock...");
				return false;
			}
		}
		senecalog.debug("Isomorphism-Check finished: Not in stock...");
		return true;
	}

	/** Calculates a score for this connection table (using the information from atomSet) */
	private ScoreSummary getScore(int[][] bm)
	{
		senecalog.debug("StoicGent->getScore() start");
		long score = 0;
		String description = "";
		Judge se = null;
		JudgeResult ser = null;
		reportString = "<score>";
		for (int f = 0; f < judges.size(); f++)
		{
			se = (Judge) judges.elementAt(f);
			if (se.getEnabled())
			{
				senecalog.debug("StoicGent->getScore()->Judge: " + se.name);
				ser = se.evaluate(bm);
				score += ser.score;
				reportString += new Long(ser.score).toString() + " ";
				description += ser.scoreDescription + "\n";
			}
		}
		reportString += new Long(score).toString();
		reportString += "</score>";
		if (report)
		{
			try
			{
				senecalog.debug(reportString);
			}
			catch(Exception exc)
			{
			
			}
		}
		if (score < 0) score = 0;
		senecalog.debug("StoicGent->getScore() return");
		return new ScoreSummary(score, description);
	}


	/** Updates the sumOfBonds array */
	private void sumBonds(int[][] bm)
	{
		for (int f = 0; f < bm.length; f++)
		{
			sumOfBonds[f] = 0;
			for (int g = 0; g < bm.length; g++)
			{
				sumOfBonds[f] += bm[f][g];
			}
		}
	}

	public long getSpeed()
	{
		long start = System.currentTimeMillis();
		int[][] bm = 
		{{0,1,0,0,0,0,0,0,1},
		 {1,0,1,0,0,0,0,0,0},
		 {0,1,0,1,0,0,0,0,0},
		 {0,0,1,0,1,0,0,0,1},
		 {0,0,0,1,0,1,0,0,0},
		 {0,0,0,0,1,0,1,1,0},
		 {0,0,0,0,0,1,0,0,0},
		 {0,0,0,0,0,1,0,0,0},
		 {1,0,0,1,0,0,0,0,0}
		};
		for (int i = 1; i < 10000; i++)
		{
			mutate(bm);
		}
		long stop = System.currentTimeMillis();
		return stop - start;
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


	/** Toggles the debug variable that determines whether output
	is generated during structure generation or not  */
	public void setVerbose(boolean v)
	{
		if (running)return;

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
	
	/** Returns a StructureGeneratorResult-Object with the best structures */
	public Object getResult()
	{
		return structureGeneratorResult.clone();
	}
	
	
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
	public void setMolecule(Molecule molecule)
	{
		this.atomSet = DataStructureTools.cloneAtomSet(molecule.getAtoms());
		attic.atomSet = DataStructureTools.cloneAtomSet(molecule.getAtoms());
		attic.normalized = DataStructureTools.cloneAtomSet(molecule.getAtoms());
		
	}

	private String reportSumBonds()
	{
		String s = "SumOfBonds Array: ";
		for (int f = 0; f < sumOfBonds.length; f++)
		{
			s += sumOfBonds[f] + " ";
		}
		return s;
	}

	private int getMaxScore()
	{
		Judge judge = null;
		int maxScore = 0;
		for (int f = 0; f < judges.size(); f++)
		{
			judge = (Judge)judges.elementAt(f);
			if (judge.getEnabled())
			{
				if (!judge.getHasMaxScore()) return -1;
				maxScore += judge.getMaxScore();
			}
		}
		return maxScore;
	}
	
	/** Lists a 2D array of int values to the System console */
	private String reportBM(int[][] bm)
	{
		String line = "Bondmatrix:\n";
		for (int f = 0; f < bm.length; f++)
		{
			for (int g = 0; g < bm.length; g++)
			{
				line += bm[f][g] + " ";
			}
			line  += "\n";
			
		}
		return line;
	}
	

	private String reportMDLStructure(Atom[] atomSet, String name)
	{
		StringWriter sw = new StringWriter();
		ChemFileUtils.writeMDLMol(sw, name, atomSet);
		return sw.toString();
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
