package seneca.client;

import compchem.Atom;
import java.util.Vector;
import seneca.core.NMRSignal;
import seneca.core.NMRSpectrum;
import seneca.core.SenecaDataset;
import seneca.judges.HHCOSYJudge;

public class HHCOSYAssigner extends SpectrumAssigner {
   private boolean debug = false;
   private SenecaDataset sd;
   HHCOSYJudge hhcosyJudge = null;

   public HHCOSYAssigner(SenecaDataset var1) {
      this.sd = var1;
      this.hhcosyJudge = (HHCOSYJudge)var1.getJudge("HHCOSYJudge");
   }

   public boolean assign() {
      try {
         NMRSignal var1 = null;
         NMRSignal var2 = null;
         NMRSignal var3 = null;
         Object var8 = null;
         Object var9 = null;
         if (this.hhcosyJudge == null) {
            this.hhcosyJudge = new HHCOSYJudge();
         }

         for (int var13 = 0; var13 < this.sd.setOfNodes.length; var13++) {
            if (this.sd.setOfNodes[var13].symbol.equals("C") && this.sd.setOfNodes[var13].getPhysicalProperty("13C chemical shift") == null) {
               return false;
            }
         }

         for (int var14 = 0; var14 < this.sd.setOfNodes.length; var14++) {
            System.out.println(this.sd.setOfNodes[var14].number);
         }

         for (int var15 = 0; var15 < this.sd.hhcosy.size(); var15++) {
            var1 = (NMRSignal)this.sd.hhcosy.getSignal(var15);
            float var4 = var1.getShift(0);
            float var5 = var1.getShift(1);
            if (this.debug) {
               System.out.println(var4 + " - " + var5);
            }

            var2 = (NMRSignal)this.sd.hetcor.pickClosestSignal(var4, NMRSpectrum.NUC_PROTON, 0.01F);
            float var6 = var2.getShift(NMRSpectrum.NUC_CARBON);
            var9 = this.pickClosestCarbons(var6, 0.05F);
            var3 = (NMRSignal)this.sd.hetcor.pickClosestSignal(var5, NMRSpectrum.NUC_PROTON, 0.01F);
            float var7 = var3.getShift(NMRSpectrum.NUC_CARBON);
            var8 = this.pickClosestCarbons(var7, 0.05F);
            if (this.debug) {
               System.out.println("Vectorsize: " + var8.size() + " - " + var9.size());
            }

            for (int var16 = 0; var16 < var8.size(); var16++) {
               int var10 = ((Atom)var8.elementAt(var16)).number;
               int var11 = ((Atom)var9.elementAt(0)).number;
               if (this.hhcosyJudge.assignment == null || this.hhcosyJudge.assignment.length == 0) {
                  this.hhcosyJudge.assignment = new boolean[this.sd.setOfNodes.length][this.sd.setOfNodes.length][this.sd.setOfNodes.length];
               }

               this.hhcosyJudge.assignment[var10][var11][var11] = true;
               if (this.debug) {
                  System.out.println("Assigning a 3JHH correlation between Hydrogens at Heavyatom " + var10 + " and " + var11);
               }

               for (int var17 = 1; var17 < var9.size(); var17++) {
                  int var12 = ((Atom)var9.elementAt(var16)).number;
                  this.hhcosyJudge.assignment[var10][var11][var12] = true;
               }
            }
         }

         return true;
      } catch (Exception var18) {
         return false;
      }
   }

   public Vector pickClosestCarbons(float var1, float var2) {
      byte var3 = -1;
      int var4 = -1;
      float var5 = Float.MAX_VALUE;
      float var6 = 0.0F;
      float var7 = 0.0F;
      Vector var8 = new Vector();

      for (int var9 = 0; var9 < this.sd.setOfNodes.length; var9++) {
         if (this.sd.setOfNodes[var9].symbol.equals("C")) {
            var6 = (Float)this.sd.setOfNodes[var9].getPhysicalProperty("13C chemical shift");
            if (var5 > Math.abs(var6 - var1)) {
               var5 = Math.abs(var6 - var1);
               var7 = var6;
               var4 = var9;
            }
         }
      }

      if (var5 < var2) {
         var8.add(this.sd.setOfNodes[var4]);
      }

      for (int var10 = 0; var10 < this.sd.setOfNodes.length; var10++) {
         if (this.sd.setOfNodes[var10].symbol.equals("C")) {
            var6 = (Float)this.sd.setOfNodes[var10].getPhysicalProperty("13C chemical shift");
            if (var6 == var7 && var10 != var4) {
               var8.add(this.sd.setOfNodes[var4]);
            }
         }
      }

      return var8;
   }

   public void setVerbose(boolean var1) {
      this.debug = var1;
   }
}
