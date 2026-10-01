package jnt.scimark2;

public class kernel {
   public static double measureFFT(int var0, double var1, Random var3) {
      double[] var4 = RandomVector(2 * var0, var3);
      double[] var5 = NewVectorCopy(var4);
      long var6 = 1L;
      Stopwatch var8 = new Stopwatch();

      while (true) {
         var8.start();

         for (int var9 = 0; var9 < var6; var9++) {
            FFT.transform(var4);
            FFT.inverse(var4);
         }

         var8.stop();
         if (var8.read() >= var1) {
            if (FFT.test(var4) / var0 > 1.0E-10) {
               return 0.0;
            }

            return FFT.num_flops(var0) * var6 / var8.read() * 1.0E-6;
         }

         var6 *= 2L;
      }
   }

   public static double measureSOR(int var0, double var1, Random var3) {
      double[][] var4 = RandomMatrix(var0, var0, var3);
      Stopwatch var5 = new Stopwatch();
      byte var6 = 1;

      while (true) {
         var5.start();
         SOR.execute(1.25, var4, var6);
         var5.stop();
         if (var5.read() >= var1) {
            return SOR.num_flops(var0, var0, var6) / var5.read() * 1.0E-6;
         }

         var6 *= 2;
      }
   }

   public static double measureMonteCarlo(double var0, Random var2) {
      Stopwatch var3 = new Stopwatch();
      byte var4 = 1;

      while (true) {
         var3.start();
         MonteCarlo.integrate(var4);
         var3.stop();
         if (var3.read() >= var0) {
            return MonteCarlo.num_flops(var4) / var3.read() * 1.0E-6;
         }

         var4 *= 2;
      }
   }

   public static double measureSparseMatmult(int var0, int var1, double var2, Random var4) {
      double[] var5 = RandomVector(var0, var4);
      double[] var6 = new double[var0];
      int var7 = var1 / var0;
      int var8 = var7 * var0;
      double[] var9 = RandomVector(var8, var4);
      int[] var10 = new int[var8];
      int[] var11 = new int[var0 + 1];
      var11[0] = 0;

      for (int var12 = 0; var12 < var0; var12++) {
         int var13 = var11[var12];
         var11[var12 + 1] = var13 + var7;
         int var14 = var12 / var7;
         if (var14 < 1) {
            var14 = 1;
         }

         for (int var15 = 0; var15 < var7; var15++) {
            var10[var13 + var15] = var15 * var14;
         }
      }

      Stopwatch var16 = new Stopwatch();
      byte var17 = 1;

      while (true) {
         var16.start();
         SparseCompRow.matmult(var6, var9, var11, var10, var5, var17);
         var16.stop();
         if (var16.read() >= var2) {
            return SparseCompRow.num_flops(var0, var1, var17) / var16.read() * 1.0E-6;
         }

         var17 *= 2;
      }
   }

   public static double measureLU(int var0, double var1, Random var3) {
      double[][] var4 = RandomMatrix(var0, var0, var3);
      double[][] var5 = new double[var0][var0];
      int[] var6 = new int[var0];
      Stopwatch var7 = new Stopwatch();
      byte var8 = 1;

      while (true) {
         var7.start();

         for (int var9 = 0; var9 < var8; var9++) {
            CopyMatrix(var5, var4);
            LU.factor(var5, var6);
         }

         var7.stop();
         if (var7.read() >= var1) {
            double[] var13 = RandomVector(var0, var3);
            double[] var10 = NewVectorCopy(var13);
            LU.solve(var5, var6, var10);
            if (normabs(var13, matvec(var4, var10)) / var0 > 1.0E-12) {
               return 0.0;
            }

            return LU.num_flops(var0) * var8 / var7.read() * 1.0E-6;
         }

         var8 *= 2;
      }
   }

   private static double[] NewVectorCopy(double[] var0) {
      int var1 = var0.length;
      double[] var2 = new double[var1];

      for (int var3 = 0; var3 < var1; var3++) {
         var2[var3] = var0[var3];
      }

      return var2;
   }

   private static void CopyVector(double[] var0, double[] var1) {
      int var2 = var1.length;

      for (int var3 = 0; var3 < var2; var3++) {
         var0[var3] = var1[var3];
      }
   }

   private static double normabs(double[] var0, double[] var1) {
      int var2 = var0.length;
      double var3 = 0.0;

      for (int var5 = 0; var5 < var2; var5++) {
         var3 += Math.abs(var0[var5] - var1[var5]);
      }

      return var3;
   }

   private static void CopyMatrix(double[][] var0, double[][] var1) {
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

   private static double[][] RandomMatrix(int var0, int var1, Random var2) {
      double[][] var3 = new double[var0][var1];

      for (int var4 = 0; var4 < var1; var4++) {
         for (int var5 = 0; var5 < var1; var5++) {
            var3[var4][var5] = var2.nextDouble();
         }
      }

      return var3;
   }

   private static double[] RandomVector(int var0, Random var1) {
      double[] var2 = new double[var0];

      for (int var3 = 0; var3 < var0; var3++) {
         var2[var3] = var1.nextDouble();
      }

      return var2;
   }

   private static double[] matvec(double[][] var0, double[] var1) {
      int var2 = var1.length;
      double[] var3 = new double[var2];
      matvec(var0, var1, var3);
      return var3;
   }

   private static void matvec(double[][] var0, double[] var1, double[] var2) {
      int var3 = var0.length;
      int var4 = var0[0].length;

      for (int var5 = 0; var5 < var3; var5++) {
         double var6 = 0.0;
         double[] var8 = var0[var5];

         for (int var9 = 0; var9 < var4; var9++) {
            var6 += var8[var9] * var1[var9];
         }

         var2[var5] = var6;
      }
   }
}
