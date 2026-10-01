/* SingleRandomStructureGenerator.java
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

/*
 * PORT NOTE (2026): The source file of this class was missing from the archived
 * source tree although StochasticGenerator.run() depends on it. This file is a
 * reconstruction from the class file contained in the jar built on 2001-09-09
 * (legacy/dist-2001/seneca-client.jar, seneca/structgen/SingleRandomStructureGenerator.class),
 * decompiled with CFR 0.152 and transcribed statement by statement. Control flow,
 * evaluation order and arithmetic are identical to the 2001 bytecode. The only
 * deliberate change: Math.random() is replaced by Rng.random() (determinism hook,
 * same number and order of draws). See legacy/runnable/PATCHES.md.
 */

package seneca.structgen;

import compchem.*;

/**
 * Generates one random, fully saturated and connected constitution for a
 * given set of atoms. Used by StochasticGenerator to obtain a starting structure.
 **/
public class SingleRandomStructureGenerator extends StructureGenerator implements java.io.Serializable, Cloneable
{
	Atom[] latestResult;
	int[][] maxBondOrders;

	public SingleRandomStructureGenerator()
	{
		super("SingleRandomGent");
	}

	public void reset()
	{
	}

	public void execute()
	{
	}

	public Object getStatus()
	{
		return null;
	}

	public Atom[] generate()
	{
		boolean structureFound = false;
		boolean bondFormed;
		int partner;
		int maxBondOrder;
		int order;
		int unsaturatedCount;
		nrOfAtoms = atomSet.length;
		for (int f = 0; f < atomSet.length; f++)
		{
			atomSet[f].reset();
		}
		do
		{
			iteration++;
			structureFound = false;
			do
			{
				bondFormed = false;
				for (int f = 0; f < atomSet.length; f++)
				{
					Thread.yield();
					if (!atomSet[f].isSaturated())
					{
						partner = getAnotherUnsaturatedNode(f);
						if (partner != -1)
						{
							maxBondOrder = Math.min(atomSet[f].getCurrentMaxBondOrder(), atomSet[partner].getCurrentMaxBondOrder());
							order = Math.max(1, (int)(Rng.random() * maxBondOrder));
							atomSet[f].formBond(atomSet[partner], order);
							atomSet[partner].formBond(atomSet[f], order);
							bondFormed = true;
						}
					}
				}
			}
			while (bondFormed);
			unsaturatedCount = getUnsaturatedCount();
			attic.atomSet = atomSet;
			bondMatrix = DataStructureTools.createConnectionTable(attic.atomSet);
			attic.apsp = spf.computeFloydAPSP(bondMatrix);
			if (unsaturatedCount == 0 && structureConnected(bondMatrix))
			{
				return atomSet;
			}
			for (int f = 0; f < atomSet.length; f++)
			{
				atomSet[f].reset();
			}
		}
		while (!structureFound);
		return null;
	}

	int getAnotherUnsaturatedNode(int node)
	{
		int start = (int)(Rng.random() * atomSet.length);
		for (int f = start; f < atomSet.length; f++)
		{
			if (!atomSet[f].isSaturated() && f != node && !atomSet[f].isBondedTo(atomSet[node]))
			{
				return f;
			}
		}
		for (int f = 0; f < start; f++)
		{
			if (!atomSet[f].isSaturated() && f != node && !atomSet[f].isBondedTo(atomSet[node]))
			{
				return f;
			}
		}
		return -1;
	}

	private int getUnsaturatedCount()
	{
		int count = 0;
		for (int f = 0; f < atomSet.length; f++)
		{
			if (!atomSet[f].isSaturated())
			{
				count++;
			}
		}
		return count;
	}

	private boolean nodeStatusOk(int node)
	{
		return atomSet[node].maxBondOrderSum >= atomSet[node].HCount + sumOfBonds[node];
	}

	private boolean nodeStatusPerfect(int node)
	{
		return atomSet[node].maxBondOrderSum == atomSet[node].HCount + sumOfBonds[node];
	}

	private boolean endOfMatrix(int row, int column)
	{
		return column >= nrOfAtoms - 1 && row >= nrOfAtoms - 2;
	}

	private void sortNodes()
	{
		boolean sorted;
		Atom temp;
		for (int f = 0; f < atomSet.length; f++)
		{
			atomSet[f].number = f;
		}
		do
		{
			sorted = true;
			for (int f = 0; f < atomSet.length - 1; f++)
			{
				if (atomSet[f].HCount > atomSet[f + 1].HCount)
				{
					temp = atomSet[f + 1];
					atomSet[f + 1] = atomSet[f];
					atomSet[f] = temp;
					sorted = false;
				}
			}
		}
		while (!sorted);
		for (int f = 0; f < atomSet.length; f++)
		{
			atomSet[f].number = f;
		}
	}

	public void setMolecule(Molecule molecule)
	{
	}

	public long getSpeed()
	{
		return -1;
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
