package seneca.client;

import compchem.Atom;
import java.util.Vector;
import seneca.core.NMRSignal;
import seneca.core.NMRSpectrum;
import seneca.core.SenecaDataset;
import seneca.judges.HMBCJudge;

public class HMBCAssigner extends SpectrumAssigner {
   private boolean debug = false;
   private SenecaDataset sd;
   HMBCJudge hmbcJudge = null;

   public HMBCAssigner(SenecaDataset var1) {
      this.sd = var1;
      this.hmbcJudge = (HMBCJudge)var1.getJudge("HMBCJudge");
   }

   public boolean assign() {
      NMRSignal var1 = null;
      NMRSignal var2 = null;
      Object var5 = null;
      Object var6 = null;
      int var7 = 0;
      int var8 = 0;
      int var9 = 0;
      if (this.hmbcJudge == null) {
         this.hmbcJudge = new HMBCJudge();
      }

      for (int var10 = 0; var10 < this.sd.setOfNodes.length; var10++) {
         if (this.sd.setOfNodes[var10].symbol.equals("C") && this.sd.setOfNodes[var10].getPhysicalProperty("13C chemical shift") == null) {
            return false;
         }
      }

      for (int var11 = 0; var11 < this.sd.hetcorlr.size(); var11++) {
         var1 = (NMRSignal)this.sd.hetcorlr.getSignal(var11);
         float var3 = var1.getShift(NMRSpectrum.NUC_CARBON);
         float var4 = var1.getShift(NMRSpectrum.NUC_PROTON);
         if (this.debug) {
            System.out.println(var3 + " - " + var4);
         }

         var5 = this.pickClosestCarbons(var3, 0.05F);
         var2 = (NMRSignal)this.sd.hetcor.pickClosestSignal(var4, NMRSpectrum.NUC_PROTON, 0.01F);
         var3 = var2.getShift(NMRSpectrum.NUC_CARBON);
         var6 = this.pickClosestCarbons(var3, 0.05F);
         if (this.debug) {
            System.out.println("Vectorsize: " + var5.size() + " - " + var6.size());
         }

         for (int var12 = 0; var12 < var5.size(); var12++) {
            var7 = ((Atom)var5.elementAt(var12)).number;
            var8 = ((Atom)var6.elementAt(0)).number;
            if (this.hmbcJudge.assignment == null || this.hmbcJudge.assignment.length == 0) {
               this.hmbcJudge.assignment = new boolean[this.sd.setOfNodes.length][this.sd.setOfNodes.length][this.sd.setOfNodes.length];
            }

            this.hmbcJudge.assignment[var7][var8][var8] = true;
            if (this.debug) {
               System.out.println("Adding assignment: " + (var7 + 1) + " - " + (var9 + 1));
            }

            for (int var13 = 1; var13 < var6.size(); var13++) {
               var9 = ((Atom)var6.elementAt(var13)).number;
               this.hmbcJudge.assignment[var7][var8][var9] = true;
               if (this.debug) {
                  System.out.println("Adding alternative assignment: " + (var7 + 1) + " - " + (var9 + 1));
               }
            }
         }
      }

      return true;
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
