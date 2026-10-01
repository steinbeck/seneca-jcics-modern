package compchem;

import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Hashtable;
import java.util.Vector;

public class DataStructureTools {
   public static boolean debug = false;
   public static boolean debug1 = false;
   public static boolean debug2 = false;
   public static boolean debug3 = false;
   public static int INFINITY = 1000000;

   /** @deprecated */
   public static String analyseSetOfNodes(Atom[] var0) {
      String var2 = "";
      int var5 = 0;
      int var6 = 0;
      String[] var3 = new String[var0.length];
      int[] var4 = new int[var0.length];

      for (int var8 = 0; var8 < var0.length; var8++) {
         String var1 = var0[var8].symbol;
         var5 += var0[var8].HCount;
         boolean var7 = false;

         for (int var9 = 0; var9 < var6; var9++) {
            if (var3[var9].equals(var1)) {
               var4[var9]++;
               var7 = true;
               break;
            }
         }

         if (!var7) {
            var3[var6] = var1;
            var4[var6]++;
            var6++;
         }
      }

      for (int var10 = 0; var10 < var6; var10++) {
         var2 = var2 + var3[var10];
         if (var4[var10] > 1) {
            var2 = var2 + new Integer(var4[var10]).toString();
         }

         if (var10 == 0 && var5 > 0) {
            var2 = var2 + "H";
            if (var5 > 1) {
               var2 = var2 + new Integer(var5).toString();
            }
         }
      }

      return var2;
   }

   public static void reportConnectionTable(Atom[] var0) {
      int[][] var1 = createConnectionTable(var0);

      for (int var3 = 0; var3 < var1.length; var3++) {
         String var2 = var0[var3].symbol + ": ";

         for (int var4 = 0; var4 < var1.length; var4++) {
            var2 = var2 + var1[var3][var4] + " ";
         }

         System.out.println(var2);
      }
   }

   public static void printInt2D(int[][] var0) {
      for (int var2 = 0; var2 < var0.length; var2++) {
         String var1 = "";

         for (int var3 = 0; var3 < var0.length; var3++) {
            var1 = var1 + var0[var2][var3] + " ";
         }

         System.out.println(var1);
      }
   }

   public static int[][] createConnectionTable(Atom[] var0) {
      int[][] var2 = new int[var0.length][var0.length];

      for (int var3 = 0; var3 < var0.length; var3++) {
         for (int var4 = 0; var4 < var0.length; var4++) {
            var2[var3][var4] = 0;
         }
      }

      for (int var6 = 0; var6 < var0.length; var6++) {
         for (int var5 = 0; var5 < var0[var6].degree; var5++) {
            Atom var1 = var0[var6].nodeTable[var5];
            var2[var6][getNumber(var0, var1)] = var0[var6].bondTable[var5];
         }
      }

      return var2;
   }

   public static void synchronizeConnectionTable(int[][] var0) {
      int var1 = var0.length;

      for (int var2 = 0; var2 < var1; var2++) {
         for (int var3 = var2 + 1; var3 < var1; var3++) {
            var0[var3][var2] = var0[var2][var3];
         }
      }
   }

   public static void synchronizeConnectionTable2(int[][] var0) {
      int var1 = var0.length;

      for (int var2 = 0; var2 < var1; var2++) {
         for (int var3 = var2 + 1; var3 < var1; var3++) {
            var0[var2][var3] = var0[var3][var2];
         }
      }
   }

   public static int[][] cloneInt2D(int[][] var0) {
      int var1 = var0.length;
      int[][] var2 = new int[var1][var1];

      for (int var3 = 0; var3 < var1; var3++) {
         for (int var4 = 0; var4 < var1; var4++) {
            var2[var3][var4] = var0[var3][var4];
         }
      }

      return var2;
   }

   public static int[][] computeEdges(int[][] var0) {
      int var1 = var0.length;
      int[][] var2 = new int[var1][var1];

      for (int var3 = 0; var3 < var1; var3++) {
         for (int var4 = 0; var4 < var1; var4++) {
            if (var0[var3][var4] > 0) {
               var2[var3][var4] = 1;
            } else {
               var2[var3][var4] = INFINITY;
            }
         }
      }

      return var2;
   }

   public static Atom[] cloneAtomSet(Atom[] var0) {
      Atom[] var1 = new Atom[var0.length];

      for (int var2 = 0; var2 < var0.length; var2++) {
         var1[var2] = (Atom)var0[var2].clone();
      }

      for (int var3 = 0; var3 < var0.length; var3++) {
         for (int var4 = 0; var4 < var0[var3].degree; var4++) {
            for (int var5 = 0; var5 < var0.length; var5++) {
               if (var0[var5].equals(var0[var3].nodeTable[var4])) {
                  var1[var3].nodeTable[var4] = var1[var5];
                  break;
               }
            }
         }
      }

      return var1;
   }

   public static synchronized void synchronizeBonds(Atom[] var0, int[][] var1) {
      if (var1.length != var0.length) {
         System.err.println("Error in DataStructureTools->synchronizeBonds: incompatible array sizes!");
      } else {
         for (int var2 = 0; var2 < var0.length; var2++) {
            var0[var2].degree = 0;
         }

         for (int var3 = 0; var3 < var0.length - 1; var3++) {
            for (int var4 = var3 + 1; var4 < var0.length; var4++) {
               if (var1[var3][var4] > 0) {
                  var0[var3].nodeTable[var0[var3].degree] = var0[var4];
                  var0[var3].bondTable[var0[var3].degree] = var1[var3][var4];
                  var0[var4].nodeTable[var0[var4].degree] = var0[var3];
                  var0[var4].bondTable[var0[var4].degree] = var1[var3][var4];
                  var0[var3].degree++;
                  var0[var4].degree++;
               }
            }
         }
      }
   }

   public static void synchronizeIntNodeTable(Atom[] var0) {
      for (int var1 = 0; var1 < var0.length - 1; var1++) {
         for (int var2 = 0; var2 < var0[var1].degree; var2++) {
            for (int var3 = 0; var3 < var0.length; var3++) {
               if (var0[var1].nodeTable[var2].equals(var0[var3])) {
                  var0[var1].intnodeTable[var2] = var3;
               }
            }
         }
      }
   }

   public static void synchronizeNodeTable(Atom[] var0) {
      for (int var1 = 0; var1 < var0.length - 1; var1++) {
         for (int var2 = 0; var2 < var0[var1].degree; var2++) {
            var0[var1].nodeTable[var2] = var0[var0[var1].intnodeTable[var2]];
         }
      }
   }

   public static void removeIsomorphism(Vector var0) {
      boolean var1 = false;
      int var5 = 0;

      do {
         var1 = false;
         var5 = var0.size();

         try {
            Atom[] var2 = (Atom[])var0.elementAt(--var5);
         } catch (Exception var8) {
            var1 = true;
         }
      } while (var1);

      var0.setSize(var5 + 1);
      int var6 = 0;
      int var7 = 0;

      do {
         var1 = false;
         IsomorphismTester var4 = new IsomorphismTester();
         Atom[] var12 = (Atom[])var0.elementAt(var6);
         var5 = var0.size();
         var7 = 0;

         do {
            if (var6 != var7 && var4.isIsomorph(var12, (Atom[])var0.elementAt(var7))) {
               var0.removeElementAt(var7);
               var1 = true;
            }
         } while (var0.size() > 0 && ++var7 < var0.size());
      } while (var0.size() > 0 && ++var6 < var0.size());
   }

   public static float getMass(String var0) {
      float var1 = 0.0F;
      StandardIsotopes var3 = new StandardIsotopes();
      MFAnalyser var4 = new MFAnalyser(var0);
      Atom[] var5 = var4.getSetOfNodes();

      for (int var6 = 0; var6 < var5.length; var6++) {
         Isotope var2 = var3.getMajorIsotope(var5[var6].symbol);
         if (var2 == null) {
            return 0.0F;
         }

         var1 = (float)(var1 + var2.exactMass);
      }

      return var1;
   }

   public static void saturate(Atom[] var0) {
      StandardElements var2 = new StandardElements();

      for (int var3 = 0; var3 < var0.length; var3++) {
         var0[var3].maxBondOrderSum = var2.getMaxBondOrderSum(var0[var3].symbol);
         var0[var3].bondOrderSum = var0[var3].getBondCount();
      }

      for (int var4 = 1; var4 < 4; var4++) {
         for (int var5 = 0; var5 < var0.length; var5++) {
            if (var0[var5].degree == var4 && var0[var5].bondOrderSum < var0[var5].maxBondOrderSum - var0[var5].HCount) {
               for (int var6 = 0; var6 < var0[var5].degree; var6++) {
                  Atom var1 = var0[var5].nodeTable[var6];
                  if (var1.bondOrderSum < var1.maxBondOrderSum - var1.HCount) {
                     var0[var5].bondTable[var6]++;
                     var0[var5].bondOrderSum++;

                     for (int var7 = 0; var7 < var1.degree; var7++) {
                        if (var1.nodeTable[var7].equals(var0[var5])) {
                           var1.bondTable[var7]++;
                           var1.bondOrderSum++;
                           break;
                        }
                     }
                     break;
                  }
               }
            }
         }
      }
   }

   public static int[] computeMorganMatrix(Atom[] var0) {
      boolean var1 = false;
      int var4 = var0.length;
      boolean var5 = false;
      int var6 = 0;
      int[] var2 = new int[var4];
      int[] var3 = new int[var4];
      new Hashtable(var4);

      for (int var8 = 0; var8 < var4; var8++) {
         var2[var8] = var0[var8].degree;
         var3[var8] = var0[var8].degree;
      }

      for (int var9 = 0; var9 < var4; var9++) {
         for (int var10 = 0; var10 < var4; var10++) {
            var2[var10] = 0;

            for (int var11 = 0; var11 < var0[var10].degree; var11++) {
               var2[var10] += var3[getNumber(var0, var0[var10].nodeTable[var11])];
            }
         }

         System.arraycopy(var2, 0, var3, 0, var4);
         var6++;
      }

      return var3;
   }

   public static void normalize(Atom[] var0) {
      RingSet var1 = new SetOfRingsFinder().getAllRings(var0);
      normalize(var0, var1);
   }

   public static void normalize(Atom[] var0, RingSet var1) {
      Path var2 = null;
      Atom var3 = null;
      var1.sort();

      for (int var4 = 0; var4 < var1.size(); var4++) {
         var2 = (Path)var1.elementAt(var1.size() - var4 - 1);
         if (debug) {
            System.out.println("Testing ring no " + var4 + " for aromaticity:");
         }

         if (!isAromatic(var0, var1, var2)) {
            if (debug) {
               System.out.println("Ring " + var2 + " is not aromatic.");
            }
         } else {
            if (debug) {
               System.out.println("Ring " + var2 + " is aromatic.");
            }

            for (int var5 = 0; var5 < var2.size(); var5++) {
               var3 = var0[var2.get(var5)];

               for (int var6 = 0; var6 < var3.degree; var6++) {
                  if (var2.isMember[getNumber(var0, var3.nodeTable[var6])]) {
                     var3.bondTable[var6] = 4;
                  }
               }
            }
         }
      }
   }

   public static boolean isAromatic(Atom[] var0, RingSet var1, Path var2) {
      int var3 = 0;
      int var4 = 0;
      Object var5 = null;
      int var6 = 0;
      if (debug) {
         System.out.println("isAromatic() -> ring: " + var2);
      }

      if (debug) {
         System.out.println("isAromatic() -> ring.size(): " + var2.size());
      }

      for (int var7 = 0; var7 < var2.size(); var7++) {
         var5 = var0[var2.get(var7)];
         if ("O-N-S-P".indexOf(((Atom)var5).symbol) > -1) {
            var4++;
         }

         if (debug2) {
            String var8 = "isMember array: [";

            for (int var9 = 0; var9 < var2.isMember.length; var9++) {
               if (var2.isMember[var9]) {
                  var8 = var8 + "1";
               } else {
                  var8 = var8 + "0";
               }
            }

            var8 = var8 + "]";
         }

         for (int var12 = 0; var12 < ((Atom)var5).degree; var12++) {
            if (debug) {
               System.out.println(var0 + " # " + ((Atom)var5).nodeTable[var12] + " # " + getNumber(var0, ((Atom)var5).nodeTable[var12]));
            }

            if (var2.isMember[getNumber(var0, ((Atom)var5).nodeTable[var12])]
               && (((Atom)var5).bondTable[var12] == 4 || ((Atom)var5).bondTable[var12] == 3 || ((Atom)var5).bondTable[var12] == 2)) {
               if (debug) {
                  System.out.println("isAromatic() -> pi electrons detected between " + ((Atom)var5).number + " and " + ((Atom)var5).nodeTable[var12].number);
               }

               var3++;
               break;
            }
         }

         for (int var14 = 0; var14 < ((Atom)var5).degree; var14++) {
            if (((Atom)var5).bondTable[var14] == 4) {
               var6++;
               break;
            }
         }
      }

      for (int var13 = 0; var13 < (var2.size() - 2) / 4 + 2; var13++) {
         if (debug1) {
            System.out.println("isAromatic() -> freeElectronPairCount: " + var4);
         }

         if (debug1) {
            System.out.println("isAromatic() -> piElectronCount: " + var3);
         }

         if (debug1) {
            System.out.println("isAromatic() -> f: " + var13);
         }

         if (debug1) {
            System.out.println("isAromatic() -> (4 * f) + 2: " + (4 * var13 + 2));
         }

         if (debug1) {
            System.out.println("isAromatic() -> ring.size(): " + var2.size());
         }

         if (debug) {
            System.out.println("isAromatic() -> aromaCounter: " + var6);
         }

         if (var6 == var2.size()) {
            return true;
         }

         if (var3 == var2.size() && 4 * var13 + 2 == var3) {
            return true;
         }

         if (4 * var13 + 2 == var3 + var4 * 2 && var2.size() < var3 + var4 * 2) {
            return true;
         }
      }

      return false;
   }

   public static int faculty(int var0) {
      int var1 = 1;

      for (int var2 = 2; var2 <= var0; var2++) {
         var1 *= var2;
      }

      return var1;
   }

   public static String getDuration(long var0, long var2) {
      long var4 = var2 - var0;
      return getDuration(var4);
   }

   public static String getDuration(long var0) {
      GregorianCalendar var2 = new GregorianCalendar();
      var2.setTime(new Date(var0));
      StringBuffer var3 = new StringBuffer();
      if (var2.get(10) > 1) {
         var3.append("hours: " + (var2.get(10) - 1) + ", ");
      }

      if (var2.get(12) > 0) {
         var3.append("minutes: " + var2.get(12) + ", ");
      }

      if (var2.get(13) > 0) {
         var3.append("seconds: " + var2.get(13) + ", ");
      }

      if (var2.get(14) > 1) {
         var3.append("milliseconds: " + var2.get(14) + ", ");
      }

      var3.append("total milliseconds: " + var0);
      return var3.toString();
   }

   private static int getNumber(Atom[] var0, Atom var1) {
      for (int var2 = 0; var2 < var0.length; var2++) {
         if (var0[var2].equals(var1)) {
            return var2;
         }
      }

      return -1;
   }
}
