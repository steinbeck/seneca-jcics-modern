package seneca.structgen;

import java.io.Serializable;

public class AnnealingLog implements Serializable, Cloneable {
   int startsize = 100;
   int growsize = 100;
   int currentsize = this.startsize;
   int seriesCount = 1;
   int typeCount = 2;
   Float[][][] values = new Float[this.seriesCount][this.currentsize][this.typeCount + 1];
   static int SCORES = 0;
   static int TEMPERATURES = 1;
   static int ITERATION = 0;
   static int Y = 1;
   int[] itemCount;
   int iterationAxisStepsize = 500;

   public AnnealingLog() {
      this(1, 2);
   }

   public AnnealingLog(int var1, int var2) {
      this.seriesCount = var1;
      this.typeCount = var2;
      this.values = new Float[var1][this.currentsize][var2 + 1];
      this.itemCount = new int[var1];
   }

   public void addEntry(float var1, float var2) {
      this.addEntry(this.itemCount[0] * this.iterationAxisStepsize, var1, var2);
   }

   public void addEntry(float var1, float var2, float var3) {
      this.addEntry(new Float(var1), new Float(var2), new Float(var3));
   }

   public void addEntry(Float var1, Float var2, Float var3) {
      Float[] var4 = new Float[]{var1, var3, var2};
      this.addEntry(0, this.itemCount[0], var4);
   }

   public void addEntry(int var1, int var2, Float[] var3) {
      if (var2 >= this.currentsize) {
         this.growArrays();
      }

      for (int var4 = 0; var4 < var3.length; var4++) {
         this.values[var1][var2][var4] = var3[var4];
      }

      this.itemCount[var1]++;
   }

   public Float[] getEntry(int var1, int var2) {
      Float[] var3 = new Float[this.typeCount + 1];

      for (int var4 = 0; var4 < this.typeCount + 1; var4++) {
         var3[var4] = this.values[var1][var2][var4];
      }

      return var3;
   }

   void growArrays() {
      int var1 = this.currentsize;
      this.currentsize = this.currentsize + this.growsize;
      Float[][][] var2 = new Float[this.seriesCount][this.currentsize][this.typeCount + 1];

      for (int var3 = 0; var3 < this.seriesCount; var3++) {
         for (int var4 = 0; var4 < var1; var4++) {
            for (int var5 = 0; var5 < this.typeCount + 1; var5++) {
               var2[var3][var4][var5] = this.values[var3][var4][var5];
            }
         }
      }

      this.values = var2;
   }

   public Float[][][] getValues() {
      Float[][][] var1 = new Float[this.seriesCount][this.maxItemCount()][this.typeCount + 1];

      for (int var2 = 0; var2 < this.seriesCount; var2++) {
         for (int var3 = 0; var3 < this.itemCount[var2]; var3++) {
            for (int var4 = 0; var4 < this.typeCount + 1; var4++) {
               var1[var2][var3][var4] = this.values[var2][var3][var4];
            }
         }
      }

      return var1;
   }

   public int getGrowsize() {
      return this.growsize;
   }

   public void setGrowsize(int var1) {
      this.growsize = var1;
   }

   public int getStartsize() {
      return this.startsize;
   }

   public void setStartsize(int var1) {
      this.startsize = var1;
   }

   public int getIterationAxisStepsize() {
      return this.iterationAxisStepsize;
   }

   public void setIterationAxisStepsize(int var1) {
      this.iterationAxisStepsize = var1;
   }

   public int getItemCount(int var1) {
      return this.itemCount[var1];
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

   public int getSeriesCount() {
      return this.seriesCount;
   }

   public void setSeriesCount(int var1) {
      this.seriesCount = var1;
   }

   public int getTypeCount() {
      return this.typeCount;
   }

   public void setTypeCount(int var1) {
      this.typeCount = var1;
   }

   public int maxItemCount() {
      int var1 = 0;

      for (int var2 = 0; var2 < this.itemCount.length; var2++) {
         if (this.itemCount[var2] > var1) {
            var1 = this.itemCount[var2];
         }
      }

      return var1;
   }

   public String toString() {
      String var1 = "Annealing Log -> ";

      for (int var2 = 0; var2 < this.seriesCount; var2++) {
         var1 = var1 + "Series " + var2 + ": ";

         for (int var3 = 0; var3 < this.itemCount[var2]; var3++) {
            Object var6 = var1 + var3 + "[ ";

            for (int var4 = 0; var4 < this.typeCount + 1; var4++) {
               var6 = var6 + this.values[var2][var3][var4] + " ";
            }

            var1 = var6 + "] ";
         }

         var1 = var1 + "\n";
      }

      return var1;
   }
}
