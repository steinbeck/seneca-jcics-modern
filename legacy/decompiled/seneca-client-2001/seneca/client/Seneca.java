package seneca.client;

import compchem.Atom;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.beans.PropertyVetoException;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.Hashtable;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.StringTokenizer;
import java.util.Vector;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDesktopPane;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.BevelBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import jmdraw.JMDraw;
import org.apache.log4j.Category;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.gui.TextPaneAppender;
import seneca.core.SenecaDataset;
import seneca.core.SpecMLGenerator;
import seneca.core.SpecMLReader;
import seneca.structgen.StructureGenerator;

class Seneca extends JFrame implements ChangeListener {
   public Atom[] atomSet;
   public static SenecaDatasetCollection senecaDatasets = new SenecaDatasetCollection();
   public StructureGeneratorCollection sgc;
   public int[] bondOrders;
   JPanel framepanel;
   JPanel mainpanel;
   JPanel statuspanel;
   JLabel status1;
   JLabel status2;
   JLabel status3;
   static Category senecalog;
   static Category structgenlog;
   static TextPaneAppender a4;
   private static ResourceBundle resources;
   private static Properties printprefs = new Properties();
   public static String senecaHome;
   private Hashtable commands;
   private Hashtable menuItems;
   private JMenuBar menubar;
   private JToolBar toolbar;
   private Container status;
   protected FileDialog fileDialog;
   public static final String imageSuffix = "Image";
   public static final String labelSuffix = "Label";
   public static final String actionSuffix = "Action";
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
   private Action[] defaultActions = new Action[]{
      new Seneca.NewAction(this),
      new Seneca.OpenAllAction(this),
      new Seneca.SaveAllAction(this),
      new Seneca.ImportLUCYAction(),
      new Seneca.PrintAction(this),
      new Seneca.ExitAction(this),
      new Seneca.CopyAction(this),
      new Seneca.CutAction(this),
      new Seneca.WinLnFAction(this),
      new Seneca.MotifLnFAction(this),
      new Seneca.JavaLnFAction(this),
      new Seneca.MetalLnFAction(this),
      new Seneca.StrucDispAction(this),
      new Seneca.AboutAction(this)
   };
   static boolean debug = false;
   public static JDesktopPane desktop;

   public Seneca(String[] var1) {
      super("Seneca");

      try {
         UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
      } catch (Exception var10) {
         System.err.println("Error loading L&F: " + var10);
      }

      Properties var2 = new Properties();

      try {
         var2.load(this.getClass().getClassLoader().getResourceAsStream("seneca/client/logging.properties"));
      } catch (Exception var9) {
         var9.printStackTrace();
      }

      PropertyConfigurator.configure(var2);
      senecalog = Category.getInstance(this.getClass().getName());
      structgenlog = Category.getInstance("seneca.structgen.StochasticGenerator.log");
      a4 = (TextPaneAppender)structgenlog.getAppender("A4");
      a4.setOption("Fancy", "false");
      desktop.setPreferredSize(new Dimension(800, 600));
      desktop.setLayout(new BorderLayout());
      this.setContentPane(desktop);
      desktop.putClientProperty("JDesktopPane.dragMode", "outline");
      this.commands = new Hashtable();
      Action[] var3 = this.getActions();

      for (int var4 = 0; var4 < var3.length; var4++) {
         Action var5 = var3[var4];
         this.commands.put(var5.getValue("Name"), var5);
      }

      this.menuItems = new Hashtable();
      this.menubar = this.createMenubar();
      this.setJMenuBar(this.menubar);
      this.setDefaultCloseOperation(0);
      this.addWindowListener(new Seneca$1(this));
      desktop.add("North", this.createToolbar());
      this.statuspanel = new JPanel();
      this.statuspanel.setLayout(new GridLayout(1, 3));
      this.statuspanel.setPreferredSize(new Dimension(640, 30));
      desktop.add("South", this.statuspanel);
      this.status1 = new JLabel();
      this.status1.setPreferredSize(new Dimension(100, 100));
      this.status1.setBorder(new BevelBorder(1));
      this.status1.setHorizontalAlignment(0);
      this.status2 = new JLabel();
      this.status2.setPreferredSize(new Dimension(100, 100));
      this.status2.setBorder(new BevelBorder(1));
      this.status2.setHorizontalAlignment(0);
      this.status3 = new JLabel();
      this.status3.setPreferredSize(new Dimension(100, 100));
      this.status3.setBorder(new BevelBorder(1));
      this.status3.setHorizontalAlignment(0);
      this.statuspanel.add(this.status1);
      this.statuspanel.add(this.status2);
      this.statuspanel.add(this.status3);
      senecaDatasets.addChangeListener(this);
      this.pack();
      Dimension var11 = Toolkit.getDefaultToolkit().getScreenSize();
      this.setLocation((var11.width - this.getSize().width) / 2, (var11.height - this.getSize().height) / 2);
      this.setStatus("No dataset available", 1);
      this.setVisible(true);
      if (var1.length > 0 && var1[0] != null) {
         try {
            this.openFile(new File(var1[0]));
         } catch (Exception var8) {
            String var7 = "Could not opoen the file referenced by the command line parameters";
            ErrorMessages.error(var7, var8);
         }
      }
   }

   public static void main(String[] var0) {
      try {
         String var1 = System.getProperty("java.version");
         if (var1.compareTo("1.1.2") < 0) {
            System.out.println("!!!WARNING: Swing must be run with a 1.1.2 or higher version VM!!!");
         }

         new Seneca(var0);
      } catch (Throwable var2) {
         System.out.println("uncaught exception: " + var2);
         var2.printStackTrace();
      }
   }

   public Action[] getActions() {
      return this.defaultActions;
   }

   protected JMenuItem createMenuItem(String var1) {
      JMenuItem var2 = new JMenuItem(this.getResourceString(var1 + "Label"));
      URL var3 = this.getResource(var1 + "Image");
      if (var3 != null) {
         var2.setHorizontalTextPosition(4);
         var2.setIcon(new ImageIcon(var3));
      }

      String var4 = this.getResourceString(var1 + "Action");
      if (var4 == null) {
         var4 = var1;
      }

      var2.setActionCommand(var4);
      Action var5 = this.getAction(var4);
      if (var5 != null) {
         var2.addActionListener(var5);
      } else {
         var2.setEnabled(false);
      }

      this.menuItems.put(var1, var2);
      return var2;
   }

   protected JMenuItem getMenuItem(String var1) {
      return (JMenuItem)this.menuItems.get(var1);
   }

   protected Action getAction(String var1) {
      return (Action)this.commands.get(var1);
   }

   protected String getResourceString(String var1) {
      String var2;
      try {
         var2 = resources.getString(var1);
      } catch (MissingResourceException var4) {
         var2 = null;
      }

      return var2;
   }

   protected URL getResource(String var1) {
      String var2 = this.getResourceString(var1);
      if (debug) {
         System.out.println("getResource -> " + var1);
      }

      return var2 != null ? this.getClass().getResource(var2) : null;
   }

   protected Container getToolbar() {
      return this.toolbar;
   }

   protected JMenuBar getMenubar() {
      return this.menubar;
   }

   private Component createToolbar() {
      this.toolbar = new JToolBar();
      String[] var1 = this.tokenize(this.getResourceString("toolbar"));

      for (int var2 = 0; var2 < var1.length; var2++) {
         if (var1[var2].equals("-")) {
            this.toolbar.add(Box.createHorizontalStrut(5));
         } else {
            this.toolbar.add(this.createTool(var1[var2]));
         }
      }

      this.toolbar.add(Box.createHorizontalGlue());
      return this.toolbar;
   }

   protected Component createTool(String var1) {
      return this.createToolbarButton(var1);
   }

   protected JButton createToolbarButton(String var1) {
      String var2 = var1 + "Image";
      if (debug) {
         System.out.println(var2);
      }

      URL var3 = this.getResource(var2);
      if (debug) {
         System.out.println(var3);
      }

      Seneca$2 var4 = new Seneca$2(this, new ImageIcon(var3));
      var4.setRequestFocusEnabled(false);
      var4.setMargin(new Insets(1, 1, 1, 1));
      String var5 = this.getResourceString(var1 + "Action");
      if (var5 == null) {
         var5 = var1;
      }

      Action var6 = this.getAction(var5);
      if (var6 != null) {
         var4.setActionCommand(var5);
         var4.addActionListener(var6);
      } else {
         var4.setEnabled(false);
      }

      String var7 = this.getResourceString(var1 + "Tooltip");
      if (var7 != null) {
         var4.setToolTipText(var7);
      }

      return var4;
   }

   protected String[] tokenize(String var1) {
      Vector var2 = new Vector();
      StringTokenizer var3 = new StringTokenizer(var1);

      while (var3.hasMoreTokens()) {
         var2.addElement(var3.nextToken());
      }

      String[] var4 = new String[var2.size()];

      for (int var5 = 0; var5 < var4.length; var5++) {
         var4[var5] = (String)var2.elementAt(var5);
      }

      return var4;
   }

   protected JMenuBar createMenubar() {
      JMenuBar var2 = new JMenuBar();
      String[] var3 = this.tokenize(this.getResourceString("menubar"));

      for (int var4 = 0; var4 < var3.length; var4++) {
         JMenu var5 = this.createMenu(var3[var4]);
         if (var5 != null) {
            var2.add(var5);
         }
      }

      return var2;
   }

   protected JMenu createMenu(String var1) {
      String[] var2 = this.tokenize(this.getResourceString(var1));
      JMenu var3 = new JMenu(this.getResourceString(var1 + "Label"));

      for (int var4 = 0; var4 < var2.length; var4++) {
         if (var2[var4].equals("-")) {
            var3.addSeparator();
         } else if (var2[var4].endsWith("}")) {
            String var5 = var2[var4].substring(1, var2[var4].length() - 1);
            JMenu var6 = this.createMenu(var5);
            var3.add((JMenuItem)var6);
         } else {
            JMenuItem var7 = this.createMenuItem(var2[var4]);
            var3.add(var7);
         }
      }

      return var3;
   }

   public void openFile(File var1) {
      SpecMLReader var2 = new SpecMLReader(var1);
      SenecaDataset var3 = var2.getSenecaDataset();
      if (var3 == null) {
         System.err.println("There was a fatal problem while trying to import SenecaDataset " + var1.getName());
      } else {
         senecaDatasets.add(var3);
         if (debug) {
            System.out.println("Importing Seneca dataset " + var1 + " done.");
         }

         SenecaDatasetFrame var4 = new SenecaDatasetFrame(senecaDatasets.getCurrentDataset());
         this.getContentPane().add(var4);
         var4.toFront();

         try {
            var4.setVisible(true);
            var4.setSelected(true);
         } catch (PropertyVetoException var6) {
            System.err.println("Problem activating new JInternalFrame");
         }
      }
   }

   public void setStatus(String var1, int var2) {
      switch (var2) {
         case 1:
            this.status1.setText(var1);
            break;
         case 2:
            this.status2.setText(var1);
            break;
         case 3:
            this.status3.setText(var1);
      }
   }

   protected void trySystemExit() {
      String var1 = this.getResourceString("ExitTitle");
      int var2 = Integer.valueOf(this.getResourceString("ExitLines"));
      String var3 = "";

      for (int var4 = 0; var4 < var2; var4++) {
         var3 = var3 + this.getResourceString("ExitMsg" + (var4 + 1)) + "\n";
      }

      int var5 = JOptionPane.showConfirmDialog(this, var3, var1, 0);
      if (var5 == 0) {
         this.setVisible(false);
         this.dispose();
         System.exit(0);
      }
   }

   public ResourceBundle getResources() {
      return resources;
   }

   protected void disableDefault() {
      String[] var1 = this.tokenize(this.getResourceString("disable"));

      for (int var3 = 0; var3 < this.defaultActions.length; var3++) {
         String var2 = (String)this.defaultActions[var3].getValue("Name");

         for (int var4 = 0; var4 < var1.length; var4++) {
            if (var2.equals(var1[var4])) {
               this.defaultActions[var3].setEnabled(false);
               break;
            }
         }
      }
   }

   public void stateChanged(ChangeEvent var1) {
      SenecaDatasetCollection var2 = (SenecaDatasetCollection)var1.getSource();
      JMenu var3 = this.menubar.getMenu(this.menubar.getMenuCount() - 2);
      JMenu var4 = (JMenu)var3.getItem(0);
      var4.removeAll();

      for (int var5 = 0; var5 < var2.size(); var5++) {
         JMenuItem var6 = new JMenuItem(((SenecaDataset)var2.elementAt(var5)).getName());
         var6.addActionListener(new Seneca.SwitchDatasetAction());
         var4.add(var6);
      }

      if (var2.getCurrentDataset() != null) {
         this.setStatus(var2.getCurrentDataset().getName(), 1);
         this.setStatus(var2.getCurrentDataset().getMolecularFormula(), 2);
      } else {
         this.setStatus("No dataset loaded", 1);
         this.setStatus("", 2);
      }
   }

   static {
      try {
         resources = ResourceBundle.getBundle("seneca.client.Seneca", Locale.getDefault());
         if (debug) {
            System.out.println(resources);
         }

         senecaHome = System.getProperty("seneca.home");
      } catch (MissingResourceException var1) {
         System.err.println("Seneca.properties not found");
         System.exit(1);
      }

      desktop = new JDesktopPane();
   }

   class AboutAction extends AbstractAction {
      JFrame frame;

      AboutAction(JFrame var2) {
         super("about");
         this.frame = var2;
      }

      public void actionPerformed(ActionEvent var1) {
         String var2 = Seneca.this.getResourceString("AboutTitle");
         int var3 = Integer.valueOf(Seneca.this.getResourceString("AboutLines"));
         String var4 = "";

         for (int var5 = 0; var5 < var3; var5++) {
            var4 = var4 + Seneca.this.getResourceString("AboutMsg" + (var5 + 1)) + "\n";
         }

         JOptionPane.showMessageDialog(null, var4, var2, 1);
      }
   }

   class ConnMatAction extends AbstractAction {
      JFrame frame;

      ConnMatAction(JFrame var2) {
         super("ConnMat");
         this.frame = var2;
      }

      public void actionPerformed(ActionEvent var1) {
      }
   }

   class CopyAction extends AbstractAction {
      JFrame frame;

      CopyAction(JFrame var2) {
         super("copy");
         this.frame = var2;
      }

      public void actionPerformed(ActionEvent var1) {
      }
   }

   class CutAction extends AbstractAction {
      JFrame frame;

      CutAction(JFrame var2) {
         super("cut");
         this.frame = var2;
      }

      public void actionPerformed(ActionEvent var1) {
      }
   }

   class ExitAction extends AbstractAction {
      JFrame frame;

      ExitAction(JFrame var2) {
         super("exit");
         this.frame = var2;
      }

      public void actionPerformed(ActionEvent var1) {
         Seneca.this.trySystemExit();
      }
   }

   class ImportLUCYAction extends AbstractAction {
      ImportLUCYAction() {
         super("LUCYimp");
      }

      public void actionPerformed(ActionEvent var1) {
         SenecaParameters var2 = new SenecaParameters();
         JFileChooser var3 = new JFileChooser(var2.getLucyFileLocation());
         ExtensionFileFilter var4 = new ExtensionFileFilter();
         var4.addExtension("col");
         var4.setDescription("Lucy datasets");
         var3.setFileFilter(var4);
         int var5 = var3.showOpenDialog(Seneca.this);
         if (var5 == 0) {
            File var6 = var3.getSelectedFile();
            var2.setLucyFileLocation(var6.getParent());
            if (Seneca.debug) {
               System.out.println("Importing LUCY file " + var6 + "...");
            }

            SenecaDataset var7 = new LUCYFileHandler().importLUCYDataset(var6);
            Seneca.senecaDatasets.add(var7);
            if (Seneca.debug) {
               System.out.println("Importing LUCY file " + var6 + " done.");
            }

            SenecaDatasetFrame var8 = new SenecaDatasetFrame(Seneca.senecaDatasets.getCurrentDataset());
            Seneca.this.getContentPane().add(var8);
            var8.toFront();

            try {
               var8.setVisible(true);
               var8.setSelected(true);
            } catch (PropertyVetoException var10) {
               System.err.println("Problem activating new JInternalFrame");
            }
         }
      }
   }

   class JavaLnFAction extends AbstractAction {
      JFrame frame;

      JavaLnFAction(JFrame var2) {
         super("javaLnF");
         this.frame = var2;
      }

      public void actionPerformed(ActionEvent var1) {
         try {
            UIManager.setLookAndFeel("javax.swing.plaf.jlf.JLFLookAndFeel");
            SwingUtilities.updateComponentTreeUI(this.frame);
            this.frame.pack();
         } catch (Exception var3) {
            System.err.println("Could not locate requested look&feel");
         }
      }
   }

   class MetalLnFAction extends AbstractAction {
      JFrame frame;

      MetalLnFAction(JFrame var2) {
         super("metalLnF");
         this.frame = var2;
      }

      public void actionPerformed(ActionEvent var1) {
         try {
            UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
            SwingUtilities.updateComponentTreeUI(this.frame);
            this.frame.pack();
         } catch (Exception var3) {
            System.err.println("Could not locate requested look&feel");
         }
      }
   }

   class MotifLnFAction extends AbstractAction {
      JFrame frame;

      MotifLnFAction(JFrame var2) {
         super("motifLnF");
         this.frame = var2;
      }

      public void actionPerformed(ActionEvent var1) {
         try {
            UIManager.setLookAndFeel("javax.swing.plaf.motif.MotifLookAndFeel");
            SwingUtilities.updateComponentTreeUI(this.frame);
            this.frame.pack();
         } catch (Exception var3) {
            System.err.println("Could not locate requested look&feel");
         }
      }
   }

   class NewAction extends AbstractAction {
      JFrame frame;

      NewAction(JFrame var2) {
         super("new");
         this.frame = var2;
      }

      public void actionPerformed(ActionEvent var1) {
         Seneca.senecaDatasets.add(new SenecaDataset("untitled" + new Date().hashCode()));
         SenecaDatasetFrame var2 = new SenecaDatasetFrame(Seneca.senecaDatasets.getCurrentDataset());
         Seneca.this.getContentPane().add(var2);
         var2.toFront();

         try {
            var2.setSelected(true);
         } catch (PropertyVetoException var4) {
            System.err.println("Problem activating new JInternalFrame");
         }
      }
   }

   class NodeTabAction extends AbstractAction {
      JFrame frame;

      NodeTabAction(JFrame var2) {
         super("NodeTab");
         this.frame = var2;
      }

      public void actionPerformed(ActionEvent var1) {
         new NodeTableFrame(Seneca.senecaDatasets.getCurrentDataset().getSetOfNodes(), this.frame);
      }
   }

   class OpenAllAction extends AbstractAction {
      JFrame frame;

      OpenAllAction(JFrame var2) {
         super("openAll");
         this.frame = var2;
      }

      public void actionPerformed(ActionEvent var1) {
         SenecaParameters var2 = new SenecaParameters();
         JFileChooser var3 = new JFileChooser(var2.getSenecaFileLocation());
         ExtensionFileFilter var4 = new ExtensionFileFilter();
         var4.addExtension("sml");
         var4.setDescription("SENECA Dataset");
         var3.setFileFilter(var4);
         int var5 = var3.showOpenDialog(Seneca.this);
         if (var5 == 0) {
            File var6 = var3.getSelectedFile();
            var2.setSenecaFileLocation(var6.getParent());
            Seneca.this.openFile(var6);
         }
      }
   }

   class PasteAction extends AbstractAction {
      JFrame frame;

      PasteAction(JFrame var2) {
         super("paste");
         this.frame = var2;
      }

      public void actionPerformed(ActionEvent var1) {
         Clipboard var2 = this.frame.getToolkit().getSystemClipboard();
         Transferable var3 = var2.getContents(this.frame);

         try {
            String var7 = (String)var3.getTransferData(DataFlavor.stringFlavor);
         } catch (Exception var6) {
            String var4 = var3.toString();
         }
      }
   }

   class PrintAction extends AbstractAction {
      JFrame frame;

      PrintAction(JFrame var2) {
         super("print");
         this.frame = var2;
      }

      public void actionPerformed(ActionEvent var1) {
      }
   }

   class SaveAllAction extends AbstractAction {
      JFrame frame;

      SaveAllAction(JFrame var2) {
         super("saveAll");
         this.frame = var2;
      }

      public void actionPerformed(ActionEvent var1) {
         SenecaParameters var2 = new SenecaParameters();
         JFileChooser var3 = new JFileChooser(var2.getSenecaFileLocation());
         ExtensionFileFilter var4 = new ExtensionFileFilter();
         var4.addExtension("sml");
         var4.setDescription("SENECA Dataset");
         var3.setFileFilter(var4);
         int var5 = var3.showSaveDialog(Seneca.this);
         if (var5 == 0) {
            File var6 = var3.getSelectedFile();
            var2.setSenecaFileLocation(var6.getParent());

            try {
               FileWriter var7 = new FileWriter(var6);
               var7.write(new SpecMLGenerator().convert(Seneca.senecaDatasets.getCurrentDataset()));
               var7.flush();
               var7.close();
            } catch (IOException var8) {
               System.out.println(var8);
            }
         }
      }
   }

   class StrucDispAction extends AbstractAction {
      JFrame frame;

      StrucDispAction(JFrame var2) {
         super("curSol");
         this.frame = var2;
      }

      public void actionPerformed(ActionEvent var1) {
         StrucDispFrame var2 = new StrucDispFrame();
         var2.setSize(new Dimension(600, 400));
      }
   }

   class StructureGenerationListener implements ChangeListener {
      StrucDispFrame sdf = null;
      StructureGenerator sg;
      JMDraw jmd;

      public StructureGenerationListener() {
      }

      public void stateChanged(ChangeEvent var1) {
      }
   }

   class SwitchDatasetAction extends AbstractAction {
      SwitchDatasetAction() {
         super("switchDS");
      }

      public void actionPerformed(ActionEvent var1) {
         Seneca.senecaDatasets.setCurrentDatasetNumber(((JMenuItem)var1.getSource()).getText());
      }
   }

   class WinLnFAction extends AbstractAction {
      JFrame frame;

      WinLnFAction(JFrame var2) {
         super("winLnF");
         this.frame = var2;
      }

      public void actionPerformed(ActionEvent var1) {
         try {
            UIManager.setLookAndFeel("javax.swing.plaf.windows.WindowsLookAndFeel");
            SwingUtilities.updateComponentTreeUI(this.frame);
            this.frame.pack();
         } catch (Exception var3) {
            System.err.println("Could not locate requested look&feel");
         }
      }
   }

   class genThreadsAction extends AbstractAction {
      JFrame frame;

      genThreadsAction(JFrame var2) {
         super("genThreads");
         this.frame = var2;
      }

      public void actionPerformed(ActionEvent var1) {
      }
   }

   class genpropsAction extends AbstractAction {
      JFrame frame;

      genpropsAction(JFrame var2) {
         super("GenProps");
         this.frame = var2;
      }

      public void actionPerformed(ActionEvent var1) {
         SenecaDatasetFrame var2 = new SenecaDatasetFrame(Seneca.senecaDatasets.getCurrentDataset());
         var2.setVisible(true);
      }
   }
}
