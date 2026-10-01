package seneca.client;

import java.awt.Dimension;
import javax.swing.Box;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextPane;
import javax.swing.table.AbstractTableModel;
import seneca.core.SenecaDataset;
import seneca.judges.TwoDSpectrumJudge;

abstract class TwoDSpectrumJudgeConfigurator extends JudgeConfigurator {
   protected transient SpectrumAssigner sa;
   protected TwoDSpectrumJudge twodsse;

   TwoDSpectrumJudgeConfigurator(SenecaDataset var1, TwoDSpectrumJudge var2, SpectrumAssigner var3, String var4) {
      super(var1, var2);
      this.twodsse = var2;
      this.sa = var3;
   }

   protected Box contructCenterBox() {
      super.table = new JTable(new TwoDSpectrumJudgeConfigurator.TwoDSpectrumAssignmentTableModel());
      Box var1 = new Box(0);
      super.scrollpane = new JScrollPane(super.table);
      super.scrollpane.setBorder(this.getTitledBorder());
      super.scrollpane.setPreferredSize(new Dimension(200, 400));
      var1.add(super.scrollpane);
      return var1;
   }

   protected void report() {
      JFrame var1 = new JFrame();
      var1.setTitle("Judge Configuration Report");
      JTextPane var2 = new JTextPane();
      var2.setPreferredSize(new Dimension(400, 400));
      var2.setText(super.judge.toString());
      var1.getContentPane().add(var2);
      var1.pack();
      var1.setVisible(true);
   }

   public void autoconfigure() {
      this.sa.setSenecaDataset(super.sd);
      if (!this.sa.assign()) {
         System.out.println("Assignment failed.");
         this.twodsse.setEnabled(false);
      } else {
         System.out.println("Assignment successful.");
         System.out.println(this.twodsse.assignment);
         this.twodsse.setEnabled(true);
         super.table.setModel(new TwoDSpectrumJudgeConfigurator.TwoDSpectrumAssignmentTableModel());
         super.scrollpane.revalidate();
      }
   }

   class TwoDSpectrumAssignmentTableModel extends AbstractTableModel {
      public int getColumnCount() {
         return TwoDSpectrumJudgeConfigurator.this.twodsse != null && TwoDSpectrumJudgeConfigurator.this.twodsse.assignment != null
            ? TwoDSpectrumJudgeConfigurator.this.twodsse.assignment.length + 1
            : 0;
      }

      public String getColumnName(int var1) {
         return var1 == 0 ? "No." : new Integer(var1).toString();
      }

      public Class getColumnClass(int var1) {
         return this.getValueAt(0, var1).getClass();
      }

      public boolean isCellEditable(int var1, int var2) {
         return var2 != 0;
      }

      public int getRowCount() {
         int var1 = 0;
         if (TwoDSpectrumJudgeConfigurator.this.twodsse != null && TwoDSpectrumJudgeConfigurator.this.twodsse.assignment != null) {
            for (int var2 = 0; var2 < TwoDSpectrumJudgeConfigurator.this.twodsse.assignment.length; var2++) {
               for (int var3 = 0; var3 < TwoDSpectrumJudgeConfigurator.this.twodsse.assignment.length; var3++) {
                  if (TwoDSpectrumJudgeConfigurator.this.twodsse.assignment[var2][var3][var3]) {
                     var1++;
                  }
               }
            }

            return var1;
         } else {
            return 0;
         }
      }

      public Object getValueAt(int var1, int var2) {
         int var3 = 0;

         for (int var4 = 0; var4 < TwoDSpectrumJudgeConfigurator.this.twodsse.assignment.length; var4++) {
            for (int var5 = 0; var5 < TwoDSpectrumJudgeConfigurator.this.twodsse.assignment.length; var5++) {
               if (TwoDSpectrumJudgeConfigurator.this.twodsse.assignment[var4][var5][var5]) {
                  var3++;
               }

               if (var3 - 1 == var1) {
                  if (var2 == 0) {
                     return new Integer(var4 + 1);
                  }

                  if (TwoDSpectrumJudgeConfigurator.this.twodsse.assignment[var4][var5][var2 - 1]) {
                     return new Boolean(true);
                  }

                  return new Boolean(false);
               }
            }
         }

         return null;
      }

      public void setValueAt(Object var1, int var2, int var3) {
      }
   }
}
