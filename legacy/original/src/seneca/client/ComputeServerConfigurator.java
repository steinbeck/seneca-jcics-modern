/* ComputeServerConfigurator.java
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
import java.util.Vector;
import java.net.*;
import java.rmi.*;
import java.io.File;
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
import seneca.gatekeeper.*;
import seneca.core.*;

/**
 *
 **/
public class ComputeServerConfigurator extends JPanel 
{
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
	* Panel in the south of this dialog which hold various buttons
	**/
	protected JPanel southPanel;

	/**
	* Panel in the center of this dialog that holds table
	**/
	//protected JPanel centerPanel;
	protected Box centerPanel;
	protected Box centerCenterPanel;

	Border border = null;

	/**
	* 
	**/
	protected JEditorPane reportPane;
	

	/**
	*
	**/
	protected JButton closeButton;

	/**
	*
	**/
	protected JButton clearListButton;

	/**
	*
	**/
	protected JButton getServerListButton;

	/**
	*
	**/
	protected Vector serverList = new Vector();

	/**
	*
	**/
	SenecaDataset sd;

	public ComputeServerConfigurator(SenecaDataset sd)
	{
		super();
		table = new JTable(new ComputeServerConfiguratorTableModel())
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
		this.serverList = sd.serverList;
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
		southPanel = new JPanel();
		southPanel.setLayout(new FlowLayout());
		centerPanel = new Box(BoxLayout.Y_AXIS);
		centerCenterPanel = new Box(BoxLayout.X_AXIS);
		clearListButton = new JButton("Clear List");
		clearListButton.addActionListener(new ClearListAction());

		getServerListButton = new JButton("Get Serverlist");
		getServerListButton.addActionListener(new GetServerListAction());
		southPanel.add(clearListButton);
		southPanel.add(getServerListButton);
		add("South",southPanel);
		scrollpane.setPreferredSize(new Dimension(400,200));
		reportPane = new JEditorPane();
		reportPane.setEditable(false);
		reportPane.setEditorKit(new HTMLEditorKit());
		reportPane.setText("No Info available");
		reportScrollPane = new JScrollPane(reportPane);
		reportScrollPane.setPreferredSize(new Dimension(200, 300));
		border = BorderFactory.createEtchedBorder();
		String title = "Current Server List";
		reportScrollPane.setBorder(BorderFactory.createTitledBorder(border, title));
		centerPanel.add(scrollpane);
		centerCenterPanel.add(reportScrollPane);
		centerPanel.add(centerCenterPanel);
		add("Center",centerPanel);
		scrollpane.repaint();
		
		
		
	}

	private String getGateKeeperStatus()
	{
		String status = "";
		try{
			String gateKeeper = "www.nmrshiftdb.org";
			String namingLookup = "//" + gateKeeper + "/GateKeeper";
			GateKeeperInterface gki = (GateKeeperInterface)Naming.lookup(namingLookup);
			status = "<html>\n" + gki.getFullServerList() + "</html>\n";
		}
		catch(Exception exc)
		{
			exc.printStackTrace();
			return "<html><b>Could not establish connection with GateKeeper</b></html>";
		}
		return status;
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
	
	


	class ComputeServerConfiguratorTableModel extends AbstractTableModel
	{
		/**
		*
		**/
		String[] names = {"No.", "Running on Host", "Response", "Speed"};

		/**
		*
		**/
		ComputeServerConfiguratorTableModel()
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
			return serverList.size();
		}

		/**
		*
		**/
		public Object getValueAt(int row, int column)
		{
			ServerInfo serverInfo = (ServerInfo)serverList.elementAt(row);
			switch(column)
			{
				/* Number of Row */
				case 0:
					return String.valueOf(row + 1);
				/* Host name on which server resides */
			case 1:
				try
				{
					return serverInfo.getHostname();
				}
				catch(Exception exc)
				{
					System.err.println("getHostname(): " + exc.toString());
				}
				/* Name of Dataset handed to the server when Structure calculation started */
			case 2:
				try
				{
					return new Long(serverInfo.getResponseTime());
				}
				catch(Exception exc)
				{
					System.err.println("getResponseTime(): " + exc.toString());
				}
				/* Corresponding Molecular formula of Dataset on Server */
			case 3:
				try
				{
					return new Long(serverInfo.getSpeed());				}
				catch(Exception exc)
				{
					System.err.println("getSpeed(): " + exc.toString());
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
			//			System.out.println("Column " + c);
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


	class GetServerListAction extends AbstractAction implements Runnable
	{


		/**
		*
		**/
		GetServerListAction()
		{
			super("GetServerListAction");
		}

		/**
		*
		**/
		public void actionPerformed(ActionEvent e)
		{
			new Thread(this).run();
		}
		
		public void run()
		{
			reportPane.setText(getGateKeeperStatus());
			reportScrollPane.revalidate();
		}
		
		
	}

	class ClearListAction extends AbstractAction
	{
		/**
		*
		**/
		ClearListAction()
		{
			super("ClearListAction");
		}
		/**
		*
		**/
		public void actionPerformed(ActionEvent e)
		{
			serverList.setSize(0);
		}

	}
}




