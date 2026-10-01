package compchem;

import java.util.Vector;

public class PathUtils {
   public static int getPathLength(Atom[] var0, Atom var1, Atom var2, int var3) {
      Vector var4 = new Vector();
      byte var5 = 0;
      boolean[] var6 = new boolean[var0.length];
      var6[var1.number] = true;
      var4.addElement(var1);
      return nextSphere(var0, var4, var6, var5, var2, var3);
   }

   public static boolean isConnected(Atom[] var0) {
      Vector var1 = new Vector();
      byte var2 = 0;
      boolean[] var3 = new boolean[var0.length];
      var3[0] = true;
      var1.addElement(var0[0]);
      int var4 = nextSphere(var0, var1, var3, var2);
      boolean var5 = false;

      for (int var6 = 0; var6 < var3.length; var6++) {
         if (!var3[var6]) {
            return false;
         }
      }

      return true;
   }

   public static Vector partitionMolecules(Atom[] var0) {
      Vector var1 = new Vector();
      Vector var2 = new Vector();
      Vector var3 = new Vector();

      for (int var4 = 0; var4 < var0.length; var4++) {
         var2.addElement(var0[var4]);
      }

      byte var6 = 0;
      boolean[] var9 = new boolean[var0.length];
      boolean[] var10 = new boolean[var0.length];
      var9[0] = true;
      System.out.println("Atoms in model: " + var9.length);

      do {
         var1.addElement(var2.elementAt(0));
         Molecule var8 = new Molecule();
         int var5 = nextSphere(var0, var1, var9, var6);
         System.out.println("Molecule: " + var3.size());

         for (int var11 = 0; var11 < var9.length; var11++) {
            if (var9[var11] && !var10[var11]) {
               var10[var11] = true;
               System.out.println(var11);
               var8.addAtom(var0[var11]);
               var2.removeElement(var0[var11]);
            }
         }

         var3.addElement(var8);
      } while (var2.size() > 0);

      System.out.println("Number of Molecules in Model: " + var3.size());
      return var3;
   }

   private static int nextSphere(Atom[] var0, Vector var1, boolean[] var2, int var3, Atom var4, int var5) {
      if (++var3 > var5) {
         return -1;
      } else {
         Vector var7 = new Vector();

         for (int var8 = 0; var8 < var1.size(); var8++) {
            Atom var6 = (Atom)var1.elementAt(var8);

            for (int var9 = 0; var9 < var6.degree; var9++) {
               if (!var2[var6.nodeTable[var9].number]) {
                  if (var6.nodeTable[var9] == var4) {
                     return var3;
                  }

                  var7.addElement(var6.nodeTable[var9]);
                  var2[var6.nodeTable[var9].number] = true;
               }
            }
         }

         return nextSphere(var0, var7, var2, var3, var4, var5);
      }
   }

   private static int nextSphere(Atom[] var0, Vector var1, boolean[] var2, int var3) {
      var3++;
      Vector var5 = new Vector();

      for (int var6 = 0; var6 < var1.size(); var6++) {
         Atom var4 = (Atom)var1.elementAt(var6);

         for (int var7 = 0; var7 < var4.degree; var7++) {
            if (!var2[var4.nodeTable[var7].number]) {
               var5.addElement(var4.nodeTable[var7]);
               var2[var4.nodeTable[var7].number] = true;
            }
         }
      }

      return var1.size() == 0 ? var3 : nextSphere(var0, var5, var2, var3);
   }
}
