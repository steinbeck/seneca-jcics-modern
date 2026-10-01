/* CarbonShiftAssigner.java
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
 
package seneca.client; 
 
import compchem.*; 
import seneca.core.*;
import java.util.*;
import javax.swing.*;
public class CarbonShiftAssigner extends SpectrumAssigner
{
	 boolean debug = false;
	 SenecaDataset sd;
	
	public CarbonShiftAssigner(SenecaDataset sd)
	{
		this.sd = sd;
	}


	public boolean assign()
	{
		StringBuffer warning = new StringBuffer("Warning");
		int tempCounter = 0;
		int HCounter = 0;
		float intensitySum = 0;
		NMRSignal cnmrsig = null, d90sig = null, d135sig = null;
		if (debug) System.out.println("This is CarbonShiftAssigner doing its job");
		float cshift = 0;
		MFAnalyser mfa = new MFAnalyser(sd.setOfNodes);
	
		normalize(sd.carbon1D);
		intensitySum = sumIntensity(sd.carbon1D);		
		if (sd.setOfNodes != null && sd.carbon1D != null)
		{
			if (debug) System.out.println("Number of Carbons: "  + mfa.getAtomCount("C")); 
			if (debug) System.out.println("Sum of signal intensity in spectrum: "  + intensitySum); 
			if (mfa.getAtomCount("C") == Math.round(intensitySum) )
			{
				/* The arrays are of same length so that we can perform a one-to-one assignment */
				tempCounter = 0;	
				for (int f = 0; f < sd.setOfNodes.length; f ++)
				{
					if (sd.setOfNodes[f].symbol.equals("C"))
					{
						if (debug) System.out.println("Assigning :" + sd.setOfNodes[f].symbol);
						cnmrsig = (NMRSignal)sd.carbon1D.getSignal(tempCounter);
						for (int g = 0; g < Math.round(cnmrsig.intensity * cnmrsig.normalizeFactor); g++)
						{
							f += g;
							cshift = cnmrsig.getShift(NMRSpectrum.NUC_CARBON);
							d90sig = (NMRSignal)sd.dept90.pickClosestSignal(cshift, NMRSpectrum.NUC_CARBON, (float)0.05);
							d135sig = (NMRSignal)sd.dept135.pickClosestSignal(cshift, NMRSpectrum.NUC_CARBON, (float)0.05);						
							sd.setOfNodes[f].setPhysicalProperty(Atom.PHYS_PROP_13C_SHIFT,new Float(cnmrsig.getShift(NMRSpectrum.NUC_CARBON)));           
							if (d135sig != null && d135sig.phase == NMRSignal.PHASE_POSITIVE && d90sig == null)
							{
								/* We have a methyl group here */
								sd.setOfNodes[f].HCount = 3;
								HCounter += 3;
							}
							if (d135sig != null && d135sig.phase == NMRSignal.PHASE_NEGATIVE)
							{
								if (d90sig != null)
								{
									warning.append("Warning: There is a DEPT90 signal within the picking range of Methylene group " + (f + 1) + "\n");
									warning.append("Assignment ambiguous. Please check.\n");								
									System.out.println(warning);
								}
								/* We have a methylene group here */
								sd.setOfNodes[f].HCount = 2;
								HCounter += 2;
							}
							if (d90sig != null && d90sig.phase == NMRSignal.PHASE_POSITIVE)
							{
								/* We have a methine group here */
								sd.setOfNodes[f].HCount = 1;
								HCounter += 1;							
							}
							if (d135sig == null && d90sig == null)
							{
								/* We have a quarternary carbon here */
								sd.setOfNodes[f].HCount = 0;
							}
						}
						tempCounter++;
					}
				}
			}
			/* Now let's check if all the hydrogens are assigned. If yes, we can set the HCount of all other atoms 
			   to zero. Otherwise, the user has the problem :-) */

			mfa = new MFAnalyser(sd.getMolecularFormula());			   
			if (debug) System.out.println("HCount in Formula: " + mfa.getAtomCount("H") + ", HCount in table: " + HCounter);
			if (mfa.getAtomCount("H") == HCounter)
			{
				for (int f = 0; f < sd.setOfNodes.length; f ++)
				{
					if (!sd.setOfNodes[f].symbol.equals("C"))
					{
						sd.setOfNodes[f].HCount = 0;
					}
				}
			if (debug) System.out.println("CarbonShiftAssigner done");
			}			   			   

		}
		
		return true;
		
	}

	/* */
	private void normalize(NMRSpectrum nmr)
	{
		MFAnalyser mfa = new MFAnalyser(sd.setOfNodes);
		NMRSignal nmrsig = null;
		for (int f = 0; f < nmr.size(); f++)
		{
			nmrsig = (NMRSignal)nmr.getSignal(f);
			nmrsig.normalizeFactor = 1/nmrsig.intensity;

		}
		while (Math.round(sumIntensity(nmr)) < mfa.getAtomCount("C"))
		{
			(getLargestSignal(nmr)).normalizeFactor *= 2;
		}
		
		for (int f = 0; f < nmr.size(); f++)
		{
			nmrsig = (NMRSignal)nmr.getSignal(f);
			System.out.println("Signal " + f + ": " + nmrsig.intensity + ", " + nmrsig.intensity * nmrsig.normalizeFactor);
		}
	}

	private float sumIntensity(NMRSpectrum nmr)
	{
		float intensitySum= 0; 
		NMRSignal nmrsig = null;		
		for (int f = 0; f < nmr.size(); f++)
		{
			nmrsig = (NMRSignal)nmr.getSignal(f);
			intensitySum += nmrsig.intensity * nmrsig.normalizeFactor;
		}
		return intensitySum;
	}

	private NMRSignal getLargestSignal(NMRSpectrum nmr)
	{
		float maxInt = 0;
		NMRSignal nmrsig = null, returnsig = null;
		for (int f = 0; f < nmr.size(); f++)
		{
			nmrsig = (NMRSignal)nmr.getSignal(f);
			if (nmrsig.intensity > maxInt) 
			{
				returnsig = nmrsig;
				maxInt = nmrsig.intensity;
			}
		}
		return returnsig;
		
	}

	public void setVerbose(boolean debug)
	{
		this.debug = debug;
	}

}


