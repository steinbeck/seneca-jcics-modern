/* HHCOSYAssigner.java
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
import seneca.judges.*;
import seneca.core.*;
import java.util.Vector;

public class HHCOSYAssigner  extends SpectrumAssigner
{
	private boolean debug = false;
	private SenecaDataset sd;
	HHCOSYJudge hhcosyJudge = null;

	public HHCOSYAssigner(SenecaDataset sd)
	{
		this.sd=sd;
		hhcosyJudge = (HHCOSYJudge)sd.getJudge("HHCOSYJudge");
	}


	public boolean assign()
	{
		try
		{
			NMRSignal hhcosySignal = null;
			NMRSignal hetcorSignal1 = null;		
			NMRSignal hetcorSignal2 = null;				
			float protonshift1; 
			float protonshift2;
			float carbonshift1; 
			float carbonshift2;
			Vector fromCarbonAtoms = null;
			Vector toCarbonAtoms = null;
			int from, to, toAlt;	
			if (hhcosyJudge == null) hhcosyJudge = new 	HHCOSYJudge();
			
			/* Check, if there is a complete carbon shift assignment. If not, refuse to do the job */	
			for (int i = 0; i < sd.setOfNodes.length; i++)
			{
				if (sd.setOfNodes[i].symbol.equals("C") && sd.setOfNodes[i].getPhysicalProperty(Atom.PHYS_PROP_13C_SHIFT) == null) return false; 
			}
			/* Now we know that each node has a carbon chemical shift assignment.
			 * We will now crawl the hetcor-lr and hetcor spectrum
			 */
			 
	 		for (int i = 0; i < sd.setOfNodes.length; i ++)
	 			System.out.println(sd.setOfNodes[i].number);
			for (int i = 0; i < sd.hhcosy.size(); i ++)
			{
				hhcosySignal = (NMRSignal)sd.hhcosy.getSignal(i);
				protonshift1 = hhcosySignal.getShift(0);
				protonshift2 = hhcosySignal.getShift(1);
				if (debug) System.out.println(protonshift1 + " - " + protonshift2);						
				hetcorSignal1 = (NMRSignal)sd.hetcor.pickClosestSignal(protonshift1, NMRSpectrum.NUC_PROTON, (float)0.01);
				carbonshift1 = hetcorSignal1.getShift(NMRSpectrum.NUC_CARBON);			
				toCarbonAtoms = pickClosestCarbons(carbonshift1, (float)0.05);
				hetcorSignal2 = (NMRSignal)sd.hetcor.pickClosestSignal(protonshift2, NMRSpectrum.NUC_PROTON, (float)0.01);
				carbonshift2 = hetcorSignal2.getShift(NMRSpectrum.NUC_CARBON);			
				fromCarbonAtoms = pickClosestCarbons(carbonshift2, (float)0.05);
				if (debug)	System.out.println("Vectorsize: " + fromCarbonAtoms.size() + " - " + toCarbonAtoms.size());
										
				for (int j = 0; j < fromCarbonAtoms.size(); j ++)
				{
					from = ((Atom)fromCarbonAtoms.elementAt(j)).number;
					to = ((Atom)toCarbonAtoms.elementAt(0)).number;
					if (hhcosyJudge.assignment == null || hhcosyJudge.assignment.length == 0)
					{
						hhcosyJudge.assignment = new boolean[sd.setOfNodes.length][sd.setOfNodes.length][sd.setOfNodes.length];
					}  
					hhcosyJudge.assignment[from][to][to]	= true;	
					if (debug) System.out.println("Assigning a 3JHH correlation between Hydrogens at Heavyatom " + from + " and " + to);		

					for (int k = 1; k < toCarbonAtoms.size(); k ++)
					{
						toAlt = ((Atom)toCarbonAtoms.elementAt(j)).number;

						hhcosyJudge.assignment[from][to][toAlt] = true;
					}
				}
						
			}
			return true;
		}
		catch(Exception exc)
		{
			return false;
		}
	}

	

	/**
	 * This method returns a Vector with nodes of elementtype 'C' that have all the same shift
	 * and whose shift value does not deviate more than 'pickprecision' from the one given by 'shift'.
	 * The purpose of this method is to find all ambiguous assignments of hhcosy signals with the 
	 * 1D carbon shifts assigned to each node. In the case of symmetry, one may have the same carbon shift
	 * for more than one carbon atom. Due to the assignment method, the values with the 1D carbon assignment
	 * are consistent. Only the carbon axis shift values in the hetcor and hhcosy might be different due
	 * to peak picking imperfections. This we search the closest shift value between 1D carbon spectrum
	 * and hhcosy and onces we found it, we make an exact search for that value within the carbon atoms
	 * in order to find all ambiguous assignments.
	 *
	 * @param   shift A carbon chemical shift value that is looked for in the carbon atom array
	 * @param   pickprecision A maximum deviation that 'shift' may have from the carbon shifts that are searched
	 * @return  A vector with carbon atoms that fullfill the criteria defined above   
	 */
	public Vector pickClosestCarbons(float shift, float pickprecision)
	{
		int dim = -1, thisPosition = -1;
		float diff = Float.MAX_VALUE, compareShift = 0, referenceShift = 0;
		Vector carbons = new Vector();

		/* Now we search dimension dim for the chemical shift.
		*/
		for (int i = 0; i < sd.setOfNodes.length; i ++)
		{
			if (sd.setOfNodes[i].symbol.equals("C"))
			{
				compareShift = ((Float)sd.setOfNodes[i].getPhysicalProperty(Atom.PHYS_PROP_13C_SHIFT)).floatValue();
				if (diff > Math.abs(compareShift - shift))
				{
					diff = Math.abs(compareShift -	shift);
					referenceShift = compareShift;
					thisPosition = i;
				}
			}
		}
		if (diff < pickprecision)
		{
			carbons.add(sd.setOfNodes[thisPosition]);
		}
		/* Here we have found one carbon atom that has the closest shift 
		 * (from the 1D carbon spectrum assignment) compared to the given shift from the 
		 * hhcosy. Since there might be more than one carbon (symmetry or incidental agreement)
		 * with one 
		 */
		 for (int i = 0; i < sd.setOfNodes.length; i ++)
		 {
			if (sd.setOfNodes[i].symbol.equals("C"))		 
			{
			 	compareShift = ((Float)sd.setOfNodes[i].getPhysicalProperty(Atom.PHYS_PROP_13C_SHIFT)).floatValue();
			 	if (compareShift == referenceShift && i != thisPosition)
			 	{
					carbons.add(sd.setOfNodes[thisPosition]);
			 	}
			}
		 }
		return carbons;
	}
	

	public void setVerbose(boolean verbose)
	{
		this.debug = verbose;
	}

}
