package seneca.client;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.border.Border;
import seneca.core.SenecaDataset;
import seneca.core.SpecMLGenerator;
import seneca.judges.Judge;
import seneca.judges.JudgeListener;

public abstract class JudgeConfigurator extends JPanel implements JudgeListener {
   protected SenecaDataset sd;
   protected Judge judge;
   protected JCheckBox enabled;
   protected JTable table;
   protected JScrollPane scrollpane;

   JudgeConfigurator() {
   }

   JudgeConfigurator(SenecaDataset var1, Judge var2) {
      this.sd = var1;
      this.judge = var2;
      System.out.println(this + " - " + var2);
      var2.setJudgeListener(this);
      this.setLayout(new BorderLayout());
      this.add("Center", this.contructCenterBox());
      this.add("South", this.contructSouthBox());
   }

   protected abstract Box contructCenterBox();

   protected Border getTitledBorder() {
      Border var1 = BorderFactory.createEtchedBorder();
      return BorderFactory.createTitledBorder(var1, this.judge.name);
   }

   protected Box contructSouthBox() {
      Box var1 = new Box(0);
      JButton var2 = new JButton("Autoconfigure Judge");
      var2.setToolTipText("Autoconfigures " + this.judge.name);
      var2.addActionListener(new JudgeConfigurator.AutoAssignAction());
      JButton var3 = new JButton("Report Judgeconfig");
      var3.setToolTipText("Opens a window with judge configuration in ASCII output");
      var3.addActionListener(new JudgeConfigurator.ReportAction());
      var1.add(var2);
      var1.add(var3);
      var1.add(Box.createHorizontalGlue());
      this.enabled = new JCheckBox("Activate this Judge?", this.judge.getEnabled());
      this.enabled.addActionListener(new JudgeConfigurator.EnableAction());
      var1.add(this.enabled);
      return var1;
   }

   protected void autoconfigure() {
      System.out.println("dummy");
   }

   protected void report() {
      System.out.println("dummy");
   }

   public static String getXML(Judge var0, Integer var1, String var2) {
      int var3 = var1;
      StringBuffer var5 = new StringBuffer();
      var5.append(SpecMLGenerator.getIndent(var3, var2) + "<judge title=\"" + var0.name + "\">\n");
      var5.append(SpecMLGenerator.getIndent(var3, var2) + "</judge>\n");
      return var5.toString();
   }

   public static Judge getJudgeFromXML() {
      return null;
   }

   protected void reactOnJudgeDataChange() {
   }

   public void judgeDataChanged() {
      System.out.println("JudgeDataChanged: " + this.judge.getEnabled());
      this.enabled.setSelected(this.judge.getEnabled());
      this.reactOnJudgeDataChange();
   }

   class AutoAssignAction extends AbstractAction {
      AutoAssignAction() {
         super("Autoconfigure");
      }

      public void actionPerformed(ActionEvent var1) {
         JudgeConfigurator.this.judge.setEnabled(true);
         JudgeConfigurator.this.autoconfigure();
      }
   }

   class EnableAction extends AbstractAction {
      EnableAction() {
         super("Enable");
      }

      public void actionPerformed(ActionEvent var1) {
         JCheckBox var2 = (JCheckBox)var1.getSource();
         JudgeConfigurator.this.judge.setEnabled(var2.isSelected());
      }
   }

   class ReportAction extends AbstractAction {
      ReportAction() {
         super("report");
      }

      public void actionPerformed(ActionEvent var1) {
         JudgeConfigurator.this.report();
      }
   }
}
