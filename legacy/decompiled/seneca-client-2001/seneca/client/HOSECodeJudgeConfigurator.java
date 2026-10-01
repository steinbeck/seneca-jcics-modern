package seneca.client;

import java.awt.Dimension;
import javax.swing.Box;
import javax.swing.JEditorPane;
import javax.swing.JScrollPane;
import javax.swing.text.html.HTMLEditorKit;
import seneca.core.SenecaDataset;
import seneca.judges.HOSECodeJudge;

public class HOSECodeJudgeConfigurator extends JudgeConfigurator {
   HOSECodeJudge hoseCodeJudge = null;

   HOSECodeJudgeConfigurator(SenecaDataset var1) {
      super(var1, (HOSECodeJudge)var1.getJudge("HOSECodeJudge"));
      this.hoseCodeJudge = (HOSECodeJudge)var1.getJudge("HOSECodeJudge");
   }

   protected Box contructCenterBox() {
      Box var1 = new Box(0);
      JEditorPane var2 = new JEditorPane();
      var2.setEditable(false);
      var2.setEditorKit(new HTMLEditorKit());
      var2.setText(this.getMessage());
      JScrollPane var3 = new JScrollPane(var2);
      var3.setPreferredSize(new Dimension(200, 400));
      var1.add(var3);
      return var1;
   }

   protected String getMessage() {
      StringBuffer var1 = new StringBuffer();
      var1.append("<html>");
      var1.append("<b>HOSECodeJudge</b> uses about 600 1-sphere HOSE codes ");
      var1.append("to validate the carbon environment of a structure ");
      var1.append("during the stochastic search process in terms of ");
      var1.append("hybridization state and hetero atom attachments. ");
      var1.append("There is no need for any configurations here. ");
      var1.append("Press the 'autoconfigure' button or check the 'activate' box. ");
      var1.append("<b>Important Note: </b><i>You should not use this Judge in conjunction ");
      var1.append("with HybridizationJudge and HeteroBondsJudge. Use either HOSECodeJudge ");
      var1.append("or the combination of HybridizationJudge or HeteroBondsJudge</i>.");
      var1.append("</html>");
      return var1.toString();
   }

   public void autoconfigure() {
      if (super.sd.setOfNodes != null) {
         System.out.println("Autoconfiguring " + super.judge.name);
         float var1 = 0.0F;
         int var2 = 0;
         double[] var3 = new double[super.sd.setOfNodes.length];

         for (int var4 = 0; var4 < super.sd.setOfNodes.length; var4++) {
            if (super.sd.setOfNodes[var4].getPhysicalProperty("13C chemical shift") != null) {
               var3[var2] = ((Float)super.sd.setOfNodes[var4].getPhysicalProperty("13C chemical shift")).floatValue();
               var2++;
            }
         }

         double[] var5 = new double[var2];
         System.arraycopy(var3, 0, var5, 0, var2);
         this.hoseCodeJudge.setCarbonShifts(var5);
      }
   }
}
