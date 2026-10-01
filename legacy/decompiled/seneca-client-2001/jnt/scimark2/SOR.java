package jnt.scimark2;

public class SOR {
   public static final double num_flops(int var0, int var1, int var2) {
      double var3 = var0;
      double var5 = var1;
      double var7 = var2;
      return (var3 - 1.0) * (var5 - 1.0) * var7 * 6.0;
   }

   public static final void execute(double var0, double[][] var2, int var3) {
      int var4 = var2.length;
      int var5 = var2[0].length;
      double var6 = var0 * 0.25;
      double var8 = 1.0 - var0;
      int var10 = var4 - 1;
      int var11 = var5 - 1;

      for (int var12 = 0; var12 < var3; var12++) {
         for (int var13 = 1; var13 < var10; var13++) {
            double[] var14 = var2[var13];
            double[] var15 = var2[var13 - 1];
            double[] var16 = var2[var13 + 1];

            for (int var17 = 1; var17 < var11; var17++) {
               var14[var17] = var6 * (var15[var17] + var16[var17] + var14[var17 - 1] + var14[var17 + 1]) + var8 * var14[var17];
            }
         }
      }
   }
}
