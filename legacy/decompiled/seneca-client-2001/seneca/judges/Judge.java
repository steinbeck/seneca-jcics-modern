package seneca.judges;

import java.io.Serializable;
import seneca.structgen.Attic;

public abstract class Judge implements Serializable, Cloneable {
   public String name = "nop";
   private boolean enabled = false;
   boolean hasMaxScore = false;
   boolean ringSetRequired = false;
   protected Attic attic = null;
   int[][] connectionTable;
   int score;
   int maxScore;
   int scoreSum;
   String resultString;
   int multiplicator;
   transient JudgeListener judgeListener = null;
   static boolean debug = false;
   static boolean report = true;

   public Judge(String var1) {
      this.name = var1;
      this.attic = new Attic();
   }

   public abstract JudgeResult evaluate(int[][] var1);

   public abstract void init();

   public abstract void calcMaxScore();

   public int getMultiplicator() {
      return this.multiplicator;
   }

   public void setMultiplicator(int var1) {
      this.multiplicator = var1;
   }

   public boolean getEnabled() {
      return this.enabled;
   }

   public void setEnabled(boolean var1) {
      this.enabled = var1;
      this.fireChanged();
   }

   public void setJudgeListener(JudgeListener var1) {
      this.judgeListener = var1;
   }

   public void fireChanged() {
      if (this.judgeListener != null) {
         this.judgeListener.judgeDataChanged();
      }
   }

   public Attic getAttic() {
      return this.attic;
   }

   public void setAttic(Attic var1) {
      this.attic = var1;
   }

   public boolean isRingSetRequired() {
      return this.ringSetRequired;
   }

   public void setRingSetRequired(boolean var1) {
      this.ringSetRequired = var1;
   }

   public boolean getHasMaxScore() {
      return this.hasMaxScore;
   }

   public void setHasMaxScore(boolean var1) {
      this.hasMaxScore = var1;
   }

   public int getMaxScore() {
      return this.maxScore;
   }

   public void setMaxScore(int var1) {
      this.maxScore = var1;
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
