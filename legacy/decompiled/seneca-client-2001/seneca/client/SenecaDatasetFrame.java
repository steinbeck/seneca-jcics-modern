package seneca.client;

import compchem.DataStructureTools;
import compchem.StandardIsotopes;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.OutputStreamWriter;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JDesktopPane;
import javax.swing.JFileChooser;
import javax.swing.JInternalFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.EventListenerList;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import javax.swing.table.AbstractTableModel;
import seneca.core.CMLGenerator;
import seneca.core.SenecaDataset;

public class SenecaDatasetFrame extends JInternalFrame {
   protected transient EventListenerList changeListeners = new EventListenerList();
   boolean DEBUG = true;
   boolean isClosed = false;
   SenecaDataset sd;
   StandardIsotopes si;
   private String MF = "";
   JDesktopPane jdp;

   public SenecaDatasetFrame(SenecaDataset var1) {
      super(var1.getName(), true, true, true, true);
      this.sd = var1;
      this.si = new StandardIsotopes();
      this.setDefaultCloseOperation(0);
      this.addInternalFrameListener(new SenecaDatasetFrame.WindowManager());
      this.getContentPane().setLayout(new BorderLayout());
      JPanel var2 = new JPanel();
      JButton var3 = new JButton("Close");
      var3.addActionListener(new SenecaDatasetFrame.CloseAction());
      var2.add(var3);
      this.setPreferredSize(new Dimension(500, 300));
      JTabbedPane var4 = new JTabbedPane();
      JTable var5 = new JTable(new SenecaDatasetFrame.GeneralTableModel("General"));
      JPanel var6 = new JPanel();
      var6.setLayout(new BorderLayout());
      var6.add(var5.getTableHeader(), "North");
      var6.add(var5, "Center");
      var4.addTab("General Settings", null, var6, "General Settings");
      JPanel var7 = new JPanel();
      var7.setLayout(new BorderLayout());
      JTabbedPane var8 = new JTabbedPane();
      var8.addTab("1D Carbon", null, new NMRSpectrumTablePanel(var1, var1.carbon1D), "1D Carbon");
      var8.addTab("DEPT-90", null, new NMRSpectrumTablePanel(var1, var1.dept90), "DEPT-90");
      var8.addTab("DEPT-135", null, new NMRSpectrumTablePanel(var1, var1.dept135), "DEPT-135");
      var8.addTab("H,C-HetCor", null, new NMRSpectrumTablePanel(var1, var1.hetcor), "H,C-HetCor");
      var8.addTab("H,C-lr-HetCor", null, new NMRSpectrumTablePanel(var1, var1.hetcorlr), "H,C-lr-HetCor");
      var8.addTab("H,H-COSY", null, new NMRSpectrumTablePanel(var1, var1.hhcosy), "H,H-COSY");
      var4.addTab("NMR Spectra", null, var8, "NMR Spectra");
      var4.addTab("Atom Properties", null, new AtomPropertyPanel(var1), "Atom Properties");
      JPanel var9 = new JPanel();
      var9.setLayout(new BorderLayout());
      var4.addTab("Annealing Schedule", null, new AnnealingScheduleConfigurator(var1), "Annealing Schedule");
      var4.addTab("Judges", null, this.getJudgeTabbedPane(), "Judges");
      var4.addTab("Serverlist", null, new ComputeServerConfigurator(var1), "Serverlist");
      var4.addTab("Structure Generation", null, new SGServerPanel(var1), "Structure Generation");
      JTextPane var10 = Seneca.a4.getTextPane();
      var10.setBackground(Color.white);
      var4.addTab("Logbook", null, new JScrollPane(var10), "LogBook");
      this.getContentPane().add("Center", var4);
      this.getContentPane().add("South", var2);
      this.setVisible(true);
   }

   private JTabbedPane getJudgeTabbedPane() {
      JTabbedPane var1 = new JTabbedPane();
      var1.addTab("H,H-COSY", null, new HHCOSYJudgeConfigurator(this.sd), "H,H-COSY");
      var1.addTab("C,H-long-range Corr.", null, new HMBCJudgeConfigurator(this.sd), "C,H-long-range Corr.");
      var1.addTab("Carbon Shifts", null, new HOSECodeJudgeConfigurator(this.sd), "Shifts");
      var1.addTab("Symmetric Atoms Classes", null, new SymmetryJudgeConfigurator(this.sd), "Symmetric Atoms Classes");
      var1.addTab("Features", null, new FeatureJudgeConfigurator(this.sd), "Features");
      var1.addTab("Hybridization", null, new HybridizationJudgeConfigurator(this.sd), "Hybridization");
      var1.addTab("Heterobond Attachments", null, new HeteroBondsJudgeConfigurator(this.sd), "Heterobond Attachments");
      return var1;
   }

   protected void tryExit() {
      OutputStreamWriter var1 = null;
      if (!this.isClosed) {
         JFileChooser var2 = new JFileChooser();
         String var3 = "Close " + this.sd.getName() + "?";
         String var4 = "You are about to close this dataset.\n";
         var4 = var4 + "Do you want to save your changes?";
         int var5 = JOptionPane.showConfirmDialog(this, var4, var3, 1);
         if (var5 == 1) {
            try {
               this.isClosed = true;
               this.sd.destroy();
               this.dispose();
            } catch (Exception var10) {
               System.err.println("There's a problem closing this internal frame.");
            }
         } else if (var5 == 0) {
            int var6 = var2.showSaveDialog(this);
            if (var6 == 0) {
               File var7 = var2.getSelectedFile();
               System.out.println("Saving: " + var7.getName() + "." + "\n");

               try {
                  var1 = new FileWriter(var7);
                  CMLGenerator var8 = new CMLGenerator();
                  var8.setVerbose(true);
                  var1.write(var8.convert(this.sd));
                  var1.flush();
                  var1.close();
                  this.isClosed = true;
                  System.out.println("File saved successfully");
                  this.sd.destroy();
                  this.dispose();
               } catch (Exception var9) {
                  System.err.println("There's a problem closing this internal frame.\n");
                  System.err.println(var9.toString());
               }
            } else {
               System.out.println("Save command cancelled by user.\n");
            }
         }
      }
   }

   public void addChangeListener(ChangeListener var1) {
      this.changeListeners
         .add(
            class$javax$swing$event$ChangeListener == null
               ? (class$javax$swing$event$ChangeListener = class$("javax.swing.event.ChangeListener"))
               : class$javax$swing$event$ChangeListener,
            var1
         );
      var1.stateChanged(new ChangeEvent(this));
   }

   public void removeChangeListener(ChangeListener var1) {
      this.changeListeners
         .remove(
            class$javax$swing$event$ChangeListener == null
               ? (class$javax$swing$event$ChangeListener = class$("javax.swing.event.ChangeListener"))
               : class$javax$swing$event$ChangeListener,
            var1
         );
   }

   protected void fireChange() {
      ChangeEvent var1 = new ChangeEvent(this);
      Object[] var2 = this.changeListeners.getListenerList();

      for (int var3 = var2.length - 2; var3 >= 0; var3 -= 2) {
         if (var2[var3]
            == (
               class$javax$swing$event$ChangeListener == null
                  ? (class$javax$swing$event$ChangeListener = class$("javax.swing.event.ChangeListener"))
                  : class$javax$swing$event$ChangeListener
            )) {
            ChangeListener var4 = (ChangeListener)var2[var3 + 1];
            var4.stateChanged(var1);
         }
      }
   }

   class CloseAction extends AbstractAction {
      CloseAction() {
         super("close");
      }

      public void actionPerformed(ActionEvent var1) {
         SenecaDatasetFrame.this.tryExit();
      }
   }

   class GeneralTableModel extends AbstractTableModel {
      String[] names = new String[]{"Parameter", "Setting"};
      String settingsType;

      GeneralTableModel(String var2) {
         this.settingsType = var2;
      }

      public int getColumnCount() {
         return this.names.length;
      }

      public int getRowCount() {
         return 4;
      }

      public Object getValueAt(int var1, int var2) {
         switch (var1) {
            case 0:
               if (var2 == 0) {
                  return "Filename";
               }

               return SenecaDatasetFrame.this.sd.getName();
            case 1:
               if (var2 == 0) {
                  return "Description";
               }

               return SenecaDatasetFrame.this.sd.getDescription();
            case 2:
               if (var2 == 0) {
                  return "Molecular Formula";
               }

               return SenecaDatasetFrame.this.sd.getMolecularFormula();
            case 3:
               if (var2 == 0) {
                  return "Molecular Mass";
               } else {
                  if (SenecaDatasetFrame.this.sd.getMolecularFormula() != null && SenecaDatasetFrame.this.sd.getMolecularMass() == 0.0F) {
                     return new Float(DataStructureTools.getMass(SenecaDatasetFrame.this.sd.getMolecularFormula()));
                  }

                  return new Double(SenecaDatasetFrame.this.sd.getMolecularMass());
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
         switch (var2) {
            case 0:
               if (var3 == 1) {
                  SenecaDatasetFrame.this.sd.setName((String)var1);
               }

               SenecaDatasetFrame.this.setTitle((String)var1);
               break;
            case 1:
               if (var3 == 1) {
                  SenecaDatasetFrame.this.sd.setDescription((String)var1);
               }
               break;
            case 2:
               if (var3 == 1) {
                  SenecaDatasetFrame.this.sd.setMolecularFormula((String)var1);
                  this.fireTableCellUpdated(var2, var3);
               }
               break;
            case 3:
               if (var3 == 1) {
                  SenecaDatasetFrame.this.sd.setMolecularMass(new Float((String)var1));
               }
         }
      }
   }

   class WindowManager extends InternalFrameAdapter {
      public void internalFrameClosing(InternalFrameEvent var1) {
         System.out.println("JInternalFrame closing...");
         SenecaDatasetFrame.this.tryExit();
      }
   }
}
