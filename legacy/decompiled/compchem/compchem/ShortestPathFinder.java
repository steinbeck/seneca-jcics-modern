package compchem;

import java.io.Serializable;

public class ShortestPathFinder implements Serializable {
   public void computeFloydAPSP(int[][] var1, int[][] var2) {
      int var6 = var1.length;

      for (int var3 = 0; var3 < var6; var3++) {
         for (int var4 = 0; var4 < var6; var4++) {
            if (var2[var3][var4] == 0) {
               var1[var3][var4] = 999999999;
            } else {
               var1[var3][var4] = 1;
            }
         }
      }

      for (int var7 = 0; var7 < var6; var7++) {
         var1[var7][var7] = 0;
      }

      for (int var5 = 0; var5 < var6; var5++) {
         for (int var8 = 0; var8 < var6; var8++) {
            for (int var9 = 0; var9 < var6; var9++) {
               if (var1[var8][var5] + var1[var5][var9] < var1[var8][var9]) {
                  var1[var8][var9] = var1[var8][var5] + var1[var5][var9];
               }
            }
         }
      }
   }

   public int[][] computeFloydAPSP(int[][] var1) {
      int var5 = var1.length;
      int[][] var6 = new int[var5][var5];

      for (int var2 = 0; var2 < var5; var2++) {
         for (int var3 = 0; var3 < var5; var3++) {
            if (var1[var2][var3] == 0) {
               var6[var2][var3] = 999999999;
            } else {
               var6[var2][var3] = 1;
            }
         }
      }

      for (int var7 = 0; var7 < var5; var7++) {
         var6[var7][var7] = 0;
      }

      for (int var4 = 0; var4 < var5; var4++) {
         for (int var8 = 0; var8 < var5; var8++) {
            for (int var9 = 0; var9 < var5; var9++) {
               if (var6[var8][var4] + var6[var4][var9] < var6[var8][var9]) {
                  var6[var8][var9] = var6[var8][var4] + var6[var4][var9];
               }
            }
         }
      }

      return var6;
   }
}
