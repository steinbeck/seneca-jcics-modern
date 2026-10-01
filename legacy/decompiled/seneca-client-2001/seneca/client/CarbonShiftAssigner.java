package seneca.client;

import compchem.MFAnalyser;
import seneca.core.NMRSignal;
import seneca.core.NMRSpectrum;
import seneca.core.SenecaDataset;

public class CarbonShiftAssigner extends SpectrumAssigner {
   boolean debug = false;
   SenecaDataset sd;

   public CarbonShiftAssigner(SenecaDataset var1) {
      this.sd = var1;
   }

   public boolean assign() {
      StringBuffer var1 = new StringBuffer("Warning");
      int var2 = 0;
      int var3 = 0;
      float var4 = 0.0F;
      NMRSignal var5 = null;
      Object var6 = null;
      Object var7 = null;
      if (this.debug) {
         System.out.println("This is CarbonShiftAssigner doing its job");
      }

      float var8 = 0.0F;
      MFAnalyser var9 = new MFAnalyser(this.sd.setOfNodes);
      this.normalize(this.sd.carbon1D);
      var4 = this.sumIntensity(this.sd.carbon1D);
      if (this.sd.setOfNodes != null && this.sd.carbon1D != null) {
         if (this.debug) {
            System.out.println("Number of Carbons: " + var9.getAtomCount("C"));
         }

         if (this.debug) {
            System.out.println("Sum of signal intensity in spectrum: " + var4);
         }

         if (var9.getAtomCount("C") == Math.round(var4)) {
            var2 = 0;

            for (int var10 = 0; var10 < this.sd.setOfNodes.length; var10++) {
               if (this.sd.setOfNodes[var10].symbol.equals("C")) {
                  if (this.debug) {
                     System.out.println("Assigning :" + this.sd.setOfNodes[var10].symbol);
                  }

                  var5 = (NMRSignal)this.sd.carbon1D.getSignal(var2);

                  for (int var11 = 0; var11 < Math.round(var5.intensity * var5.normalizeFactor); var11++) {
                     var10 += var11;
                     var8 = var5.getShift(NMRSpectrum.NUC_CARBON);
                     var6 = (NMRSignal)this.sd.dept90.pickClosestSignal(var8, NMRSpectrum.NUC_CARBON, 0.05F);
                     var7 = (NMRSignal)this.sd.dept135.pickClosestSignal(var8, NMRSpectrum.NUC_CARBON, 0.05F);
                     this.sd.setOfNodes[var10].setPhysicalProperty("13C chemical shift", new Float(var5.getShift(NMRSpectrum.NUC_CARBON)));
                     if (var7 != null && ((NMRSignal)var7).phase == NMRSignal.PHASE_POSITIVE && var6 == null) {
                        this.sd.setOfNodes[var10].HCount = 3;
                        var3 += 3;
                     }

                     if (var7 != null && ((NMRSignal)var7).phase == NMRSignal.PHASE_NEGATIVE) {
                        if (var6 != null) {
                           var1.append("Warning: There is a DEPT90 signal within the picking range of Methylene group " + (var10 + 1) + "\n");
                           var1.append("Assignment ambiguous. Please check.\n");
                           System.out.println(var1);
                        }

                        this.sd.setOfNodes[var10].HCount = 2;
                        var3 += 2;
                     }

                     if (var6 != null && ((NMRSignal)var6).phase == NMRSignal.PHASE_POSITIVE) {
                        this.sd.setOfNodes[var10].HCount = 1;
                        var3++;
                     }

                     if (var7 == null && var6 == null) {
                        this.sd.setOfNodes[var10].HCount = 0;
                     }
                  }

                  var2++;
               }
            }
         }

         var9 = new MFAnalyser(this.sd.getMolecularFormula());
         if (this.debug) {
            System.out.println("HCount in Formula: " + var9.getAtomCount("H") + ", HCount in table: " + var3);
         }

         if (var9.getAtomCount("H") == var3) {
            for (int var19 = 0; var19 < this.sd.setOfNodes.length; var19++) {
               if (!this.sd.setOfNodes[var19].symbol.equals("C")) {
                  this.sd.setOfNodes[var19].HCount = 0;
               }
            }

            if (this.debug) {
               System.out.println("CarbonShiftAssigner done");
            }
         }
      }

      return true;
   }

   private void normalize(NMRSpectrum var1) {
      MFAnalyser var2 = new MFAnalyser(this.sd.setOfNodes);
      NMRSignal var3 = null;

      for (int var4 = 0; var4 < var1.size(); var4++) {
         var3 = (NMRSignal)var1.getSignal(var4);
         var3.normalizeFactor = 1.0F / var3.intensity;
      }

      while (Math.round(this.sumIntensity(var1)) < var2.getAtomCount("C")) {
         this.getLargestSignal(var1).normalizeFactor *= 2.0F;
      }

      for (int var5 = 0; var5 < var1.size(); var5++) {
         var3 = (NMRSignal)var1.getSignal(var5);
         System.out.println("Signal " + var5 + ": " + var3.intensity + ", " + var3.intensity * var3.normalizeFactor);
      }
   }

   private float sumIntensity(NMRSpectrum var1) {
      float var2 = 0.0F;
      NMRSignal var3 = null;

      for (int var4 = 0; var4 < var1.size(); var4++) {
         var3 = (NMRSignal)var1.getSignal(var4);
         var2 += var3.intensity * var3.normalizeFactor;
      }

      return var2;
   }

   private NMRSignal getLargestSignal(NMRSpectrum var1) {
      float var2 = 0.0F;
      NMRSignal var3 = null;
      NMRSignal var4 = null;

      for (int var5 = 0; var5 < var1.size(); var5++) {
         var3 = (NMRSignal)var1.getSignal(var5);
         if (var3.intensity > var2) {
            var4 = var3;
            var2 = var3.intensity;
         }
      }

      return var4;
   }

   public void setVerbose(boolean var1) {
      this.debug = var1;
   }
}
