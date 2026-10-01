package seneca.client;

import compchem.Atom;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.border.BevelBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;

public class NodeTableFrame extends JDialog {
   protected Atom[] setOfNodes;
   protected TableModel tableModel;
   protected JTable table;
   protected JScrollPane scrollpane;
   protected JPanel southPanel;
   protected JPanel centerPanel;
   protected JButton closeButton;

   public NodeTableFrame(Atom[] var1, JFrame var2) {
      super((Frame)var2, "Atom Properties");
      this.setOfNodes = var1;
      this.table = new NodeTableFrame$1(this, new NodeTableFrame.NodeTableModel());
      FontMetrics var3 = this.getFontMetrics(this.table.getFont());

      for (int var4 = 0; var4 < this.table.getColumnCount(); var4++) {
         TableColumn var5 = this.table.getColumn(this.table.getColumnName(var4));
         var5.setWidth((int)(var3.stringWidth(this.table.getColumnName(var4)) * 1.2));
         DefaultTableCellRenderer var6 = new DefaultTableCellRenderer();
         var6.setBackground(Color.yellow);
      }

      this.setCellRendering();
      this.scrollpane = new JScrollPane(this.table);
      this.scrollpane.setBorder(new BevelBorder(1));
      this.getContentPane().setLayout(new BorderLayout());
      this.southPanel = new JPanel();
      this.southPanel.setLayout(new FlowLayout());
      this.centerPanel = new JPanel();
      this.centerPanel.setLayout(new BorderLayout());
      this.closeButton = new JButton("Close");
      this.closeButton.addActionListener(new NodeTableFrame.closeAction(this));
      this.southPanel.add(this.closeButton);
      this.getContentPane().add("South", this.southPanel);
      this.scrollpane.setPreferredSize(new Dimension(400, 400));
      this.centerPanel.add("Center", this.scrollpane);
      this.getContentPane().add("Center", this.centerPanel);
      this.pack();
      this.setVisible(true);
      this.scrollpane.repaint();
   }

   protected void setCellRendering() {
      JLabel var1 = new JLabel();
      this.table.setRowSelectionAllowed(false);
      this.table.setColumnSelectionAllowed(false);

      for (int var3 = 0; var3 < this.table.getColumnCount(); var3++) {
         TableColumn var2 = this.table.getColumn(this.table.getColumnName(var3));
         var1.setVerticalAlignment(0);
         var1.setHorizontalAlignment(0);
         DefaultTableCellRenderer var4 = new DefaultTableCellRenderer();
         var4.setBackground(Color.lightGray);
         var2.setCellRenderer(var4);
      }
   }

   class NodeTableModel extends AbstractTableModel {
      String[] names = new String[]{"No.", "Symb.", "Max.Bond", "Max.Bond.Sum", "HCount"};

      public int getColumnCount() {
         return this.names.length;
      }

      public int getRowCount() {
         return NodeTableFrame.this.setOfNodes.length;
      }

      public Object getValueAt(int var1, int var2) {
         switch (var2) {
            case 0:
               return String.valueOf(var1);
            case 1:
               return NodeTableFrame.this.setOfNodes[var1].symbol;
            case 2:
               return String.valueOf(NodeTableFrame.this.setOfNodes[var1].maxBondOrder);
            case 3:
               return String.valueOf(NodeTableFrame.this.setOfNodes[var1].maxBondOrderSum);
            case 4:
               return String.valueOf(NodeTableFrame.this.setOfNodes[var1].HCount);
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
         switch (var3) {
            case 0:
            default:
               break;
            case 1:
               NodeTableFrame.this.setOfNodes[var2].symbol = (String)var1;
               break;
            case 2:
               NodeTableFrame.this.setOfNodes[var2].maxBondOrder = new Integer((String)var1);
               break;
            case 3:
               NodeTableFrame.this.setOfNodes[var2].maxBondOrderSum = new Integer((String)var1);
               break;
            case 4:
               NodeTableFrame.this.setOfNodes[var2].HCount = new Integer((String)var1);
         }
      }
   }

   class closeAction extends AbstractAction {
      JDialog dialog;

      closeAction(JDialog var2) {
         super("CloseTableFrame");
         this.dialog = var2;
      }

      public void actionPerformed(ActionEvent var1) {
         this.dialog.dispose();
      }
   }
}
