package compchem;

import java.io.Serializable;
import java.util.Arrays;

public class IsomorphismTester implements Serializable {
   int[] baseTable;
   int[] sortedBaseTable;
   int[] compareTable;
   int[] sortedCompareTable;
   Atom[] baseNodes;
   Atom[] compareNodes;

   public IsomorphismTester() {
   }

   public IsomorphismTester(Atom[] var1) {
      this.setBaseTable(var1);
   }

   public boolean isIsomorph(Atom[] var1, Atom[] var2) {
      this.setBaseTable(var1);
      return this.isIsomorph(var2);
   }

   public boolean isIsomorph(Atom[] var1) {
      this.setCompareTable(var1);

      for (int var3 = 0; var3 < this.sortedBaseTable.length; var3++) {
         if (this.sortedBaseTable[var3] != this.sortedCompareTable[var3]) {
            return false;
         }
      }

      for (int var4 = 0; var4 < this.baseTable.length; var4++) {
         boolean var2 = false;

         for (int var5 = 0; var5 < this.compareTable.length; var5++) {
            if (this.baseTable[var4] == this.compareTable[var5]) {
               if (!this.baseNodes[var4].symbol.equals(this.compareNodes[var5].symbol) || this.baseNodes[var4].HCount != this.compareNodes[var5].HCount) {
                  return false;
               }

               var2 = true;
            }
         }

         if (!var2) {
            return false;
         }
      }

      return true;
   }

   private void setBaseTable(Atom[] var1) {
      this.baseNodes = var1;
      this.baseTable = DataStructureTools.computeMorganMatrix(this.baseNodes);
      this.sortedBaseTable = new int[this.baseTable.length];
      System.arraycopy(this.baseTable, 0, this.sortedBaseTable, 0, this.baseTable.length);
      Arrays.sort(this.sortedBaseTable);
   }

   private void setCompareTable(Atom[] var1) {
      this.compareNodes = var1;
      this.compareTable = DataStructureTools.computeMorganMatrix(this.compareNodes);
      this.sortedCompareTable = new int[this.compareTable.length];
      System.arraycopy(this.compareTable, 0, this.sortedCompareTable, 0, this.compareTable.length);
      Arrays.sort(this.sortedCompareTable);
   }

   public void report() {
      String var1 = "";

      for (int var2 = 0; var2 < this.sortedBaseTable.length; var2++) {
         var1 = var1 + this.sortedBaseTable[var2] + " ";
      }

      System.out.println(var1);
      var1 = "";

      for (int var3 = 0; var3 < this.sortedCompareTable.length; var3++) {
         var1 = var1 + this.sortedCompareTable[var3] + " ";
      }

      System.out.println(var1);
   }
}
