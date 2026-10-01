package jnt.scimark2;

public class Random {
   int seed = 0;
   private int[] m;
   private int i = 4;
   private int j = 16;
   private final int mdig = 32;
   private final int one = 1;
   private final int m1 = Integer.MAX_VALUE;
   private final int m2 = 65536;
   private double dm1 = 4.656612875245797E-10;
   private boolean haveRange = false;
   private double left = 0.0;
   private double right = 1.0;
   private double width = 1.0;

   public Random() {
      this.initialize((int)System.currentTimeMillis());
   }

   public Random(double var1, double var3) {
      this.initialize((int)System.currentTimeMillis());
      this.left = var1;
      this.right = var3;
      this.width = var3 - var1;
      this.haveRange = true;
   }

   public Random(int var1) {
      this.initialize(var1);
   }

   public Random(int var1, double var2, double var4) {
      this.initialize(var1);
      this.left = var2;
      this.right = var4;
      this.width = var4 - var2;
      this.haveRange = true;
   }

   public final synchronized double nextDouble() {
      int var1 = this.m[this.i] - this.m[this.j];
      if (var1 < 0) {
         var1 += Integer.MAX_VALUE;
      }

      this.m[this.j] = var1;
      if (this.i == 0) {
         this.i = 16;
      } else {
         this.i--;
      }

      if (this.j == 0) {
         this.j = 16;
      } else {
         this.j--;
      }

      return this.haveRange ? this.left + this.dm1 * var1 * this.width : this.dm1 * var1;
   }

   public final synchronized void nextDoubles(double[] var1) {
      int var2 = var1.length;
      int var3 = var2 & 3;
      if (this.haveRange) {
         for (int var4 = 0; var4 < var2; var4++) {
            int var5 = this.m[this.i] - this.m[this.j];
            if (this.i == 0) {
               this.i = 16;
            } else {
               this.i--;
            }

            if (var5 < 0) {
               var5 += Integer.MAX_VALUE;
            }

            this.m[this.j] = var5;
            if (this.j == 0) {
               this.j = 16;
            } else {
               this.j--;
            }

            var1[var4] = this.left + this.dm1 * var5 * this.width;
         }
      } else {
         for (int var7 = 0; var7 < var3; var7++) {
            int var8 = this.m[this.i] - this.m[this.j];
            if (this.i == 0) {
               this.i = 16;
            } else {
               this.i--;
            }

            if (var8 < 0) {
               var8 += Integer.MAX_VALUE;
            }

            this.m[this.j] = var8;
            if (this.j == 0) {
               this.j = 16;
            } else {
               this.j--;
            }

            var1[var7] = this.dm1 * var8;
         }

         for (int var9 = var3; var9 < var2; var9 += 4) {
            int var6 = this.m[this.i] - this.m[this.j];
            if (this.i == 0) {
               this.i = 16;
            } else {
               this.i--;
            }

            if (var6 < 0) {
               var6 += Integer.MAX_VALUE;
            }

            this.m[this.j] = var6;
            if (this.j == 0) {
               this.j = 16;
            } else {
               this.j--;
            }

            var1[var9] = this.dm1 * var6;
            var6 = this.m[this.i] - this.m[this.j];
            if (this.i == 0) {
               this.i = 16;
            } else {
               this.i--;
            }

            if (var6 < 0) {
               var6 += Integer.MAX_VALUE;
            }

            this.m[this.j] = var6;
            if (this.j == 0) {
               this.j = 16;
            } else {
               this.j--;
            }

            var1[var9 + 1] = this.dm1 * var6;
            var6 = this.m[this.i] - this.m[this.j];
            if (this.i == 0) {
               this.i = 16;
            } else {
               this.i--;
            }

            if (var6 < 0) {
               var6 += Integer.MAX_VALUE;
            }

            this.m[this.j] = var6;
            if (this.j == 0) {
               this.j = 16;
            } else {
               this.j--;
            }

            var1[var9 + 2] = this.dm1 * var6;
            var6 = this.m[this.i] - this.m[this.j];
            if (this.i == 0) {
               this.i = 16;
            } else {
               this.i--;
            }

            if (var6 < 0) {
               var6 += Integer.MAX_VALUE;
            }

            this.m[this.j] = var6;
            if (this.j == 0) {
               this.j = 16;
            } else {
               this.j--;
            }

            var1[var9 + 3] = this.dm1 * var6;
         }
      }
   }

   private void initialize(int var1) {
      this.seed = var1;
      this.m = new int[17];
      int var2 = Math.min(Math.abs(var1), Integer.MAX_VALUE);
      if (var2 % 2 == 0) {
         var2--;
      }

      short var3 = 9069;
      byte var4 = 0;
      int var5 = var2 % 65536;
      int var6 = var2 / 65536;

      for (int var7 = 0; var7 < 17; var7++) {
         var2 = var5 * var3;
         var6 = (var2 / 65536 + var5 * var4 + var6 * var3) % 32768;
         var5 = var2 % 65536;
         this.m[var7] = var5 + 65536 * var6;
      }

      this.i = 4;
      this.j = 16;
   }
}
