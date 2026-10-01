package seneca.client;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.util.StringTokenizer;
import javax.swing.AbstractAction;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.border.BevelBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import seneca.core.NMRSignal;
import seneca.core.NMRSpectrum;
import seneca.core.SenecaDataset;

public class NMRSpectrumTablePanel extends JPanel implements ChangeListener {
   public boolean debug = false;
   public NMRSpectrum nmrSpectrum;
   JInternalFrame jif;
   protected TableModel tableModel;
   protected JTable table;
   protected JScrollPane scrollpane;
   protected JPanel southPanel;
   protected JLabel northLabel;
   protected JPanel centerPanel;
   protected JButton addSignalButton;
   protected JButton delSignalButton;
   protected JButton importPPButton;
   JComboBox ppSelector;
   SenecaDataset sd = null;

   NMRSpectrumTablePanel(SenecaDataset var1, NMRSpectrum var2) {
      this.sd = var1;
      if (this.debug) {
         System.out.println(var1 + ", " + var2);
      }

      this.nmrSpectrum = var2;
      if (this.debug) {
         System.out.println(var1 + ", " + this.nmrSpectrum);
      }

      this.setLayout(new BorderLayout());
      this.table = new JTable(new NMRSpectrumTablePanel.NMRSpectrumTableModel());
      this.table.setRowSelectionAllowed(true);
      JComboBox var3 = new JComboBox();
      var3.addItem("NONE");
      var3.addItem("POSITIVE");
      var3.addItem("NEGATIVE");
      var3.setAlignmentX(1.0F);
      TableColumn var4 = this.table.getColumnModel().getColumn(var2.dim + 2);
      var4.setCellEditor(new DefaultCellEditor(var3));
      this.setPreferredSize(new Dimension(500, 300));
      this.scrollpane = new JScrollPane(this.table);
      this.scrollpane.setBorder(new BevelBorder(1));
      this.southPanel = new JPanel();
      this.southPanel.setLayout(new FlowLayout());
      this.centerPanel = new JPanel();
      this.centerPanel.setLayout(new BorderLayout());
      this.addSignalButton = new JButton("Add Signal");
      this.addSignalButton.addActionListener(new NMRSpectrumTablePanel.AddSignalAction());
      this.delSignalButton = new JButton("Delete Signal");
      this.delSignalButton.addActionListener(new NMRSpectrumTablePanel.RemoveSignalAction());
      this.ppSelector = new JComboBox();
      this.ppSelector.addItem("Import peak picking");
      this.ppSelector.addItem("Paste WinNMR pp from clipboard");
      this.ppSelector.addItem("Import GIFA pp from file");
      this.ppSelector.addActionListener(new NMRSpectrumTablePanel$1(this));
      this.southPanel.add(this.addSignalButton);
      this.southPanel.add(this.delSignalButton);
      this.southPanel.add(this.ppSelector);
      String var5 = var2.size() + " Signals in ";
      var5 = var5 + var2.specType + ".";
      this.northLabel = new JLabel(var5);
      this.add("North", this.northLabel);
      this.add("South", this.southPanel);
      this.scrollpane.setPreferredSize(new Dimension(400, 400));
      this.centerPanel.add("Center", this.scrollpane);
      this.add("Center", this.centerPanel);
      var1.addChangeListener(this);
   }

   protected void setCellRendering() {
      JLabel var1 = new JLabel();
      this.table.setRowSelectionAllowed(false);

      for (int var3 = 0; var3 < this.table.getColumnCount(); var3++) {
         TableColumn var2 = this.table.getColumn(this.table.getColumnName(var3));
         var1.setVerticalAlignment(0);
         var1.setHorizontalAlignment(0);
      }
   }

   public void loadGIFApp() {
   }

   public void pasteWinNMRpp() {
      Clipboard var1 = this.getToolkit().getSystemClipboard();
      Transferable var2 = var1.getContents(this);

      String var3;
      try {
         var3 = (String)var2.getTransferData(DataFlavor.stringFlavor);
      } catch (Exception var5) {
         var3 = var2.toString();
      }

      if (var3.indexOf("Peak Picking results:") > 0) {
         this.importWinNMR1D(var3);
      } else {
         this.importWinNMR2D(var3);
      }

      this.table.revalidate();
   }

   void importWinNMR1D(String var1) {
      int var6 = 0;
      var1 = var1.substring(var1.indexOf("Peak Picking results:"));
      var1 = var1.substring(var1.indexOf("1"));
      StringTokenizer var7 = new StringTokenizer(var1);

      do {
         var7.nextToken();
         var7.nextToken();
         var7.nextToken();
         float var2 = Float.parseFloat(var7.nextToken().trim());
         var7.nextToken();
         float var3 = new Float(var7.nextToken().trim());
         float[] var4 = new float[]{var2};
         if (var3 > 0.0F) {
            var6 = NMRSignal.PHASE_POSITIVE;
         }

         if (var3 < 0.0F) {
            var6 = NMRSignal.PHASE_NEGATIVE;
         }

         NMRSignal var5 = new NMRSignal(this.nmrSpectrum.nucleus, var4, var3, var6);
         System.out.println("This is Spectrum " + this.nmrSpectrum.name);
         this.nmrSpectrum.addSignal(var5);
      } while (var7.hasMoreTokens());
   }

   void importWinNMR2D(String var1) {
      int var5 = 0;
      float[] var6 = new float[2];
      StringTokenizer var7 = new StringTokenizer(var1);

      do {
         var6[0] = new Float(var7.nextToken().trim());
         var6[1] = new Float(var7.nextToken().trim());
         float var3 = 1.0F;
         var5 = NMRSignal.PHASE_POSITIVE;
         NMRSignal var4 = new NMRSignal(this.nmrSpectrum.nucleus, var6, var3, var5);
         System.out.println(var4);
         System.out.println("This is Spectrum " + this.nmrSpectrum.name);
         this.nmrSpectrum.addSignal(var4);
      } while (var7.hasMoreTokens());
   }

   public void stateChanged(ChangeEvent var1) {
      if (this.nmrSpectrum != null) {
         String var2 = this.nmrSpectrum.size() + " signals in ";
         var2 = var2 + this.nmrSpectrum.specType + ".";
         this.northLabel.setText(var2);
      }
   }

   class AddSignalAction extends AbstractAction {
      AddSignalAction() {
         super("addSignal");
      }

      public void actionPerformed(ActionEvent var1) {
         NMRSpectrumTablePanel.this.nmrSpectrum.newSignal();
         NMRSpectrumTablePanel.this.table.revalidate();
         if (NMRSpectrumTablePanel.this.debug) {
            System.out.println("Attempt to add a signal");
         }
      }
   }

   class NMRSpectrumTableModel extends AbstractTableModel {
      public int getColumnCount() {
         return NMRSpectrumTablePanel.this.nmrSpectrum == null ? 0 : NMRSpectrumTablePanel.this.nmrSpectrum.dim + 3;
      }

      public int getRowCount() {
         return NMRSpectrumTablePanel.this.nmrSpectrum == null ? 0 : NMRSpectrumTablePanel.this.nmrSpectrum.size();
      }

      public Object getValueAt(int var1, int var2) {
         NMRSignal var3 = (NMRSignal)NMRSpectrumTablePanel.this.nmrSpectrum.getSignal(var1);
         if (var2 == 0) {
            return new Integer(var1 + 1);
         } else if (var2 >= 1 && var2 <= NMRSpectrumTablePanel.this.nmrSpectrum.dim) {
            return new Float(var3.shift[var2 - 1]);
         } else if (var2 == NMRSpectrumTablePanel.this.nmrSpectrum.dim + 1) {
            return new Float(var3.intensity);
         } else {
            return var2 == NMRSpectrumTablePanel.this.nmrSpectrum.dim + 2 ? NMRSignal.PHASENAMES[var3.phase] : "-";
         }
      }

      public String getColumnName(int var1) {
         if (var1 == 0) {
            return "No.";
         } else if (var1 >= 1 && var1 <= NMRSpectrumTablePanel.this.nmrSpectrum.dim) {
            return NMRSpectrumTablePanel.this.nmrSpectrum.nucleus[var1 - 1];
         } else if (var1 == NMRSpectrumTablePanel.this.nmrSpectrum.dim + 1) {
            return "Intensity";
         } else {
            return var1 == NMRSpectrumTablePanel.this.nmrSpectrum.dim + 2 ? "Phase" : "-";
         }
      }

      public Class getColumnClass(int var1) {
         return this.getValueAt(0, var1).getClass();
      }

      public boolean isCellEditable(int var1, int var2) {
         return true;
      }

      public void setValueAt(Object var1, int var2, int var3) {
         NMRSignal var4 = (NMRSignal)NMRSpectrumTablePanel.this.nmrSpectrum.getSignal(var2);
         if (var3 >= 1 && var3 <= NMRSpectrumTablePanel.this.nmrSpectrum.dim) {
            var4.shift[var3 - 1] = (Float)var1;
         }

         if (var3 == NMRSpectrumTablePanel.this.nmrSpectrum.dim + 1) {
            var4.intensity = (Float)var1;
         }

         if (var3 == NMRSpectrumTablePanel.this.nmrSpectrum.dim + 2) {
            if (((String)var1).equals("NONE")) {
               var4.phase = NMRSignal.PHASE_NONE;
            }

            if (((String)var1).equals("POSITIVE")) {
               var4.phase = NMRSignal.PHASE_POSITIVE;
            }

            if (((String)var1).equals("NEGATIVE")) {
               var4.phase = NMRSignal.PHASE_NEGATIVE;
            }
         }
      }
   }

   class RemoveSignalAction extends AbstractAction {
      RemoveSignalAction() {
         super("remSignal");
      }

      public void actionPerformed(ActionEvent var1) {
         System.out.println(NMRSpectrumTablePanel.this.table.getSelectedRow());
         NMRSpectrumTablePanel.this.nmrSpectrum.removeElementAt(NMRSpectrumTablePanel.this.table.getSelectedRow());
         NMRSpectrumTablePanel.this.table.repaint();
         if (NMRSpectrumTablePanel.this.debug) {
            System.out.println("Attempt to add a signal");
         }
      }
   }

   class closeAction extends AbstractAction {
      JInternalFrame jif;

      closeAction(JInternalFrame var2) {
         super("CloseTableFrame");
         this.jif = var2;
      }

      public void actionPerformed(ActionEvent var1) {
         this.jif.dispose();
      }
   }

   class toggleAction extends AbstractAction {
      toggleAction() {
         super("toggleDEPTPhases");
      }

      public void actionPerformed(ActionEvent var1) {
         int var2 = NMRSpectrumTablePanel.this.table.getSelectedRow();
         int var3 = NMRSpectrumTablePanel.this.table.getSelectedColumn();
         if (var3 > 1) {
            String var4 = NMRSpectrumTablePanel.this.table.getValueAt(var2, var3).toString().trim();
            if (var4.equals("none")) {
               NMRSpectrumTablePanel.this.table.setValueAt("positive", var2, var3);
            } else if (var4.equals("positive")) {
               NMRSpectrumTablePanel.this.table.setValueAt("negative", var2, var3);
            } else {
               NMRSpectrumTablePanel.this.table.setValueAt("none", var2, var3);
            }
         }
      }
   }
}
