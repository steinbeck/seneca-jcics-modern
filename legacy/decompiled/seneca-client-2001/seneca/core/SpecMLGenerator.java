package seneca.core;

import compchem.Atom;
import java.util.Vector;
import seneca.gatekeeper.ServerInfo;
import seneca.structgen.AnnealingSchedule;

public class SpecMLGenerator {
   private static String indent = "  ";
   private static int indentLevel = 0;
   private static boolean verbose = false;

   public void setIndentString(String var1) {
      indent = var1;
   }

   public void setVerbose(boolean var1) {
      verbose = var1;
   }

   public static String getIndent() {
      return getIndent(indentLevel, indent);
   }

   public static String getIndent(int var0) {
      return getIndent(var0, indent);
   }

   public static String getIndent(int var0, String var1) {
      StringBuffer var2 = new StringBuffer();

      for (int var3 = 1; var3 <= var0; var3++) {
         var2.append(var1);
      }

      return var2.toString();
   }

   public String convert(SenecaDataset var1) {
      Object var2 = null;
      Object var3 = null;
      Object var4 = null;
      Object var5 = null;
      Class[] var6 = new Class[3];
      Object var7 = null;
      Object[] var8 = new Object[3];
      StringBuffer var9 = new StringBuffer();
      var9.append("<?xml version=\"1.0\" ?>\n");
      var9.append("<senecadataset title=\"" + var1.getName() + "\">\n");
      indentLevel++;
      var9.append(getIndent() + "<formula>" + var1.getMolecularFormula() + "</formula>\n");
      var9.append(this.convert(var1.getSetOfNodes()));
      var9.append(this.convert(var1.carbon1D));
      var9.append(this.convert(var1.dept90));
      var9.append(this.convert(var1.dept135));
      var9.append(this.convert(var1.hetcor));
      var9.append(this.convert(var1.hetcorlr));
      var9.append(this.convert(var1.hhcosy));
      var9.append(this.convert(var1.noesy));
      int var10 = 0;

      while (var10 < var1.judges.size()) {
         var10++;
      }

      for (int var11 = 0; var11 < var1.annealingSchedules.size(); var11++) {
         var9.append(this.convert((AnnealingSchedule)var1.annealingSchedules.elementAt(var11)));
      }

      for (int var12 = 0; var12 < var1.serverList.size(); var12++) {
         var9.append(convert((ServerInfo)var1.serverList.elementAt(var12), indentLevel));
      }

      indentLevel--;
      var9.append("</senecadataset>\n");
      return var9.toString();
   }

   public String escape(String var1) {
      StringBuffer var2 = new StringBuffer();

      for (int var3 = 0; var3 < var1.length(); var3++) {
         char var4 = var1.charAt(var3);
         if (var4 == '&') {
            var2.append("&amp;");
         } else if (var4 == '<') {
            var2.append("&lt;");
         } else {
            var2.append(var4);
         }
      }

      return var2.toString();
   }

   public String convert(Atom[] var1) {
      StringBuffer var2 = new StringBuffer();
      Vector var3 = new Vector();
      var2.append(getIndent() + "<molecule xmlns=\"http://www.xml-cml.org/cml.dtd\" title=\"ConnectionTable\">\n");
      indentLevel++;

      for (int var4 = 0; var4 < var1.length; var4++) {
         var2.append(getIndent() + "<atom id=\"a" + var1[var4].number + "\">\n");
         indentLevel++;
         var2.append(getIndent() + "<string builtin=\"elementType\">" + var1[var4].symbol + "</string>\n");
         if (var1[var4].HCount >= 0) {
            var2.append(getIndent() + "<integer builtin=\"hydrogenCount\">" + var1[var4].HCount + "</integer>\n");
         }

         if (var1[var4].getPhysicalProperty("13C chemical shift") != null) {
            var2.append(getIndent() + "<float title=\"assignedCarbonShift\">" + var1[var4].getPhysicalProperty("13C chemical shift") + "</float>\n");
         }

         indentLevel--;
         var2.append(getIndent() + "</atom>\n");
      }

      int var5 = 1;

      for (int var6 = 0; var6 < var1.length; var6++) {
         var3.addElement(var1[var6]);

         for (int var7 = 0; var7 < var1[var6].degree; var7++) {
            if (!var3.contains(var1[var6].nodeTable[var7])) {
               var2.append(getIndent() + "<bond id=\"b" + ++var5 + "\" atomRefs=\"a" + var1[var6].number + " a" + var1[var6].nodeTable[var7].number + "\">\n");
               indentLevel++;
               var2.append(getIndent() + "<string builtin=\"order\">" + var1[var6].bondTable[var7] + "</string>\n");
               indentLevel--;
               var2.append(getIndent() + "</bond>\n");
            }
         }
      }

      indentLevel--;
      var2.append(getIndent() + "</molecule>\n");
      return var2.toString();
   }

   public String convert(NMRSpectrum var1) {
      if (var1 == null) {
         return null;
      } else {
         StringBuffer var2 = new StringBuffer();
         new StringBuffer();
         new StringBuffer();
         Object var5 = null;
         var2.append(
            getIndent() + "<spectrum xmlns=\"http://www.nmrshiftdb.org/\" type=\"NMR Experiment\" " + "id=\"" + var1.name + "\" convention=\"Seneca\">\n"
         );
         if (verbose) {
            System.out.println("Converting " + var1.specType);
         }

         indentLevel++;
         var2.append(getIndent() + "<spectruminfo>\n");
         indentLevel++;
         var2.append(getIndent() + "<dimension>" + var1.dim + "</dimension>\n");
         var2.append(getIndent() + "<type>" + var1.specType + "</type>\n");
         var2.append(getIndent() + "<frequency unit=\"MHz\">" + var1.getSpectrometerFrequency() + "</frequency>\n");
         var2.append(getIndent() + "<solvent>" + var1.getSolvent() + "</solvent>\n");
         var2.append(getIndent() + "<standard role=\"calibration\">" + var1.getStandard() + "</standard>\n");
         if (var1.size() == 0) {
            indentLevel--;
            var2.append(getIndent() + "</spectruminfo>\n");
            indentLevel--;
            var2.append(getIndent() + "</spectrum>\n");
            return var2.toString();
         } else {
            for (int var6 = 0; var6 < ((NMRSignal)var1.getSignal(0)).nucleus.length; var6++) {
               var5 = "f" + (var6 + 1);
               var2.append(getIndent() + "<axisinfo role=\"" + var5 + "\">\n");
               indentLevel++;
               var2.append(getIndent() + "<nucleus>" + ((NMRSignal)var1.getSignal(0)).nucleus[var6] + "</nucleus>\n");
               var2.append(getIndent() + "<property>shift</property>\n");
               var2.append(getIndent() + "<unit>ppm</unit>\n");
               indentLevel--;
               var2.append(getIndent() + "</axisinfo>\n");
            }

            indentLevel--;
            var2.append(getIndent() + "</spectruminfo>\n");
            if (verbose) {
               System.out.println(".");
            }

            if (var1.size() > 0) {
               for (int var7 = 0; var7 < var1.size(); var7++) {
                  var2.append(getIndent() + "<signal" + " id=\"" + var1.name + ".p" + var7 + "\">\n");
                  indentLevel++;

                  for (int var8 = 0; var8 < ((NMRSignal)var1.getSignal(var7)).shift.length; var8++) {
                     var5 = "f" + (var8 + 1);
                     var2.append(getIndent() + "<location role=\"" + var5 + "\">" + ((NMRSignal)var1.getSignal(var7)).shift[var8] + "</location>\n");
                  }

                  var2.append(getIndent() + "<intensity type=\"relative\">" + ((NMRSignal)var1.getSignal(var7)).intensity + "</intensity>\n");
                  var2.append(getIndent() + "<normalizefactor>" + ((NMRSignal)var1.getSignal(var7)).normalizeFactor + "</normalizefactor>\n");
                  var2.append(getIndent() + "<phase>" + ((NMRSignal)var1.getSignal(var7)).phase + "</phase>\n");
                  indentLevel--;
                  var2.append(getIndent() + "</signal>\n");
               }
            }

            indentLevel--;
            var2.append(getIndent() + "</spectrum>\n");
            return var2.toString();
         }
      }
   }

   public String convert(AnnealingSchedule var1) {
      if (var1 == null) {
         return null;
      } else {
         StringBuffer var2 = new StringBuffer();
         Object var3 = null;
         var2.append(getIndent() + "<annealingschedule>\n");
         if (verbose) {
            System.out.println("Converting " + var1);
         }

         indentLevel++;
         var2.append(getIndent() + "<initialAcceptanceProbability>" + var1.initialAcceptanceProbability + "</initialAcceptanceProbability>\n");
         var2.append(getIndent() + "<maxUphillSteps>" + var1.maxUphillSteps + "</maxUphillSteps>\n");
         var2.append(getIndent() + "<plateauSteps>" + var1.plateauSteps + "</plateauSteps>\n");
         var2.append(getIndent() + "<asymptoticCoolingFactor>" + var1.asymptoticCoolingFactor + "</asymptoticCoolingFactor>\n");
         indentLevel--;
         var2.append(getIndent() + "</annealingschedule>\n");
         return var2.toString();
      }
   }

   public static String convert(ServerInfo var0, int var1) {
      if (var0 == null) {
         return null;
      } else {
         StringBuffer var2 = new StringBuffer();
         new SpecMLGenerator();
         Object var4 = null;
         var2.append(getIndent() + "<serverinfo>\n");
         if (verbose) {
            System.out.println("Converting " + var0);
         }

         var1++;
         var2.append(getIndent() + "<hostname>" + var0.getHostname() + "</hostname>\n");
         var2.append(getIndent() + "<responseTime>" + var0.getResponseTime() + "</responseTime>\n");
         var2.append(getIndent() + "<speed>" + var0.getSpeed() + "</speed>\n");
         var1--;
         var2.append(getIndent() + "</serverinfo>\n");
         return var2.toString();
      }
   }
}
