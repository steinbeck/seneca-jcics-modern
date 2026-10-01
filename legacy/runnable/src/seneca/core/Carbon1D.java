/* Carbon1D.java
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

import java.util.Vector;

/** Class to store the four different types of carbon-13 spectra (Broadband-decoupled, 
  * DEPT135, DEPT90, DEPT45). A vector object is supplied for each spectrum. 
  * Sorting is performed in each spectrum so that the signal with the highest chemical 
  * shift is stored at position zero in the vector.
  */

public class Carbon1D extends NMRSpectrum implements java.io.Serializable
{ 

    public static final int DEPT_POSITIVE = 1, DEPT_NEGATIVE = 2, DEPT_NULL = 0;
    public static final int BB = 0, GD = 1, DEPT135 = 2, DEPT90 = 3, DEPT45 = 4;
    public int DEPT135Phase = DEPT_NULL, DEPT90Phase = DEPT_NULL, DEPT45Phase = DEPT_NULL;
    static String[] nucleus = {NMRSpectrum.NUC_CARBON};
    
    /** Initializes the Carbon1D spectra collection by creating a vector for each of the 
        four spectra types to store */ 
    Carbon1D(){
        super(nucleus, "carbon1d");
        specType = "Carbon1D";
    }

 
    /** Adds the NMRSignal n to the spectrum (BB, DEPT135, etc.) specified by spectrum */
    public void addSignal(CNMRSignal n){
        fireChange();
        addElement(n);        
        updateShiftLists();        
    }
    
    /** Replaces the signal at position pos with the signal sig */
    public void replaceSignalAt(CNMRSignal sig, int pos){
        fireChange();
        setElementAt(sig, pos);
        updateShiftLists();        
    }
}