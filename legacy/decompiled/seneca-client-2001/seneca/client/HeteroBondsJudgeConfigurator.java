package seneca.client;

import java.awt.Dimension;
import javax.swing.Box;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import seneca.core.SenecaDataset;
import seneca.judges.HeteroBondsJudge;

public class HeteroBondsJudgeConfigurator extends JudgeConfigurator {
   HeteroBondsJudge hbJudge = null;

   HeteroBondsJudgeConfigurator(SenecaDataset var1) {
      super(var1, (HeteroBondsJudge)var1.getJudge("HeteroBondsJudge"));
      this.hbJudge = (HeteroBondsJudge)var1.getJudge("HeteroBondsJudge");
      this.hbJudge.getAttic().atomSet = var1.getSetOfNodes();
      this.hbJudge.init();
   }

   protected Box contructCenterBox() {
      Box var1 = new Box(0);
      JScrollPane var2 = new JScrollPane(new JTable(new HeteroBondsJudgeConfigurator.HeteroBondsTableModel()));
      var2.setBorder(this.getTitledBorder());
      var2.setPreferredSize(new Dimension(200, 400));
      var1.add(var2);
      return var1;
   }

   class HeteroBondsTableModel extends AbstractTableModel {
      String[] names = new String[]{"No.", "Symb.", "Shift", "hetero-attached?"};

      public int getColumnCount() {
         return this.names.length;
      }

      public int getRowCount() {
         return HeteroBondsJudgeConfigurator.super.sd.setOfNodes == null ? 0 : HeteroBondsJudgeConfigurator.super.sd.setOfNodes.length;
      }

      public Object getValueAt(int var1, int var2) {
         if (HeteroBondsJudgeConfigurator.this.hbJudge == null) {
            return "";
         } else if (HeteroBondsJudgeConfigurator.this.hbJudge.isHeteroBonded == null) {
            return "";
         } else if (HeteroBondsJudgeConfigurator.this.hbJudge.isHeteroBonded.length < 1) {
            return "";
         } else {
            switch (var2) {
               case 0:
                  return String.valueOf(var1);
               case 1:
                  return HeteroBondsJudgeConfigurator.super.sd.setOfNodes[var1].symbol;
               case 2:
                  if (HeteroBondsJudgeConfigurator.super.sd.setOfNodes[var1].getPhysicalProperty("13C chemical shift") != null) {
                     return String.valueOf(HeteroBondsJudgeConfigurator.super.sd.setOfNodes[var1].getPhysicalProperty("13C chemical shift"));
                  }

                  return "-";
               case 3:
                  return new Boolean(HeteroBondsJudgeConfigurator.this.hbJudge.isHeteroBonded[var1]);
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
         return var2 >= 2;
      }

      public void setValueAt(Object var1, int var2, int var3) {
         switch (var3) {
            case 3:
               HeteroBondsJudgeConfigurator.this.hbJudge.isHeteroBonded[var2] = (Boolean)var1;
            case 0:
            case 1:
            case 2:
            default:
               this.fireTableDataChanged();
         }
      }
   }
}
