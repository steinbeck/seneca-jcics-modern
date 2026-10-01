package seneca.structgen;

import java.io.Serializable;
import seneca.judges.ScoreSummary;

public class StructureGeneratorStatus implements Serializable, Cloneable {
   public static String[] statusStrings = new String[]{"Idle", "Running", "Holding Results", "Stopped by User"};
   public static int IDLE = 0;
   public static int RUNNING = 1;
   public static int HOLDING = 2;
   public static int STOPPED = 3;
   public String datasetName = null;
   public String molecularFormula = null;
   public long iteration = 0L;
   public String bestStructure = null;
   public String status = null;
   public ScoreSummary bestEvaluation = null;
   public AnnealingLog al = null;

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
