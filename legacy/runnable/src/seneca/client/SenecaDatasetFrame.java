/* SenecaDatasetFrame.java
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
import java.io.*;
import compchem.*;
import seneca.structgen.*;
import seneca.judges.*;
import seneca.core.*;

import org.apache.log4j.gui.*;

import java.util.Vector;

/**
 *
 **/
public class SenecaDatasetFrame extends JInternalFrame 
{
    /**
     *
     **/
    /* The idea is that this SenecaDatasetFrame should be open as long as a this dataset
       handled. That means, any form manipulating the data in this dataset must register
       with this SenecaDatasetFrame as an EventListener */
    protected transient EventListenerList changeListeners = new EventListenerList();

    /**
     *
     **/
    boolean DEBUG = true;
    
    boolean isClosed = false; 

    /**
     *
     **/
    SenecaDataset sd;

    /**
     *
     **/
    StandardIsotopes si;

    /**
     *
     **/
    private String MF = "";
    
    JDesktopPane jdp;

    /** Constructs a dataset properties sheet from SenecaDataset number 'thisone'
     * of the vector 'dsvect'*/
    public SenecaDatasetFrame(SenecaDataset sd)
    {
        super(sd.getName(),true,true,true,true);
        this.sd = sd;
        si = new StandardIsotopes();
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addInternalFrameListener(new WindowManager());
        getContentPane().setLayout(new BorderLayout());
        JPanel southPanel = new JPanel();
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(new CloseAction());
        southPanel.add(closeButton);
        setPreferredSize(new Dimension(500,300));
        JTabbedPane tabbedPane = new JTabbedPane();
        JTable table1 = new JTable(new GeneralTableModel("General"));
        JPanel panel1 = new JPanel();
        panel1.setLayout(new BorderLayout());
        panel1.add(table1.getTableHeader(),BorderLayout.NORTH);
        panel1.add(table1,BorderLayout.CENTER);
        tabbedPane.addTab("General Settings",null,panel1,"General Settings");
        JPanel panel2 = new JPanel();
        panel2.setLayout(new BorderLayout());
        JTabbedPane spectraTabbedPane = new JTabbedPane();
        spectraTabbedPane.addTab("1D Carbon",null, new NMRSpectrumTablePanel(sd, sd.carbon1D),"1D Carbon");
        spectraTabbedPane.addTab("DEPT-90",null,new NMRSpectrumTablePanel(sd, sd.dept90),"DEPT-90");
        spectraTabbedPane.addTab("DEPT-135",null,new NMRSpectrumTablePanel(sd, sd.dept135),"DEPT-135");				
        spectraTabbedPane.addTab("H,C-HetCor",null,new NMRSpectrumTablePanel(sd, sd.hetcor),"H,C-HetCor");
        spectraTabbedPane.addTab("H,C-lr-HetCor",null,new NMRSpectrumTablePanel(sd, sd.hetcorlr),"H,C-lr-HetCor");
        spectraTabbedPane.addTab("H,H-COSY",null,new NMRSpectrumTablePanel(sd, sd.hhcosy),"H,H-COSY");
        tabbedPane.addTab("NMR Spectra",null,spectraTabbedPane,"NMR Spectra");
        tabbedPane.addTab("Atom Properties",null,new AtomPropertyPanel(sd),"Atom Properties");
        JPanel panel3 = new JPanel();
        panel3.setLayout(new BorderLayout());
        tabbedPane.addTab("Annealing Schedule",null,new AnnealingScheduleConfigurator(sd),"Annealing Schedule");
		tabbedPane.addTab("Judges",null,getJudgeTabbedPane(),"Judges");
        tabbedPane.addTab("Serverlist",null,new ComputeServerConfigurator(sd),"Serverlist");
        tabbedPane.addTab("Structure Generation",null,new SGServerPanel(sd),"Structure Generation");		
		Component logPane = ((TextPaneAppender)Seneca.a4).getTextPane();
		logPane.setBackground(Color.white);
		tabbedPane.addTab("Logbook", null, new JScrollPane(logPane), "LogBook");
        getContentPane().add("Center",tabbedPane);
        getContentPane().add("South",southPanel);
        setVisible(true);
        
    }
	
	private JTabbedPane getJudgeTabbedPane()
	{
		JTabbedPane judgeTabbedPane = new JTabbedPane();

		judgeTabbedPane.addTab("H,H-COSY",null,new HHCOSYJudgeConfigurator(sd),"H,H-COSY");
		judgeTabbedPane.addTab("C,H-long-range Corr.",null,new HMBCJudgeConfigurator(sd),"C,H-long-range Corr.");
		judgeTabbedPane.addTab("Carbon Shifts",null,new HOSECodeJudgeConfigurator(sd),"Shifts");
		judgeTabbedPane.addTab("Symmetric Atoms Classes",null,new SymmetryJudgeConfigurator(sd),"Symmetric Atoms Classes");
		judgeTabbedPane.addTab("Features",null,new FeatureJudgeConfigurator(sd),"Features");
		judgeTabbedPane.addTab("Hybridization",null,new HybridizationJudgeConfigurator(sd),"Hybridization");
		judgeTabbedPane.addTab("Heterobond Attachments",null,new HeteroBondsJudgeConfigurator(sd),"Heterobond Attachments");
		return judgeTabbedPane;			
	}

    /**
     *
     **/
    protected void tryExit()
    {
        FileWriter fw = null;
        if(isClosed) return; 
        /* I had to use a selfmade flag here. JInternalFrame.isClosed()
           does seem to have a different function */
        final JFileChooser fc = new JFileChooser();
        String title = "Close " + sd.getName() + "?";
        String msg = "You are about to close this dataset.\n";
        msg += "Do you want to save your changes?";
        int answer = JOptionPane.showConfirmDialog(this,msg,title,JOptionPane.YES_NO_CANCEL_OPTION);
        if(answer == JOptionPane.NO_OPTION)
        {
            //this.setVisible(false);
            try
            {
                isClosed = true;
                sd.destroy();
                dispose();
            }
            catch(Exception exc)
            {
                System.err.println("There's a problem closing this internal frame.");
            }
        }
        else
            if(answer == JOptionPane.YES_OPTION)
            {
                int returnVal = fc.showSaveDialog(SenecaDatasetFrame.this);
                if(returnVal == JFileChooser.APPROVE_OPTION)
                {
                    File file = fc.getSelectedFile();
                    //this is where a real application would save the file.
                    System.out.println("Saving: " + file.getName() + "." + "\n");
                    //this.setVisible(false);
                    try
                    {
  		               		fw = new FileWriter(file);
	                  		CMLGenerator cmlGen = new CMLGenerator();
                     		cmlGen.setVerbose(true);
                        fw.write(cmlGen.convert(sd));
                        fw.flush();
                        fw.close();
                        isClosed = true;
                        System.out.println("File saved successfully");
                        sd.destroy();
                        dispose();
                    }
                    catch(Exception exc)
                    {
                        System.err.println("There's a problem closing this internal frame.\n");
                        System.err.println(exc.toString());
                    }
                }
                else
                {
                    System.out.println("Save command cancelled by user." + "\n");
                }
            }
    }
    
    

    class CloseAction extends AbstractAction 
    {
        /**
         *
         **/
        CloseAction()
        {
            super("close");
        }

        /**
         *
         **/
        public void actionPerformed(ActionEvent e)
        {
            tryExit();
        }

    }

    class GeneralTableModel extends AbstractTableModel 
    {
        /**
         *
         **/
        String[] names = {"Parameter", "Setting"};

        /**
         *
         **/
        String settingsType;

        /**
         *
         **/
        GeneralTableModel(String s)
        {
            settingsType = s;
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
            return 4;
        }

        /**
         *
         **/
        public Object getValueAt(int row, int column)
        {
            switch(row)
            {
                case 0:
                    if(column == 0)
                        return "Filename";
                    else
                        return sd.getName();
                case 1:
                    if(column == 0)
                        return "Description";
                    else
                        return sd.getDescription();
                case 2:
                    if(column == 0)
                        return "Molecular Formula";
                    else
                        return sd.getMolecularFormula();
                case 3:
                    if(column == 0)
                        return "Molecular Mass";
                    else
                    {
                        if (sd.getMolecularFormula() != null && sd.getMolecularMass() == 0)
                            return new Float(DataStructureTools.getMass(sd.getMolecularFormula()));
                        return new Double(sd.getMolecularMass());
                    }
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
            if(col == 0)
                return false;
            else
                return true;
        }

        /**
         *
         **/
        public void setValueAt(Object aValue, int row, int column)
        {
            switch(row)
            {
                case 0:
                    if(column == 1)
                        sd.setName((String)aValue);
                    setTitle((String)aValue);
                    break;
                case 1:
                    if(column == 1)
                        sd.setDescription((String)aValue);
                    break;
                case 2:
                    if(column == 1)
                    {
                        sd.setMolecularFormula((String)aValue);
                        fireTableCellUpdated(row, column);
                    }
                    break;
                case 3:
                    if(column == 1)
                    {
                        sd.setMolecularMass(new Float((String)aValue).floatValue());
                        break;
                    }
            }
        }

    }


    class WindowManager extends InternalFrameAdapter 
    {
        /**
         *
         **/
        public void internalFrameClosing(InternalFrameEvent e)
        {
            System.out.println("JInternalFrame closing...");
            tryExit();
        }

    }


    public void addChangeListener(ChangeListener x)
    {
        changeListeners.add(ChangeListener.class,x);
        // bring it up to date with current state
        x.stateChanged(new ChangeEvent(this));
    }

    /**
     *
     *
     * @param   x
     */
    public void removeChangeListener(ChangeListener x)
    {
        changeListeners.remove(ChangeListener.class,x);
    }

    /**
     *
     *
     */
    protected void fireChange()
    {
        // Create the event:
        ChangeEvent c = new ChangeEvent(this);
        // Get the listener list
        Object[] listeners = changeListeners.getListenerList();
        // Process the listeners last to first
        // List is in pairs, Class and instance
        for(int i = listeners.length - 2;i >= 0;i -= 2)
        {
            if(listeners[i] == ChangeListener.class)
            {
                ChangeListener cl = (ChangeListener)listeners[i + 1];
                cl.stateChanged(c);
            }
        }
    }

/* Listener notification support methods END here */
}