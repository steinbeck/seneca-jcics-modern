/* SenecaDatasetCollection.java
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

import java.util.Vector;
import javax.swing.*;
import javax.swing.event.*;
import seneca.core.*;

/** SenecaDatasetCollection.java
  * An extension of a simple vector to store SenecaDatasets.
  *  
  */
  
public class SenecaDatasetCollection extends Vector implements ChangeListener{
	
	protected transient EventListenerList changeListeners = new EventListenerList();
	protected int currentDatasetNumber;
	
	public SenecaDatasetCollection(){
	
	}	

	/** Get an String array with the of all */
	public String[] getNames(){
		String[] names = new String[size()];
		for (int f = 0; f < names.length; f++){
			names[f] = ((SenecaDataset)elementAt(f)).getName();
		}
		return names;
	}
	
	public void setCurrentDatasetNumber(int thisOne){
		if (thisOne != currentDatasetNumber){
			currentDatasetNumber = thisOne;
			fireChange();
		}
	}
	
	public void setCurrentDatasetNumber(String thisOne){
		for (int f = 0; f < size(); f++){
			if (((SenecaDataset)elementAt(f)).getName().equals(thisOne) && f != currentDatasetNumber){
				currentDatasetNumber = f;
				fireChange();
				return;
			}
			else if (((SenecaDataset)elementAt(f)).getName().equals(thisOne) && f == currentDatasetNumber) return;
		}
		System.err.println("Error: No such dataset!");
	}

	
	public int getCurrentDatasetNumber(){
		if (size() == 0) return -1;
		return currentDatasetNumber;	
	}
	
	public SenecaDataset getCurrentDataset(){
		if (size() == 0) return null;
		return (SenecaDataset)elementAt(currentDatasetNumber);	
	}
	

	/** Use this method instead of 'addElement' to make sure that a 
	  * ChangeEvent is fired. 		
	  */
	public boolean add(Object obj){
		((SenecaDataset)obj).addChangeListener(this);
		super.addElement(obj);
		setCurrentDatasetNumber(size()-1);
		fireChange();
      return true;
	}

	/** Use this method instead of 'removeElementAt' to make sure that a 
	  * ChangeEvent is fired. 		
	  */
	public Object remove(int f){
		fireChange();
    return super.remove(f);
	}

	/** Use this method instead of 'removeElementAt' to make sure that a 
	  * ChangeEvent is fired. 		
	  */
	public boolean remove(Object o){
		fireChange();
    return super.remove(o);
	}

    // Listener notification support
    public void addChangeListener(ChangeListener x) {
	    changeListeners.add (ChangeListener.class, x);
	     // bring it up to date with current state
         x.stateChanged(new ChangeEvent(this));
    }

    public void removeChangeListener(ChangeListener x) {
    	changeListeners.remove (ChangeListener.class, x);                
    }

    protected void fireChange() {
	    // Create the event:
    	ChangeEvent c = new ChangeEvent(this);
        // Get the listener list
        Object[] listeners = changeListeners.getListenerList();
        // Process the listeners last to first
        // List is in pairs, Class and instance
        for (int i = listeners.length-2; i >= 0; i -= 2) {
        	if (listeners[i] == ChangeListener.class) {
            	ChangeListener cl = (ChangeListener)listeners[i+1];
                cl.stateChanged(c);
            }
        }
    }
    
    /** This collection of SenecaDatasets registers itself with all
      * it members to be informed if on of them changes
	  */ 
   
    public void stateChanged(ChangeEvent e){
    	SenecaDataset sd = (SenecaDataset)e.getSource();
    	if (sd.destroy){
    		System.out.println("SenecaDatasetCollection says: SenecaDataset " + sd.getName() + " is removed now...");
    		System.out.println(remove(sd));
    		sd = null;  
    	}
    	fireChange();
    }
	

}  