/* StrucDispFrame.java
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
import java.util.zip.*;
import java.io.*;
import compchem.*;
import jmdraw.*;

public class StrucDispFrame extends JFrame
{
	protected StrucContainer panel;

	protected JScrollPane scrollPane;

	protected int noOfStructures = 0;

	protected Dimension jmdDim = new Dimension(300,200);

	protected JPanel southPanel;

	protected JButton closeButton;

	protected JButton saveNativeButton;

	protected JButton loadNativeButton;

		protected JButton saveMDLButton;

	protected JButton loadMDLButton;

		protected JButton loadLUCYButton;

	protected JButton isoButton;

	protected Vector jmdPanels = new Vector();

	public static boolean standAlone = false;

	public StrucDispFrame()
	{
		super();
		getContentPane().setLayout(new BorderLayout());
		setTitle("Structure Display");
		panel = new StrucContainer(this);
		scrollPane = new JScrollPane(panel);
		southPanel = new JPanel();
		southPanel.setLayout(new GridLayout(2,4));
		closeButton = new JButton("Close");
		closeButton.addActionListener(new closeAction(this));
		loadMDLButton = new JButton("Load MDL");
		loadMDLButton.addActionListener(new OpenMDLAction(this));
		saveMDLButton = new JButton("Save MDL");
		saveMDLButton.addActionListener(new SaveMDLAction(this));
		loadLUCYButton = new JButton("Load LUCY Result");
		loadLUCYButton.addActionListener(new OpenLUCYAction(this));


		isoButton = new JButton("Remove Isomorphism");
		isoButton.addActionListener(new IsoAction(this));
		southPanel.add(loadMDLButton);
		southPanel.add(loadLUCYButton);
		southPanel.add(saveMDLButton);
		southPanel.add(closeButton);
		scrollPane.setPreferredSize(new Dimension(600, 400));
		getContentPane().add("Center",scrollPane);
		getContentPane().add("South",southPanel);
		setVisible(true);
		pack();

	}

	public void addStructure(JPanel jmd)
	{
		String title = "Hit no. " + (noOfStructures + 1);
		addStructure(jmd,title);
	}

	public void addStructure(JPanel jmd, String title)
	{
		noOfStructures++;
		jmd.setPreferredSize(jmdDim);
		jmd.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), title));

		jmdPanels.addElement(jmd);
		panel.add(jmd);
		//      panel.invalidate();
		//      panel.validate();
		//      scrollPane.invalidate();
		//      scrollPane.validate();
		//      invalidate();
		//      validate();
	}

	class StrucContainer extends JPanel
	{
		JFrame frame;

		public StrucContainer(JFrame f)
		{
			super();
			this.frame = f;
			setLayout(new FlowLayout(FlowLayout.LEFT,0,0));
		}

		public Dimension getPreferredSize()
		{
			int width, height;
			width = frame.getContentPane().getSize().width;
			if(width < jmdDim.width)
				width = jmdDim.width;
			height = ((noOfStructures / ((int)width / jmdDim.width)) + 1) * jmdDim.height;
			height = (int)(height * 1.2);
			return new Dimension(width,height);
		}

		public Rectangle getBounds()
		{
			return new Rectangle(new java.awt.Point(0,0),getPreferredSize());
		}

	}

	/** Action class for the close button of ConnTableFrame */
	class closeAction extends AbstractAction 
	{
		JFrame frame;

		closeAction(JFrame f)
		{
			super("CloseTableFrame");
			this.frame = f;
		}

		public void actionPerformed(ActionEvent e)
		{
			frame.dispose();
		}

	}


	class SaveMDLAction extends AbstractAction
	{
		Frame frame;

		SaveMDLAction(Frame frame)
		{
			super("saveMDL");
			this.frame = frame;
		}

		public void actionPerformed(ActionEvent e)
		{
			Atom[] son;
			JMDraw jmd;
			String fileName;
			String dirName;
			String outName;
			FileDialog f = new FileDialog(frame,"Save MDL MOL Files",FileDialog.SAVE);
			f.show();
			fileName = f.getFile();
			dirName = f.getDirectory();
			if(fileName == null)
			{
				System.err.println("Error while trying open file.");
				return;
			}
			int i = fileName.lastIndexOf("mol");
			if (i == fileName.lastIndexOf(".") + 1) 
				fileName = fileName.substring(0, fileName.lastIndexOf("."));

			for (int g = 0; g < jmdPanels.size(); g++)
			{
				son = ((JMDrawPanel)jmdPanels.elementAt(g)).getJMDraw().atomSet;
				outName = fileName + String.valueOf(g) + ".mol";  
				ChemFileUtils.writeMDLMolFile(dirName + outName, outName, son);       

			}


		}

	}

	class OpenMDLAction extends AbstractAction
	{
		Frame frame;

		OpenMDLAction(Frame frame)
		{
			super("openMDL");
			this.frame = frame;
		}

		public void actionPerformed(ActionEvent e)
		{

			Atom[] setOfNodes;
			JMDrawPanel jmdPanel;
			String filename = null;
			//Create a file chooser
			final JFileChooser fc = new JFileChooser();
			fc.setMultiSelectionEnabled(true);
			//In response to a button click:
			int returnVal = fc.showOpenDialog(frame);

			if (returnVal == JFileChooser.APPROVE_OPTION)
			{


				// ************* This is must be commented out 
				// ************** if using jdk1.2 because Sun has not yet implemented
				// ************* multiple selection as of Jan 11, 2000


				File[] filez = fc.getSelectedFiles();     
				System.out.println("You selected " + filez.length + " files.");    
				for (int f = 0; f < filez.length; f++)
				{
					filename = filez[f].getAbsolutePath();
					System.out.println("Loading file " + filename);
					setOfNodes = ChemFileUtils.readMDLMolFile(filename);
					jmdPanel = new JMDrawPanel();
					jmdPanel.getJMDraw().atomSet = DataStructureTools.cloneAtomSet(setOfNodes);
					jmdPanel.getJMDraw().draw();
					addStructure(jmdPanel, filez[f].getName());
				}
			}
		}

	}


	class OpenLUCYAction extends AbstractAction
	{
		Frame frame;

		OpenLUCYAction(Frame frame)
		{
			super("openLUCY");
			this.frame = frame;
		}

		public void actionPerformed(ActionEvent e)
		{
			Atom[] setOfNodes;
			JMDrawPanel jmdPanel;
			String filename = null;
			//Create a file chooser
			final JFileChooser fc = new JFileChooser();
			fc.setMultiSelectionEnabled(true);
			//In response to a button click:
			int returnVal = fc.showOpenDialog(frame);

			if (returnVal == JFileChooser.APPROVE_OPTION)
			{


				// ************* This is must be commented out 
				// ************** if using jdk1.2 because Sun has not yet implemented
				// ************* multiple selection as of Jan 11, 2000


				File[] filez = fc.getSelectedFiles();     
				System.out.println("You selected " + filez.length + " files.");    
				for (int f = 0; f < filez.length; f++)
				{
					filename = filez[f].getAbsolutePath();
					System.out.println("Loading file " + filename);
					setOfNodes = ChemFileUtils.readLUCYStructure(filename);
					jmdPanel = new JMDrawPanel();
					jmdPanel.getJMDraw().atomSet = DataStructureTools.cloneAtomSet(setOfNodes);
					jmdPanel.getJMDraw().draw();
					addStructure(jmdPanel, filez[f].getName());
				}
			}
		}

	}

	class IsoAction extends AbstractAction
	{
		Frame frame;

		IsoAction(Frame frame)
		{
			super("iso");
			this.frame = frame;
		}

		public void actionPerformed(ActionEvent e)
		{
			Atom[] son;
			JMDrawPanel jmdPanel;
			Vector sons = new Vector();

			for (int f = 0; f < jmdPanels.size(); f++)
			{
				son = ((JMDrawPanel)jmdPanels.elementAt(f)).getJMDraw().atomSet;
				if (son != null && son.length > 0) sons.addElement(DataStructureTools.cloneAtomSet(son));
			}
			son = (Atom[])sons.elementAt(0);
			for (int g = 0; g < son.length; g++) System.out.println(son[g].toString());

			System.out.println(sons.size());
			DataStructureTools.removeIsomorphism(sons);         
			System.out.println(sons.size());
			panel.removeAll();
			for (int f = 0; f < sons.size(); f++)
			{
				jmdPanel = new JMDrawPanel();
				son = (Atom[])sons.elementAt(f);
				jmdPanel.getJMDraw().atomSet = son;
				for (int g = 0; g < son.length; g++) System.out.println(son[g].toString());
				try
				{
					//jmd.draw();
					addStructure(jmdPanel);
				}catch(Exception exc)
				{
				}
			}

		}

	}


	public static void main(String[] args)
	{
		StrucDispFrame sdf = new StrucDispFrame();
		sdf.setVisible(true);
		sdf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	}
}
