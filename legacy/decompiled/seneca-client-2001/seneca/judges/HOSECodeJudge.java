package seneca.judges;

import compchem.BremserOneSphereHOSECodePredictor;
import compchem.HOSECodeGenerator;
import java.io.Serializable;

public class HOSECodeJudge extends Judge implements Serializable, Cloneable {
   public int score = 100;
   protected HOSECodeGenerator hcg;
   protected BremserOneSphereHOSECodePredictor predictor;
   protected double[] carbonShifts;
   private int wrongCounter = 0;

   public HOSECodeJudge() {
      super("HOSECodeJudge");
      this.hcg = new HOSECodeGenerator();
      this.predictor = new BremserOneSphereHOSECodePredictor();
      super.hasMaxScore = true;
   }

   public void setScore(int var1) {
   }

   public void setCarbonShifts(double[] var1) {
      this.carbonShifts = var1;
   }

   public void init() {
   }

   public void calcMaxScore() {
      super.maxScore = this.carbonShifts.length * this.score;
   }

   public JudgeResult evaluate(int[][] var1) {
      super.scoreSum = 0;
      Judge.debug = false;
      Object var2 = null;
      double var3 = 0.0;
      double var5 = 0.0;
      double var7 = 0.0;
      double var9 = 0.0;
      int var11 = 0;

      for (int var12 = 0; var12 < super.attic.atomSet.length; var12++) {
         if (super.attic.atomSet[var12].symbol.equals("C")) {
            var2 = this.hcg.getHOSECode(super.attic.atomSet, super.attic.atomSet[var12], 1);

            try {
               var3 = this.predictor.predict((String)var2);
               if (Judge.debug) {
                  System.out.println("HOSECodeJudge -> evaluate: shift=" + var3);
               }

               var5 = this.predictor.getConfidenceLimit((String)var2);
               if (Judge.debug) {
                  System.out.println("HOSECodeJudge -> evaluate: confidenceLimit=" + var5);
               }

               var7 = Math.abs(var3 - this.carbonShifts[var12]);
               var11++;
               var9 += var7;
               if (var7 < var5) {
                  super.scoreSum = super.scoreSum + this.score;
               }
            } catch (Exception var14) {
            }
         }
      }

      String var13 = "<p>Score: " + super.scoreSum + "/" + super.maxScore + ", Carbon shift medium deveation: " + var9 / var11 + "</p>";
      return new JudgeResult(super.maxScore, super.scoreSum, 0L, var13);
   }
}
