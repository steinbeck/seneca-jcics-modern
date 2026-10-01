package jnt.scimark2;

public class FFT {
   public static final double num_flops(int var0) {
      double var1 = var0;
      double var3 = log2(var0);
      return (5.0 * var1 - 2.0) * var3 + 2.0 * (var1 + 1.0);
   }

   public static void transform(double[] var0) {
      transform_internal(var0, -1);
   }

   public static void inverse(double[] var0) {
      transform_internal(var0, 1);
      int var1 = var0.length;
      int var2 = var1 / 2;
      double var3 = 1.0 / var2;

      for (int var5 = 0; var5 < var1; var5++) {
         var0[var5] *= var3;
      }
   }

   public static double test(double[] var0) {
      int var1 = var0.length;
      double[] var2 = new double[var1];
      System.arraycopy(var0, 0, var2, 0, var1);
      transform(var0);
      inverse(var0);
      double var3 = 0.0;

      for (int var5 = 0; var5 < var1; var5++) {
         double var6 = var0[var5] - var2[var5];
         var3 += var6 * var6;
      }

      return Math.sqrt(var3 / var1);
   }

   public static double[] makeRandom(int var0) {
      int var1 = 2 * var0;
      double[] var2 = new double[var1];

      for (int var3 = 0; var3 < var1; var3++) {
         var2[var3] = Math.random();
      }

      return var2;
   }

   public static void main(String[] var0) {
      if (var0.length == 0) {
         short var1 = 1024;
         System.out.println("n=" + var1 + " => RMS Error=" + test(makeRandom(var1)));
      }

      for (int var3 = 0; var3 < var0.length; var3++) {
         int var2 = Integer.parseInt(var0[var3]);
         System.out.println("n=" + var2 + " => RMS Error=" + test(makeRandom(var2)));
      }
   }

   protected static int log2(int var0) {
      int var1 = 0;

      for (byte var2 = 1; var2 < var0; var1++) {
         var2 *= 2;
      }

      if (var0 != 1 << var1) {
         throw new Error("FFT: Data length is not a power of 2!: " + var0);
      } else {
         return var1;
      }
   }

   protected static void transform_internal(double[] var0, int var1) {
      if (var0.length != 0) {
         int var2 = var0.length / 2;
         if (var2 != 1) {
            int var3 = log2(var2);
            bitreverse(var0);
            int var4 = 0;

            for (byte var5 = 1; var4 < var3; var5 *= 2) {
               double var6 = 1.0;
               double var8 = 0.0;
               double var10 = 2.0 * var1 * Math.PI / (2.0 * var5);
               double var12 = Math.sin(var10);
               double var14 = Math.sin(var10 / 2.0);
               double var16 = 2.0 * var14 * var14;

               for (int var18 = 0; var18 < var2; var18 += 2 * var5) {
                  int var19 = 2 * var18;
                  int var20 = 2 * (var18 + var5);
                  double var21 = var0[var20];
                  double var23 = var0[var20 + 1];
                  var0[var20] = var0[var19] - var21;
                  var0[var20 + 1] = var0[var19 + 1] - var23;
                  var0[var19] += var21;
                  var0[var19 + 1] = var0[var19 + 1] + var23;
               }

               for (int var31 = 1; var31 < var5; var31++) {
                  double var32 = var6 - var12 * var8 - var16 * var6;
                  double var22 = var8 + var12 * var6 - var16 * var8;
                  var6 = var32;
                  var8 = var22;

                  for (int var33 = 0; var33 < var2; var33 += 2 * var5) {
                     int var34 = 2 * (var33 + var31);
                     int var35 = 2 * (var33 + var31 + var5);
                     double var36 = var0[var35];
                     double var25 = var0[var35 + 1];
                     double var27 = var6 * var36 - var8 * var25;
                     double var29 = var6 * var25 + var8 * var36;
                     var0[var35] = var0[var34] - var27;
                     var0[var35 + 1] = var0[var34 + 1] - var29;
                     var0[var34] += var27;
                     var0[var34 + 1] = var0[var34 + 1] + var29;
                  }
               }

               var4++;
            }
         }
      }
   }

   protected static void bitreverse(double[] var0) {
      int var1 = var0.length / 2;
      int var2 = var1 - 1;
      int var3 = 0;

      for (int var4 = 0; var3 < var2; var3++) {
         int var5 = var3 << 1;
         int var6 = var4 << 1;
         int var7 = var1 >> 1;
         if (var3 < var4) {
            double var8 = var0[var5];
            double var10 = var0[var5 + 1];
            var0[var5] = var0[var6];
            var0[var5 + 1] = var0[var6 + 1];
            var0[var6] = var8;
            var0[var6 + 1] = var10;
         }

         while (var7 <= var4) {
            var4 -= var7;
            var7 >>= 1;
         }

         var4 += var7;
      }
   }
}
