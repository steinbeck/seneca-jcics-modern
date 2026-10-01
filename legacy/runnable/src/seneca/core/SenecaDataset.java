/* SenecaDataset.java
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

import java.util.*;
import java.io.*;
import javax.swing.*;
import javax.swing.event.*;
import compchem.*;
import seneca.structgen.*;
import seneca.judges.*;

/**
 *  SenecaDataset.java
 *  Collection of datastructures (like those representing
 *  spectra) that make up a dataset (belonging to one particular
 *  compound or structure elucidation project) in Seneca.
 */

public class SenecaDataset implements java.io.Serializable, ChangeListener
{

	public static String carbon1d_name = "Carbon1D";
	public static String dept90_name = "DEPT-90";
	public static String dept135_name = "DEPT-135";
	public static String hetcor_name = "1JCH correlation";
	public static String hetcorlr_name = "CH long-range correlation";
	public static String hhcosy_name = "H,H-COSY";
	public static String noesy_name = "NOESY";
							
    /* Some general properties like a name for this dataset, the
     * molecular formula and the associated mass.
     * Nothing but a unique name is essential, though.
     */
	public boolean debug = false;
    public boolean destroy = false;
		
    private String name = null;
    private String description = "none";
    private String molecularFormula = null;
    private float molecularMass = (float) 0;

    /* A set of spectra. Since one can actually very well have two
     * spectra of the same kind in a dataset (e.g. NOESY's with
     * different mixing times), I'm thinking of makeing this a little more
     * flexible.
     */
    
//    public Carbon1D carbon1D = new Carbon1D();
	public NMRSpectrum carbon1D = new NMRSpectrum(NMRSpectrum.SPECTYPE_BB, "carbon1d");
	public NMRSpectrum dept90 = new NMRSpectrum(NMRSpectrum.SPECTYPE_DEPT, "dept90");
	public NMRSpectrum dept135 = new NMRSpectrum(NMRSpectrum.SPECTYPE_DEPT, "dept135");		
    public NMRSpectrum hetcor = new NMRSpectrum(NMRSpectrum.SPECTYPE_HSQC, "hetcor");
    public NMRSpectrum hetcorlr = new NMRSpectrum(NMRSpectrum.SPECTYPE_HSQC, "hetcorlr");
    public NMRSpectrum hhcosy = new NMRSpectrum(NMRSpectrum.SPECTYPE_HHCOSY, "hhcosy");
    public NMRSpectrum noesy = new NMRSpectrum(NMRSpectrum.SPECTYPE_NOESY, "noesy");    

    
    /* And finally the set of nodes that is generated from whatever
     * information is suitable. So far, still lacking a good idea for
     * what to do in cases where I do not have a molecular formula,
     * it's generated from this single, straightforward piece of information.
     */

    public Vector annealingSchedules = new Vector();
    
    public Atom[] setOfNodes;

    public Vector judges = new Vector();
	
	
	/** A vector to store all sessions (calculations) that have been done 
	on this dataset */
    public Vector sessions = new Vector();
	
    /** A vector to store a list of servers known to this client */
    public Vector serverList = new Vector();


	
	/* Administrative stuff */

    protected transient EventListenerList changeListeners = new EventListenerList();

    public SenecaDataset()
    {
        /* Carbon1D dataset sets spectype in its contructor */
		carbon1D.specType = carbon1d_name;
		dept90.specType = dept90_name;
		dept135.specType = dept135_name;
    	hetcor.specType = hetcor_name;
        hetcorlr.specType = hetcorlr_name;    
        hhcosy.specType = hhcosy_name;
        noesy.specType=noesy_name;
        
        carbon1D.addChangeListener(this);
        dept90.addChangeListener(this);
        dept135.addChangeListener(this);				
        hetcor.addChangeListener(this);
        hetcorlr.addChangeListener(this);
        hhcosy.addChangeListener(this);
        noesy.addChangeListener(this);
    }

    public SenecaDataset(String name)
    {
        this();
        this.name = name;
    }

    public void setName(String name)
    {
        this.name = name;
        fireChange();
    }

    /** Set the molecular formula for this dataset.
       * Also seems to be a good place to initialize the set of rings.
       */
    public void setMolecularFormula(String MF)
    {
        this.molecularFormula = MF;
        /* Uses the MFAnalyser class from compchem package */
        MFAnalyser mfa = new MFAnalyser(MF);
        setOfNodes = mfa.getSetOfHeavyNodes();
        initializeNodes();
        fireChange();
    }
	
	/** Set the molecular formula for this dataset by assigning a set of Nodes.
       * Also seems to be a good place to initialize the set of rings.
       */
    public void setMolecularFormula(Atom[] son)
    {
        this.setOfNodes = son;
        this.molecularFormula = (new MFAnalyser(setOfNodes)).analyseSetOfNodes();
        initializeNodes();
        fireChange();
    }

    public void setMolecularMass(float MS)
    {
        this.molecularMass = MS;
        fireChange();
    }

    public String getName()
    {
        return name;
    }

		public void setDescription(String d)
	{
		description = d;
		fireChange();	
	} 

    public String getDescription()
    {
        return description;
    }

    public void setCurrent()
    {
        fireChange();
    }

    public String getMolecularFormula()
    {
        return molecularFormula;
    }

    public float getMolecularMass()
    {
        return molecularMass;
    }
	
    public void setSetOfNodes(Atom[] son)
    {
		this.setOfNodes = son;
		initializeNodes();				
		if (this.molecularFormula == null) this.molecularFormula = analyseSetOfNodes();
		fireChange();
		
    }
	

    public Atom[] getSetOfNodes()
    {
        return setOfNodes;
    }

	 public void destroy()
	 {
		destroy = true;
		System.out.println("SenecaDataset " + getName() + " says: Arrgh - I´m being destroyed...");
		fireChange();	
	 }

	
	public Object getJudge(String name)
	{
		if (!name.startsWith("seneca.judges")) name = "seneca.judges." + name;
		for (int f = 0; f < judges.size(); f++)
		{
			if (name.equals(judges.elementAt(f).getClass().getName())) return judges.elementAt(f);
		}
		try
		{
			return Class.forName(name).newInstance();
		}
		catch(Exception exc)
		{
			System.out.println("Could not instantiate class " + name);
		}
		return null;
	}


    /** Analyses a set of Nodes that has been changed or recently loaded
         and  returns a molecular formula  */
    private String analyseSetOfNodes()
    {
        String symbol, mf = "";
        String[] symbols;
        int[] elementCount;
        int HCount = 0;
        int numberOfElements = 0;
        boolean done;
        symbols = new String[setOfNodes.length];
        elementCount = new int[setOfNodes.length];
        for (int f = 0; f < setOfNodes.length; f++)
        {
            symbol = setOfNodes[f].symbol;
            HCount += setOfNodes[f].HCount;
            done = false;
            for (int g = 0; g < numberOfElements ; g++)
            {
                if (symbols[g].equals(symbol))
                {
                    elementCount[g]++;
                    done = true;
                    break;
                }
            }
            if (!done)
            {
                symbols[numberOfElements] = symbol;
                elementCount[numberOfElements]++;
                numberOfElements++;
            }
        }
        for (int g = 0; g < numberOfElements ; g++)
        {
            mf += symbols[g];
            if (elementCount[g] > 1)
                mf += new Integer(elementCount[g]).toString();
            if (g == 0 && HCount > 0)
            {
                mf += "H";
                if (HCount > 1)
                    mf += new Integer(HCount).toString();
            }

        }
        return mf;
    }
	
	public boolean init()
	{
		initializeNodes();
		if (debug) System.out.println("This is sd.init()!");		
		judges.addElement(new HHCOSYJudge());		
		judges.addElement(new HMBCJudge());		
		judges.addElement(new HeteroBondsJudge());		
		judges.addElement(new SymmetryJudge());		
		judges.addElement(new HybridizationJudge());
		judges.addElement(new HOSECodeJudge());
		judges.addElement(new FeatureJudge());
		for (int f = 0; f < judges.size(); f++)
		{
			if (debug) System.out.println(judges.elementAt(f));
		}
		return true;
	}

    private void initializeNodes()
    {
 		if (setOfNodes == null) return;
        StandardElements se = new StandardElements();
        for (int f = 0; f < setOfNodes.length; f++)
        {
            setOfNodes[f].number = f;
            setOfNodes[f].maxBondOrder =
              se.getMaxBondOrder(setOfNodes[f].symbol);
            setOfNodes[f].maxBondOrderSum =
              se.getMaxBondOrderSum(setOfNodes[f].symbol);
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

    public void stateChanged (ChangeEvent e)
    {
        if (debug) System.out.println("StateChanged in SenecaDataset");
        fireChange();    
    }

}