package compchem;

import java.io.Serializable;
import java.util.Vector;

public class Ring extends Vector implements Serializable {
   public int status = 0;
   public static final int PLACEMENT_NONE = 0;
   public static final int PLACEMENT_PRELIM = 1;
   public static final int PLACEMENT_FINAL = 2;
   public double x;
   public double y;
   public boolean aromatic = false;

   public String getSortedString() {
      String var1 = "";
      int[] var2 = new int[this.size()];

      for (int var3 = 0; var3 < this.size(); var3++) {
         var2[var3] = ((Atom)this.elementAt(var3)).number;
      }

      this.quickSort(var2, 0, var2.length - 1);

      for (int var4 = 0; var4 < this.size() - 1; var4++) {
         var1 = var1 + var2[var4] + "-";
      }

      return var1 + var2[this.size() - 1];
   }

   public String getString() {
      String var1 = "";
      int[] var2 = new int[this.size()];

      for (int var3 = 0; var3 < this.size(); var3++) {
         var2[var3] = ((Atom)this.elementAt(var3)).number;
      }

      for (int var4 = 0; var4 < this.size() - 1; var4++) {
         var1 = var1 + var2[var4] + "-";
      }

      return var1 + var2[this.size() - 1];
   }

   public boolean isInRing(int var1) {
      for (int var2 = 0; var2 < this.size(); var2++) {
         if (((Atom)this.elementAt(var2)).number == var1) {
            return true;
         }
      }

      return false;
   }

   private void quickSort(int[] var1, int var2, int var3) {
      int var4 = var2;
      int var5 = var3;
      if (var3 > var2) {
         float var6 = var1[(var2 + var3) / 2];

         while (var4 <= var5) {
            while (var4 < var3 && var1[var4] < var6) {
               var4++;
            }

            while (var5 > var2 && var1[var5] > var6) {
               var5--;
            }

            if (var4 <= var5) {
               this.swap(var1, var4, var5);
               var4++;
               var5--;
            }
         }

         if (var2 < var5) {
            this.quickSort(var1, var2, var5);
         }

         if (var4 < var3) {
            this.quickSort(var1, var4, var3);
         }
      }
   }

   private void swap(int[] var1, int var2, int var3) {
      int var4 = var1[var2];
      var1[var2] = var1[var3];
      var1[var3] = var4;
   }

   public Bond getEdge(int var1) {
      if (var1 < this.size() - 1) {
         Atom var4 = (Atom)this.elementAt(var1);
         Atom var5 = (Atom)this.elementAt(var1 + 1);
         return new Bond(var4, var5);
      } else {
         Atom var2 = (Atom)this.elementAt(0);
         Atom var3 = (Atom)this.elementAt(this.size() - 1);
         return new Bond(var2, var3);
      }
   }

   public void sort() {
      int var1 = ((Atom)this.elementAt(0)).number;
      int var2 = 0;
      int var3 = this.size();

      for (int var4 = 1; var4 < var3; var4++) {
         if (((Atom)this.elementAt(var4)).number < var1) {
            var1 = ((Atom)this.elementAt(var4)).number;
            var2 = var4;
         }
      }

      if (var2 == var3 - 1) {
         this.shiftLeft(1);
         var2--;
      } else if (var2 == 0) {
         this.shiftRight(1);
         var2++;
      }

      if (((Atom)this.elementAt(var2 + 1)).number < ((Atom)this.elementAt(var2 - 1)).number) {
         this.shiftLeft(var2);
      } else {
         this.shiftRight(this.size() - 1 - var2);
         this.invert();
      }
   }

   public void sort(int var1) {
      for (int var2 = ((Atom)this.elementAt(0)).number; var2 != var1; var2 = ((Atom)this.elementAt(0)).number) {
         this.shiftLeft(1);
      }
   }

   public void shiftLeft(int var1) {
      for (int var2 = 0; var2 < var1; var2++) {
         Atom var3 = (Atom)this.elementAt(0);
         this.removeElementAt(0);
         this.addElement(var3);
      }
   }

   public void shiftRight(int var1) {
      for (int var2 = 0; var2 < var1; var2++) {
         Atom var3 = (Atom)this.elementAt(this.size() - 1);
         this.removeElementAt(this.size() - 1);
         this.insertElementAt(var3, 0);
      }
   }

   public void invert() {
      int var1 = this.size();

      for (int var2 = 0; var2 < var1; var2++) {
         this.insertElementAt((Atom)this.elementAt(this.size() - 1 - var2), var2);
      }

      this.setSize(var1);
   }

   public int getComplexity(Atom[] var1) {
      int var2 = 0;

      for (int var3 = 0; var3 < this.size(); var3++) {
         var2 += var1[((Atom)this.elementAt(var3)).number].ringCounter;
      }

      return var2;
   }

   public boolean notAllPositioned(Atom[] var1) {
      for (int var3 = 0; var3 < this.size(); var3++) {
         if (var1[((Atom)this.elementAt(var3)).number].status < 1) {
            return true;
         }
      }

      return false;
   }

   public Vector getNodeNumberList() {
      Vector var1 = new Vector();

      for (int var2 = 0; var2 < this.size(); var2++) {
         var1.addElement(new Integer(((Atom)this.elementAt(var2)).number));
      }

      return var1;
   }

   public double getRadius(double var1) {
      return Math.sqrt(Math.pow(var1, 2.0) / (2.0 - 2.0 * Math.cos(this.getSectorAngle())));
   }

   public double getSectorAngle() {
      double var1 = 360 / this.size();
      return (Math.PI / 180.0) * var1;
   }

   public int getNodeNumber(int var1) {
      return ((Atom)this.elementAt(var1)).number;
   }
}
