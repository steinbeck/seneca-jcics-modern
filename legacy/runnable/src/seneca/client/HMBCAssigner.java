/* HMBCAssigner.java
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

public class HMBCAssigner  extends SpectrumAssigner
{
	private boolean debug = false;
	private SenecaDataset sd;

	HMBCJudge hmbcJudge = null;


	public HMBCAssigner(SenecaDataset sd)
	{
		this.sd=sd;
		hmbcJudge = (HMBCJudge)sd.getJudge("HMBCJudge");

	}


	public boolean assign()
	{
		NMRSignal hetcorlrSignal = null;
		NMRSignal hetcorSignal = null;		
		float carbonshift;
		float protonshift; 
		Vector fromCarbonAtoms = null;
		Vector toCarbonAtoms = null;
		int from = 0, to = 0, toAlt = 0;	
		if (hmbcJudge == null) hmbcJudge = new 	HMBCJudge();			
		
		/* Check, if there is a complete carbon shift assignment. If not, refuse to do the job */	
		for (int i = 0; i < sd.setOfNodes.length; i++)
		{
			if (sd.setOfNodes[i].symbol.equals("C") && sd.setOfNodes[i].getPhysicalProperty(Atom.PHYS_PROP_13C_SHIFT) == null) return false; 
		}
		/* Now we know that each node has a carbon chemical shift assignment.
		 * We will now crawl the hetcor-lr and hetcor spectrum
		 */
		for (int i = 0; i < sd.hetcorlr.size(); i ++)
		{
			hetcorlrSignal = (NMRSignal)sd.hetcorlr.getSignal(i);
			carbonshift = hetcorlrSignal.getShift(NMRSpectrum.NUC_CARBON);
			protonshift = hetcorlrSignal.getShift(NMRSpectrum.NUC_PROTON);
			if (debug) System.out.println(carbonshift + " - " + protonshift);						
			fromCarbonAtoms = pickClosestCarbons(carbonshift, (float)0.05);		
			hetcorSignal = (NMRSignal)sd.hetcor.pickClosestSignal(protonshift, NMRSpectrum.NUC_PROTON, (float)0.01);
			carbonshift = hetcorSignal.getShift(NMRSpectrum.NUC_CARBON);			
			toCarbonAtoms = pickClosestCarbons(carbonshift, (float)0.05);
			if (debug)	System.out.println("Vectorsize: " + fromCarbonAtoms.size() + " - " + toCarbonAtoms.size());
									
			for (int j = 0; j < fromCarbonAtoms.size(); j ++)
			{
				from = ((Atom)fromCarbonAtoms.elementAt(j)).number;
				to = ((Atom)toCarbonAtoms.elementAt(0)).number;
				if (hmbcJudge.assignment == null || hmbcJudge.assignment.length == 0)
				{
					hmbcJudge.assignment = new boolean[sd.setOfNodes.length][sd.setOfNodes.length][sd.setOfNodes.length];
				}  
				hmbcJudge.assignment[from][to][to]	= true;			
				if (debug) System.out.println("Adding assignment: " + (from + 1) + " - " + (toAlt + 1));						

				for (int k = 1; k < toCarbonAtoms.size(); k ++)
				{
					toAlt = ((Atom)toCarbonAtoms.elementAt(k)).number;

					hmbcJudge.assignment[from][to][toAlt] = true;
					if (debug) System.out.println("Adding alternative assignment: " + (from + 1) + " - " + (toAlt + 1));						
				}
			}
					
		}
		return true;
	}

	

	/**
	 * This method returns a Vector with nodes of elementtype 'C' that have all the same shift
	 * and whose shift value does not deviate more than 'pickprecision' from the one given by 'shift'.
	 * The purpose of this method is to find all ambiguous assignments of hetcorlr signals with the 
	 * 1D carbon shifts assigned to each node. In the case of symmetry, one may have the same carbon shift
	 * for more than one carbon atom. Due to the assignment method, the values with the 1D carbon assignment
	 * are consistent. Only the carbon axis shift values in the hetcor and hetcorlr might be different due
	 * to peak picking imperfections. This we search the closest shift value between 1D carbon spectrum
	 * and hetcorlr and onces we found it, we make an exact search for that value within the carbon atoms
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
		 * hetcorlr. Since there might be more than one carbon (symmetry or incidental agreement)
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
