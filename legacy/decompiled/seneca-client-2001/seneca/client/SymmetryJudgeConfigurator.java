package seneca.client;

import java.awt.Dimension;
import javax.swing.Box;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import seneca.core.SenecaDataset;
import seneca.judges.SymmetryJudge;

public class SymmetryJudgeConfigurator extends JudgeConfigurator {
   SymmetryJudge symJudge = null;

   SymmetryJudgeConfigurator(SenecaDataset var1) {
      super(var1, (SymmetryJudge)var1.getJudge("SymmetryJudge"));
      this.symJudge = (SymmetryJudge)var1.getJudge("SymmetryJudge");
   }

   protected Box contructCenterBox() {
      Box var1 = new Box(0);
      super.table = new JTable(new SymmetryJudgeConfigurator.SymmetryTableModel());
      super.scrollpane = new JScrollPane(super.table);
      super.scrollpane.setBorder(this.getTitledBorder());
      super.scrollpane.setPreferredSize(new Dimension(200, 400));
      var1.add(super.scrollpane);
      return var1;
   }

   public void autoconfigure() {
      if (super.sd.setOfNodes != null) {
         this.symJudge.clearSymmetryClasses();
         System.out.println("Autoconfiguring " + super.judge.name);
         float var1 = 0.0F;
         float var2 = 0.0F;
         boolean var3 = false;
         boolean[] var4 = new boolean[super.sd.setOfNodes.length];

         for (int var5 = 0; var5 < super.sd.setOfNodes.length; var5++) {
            if (super.sd.setOfNodes[var5].getPhysicalProperty("13C chemical shift") != null) {
               var1 = (Float)super.sd.setOfNodes[var5].getPhysicalProperty("13C chemical shift");
            }

            for (int var6 = var5 + 1; var6 < super.sd.setOfNodes.length; var6++) {
               if (super.sd.setOfNodes[var6].getPhysicalProperty("13C chemical shift") != null) {
                  var2 = (Float)super.sd.setOfNodes[var6].getPhysicalProperty("13C chemical shift");
                  if (var1 == var2) {
                     if (!var3) {
                        var3 = true;
                        var4 = new boolean[super.sd.setOfNodes.length];

                        for (int var7 = 0; var7 < var4.length; var7++) {
                           var4[var7] = false;
                        }

                        var4[var5] = true;
                     }

                     var4[var6] = true;
                  }
               }
            }

            if (var3) {
               this.symJudge.addSymmetryClass(var4);
            }

            var3 = false;
         }

         super.table.setModel(new SymmetryJudgeConfigurator.SymmetryTableModel());
         super.scrollpane.revalidate();
      }
   }

   class SymmetryTableModel extends AbstractTableModel {
      public int getColumnCount() {
         return SymmetryJudgeConfigurator.super.sd.setOfNodes != null && SymmetryJudgeConfigurator.this.symJudge != null
            ? SymmetryJudgeConfigurator.super.sd.setOfNodes.length + 3
            : 0;
      }

      public int getRowCount() {
         return SymmetryJudgeConfigurator.super.sd.setOfNodes != null && SymmetryJudgeConfigurator.this.symJudge != null
            ? SymmetryJudgeConfigurator.this.symJudge.getSymmetryClassCount()
            : 0;
      }

      public Object getValueAt(int var1, int var2) {
         switch (var2) {
            case 0:
               return String.valueOf(var1 + 1);
            case 1:
               return SymmetryJudgeConfigurator.super.sd.setOfNodes[var1].symbol;
            case 2:
               return String.valueOf(
                  SymmetryJudgeConfigurator.super.sd.setOfNodes[SymmetryJudgeConfigurator.this.symJudge.getClassMember(var1, 1)]
                     .getPhysicalProperty("13C chemical shift")
               );
            default:
               boolean[] var4 = SymmetryJudgeConfigurator.this.symJudge.getSymmetryClass(var1);
               return new Boolean(var4[var2 - 3]);
         }
      }

      public String getColumnName(int var1) {
         switch (var1) {
            case 0:
               return "No.";
            case 1:
               return "Symb.";
            case 2:
               return "Shift";
            default:
               return new String(SymmetryJudgeConfigurator.super.sd.setOfNodes[var1 - 3].symbol + (var1 - 2));
         }
      }

      public Class getColumnClass(int var1) {
         return this.getValueAt(0, var1).getClass();
      }

      public boolean isCellEditable(int var1, int var2) {
         return var2 >= 4;
      }

      public void setValueAt(Object var1, int var2, int var3) {
         switch (var3) {
            case 0:
            case 1:
            case 2:
            default:
               this.fireTableDataChanged();
         }
      }
   }
}
