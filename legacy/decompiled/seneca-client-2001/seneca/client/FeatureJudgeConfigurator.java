package seneca.client;

import java.awt.ItemSelectable;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Enumeration;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.border.Border;
import javax.swing.table.AbstractTableModel;
import seneca.core.SenecaDataset;
import seneca.judges.FeatureJudge;

public class FeatureJudgeConfigurator extends JudgeConfigurator {
   FeatureJudge featureJudge = null;
   JCheckBox hetHetCheckBox = new JCheckBox();

   FeatureJudgeConfigurator(SenecaDataset var1) {
      super(var1, (FeatureJudge)var1.getJudge("FeatureJudge"));
      this.featureJudge = (FeatureJudge)var1.getJudge("FeatureJudge");
   }

   protected Box contructCenterBox() {
      Box var1 = new Box(1);
      String var2 = "Ringsize Scores";
      Border var3 = BorderFactory.createEtchedBorder();
      new JPanel();
      super.table = new JTable(new FeatureJudgeConfigurator.RingScoresTableModel());
      super.scrollpane = new JScrollPane(super.table);
      super.scrollpane.setBorder(BorderFactory.createTitledBorder(var3, var2));
      var1.add(super.scrollpane);
      var2 = "Hetero-Hetero Atom Bonds forbidden";
      this.hetHetCheckBox = new JCheckBox(var2);
      System.out.println(this.hetHetCheckBox);
      this.hetHetCheckBox.addItemListener(new FeatureJudgeConfigurator.CheckBoxListener());
      var1.add(this.hetHetCheckBox);
      return var1;
   }

   public void autoconfigure() {
      this.featureJudge.addRingSizeScore(3, -500);
      this.featureJudge.addRingSizeScore(4, -300);
      this.featureJudge.setHetHetPenaltyEnabled(true);
   }

   protected void reactOnJudgeDataChange() {
      System.out.println("FeatureJudge data changed");
      super.table.revalidate();
      System.out.println(this.featureJudge.isHetHetPenaltyEnabled());
      if (this.featureJudge.isHetHetPenaltyEnabled() && !this.hetHetCheckBox.isSelected()) {
         System.out.println("doClick");
         this.hetHetCheckBox.doClick();
      }

      if (!this.featureJudge.isHetHetPenaltyEnabled() && this.hetHetCheckBox.isSelected()) {
         System.out.println("doClick");
         this.hetHetCheckBox.doClick();
      }
   }

   class CheckBoxListener implements ItemListener {
      public void itemStateChanged(ItemEvent var1) {
         System.out.println("ItemListener");
         ItemSelectable var2 = var1.getItemSelectable();
         if (var1.getStateChange() == 2) {
            FeatureJudgeConfigurator.this.featureJudge.setHetHetPenaltyEnabled(false);
         }

         if (var1.getStateChange() == 1) {
            FeatureJudgeConfigurator.this.featureJudge.setHetHetPenaltyEnabled(true);
         }
      }
   }

   class RingScoresTableModel extends AbstractTableModel {
      String[] names = new String[]{"Ring Size", "Score"};

      public int getColumnCount() {
         return this.names.length;
      }

      public int getRowCount() {
         return FeatureJudgeConfigurator.this.featureJudge == null ? 0 : FeatureJudgeConfigurator.this.featureJudge.ringSizeScores.size();
      }

      public Object getValueAt(int var1, int var2) {
         if (FeatureJudgeConfigurator.this.featureJudge == null) {
            return "";
         } else {
            Enumeration var3 = FeatureJudgeConfigurator.this.featureJudge.ringSizeScores.keys();
            int var4 = 0;

            do {
               Integer var5 = (Integer)var3.nextElement();
               if (var1 == var4) {
                  if (var2 == 0) {
                     return var5;
                  }

                  if (var2 == 1) {
                     return (Integer)FeatureJudgeConfigurator.this.featureJudge.ringSizeScores.get(var5);
                  }
               }

               var4++;
            } while (var3.hasMoreElements());

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
         return var2 > 1;
      }

      public void setValueAt(Object var1, int var2, int var3) {
      }
   }
}
