package seneca.judges;

import java.io.Serializable;

public class HybridizationJudge extends Judge implements Serializable, Cloneable {
   public int[][] scores;
   public static int SP3 = 0;
   public static int SP2 = 1;
   public static int SP = 2;
   public static String[] HYBRIDIZATIONSTRINGS = new String[]{"SP3", "SP2", "SP"};

   public HybridizationJudge() {
      super("HybridizationJudge");
      super.multiplicator = 100;
      super.hasMaxScore = true;
   }

   public void initScoreArray(int var1) {
      this.scores = new int[var1][3];
   }

   public void addHybridizationScore(int var1, int var2, double var3) {
      this.scores[var1 - 1][var2] = (int)(var3 * super.multiplicator);
   }

   public void init() {
      if (super.attic.atomSet != null) {
         this.scores = new int[super.attic.atomSet.length][3];
      }
   }

   public void calcMaxScore() {
      int var1 = 0;
      super.maxScore = 0;

      for (int var2 = 0; var2 < this.scores.length; var2++) {
         var1 = this.scores[var2][0];

         for (int var3 = 1; var3 < 3; var3++) {
            if (this.scores[var2][var3] > var1) {
               var1 = this.scores[var2][var3];
            }
         }

         super.maxScore += var1;
      }
   }

   public JudgeResult evaluate(int[][] var1) {
      super.scoreSum = 0;
      int var2 = 0;

      for (int var3 = 0; var3 < super.attic.atomSet.length; var3++) {
         var2 = super.attic.atomSet[var3].getBondCount();
         if (super.attic.atomSet[var3].symbol.equals("C")) {
            if (super.attic.atomSet[var3].degree == 1) {
               if (var2 == 1) {
                  super.scoreSum = super.scoreSum + this.scores[var3][SP3];
               }

               if (var2 == 2) {
                  super.scoreSum = super.scoreSum + this.scores[var3][SP2];
               }

               if (var2 == 3) {
                  super.scoreSum = super.scoreSum + this.scores[var3][SP];
               }
            }

            if (super.attic.atomSet[var3].degree == 2) {
               if (var2 == 2) {
                  super.scoreSum = super.scoreSum + this.scores[var3][SP3];
               }

               if (var2 == 3) {
                  super.scoreSum = super.scoreSum + this.scores[var3][SP2];
               }

               if (var2 == 4) {
                  super.scoreSum = super.scoreSum + this.scores[var3][SP];
               }
            }

            if (super.attic.atomSet[var3].degree == 3) {
               if (var2 == 3) {
                  super.scoreSum = super.scoreSum + this.scores[var3][SP3];
               }

               if (var2 == 4) {
                  super.scoreSum = super.scoreSum + this.scores[var3][SP2];
               }
            }

            if (super.attic.atomSet[var3].degree == 4) {
               super.scoreSum = super.scoreSum + this.scores[var3][SP3];
            }
         }
      }

      return new JudgeResult(
         super.maxScore, super.scoreSum, 0L, "<p>" + super.score + " from a possible " + super.maxScore + " points for hybridization state were given.</p>"
      );
   }
}
