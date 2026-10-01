package seneca.judges;

import java.io.Serializable;

public class HeteroBondsJudge extends Judge implements Serializable, Cloneable {
   public int score = 100;
   public boolean[] isHeteroBonded;
   private int maxScore = 0;
   private int trueFlagsCount = 0;

   public HeteroBondsJudge() {
      super("HeteroBondsJudge");
      super.hasMaxScore = true;
   }

   public void setScore(int var1) {
      this.score = var1;
   }

   public void init() {
      if (super.attic.atomSet != null) {
         this.isHeteroBonded = new boolean[super.attic.atomSet.length];
      }
   }

   public void calcMaxScore() {
      this.maxScore = 0;
      this.trueFlagsCount = 0;

      for (int var1 = 0; var1 < this.isHeteroBonded.length; var1++) {
         if (this.isHeteroBonded[var1]) {
            this.maxScore = this.maxScore + this.score;
            this.trueFlagsCount++;
         }
      }
   }

   public JudgeResult evaluate(int[][] var1) {
      super.scoreSum = 0;
      int var2 = 0;

      for (int var3 = 0; var3 < this.isHeteroBonded.length; var3++) {
         if (this.isHeteroBonded[var3] && super.attic.atomSet[var3].symbol.equals("C")) {
            for (int var4 = 0; var4 < super.attic.atomSet[var3].degree; var4++) {
               if (!super.attic.atomSet[var3].nodeTable[var4].symbol.equals("C")) {
                  super.scoreSum = super.scoreSum + this.score;
                  var2++;
                  break;
               }
            }
         }
      }

      String var5 = "<p>" + var2 + " of " + this.trueFlagsCount + " carbons assigned to be heterobonded are indeed hetero-bonded in the structure.</p>";
      return new JudgeResult(this.maxScore, super.scoreSum, 0L, var5);
   }
}
