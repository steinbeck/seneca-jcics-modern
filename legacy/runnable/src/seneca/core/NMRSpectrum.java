/* NMRSpectrum.java
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
 
package seneca.core;


/** A Class to model an n-dimensional NMR spectrum,
  *
  */

import java.util.Vector;
import javax.swing.event.*;


public class NMRSpectrum extends Vector  implements java.io.Serializable
{
	public boolean debug = false;

	/** An arbitrary name that can be assigned to this spectrum
	for identification purposes.*/
	public String name = "";
	/** An arbitrary name to identify the type of this spectrum,
	like COSY, NOESY, HSQC, etc. I decided not to provide
	static Strings with given experiment type since the
	there are numerous experiments yielding basically identical
	information having different names */
	public String specType = "";
	/** The actual spectrum, i.e. a collection of nmrSignals */
	//protected NMRSignal[] nmrSignals;
	/** This holds sorted list of Chemical Shifts of all axes.
	The first dimension addresses the axes, the second the
	shift values in this axis, starting from the highest value. */
	public Vector[] shiftList;
	/** Not yet clear if this is needed.  */
	public float[] pickPrecision;
	/** Declares how many axes are in involved in this spectrum. */
	public int dim = 1;
	/** The nuclei of the different axes. */
	public String nucleus[];
	/** The proton frequency of the spectrometer used to
	record this spectrum. */
	public float spectrometerFrequency;

	public String solvent = ""; 
	public String standard = "";

	/** Some standard nulcei for the 'nucleus' field.*/
	public static String NUC_PROTON = "1H";
	public static String NUC_CARBON = "13C";
	public static String NUC_NITROGEN = "15N";
	public static String NUC_PHOSPHORUS = "31P";
	// ... to be continued...

	public static String[] SPECTYPE_BB = {NUC_CARBON};
	public static String[] SPECTYPE_DEPT = {NUC_CARBON};

	public static String[] SPECTYPE_HMQC = {NUC_PROTON, NUC_CARBON};
	public static String[] SPECTYPE_HSQC = {NUC_PROTON, NUC_CARBON};
	public static String[] SPECTYPE_HMBC = {NUC_PROTON, NUC_CARBON};
	public static String[] SPECTYPE_HHCOSY = {NUC_PROTON, NUC_PROTON};
	public static String[] SPECTYPE_NOESY = {NUC_PROTON, NUC_PROTON};


	protected transient EventListenerList changeListeners = new EventListenerList();


	NMRSpectrum(String[] nucleus, String name)
	{
		this.dim = nucleus.length; //redundant, I know :-)
		this.nucleus = nucleus;
		shiftList = new Vector[dim];
		for (int f = 0; f < dim; f++)
		{
			shiftList[f] = new Vector();
		}
		this.name = name;
	}

	/** Return the number of individual frequencies in the
	heteroatom shift list, which should be equal or smaller than
	the number of respective atoms */

	public int getSignalNumber(int axis)
	{
		return shiftList[axis].size();
	}


	/** Adds an NMRSignal to the NMRSpectrum. */
	public void addSignal(NMRSignal thisSignal)
	{
		addElement(thisSignal);
		updateShiftLists();
		fireChange();
		//createMatrix();

	}
	
	/** Creates an empty signal with correct dimension*/
	public void newSignal()
	{
		addElement(new NMRSignal(nucleus));
		updateShiftLists();
		fireChange();
		//createMatrix();

	}

	/** Returns an NMRSignal at position number in the vector */
	public Object getSignal(int number)
	{
		return elementAt(number);
	}

	public void setSpectrometerFrequency(float sf)
	{
		this.spectrometerFrequency = sf;
	}

	public float getSpectrometerFrequency()
	{
		return spectrometerFrequency;
	}

	public void setSolvent(String solvent)
	{
		this.solvent = solvent;
	}

	public String getSolvent()
	{
		return solvent;
	}


	public void setStandard(String standard)
	{
		this.standard = standard;
	}

	public String getStandard()
	{
		return standard;
	}

	/** Returns the signal closest to the shift sought.
	* If no Signal is found within the interval defined by
	* pickprecision, null is returned.
	*/
	public Object pickClosestSignal(float shift, String nnucleus, float pickprecision)
	{
		int dim = -1, thisPosition = -1;
		float diff = Float.MAX_VALUE, shift1 = 0;
		for (int f = 0; f < nucleus.length; f ++)
		{
			if (nucleus[f].equals(nnucleus))
			{
				dim = f;
				break;
			}
		}
		
		/* Now we search dimension dim for the chemical shift.
		*/
		for (int f = 0; f < size(); f ++)
		{
			if (diff > Math.abs(((NMRSignal) elementAt(f)).shift[dim] - shift))
			{
				diff = Math.abs(((NMRSignal) elementAt(f)).shift[dim] -	shift);
				thisPosition = f;
			}
		}
		if (diff < pickprecision)
		{
			return elementAt(thisPosition);
		}
		return null;
	}

	/** Returns a vector with signals within the interval defined by
	* pickprecision. If none is found an empty vector is returned.
	*/
	public Vector pickSignals(float shift, String nnucleus,
		float pickprecision)
	{
		int dim = -1, thisPosition = -1;
		float diff = Float.MAX_VALUE, shift1 = 0;
		Vector pickedSignals = new Vector();
		for (int f = 0; f < nucleus.length; f ++)
		{
			if (nucleus[f].equals(nnucleus))
			{
				if (debug) System.out.println("Searching dimension " + f + " of type " + nnucleus + " for shift " + shift);
				dim = f;
				break;
			}
		}
		/* Now we search dimension dim for the chemical shift.
		*/
		for (int f = 0; f < size(); f ++)
		{
			if (debug) System.out.println(((NMRSignal) elementAt(f)).shift[dim]);
			if (pickprecision > Math.abs(((NMRSignal) elementAt(f)).shift[dim] - shift))
				pickedSignals.add(elementAt(f));
		}
		return pickedSignals;
	}



	/** Extracts a list of unique shifts from the list of cross signals and sorts them.
	* This is to define the column and row headers for tables.
	*/
	protected void updateShiftLists()
	{
		int hetCounter = 0, protCounter = 0;
		Float shift;
		float[] temp;
		for (int i = 0; i < size(); i++)
		{
			NMRSignal nmrSignal = (NMRSignal) elementAt(i);
			for (int j = 0; j < nmrSignal.shift.length; j++)
			{
				shift = new Float(nmrSignal.shift[j]);
				if (!shiftList[j].contains(shift))
				{
					shiftList[j].add(shift);
				}
			}
		}
	}




	/** Creates a 2D matrix of booleans, that models the set of crosspeaks in the 2D NMR spectrum.
	The dimensions are taken from hetAtomShiftList and protonShiftList, which again are
	produced by updateShiftLists based a collection of 2D nmrSignals

	private void createMatrix(){
	boolean found;
	float het, prot;
	int hetPos, protPos;
	hetCorMatrix = new boolean[hetAtomShiftList.length][protonShiftList.length];
	for (int f = 0; f < size(); f++){
	HetCorNMRSignal hetCorSignal = (HetCorNMRSignal)elementAt(f);
	prot = hetCorSignal.shift[NMRSignal.SHIFT_PROTON];
	het = hetCorSignal.shift[NMRSignal.SHIFT_HETERO];
	found = false;
	hetPos = isInShiftList(hetAtomShiftList, het, hetAtomShiftList.length);
	if (hetPos >= 0){
	protPos = isInShiftList(protonShiftList, prot, protonShiftList.length);
	if ( protPos >= 0){
	found = true;
	hetCorMatrix[hetPos][protPos] = true;
	}
	}
	}
	}
	*/


	public void report()
	{
		String s = "";
		System.out.println("Report for nmr spectrum " + name +
			" of type " + specType + ": ");
		for (int i = 0; i < shiftList.length; i++)
		{
			System.out.println("ShiftList for dimension " + (i + 1) + ":");
			for (int j = 0; j < shiftList[i].size(); j++)
			{
				s += (Float) shiftList[i].elementAt(j) + "; ";
			}
			System.out.println(s + "\n");
			s = "";
		}

	}

	/* Listener notification support methods START here */
	public void addChangeListener(ChangeListener x)
	{
		changeListeners.add (ChangeListener.class, x);
		// bring it up to date with current state
		x.stateChanged(new ChangeEvent(this));
	}

	public void removeChangeListener(ChangeListener x)
	{
		changeListeners.remove (ChangeListener.class, x);
	}

	protected void fireChange()
	{
		// Create the event:
		ChangeEvent c = new ChangeEvent(this);
		// Get the listener list
		Object[] listeners = changeListeners.getListenerList();
		// Process the listeners last to first
		// List is in pairs, Class and instance
		for (int i = listeners.length - 2; i >= 0; i -= 2)
		{
			if (listeners[i] == ChangeListener.class)
			{
				ChangeListener cl = (ChangeListener) listeners[i + 1];
				cl.stateChanged(c);
			}
		}
	}
	/* Listener notification support methods END here */



}

