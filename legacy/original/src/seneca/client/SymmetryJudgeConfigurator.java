/* SymmetryJudgeConfigurator.java
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

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import javax.swing.table.*;
import java.util.Vector;
import seneca.structgen.*;
import seneca.judges.*;
import seneca.core.*;
import compchem.*;



/**
 * This class provides a gui for configuring a SymmetryJudgeConfigurator
 */
public class SymmetryJudgeConfigurator extends JudgeConfigurator
{
	SymmetryJudge symJudge = null;
    
	SymmetryJudgeConfigurator(SenecaDataset sd)
    {
        super(sd, (SymmetryJudge)sd.getJudge("SymmetryJudge"));
		this.symJudge = (SymmetryJudge)sd.getJudge("SymmetryJudge");
    }

    protected Box contructCenterBox()
    {
    	Box centerBox = new Box(BoxLayout.X_AXIS);			
		table = new JTable(new SymmetryTableModel())		;
    	scrollpane = new JScrollPane(table);
    	scrollpane.setBorder(getTitledBorder());
    	scrollpane.setPreferredSize(new Dimension(200, 400));
    	centerBox.add(scrollpane);

    	return centerBox;

    }


    class SymmetryTableModel extends AbstractTableModel
    {

        SymmetryTableModel()
        {}

        public int getColumnCount()
        {
	        //String[] names = {"No.", "Symb.", "Shift", "sp3", "sp2", "sp"};
	        if (sd.setOfNodes == null || symJudge == null)
	            return 0;
            return sd.setOfNodes.length + 3;
        }

        public int getRowCount()
        {
            if (sd.setOfNodes == null || symJudge == null)
                return 0;
            return symJudge.getSymmetryClassCount();
        }

        public Object getValueAt(int row, int column)
        {
            Atom node;
            switch (column)
            {
                case 0:
                    return String.valueOf(row + 1);
                case 1:
                    return sd.setOfNodes[row].symbol;
                case 2:
					return String.valueOf(sd.setOfNodes[symJudge.getClassMember(row, 1)].getPhysicalProperty(Atom.PHYS_PROP_13C_SHIFT));
                default:
//					System.out.println(row + " - " + column);
					boolean[] symmetryClass = symJudge.getSymmetryClass(row);
//					System.out.println("Symmetryclass size: " + symmetryClass.length);
                    return new Boolean(symmetryClass[column - 3]);
            }
        }

        public String getColumnName(int column)
        {
           switch (column)
            {
                case 0:
                    return "No.";
                case 1:
                    return "Symb.";
                case 2:
					return "Shift";
                default:
                    return new String(sd.setOfNodes[column - 3].symbol + (column - 2));
            }
        }

        public Class getColumnClass(int c)
        {
            return getValueAt(0, c).getClass();
        }

        public boolean isCellEditable(int row, int col)
        {
            if (col < 4)
                return false;
            else
                return true;
        }

        public void setValueAt(Object aValue, int row, int column)
        {
            switch (column)
            {
                case 0:
                    // You can't change the numbering
                    break;
                case 1:
                    // You can't change the symbols.
                    break;
                case 2:
                        // You can't change the shift values
                        break;
				default:
					break;
            }
			fireTableDataChanged();
        }

    }
	
    public void autoconfigure()
    {
		if (sd.setOfNodes == null) return;
		symJudge.clearSymmetryClasses();
    	System.out.println("Autoconfiguring " + judge.name);
    	float shift1 = 0, shift2 = 0;
    	boolean symClassExists = false;
    	boolean[] symClass = new boolean[sd.setOfNodes.length];
    	for (int f = 0; f < sd.setOfNodes.length; f++)
    	{
    		if (sd.setOfNodes[f].getPhysicalProperty(Atom.PHYS_PROP_13C_SHIFT) != null)
    		{
    		   shift1 = ((Float)sd.setOfNodes[f].getPhysicalProperty(Atom.PHYS_PROP_13C_SHIFT)).floatValue();
    		}
    		for (int g = f + 1; g < sd.setOfNodes.length; g++)
    		{
    			if (sd.setOfNodes[g].getPhysicalProperty(Atom.PHYS_PROP_13C_SHIFT) != null)
    			{
    			   shift2 = ((Float)sd.setOfNodes[g].getPhysicalProperty(Atom.PHYS_PROP_13C_SHIFT)).floatValue();
    			
	    			if (shift1 == shift2)
	    			{
	    				if (!symClassExists)
	    				{
	    					symClassExists = true;
	    					symClass = new boolean[sd.setOfNodes.length];
	    					for (int i = 0; i < symClass.length; i ++) symClass[i] = false;
	    					symClass[f] = true;
	    				}
	    				symClass[g] = true;
	    			}
    			}
    		}
	   		if (symClassExists) symJudge.addSymmetryClass(symClass);
    		symClassExists = false;	
    	}	
    	table.setModel(new SymmetryTableModel());		
    	scrollpane.revalidate();
    }
}
