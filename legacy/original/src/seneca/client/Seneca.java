/* Seneca.java
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

import jmdraw.*;
import compchem.*;
import seneca.structgen.*;
import seneca.core.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.Dimension;
import java.awt.Color;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.zip.*;
//import java.rmi.*;
import javax.swing.*;
import javax.swing.table.*;
import javax.swing.border.*;
import javax.swing.event.*;
import org.apache.log4j.*;
import org.apache.log4j.gui.*;

class Seneca extends JFrame implements ChangeListener
{
	// Global declarations
	// Essential data struct ures
	public compchem.Atom[] atomSet;

	public static SenecaDatasetCollection senecaDatasets = new SenecaDatasetCollection();

	public StructureGeneratorCollection sgc;

	public int[] bondOrders;

	// Gui stuff
	JPanel framepanel;

	JPanel mainpanel;

	JPanel statuspanel;

	JLabel status1;

	JLabel status2;

	JLabel status3;
	
	/*** logging ***/
	static Category senecalog;
	static Category structgenlog;
	static TextPaneAppender a4;
	/*** logging ***/
	
	 
	 


	private static ResourceBundle resources;

	private static Properties printprefs = new Properties();
	
	public static String senecaHome;

	private Hashtable commands;

	private Hashtable menuItems;

	private JMenuBar menubar;

	private JToolBar toolbar;

	private Container status;

	protected FileDialog fileDialog;

	/**
	* Suffix applied to the key used in resource file
	* lookups for an image.
	*/
	public static final String imageSuffix = "Image";

	/**
	* Suffix applied to the key used in resource file
	* lookups for a label.
	*/
	public static final String labelSuffix = "Label";

	/**
	* Suffix applied to the key used in resource file
	* lookups for an action.
	*/
	public static final String actionSuffix = "Action";

	/**
	* Suffix applied to the key used in resource file
	* lookups for tooltip text.
	*/
	public static final String tipSuffix = "Tooltip";

	public static final String newAction = "new";

	public static final String openAllAction = "openAll";

	public static final String openNodePropsAction = "openNodeProps";

	public static final String saveAllAction = "saveAll";

	public static final String saveNodePropsAction = "saveNodeProps";

	public static final String importPDBAction = "pdbImp";

	public static final String importMDLAction = "mdlImp";

	public static final String importLUCYAction = "LUCYimp";

	public static final String importStrucHMBCMDLAction = "strucHMBCMDLimp";

	public static final String exportPDBAction = "pdbExp";

	public static final String printAction = "print";

	public static final String exitAction = "exit";

	public static final String cutAction = "cut";

	public static final String copyAction = "copy";

	public static final String pasteAction = "paste";

	public static final String genpropsAction = "GenProps";

	public static final String onedspectAction = "OneDSpect";

	public static final String chcorrAction = "CHCorr";

	public static final String chcorrlrAction = "CHCorrLr";

	public static final String hhcosyAction = "HHCOSY";

	public static final String noesyAction = "NOESY";

	public static final String connmatAction = "ConnMat";

	public static final String nodeTabAction = "NodeTab";

	public static final String annealPropsAction = "AnnealProps";

	public static final String bondOrdersAction = "bondOrders";

	public static final String winLnFAction = "winLnF";

	public static final String motifLnFAction = "motifLnF";

	public static final String javaLnFAction = "javaLnF";

	public static final String metalLnFAction = "metalLnF";

	public static final String aboutAction = "about";

	public static final String curSolAction = "curSol";

	public static final String genThreadsAction = "genThreads";

	public static final String switchDatasetAction = "switchDS";

	public static final String startCalcAction = "startCalc";

	// --- action implementations -----------------------------------
	/**
	* Actions defined by the Pascale Class
	*/

	private Action[] defaultActions = {
             new NewAction(this),
					new OpenAllAction(this),
					new SaveAllAction(this), 
					new ImportLUCYAction(), 
					new PrintAction(this), 
					new ExitAction(this), 
					new CopyAction(this), 
					new CutAction(this), 
				//	new PasteAction(this), 
				//	new genpropsAction(this), 
				//	new genThreadsAction(this), 
				//	new OneDSpectAction(this), 
				//	new CHCorrAction(this), 
				//	new CHCorrLrAction(this), 
				//	new HHCOSYAction(this), 
				//	new NOESYAction(this), 
					new WinLnFAction(this), 
					new MotifLnFAction(this), 
					new JavaLnFAction(this), 
					new MetalLnFAction(this), 
				//	new ConnMatAction(this), 
				//	new AnnealPropsAction(this), 
        //         new BondOrdersAction(this),  
				//	new NodeTabAction(this), 
					new StrucDispAction(this), 
				//	new startCalcAction(this), 
					new AboutAction(this)
   };

	/** 
	  * Set this to true if you want some debug output
	  */
	static boolean debug = false;
	
	static
	{
		try
		{
			resources = ResourceBundle.getBundle("seneca.client.Seneca",Locale.getDefault());
			if (debug) System.out.println(resources);
			senecaHome = System.getProperty("seneca.home");    
		}
		catch(MissingResourceException mre)
		{
			System.err.println("Seneca.properties not found");
			System.exit(1);
		}
	}

	public static JDesktopPane desktop = new JDesktopPane();

	public Seneca(String[] args)
	{
		super("Seneca");
		try
		{
			UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
		}
		catch(Exception exc)
		{
			System.err.println("Error loading L&F: " + exc);
		}
		
		/*** start of logging configuration ***/
		Properties logprops = new Properties();
		try
		{
			logprops.load(this.getClass().getClassLoader().getResourceAsStream("seneca/client/logging.properties"));
		}
		catch(Exception exc)
		{
			exc.printStackTrace();	
		}
		PropertyConfigurator.configure(logprops);
 		senecalog = Category.getInstance(this.getClass().getName());
		structgenlog = Category.getInstance("seneca.structgen.StochasticGenerator.log");
		a4 = (TextPaneAppender)structgenlog.getAppender("A4");
		a4.setOption("Fancy", "false");
		
		/*** End of logging configuration ***/		
		
		
		desktop.setPreferredSize(new Dimension(800,600));
		desktop.setLayout(new BorderLayout());
		setContentPane(desktop);
		desktop.putClientProperty("JDesktopPane.dragMode", "outline");
		commands = new Hashtable();
		Action[] actions = getActions();
		for(int i = 0;i < actions.length;i++)
		{
			Action a = actions[i];
			commands.put(a.getValue(Action.NAME),a);
		}
		menuItems = new Hashtable();
		menubar = createMenubar();
		setJMenuBar(menubar);
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter()
			{
			public void windowClosing(WindowEvent e)
			{
				trySystemExit();
			}

		}
		);

		desktop.add("North",createToolbar());
		statuspanel = new JPanel();
		statuspanel.setLayout(new GridLayout(1,3));
		statuspanel.setPreferredSize(new Dimension(640,30));
		desktop.add("South",statuspanel);
		status1 = new JLabel();
		status1.setPreferredSize(new Dimension(100,100));
		status1.setBorder(new BevelBorder(BevelBorder.LOWERED));
		status1.setHorizontalAlignment(JLabel.CENTER);
		status2 = new JLabel();
		status2.setPreferredSize(new Dimension(100,100));
		status2.setBorder(new BevelBorder(BevelBorder.LOWERED));
		status2.setHorizontalAlignment(JLabel.CENTER);
		status3 = new JLabel();
		status3.setPreferredSize(new Dimension(100,100));
		status3.setBorder(new BevelBorder(BevelBorder.LOWERED));
		status3.setHorizontalAlignment(JLabel.CENTER);
		statuspanel.add(status1);
		statuspanel.add(status2);
		statuspanel.add(status3);
		senecaDatasets.addChangeListener(this);
		pack();
		Dimension screenDim = Toolkit.getDefaultToolkit().getScreenSize();
		setLocation((screenDim.width - getSize().width) / 2,(screenDim.height - getSize().height) / 2);
		setStatus("No dataset available",1);
		this.setVisible(true);
		if (args.length > 0 && args[0] != null)
		{
			try
			{
				openFile(new File(args[0]));
			}
			catch(Exception exc)
			{
				String s = "Could not opoen the file referenced by the command line parameters";
				ErrorMessages.error(s, exc);
			}
		}

	}

	public static void main(String[] args)
	{
		try
		{
			String vers = System.getProperty("java.version");
			if(vers.compareTo("1.1.2") < 0)
			{
				System.out.println("!!!WARNING: Swing must be run with a " + "1.1.2 or higher version VM!!!");
			}
			new Seneca(args);
		}
		catch(Throwable t)
		{
			System.out.println("uncaught exception: " + t);
			t.printStackTrace();
		}
	}

	public Action[] getActions()
	{
		return defaultActions;
	}

	/**
	* This is the hook through which all menu items are
	* created.  It registers the result with the menuitem
	* hashtable so that it can be fetched with getMenuItem().
	* @see #getMenuItem
	*/
	protected JMenuItem createMenuItem(String cmd)
	{
		JMenuItem mi = new JMenuItem(getResourceString(cmd + labelSuffix));
		URL url = getResource(cmd + imageSuffix);
		if(url != null)
		{
			mi.setHorizontalTextPosition(JButton.RIGHT);
			mi.setIcon(new ImageIcon(url));
		}
		String astr = getResourceString(cmd + actionSuffix);
		if(astr == null)
		{
			astr = cmd;
		}
		mi.setActionCommand(astr);
		Action a = getAction(astr);
		if(a != null)
		{
			mi.addActionListener(a);
		}
		else
		{
			mi.setEnabled(false);
		}
		menuItems.put(cmd,mi);
		return mi;
	}

	/**
	* Fetch the menu item that was created for the given
	* command.
	* @param cmd  Name of the action.
	* @returns item created for the given command or null
	*  if one wasn't created.
	*/
	protected JMenuItem getMenuItem(String cmd)
	{
		return (JMenuItem)menuItems.get(cmd);
	}

	protected Action getAction(String cmd)
	{
		return (Action)commands.get(cmd);
	}

	protected String getResourceString(String nm)
	{
		String str;
		try
		{
			str = resources.getString(nm);
		}
		catch(MissingResourceException mre)
		{
			str = null;
		}
		return str;
	}

	protected URL getResource(String key)
	{
		String name = getResourceString(key);
		if (debug) System.out.println("getResource -> " + key);
		if(name != null)
		{
			URL url = this.getClass().getResource(name);
			return url;
		}
		return null;
	}

	protected Container getToolbar()
	{
		return toolbar;
	}

	protected JMenuBar getMenubar()
	{
		return menubar;
	}

	/**
	* Create the toolbar.  By default this reads the
	* resource file for the definition of the toolbar.
	*/
	private Component createToolbar()
	{
		toolbar = new JToolBar();
		String[] toolKeys = tokenize(getResourceString("toolbar"));
		for(int i = 0;i < toolKeys.length;i++)
		{
			if(toolKeys[i].equals("-"))
			{
				toolbar.add(Box.createHorizontalStrut(5));
			}
			else
			{
				toolbar.add(createTool(toolKeys[i]));
			}
		}
		toolbar.add(Box.createHorizontalGlue());
		return toolbar;
	}

	/**
	* Hook through which every toolbar item is created.
	*/
	protected Component createTool(String key)
	{
		return createToolbarButton(key);
	}

	/**
	* Create a button to go inside of the toolbar.  By default this
	* will load an image resource.  The image filename is relative to
	* the classpath (including the '.' directory if its a part of the
	* classpath), and may either be in a JAR file or a separate file.
	*
	* @param key The key in the resource file to serve as the basis
	*  of lookups.
	*/
	protected JButton createToolbarButton(String key)
	{
		String s = key + imageSuffix;
		if (debug) System.out.println(s);	
		URL url = getResource(s);
		if (debug) System.out.println(url);
		JButton b = new JButton(new ImageIcon(url))
		{
			public float getAlignmentY()
			{
				return 0.5f;
			}

		}
		;
		b.setRequestFocusEnabled(false);
		b.setMargin(new Insets(1,1,1,1));
		String astr = getResourceString(key + actionSuffix);
		if(astr == null)
		{
			astr = key;
		}
		Action a = getAction(astr);
		if(a != null)
		{
			b.setActionCommand(astr);
			b.addActionListener(a);
		}
		else
		{
			b.setEnabled(false);
		}
		String tip = getResourceString(key + tipSuffix);
		if(tip != null)
		{
			b.setToolTipText(tip);
		}
		return b;
	}

	/**
	* Take the given string and chop it up into a series
	* of strings on whitespace boundries.  This is useful
	* for trying to get an array of strings out of the
	* resource file.
	*/
	protected String[] tokenize(String input)
	{
		Vector v = new Vector();
		StringTokenizer t = new StringTokenizer(input);
		String cmd[];
		while(t.hasMoreTokens())
			v.addElement(t.nextToken());
		cmd = new String[v.size()];
		for(int i = 0;i < cmd.length;i++)
			cmd[i] = (String)v.elementAt(i);
		return cmd;
	}

	/**
	* Create the menubar for the app.  By default this pulls the
	* definition of the menu from the associated resource file.
	*/
	protected JMenuBar createMenubar()
	{
		JMenuItem mi;
		JMenuBar mb = new JMenuBar();
		String[] menuKeys = tokenize(getResourceString("menubar"));
		for(int i = 0;i < menuKeys.length;i++)
		{
			JMenu m = createMenu(menuKeys[i]);
			if(m != null)
			{
				mb.add(m);
			}
		}
		return mb;
	}

	/**
	* Create a menu for the app.  By default this pulls the
	* definition of the menu from the associated resource file.
	*/
	protected JMenu createMenu(String key)
	{
		String[] itemKeys = tokenize(getResourceString(key));
		JMenu menu = new JMenu(getResourceString(key + "Label"));
		for(int i = 0;i < itemKeys.length;i++)
		{
			if(itemKeys[i].equals("-"))
			{
				menu.addSeparator();
			}
			else
				if(itemKeys[i].endsWith("}"))
			{
				String menuTitle = itemKeys[i].substring(1,itemKeys[i].length() - 1);
				JMenu me = createMenu(menuTitle);
				menu.add(me);
			}
			else
			{
				JMenuItem mi = createMenuItem(itemKeys[i]);
				menu.add(mi);
			}
		}
		return menu;
	}
	
	public void openFile(File file)
	{
		SpecMLReader smlr = new SpecMLReader(file);

		SenecaDataset sd = smlr.getSenecaDataset();
		if (sd == null) 
		{
			System.err.println("There was a fatal problem while trying to import SenecaDataset " + file.getName());
			return;
		}
		senecaDatasets.add(sd);
		if (debug) System.out.println("Importing Seneca dataset " + file + " done.");
		SenecaDatasetFrame sdf = new SenecaDatasetFrame(senecaDatasets.getCurrentDataset());
		getContentPane().add(sdf);
		sdf.toFront();
		try
		{
			sdf.setVisible(true); 
			sdf.setSelected(true);
		}
		catch (java.beans.PropertyVetoException pvexc)
		{
			System.err.println("Problem activating new JInternalFrame"); 
		}
	}	

	public void setStatus(String s, int i)
	{
		switch(i)
		{
			case 1:
				status1.setText(s);
				break;
			case 2:
				status2.setText(s);
				break;
			case 3:
				status3.setText(s);
				break;
		}
	}

	protected void trySystemExit()
	{
		String title = getResourceString("ExitTitle");
		int lineNum = (Integer.valueOf(getResourceString("ExitLines"))).intValue();
		String msg = "";
		for(int f = 0;f < lineNum;f++)
		{
			msg += getResourceString("ExitMsg" + (f + 1)) + "\n";
		}
		int answer = JOptionPane.showConfirmDialog(this,msg,title,JOptionPane.YES_NO_OPTION);
		if(answer == JOptionPane.YES_OPTION)
		{
			this.setVisible(false);
			this.dispose();
			System.exit(0);
		}
	}

	public ResourceBundle getResources()
	{
		return resources;
	}


	protected void disableDefault()
	{
		String[] itemsToDisable = tokenize(getResourceString("disable"));
		String ident;
		for(int f = 0;f < defaultActions.length;f++)
		{
			ident = (String)defaultActions[f].getValue(Action.NAME);
			for(int i = 0;i < itemsToDisable.length;i++)
			{
				if(ident.equals(itemsToDisable[i]))
				{
					defaultActions[f].setEnabled(false);
					//                    if (debug) System.out.println("disabling " + defaultActions[f].getValue(Action.NAME));
					//                    if (debug) System.out.println(defaultActions[f].isEnabled());
					break;
				}
			}
		}
	}

	/**
	*  Seneca implements ChangeListener interface in order to be 
	*  informed of changes in its dataset collection
	*/

		public void stateChanged(ChangeEvent e)
	{
		SenecaDatasetCollection sdc = (SenecaDatasetCollection)e.getSource();
		JMenu jma = menubar.getMenu(menubar.getMenuCount() - 2);
		JMenu jmb = (JMenu)(jma.getItem(0));
		jmb.removeAll();
		for(int f = 0;f < sdc.size();f++)
		{
			JMenuItem jmi = new JMenuItem(((SenecaDataset)sdc.elementAt(f)).getName());
			jmi.addActionListener(new SwitchDatasetAction());
			jmb.add(jmi);
		}
		if (sdc.getCurrentDataset() != null)
		{
			setStatus(sdc.getCurrentDataset().getName(),1);
			setStatus(sdc.getCurrentDataset().getMolecularFormula(),2);
		}
		else
		{
			setStatus("No dataset loaded",1);
			setStatus("",2);

		}

	}






	class NewAction extends AbstractAction
	{
		JFrame frame;

		NewAction(JFrame f)
		{
			super(newAction);
			this.frame = f;
		}

		public void actionPerformed(ActionEvent e)
		{
			senecaDatasets.add(new SenecaDataset("untitled" + (new Date().hashCode())));
			SenecaDatasetFrame sdf = new SenecaDatasetFrame(senecaDatasets.getCurrentDataset());
			getContentPane().add(sdf);
			sdf.toFront();
			try
			{
				sdf.setSelected(true);
			}
			catch (java.beans.PropertyVetoException pvexc)
			{
				System.err.println("Problem activating new JInternalFrame");          
			}
		}

	}

	class OpenAllAction extends AbstractAction
	{
		JFrame frame;

		OpenAllAction(JFrame f)
		{
			super(openAllAction);
			this.frame = f;
		}

		public void actionPerformed(ActionEvent e)
		{
			SenecaParameters sp = new SenecaParameters();			
			JFileChooser chooser = new JFileChooser(sp.getSenecaFileLocation());
			ExtensionFileFilter filter = new ExtensionFileFilter();
			filter.addExtension("sml");
			filter.setDescription("SENECA Dataset");
			chooser.setFileFilter(filter);
			int returnVal = chooser.showOpenDialog(Seneca.this);
			if (returnVal != JFileChooser.APPROVE_OPTION)
			    return;
			File file = chooser.getSelectedFile();
			sp.setSenecaFileLocation(file.getParent());	
			openFile(file);					

		}

	}


	class SaveAllAction extends AbstractAction
	{
		JFrame frame;

		SaveAllAction(JFrame f)
		{
			super(saveAllAction);
			this.frame = f;
		}

		public void actionPerformed(ActionEvent e)
		{

			SenecaParameters sp = new SenecaParameters();			
 			JFileChooser chooser = new JFileChooser(sp.getSenecaFileLocation());
			ExtensionFileFilter filter = new ExtensionFileFilter();
			filter.addExtension("sml");
			filter.setDescription("SENECA Dataset");
			chooser.setFileFilter(filter);
			int returnVal = chooser.showSaveDialog(Seneca.this);
			if (returnVal != JFileChooser.APPROVE_OPTION)
			    return;
			File file = chooser.getSelectedFile();
			sp.setSenecaFileLocation(file.getParent());			
			try
			{
				FileWriter fw  = new FileWriter(file);
				fw.write(new SpecMLGenerator().convert(senecaDatasets.getCurrentDataset()));
				fw.flush();
				fw.close();
			}
			catch(IOException ioe)
			{
				System.out.println(ioe);
			}
		}

	}

	class ImportLUCYAction extends AbstractAction
	{
		ImportLUCYAction()
		{
			super(importLUCYAction);
		}

		public void actionPerformed(ActionEvent e)
		{
			SenecaParameters sp = new SenecaParameters();
			JFileChooser chooser = new JFileChooser(sp.getLucyFileLocation());
			ExtensionFileFilter filter = new ExtensionFileFilter();
			filter.addExtension("col");
			filter.setDescription("Lucy datasets");
			chooser.setFileFilter(filter);
			int returnVal = chooser.showOpenDialog(Seneca.this);
			if (returnVal != JFileChooser.APPROVE_OPTION)
			    return;
			File file = chooser.getSelectedFile();
			sp.setLucyFileLocation(file.getParent());
	        if (debug) System.out.println("Importing LUCY file " + file + "...");			
			SenecaDataset sd = (new LUCYFileHandler()).importLUCYDataset(file);
			senecaDatasets.add(sd);
	        if (debug) System.out.println("Importing LUCY file " + file + " done.");
			SenecaDatasetFrame sdf = new SenecaDatasetFrame(senecaDatasets.getCurrentDataset());
			getContentPane().add(sdf);
			sdf.toFront();
			try
			{
				sdf.setVisible(true); 
				sdf.setSelected(true);
			}
			catch (java.beans.PropertyVetoException pvexc)
			{
				System.err.println("Problem activating new JInternalFrame"); 
			}

		}

	}


	class PrintAction extends AbstractAction
	{
		JFrame frame;

		PrintAction(JFrame f)
		{
			super(printAction);
			this.frame = f;
		}

		public void actionPerformed(ActionEvent e)
		{
		}

	}

	class PasteAction extends AbstractAction
	{
		JFrame frame;

		PasteAction(JFrame f)
		{
			super(pasteAction);
			this.frame = f;
		}

		public void actionPerformed(ActionEvent e)
		{
			java.awt.datatransfer.Clipboard clipboard = frame.getToolkit().getSystemClipboard();
			java.awt.datatransfer.Transferable data = clipboard.getContents(frame);
			String s;
			try
			{
				s = (String)(data.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor));
			}
			catch(Exception ex)
			{
				s = data.toString();
			}
		}

	}

	class CutAction extends AbstractAction
	{
		JFrame frame;

		CutAction(JFrame f)
		{
			super(cutAction);
			this.frame = f;
		}

		public void actionPerformed(ActionEvent e)
		{
		}

	}

	class CopyAction extends AbstractAction
	{
		JFrame frame;

		CopyAction(JFrame f)
		{
			super(copyAction);
			this.frame = f;
		}

		public void actionPerformed(ActionEvent e)
		{
		}

	}

	class ExitAction extends AbstractAction
	{
		JFrame frame;

		ExitAction(JFrame f)
		{
			super(exitAction);
			this.frame = f;
		}

		public void actionPerformed(ActionEvent e)
		{
			trySystemExit();
		}

	}

	class genpropsAction extends AbstractAction
	{
		JFrame frame;

		genpropsAction(JFrame f)
		{
			super(genpropsAction);
			this.frame = f;
		}

		public void actionPerformed(ActionEvent e)
		{
			SenecaDatasetFrame sdf = new SenecaDatasetFrame(senecaDatasets.getCurrentDataset());
			sdf.setVisible(true);
		}

	}

	class genThreadsAction extends AbstractAction
	{
		JFrame frame;

		genThreadsAction(JFrame f)
		{
			super(genThreadsAction);
			this.frame = f;
		}

		public void actionPerformed(ActionEvent e)
		{
			//			ThreadPropertyFrame tpf = new ThreadPropertyFrame(sgc, frame);
		}

	}





	class ConnMatAction extends AbstractAction
	{
		JFrame frame;

		ConnMatAction(JFrame f)
		{
			super(connmatAction);
			this.frame = f;
		}

		public void actionPerformed(ActionEvent e)
		{
			//            new ConnTableFrame(stdMolMatrix);
		}

	}

	class NodeTabAction extends AbstractAction
	{
		JFrame frame;

		NodeTabAction(JFrame f)
		{
			super(nodeTabAction);
			this.frame = f;
		}

		public void actionPerformed(ActionEvent e)
		{
			new NodeTableFrame(senecaDatasets.getCurrentDataset().getSetOfNodes(),frame);
		}

	}

	class WinLnFAction extends AbstractAction
	{
		JFrame frame;

		WinLnFAction(JFrame f)
		{
			super(winLnFAction);
			this.frame = f;
		}

		public void actionPerformed(ActionEvent e)
		{
			try
			{
				UIManager.setLookAndFeel("javax.swing.plaf.windows.WindowsLookAndFeel");
				SwingUtilities.updateComponentTreeUI(frame);
				frame.pack();
			}
			catch(Exception Ex)
			{
				System.err.println("Could not locate requested look&feel");
			}
		}

	}

	class MotifLnFAction extends AbstractAction
	{
		JFrame frame;

		MotifLnFAction(JFrame f)
		{
			super(motifLnFAction);
			this.frame = f;
		}

		public void actionPerformed(ActionEvent e)
		{
			try
			{
				UIManager.setLookAndFeel("javax.swing.plaf.motif.MotifLookAndFeel");
				SwingUtilities.updateComponentTreeUI(frame);
				frame.pack();
			}
			catch(Exception Ex)
			{
				System.err.println("Could not locate requested look&feel");
			}
		}

	}

	class JavaLnFAction extends AbstractAction
	{
		JFrame frame;

		JavaLnFAction(JFrame f)
		{
			super(javaLnFAction);
			this.frame = f;
		}

		public void actionPerformed(ActionEvent e)
		{
			try
			{
				UIManager.setLookAndFeel("javax.swing.plaf.jlf.JLFLookAndFeel");
				SwingUtilities.updateComponentTreeUI(frame);
				frame.pack();
			}
			catch(Exception Ex)
			{
				System.err.println("Could not locate requested look&feel");
			}
		}

	}

	class MetalLnFAction extends AbstractAction
	{
		JFrame frame;

		MetalLnFAction(JFrame f)
		{
			super(metalLnFAction);
			this.frame = f;
		}

		public void actionPerformed(ActionEvent e)
		{
			try
			{
				UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
				SwingUtilities.updateComponentTreeUI(frame);
				frame.pack();
			}
			catch(Exception Ex)
			{
				System.err.println("Could not locate requested look&feel");
			}
		}

	}

	class StrucDispAction extends AbstractAction
	{
		JFrame frame;

		StrucDispAction(JFrame f)
		{
			super(curSolAction);
			this.frame = f;
		}

		public void actionPerformed(ActionEvent e)
		{
			StrucDispFrame sdf = new StrucDispFrame();
			sdf.setSize(new Dimension(600,400));
		}

	}


	class AboutAction extends AbstractAction
	{
		JFrame frame;

		AboutAction(JFrame f)
		{
			super(aboutAction);
			this.frame = f;
		}

		public void actionPerformed(ActionEvent e)
		{
			String title = getResourceString("AboutTitle");
			int lineNum = (Integer.valueOf(getResourceString("AboutLines"))).intValue();
			String msg = "";
			for(int f = 0;f < lineNum;f++)
			{
				msg += getResourceString("AboutMsg" + (f + 1)) + "\n";
			}
			JOptionPane.showMessageDialog(null,msg,title,JOptionPane.INFORMATION_MESSAGE);
		}

	}

	class SwitchDatasetAction extends AbstractAction
	{
		SwitchDatasetAction()
		{
			super(switchDatasetAction);
		}

		public void actionPerformed(ActionEvent e)
		{
			senecaDatasets.setCurrentDatasetNumber(((JMenuItem)e.getSource()).getText());
		}

	}

	/* Start of definitions of the model/view adaptors */
	class StructureGenerationListener implements ChangeListener
	{
		StrucDispFrame sdf = null;

		StructureGenerator sg;

		JMDraw jmd;

		public StructureGenerationListener()
		{
		}

		public void stateChanged(ChangeEvent e)
		{
		}

	}



}
