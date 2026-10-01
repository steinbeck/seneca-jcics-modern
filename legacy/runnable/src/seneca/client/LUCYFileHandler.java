/* LUCYFileHandler.java
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

/**
 * A class to provide methods for all file i/o that is done
 * in SENECA
 */

import java.util.*;
import java.awt.*;
import javax.swing.*;
import javax.swing.filechooser.*;
import java.io.*;
import compchem.*;
import seneca.core.*;

public class LUCYFileHandler
{
    boolean debug = false;

    /**
     *  Reads a SenecaDataset from a LUCY input file.
     */
    public  SenecaDataset importLUCYDataset(File file)
    {
        boolean[][] hmbcTable;
        boolean[][] hhcosyTable;
        float carbonshift, protonshift1, protonshift2, carbonshift1,
        carbonshift2;
        float[] hetcorshifts = new float[2];
        float[] carbonshifts;
        NMRSignal tempSignal1 = null, tempSignal2 = null,
        tempSignal3 = null;
        boolean done = false;
        Vector pickedSignals = null;
        Vector pickedSignals1 = null;
        Vector pickedSignals2 = null;
        NMRSignal cnmrSignal = null;
        NMRSignal dept90Signal = null;
        NMRSignal dept135Signal = null;
						
        String mf = "", filename = "", thisLine = "";
        int heavyAtomsCount = 0, elementCount = 0, tempInt = 0,
        carbonCount, i = 0, j = 0, val = 0;
        StringTokenizer st = new StringTokenizer("");
        filename = file.getParent() + File.separator + file.getName();
        SenecaDataset newDataset = new SenecaDataset(file.getName());
        try
        {
            FileReader fr = new FileReader(filename);
            BufferedReader br = new BufferedReader(fr);
            // Read the version line and forget it
            br.readLine();
            // read line 2 containing the molecular formula string and
            // create a set of Nodes based on this information
            thisLine = br.readLine().trim();
            if (debug)
                System.out.println("Header: " + thisLine);
            MFAnalyser mfa = new MFAnalyser(thisLine);
            Atom[] setOfNodes = mfa.getSetOfHeavyNodes();
            newDataset.setMolecularFormula(thisLine);
            newDataset.setMolecularMass(mfa.getMass());
            setOfNodes = newDataset.getSetOfNodes();
            // read line 3 stating the number of heavy (non-hydrogen) atoms in the molecule
            heavyAtomsCount = Integer.parseInt(br.readLine().trim());
            // read line 4 stating the number of elements in the molecule
            elementCount = Integer.parseInt(br.readLine().trim());
            // read the following line with the MF taken apart
            for (int f = 0; f < elementCount; f ++)
            {
                br.readLine();
            }

            carbonCount = Integer.parseInt(br.readLine().trim());
            // read the following line with the MF taken apart
            for (int f = 0; f < elementCount - 1; f++)
            {
                br.readLine();
            }

            // read the following lines with atom characteristics and throw away everything but the
            // number of implicit hydrogens per heavy atom
            if (debug)
                System.out.println("Parsing atom characteristics...");
            for (int f = 0; f < heavyAtomsCount; f ++)
            {
                st = new StringTokenizer(br.readLine().trim());
                st.nextToken();
                setOfNodes[f].HCount = Integer.parseInt(st.nextToken().trim());
            }
            // skip the lines listing known bonds (we skip these unless we can handle them in SENECA)
            tempInt = Integer.parseInt(br.readLine().trim());
			if (debug)
			{
	            for (int f = 0; f < tempInt; f ++)
	            {
	                System.out.println(br.readLine());
	            }
			}
            // skip the listing of elementsymbols
            for (int f = 0; f < heavyAtomsCount; f ++)
            {
                br.readLine();
            }
            // extract the carbon chemical shifts from the CH-COSY information
            if (debug)
                System.out.println("Parsing CH-correlation table...");
            carbonshifts = new float[carbonCount];
            for (int f = 0; f < carbonCount; f ++)
            {
                st = new StringTokenizer(br.readLine().trim());
                carbonshift = Float.parseFloat(st.nextToken().trim());
                protonshift1 = Float.parseFloat(st.nextToken().trim());
                protonshift2 = Float.parseFloat(st.nextToken().trim());
                carbonshifts[f] = carbonshift;
				cnmrSignal = new NMRSignal(NMRSpectrum.SPECTYPE_BB);
				cnmrSignal.setShift(carbonshift, 0);
				cnmrSignal.intensity = 1;															
				dept90Signal = new NMRSignal(NMRSpectrum.SPECTYPE_BB);											
				dept90Signal.setShift(carbonshift, 0);
				dept135Signal = new NMRSignal(NMRSpectrum.SPECTYPE_BB);											
				dept135Signal.setShift(carbonshift, 0);				
                switch (setOfNodes[f].HCount)
                {
                    case 0:
                        break;
                    case 1:
						dept90Signal.phase = NMRSignal.PHASE_POSITIVE;
						dept135Signal.phase = NMRSignal.PHASE_POSITIVE;						
						dept90Signal.intensity = 1;
						dept135Signal.intensity = 1;
		                newDataset.dept90.addSignal(dept90Signal);
			            newDataset.dept135.addSignal(dept135Signal);							
                        break;
                    case 2:
                        dept135Signal.phase = NMRSignal.PHASE_NEGATIVE;						
                        dept135Signal.intensity = -1;
                        newDataset.dept135.addSignal(dept135Signal);							
                        break;
                    case 3:
                        dept135Signal.phase = NMRSignal.PHASE_POSITIVE;						
                        dept135Signal.intensity = 1;
                        newDataset.dept135.addSignal(dept135Signal);							
                        break;
                }

                newDataset.carbon1D.addSignal(cnmrSignal);
                if (protonshift1 != 0)
                {
                    tempSignal1 = new NMRSignal(NMRSpectrum.SPECTYPE_HSQC);
                    tempSignal1.setShift(carbonshift,
                                         NMRSpectrum.NUC_CARBON);
                    tempSignal1.setShift(protonshift1,
                                         NMRSpectrum.NUC_PROTON);
                    newDataset.hetcor.addSignal(tempSignal1);
                }
                if (protonshift2 != 0)
                {
                    tempSignal1 = new NMRSignal(NMRSpectrum.SPECTYPE_HSQC);
                    tempSignal1.setShift(carbonshift,
                                         NMRSpectrum.NUC_CARBON);
                    tempSignal1.setShift(protonshift2,
                                         NMRSpectrum.NUC_PROTON);
                    newDataset.hetcor.addSignal(tempSignal1);
                }
                
            }
            /* Set the Hydrogen Count of all atoms to -1, which means 'undefined' as oposed to 
			   '0 hydrogens attached', which already is a statement */
            for (j = 0; j < heavyAtomsCount; j ++)
            {
            	setOfNodes[j].HCount = -1;
            }
            hhcosyTable = new boolean[heavyAtomsCount][heavyAtomsCount];
            hmbcTable = new boolean[heavyAtomsCount][heavyAtomsCount];
            for (i = 0; i < heavyAtomsCount; i ++)
                for (j = 0; j < heavyAtomsCount; j ++)
                    hhcosyTable[i][j] = false;
            for (i = 0; i < heavyAtomsCount; i ++)
                for (j = 0; j < heavyAtomsCount; j ++)
                    hmbcTable[i][j] = false;

            // Read the HMBC information
            tempInt = Integer.parseInt(br.readLine().trim());
            for (int f = 0; f < tempInt; f ++)
            {
                st = new StringTokenizer(br.readLine().trim());
                i = Integer.parseInt(st.nextToken().trim());
                j = Integer.parseInt(st.nextToken().trim());
                hmbcTable[i - 1][j - 1] = true;
                hmbcTable[j - 1][i - 1] = true;
            }
            // Read the HHCOSY information
            tempInt = Integer.parseInt(br.readLine().trim());
            for (int f = 0; f < tempInt; f ++)
            {
                st = new StringTokenizer(br.readLine().trim());
                i = Integer.parseInt(st.nextToken().trim());
                j = Integer.parseInt(st.nextToken().trim());
                hhcosyTable[i - 1][j - 1] = true;
                hhcosyTable[j - 1][i - 1] = true;
            }
            fr.close();
            if (debug) System.out.println("Converting HMBC boolean table to HMBC spectrum...");
            for (i = 0; i < hmbcTable.length; i ++)
            {
                for (j = i + 1; j < hmbcTable.length; j ++)
                {
                    if (hmbcTable[i][j])
                    {
                        /* Here we assume that the first atom's chemical shift
                            * is the carbon shift and that the we can lookup the carbon
                            * shift if atom 2 in the HSQC in order to get it's corresponding
                            * proton shift. If nothing is found, we need to reverse the pair.
                            */
                        done = false;
                        if (debug) System.out.println(
                          "HMBC Correlation found between carbon number " + (i + 1) +  " at " +
                          carbonshifts[i] + " and number  " + (j + 1) +  " at " + carbonshifts[j] + " ppm.");
                        for (int f = 0; f < 2; f ++)
                        {
                            if (f == 0)
                            {
                                pickedSignals =
                                  newDataset.hetcor.pickSignals(
                                    carbonshifts[j],
                                    NMRSpectrum.NUC_CARBON,
                                    (float) 0.01);
                                if (pickedSignals.size() > 0)
                                {
                                    for (int g = 0;
                                            g < pickedSignals.size(); g ++)
                                    {
                                        tempSignal1 = (NMRSignal)
                                                      pickedSignals.elementAt(g);
                                        if (debug) System.out.println(
                                          "Adding HMBC Correlation between carbon at " +
                                          carbonshifts[i] +
                                          " and proton at" +
                                          tempSignal1.getShift(
                                            NMRSpectrum.NUC_PROTON)
                                          + " ppm.");
                                        tempSignal2 = new NMRSignal(
                                                        NMRSpectrum.SPECTYPE_HMQC);
                                        tempSignal2.setShift(
                                          carbonshifts[i],
                                          NMRSpectrum.NUC_CARBON);
                                        tempSignal2.setShift(
                                          tempSignal1.getShift(
                                            NMRSpectrum.NUC_PROTON)
                                          , NMRSpectrum.NUC_PROTON);
                                        newDataset.hetcorlr.addSignal(
                                          tempSignal2);
                                        if (debug) System.out.println(tempSignal2);
                                    }
                                    break;
                                }
                                else
                                    if (debug) System.out.println("Found " +
                                                       pickedSignals.size() +
                                                       " signals with shift " +
                                                       carbonshifts[j]);
                            }
                            else
                            {
                                if (debug) System.out.println("Thus reverting assignment...");
                                pickedSignals =
                                  newDataset.hetcor.pickSignals(
                                    carbonshifts[i],
                                    NMRSpectrum.NUC_CARBON,
                                    (float) 0.01);
                                if (debug) System.out.println("Found " +
                                                   pickedSignals.size() +
                                                   " signals with shift " +
                                                   carbonshifts[i]);
                                if (pickedSignals.size() > 0)
                                {
                                    for (int g = 0;
                                            g < pickedSignals.size(); g ++)
                                    {
                                        tempSignal1 = (NMRSignal)
                                                      pickedSignals.elementAt(g);
                                        if (debug) System.out.println(
                                          "Adding HMBC Correlation between carbon at " +
                                          carbonshifts[j] +
                                          " and proton at " +
                                          tempSignal1.getShift(
                                            NMRSpectrum.NUC_PROTON)
                                          + " ppm.");
                                        tempSignal2 = new NMRSignal(
                                                        NMRSpectrum.SPECTYPE_HMQC);
                                        tempSignal2.setShift(
                                          carbonshifts[j],
                                          NMRSpectrum.NUC_CARBON);
                                        tempSignal2.setShift(
                                          tempSignal1.getShift(
                                            NMRSpectrum.NUC_PROTON)
                                          , NMRSpectrum.NUC_PROTON);
                                        newDataset.hetcorlr.addSignal(
                                          tempSignal2);
                                        if (debug) System.out.println(tempSignal2);
                                    }
                                }
                            }
                        }
                    }
                }
            }


            for (i = 0; i < hhcosyTable.length; i ++)
            {
                for (j = 0; j < hhcosyTable.length; j ++)
                {
                    if (hhcosyTable[i][j])
                    {
                        /* Here we assume that the first atom's chemical shift
                            * is the carbon shift and that the we can lookup the carbon
                            * shift if atom 2 in the HSQC in order to get it's corresponding
                            * proton shift. If nothing is found, we need to reverse the pair.
                            */
                        done = false;
                        if (debug) System.out.println(
                          "HH-COSY Correlation found between protons attached to carbons at " +
                          carbonshifts[i] + " and " +
                          carbonshifts[j] + " ppm.");
                        pickedSignals1 = newDataset.hetcor.pickSignals(
                                           carbonshifts[i],
                                           NMRSpectrum.NUC_CARBON, (float) 0.01);
                        pickedSignals2 = newDataset.hetcor.pickSignals(
                                           carbonshifts[j],
                                           NMRSpectrum.NUC_CARBON, (float) 0.01);
                        for (int f = 0; f < pickedSignals1.size(); f ++)
                        {
                            for (int g = 0; g < pickedSignals2.size(); g ++)
                            {
                                tempSignal1 = (NMRSignal)
                                              pickedSignals1.elementAt(f);
                                tempSignal2 = (NMRSignal)
                                              pickedSignals2.elementAt(g);
                                tempSignal3 = new NMRSignal(
                                                NMRSpectrum.SPECTYPE_HHCOSY);
                                tempSignal3.setShift(tempSignal1.getShift(
                                                        NMRSpectrum.NUC_PROTON), 0);
                                tempSignal3.setShift(tempSignal2.getShift(
                                                        NMRSpectrum.NUC_PROTON), 1);

                                if (debug) System.out.println(
                                  "Adding HHCOSY Correlation between protons at " +
                                  tempSignal1.getShift(
                                    NMRSpectrum.NUC_PROTON) + " and " +
                                  tempSignal2.getShift(
                                    NMRSpectrum.NUC_PROTON) + " ppm.");
                                newDataset.hhcosy.addSignal(tempSignal3);
                                if (debug) System.out.println(tempSignal3);
                            }
                        }
                    }
                }
            }


        }
        catch (Exception exc)
        {
            System.out.println( "Error while trying to read LUCY file\n" +
                                exc.toString());
        }



        if (debug) newDataset.carbon1D.report();
        if (debug) newDataset.hetcor.report();
        if (debug) newDataset.hetcorlr.report();
        if (debug) newDataset.hhcosy.report();
		newDataset.init();
        return newDataset;
    }
    
    

    /*
          * Get the extension of a file.
          */
    public  String getExtension(File f)
    {
        String ext = null;
        String s = f.getName();
        int i = s.lastIndexOf('.');

        if (i > 0 && i < s.length() - 1)
        {
            ext = s.substring(i + 1).toLowerCase();
        }
        return ext;
    }



}