package seneca.engine;

import java.io.Serializable;
import jnt.scimark2.Random;
import jnt.scimark2.kernel;

public class benchmarker implements Serializable {
   public static long getBenchmark() {
      double var0 = 2.0;
      short var2 = 1024;
      byte var3 = 100;
      short var4 = 1000;
      short var5 = 5000;
      byte var6 = 100;
      double[] var7 = new double[6];
      Random var8 = new Random(101010);
      var7[1] = kernel.measureFFT(var2, var0, var8);
      var7[2] = kernel.measureSOR(var3, var0, var8);
      var7[3] = kernel.measureMonteCarlo(var0, var8);
      var7[4] = kernel.measureSparseMatmult(var4, var5, var0, var8);
      var7[5] = kernel.measureLU(var6, var0, var8);
      var7[0] = (var7[1] + var7[2] + var7[3] + var7[4] + var7[5]) / 5.0;
      return (long)var7[0];
   }
}
