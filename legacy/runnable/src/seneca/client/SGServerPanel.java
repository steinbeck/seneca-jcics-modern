/* SGServerPanel.java
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
import java.util.*;
import java.net.*;
import java.rmi.*;
import java.io.*;
import java.awt.geom.*;
import javax.swing.*;
import javax.swing.table.*;
import javax.swing.event.*;
import javax.swing.border.*;
import javax.swing.text.html.*;
import compchem.*;
import jmdraw.*;
import seneca.structgen.*;
import seneca.judges.*;
import seneca.compute.*;
import seneca.core.*;
import seneca.gatekeeper.*;
import com.jrefinery.chart.*;
import com.jrefinery.chart.event.*;
import org.apache.log4j.*;

/**
 *
 **/
public class SGServerPanel extends JPanel 
{
	boolean debug = false;

	/**
	* Table model for table that displays available servers
	**/
	protected TableModel tableModel;

	/**
	* Table that displays available servers
	**/
	protected JTable table;

	/**
	* Scrollpane for table
	**/
	protected JScrollPane scrollpane;

	/**
	* Scrollpane for reportArea
	**/
	protected JScrollPane reportScrollPane;

	/**
	* Various Boxes for organizing the Layout of this Panel
	**/
	protected Box southBox;
	protected Box centerBox;
	protected Box centerCenterBox;
	protected Box topCenterBox;
	protected JPanel handleSelectedButtonsPanel;
	protected JPanel serverAdminButtonsPanel;
	protected JPanel resultButtonsPanel;

	JMDrawPanel jmdPanel = null;
	
	ChartPanel chartPanel;
	JFreeChart chart;
	AnnealingDataSource dataSource;
	Float[][][] alData;
	float maxScore = 0;	
	
	StructureGeneratorStatus[] sgs = null;
	
	Border border = null;

	/**
	* 
	**/
	protected JTextField statusField;

	/**
	* 
	**/
	protected JEditorPane reportPane;
	

	
	/**
	* The Control Buttons of this Panel. The Names should
	* be selfexplanatory
	**/
	protected JButton closeButton;
	protected JButton stopSelectedButton;
	protected JButton updateButton;
	protected JButton showCurrentResultButton;
	protected JButton goButton;
	protected JButton resetButton;
	protected JButton startLocalButton;
	protected JButton clearListButton;
	protected JButton logAnnealingDataButton;
	/**
	* A Timer that updates the server information every two seconds.
 	**/
	protected javax.swing.Timer tableUpdateTimer; 

	protected Vector sgServerProcesses = new Vector();

	/**
	*
	**/
	SenecaDataset sd;

	/**
	*
	**/
	StructureGeneratorResult structureGeneratorResult;

	public Dimension jmdDim = new Dimension(300, 300);


	/**
	* Vector holding the annealing schedules
	**/
	Vector schedules;

	int currentBest = 0;

	/* Used in the update method of the timer to load the table with 
	a new model once the net has been scanned completely */
	boolean newModelNeeded = true; 



	/**
	*
	**/

	static int ALL = 0, SEL = 1;

	public SGServerPanel(SenecaDataset sd)
	{
		super();
		table = new JTable(new SGServerTableModel())
		{
			/**
			*
			**/
			public Dimension getPreferredSize()
			{
				return (new Dimension(scrollpane.getSize().width,(getRowCount() + 2) * getRowHeight()));
			}

		}
		;
		this.sd = sd;
		this.schedules = sd.annealingSchedules;
		FontMetrics fm = getFontMetrics(table.getFont());
		for(int f = 0;f < table.getColumnCount();f++)
		{
			TableColumn column = table.getColumn(table.getColumnName(f));
			column.setWidth((int)(fm.stringWidth(table.getColumnName(f)) * 1.2));
			DefaultTableCellRenderer columnRenderer = new DefaultTableCellRenderer();
			columnRenderer.setBackground(Color.yellow);
		}
		TableColumn column = null;
		Component comp = null;
		int headerWidth = 0;
		int cellWidth = 0;
		JButton placeholder = new JButton("");
		setCellRendering();
		scrollpane = new JScrollPane(table);
		scrollpane.setBorder(new BevelBorder(BevelBorder.LOWERED));
		setLayout(new BorderLayout());
		southBox = new Box(BoxLayout.X_AXIS);;
		handleSelectedButtonsPanel = new JPanel(new GridLayout(3,1));
		serverAdminButtonsPanel = new JPanel(new GridLayout(3,1));
		resultButtonsPanel = new JPanel(new GridLayout(3,1));
		String title = "Server Admin";
		serverAdminButtonsPanel.setBorder(BorderFactory.createTitledBorder(border, title));
		title = "Selections";
		handleSelectedButtonsPanel.setBorder(BorderFactory.createTitledBorder(border, title));
		title = "Result";
		resultButtonsPanel.setBorder(BorderFactory.createTitledBorder(border, title));

		centerBox = new Box(BoxLayout.Y_AXIS);
		topCenterBox = new Box(BoxLayout.X_AXIS);
		centerCenterBox = new Box(BoxLayout.X_AXIS);
		statusField = new JTextField();
		
		startLocalButton = new JButton("Start local server");
		startLocalButton.addActionListener(new StartLocalAction());
		updateButton = new JButton("Update server list");
		updateButton.addActionListener(new ScanNetAction());
		clearListButton = new JButton("Clear Server List");
		clearListButton.addActionListener(new ClearListAction());
		serverAdminButtonsPanel.add(startLocalButton);
		serverAdminButtonsPanel.add(updateButton);
		serverAdminButtonsPanel.add(clearListButton);

     	goButton = new JButton("Start as selected");
		goButton.addActionListener(new goAction());
		stopSelectedButton = new JButton("Stop Selected");
		stopSelectedButton.addActionListener(new stopSelectedAction());
		resetButton = new JButton("Reset selected");
		resetButton.addActionListener(new ResetSelectedAction());
		handleSelectedButtonsPanel.add(goButton);
		handleSelectedButtonsPanel.add(stopSelectedButton);
		handleSelectedButtonsPanel.add(resetButton);
		
		showCurrentResultButton = new JButton("Summarize Result");
		showCurrentResultButton.addActionListener(new ShowCurrentAction());
		logAnnealingDataButton  = new JButton("Log Annealing Data");
		logAnnealingDataButton.addActionListener(new LogAnnealingDataAction());		
		resultButtonsPanel.add(showCurrentResultButton);
		resultButtonsPanel.add(logAnnealingDataButton);		

		southBox.add(serverAdminButtonsPanel);
		southBox.add(handleSelectedButtonsPanel);
		southBox.add(resultButtonsPanel);
		add("South",southBox);
		
		scrollpane.setPreferredSize(new Dimension(400,200));
		reportPane = new JEditorPane();
		reportPane.setEditable(false);
		reportPane.setEditorKit(new HTMLEditorKit());
		reportScrollPane = new JScrollPane(reportPane);
		reportScrollPane.setPreferredSize(new Dimension(200, 300));
		border = BorderFactory.createEtchedBorder();
		jmdPanel = new JMDrawPanel();
		jmdPanel.setPreferredSize(new Dimension(200, 300));
		title = "No structure available";
		jmdPanel.setBorder(BorderFactory.createTitledBorder(border, title));
		title = "Current Structure Evaluation";
		reportScrollPane.setBorder(BorderFactory.createTitledBorder(border, title));
		reportScrollPane.setBackground(Color.white);
		chartPanel = getAnnealingViewPanel();
		
		title = "Annealing Data";
		chartPanel.setBorder(BorderFactory.createTitledBorder(border, title));
		topCenterBox.add(scrollpane);

	    topCenterBox.add(chartPanel);
		centerBox.add(topCenterBox);
		centerCenterBox.add(jmdPanel);
		centerCenterBox.add(reportScrollPane);
		centerBox.add(centerCenterBox);
		add("Center",centerBox);
		add("North",statusField);
		tableUpdateTimer = new javax.swing.Timer(2000,new updateAction());
		tableUpdateTimer.start();
		scrollpane.repaint();
	}

	void updateSgs()
	{
		StructureGeneratorStatus[] newSgs = new StructureGeneratorStatus[sgServerProcesses.size()];				
		if (sgs != null)
		{
			for (int f = 0; f < sgs.length; f++)
			{
				newSgs[f] = sgs[f];
			}
		}
		sgs = newSgs;
	}

	private ChartPanel getAnnealingViewPanel()
	{
		dataSource = new AnnealingDataSource();
		chart = JFreeChart.createXYChart(dataSource);
		chart.setTitle(new StandardTitle("", new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 6)));
		Plot plot = chart.getPlot();
		Axis horizontalAxis = plot.getAxis(Plot.HORIZONTAL_AXIS);
		horizontalAxis.setLabel("Iteration");			
		Axis verticalAxis = plot.getAxis(Plot.VERTICAL_AXIS);
		verticalAxis.setLabel("Score");					
		chartPanel = new ChartPanel(chart);
		chartPanel.addMouseListener(dataSource);
		return chartPanel;
	}


	/**
	*
	**/
	protected void setCellRendering()
	{
		JLabel label = new JLabel();
		TableColumn column;
		table.setRowSelectionAllowed(true);
		table.setColumnSelectionAllowed(false);
		for(int f = 0;f < table.getColumnCount();f++)
		{
			column = table.getColumn(table.getColumnName(f));
			label.setVerticalAlignment(JLabel.CENTER);
			label.setHorizontalAlignment(JLabel.CENTER);
			DefaultTableCellRenderer columnRenderer = new DefaultTableCellRenderer();
			columnRenderer.setBackground(Color.yellow);
			column.setCellRenderer(columnRenderer);
		}
	}


	class SGServerTableModel extends AbstractTableModel
	{
		/**
		*
		**/
		String[] names = {"No.", "ID", "Host", "Dataset", "MF", "Status ", "Iter."};

		/**
		*
		**/
		SGServerTableModel()
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
			return sgServerProcesses.size();
		}

		/**
		*
		**/
		public Object getValueAt(int row, int column)
		{
			Compute server = (Compute)sgServerProcesses.elementAt(row);
			switch(column)
			{
				/* Number of Row */
				case 0:
					return String.valueOf(row + 1);
				case 1:
					try
					{
						return server.getID();
					}
					catch(Exception exc)
					{
						System.err.println("getID(): " + exc.toString());
					}

				/* Host name on which server resides */
			case 2:
				try
				{
					return server.getHostName();
				}
				catch(Exception exc)
				{
					System.err.println("getHostName(): " + exc.toString());
				}
				/* Name of Dataset handed to the server when Structure calculation started */
			case 3:
				try
				{
					if (sgs[row] == null) return "none";
					return sgs[row].datasetName;
				}
				catch(Exception exc)
				{
					System.err.println("getDatasetName(): " + exc.toString());
				}
				/* Corresponding Molecular formula of Dataset on Server */
			case 4:
				try
				{
					if (sgs[row] == null) return "none";					
					return sgs[row].molecularFormula;
				}
				catch(Exception exc)
				{
					System.err.println("getMF(): " + exc.toString());
				}
				/* Status of SG-Server: "Waiting for Job", "Generating", "Holding results" */
			case 5:
				try
				{
					if (sgs[row] == null) return "idle";					
					return sgs[row].status;
				}
				catch(Exception exc)
				{
					System.err.println("getStatus(): " + exc.toString());
				}
				/* Current iteration and percent-completed information for the current generation on server */
			case 6:
				try
				{
					if (sgs[row] == null) return new String("none");					
					return new String ("" + sgs[row].iteration);
				}
				catch(Exception exc)
				{
					System.err.println("getCurrentIteration(): " + exc.toString());
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
		}

	}


	class updateAction extends AbstractAction
	{


		/**
		*
		**/
		updateAction()
		{
			super("updateTableFrame");
		}

		/**
		*
		**/
		public void actionPerformed(ActionEvent e)
		{
			Atom[] son;
			String title;
			Compute sgi;
			String structure;
			boolean stillAlive = false;
			int aliveCount = 0;   		 
			FastResolver fr = null;
			StringBuffer reportText = new StringBuffer();
			String message = null;
			table.repaint();
			AnnealingLog al = null;
			if (sgServerProcesses.size() > 0)
			{
				Compute	server = (Compute)sgServerProcesses.elementAt(currentBest);
				try
				{
					sgs[currentBest] = (StructureGeneratorStatus) server.getTaskStatus();
					maxScore = sgs[currentBest].bestEvaluation.maxScore;

					if (sgs[currentBest] != null)
					{
						structure = ((StructureGeneratorStatus)server.getTaskStatus()).bestStructure;
//						System.out.println(structure);
						son = ChemFileUtils.readMDLMolFile(new BufferedReader(new StringReader(structure)));
						if (son == null)
						{
							title = "No structure available";
							jmdPanel.setBorder(BorderFactory.createTitledBorder(border, title));
							jmdPanel.getJMDraw().atomSet = null;
							jmdPanel.getJMDraw().draw();
							jmdPanel.repaint();
							reportPane.setText("");
						}
						else
						{
							title = "Best structure of no." + (currentBest + 1);
							jmdPanel.setJMDraw(new JMDraw(son));
							jmdPanel.setBorder(BorderFactory.createTitledBorder(border, title));
							jmdPanel.getJMDraw().draw();
							jmdPanel.repaint();
							reportText.append("<html>\n");
							reportText.append("<b>Score: " + sgs[currentBest].bestEvaluation.score);
							if (sgs[currentBest].bestEvaluation.maxScore > 0)
							{
								reportText.append("/" + sgs[currentBest].bestEvaluation.maxScore);
							}
							reportText.append( "</b>");
							title = "Evaluation of no. " + (currentBest + 1);							
							reportScrollPane.setBorder(BorderFactory.createTitledBorder(border, title));							
							reportText.append(((StructureGeneratorStatus)server.getTaskStatus()).bestEvaluation.description + "</html>\n");
							reportPane.setText(reportText.toString());
						}
					}										
					al = sgs[currentBest].al;
					if (dataSource.getData() == null)
					{
						dataSource.init(sgServerProcesses.size());
					}
					dataSource.addData(al, currentBest);
					chart = JFreeChart.createXYChart(dataSource);
					chart.setTitle(new StandardTitle("", new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 6)));
					title = "Annealing data";
					chartPanel.setChart(chart);
					JFreeChart jfc = chartPanel.getChart();
					Plot plot = jfc.getPlot();
					Axis horizontalAxis = plot.getAxis(Plot.HORIZONTAL_AXIS);
					horizontalAxis.setLabel("Iteration");			
					Axis verticalAxis = plot.getAxis(Plot.VERTICAL_AXIS);
					if (dataSource.getDisplayType() == dataSource.KT)
					{
						verticalAxis.setLabel("kT");			
					}
					else
					{
						verticalAxis.setLabel("Score");			
					}

					chartPanel.setBorder(BorderFactory.createTitledBorder(border, title));
					chartPanel.repaint();
					
				}
				catch(Exception exc)
				{
					if (debug) exc.printStackTrace();
				}

				currentBest ++;
				if (currentBest == sgServerProcesses.size()) currentBest = 0;
			}
			scrollpane.repaint();

		}
	}

	class stopSelectedAction extends AbstractAction
	{
		/**
		*
		**/
		stopSelectedAction()
		{
			super("stopSelected");
		}
		/**
		*
		**/
		public void actionPerformed(ActionEvent e)
		{
			int rows[] = table.getSelectedRows();
			int[] selrows = new int[sgServerProcesses.size()];
			for (int f = 0; f < rows.length; f++) selrows[rows[f]] = 1;
			Compute server;

			for(int f = 0;f < sgServerProcesses.size();f++)
			{
				if (selrows[f] == 1)
				{
					server = (Compute)sgServerProcesses.elementAt(f);
					try
					{
						server.endTask();
					}
					catch(Exception exc)
					{
						if (debug) System.out.println("Error while stopping calculation on no." + f);
						if (debug) System.out.println(exc.toString());
					}
				}
			}      
		}

	}

	class ResetSelectedAction extends AbstractAction
	{
		/**
		*
		**/
		ResetSelectedAction()
		{
			super("ResetSelected");
		}
		/**
		*
		**/
		public void actionPerformed(ActionEvent e)
		{
			dataSource.setData(null);
			int rows[] = table.getSelectedRows();
			int[] selrows = new int[sgServerProcesses.size()];
			for (int f = 0; f < rows.length; f++) selrows[rows[f]] = 1;
			Compute server;

			for(int f = 0;f < sgServerProcesses.size();f++)
			{
				if (selrows[f] == 1)
				{
					server = (Compute)sgServerProcesses.elementAt(f);
					try
					{
						server.resetTask();
					}
					catch(Exception exc)
					{
						if (debug) System.out.println("Error while stopping calculation on no." + f);
						if (debug) System.out.println(exc.toString());
					}
				}
			}      
		}

	}

	class ClearListAction extends AbstractAction
	{
		/**
		*
		**/
		ClearListAction()
		{
			super("ClearList");
		}
		/**
		*
		**/
		public void actionPerformed(ActionEvent e)
		{
			tableUpdateTimer.stop();
			sgServerProcesses.setSize(0);
			tableUpdateTimer.start();
		}
	}

	class LogAnnealingDataAction extends AbstractAction
	{
		/**
		*
		**/
		LogAnnealingDataAction()
		{
			super("LogAnnealingData");
		}
		/**
		*
		**/
		public void actionPerformed(ActionEvent e)
		{
			dataSource.logResults();
		}
	}


	class goAction extends AbstractAction
	{
		/**
		*
		**/
		goAction()
		{
			super("go");
		}

		/**
		*
		**/
		public void actionPerformed(ActionEvent e)
		{
			int hetCounter = 0;
			Vector judges = null;
			Vector annealingSchedules = null;
			Judge judge = null;
			StochasticGenerator submitGent;
			int rows[] = table.getSelectedRows();
			int[] selrows = new int[sgServerProcesses.size()];
			for (int f = 0; f < rows.length; f++) selrows[rows[f]] = 1;
			Atom[] setOfNodes = sd.getSetOfNodes();
			Compute sgi;
			for(int f = 0;f < sgServerProcesses.size();f++)
			{
				if (selrows[f] == 1)
				{
					sgi = (Compute)sgServerProcesses.elementAt(f);
					judges = new Vector();
					for (int g = 0; g < sd.judges.size(); g++)
					{
						judge = ((Judge)sd.judges.elementAt(g));
						if (judge.getEnabled())
						{
							judge.calcMaxScore();
							judges.addElement(judge.clone());
						}
					}
					annealingSchedules = new Vector();
					try
					{
						for (int g = 0; g < sd.annealingSchedules.size(); g++)
						{
							annealingSchedules.addElement(((AnnealingSchedule)sd.annealingSchedules.elementAt(g)).clone());
						}

						sgi.setMolecule((Molecule)serClone(new Molecule(sd.getSetOfNodes())));
						sgi.setJudges((Vector)serClone(sd.judges));
						sgi.setDatasetName(sd.getName());
						sgi.setSASchedules((Vector)serClone(annealingSchedules));
						sgi.executeTask();
					}
					catch(Exception exc)
					{
						if (debug) System.out.println("Error while starting calculation on no." + f);
						if (debug) System.out.println(exc.toString());
						exc.printStackTrace();
					}
				}
			}
		}

		Object serClone(Object obj)
		{
			try
			{
				ByteArrayOutputStream buf =	new ByteArrayOutputStream();
				ObjectOutputStream o = new ObjectOutputStream(buf);
				o.writeObject(obj);
				// Now get copies:
				ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(buf.toByteArray()));
				return in.readObject();
			}
			catch(Exception exc)
			{
				return null;		
			}
		}
	}

	class ScanNetAction extends AbstractAction
	{

		/**
		*
		**/
		ScanNetAction()
		{
			super("ScanNet");

		}

		/**
		*
		**/
		public void actionPerformed(ActionEvent e)
		{
			String namingLookup;
			ServerInfo si;
			
			Vector serverInfos = new Vector();
			sgServerProcesses.setSize(0);
			try{
				String gateKeeper = "www.nmrshiftdb.org";
				namingLookup = "//" + gateKeeper + "/GateKeeper";
				GateKeeperInterface gki = (GateKeeperInterface)Naming.lookup(namingLookup);
				serverInfos = gki.getHosts(8);
			}
			catch(Exception exc)
			{
				exc.printStackTrace();
				return;
			}
			
			for (int f = 0; f < serverInfos.size(); f++)
			{
				si = (ServerInfo)serverInfos.elementAt(f);
				try{
					namingLookup = "//" + si.getHostname() + "/StoIcGentServer";
					Compute stoicGent = (Compute)Naming.lookup(namingLookup);
					if (stoicGent.willingToManage())
					{
						sgServerProcesses.addElement(stoicGent);
						table.revalidate();
						scrollpane.revalidate();
					}
				}
				catch(Exception exc)
				{
				}
			}
			sgs = new StructureGeneratorStatus[sgServerProcesses.size()];

		}

	}


	class StartLocalAction extends AbstractAction
	{

		/**
		*
		**/
		StartLocalAction()
		{
			super("StartLocal");

		}

		/**
		*
		**/
		public void actionPerformed(ActionEvent e)
		{
			try
			{
				tableUpdateTimer.stop();
				sgServerProcesses.addElement(new seneca.engine.StructureGeneratorServer("local" + sgServerProcesses.size()));
				updateSgs();
				sgs[sgs.length - 1] = (StructureGeneratorStatus)((Compute)sgServerProcesses.lastElement()).getTaskStatus();				
				tableUpdateTimer.start();
				scrollpane.repaint();
			}
			catch(Exception exc)
			{
				if (debug) System.out.println("Local StoicGent service could not be created.");
				exc.printStackTrace();
			}
			table.revalidate();
			scrollpane.revalidate();
		}

	}


	class ShowCurrentAction extends AbstractAction
	{

		ShowCurrentAction()
		{
			super("Show Current");
		}

		/**
		*
		**/
		public void actionPerformed(ActionEvent e)
		{
			int members = 0;
			StrucDispFrame sdf = null;
			IsomorphismTester isoTest;
			boolean removed;
			Compute sgi = null;
			JMDrawPanel jmdPanel = null;
			Atom[] setOfNodes = null;
			String title = "", serverTitle = "";
			StructureGeneratorResult structureGeneratorResult = new StructureGeneratorResult(sgServerProcesses.size() * 30);
			StructureGeneratorResult result = null;
			StructureGeneratorStatus sgs = null;
			ScoreSummary scoreSummary = null;
			try
			{
				for(int i = 0;i < sgServerProcesses.size();i++)
				{
					sgi = (Compute)sgServerProcesses.elementAt(i);
					if (debug) System.out.println("Getting result from host " + (i + 1));
					result = (StructureGeneratorResult)sgi.getTaskResult();
					if (result != null)
					{
						members = result.structures.size();
						if (debug) System.out.println("Host " + (i + 1) + " has " + members + " structures.");						
						for(int f = 0;f < members;f++)
						{
							structureGeneratorResult.structures.push(result.structures.pop());
							scoreSummary = (ScoreSummary)result.scoreSummaries.pop();
							scoreSummary.description += "<p>Result from host " + (i + 1) + "</p>";
							structureGeneratorResult.scoreSummaries.push(scoreSummary);
						}
					}
				}
			}
			catch(Exception exc)
			{
				if (debug) System.out.println(exc.toString());
			}
			if (debug) System.out.println("Found a total of " + structureGeneratorResult.structures.size() + " structures.");						
			if(sdf == null)
			{
				sdf = new StrucDispFrame();
				sdf.setSize(new Dimension(600,400));
				}
			sdf.setVisible(true);
			try
			{
				serverTitle = sgi.getID() + " Structure ";
			}
			catch(Exception exc)
			{
				if (debug) System.out.println(exc.toString());
			}
			if (debug) System.out.println("Isomorphism Check...");
			structureGeneratorResult.removeIsomorphism();
			structureGeneratorResult.sort();
			if (debug) System.out.println("Isomorphism Check done");                

			for(int f = 0;f < structureGeneratorResult.structures.size();f++)
			{
				title = "<html><b>Score " + ((ScoreSummary)structureGeneratorResult.scoreSummaries.elementAt(f)).score + ": </b>";

				title += ((ScoreSummary)structureGeneratorResult.scoreSummaries.elementAt(f)).description + "</html>";
				jmdPanel = new JMDrawPanel();
				jmdPanel.setJMDraw(new JMDraw(ChemFileUtils.readMDLMolFile(new BufferedReader(new StringReader((String)structureGeneratorResult.structures.elementAt(f))))));
				jmdPanel.setPreferredSize(new Dimension(300,200));
				jmdPanel.setToolTipText(title);
				jmdPanel.getJMDraw().draw();
				sdf.addStructure(jmdPanel, "Score " + ((ScoreSummary)structureGeneratorResult.scoreSummaries.elementAt(f)).score);
			}
		}

	}

	class ChartPanel extends JComponent implements ChartChangeListener {

	  /** The chart that is contained within the panel. */
	 protected JFreeChart chart;

	  /** Full constructor: returns a panel containing the specified chart.
	      @param chart The chart to display in the panel. */
	  public ChartPanel(JFreeChart chart) {
	    this.chart = chart;
	    this.chart.addChangeListener(this);
	    setPreferredSize(new Dimension(400, 400));
	  }

	  /** Returns a reference to the chart displayed in the panel. */
	  public void setChart(JFreeChart chart) {
	    this.chart = chart;
	    this.chart.addChangeListener(this);
	  }

	  /** Returns a reference to the chart displayed in the panel. */
	  public JFreeChart getChart()
	  {
	  	return this.chart;
	  }


	  /** Paints the component - this means drawing the chart to fill the entire component, but
	      allowing for the insets (which will be non-zero if a border has been set for this
	      component). */
	  public void paintComponent(Graphics g) {

	    Graphics2D g2 = (Graphics2D)g;
	    Dimension size = getSize();
	    Insets insets = getInsets();
	    Rectangle2D chartArea = new Rectangle2D.Double(insets.left, insets.top,
	                                                   size.getWidth()-insets.left-insets.right,
	                                                   size.getHeight()-insets.top-insets.bottom);
		try
		{
		    chart.draw(g2, chartArea);
		}
		catch(Exception exc)
		{
		
		}

	  }

	  /** Receives notification of changes to the chart, and redraws the chart. */
	  public void chartChanged(ChartChangeEvent event) {
	    this.repaint();
	  }

	}


	class AnnealingDataSource extends AbstractDataSource implements XYDataSource, MouseListener {
		
		AnnealingLog al = null;
		int SCORES = 1;
		int KT = 0;
		int displayType = SCORES;
		float maxX = 0;
		int maxSeries = 0;
		int maxItemCount = 0;
		
		public AnnealingDataSource() 
		{

		}

		public Number getXValue(int series, int item) 
		{
			Float[] entry;
			if (al == null) return new Float(1);
			if (series == al.getSeriesCount())
			{
				entry = al.getEntry(maxSeries, item);
				return entry[0];
			}

			entry = al.getEntry(series, item);
			if (entry[0].floatValue() > maxX)
			{
				maxX = entry[0].floatValue();
				maxSeries = series;
			}
			
			return entry[0];
		}

		public Number getYValue(int series, int item) 
		{
			if (al == null) return new Float(1);
			if (series == al.getSeriesCount())
			{
				return new Float(maxScore);
			}
			Float[] entry = al.getEntry(series, item);
 			return entry[1 + displayType];

		}
		
		public int getSeriesCount() 
		{
			if (al == null) return 1;
			if (displayType == SCORES)
			{
				return al.getSeriesCount() + 1;
			}
			return al.getSeriesCount();
		}

		public String getSeriesName(int series) 
		{
			if (al == null) return "no data";
			if (series == al.getSeriesCount())
			{
				return "max. Score";
			}
			else 
			{
				return "host " + (series + 1) ;
			}
		}


		/** Add the Annealing Data for a given host, identified by number series */
		public void addData(AnnealingLog addal, int series)
		{
			int ic = addal.getItemCount(0);
			Float[] entry;
			int itemCount = 0; 
			for (int f = 0; f < ic; f++)
			{
				entry = addal.getEntry(0, f);
				al.addEntry(series, al.getItemCount(series), entry);
			}
		}
		
		public AnnealingLog getData()
		{
			return al;
		}
		
		public void setData(AnnealingLog newAl)
		{
			al = newAl;
		}

		public void init(int size)
		{
			al = new AnnealingLog(size, 2);
		}

		public int getItemCount(int series) 
		{
			int ic = 0;
			if (al == null) return 1;
			if (series == al.getSeriesCount())
			{
				return maxItemCount;
			}
			ic = al.getItemCount(series);
			if (ic > maxItemCount) maxItemCount = ic;
			return al.getItemCount(series);
		}
		
		public int getDisplayType()
		{
			return displayType;
		}

		public void mouseClicked(java.awt.event.MouseEvent event)
		{
			if (displayType == SCORES) displayType = KT;
			else  displayType = SCORES;
			ChartPanel cp = (ChartPanel)event.getSource();
			JFreeChart jfc = cp.getChart();
			Plot plot = jfc.getPlot();
			Axis horizontalAxis = plot.getAxis(Plot.HORIZONTAL_AXIS);
			horizontalAxis.setLabel("Iteration");			
			Axis verticalAxis = plot.getAxis(Plot.VERTICAL_AXIS);
			if (displayType == KT)
			{
				verticalAxis.setLabel("kT");			
			}
			else
			{
				verticalAxis.setLabel("Score");			
			}
		}
		
		public void logResults()
		{
			Category annealinglog = Category.getInstance("seneca.structgen.StochasticGenerator.annealing.log");
			Float[] entry;
			String string, xString;
			int maxItemCount = 0;
			
			for (int x = 0; x < al.getSeriesCount(); x++)
			{
				if (al.getItemCount(x) > maxItemCount)
				{
					maxItemCount = al.getItemCount(x);
				}
			}
			
			Float[][] data = new Float[maxItemCount][al.getSeriesCount() + 1];
			
			for (int x = 0; x < al.getSeriesCount(); x++)
			{
				for (int y = 0; y < al.getItemCount(x); y++)
				{
					entry = al.getEntry(x, y);
					data[y][0]= entry[0];
					data[y][x + 1] = entry[2];
				}
			}
			string = "Iteration; ";
			for (int x = 0; x < al.getSeriesCount(); x++)
			{
				string += "Server " + (x + 1) + "; ";
			}
			System.out.println(string);
			annealinglog.info(string + "\n");
		
			for (int x = 0; x < maxItemCount; x++)
			{
				string = "";
				for (int y = 0; y < al.getSeriesCount() + 1; y++)
				{
					if (data[x][y] != null)
					{
						string += data[x][y];
					}
					string += "; ";

				}
				System.out.println(string);
				annealinglog.info(string + "\n");
			}

			
		}

		public void mousePressed(java.awt.event.MouseEvent event){}
		public void mouseReleased(java.awt.event.MouseEvent event){}
		public void mouseEntered(java.awt.event.MouseEvent event){}
		public void mouseExited(java.awt.event.MouseEvent event){}
	}


}
