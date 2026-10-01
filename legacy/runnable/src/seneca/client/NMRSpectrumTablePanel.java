/* NMRSpectrumTablePanel.java
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
 
import java.io.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.table.*;
import javax.swing.event.*;
import javax.swing.border.*;
import seneca.core.*;

/**
 *
 **/
public class NMRSpectrumTablePanel extends JPanel implements ChangeListener
{
	public boolean debug = false;

    /**
      *
      **/
    public NMRSpectrum nmrSpectrum;

    /**
     *
     **/
    JInternalFrame jif;

    /**
     *
     **/
    protected TableModel tableModel;

    /**
     *
     **/
    protected JTable table;

    /**
     *
     **/
    protected JScrollPane scrollpane;

    /**
     *
     **/
    protected JPanel southPanel;
    
    protected JLabel northLabel;
    /**
     *
     **/
    protected JPanel centerPanel;

    /**
     *
     **/
    protected JButton addSignalButton;

    /**
     *
     **/
    protected JButton delSignalButton;

    /**
     *
     **/
    protected JButton importPPButton;

    /**
     *
     **/
    JComboBox ppSelector;
	
	SenecaDataset sd = null;


    /**
     *
     **/
    NMRSpectrumTablePanel(SenecaDataset sd, NMRSpectrum nmrSpectrum)
    {
        super();
		this.sd = sd;
		if (debug) System.out.println(sd + ", " + nmrSpectrum);
        this.nmrSpectrum = nmrSpectrum;
        if (debug) System.out.println(sd + ", " + this.nmrSpectrum);        
        setLayout(new BorderLayout());
        table = new JTable(new NMRSpectrumTableModel());
        table.setRowSelectionAllowed(true);
		JComboBox phaseSelector = new JComboBox();
		phaseSelector.addItem("NONE");
		phaseSelector.addItem("POSITIVE");
		phaseSelector.addItem("NEGATIVE");
		phaseSelector.setAlignmentX(JComboBox.RIGHT_ALIGNMENT);
		TableColumn phaseColumn = table.getColumnModel().getColumn(nmrSpectrum.dim + 2);
		phaseColumn.setCellEditor(new DefaultCellEditor(phaseSelector));
		setPreferredSize(new Dimension(500, 300));
        scrollpane = new JScrollPane(table);
        scrollpane.setBorder(new BevelBorder(BevelBorder.LOWERED));
        southPanel = new JPanel();
        southPanel.setLayout(new FlowLayout());
        centerPanel = new JPanel();
        centerPanel.setLayout(new BorderLayout());
        addSignalButton = new JButton("Add Signal");
        addSignalButton.addActionListener(new AddSignalAction());
        delSignalButton = new JButton("Delete Signal");
		delSignalButton.addActionListener(new RemoveSignalAction());
        ppSelector = new JComboBox();
        ppSelector.addItem("Import peak picking");
        ppSelector.addItem("Paste WinNMR pp from clipboard");
        ppSelector.addItem("Import GIFA pp from file");
        ppSelector.addActionListener(new ActionListener()
                                     {
                                         /**
                                           *
                                           **/
                                         public void actionPerformed(ActionEvent e)
                                         {
                                             JComboBox cb = (JComboBox) e.getSource();
                                             String doThis = (String) cb.getSelectedItem();
                                         if (doThis.equals("Paste WinNMR pp from clipboard"))
                                                 pasteWinNMRpp();
                                         else if (doThis.equals("Import GIFA pp from file"))
                                                 loadGIFApp();
                                             ppSelector.setSelectedIndex(0);
                                         }

                                     }
                                    );
        southPanel.add(addSignalButton);
        southPanel.add(delSignalButton);
        southPanel.add(ppSelector);
        String s = nmrSpectrum.size() + " Signals in ";
         s += nmrSpectrum.specType + ".";
        northLabel = new JLabel(s);
        add("North", northLabel);
        add("South",southPanel);
        scrollpane.setPreferredSize(new Dimension(400, 400));
        centerPanel.add("Center",scrollpane);
        add("Center",centerPanel);
        sd.addChangeListener(this);
    }

    /**
      *
      **/
    protected void setCellRendering()
    {
        JLabel label = new JLabel();
        TableColumn column;
        table.setRowSelectionAllowed(false);
 
        for (int f = 0; f < table.getColumnCount(); f++)
        {
            column = table.getColumn(table.getColumnName(f));
            label.setVerticalAlignment(JLabel.CENTER);
            label.setHorizontalAlignment(JLabel.CENTER);
        }
    }


    /**
     *    loads a peakpicking from a file produce by GIFA
     *    (http://tome.cbs.univ-montp1.fr/GIFA/)
     **/
    public void loadGIFApp()
    {
//        System.out.println("Importing GIFA peakpicking");
//        CNMRSignal cnmrsig = new CNMRSignal();
//        int number = 0;
//        float ppm = 0;
//        float procInt = 0;
//        String thisLine = "", filename;
//        StringTokenizer st = new StringTokenizer("");
//        JFileChooser fc = new JFileChooser();
//        fc.showOpenDialog(null);
//        if(fc.getSelectedFile() == null)
//            return;
//        try
//        {
//            FileReader fr = new FileReader(fc.getSelectedFile());
//            BufferedReader br = new BufferedReader(fr);
//            do
//            {
//                thisLine = br.readLine();
//            }
//            while(thisLine.indexOf("Peak Picking results") < 0);
//            br.readLine();
//            br.readLine();
//            br.readLine();
//            thisLine = br.readLine();
//            do
//            {
//                st = new StringTokenizer(thisLine.trim());
//                number = Integer.parseInt(st.nextToken().trim());
//                st.nextToken();
//                st.nextToken();
//                ppm = new Float(st.nextToken().trim()).floatValue();
//                st.nextToken();
//                procInt = new Float(st.nextToken().trim()).floatValue();
//                thisLine = br.readLine();
//                cnmrsig = new NMRSignal(ppm,procInt,1);
//                sd.carbon1D.addSignal(cnmrsig);
//            }
//            while(thisLine.indexOf("-------------") < 0);
//            table.repaint();
//        }
//        catch(Exception exc)
//        {
//        }
    }

    /**
     *
     **/
    public void pasteWinNMRpp()
    {

        java.awt.datatransfer.Clipboard clipboard = getToolkit().getSystemClipboard();
        java.awt.datatransfer.Transferable data = clipboard.getContents(this);
        String s;
        try
        {
            s = (String)(data.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor));
        }
        catch(Exception ex)
        {
            s = data.toString();
        }
        if(s.indexOf("Peak Picking results:") > 0)
        {
			importWinNMR1D(s);
        }
		else
		{
			importWinNMR2D(s);
		}
        table.revalidate();
    }  
	
	
	void importWinNMR1D(String s)
	{
        float ppm, procInt;
		float[] shifts;
        NMRSignal cnmrsig;
		int phase = 0;
        s = s.substring(s.indexOf("Peak Picking results:"));
        s = s.substring(s.indexOf("1"));
        StringTokenizer strTok = new StringTokenizer(s);
        do
        {
            //number of peak
            strTok.nextToken();
            //data point;
            strTok.nextToken();
            //frequency;
            strTok.nextToken();
            //chemical shift;
            ppm = Float.parseFloat(strTok.nextToken().trim());
            //intensity;
            strTok.nextToken();
            //percent intensity;
			
            procInt = new Float(strTok.nextToken().trim()).floatValue();
			shifts = new float[1];
			shifts[0] = ppm;
			if (procInt > 0) phase = NMRSignal.PHASE_POSITIVE;
			if (procInt < 0) phase = NMRSignal.PHASE_NEGATIVE;
			 
            cnmrsig = new NMRSignal(nmrSpectrum.nucleus, shifts ,procInt, phase);
			System.out.println("This is Spectrum " + nmrSpectrum.name);
            nmrSpectrum.addSignal(cnmrsig);
        }
        while(strTok.hasMoreTokens());		
	}
	

	void importWinNMR2D(String s)
	{
        float ppm, procInt;
        NMRSignal cnmrsig;
		int phase = 0;
		float shifts[] = new float[2];
		StringTokenizer strTok = new StringTokenizer(s);
		do
		{
			shifts[0] = new Float(strTok.nextToken().trim()).floatValue();
			shifts[1] = new Float(strTok.nextToken().trim()).floatValue();
			procInt = 1;
			phase = NMRSignal.PHASE_POSITIVE;
		    cnmrsig = new NMRSignal(nmrSpectrum.nucleus, shifts ,procInt, phase);
			System.out.println(cnmrsig);
			System.out.println("This is Spectrum " + nmrSpectrum.name);
		    nmrSpectrum.addSignal(cnmrsig);
		}
		while(strTok.hasMoreTokens());
		
	}
	
	  

      public void stateChanged(ChangeEvent e)
      {
         if (nmrSpectrum == null) return;
         String s = nmrSpectrum.size() + " signals in ";
         s += nmrSpectrum.specType + ".";
         northLabel.setText(s);
      }
	  
	  
	  



    class NMRSpectrumTableModel extends AbstractTableModel
    {

        /**
         *
         **/
        NMRSpectrumTableModel()
        {
        }

        /**
          *
          **/
        public int getColumnCount()
        {
			if (nmrSpectrum == null) return 0;
            return nmrSpectrum.dim + 3;
        }

        /**
          *
          **/
        public int getRowCount()
        {
			if (nmrSpectrum == null) return 0;
            return nmrSpectrum.size();
        }

        /**
          *
          **/
        public Object getValueAt(int row, int column)
        {
            NMRSignal signal;
            signal = (NMRSignal) nmrSpectrum.getSignal(row);
			if (column == 0) return new Integer(row + 1);
			if (column >= 1 && column <= nmrSpectrum.dim) return new Float(signal.shift[column - 1]);
			if (column == nmrSpectrum.dim + 1) return new Float(signal.intensity);
			if (column == nmrSpectrum.dim + 2) return NMRSignal.PHASENAMES[signal.phase];
			return "-";									
        }

        /**
          *
          **/
        public String getColumnName(int column)
        {
			if (column == 0) return "No.";
			if (column >= 1 && column <= nmrSpectrum.dim) return nmrSpectrum.nucleus[column - 1];
			if (column == nmrSpectrum.dim + 1) return "Intensity";
			if (column == nmrSpectrum.dim + 2) return "Phase";
			return "-";
        }

        /**
          *
          **/
        public Class getColumnClass(int c)
        {
            return getValueAt(0, c).getClass();
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
	        NMRSignal signal;
	        signal = (NMRSignal) nmrSpectrum.getSignal(row);
	        if (column >= 1 && column <= nmrSpectrum.dim) signal.shift[column - 1] = ((Float)aValue).floatValue();
	        if (column == nmrSpectrum.dim + 1) signal.intensity = ((Float)aValue).floatValue();
	        if (column == nmrSpectrum.dim + 2)
			{
				if (((String)aValue).equals("NONE")) signal.phase = NMRSignal.PHASE_NONE;
				if (((String)aValue).equals("POSITIVE")) signal.phase = NMRSignal.PHASE_POSITIVE;
				if (((String)aValue).equals("NEGATIVE")) signal.phase = NMRSignal.PHASE_NEGATIVE;
			}
		}


    }

    class closeAction extends AbstractAction
    {
        /**
          *
          **/
        JInternalFrame jif;

        /**
         *
         **/
        closeAction(JInternalFrame jif)
        {
            super("CloseTableFrame");
            this.jif = jif;
        }

        /**
          *
          **/
        public void actionPerformed(ActionEvent e)
        {
            jif.dispose();
        }

    }

    class AddSignalAction extends AbstractAction
    {
        /**
          *
          **/
        AddSignalAction()
        {
            super("addSignal");
        }

        /**
          *
          **/
        public void actionPerformed(ActionEvent e)
        {
            nmrSpectrum.newSignal();
            table.revalidate();
            if (debug) System.out.println("Attempt to add a signal");
        }

    }

    class RemoveSignalAction extends AbstractAction
    {
        /**
          *
          **/
        RemoveSignalAction()
        {
            super("remSignal");
        }

        /**
          *
          **/
        public void actionPerformed(ActionEvent e)
        {
			System.out.println(table.getSelectedRow());
			nmrSpectrum.removeElementAt(table.getSelectedRow());			
	        table.repaint();
            if (debug) System.out.println("Attempt to add a signal");
        }

    }

    class toggleAction extends AbstractAction
    {
        /**
          *
          **/
        toggleAction()
        {
            super("toggleDEPTPhases");
        }

        /**
          *
          **/
        public void actionPerformed(ActionEvent e)
        {
            int r = table.getSelectedRow();
            int c = table.getSelectedColumn();
            if (c > 1)
            {
                String s = (table.getValueAt(r, c)).toString().trim();
                if (s.equals("none"))
                    table.setValueAt("positive",r, c);
                else if (s.equals("positive"))
                    table.setValueAt("negative",r, c);
                else
                    table.setValueAt("none",r, c);
            }
        }

    }

}