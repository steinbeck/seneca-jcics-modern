package seneca.client;

import java.awt.Dimension;
import javax.swing.JTable;
import javax.swing.table.TableModel;

class NodeTableFrame$1 extends JTable {
   NodeTableFrame$1(NodeTableFrame var1, TableModel var2) {
      super(var2);
      this.this$0 = var1;
   }

   public Dimension getPreferredSize() {
      return new Dimension(this.this$0.scrollpane.getSize().width, (this.getRowCount() + 2) * this.getRowHeight());
   }
}
