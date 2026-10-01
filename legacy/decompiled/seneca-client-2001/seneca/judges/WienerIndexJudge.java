package seneca.judges;

import compchem.Atom;
import compchem.DataStructureTools;
import java.io.Serializable;

public class WienerIndexJudge extends Judge implements Serializable, Cloneable {
   private long targetIndex;

   public WienerIndexJudge() {
      super("WienerIndexJudge");
   }

   public WienerIndexJudge(Atom[] var1) {
      this();
   }

   public void setTargetIndex(long var1) {
      this.targetIndex = var1;
   }

   public void init() {
   }

   public void calcMaxScore() {
   }

   public JudgeResult evaluate(int[][] var1) {
      long var2 = 0L;
      super.connectionTable = var1;
      long var4 = super.connectionTable.length;

      for (int var6 = 0; var6 < super.connectionTable.length - 1; var6++) {
         for (int var7 = var6 + 1; var7 < super.connectionTable.length; var7++) {
            var2 += super.attic.apsp[var6][var7];
         }
      }

      super.scoreSum = (int)Math.min(this.targetIndex - Math.abs(this.targetIndex - var2), this.targetIndex);
      super.scoreSum = Math.max(0, super.scoreSum);
      return new JudgeResult(this.targetIndex, super.scoreSum, var2, "");
   }

   public JudgeResult evaluate(Atom[] var1) {
      int[][] var2 = DataStructureTools.createConnectionTable(var1);
      return this.evaluate(var2);
   }
}
