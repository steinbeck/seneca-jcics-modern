package seneca.structgen;

import java.io.Serializable;

public class AnnealingSchedule implements Serializable, Cloneable {
   public double initialAcceptanceProbability = 0.8;
   public long maxUphillSteps = 50L;
   public long plateauSteps = 500L;
   public double asymptoticCoolingFactor = 0.95;

   public String toString() {
      String var1 = "";
      return "A-F:" + this.asymptoticCoolingFactor + "-IAP:" + this.initialAcceptanceProbability + "-PS:" + this.plateauSteps + "-UHS: " + this.maxUphillSteps;
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
