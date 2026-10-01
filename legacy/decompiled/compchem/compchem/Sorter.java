package compchem;

import java.io.Serializable;

public class Sorter implements Serializable {
   static void QuickSort(int[] var0, int var1, int var2) throws Exception {
      int var3 = var1;
      int var4 = var2;
      if (var2 > var1) {
         int var5 = var0[(var1 + var2) / 2];

         while (var3 <= var4) {
            while (var3 < var2 && var0[var3] < var5) {
               var3++;
            }

            while (var4 > var1 && var0[var4] > var5) {
               var4--;
            }

            if (var3 <= var4) {
               swap(var0, var3, var4);
               var3++;
               var4--;
            }
         }

         if (var1 < var4) {
            QuickSort(var0, var1, var4);
         }

         if (var3 < var2) {
            QuickSort(var0, var3, var2);
         }
      }
   }

   private static void swap(int[] var0, int var1, int var2) {
      int var3 = var0[var1];
      var0[var1] = var0[var2];
      var0[var2] = var3;
   }

   public static void sort(int[] var0) throws Exception {
      QuickSort(var0, 0, var0.length - 1);
   }
}
