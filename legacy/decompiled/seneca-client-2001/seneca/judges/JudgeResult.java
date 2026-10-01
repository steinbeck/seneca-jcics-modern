package seneca.judges;

import java.io.Serializable;

public class JudgeResult implements Serializable, Cloneable {
   public long score;
   public long maxScore;
   public long propertyValue;
   public String scoreDescription;

   public JudgeResult() {
   }

   public JudgeResult(long var1, long var3, long var5, String var7) {
      this.maxScore = var1;
      this.score = var3;
      this.propertyValue = var5;
      this.scoreDescription = var7;
   }

   public Object clone() {
      Object var1 = null;

      try {
         var1 = super.clone();
      } catch (CloneNotSupportedException var3) {
         System.err.println("MyObject can't clone");
      }

      return var1;
   }
}
