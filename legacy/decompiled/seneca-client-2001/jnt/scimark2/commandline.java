package jnt.scimark2;

public class commandline {
   public static void main(String[] var0) {
      double var1 = 2.0;
      int var3 = 1024;
      short var4 = 100;
      int var5 = 1000;
      int var6 = 5000;
      short var7 = 100;
      if (var0.length > 0) {
         if (var0[0].equalsIgnoreCase("-h") || var0[0].equalsIgnoreCase("-help")) {
            System.out.println("Usage: [-large] [minimum_time]");
            return;
         }

         int var8 = 0;
         if (var0[var8].equalsIgnoreCase("-large")) {
            var3 = 1048576;
            var4 = 1000;
            var5 = 100000;
            var6 = 1000000;
            var7 = 1000;
            var8++;
         }

         if (var0.length > var8) {
            var1 = Double.valueOf(var0[var8]);
         }
      }

      double[] var10 = new double[6];
      Random var9 = new Random(101010);
      var10[1] = kernel.measureFFT(var3, var1, var9);
      var10[2] = kernel.measureSOR(var4, var1, var9);
      var10[3] = kernel.measureMonteCarlo(var1, var9);
      var10[4] = kernel.measureSparseMatmult(var5, var6, var1, var9);
      var10[5] = kernel.measureLU(var7, var1, var9);
      var10[0] = (var10[1] + var10[2] + var10[3] + var10[4] + var10[5]) / 5.0;
      System.out.println("Composite Score: " + var10[0]);
      System.out.print("FFT (" + var3 + "): ");
      if (var10[1] == 0.0) {
         System.out.println(" ERROR, INVALID NUMERICAL RESULT!");
      } else {
         System.out.println(var10[1]);
      }

      System.out.println("SOR (" + var4 + "x" + var4 + "): " + "  " + var10[2]);
      System.out.println("Monte Carlo : " + var10[3]);
      System.out.println("Sparse matmult (N=" + var5 + ", nz=" + var6 + "): " + var10[4]);
      System.out.print("LU (" + var7 + "x" + var7 + "): ");
      if (var10[5] == 0.0) {
         System.out.println(" ERROR, INVALID NUMERICAL RESULT!");
      } else {
         System.out.println(var10[1]);
      }

      System.out.println();
      System.out.println("java.vendor: " + System.getProperty("java.vendor"));
      System.out.println("java.version: " + System.getProperty("java.version"));
      System.out.println("os.arch: " + System.getProperty("os.arch"));
      System.out.println("os.name: " + System.getProperty("os.name"));
      System.out.println("os.version: " + System.getProperty("os.version"));
   }
}
