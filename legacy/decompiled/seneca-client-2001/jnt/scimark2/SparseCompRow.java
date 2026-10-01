package jnt.scimark2;

public class SparseCompRow {
   public static double num_flops(int var0, int var1, int var2) {
      int var3 = var1 / var0 * var0;
      return var3 * 2.0 * var2;
   }

   public static void matmult(double[] var0, double[] var1, int[] var2, int[] var3, double[] var4, int var5) {
      int var6 = var2.length - 1;

      for (int var7 = 0; var7 < var5; var7++) {
         for (int var8 = 0; var8 < var6; var8++) {
            double var9 = 0.0;
            int var11 = var2[var8];
            int var12 = var2[var8 + 1];

            for (int var13 = var11; var13 < var12; var13++) {
               var9 += var4[var3[var13]] * var1[var13];
            }

            var0[var8] = var9;
         }
      }
   }
}
