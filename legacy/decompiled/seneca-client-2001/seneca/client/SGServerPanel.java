package seneca.client;

import com.jrefinery.chart.AbstractDataSource;
import com.jrefinery.chart.Axis;
import com.jrefinery.chart.JFreeChart;
import com.jrefinery.chart.Plot;
import com.jrefinery.chart.StandardTitle;
import com.jrefinery.chart.XYDataSource;
import com.jrefinery.chart.event.ChartChangeEvent;
import com.jrefinery.chart.event.ChartChangeListener;
import compchem.Atom;
import compchem.ChemFileUtils;
import compchem.Molecule;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.Rectangle2D.Double;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StringReader;
import java.rmi.Naming;
import java.util.Vector;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import javax.swing.text.html.HTMLEditorKit;
import jmdraw.JMDraw;
import org.apache.log4j.Category;
import seneca.compute.Compute;
import seneca.core.SenecaDataset;
import seneca.engine.StructureGeneratorServer;
import seneca.gatekeeper.GateKeeperInterface;
import seneca.gatekeeper.ServerInfo;
import seneca.judges.Judge;
import seneca.judges.ScoreSummary;
import seneca.structgen.AnnealingLog;
import seneca.structgen.AnnealingSchedule;
import seneca.structgen.StructureGeneratorResult;
import seneca.structgen.StructureGeneratorStatus;

public class SGServerPanel extends JPanel {
   boolean debug = false;
   protected TableModel tableModel;
   protected JTable table;
   protected JScrollPane scrollpane;
   protected JScrollPane reportScrollPane;
   protected Box southBox;
   protected Box centerBox;
   protected Box centerCenterBox;
   protected Box topCenterBox;
   protected JPanel handleSelectedButtonsPanel;
   protected JPanel serverAdminButtonsPanel;
   protected JPanel resultButtonsPanel;
   JMDrawPanel jmdPanel = null;
   SGServerPanel.ChartPanel chartPanel;
   JFreeChart chart;
   SGServerPanel.AnnealingDataSource dataSource;
   Float[][][] alData;
   float maxScore = 0.0F;
   StructureGeneratorStatus[] sgs = null;
   Border border = null;
   protected JTextField statusField;
   protected JEditorPane reportPane;
   protected JButton closeButton;
   protected JButton stopSelectedButton;
   protected JButton updateButton;
   protected JButton showCurrentResultButton;
   protected JButton goButton;
   protected JButton resetButton;
   protected JButton startLocalButton;
   protected JButton clearListButton;
   protected JButton logAnnealingDataButton;
   protected Timer tableUpdateTimer;
   protected Vector sgServerProcesses = new Vector();
   SenecaDataset sd;
   StructureGeneratorResult structureGeneratorResult;
   public Dimension jmdDim = new Dimension(300, 300);
   Vector schedules;
   int currentBest = 0;
   boolean newModelNeeded = true;
   static int ALL = 0;
   static int SEL = 1;

   public SGServerPanel(SenecaDataset var1) {
      this.table = new SGServerPanel$1(this, new SGServerPanel.SGServerTableModel());
      this.sd = var1;
      this.schedules = var1.annealingSchedules;
      FontMetrics var2 = this.getFontMetrics(this.table.getFont());

      for (int var3 = 0; var3 < this.table.getColumnCount(); var3++) {
         TableColumn var4 = this.table.getColumn(this.table.getColumnName(var3));
         var4.setWidth((int)(var2.stringWidth(this.table.getColumnName(var3)) * 1.2));
         DefaultTableCellRenderer var5 = new DefaultTableCellRenderer();
         var5.setBackground(Color.yellow);
      }

      Object var10 = null;
      Object var11 = null;
      boolean var6 = false;
      boolean var7 = false;
      new JButton("");
      this.setCellRendering();
      this.scrollpane = new JScrollPane(this.table);
      this.scrollpane.setBorder(new BevelBorder(1));
      this.setLayout(new BorderLayout());
      this.southBox = new Box(0);
      this.handleSelectedButtonsPanel = new JPanel(new GridLayout(3, 1));
      this.serverAdminButtonsPanel = new JPanel(new GridLayout(3, 1));
      this.resultButtonsPanel = new JPanel(new GridLayout(3, 1));
      String var9 = "Server Admin";
      this.serverAdminButtonsPanel.setBorder(BorderFactory.createTitledBorder(this.border, var9));
      var9 = "Selections";
      this.handleSelectedButtonsPanel.setBorder(BorderFactory.createTitledBorder(this.border, var9));
      var9 = "Result";
      this.resultButtonsPanel.setBorder(BorderFactory.createTitledBorder(this.border, var9));
      this.centerBox = new Box(1);
      this.topCenterBox = new Box(0);
      this.centerCenterBox = new Box(0);
      this.statusField = new JTextField();
      this.startLocalButton = new JButton("Start local server");
      this.startLocalButton.addActionListener(new SGServerPanel.StartLocalAction());
      this.updateButton = new JButton("Update server list");
      this.updateButton.addActionListener(new SGServerPanel.ScanNetAction());
      this.clearListButton = new JButton("Clear Server List");
      this.clearListButton.addActionListener(new SGServerPanel.ClearListAction());
      this.serverAdminButtonsPanel.add(this.startLocalButton);
      this.serverAdminButtonsPanel.add(this.updateButton);
      this.serverAdminButtonsPanel.add(this.clearListButton);
      this.goButton = new JButton("Start as selected");
      this.goButton.addActionListener(new SGServerPanel.goAction());
      this.stopSelectedButton = new JButton("Stop Selected");
      this.stopSelectedButton.addActionListener(new SGServerPanel.stopSelectedAction());
      this.resetButton = new JButton("Reset selected");
      this.resetButton.addActionListener(new SGServerPanel.ResetSelectedAction());
      this.handleSelectedButtonsPanel.add(this.goButton);
      this.handleSelectedButtonsPanel.add(this.stopSelectedButton);
      this.handleSelectedButtonsPanel.add(this.resetButton);
      this.showCurrentResultButton = new JButton("Summarize Result");
      this.showCurrentResultButton.addActionListener(new SGServerPanel.ShowCurrentAction());
      this.logAnnealingDataButton = new JButton("Log Annealing Data");
      this.logAnnealingDataButton.addActionListener(new SGServerPanel.LogAnnealingDataAction());
      this.resultButtonsPanel.add(this.showCurrentResultButton);
      this.resultButtonsPanel.add(this.logAnnealingDataButton);
      this.southBox.add(this.serverAdminButtonsPanel);
      this.southBox.add(this.handleSelectedButtonsPanel);
      this.southBox.add(this.resultButtonsPanel);
      this.add("South", this.southBox);
      this.scrollpane.setPreferredSize(new Dimension(400, 200));
      this.reportPane = new JEditorPane();
      this.reportPane.setEditable(false);
      this.reportPane.setEditorKit(new HTMLEditorKit());
      this.reportScrollPane = new JScrollPane(this.reportPane);
      this.reportScrollPane.setPreferredSize(new Dimension(200, 300));
      this.border = BorderFactory.createEtchedBorder();
      this.jmdPanel = new JMDrawPanel();
      this.jmdPanel.setPreferredSize(new Dimension(200, 300));
      var9 = "No structure available";
      this.jmdPanel.setBorder(BorderFactory.createTitledBorder(this.border, var9));
      var9 = "Current Structure Evaluation";
      this.reportScrollPane.setBorder(BorderFactory.createTitledBorder(this.border, var9));
      this.reportScrollPane.setBackground(Color.white);
      this.chartPanel = this.getAnnealingViewPanel();
      var9 = "Annealing Data";
      this.chartPanel.setBorder(BorderFactory.createTitledBorder(this.border, var9));
      this.topCenterBox.add(this.scrollpane);
      this.topCenterBox.add(this.chartPanel);
      this.centerBox.add(this.topCenterBox);
      this.centerCenterBox.add(this.jmdPanel);
      this.centerCenterBox.add(this.reportScrollPane);
      this.centerBox.add(this.centerCenterBox);
      this.add("Center", this.centerBox);
      this.add("North", this.statusField);
      this.tableUpdateTimer = new Timer(2000, new SGServerPanel.updateAction());
      this.tableUpdateTimer.start();
      this.scrollpane.repaint();
   }

   void updateSgs() {
      StructureGeneratorStatus[] var1 = new StructureGeneratorStatus[this.sgServerProcesses.size()];
      if (this.sgs != null) {
         for (int var2 = 0; var2 < this.sgs.length; var2++) {
            var1[var2] = this.sgs[var2];
         }
      }

      this.sgs = var1;
   }

   private SGServerPanel.ChartPanel getAnnealingViewPanel() {
      this.dataSource = new SGServerPanel.AnnealingDataSource();
      this.chart = JFreeChart.createXYChart(this.dataSource);
      this.chart.setTitle(new StandardTitle("", new Font("SansSerif", 0, 6)));
      Plot var1 = this.chart.getPlot();
      Axis var2 = var1.getAxis(0);
      var2.setLabel("Iteration");
      Axis var3 = var1.getAxis(1);
      var3.setLabel("Score");
      this.chartPanel = new SGServerPanel.ChartPanel(this.chart);
      this.chartPanel.addMouseListener(this.dataSource);
      return this.chartPanel;
   }

   protected void setCellRendering() {
      JLabel var1 = new JLabel();
      this.table.setRowSelectionAllowed(true);
      this.table.setColumnSelectionAllowed(false);

      for (int var3 = 0; var3 < this.table.getColumnCount(); var3++) {
         TableColumn var2 = this.table.getColumn(this.table.getColumnName(var3));
         var1.setVerticalAlignment(0);
         var1.setHorizontalAlignment(0);
         DefaultTableCellRenderer var4 = new DefaultTableCellRenderer();
         var4.setBackground(Color.yellow);
         var2.setCellRenderer(var4);
      }
   }

   class AnnealingDataSource extends AbstractDataSource implements XYDataSource, MouseListener {
      AnnealingLog al = null;
      int SCORES = 1;
      int KT = 0;
      int displayType = this.SCORES;
      float maxX = 0.0F;
      int maxSeries = 0;
      int maxItemCount = 0;

      public AnnealingDataSource() {
      }

      public Number getXValue(int var1, int var2) {
         if (this.al == null) {
            return new Float(1.0F);
         } else if (var1 == this.al.getSeriesCount()) {
            Float[] var4 = this.al.getEntry(this.maxSeries, var2);
            return var4[0];
         } else {
            Float[] var3 = this.al.getEntry(var1, var2);
            if (var3[0] > this.maxX) {
               this.maxX = var3[0];
               this.maxSeries = var1;
            }

            return var3[0];
         }
      }

      public Number getYValue(int var1, int var2) {
         if (this.al == null) {
            return new Float(1.0F);
         } else if (var1 == this.al.getSeriesCount()) {
            return new Float(SGServerPanel.this.maxScore);
         } else {
            Float[] var3 = this.al.getEntry(var1, var2);
            return var3[1 + this.displayType];
         }
      }

      public int getSeriesCount() {
         if (this.al == null) {
            return 1;
         } else {
            return this.displayType == this.SCORES ? this.al.getSeriesCount() + 1 : this.al.getSeriesCount();
         }
      }

      public String getSeriesName(int var1) {
         if (this.al == null) {
            return "no data";
         } else {
            return var1 == this.al.getSeriesCount() ? "max. Score" : "host " + (var1 + 1);
         }
      }

      public void addData(AnnealingLog var1, int var2) {
         int var3 = var1.getItemCount(0);
         boolean var5 = false;

         for (int var6 = 0; var6 < var3; var6++) {
            Float[] var4 = var1.getEntry(0, var6);
            this.al.addEntry(var2, this.al.getItemCount(var2), var4);
         }
      }

      public AnnealingLog getData() {
         return this.al;
      }

      public void setData(AnnealingLog var1) {
         this.al = var1;
      }

      public void init(int var1) {
         this.al = new AnnealingLog(var1, 2);
      }

      public int getItemCount(int var1) {
         int var2 = 0;
         if (this.al == null) {
            return 1;
         } else if (var1 == this.al.getSeriesCount()) {
            return this.maxItemCount;
         } else {
            var2 = this.al.getItemCount(var1);
            if (var2 > this.maxItemCount) {
               this.maxItemCount = var2;
            }

            return this.al.getItemCount(var1);
         }
      }

      public int getDisplayType() {
         return this.displayType;
      }

      public void mouseClicked(MouseEvent var1) {
         if (this.displayType == this.SCORES) {
            this.displayType = this.KT;
         } else {
            this.displayType = this.SCORES;
         }

         SGServerPanel.ChartPanel var2 = (SGServerPanel.ChartPanel)var1.getSource();
         JFreeChart var3 = var2.getChart();
         Plot var4 = var3.getPlot();
         Axis var5 = var4.getAxis(0);
         var5.setLabel("Iteration");
         Axis var6 = var4.getAxis(1);
         if (this.displayType == this.KT) {
            var6.setLabel("kT");
         } else {
            var6.setLabel("Score");
         }
      }

      public void logResults() {
         Category var1 = Category.getInstance("seneca.structgen.StochasticGenerator.annealing.log");
         int var5 = 0;

         for (int var6 = 0; var6 < this.al.getSeriesCount(); var6++) {
            if (this.al.getItemCount(var6) > var5) {
               var5 = this.al.getItemCount(var6);
            }
         }

         Float[][] var7 = new Float[var5][this.al.getSeriesCount() + 1];

         for (int var8 = 0; var8 < this.al.getSeriesCount(); var8++) {
            for (int var9 = 0; var9 < this.al.getItemCount(var8); var9++) {
               Float[] var2 = this.al.getEntry(var8, var9);
               var7[var9][0] = var2[0];
               var7[var9][var8 + 1] = var2[2];
            }
         }

         String var3 = "Iteration; ";

         for (int var13 = 0; var13 < this.al.getSeriesCount(); var13++) {
            var3 = var3 + "Server " + (var13 + 1) + "; ";
         }

         System.out.println(var3);
         var1.info(var3 + "\n");

         for (int var10 = 0; var10 < var5; var10++) {
            var3 = "";

            for (int var11 = 0; var11 < this.al.getSeriesCount() + 1; var11++) {
               if (var7[var10][var11] != null) {
                  var3 = var3 + var7[var10][var11];
               }

               var3 = var3 + "; ";
            }

            System.out.println(var3);
            var1.info(var3 + "\n");
         }
      }

      public void mousePressed(MouseEvent var1) {
      }

      public void mouseReleased(MouseEvent var1) {
      }

      public void mouseEntered(MouseEvent var1) {
      }

      public void mouseExited(MouseEvent var1) {
      }
   }

   class ChartPanel extends JComponent implements ChartChangeListener {
      protected JFreeChart chart;

      public ChartPanel(JFreeChart var2) {
         this.chart = var2;
         this.chart.addChangeListener(this);
         this.setPreferredSize(new Dimension(400, 400));
      }

      public void setChart(JFreeChart var1) {
         this.chart = var1;
         this.chart.addChangeListener(this);
      }

      public JFreeChart getChart() {
         return this.chart;
      }

      public void paintComponent(Graphics var1) {
         Graphics2D var2 = (Graphics2D)var1;
         Dimension var3 = this.getSize();
         Insets var4 = this.getInsets();
         Double var5 = new Double(var4.left, var4.top, var3.getWidth() - var4.left - var4.right, var3.getHeight() - var4.top - var4.bottom);

         try {
            this.chart.draw(var2, var5);
         } catch (Exception var7) {
         }
      }

      public void chartChanged(ChartChangeEvent var1) {
         this.repaint();
      }
   }

   class ClearListAction extends AbstractAction {
      ClearListAction() {
         super("ClearList");
      }

      public void actionPerformed(ActionEvent var1) {
         SGServerPanel.this.tableUpdateTimer.stop();
         SGServerPanel.this.sgServerProcesses.setSize(0);
         SGServerPanel.this.tableUpdateTimer.start();
      }
   }

   class LogAnnealingDataAction extends AbstractAction {
      LogAnnealingDataAction() {
         super("LogAnnealingData");
      }

      public void actionPerformed(ActionEvent var1) {
         SGServerPanel.this.dataSource.logResults();
      }
   }

   class ResetSelectedAction extends AbstractAction {
      ResetSelectedAction() {
         super("ResetSelected");
      }

      public void actionPerformed(ActionEvent var1) {
         SGServerPanel.this.dataSource.setData(null);
         int[] var2 = SGServerPanel.this.table.getSelectedRows();
         int[] var3 = new int[SGServerPanel.this.sgServerProcesses.size()];

         for (int var4 = 0; var4 < var2.length; var4++) {
            var3[var2[var4]] = 1;
         }

         for (int var6 = 0; var6 < SGServerPanel.this.sgServerProcesses.size(); var6++) {
            if (var3[var6] == 1) {
               Compute var5 = (Compute)SGServerPanel.this.sgServerProcesses.elementAt(var6);

               try {
                  var5.resetTask();
               } catch (Exception var8) {
                  if (SGServerPanel.this.debug) {
                     System.out.println("Error while stopping calculation on no." + var6);
                  }

                  if (SGServerPanel.this.debug) {
                     System.out.println(var8.toString());
                  }
               }
            }
         }
      }
   }

   class SGServerTableModel extends AbstractTableModel {
      String[] names = new String[]{"No.", "ID", "Host", "Dataset", "MF", "Status ", "Iter."};

      public int getColumnCount() {
         return this.names.length;
      }

      public int getRowCount() {
         return SGServerPanel.this.sgServerProcesses.size();
      }

      public Object getValueAt(int var1, int var2) {
         Compute var3 = (Compute)SGServerPanel.this.sgServerProcesses.elementAt(var1);
         switch (var2) {
            case 0:
               return String.valueOf(var1 + 1);
            case 1:
               try {
                  return var3.getID();
               } catch (Exception var10) {
                  System.err.println("getID(): " + var10.toString());
               }
            case 2:
               try {
                  return var3.getHostName();
               } catch (Exception var9) {
                  System.err.println("getHostName(): " + var9.toString());
               }
            case 3:
               try {
                  if (SGServerPanel.this.sgs[var1] == null) {
                     return "none";
                  }

                  return SGServerPanel.this.sgs[var1].datasetName;
               } catch (Exception var8) {
                  System.err.println("getDatasetName(): " + var8.toString());
               }
            case 4:
               try {
                  if (SGServerPanel.this.sgs[var1] == null) {
                     return "none";
                  }

                  return SGServerPanel.this.sgs[var1].molecularFormula;
               } catch (Exception var7) {
                  System.err.println("getMF(): " + var7.toString());
               }
            case 5:
               try {
                  if (SGServerPanel.this.sgs[var1] == null) {
                     return "idle";
                  }

                  return SGServerPanel.this.sgs[var1].status;
               } catch (Exception var6) {
                  System.err.println("getStatus(): " + var6.toString());
               }
            case 6:
               try {
                  if (SGServerPanel.this.sgs[var1] == null) {
                     return new String("none");
                  }

                  return new String("" + SGServerPanel.this.sgs[var1].iteration);
               } catch (Exception var5) {
                  System.err.println("getCurrentIteration(): " + var5.toString());
               }
            default:
               return "-";
         }
      }

      public String getColumnName(int var1) {
         return this.names[var1];
      }

      public Class getColumnClass(int var1) {
         return this.getValueAt(0, var1).getClass();
      }

      public boolean isCellEditable(int var1, int var2) {
         return var2 != 0;
      }

      public void setValueAt(Object var1, int var2, int var3) {
      }
   }

   class ScanNetAction extends AbstractAction {
      ScanNetAction() {
         super("ScanNet");
      }

      public void actionPerformed(ActionEvent var1) {
         new Vector();
         SGServerPanel.this.sgServerProcesses.setSize(0);

         Vector var4;
         try {
            String var5 = "www.nmrshiftdb.org";
            String var2 = "//" + var5 + "/GateKeeper";
            GateKeeperInterface var6 = (GateKeeperInterface)Naming.lookup(var2);
            var4 = var6.getHosts(8);
         } catch (Exception var8) {
            var8.printStackTrace();
            return;
         }

         for (int var10 = 0; var10 < var4.size(); var10++) {
            ServerInfo var3 = (ServerInfo)var4.elementAt(var10);

            try {
               String var9 = "//" + var3.getHostname() + "/StoIcGentServer";
               Compute var11 = (Compute)Naming.lookup(var9);
               if (var11.willingToManage()) {
                  SGServerPanel.this.sgServerProcesses.addElement(var11);
                  SGServerPanel.this.table.revalidate();
                  SGServerPanel.this.scrollpane.revalidate();
               }
            } catch (Exception var7) {
            }
         }

         SGServerPanel.this.sgs = new StructureGeneratorStatus[SGServerPanel.this.sgServerProcesses.size()];
      }
   }

   class ShowCurrentAction extends AbstractAction {
      ShowCurrentAction() {
         super("Show Current");
      }

      public void actionPerformed(ActionEvent var1) {
         int var2 = 0;
         StrucDispFrame var3 = null;
         Compute var6 = null;
         JMDrawPanel var7 = null;
         Object var8 = null;
         String var9 = "";
         String var10 = "";
         StructureGeneratorResult var11 = new StructureGeneratorResult(SGServerPanel.this.sgServerProcesses.size() * 30);
         Object var12 = null;
         Object var13 = null;
         ScoreSummary var14 = null;

         try {
            for (int var15 = 0; var15 < SGServerPanel.this.sgServerProcesses.size(); var15++) {
               var6 = (Compute)SGServerPanel.this.sgServerProcesses.elementAt(var15);
               if (SGServerPanel.this.debug) {
                  System.out.println("Getting result from host " + (var15 + 1));
               }

               var12 = (StructureGeneratorResult)var6.getTaskResult();
               if (var12 != null) {
                  var2 = ((StructureGeneratorResult)var12).structures.size();
                  if (SGServerPanel.this.debug) {
                     System.out.println("Host " + (var15 + 1) + " has " + var2 + " structures.");
                  }

                  for (int var16 = 0; var16 < var2; var16++) {
                     var11.structures.push(((StructureGeneratorResult)var12).structures.pop());
                     var14 = (ScoreSummary)((StructureGeneratorResult)var12).scoreSummaries.pop();
                     var14.description = var14.description + "<p>Result from host " + (var15 + 1) + "</p>";
                     var11.scoreSummaries.push(var14);
                  }
               }
            }
         } catch (Exception var18) {
            if (SGServerPanel.this.debug) {
               System.out.println(var18.toString());
            }
         }

         if (SGServerPanel.this.debug) {
            System.out.println("Found a total of " + var11.structures.size() + " structures.");
         }

         if (var3 == null) {
            var3 = new StrucDispFrame();
            var3.setSize(new Dimension(600, 400));
         }

         var3.setVisible(true);

         try {
            var10 = var6.getID() + " Structure ";
         } catch (Exception var17) {
            if (SGServerPanel.this.debug) {
               System.out.println(var17.toString());
            }
         }

         if (SGServerPanel.this.debug) {
            System.out.println("Isomorphism Check...");
         }

         var11.removeIsomorphism();
         var11.sort();
         if (SGServerPanel.this.debug) {
            System.out.println("Isomorphism Check done");
         }

         for (int var26 = 0; var26 < var11.structures.size(); var26++) {
            var9 = "<html><b>Score " + ((ScoreSummary)var11.scoreSummaries.elementAt(var26)).score + ": </b>";
            var9 = var9 + ((ScoreSummary)var11.scoreSummaries.elementAt(var26)).description + "</html>";
            var7 = new JMDrawPanel();
            var7.setJMDraw(new JMDraw(ChemFileUtils.readMDLMolFile(new BufferedReader(new StringReader((String)var11.structures.elementAt(var26))))));
            var7.setPreferredSize(new Dimension(300, 200));
            var7.setToolTipText(var9);
            var7.getJMDraw().draw();
            var3.addStructure(var7, "Score " + ((ScoreSummary)var11.scoreSummaries.elementAt(var26)).score);
         }
      }
   }

   class StartLocalAction extends AbstractAction {
      StartLocalAction() {
         super("StartLocal");
      }

      public void actionPerformed(ActionEvent var1) {
         try {
            SGServerPanel.this.tableUpdateTimer.stop();
            SGServerPanel.this.sgServerProcesses.addElement(new StructureGeneratorServer("local" + SGServerPanel.this.sgServerProcesses.size()));
            SGServerPanel.this.updateSgs();
            SGServerPanel.this.sgs[SGServerPanel.this.sgs.length - 1] = (StructureGeneratorStatus)((Compute)SGServerPanel.this.sgServerProcesses.lastElement())
               .getTaskStatus();
            SGServerPanel.this.tableUpdateTimer.start();
            SGServerPanel.this.scrollpane.repaint();
         } catch (Exception var3) {
            if (SGServerPanel.this.debug) {
               System.out.println("Local StoicGent service could not be created.");
            }

            var3.printStackTrace();
         }

         SGServerPanel.this.table.revalidate();
         SGServerPanel.this.scrollpane.revalidate();
      }
   }

   class goAction extends AbstractAction {
      goAction() {
         super("go");
      }

      public void actionPerformed(ActionEvent var1) {
         boolean var2 = false;
         Vector var3 = null;
         Vector var4 = null;
         Judge var5 = null;
         int[] var7 = SGServerPanel.this.table.getSelectedRows();
         int[] var8 = new int[SGServerPanel.this.sgServerProcesses.size()];

         for (int var9 = 0; var9 < var7.length; var9++) {
            var8[var7[var9]] = 1;
         }

         Atom[] var10 = SGServerPanel.this.sd.getSetOfNodes();

         for (int var12 = 0; var12 < SGServerPanel.this.sgServerProcesses.size(); var12++) {
            if (var8[var12] == 1) {
               Compute var11 = (Compute)SGServerPanel.this.sgServerProcesses.elementAt(var12);
               var3 = new Vector();

               for (int var13 = 0; var13 < SGServerPanel.this.sd.judges.size(); var13++) {
                  var5 = (Judge)SGServerPanel.this.sd.judges.elementAt(var13);
                  if (var5.getEnabled()) {
                     var5.calcMaxScore();
                     var3.addElement(var5.clone());
                  }
               }

               var4 = new Vector();

               try {
                  for (int var14 = 0; var14 < SGServerPanel.this.sd.annealingSchedules.size(); var14++) {
                     var4.addElement(((AnnealingSchedule)SGServerPanel.this.sd.annealingSchedules.elementAt(var14)).clone());
                  }

                  var11.setMolecule((Molecule)this.serClone(new Molecule(SGServerPanel.this.sd.getSetOfNodes())));
                  var11.setJudges((Vector)this.serClone(SGServerPanel.this.sd.judges));
                  var11.setDatasetName(SGServerPanel.this.sd.getName());
                  var11.setSASchedules((Vector)this.serClone(var4));
                  var11.executeTask();
               } catch (Exception var15) {
                  if (SGServerPanel.this.debug) {
                     System.out.println("Error while starting calculation on no." + var12);
                  }

                  if (SGServerPanel.this.debug) {
                     System.out.println(var15.toString());
                  }

                  var15.printStackTrace();
               }
            }
         }
      }

      Object serClone(Object var1) {
         try {
            ByteArrayOutputStream var2 = new ByteArrayOutputStream();
            ObjectOutputStream var3 = new ObjectOutputStream(var2);
            var3.writeObject(var1);
            ObjectInputStream var4 = new ObjectInputStream(new ByteArrayInputStream(var2.toByteArray()));
            return var4.readObject();
         } catch (Exception var5) {
            return null;
         }
      }
   }

   class stopSelectedAction extends AbstractAction {
      stopSelectedAction() {
         super("stopSelected");
      }

      public void actionPerformed(ActionEvent var1) {
         int[] var2 = SGServerPanel.this.table.getSelectedRows();
         int[] var3 = new int[SGServerPanel.this.sgServerProcesses.size()];

         for (int var4 = 0; var4 < var2.length; var4++) {
            var3[var2[var4]] = 1;
         }

         for (int var6 = 0; var6 < SGServerPanel.this.sgServerProcesses.size(); var6++) {
            if (var3[var6] == 1) {
               Compute var5 = (Compute)SGServerPanel.this.sgServerProcesses.elementAt(var6);

               try {
                  var5.endTask();
               } catch (Exception var8) {
                  if (SGServerPanel.this.debug) {
                     System.out.println("Error while stopping calculation on no." + var6);
                  }

                  if (SGServerPanel.this.debug) {
                     System.out.println(var8.toString());
                  }
               }
            }
         }
      }
   }

   class updateAction extends AbstractAction {
      updateAction() {
         super("updateTableFrame");
      }

      public void actionPerformed(ActionEvent var1) {
         boolean var6 = false;
         boolean var7 = false;
         Object var8 = null;
         StringBuffer var9 = new StringBuffer();
         Object var10 = null;
         SGServerPanel.this.table.repaint();
         Object var11 = null;
         if (SGServerPanel.this.sgServerProcesses.size() > 0) {
            Compute var12 = (Compute)SGServerPanel.this.sgServerProcesses.elementAt(SGServerPanel.this.currentBest);

            try {
               SGServerPanel.this.sgs[SGServerPanel.this.currentBest] = (StructureGeneratorStatus)var12.getTaskStatus();
               SGServerPanel.this.maxScore = (float)SGServerPanel.this.sgs[SGServerPanel.this.currentBest].bestEvaluation.maxScore;
               if (SGServerPanel.this.sgs[SGServerPanel.this.currentBest] != null) {
                  String var5 = ((StructureGeneratorStatus)var12.getTaskStatus()).bestStructure;
                  Atom[] var2 = ChemFileUtils.readMDLMolFile(new BufferedReader(new StringReader(var5)));
                  if (var2 == null) {
                     String var3 = "No structure available";
                     SGServerPanel.this.jmdPanel.setBorder(BorderFactory.createTitledBorder(SGServerPanel.this.border, var3));
                     SGServerPanel.this.jmdPanel.getJMDraw().atomSet = null;
                     SGServerPanel.this.jmdPanel.getJMDraw().draw();
                     SGServerPanel.this.jmdPanel.repaint();
                     SGServerPanel.this.reportPane.setText("");
                  } else {
                     String var18 = "Best structure of no." + (SGServerPanel.this.currentBest + 1);
                     SGServerPanel.this.jmdPanel.setJMDraw(new JMDraw(var2));
                     SGServerPanel.this.jmdPanel.setBorder(BorderFactory.createTitledBorder(SGServerPanel.this.border, var18));
                     SGServerPanel.this.jmdPanel.getJMDraw().draw();
                     SGServerPanel.this.jmdPanel.repaint();
                     var9.append("<html>\n");
                     var9.append("<b>Score: " + SGServerPanel.this.sgs[SGServerPanel.this.currentBest].bestEvaluation.score);
                     if (SGServerPanel.this.sgs[SGServerPanel.this.currentBest].bestEvaluation.maxScore > 0L) {
                        var9.append("/" + SGServerPanel.this.sgs[SGServerPanel.this.currentBest].bestEvaluation.maxScore);
                     }

                     var9.append("</b>");
                     var18 = "Evaluation of no. " + (SGServerPanel.this.currentBest + 1);
                     SGServerPanel.this.reportScrollPane.setBorder(BorderFactory.createTitledBorder(SGServerPanel.this.border, var18));
                     var9.append(((StructureGeneratorStatus)var12.getTaskStatus()).bestEvaluation.description + "</html>\n");
                     SGServerPanel.this.reportPane.setText(var9.toString());
                  }
               }

               var11 = SGServerPanel.this.sgs[SGServerPanel.this.currentBest].al;
               if (SGServerPanel.this.dataSource.getData() == null) {
                  SGServerPanel.this.dataSource.init(SGServerPanel.this.sgServerProcesses.size());
               }

               SGServerPanel.this.dataSource.addData((AnnealingLog)var11, SGServerPanel.this.currentBest);
               SGServerPanel.this.chart = JFreeChart.createXYChart(SGServerPanel.this.dataSource);
               SGServerPanel.this.chart.setTitle(new StandardTitle("", new Font("SansSerif", 0, 6)));
               String var20 = "Annealing data";
               SGServerPanel.this.chartPanel.setChart(SGServerPanel.this.chart);
               JFreeChart var13 = SGServerPanel.this.chartPanel.getChart();
               Plot var14 = var13.getPlot();
               Axis var15 = var14.getAxis(0);
               var15.setLabel("Iteration");
               Axis var16 = var14.getAxis(1);
               if (SGServerPanel.this.dataSource.getDisplayType() == SGServerPanel.this.dataSource.KT) {
                  var16.setLabel("kT");
               } else {
                  var16.setLabel("Score");
               }

               SGServerPanel.this.chartPanel.setBorder(BorderFactory.createTitledBorder(SGServerPanel.this.border, var20));
               SGServerPanel.this.chartPanel.repaint();
            } catch (Exception var17) {
               if (SGServerPanel.this.debug) {
                  var17.printStackTrace();
               }
            }

            SGServerPanel.this.currentBest++;
            if (SGServerPanel.this.currentBest == SGServerPanel.this.sgServerProcesses.size()) {
               SGServerPanel.this.currentBest = 0;
            }
         }

         SGServerPanel.this.scrollpane.repaint();
      }
   }
}
