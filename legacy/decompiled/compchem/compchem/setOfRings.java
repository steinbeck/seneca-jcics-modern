package compchem;

import java.io.Serializable;
import java.util.Vector;

public class setOfRings extends Vector implements Serializable {
   public boolean ringAlreadyInSet(Ring var1) {
      String var3 = var1.getSortedString();

      for (int var5 = 0; var5 < this.size(); var5++) {
         Ring var2 = (Ring)this.elementAt(var5);
         String var4 = var2.getSortedString();
         if (var3.equals(var4)) {
            return true;
         }
      }

      return false;
   }

   public void sort() {
      byte var1 = 0;
      int var2 = this.size() - 1;
      this.quickSort(var1, var2);
   }

   private void quickSort(int var1, int var2) {
      int var3 = var1;
      int var4 = var2;
      if (var2 > var1) {
         float var5 = ((Ring)this.elementAt((var1 + var2) / 2)).size();

         while (var3 <= var4) {
            while (var3 < var2 && ((Ring)this.elementAt(var3)).size() < var5) {
               var3++;
            }

            while (var4 > var1 && ((Ring)this.elementAt(var4)).size() > var5) {
               var4--;
            }

            if (var3 <= var4) {
               this.swap(var3, var4);
               var3++;
               var4--;
            }
         }

         if (var1 < var4) {
            this.quickSort(var1, var4);
         }

         if (var3 < var2) {
            this.quickSort(var3, var2);
         }
      }
   }

   private void swap(int var1, int var2) {
      Ring var3 = (Ring)this.elementAt(var1);
      this.setElementAt(this.elementAt(var2), var1);
      this.setElementAt(var3, var2);
   }

   public void reportRingList() {
      for (int var1 = 0; var1 < this.size(); var1++) {
         System.out.println(((Ring)this.elementAt(var1)).getString());
      }
   }

   public void makeNodeRingLists(Atom[] var1) {
      Object var2 = null;
      int var3 = -1;

      for (int var4 = 0; var4 < this.size(); var4++) {
         var2 = (Ring)this.elementAt(var4);

         for (int var5 = 0; var5 < var2.size(); var5++) {
            var3 = ((Atom)var2.elementAt(var5)).number;
            var1[var3].inRings[var1[var3].ringCounter] = var4;
            var1[var3].ringCounter++;
         }
      }
   }
}
