package seneca.core;

import compchem.Atom;
import java.util.Vector;

public class CMLGenerator {
   private String indent;
   private int indentLevel;
   private boolean verbose = false;

   public CMLGenerator() {
      this.indent = "  ";
      this.indentLevel = 0;
   }

   public void setIndentString(String var1) {
      this.indent = var1;
   }

   public void setVerbose(boolean var1) {
      this.verbose = var1;
   }

   private String getIndent() {
      StringBuffer var1 = new StringBuffer();

      for (int var2 = 1; var2 <= this.indentLevel; var2++) {
         var1.append(this.indent);
      }

      return var1.toString();
   }

   public String convert(SenecaDataset var1) {
      StringBuffer var2 = new StringBuffer();
      var2.append("<?xml version=\"1.0\" ?>\n");
      var2.append("<!DOCTYPE molecule SYSTEM \"cml.dtd\" []>\n");
      var2.append("<molecule convention=\"SenecaDocument\">\n");
      this.indentLevel++;
      var2.append(this.getIndent() + "<string title=\"DocumentName\">" + var1.getName() + "</string>\n");
      var2.append(this.getIndent() + "<string title=\"DocumentDescription\">\n");
      this.indentLevel++;
      var2.append(this.getIndent() + this.escape(var1.getDescription()) + "\n");
      this.indentLevel--;
      var2.append(this.getIndent() + "</string>\n");
      var2.append(this.getIndent() + "<formula>" + var1.getMolecularFormula() + "</formula>\n");
      var2.append(this.getIndent() + "<float title=\"Mass\">" + var1.getMolecularMass() + "</float>\n");
      var2.append(this.convert(var1.getSetOfNodes()));
      var2.append(this.convert(var1.carbon1D));
      var2.append(this.convert(var1.hetcor));
      var2.append(this.convert(var1.hetcorlr));
      var2.append(this.convert(var1.hhcosy));
      var2.append(this.convert(var1.noesy));
      this.indentLevel--;
      var2.append("</molecule>\n");
      return var2.toString();
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
      var2.append(this.getIndent() + "<molecule title=\"ConnectionTable\">\n");
      this.indentLevel++;

      for (int var4 = 0; var4 < var1.length; var4++) {
         var2.append(this.getIndent() + "<atom id=\"a" + var1[var4].number + "\">\n");
         this.indentLevel++;
         var2.append(this.getIndent() + "<string builtin=\"elementType\">" + var1[var4].symbol + "</string>\n");
         this.indentLevel--;
         var2.append(this.getIndent() + "</atom>\n");
      }

      int var5 = 1;

      for (int var6 = 0; var6 < var1.length; var6++) {
         var3.addElement(var1[var6]);

         for (int var7 = 0; var7 < var1[var6].degree; var7++) {
            if (!var3.contains(var1[var6].nodeTable[var7])) {
               var2.append(
                  this.getIndent() + "<bond id=\"b" + ++var5 + "\" atomRefs=\"a" + var1[var6].number + " a" + var1[var6].nodeTable[var7].number + "\">\n"
               );
               this.indentLevel++;
               var2.append(this.getIndent() + "<string builtin=\"order\">" + var1[var6].bondTable[var7] + "</string>\n");
               this.indentLevel--;
               var2.append(this.getIndent() + "</bond>\n");
            }
         }
      }

      this.indentLevel--;
      var2.append(this.getIndent() + "</molecule>\n");
      return var2.toString();
   }

   public String convert(NMRSpectrum var1) {
      StringBuffer var2 = new StringBuffer();
      StringBuffer var3 = new StringBuffer();
      StringBuffer var4 = new StringBuffer();
      var2.append(this.getIndent() + "<molecule title=\"NMR Experiment\" " + "id=\"" + var1.name + "\" convention=\"Jena2000NMR\">\n");
      if (this.verbose) {
         System.out.println("Converting " + var1.specType);
      }

      this.indentLevel++;
      var2.append(this.getIndent() + "<string title=\"ExperimentType\">" + var1.specType + "</string>\n");
      if (this.verbose) {
         System.out.println(".");
      }

      var2.append(this.getIndent() + "<float title=\"Frequency\">" + var1.getSpectrometerFrequency() + "</string>\n");
      var2.append(this.getIndent() + "<string title=\"Solvent\">" + var1.getSolvent() + "</string>\n");
      if (this.verbose) {
         System.out.println(".");
      }

      var2.append(this.getIndent() + "<string title=\"Standard\">" + var1.getStandard() + "</string>\n");
      if (this.verbose) {
         System.out.println(".");
      }

      if (var1.size() > 0) {
         for (int var5 = 0; var5 < ((NMRSignal)var1.getSignal(0)).nucleus.length; var5++) {
            var3.append(((NMRSignal)var1.getSignal(0)).nucleus[var5] + " ");
            var4.append("Shift ");
         }

         if (this.verbose) {
            System.out.println(".");
         }

         var2.append(this.getIndent() + "<stringArray title=\"Nucleus\">" + var3.toString() + "</string>\n");
         if (this.verbose) {
            System.out.println(".");
         }

         var2.append(this.getIndent() + "<stringArray title=\"Properties\">" + var4.toString() + "</string>\n");
         if (this.verbose) {
            System.out.println(".");
         }

         var2.append(this.getIndent() + "<list title=\"Signals\">\n");
         if (this.verbose) {
            System.out.println(".");
         }

         this.indentLevel++;

         for (int var6 = 0; var6 < var1.size(); var6++) {
            var2.append(this.getIndent() + "<list title=\"Signal\" " + " id=\"" + var1.name + ".p" + var6 + "\">\n");
            this.indentLevel++;
            var2.append(this.getIndent() + "<floatArray title=\"Value\">");

            for (int var7 = 0; var7 < ((NMRSignal)var1.getSignal(var6)).shift.length; var7++) {
               var2.append(((NMRSignal)var1.getSignal(var6)).shift[var7] + " ");
            }

            var2.append(this.getIndent() + "</floatArray>\n");
            var2.append(this.getIndent() + "<float title=\"Intensity\">" + ((NMRSignal)var1.getSignal(var6)).intensity + "</float>\n");
            var2.append(this.getIndent() + "<float title=\"Phase\">" + ((NMRSignal)var1.getSignal(var6)).phase + "</float>\n");
            this.indentLevel--;
            var2.append(this.getIndent() + "</list>\n");
         }

         this.indentLevel--;
         var2.append(this.getIndent() + "</list>\n");
         this.indentLevel--;
      }

      var2.append(this.getIndent() + "</molecule>\n");
      return var2.toString();
   }
}
