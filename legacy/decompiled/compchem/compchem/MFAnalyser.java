package compchem;

import java.util.Vector;

public class MFAnalyser {
   public String MF;
   public Atom[] setOfNodes;
   public int HCount = 0;
   private boolean verbose = false;

   public MFAnalyser(String var1) {
      this.MF = var1;
      this.setOfNodes = this.analyseMF(var1);
   }

   public MFAnalyser(Atom[] var1) {
      this.setOfNodes = var1;
      this.MF = this.analyseSetOfNodes();
   }

   public Atom[] getSetOfNodes() {
      return this.setOfNodes;
   }

   public String getMolecularFormula() {
      return this.MF;
   }

   public float getMass() {
      float var1 = 0.0F;
      StandardIsotopes var3 = new StandardIsotopes();
      Atom[] var4 = this.getSetOfNodes();

      for (int var5 = 0; var5 < var4.length; var5++) {
         Isotope var2 = var3.getMajorIsotope(var4[var5].symbol);
         if (var2 == null) {
            return 0.0F;
         }

         var1 = (float)(var1 + var2.exactMass);
      }

      return var1;
   }

   public Atom[] getSetOfHeavyNodes() {
      Atom[] var1 = new Atom[this.setOfNodes.length];
      int var3 = 0;

      for (int var4 = 0; var4 < this.setOfNodes.length; var4++) {
         if (!this.setOfNodes[var4].symbol.equals("H")) {
            var1[var3++] = this.setOfNodes[var4];
         }
      }

      Atom[] var2 = new Atom[var3];
      System.arraycopy(var1, 0, var2, 0, var3);
      return var2;
   }

   private Atom[] analyseMF(String var1) {
      Vector var2 = new Vector();
      String var5 = new String();
      String var6 = new String("0");
      if (var1.length() == 0) {
         return null;
      } else {
         for (int var8 = 0; var8 < var1.length(); var8++) {
            char var4 = var1.charAt(var8);
            if (var8 < var1.length()) {
               if (var4 >= 'A' && var4 <= 'Z') {
                  var5 = String.valueOf(var4);
                  var6 = "0";
               }

               if (var4 >= 'a' && var4 <= 'z') {
                  var5 = var5 + var4;
               }

               if (var4 >= '0' && var4 <= '9') {
                  var6 = var6 + var4;
               }
            }

            if (var8 == var1.length() - 1 || var1.charAt(var8 + 1) >= 'A' && var1.charAt(var8 + 1) <= 'Z') {
               Integer var9 = new Integer(var6);
               int var7 = var9;
               if (var7 == 0) {
                  var7 = 1;
               }

               for (int var10 = 0; var10 < var7; var10++) {
                  var2.addElement(new Atom(var5));
               }
            }
         }

         Atom[] var3 = new Atom[var2.size()];
         var2.copyInto(var3);
         if (this.verbose) {
            for (int var11 = 0; var11 < var3.length; var11++) {
               System.out.println(var3[var11].symbol);
            }
         }

         return var3;
      }
   }

   public String analyseSetOfNodes() {
      String var2 = "";
      int var5 = 0;
      int var6 = 0;
      String[] var3 = new String[this.setOfNodes.length];
      int[] var4 = new int[this.setOfNodes.length];

      for (int var8 = 0; var8 < this.setOfNodes.length; var8++) {
         String var1 = this.setOfNodes[var8].symbol;
         if (this.setOfNodes[var8].HCount > 0) {
            var5 += this.setOfNodes[var8].HCount;
         }

         boolean var7 = false;

         for (int var9 = 0; var9 < var6; var9++) {
            if (var3[var9].equals(var1)) {
               var4[var9]++;
               var7 = true;
               break;
            }
         }

         if (!var7) {
            var3[var6] = var1;
            var4[var6]++;
            var6++;
         }
      }

      for (int var10 = 0; var10 < var6; var10++) {
         var2 = var2 + var3[var10];
         if (var4[var10] > 1) {
            var2 = var2 + new Integer(var4[var10]).toString();
         }

         if (var10 == 0 && var5 > 0) {
            var2 = var2 + "H";
            if (var5 > 1) {
               var2 = var2 + new Integer(var5).toString();
            }
         }
      }

      this.HCount = var5;
      return var2;
   }

   public int getAtomCount(String var1) {
      int var2 = 0;
      if (var1.equals("H") && this.HCount > 0) {
         return this.HCount;
      } else {
         for (int var3 = 0; var3 < this.setOfNodes.length; var3++) {
            if (this.setOfNodes[var3].symbol.equals(var1)) {
               var2++;
            }
         }

         return var2;
      }
   }

   public void setVerbose(boolean var1) {
      this.verbose = var1;
   }
}
