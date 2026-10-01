package compchem;

import java.io.Serializable;
import java.util.Vector;

public class RingSet extends Vector implements Serializable {
   public void sort() {
      byte var1 = 0;
      int var2 = this.size() - 1;
      this.quickSort(var1, var2);
   }

   private void quickSort(int var1, int var2) {
      int var3 = var1;
      int var4 = var2;
      if (var2 > var1) {
         float var5 = ((Path)this.elementAt((var1 + var2) / 2)).size();

         while (var3 <= var4) {
            while (var3 < var2 && ((Path)this.elementAt(var3)).size() < var5) {
               var3++;
            }

            while (var4 > var1 && ((Path)this.elementAt(var4)).size() > var5) {
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
      Path var3 = (Path)this.elementAt(var1);
      this.setElementAt(this.elementAt(var2), var1);
      this.setElementAt(var3, var2);
   }

   public void reportRingList() {
      for (int var1 = 0; var1 < this.size(); var1++) {
         System.out.println((Path)this.elementAt(var1));
      }
   }
}
