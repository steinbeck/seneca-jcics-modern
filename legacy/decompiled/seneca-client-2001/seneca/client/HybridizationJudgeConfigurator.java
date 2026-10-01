package seneca.client;

import java.awt.Dimension;
import javax.swing.Box;
import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import org.w3c.dom.Node;
import seneca.core.SenecaDataset;
import seneca.core.SpecMLConstants;
import seneca.core.SpecMLGenerator;
import seneca.judges.HybridizationJudge;
import seneca.judges.Judge;

public class HybridizationJudgeConfigurator extends JudgeConfigurator implements SpecMLConstants {
   HybridizationJudge hyJudge = null;

   HybridizationJudgeConfigurator(SenecaDataset var1) {
      super(var1, (HybridizationJudge)var1.getJudge("HybridizationJudge"));
      this.hyJudge = (HybridizationJudge)var1.getJudge("HybridizationJudge");
      this.hyJudge.getAttic().atomSet = var1.getSetOfNodes();
      this.hyJudge.init();
   }

   protected Box contructCenterBox() {
      Box var1 = new Box(0);
      JTable var2 = new JTable(new HybridizationJudgeConfigurator.HybridizationTableModel());
      JScrollPane var3 = new JScrollPane(var2);
      var3.setBorder(this.getTitledBorder());
      var3.setPreferredSize(new Dimension(200, 400));
      JComboBox var4 = new JComboBox();
      var4.addItem("1.0");
      var4.addItem("0.9");
      var4.addItem("0.8");
      var4.addItem("0.7");
      var4.addItem("0.6");
      var4.addItem("0.5");
      var4.addItem("0.4");
      var4.addItem("0.3");
      var4.addItem("0.2");
      var4.addItem("0.1");
      var4.addItem("0.0");
      var2.getColumnModel().getColumn(3).setCellEditor(new DefaultCellEditor(var4));
      var2.getColumnModel().getColumn(4).setCellEditor(new DefaultCellEditor(var4));
      var2.getColumnModel().getColumn(5).setCellEditor(new DefaultCellEditor(var4));
      var1.add(var3);
      return var1;
   }

   public static String getXML(Judge var0, Integer var1, String var2) {
      int var3 = var1;
      String var4 = var2;
      StringBuffer var5 = new StringBuffer();
      HybridizationJudge var6 = (HybridizationJudge)var0;
      var5.append(SpecMLGenerator.getIndent(var3, var2) + "<judge title=\"" + var6.name + "\">\n");
      var3++;

      for (int var7 = 0; var7 < var6.scores.length; var7++) {
         var5.append(SpecMLGenerator.getIndent(var3, var4));

         for (int var8 = 0; var8 < 3; var8++) {
            var5.append("<judgeScore ");
            var5.append("atomref=\"" + var7 + "\" ");
            var5.append("role=\"" + HybridizationJudge.HYBRIDIZATIONSTRINGS[var8] + "\">");
            var3++;
            var5.append(var6.scores[var7][var8]);
            var3--;
            var5.append("</judgeScore>");
         }

         var5.append("\n");
      }

      var5.append(SpecMLGenerator.getIndent(--var3, var4) + "</judge>\n");
      return var5.toString();
   }

   public static Judge getJudgeFromXML(Node var0) {
      boolean var1 = false;
      HybridizationJudge var2 = new HybridizationJudge();
      Object var5 = null;
      if (var1) {
         System.out.println("readAtom");
      }

      Object var6 = null;
      var2.name = var0.getAttributes().getNamedItem("title").getNodeValue();
      var2.initScoreArray(var0.getChildNodes().getLength());

      for (int var7 = 0; var7 < var0.getChildNodes().getLength(); var7++) {
         var6 = var0.getChildNodes().item(var7);
         if (SpecMLConstants.typeName[var6.getNodeType()].equals("Element") && var6.getNodeName().toLowerCase().equals("judgescore")) {
            int var4 = new Integer(var6.getAttributes().getNamedItem("atomref").getNodeValue());
            var5 = var6.getAttributes().getNamedItem("role").getNodeValue().trim();
            int var3 = new Integer(var6.getChildNodes().item(0).getNodeValue());
            if (var5.equals(HybridizationJudge.HYBRIDIZATIONSTRINGS[HybridizationJudge.SP3])) {
               var2.addHybridizationScore(var4, HybridizationJudge.SP3, var3);
            }

            if (var1) {
               System.out.println(var3);
            }
         }
      }

      return var2;
   }

   class HybridizationTableModel extends AbstractTableModel {
      String[] names = new String[]{"No.", "Symb.", "Shift", "sp3", "sp2", "sp"};

      public int getColumnCount() {
         return this.names.length;
      }

      public int getRowCount() {
         return HybridizationJudgeConfigurator.super.sd.setOfNodes == null ? 0 : HybridizationJudgeConfigurator.super.sd.setOfNodes.length;
      }

      public Object getValueAt(int var1, int var2) {
         if (HybridizationJudgeConfigurator.this.hyJudge == null) {
            return "";
         } else if (HybridizationJudgeConfigurator.this.hyJudge.scores == null) {
            return "";
         } else if (HybridizationJudgeConfigurator.this.hyJudge.scores.length < 1) {
            return "";
         } else {
            switch (var2) {
               case 0:
                  return String.valueOf(var1);
               case 1:
                  return HybridizationJudgeConfigurator.super.sd.setOfNodes[var1].symbol;
               case 2:
                  if (HybridizationJudgeConfigurator.super.sd.setOfNodes[var1].getPhysicalProperty("13C chemical shift") != null) {
                     return String.valueOf(HybridizationJudgeConfigurator.super.sd.setOfNodes[var1].getPhysicalProperty("13C chemical shift"));
                  }

                  return "-";
               case 3:
                  return new Integer(HybridizationJudgeConfigurator.this.hyJudge.scores[var1][0]).toString();
               case 4:
                  return new Integer(HybridizationJudgeConfigurator.this.hyJudge.scores[var1][1]).toString();
               case 5:
                  return new Integer(HybridizationJudgeConfigurator.this.hyJudge.scores[var1][2]).toString();
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
            case 0:
            case 1:
            case 2:
            default:
               break;
            case 3:
               HybridizationJudgeConfigurator.this.hyJudge.addHybridizationScore(var2 + 1, HybridizationJudge.SP3, new Double((String)var1));
               break;
            case 4:
               HybridizationJudgeConfigurator.this.hyJudge.addHybridizationScore(var2 + 1, HybridizationJudge.SP2, new Double((String)var1));
               break;
            case 5:
               HybridizationJudgeConfigurator.this.hyJudge.addHybridizationScore(var2 + 1, HybridizationJudge.SP, new Double((String)var1));
         }

         this.fireTableDataChanged();
      }
   }
}
