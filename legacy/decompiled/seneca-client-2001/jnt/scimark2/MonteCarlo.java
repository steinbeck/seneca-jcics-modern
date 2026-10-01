package jnt.scimark2;

public class MonteCarlo {
   static final int SEED = 113;

   public static final double num_flops(int var0) {
      return var0 * 4.0;
   }

   public static final double integrate(int var0) {
      Random var1 = new Random(113);
      int var2 = 0;

      for (int var3 = 0; var3 < var0; var3++) {
         double var4 = var1.nextDouble();
         double var6 = var1.nextDouble();
         if (var4 * var4 + var6 * var6 <= 1.0) {
            var2++;
         }
      }

      return (double)var2 / var0 * 4.0;
   }
}
