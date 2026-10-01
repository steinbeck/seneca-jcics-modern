/* AtomPropertyPanel.java
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
import javax.swing.table.*;
import javax.swing.event.*;
import javax.swing.border.*;
import java.util.Vector;
import compchem.*;
import seneca.core.*;
import seneca.structgen.*;


/**
  * Allow the user to graphically assign carbon shifts to carbon atoms as well as inherent hydrogens
  */

public class AtomPropertyPanel extends JPanel implements ChangeListener
{
    protected Box centerBox;
    protected Box centerCenterBox;
    protected Box topCenterCenterBox;
    protected Box southBox;
    protected JTable setOfNodesTable;
    protected JTable shiftTable;
    protected JScrollPane scrollpane;
    protected JScrollPane lsp;
    protected JButton autoButton;
    protected JLabel messagelabel;
    protected Border etchedBorder;
    protected Border trustSliderBorder;
    protected Vector carbonShifts;
    protected SenecaDataset sd;
	protected int hcount = 0;
		
    /**
     *
     **/
    protected String[] DEPTPhaseDescriptors = {"none", "positive", "negative",};

    AtomPropertyPanel(SenecaDataset sd)
    {
        super();
        this.sd = sd;
        //if (sd.hyse == null)
        //    sd.hyse = new BondOrderJudge();
        carbonShifts = new Vector();
        sd.addChangeListener(this);
        setLayout(new BorderLayout());
        etchedBorder = BorderFactory.createEtchedBorder();
        centerBox = new Box(BoxLayout.X_AXIS);
        centerCenterBox = new Box(BoxLayout.Y_AXIS);
        topCenterCenterBox = new Box(BoxLayout.X_AXIS);
        southBox = new Box(BoxLayout.X_AXIS);
        setOfNodesTable = new JTable(new SetOfNodesTableModel());
        shiftTable = new JTable(new Carbon1DTableModel());
        
        scrollpane = new JScrollPane(setOfNodesTable);
        scrollpane.setBorder(BorderFactory.createTitledBorder(etchedBorder, "Atom Properties"));
        scrollpane.setPreferredSize(new Dimension(200, 400));
        lsp = new JScrollPane(shiftTable);
        lsp.setBorder(BorderFactory.createTitledBorder(etchedBorder, "Carbon Shifts"));
        lsp.setPreferredSize(new Dimension(200, 400));
        
        centerBox.add(scrollpane);
        centerBox.add(lsp);
        
        autoButton = new JButton("Autoassignment");
        autoButton.addActionListener(new AutoAssignAction());
        
        messagelabel = new JLabel();
		messagelabel.setBorder(BorderFactory.createEmptyBorder(0,30, 0, 0));
        southBox.add(autoButton);
        southBox.add(messagelabel);
        add("Center", centerBox);
        add("South", southBox);
    }


    private void loadCarbonShifts()
    {
        carbonShifts.clear();
        for (int f = 0; f < sd.carbon1D.size(); f++)
        {
            carbonShifts.addElement( new Float(
                                       ((NMRSignal) sd.carbon1D.getSignal(f)).shift[0]));
        }

    }
	
    public void updateMessageLabel()
    {
		int is = new MFAnalyser(sd.getSetOfNodes()).getAtomCount("H");
		int shouldBe = new MFAnalyser(sd.getMolecularFormula()).getAtomCount("H");
		if (is != shouldBe) messagelabel.setForeground(Color.red);
		else
		{
			for (int i = 0; i < sd.getSetOfNodes().length; i++)
			{
				if (sd.setOfNodes[i].HCount < 0) sd.setOfNodes[i].HCount = 0;
			}
			messagelabel.setForeground(new Color((Color.green).getRGB()).darker()); messagelabel.setForeground(new Color((Color.green).getRGB()).darker());
		}
    	String msg = "HCount in molecular formula: " + shouldBe;
    	msg += " - Sum of HCounts in this table: " + is;
		messagelabel.setText(msg);
		setOfNodesTable.repaint();
		shiftTable.repaint();		
		
    	
    }

    public void stateChanged(ChangeEvent e)
    {
        SenecaDataset sd = (SenecaDataset) e.getSource();
        System.out.println("State changed in SenecaDataset");
    }
    
    class AutoAssignAction extends AbstractAction 
    {
        /**
         *
         **/
        AutoAssignAction()
        {
            super("AutoAssign");
        }

        /**
         *
         **/
        public void actionPerformed(ActionEvent e)
        {
	 		new CarbonShiftAssigner(sd).assign();
    	    setOfNodesTable.repaint();
        }
    }



    class Carbon1DTableModel extends AbstractTableModel 
    {
        /**
         *
         **/
        String[] names = {"[ppm]", "Int.", "DEPT-90", "Int.", "DEPT-135", "Int."};

        /**
         *
         **/
        Carbon1DTableModel()
        {
        }

        /**
         *
         **/
        public int getColumnCount()
        {
            return names.length;
        }

        /**
         *
         **/
        public int getRowCount()
        {
            if (sd.carbon1D == null) return 0;
            return sd.carbon1D.size();
        }

        /**
         *
         **/
        public Object getValueAt(int row, int column)
        {
            NMRSignal signal;
            switch(column)
            {
                case 0:
                    signal = (NMRSignal)sd.carbon1D.getSignal(row);
                    if(signal == null)
                        return "-";
                    else
                        return String.valueOf(signal.shift[NMRSignal.DIM_ONE - 1]);
                case 1:
                    signal = (NMRSignal)sd.carbon1D.getSignal(row);
                    if(signal == null)
                        return "";
                    else
                        return String.valueOf(signal.intensity * signal.normalizeFactor);
	            case 2:
		            if (row < sd.dept90.size())
		            {
		                signal = (NMRSignal)sd.dept90.getSignal(row);
		                if(signal == null)
		                    return "-";
		                else
		                    return String.valueOf(signal.shift[NMRSignal.DIM_ONE - 1]);
		            }
					else return "-";
	            case 3:
		            if (row < sd.dept90.size())
		            {
		                signal = (NMRSignal)sd.dept90.getSignal(row);
		                if(signal == null)
		                    return "";
		                else
		                    return String.valueOf(signal.intensity * signal.normalizeFactor);
		            }
					else return "-";
                case 4:
	                if (row < sd.dept135.size())
	                {
	                    signal = (NMRSignal)sd.dept135.getSignal(row);
	                    if(signal == null)
	                        return "-";
	                    else
	                        return String.valueOf(signal.shift[NMRSignal.DIM_ONE - 1]);
	                }
					else return "-";					
                case 5:
                    if (row < sd.dept135.size())
					{
						signal = (NMRSignal)sd.dept135.getSignal(row);
	                    if(signal == null)
	                        return "";
	                    else
	                        return String.valueOf(signal.intensity  * signal.normalizeFactor);
					}	
					else return "-";					
            }
            return "-";
        }

        /**
         *
         **/
        public String getColumnName(int column)
        {
            return names[column];
        }

        /**
         *
         **/
        public Class getColumnClass(int c)
        {
            return getValueAt(0,c).getClass();
        }

        /**
         *
         **/
        public boolean isCellEditable(int row, int col)
        {
            return true;
        }

        /**
         *
         **/
        public void setValueAt(Object aValue, int row, int column)
        {
        }

        /**
         *
         **/
//        private int search(String s)
//        {
//            for(int f = 0;f < DEPTPhaseDescriptors.length;f++)
//            {
//                if(DEPTPhaseDescriptors[f].equals(s))
//                    return f;
//            }
//            return 0;
//        }

    }


    class SetOfNodesTableModel extends AbstractTableModel 
    {
        /**
         *
         **/
        String[] names = {"No.", "Symbol", "Hydrogens", "Assigned 13C Shift"};

        /**
         *
         **/
        SetOfNodesTableModel()
        {
        }

        /**
         *
         **/
        public int getColumnCount()
        {
            return names.length;
        }

        /**
         *
         **/
        public int getRowCount()
        {
            if (sd.getSetOfNodes() == null) return 0;
            return sd.getSetOfNodes().length;
        }

        /**
         *
         **/
        public Object getValueAt(int row, int column)
        {
            Atom[] setOfNodes = sd.getSetOfNodes();
            if (setOfNodes == null) return "";
            switch(column)
            {
                case 0:
                    return String.valueOf(row + 1);
                case 1:
                    return String.valueOf(setOfNodes[row].symbol);
                case 2:
					  updateMessageLabel();
                	  if (setOfNodes[row].HCount < 0) return "undefined";
                    else return String.valueOf(setOfNodes[row].HCount);
                case 3:
                    if (setOfNodes[row].getPhysicalProperty(Atom.PHYS_PROP_13C_SHIFT) != null)
                        return String.valueOf(setOfNodes[row].getPhysicalProperty(Atom.PHYS_PROP_13C_SHIFT));
            }
            return "-";
            
        }

        /**
         *
         **/
        public String getColumnName(int column)
        {
            return names[column];
        }

        /**
         *
         **/
        public Class getColumnClass(int c)
        {
            return getValueAt(0,c).getClass();
        }

        /**
         *
         **/
        public boolean isCellEditable(int row, int col)
        {
            if (col > 1) return true;
			return false;
        }

        /**
         *
         **/
        public void setValueAt(Object aValue, int row, int column)
		{
			if (column == 2)
			{
				try
				{
					int i = Integer.parseInt((String)aValue);
					if (i >= 0 && i <= 4)
					{
						sd.setOfNodes[row].HCount = i;
					}
				}catch(Exception exc)
				{
				}								
				
			}
			if (column == 3)
			{
				sd.setOfNodes[row].setPhysicalProperty(Atom.PHYS_PROP_13C_SHIFT, new Float((String)aValue));
			}
			System.out.println("now");
		  updateMessageLabel();			
			
        }

    }



}