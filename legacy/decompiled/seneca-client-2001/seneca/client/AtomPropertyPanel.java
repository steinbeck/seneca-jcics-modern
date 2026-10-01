package seneca.client;

import compchem.Atom;
import compchem.MFAnalyser;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.util.Vector;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.AbstractTableModel;
import seneca.core.NMRSignal;
import seneca.core.SenecaDataset;

public class AtomPropertyPanel extends JPanel implements ChangeListener {
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
   protected String[] DEPTPhaseDescriptors = new String[]{"none", "positive", "negative"};

   AtomPropertyPanel(SenecaDataset var1) {
      this.sd = var1;
      this.carbonShifts = new Vector();
      var1.addChangeListener(this);
      this.setLayout(new BorderLayout());
      this.etchedBorder = BorderFactory.createEtchedBorder();
      this.centerBox = new Box(0);
      this.centerCenterBox = new Box(1);
      this.topCenterCenterBox = new Box(0);
      this.southBox = new Box(0);
      this.setOfNodesTable = new JTable(new AtomPropertyPanel.SetOfNodesTableModel());
      this.shiftTable = new JTable(new AtomPropertyPanel.Carbon1DTableModel());
      this.scrollpane = new JScrollPane(this.setOfNodesTable);
      this.scrollpane.setBorder(BorderFactory.createTitledBorder(this.etchedBorder, "Atom Properties"));
      this.scrollpane.setPreferredSize(new Dimension(200, 400));
      this.lsp = new JScrollPane(this.shiftTable);
      this.lsp.setBorder(BorderFactory.createTitledBorder(this.etchedBorder, "Carbon Shifts"));
      this.lsp.setPreferredSize(new Dimension(200, 400));
      this.centerBox.add(this.scrollpane);
      this.centerBox.add(this.lsp);
      this.autoButton = new JButton("Autoassignment");
      this.autoButton.addActionListener(new AtomPropertyPanel.AutoAssignAction());
      this.messagelabel = new JLabel();
      this.messagelabel.setBorder(BorderFactory.createEmptyBorder(0, 30, 0, 0));
      this.southBox.add(this.autoButton);
      this.southBox.add(this.messagelabel);
      this.add("Center", this.centerBox);
      this.add("South", this.southBox);
   }

   private void loadCarbonShifts() {
      this.carbonShifts.clear();

      for (int var1 = 0; var1 < this.sd.carbon1D.size(); var1++) {
         this.carbonShifts.addElement(new Float(((NMRSignal)this.sd.carbon1D.getSignal(var1)).shift[0]));
      }
   }

   public void updateMessageLabel() {
      int var1 = new MFAnalyser(this.sd.getSetOfNodes()).getAtomCount("H");
      int var2 = new MFAnalyser(this.sd.getMolecularFormula()).getAtomCount("H");
      if (var1 != var2) {
         this.messagelabel.setForeground(Color.red);
      } else {
         for (int var3 = 0; var3 < this.sd.getSetOfNodes().length; var3++) {
            if (this.sd.setOfNodes[var3].HCount < 0) {
               this.sd.setOfNodes[var3].HCount = 0;
            }
         }

         this.messagelabel.setForeground(new Color(Color.green.getRGB()).darker());
         this.messagelabel.setForeground(new Color(Color.green.getRGB()).darker());
      }

      String var4 = "HCount in molecular formula: " + var2;
      var4 = var4 + " - Sum of HCounts in this table: " + var1;
      this.messagelabel.setText(var4);
      this.setOfNodesTable.repaint();
      this.shiftTable.repaint();
   }

   public void stateChanged(ChangeEvent var1) {
      SenecaDataset var2 = (SenecaDataset)var1.getSource();
      System.out.println("State changed in SenecaDataset");
   }

   class AutoAssignAction extends AbstractAction {
      AutoAssignAction() {
         super("AutoAssign");
      }

      public void actionPerformed(ActionEvent var1) {
         new CarbonShiftAssigner(AtomPropertyPanel.this.sd).assign();
         AtomPropertyPanel.this.setOfNodesTable.repaint();
      }
   }

   class Carbon1DTableModel extends AbstractTableModel {
      String[] names = new String[]{"[ppm]", "Int.", "DEPT-90", "Int.", "DEPT-135", "Int."};

      public int getColumnCount() {
         return this.names.length;
      }

      public int getRowCount() {
         return AtomPropertyPanel.this.sd.carbon1D == null ? 0 : AtomPropertyPanel.this.sd.carbon1D.size();
      }

      public Object getValueAt(int var1, int var2) {
         switch (var2) {
            case 0:
               NMRSignal var8 = (NMRSignal)AtomPropertyPanel.this.sd.carbon1D.getSignal(var1);
               if (var8 == null) {
                  return "-";
               }

               return String.valueOf(var8.shift[NMRSignal.DIM_ONE - 1]);
            case 1:
               NMRSignal var7 = (NMRSignal)AtomPropertyPanel.this.sd.carbon1D.getSignal(var1);
               if (var7 == null) {
                  return "";
               }

               return String.valueOf(var7.intensity * var7.normalizeFactor);
            case 2:
               if (var1 < AtomPropertyPanel.this.sd.dept90.size()) {
                  NMRSignal var6 = (NMRSignal)AtomPropertyPanel.this.sd.dept90.getSignal(var1);
                  if (var6 == null) {
                     return "-";
                  }

                  return String.valueOf(var6.shift[NMRSignal.DIM_ONE - 1]);
               }

               return "-";
            case 3:
               if (var1 < AtomPropertyPanel.this.sd.dept90.size()) {
                  NMRSignal var5 = (NMRSignal)AtomPropertyPanel.this.sd.dept90.getSignal(var1);
                  if (var5 == null) {
                     return "";
                  }

                  return String.valueOf(var5.intensity * var5.normalizeFactor);
               }

               return "-";
            case 4:
               if (var1 < AtomPropertyPanel.this.sd.dept135.size()) {
                  NMRSignal var4 = (NMRSignal)AtomPropertyPanel.this.sd.dept135.getSignal(var1);
                  if (var4 == null) {
                     return "-";
                  }

                  return String.valueOf(var4.shift[NMRSignal.DIM_ONE - 1]);
               }

               return "-";
            case 5:
               if (var1 < AtomPropertyPanel.this.sd.dept135.size()) {
                  NMRSignal var3 = (NMRSignal)AtomPropertyPanel.this.sd.dept135.getSignal(var1);
                  if (var3 == null) {
                     return "";
                  }

                  return String.valueOf(var3.intensity * var3.normalizeFactor);
               }

               return "-";
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
         return true;
      }

      public void setValueAt(Object var1, int var2, int var3) {
      }
   }

   class SetOfNodesTableModel extends AbstractTableModel {
      String[] names = new String[]{"No.", "Symbol", "Hydrogens", "Assigned 13C Shift"};

      public int getColumnCount() {
         return this.names.length;
      }

      public int getRowCount() {
         return AtomPropertyPanel.this.sd.getSetOfNodes() == null ? 0 : AtomPropertyPanel.this.sd.getSetOfNodes().length;
      }

      public Object getValueAt(int var1, int var2) {
         Atom[] var3 = AtomPropertyPanel.this.sd.getSetOfNodes();
         if (var3 == null) {
            return "";
         } else {
            switch (var2) {
               case 0:
                  return String.valueOf(var1 + 1);
               case 1:
                  return String.valueOf(var3[var1].symbol);
               case 2:
                  AtomPropertyPanel.this.updateMessageLabel();
                  if (var3[var1].HCount < 0) {
                     return "undefined";
                  }

                  return String.valueOf(var3[var1].HCount);
               case 3:
                  if (var3[var1].getPhysicalProperty("13C chemical shift") != null) {
                     return String.valueOf(var3[var1].getPhysicalProperty("13C chemical shift"));
                  }
               default:
                  return "-";
            }
         }
      }

      public String getColumnName(int var1) {
         return this.names[var1];
      }

      public Class getColumnClass(int var1) {
         return this.getValueAt(0, var1).getClass();
      }

      public boolean isCellEditable(int var1, int var2) {
         return var2 > 1;
      }

      public void setValueAt(Object var1, int var2, int var3) {
         if (var3 == 2) {
            try {
               int var4 = Integer.parseInt((String)var1);
               if (var4 >= 0 && var4 <= 4) {
                  AtomPropertyPanel.this.sd.setOfNodes[var2].HCount = var4;
               }
            } catch (Exception var5) {
            }
         }

         if (var3 == 3) {
            AtomPropertyPanel.this.sd.setOfNodes[var2].setPhysicalProperty("13C chemical shift", new Float((String)var1));
         }

         System.out.println("now");
         AtomPropertyPanel.this.updateMessageLabel();
      }
   }
}
