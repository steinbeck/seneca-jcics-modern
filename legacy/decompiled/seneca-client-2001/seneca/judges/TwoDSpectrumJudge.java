package seneca.judges;

import compchem.PathUtils;
import java.io.Serializable;
import java.util.Vector;

public abstract class TwoDSpectrumJudge extends Judge implements Serializable, Cloneable {
   public boolean[][][] assignment;
   protected int numberOf2DSignals = 0;
   private int cutoff;
   public int[] scores = new int[7];
   public Vector rules = new Vector();

   public TwoDSpectrumJudge(String var1) {
      super(var1);

      for (int var2 = 0; var2 < this.scores.length; var2++) {
         this.scores[var2] = 0;
      }

      super.hasMaxScore = true;
   }

   public void init() {
      for (int var1 = 0; var1 < this.assignment.length; var1++) {
         for (int var2 = 0; var2 < this.assignment.length; var2++) {
            for (int var3 = 0; var3 < this.assignment.length; var3++) {
               if (this.assignment[var1][var2][var3]) {
                  this.rules.addElement(new TwoDRule(var1, var3));
               }
            }
         }
      }

      for (int var4 = 0; var4 < this.scores.length; var4++) {
         if (this.scores[var4] > 0) {
            this.cutoff = var4 + 1;
         }
      }
   }

   public void calcMaxScore() {
      int var1 = 0;

      for (int var2 = 0; var2 < this.scores.length; var2++) {
         if (this.scores[var2] > var1) {
            var1 = this.scores[var2];
         }
      }

      this.numberOf2DSignals = 0;
      System.out.println(super.name + ": " + this.assignment);

      for (int var3 = 0; var3 < this.assignment.length; var3++) {
         for (int var4 = 0; var4 < this.assignment.length; var4++) {
            for (int var5 = 0; var5 < this.assignment.length; var5++) {
               if (this.assignment[var3][var4][var5]) {
                  this.numberOf2DSignals++;
               }
            }
         }
      }

      super.maxScore = this.numberOf2DSignals * var1;
   }

   public JudgeResult evaluate(int[][] var1) {
      if (this.assignment == null) {
         super.resultString = "<p>No signals available for " + super.name + "</p>";
         return new JudgeResult(0L, 0L, 0L, super.resultString);
      } else {
         super.scoreSum = 0;
         int var2 = 0;
         int var3 = 0;

         for (int var5 = 0; var5 < this.rules.size(); var5++) {
            TwoDRule var4 = (TwoDRule)this.rules.elementAt(var5);
            var3 = PathUtils.getPathLength(super.attic.atomSet, super.attic.atomSet[var4.atom1], super.attic.atomSet[var4.atom2], this.cutoff);
            if (var3 > 0) {
               super.scoreSum = super.scoreSum + this.scores[var3 - 1];
            }

            var2++;
         }

         super.resultString = "<p>"
            + var2
            + "/"
            + this.numberOf2DSignals
            + " Signals satisfied in "
            + super.name
            + ". Score "
            + super.scoreSum
            + "/"
            + super.maxScore
            + "</p>";
         return new JudgeResult(super.maxScore, super.scoreSum, var2, super.resultString);
      }
   }

   public void setScores(int[] var1) {
      this.scores = var1;
   }

   public void setScore(int var1, int var2) {
      if (var2 < this.scores.length && var2 >= 0) {
         this.scores[var2] = var1;
      }
   }

   public String toString() {
      StringBuffer var1 = new StringBuffer();
      if (super.name != null && this.assignment != null) {
         var1.append("Configuration of TwoDSpectrumJudge " + super.name + ":\n");
         var1.append("Listing relation of nuclei by number...\n");

         for (int var2 = 0; var2 < this.assignment.length; var2++) {
            for (int var3 = 0; var3 < this.assignment.length; var3++) {
               for (int var4 = 0; var4 < this.assignment.length; var4++) {
                  if (this.assignment[var2][var3][var4]) {
                     var1.append(var2 + "-" + var4 + "\n");
                  }
               }
            }
         }

         var1.append("End of listing");
         return var1.toString();
      } else {
         return "";
      }
   }
}
