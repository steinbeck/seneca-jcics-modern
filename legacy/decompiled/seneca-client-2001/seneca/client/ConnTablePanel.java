package seneca.client;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.border.BevelBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;
import seneca.structgen.StructureGenerator;

public class ConnTablePanel extends JPanel {
   protected TableModel tableModel;
   protected JTable table;
   protected JScrollPane scrollpane;
   protected JPanel southPanel;
   protected JPanel centerPanel;
   protected int maxRowHeight;
   protected int sumColWidth = 0;
   protected int sumRowHeight = 0;
   protected int[][] matrix;

   ConnTablePanel(int[][] var1) {
      this.tableModel = new ConnTablePanel.ConnTableModel();
      this.setLayout(new BorderLayout());
      this.southPanel = new JPanel();
      this.southPanel.setLayout(new FlowLayout());
      this.centerPanel = new JPanel();
      this.centerPanel.setLayout(new BorderLayout());
      this.add("South", this.southPanel);
      this.table = new JTable(this.tableModel);
      this.table.sizeColumnsToFit(4);
      this.table.setAutoResizeMode(0);
      this.scrollpane = new JScrollPane(this.table);
      this.scrollpane.setBorder(new BevelBorder(1));
      this.centerPanel.add("Center", this.scrollpane);
      this.add("Center", this.centerPanel);
      this.setVisible(true);
      this.scrollpane.invalidate();
      this.scrollpane.validate();
   }

   class ConnTableModel extends AbstractTableModel {
      public int getColumnCount() {
         return ConnTablePanel.this.matrix.length;
      }

      public String getColumnName(int var1) {
         return new Integer(var1 + 1).toString();
      }

      public int getRowCount() {
         return ConnTablePanel.this.matrix.length;
      }

      public Object getValueAt(int var1, int var2) {
         return new Integer(ConnTablePanel.this.matrix[var1][var2]);
      }

      public Class getColumnClass(int var1) {
         return this.getValueAt(0, var1).getClass();
      }

      public boolean isCellEditable(int var1, int var2) {
         return false;
      }

      public void setValueAt(Object var1, int var2, int var3) {
      }
   }

   class stopAction extends AbstractAction {
      StructureGenerator dg;

      stopAction(StructureGenerator var2) {
         super("Stop");
         this.dg = var2;
      }

      public void actionPerformed(ActionEvent var1) {
      }
   }
}
