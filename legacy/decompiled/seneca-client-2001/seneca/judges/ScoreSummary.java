package seneca.judges;

import java.io.Serializable;

public class ScoreSummary implements Serializable, Cloneable {
   public long score = 0L;
   public long maxScore = -1L;
   public String description = "";

   public ScoreSummary(long var1, String var3) {
      this.score = var1;
      this.description = var3;
   }

   public String toString() {
      String var1 = this.description + "Overall Score:" + this.score;
      if (this.maxScore > 0L) {
         var1 = var1 + "/" + this.maxScore;
      }

      return var1;
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
