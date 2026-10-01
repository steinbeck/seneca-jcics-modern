package jnt.scimark2;

public class LU {
   private double[][] LU_;
   private int[] pivot_;

   public static final double num_flops(int var0) {
      double var1 = var0;
      return 2.0 * var1 * var1 * var1 / 3.0;
   }

   protected static double[] new_copy(double[] var0) {
      int var1 = var0.length;
      double[] var2 = new double[var1];

      for (int var3 = 0; var3 < var1; var3++) {
         var2[var3] = var0[var3];
      }

      return var2;
   }

   protected static double[][] new_copy(double[][] var0) {
      int var1 = var0.length;
      int var2 = var0[0].length;
      double[][] var3 = new double[var1][var2];

      for (int var4 = 0; var4 < var1; var4++) {
         double[] var5 = var3[var4];
         double[] var6 = var0[var4];

         for (int var7 = 0; var7 < var2; var7++) {
            var5[var7] = var6[var7];
         }
      }

      return var3;
   }

   public static int[] new_copy(int[] var0) {
      int var1 = var0.length;
      int[] var2 = new int[var1];

      for (int var3 = 0; var3 < var1; var3++) {
         var2[var3] = var0[var3];
      }

      return var2;
   }

   protected static final void insert_copy(double[][] var0, double[][] var1) {
      int var2 = var1.length;
      int var3 = var1[0].length;
      int var4 = var3 & 3;

      for (int var5 = 0; var5 < var2; var5++) {
         double[] var6 = var0[var5];
         double[] var7 = var1[var5];

         for (int var8 = 0; var8 < var4; var8++) {
            var6[var8] = var7[var8];
         }

         for (int var9 = var4; var9 < var3; var9 += 4) {
            var6[var9] = var7[var9];
            var6[var9 + 1] = var7[var9 + 1];
            var6[var9 + 2] = var7[var9 + 2];
            var6[var9 + 3] = var7[var9 + 3];
         }
      }
   }

   public double[][] getLU() {
      return new_copy(this.LU_);
   }

   public int[] getPivot() {
      return new_copy(this.pivot_);
   }

   public LU(double[][] var1) {
      int var2 = var1.length;
      int var3 = var1[0].length;
      this.LU_ = new double[var2][var3];
      insert_copy(this.LU_, var1);
      this.pivot_ = new int[var2];
      factor(this.LU_, this.pivot_);
   }

   public double[] solve(double[] var1) {
      double[] var2 = new_copy(var1);
      solve(this.LU_, this.pivot_, var2);
      return var2;
   }

   public static int factor(double[][] var0, int[] var1) {
      int var2 = var0.length;
      int var3 = var0[0].length;
      int var4 = Math.min(var3, var2);

      for (int var5 = 0; var5 < var4; var5++) {
         int var6 = var5;
         double var7 = Math.abs(var0[var5][var5]);

         for (int var9 = var5 + 1; var9 < var3; var9++) {
            double var10 = Math.abs(var0[var9][var5]);
            if (var10 > var7) {
               var6 = var9;
               var7 = var10;
            }
         }

         var1[var5] = var6;
         if (var0[var6][var5] == 0.0) {
            return 1;
         }

         if (var6 != var5) {
            double[] var16 = var0[var5];
            var0[var5] = var0[var6];
            var0[var6] = var16;
         }

         if (var5 < var3 - 1) {
            double var17 = 1.0 / var0[var5][var5];

            for (int var12 = var5 + 1; var12 < var3; var12++) {
               var0[var12][var5] = var0[var12][var5] * var17;
            }
         }

         if (var5 < var4 - 1) {
            for (int var18 = var5 + 1; var18 < var3; var18++) {
               double[] var11 = var0[var18];
               double[] var19 = var0[var5];
               double var13 = var11[var5];

               for (int var15 = var5 + 1; var15 < var2; var15++) {
                  var11[var15] -= var13 * var19[var15];
               }
            }
         }
      }

      return 0;
   }

   public static void solve(double[][] var0, int[] var1, double[] var2) {
      int var3 = var0.length;
      int var4 = var0[0].length;
      int var5 = 0;

      for (int var6 = 0; var6 < var3; var6++) {
         int var7 = var1[var6];
         double var8 = var2[var7];
         var2[var7] = var2[var6];
         if (var5 == 0) {
            for (int var10 = var5; var10 < var6; var10++) {
               var8 -= var0[var6][var10] * var2[var10];
            }
         } else if (var8 == 0.0) {
            var5 = var6;
         }

         var2[var6] = var8;
      }

      for (int var11 = var4 - 1; var11 >= 0; var11--) {
         double var12 = var2[var11];

         for (int var13 = var11 + 1; var13 < var4; var13++) {
            var12 -= var0[var11][var13] * var2[var13];
         }

         var2[var11] = var12 / var0[var11][var11];
      }
   }
}
