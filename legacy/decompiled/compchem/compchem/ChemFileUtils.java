package compchem;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.io.Writer;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.Vector;

public class ChemFileUtils {
   static boolean debug = false;

   public static Atom[] readMDLMolFile(String var0) {
      File var1 = new File(var0);
      return readMDLMolFile(var1);
   }

   public static Atom[] readMDLMolFile(File var0) {
      try {
         BufferedReader var1 = new BufferedReader(new FileReader(var0));
         Atom[] var2 = readMDLMolFile(var1);

         try {
            var1.close();
         } catch (IOException var4) {
            System.out.println("exc.toString()");
         }

         return var2;
      } catch (FileNotFoundException var5) {
         System.err.println("Error while reading MDL Molfile.");
         System.err.println("Reason for failure: " + var5.toString());
         return null;
      }
   }

   public static Atom[] readMDLMolFile(BufferedReader var0) {
      Atom var1 = null;
      int var2 = 0;
      int var3 = 0;
      int var4 = 0;
      int var5 = 0;
      int var6 = 0;
      int var7 = 0;
      int var8 = 0;
      int var9 = 0;
      double var10 = 0.0;
      double var12 = 0.0;
      double var14 = 0.0;
      int[][] var16 = new int[0][0];
      Vector var18 = new Vector();

      try {
         if (debug) {
            System.out.println("Attempt to read file " + var0);
         }

         var0.readLine();
         var0.readLine();
         var0.readLine();
         StringBuffer var19 = new StringBuffer(var0.readLine());
         var19.insert(3, " ");
         StringTokenizer var20 = new StringTokenizer(var19.toString());
         var2 = Integer.valueOf(var20.nextToken());
         var3 = Integer.valueOf(var20.nextToken());
         if (debug) {
            System.out.println("Number of atoms: " + var2);
         }

         if (debug) {
            System.out.println("Number of bonds: " + var3);
         }

         var16 = new int[var2][var2];

         for (int var21 = 0; var21 < var2; var21++) {
            var19 = new StringBuffer(var0.readLine());
            var20 = new StringTokenizer(var19.toString().trim());
            var10 = new Double(var20.nextToken());
            var12 = new Double(var20.nextToken());
            var14 = new Double(var20.nextToken());
            var18.addElement(new Atom(var20.nextToken(), var10, var12, var14));
         }

         for (int var22 = 0; var22 < var3; var22++) {
            var19 = new StringBuffer(var0.readLine());
            var19.insert(3, " ");
            var19.insert(7, " ");
            var19.insert(11, " ");
            var19.insert(15, " ");
            var20 = new StringTokenizer(var19.toString());
            var4 = Integer.valueOf(var20.nextToken());
            var5 = Integer.valueOf(var20.nextToken());
            var6 = Integer.valueOf(var20.nextToken());
            var9 = Integer.valueOf(var20.nextToken());
            if (var9 == 0) {
               var7 = var6;
               var8 = var6;
            } else if (var9 == 1) {
               var7 = 5;
               var8 = 6;
            } else if (var9 == 6) {
               var7 = 7;
               var8 = 8;
            }

            var16[var4 - 1][var5 - 1] = var7;
            var16[var5 - 1][var4 - 1] = var8;
         }

         for (String var23 = var0.readLine(); !var23.startsWith("M  END"); var23 = var0.readLine()) {
            if (var23.startsWith("M  CHG")) {
               System.out.println("Charges found: " + var23);
               var23 = var23.substring(6);
               var20 = new StringTokenizer(var23.trim());
               int var24 = Integer.valueOf(var20.nextToken());
               System.out.println("Charges defined for " + var24 + " atoms");
               System.out.println("Tokens left: " + var20.countTokens());

               for (int var25 = 1; var25 <= var24; var25++) {
                  if (var20.countTokens() >= 2) {
                     int var26 = Integer.valueOf(var20.nextToken());
                     int var27 = Integer.valueOf(var20.nextToken());
                     System.out.println("Charge for atom " + var26 + " = " + var27);
                     ((Atom)var18.elementAt(var26 - 1)).charge = var27;
                  }
               }
            }
         }

         if (debug) {
            System.out.println("File scan successful");
         }
      } catch (Exception var28) {
         System.err.println("Error while reading MDL Molfile.");
         System.err.println("Reason for failure: ");
         var28.printStackTrace();
      }

      for (int var41 = 0; var41 < var18.size(); var41++) {
         ((Atom)var18.elementAt(var41)).number = var41;
         ((Atom)var18.elementAt(var41)).degree = 0;

         for (int var45 = 0; var45 < var18.size(); var45++) {
            if (var16[var41][var45] > 0) {
               ((Atom)var18.elementAt(var41)).nodeTable[((Atom)var18.elementAt(var41)).degree] = (Atom)var18.elementAt(var45);
               ((Atom)var18.elementAt(var41)).bondTable[((Atom)var18.elementAt(var41)).degree] = var16[var41][var45];
               ((Atom)var18.elementAt(var41)).degree++;
            }
         }
      }

      StandardElements var46 = new StandardElements();
      var1 = new Atom[var18.size()];

      for (int var47 = 0; var47 < var18.size(); var47++) {
         ((Atom)var18.elementAt(var47)).maxBondOrderSum = var46.getMaxBondOrderSum(((Atom)var18.elementAt(var47)).symbol);
         ((Atom)var18.elementAt(var47)).saturate();
         ((Object[])var1)[var47] = (Atom)var18.elementAt(var47);
      }

      return var1;
   }

   public static void writeMDLMolFile(String var0, String var1, Atom[] var2) {
      File var3 = new File(var0);
      writeMDLMolFile(var3, var1, var2);
   }

   public static void writeMDLMol(Writer var0, String var1, Atom[] var2) {
      int var3 = var2.length;
      String var4 = "";
      int[][] var5 = DataStructureTools.createConnectionTable(var2);
      int var6 = 0;
      int var7 = 0;
      byte var8 = 0;
      int var9 = 0;
      int var10 = 0;
      int var11 = 0;

      for (int var12 = 0; var12 < var3; var12++) {
         for (int var13 = var12 + 1; var13 < var3; var13++) {
            if (var5[var12][var13] > 0) {
               var6++;
            }
         }
      }

      try {
         var0.write(var1 + "\n");
         var0.write("\n");
         var0.write("\n");
         var4 = var4 + formatMDLInt(var2.length, 3);
         var4 = var4 + formatMDLInt(var6, 3);
         var4 = var4 + "  0  0  0  0  0  0  0  0  1 V2000";
         var0.write(var4 + "\n");

         for (int var38 = 0; var38 < var3; var38++) {
            var4 = "";
            var4 = var4 + formatMDLFloat((float)var2[var38].x);
            var4 = var4 + formatMDLFloat((float)var2[var38].y);
            var4 = var4 + formatMDLFloat((float)var2[var38].z);
            var4 = var4 + " ";
            var4 = var4 + formatMDLString(var2[var38].symbol, 3);
            var4 = var4 + " 0  0  0  0  0  0  0  0  0  0  0  0";
            var0.write(var4 + "\n");
         }

         for (int var14 = 0; var14 < var3; var14++) {
            for (int var15 = var14 + 1; var15 < var3; var15++) {
               if (var5[var14][var15] > 0) {
                  var7 = var5[var14][var15];
                  var9 = var5[var14][var15];
                  var8 = 0;
                  var10 = var14;
                  var11 = var15;
                  var4 = "";
                  if (var7 == 5) {
                     var9 = 1;
                     var8 = 1;
                  }

                  if (var7 == 6) {
                     var9 = 1;
                     var8 = 1;
                     var10 = var15;
                     var11 = var14;
                  }

                  if (var7 == 7) {
                     var9 = 1;
                     var8 = 6;
                  }

                  if (var7 == 8) {
                     var9 = 1;
                     var8 = 6;
                     var10 = var15;
                     var11 = var14;
                  }

                  var4 = var4 + formatMDLInt(var10 + 1, 3);
                  var4 = var4 + formatMDLInt(var11 + 1, 3);
                  var4 = var4 + formatMDLInt(var9, 3);
                  var4 = var4 + formatMDLInt(var8, 3);
                  var4 = var4 + "  0  0  0";
                  var0.write(var4 + "\n");
               }
            }
         }

         var0.write("M  END\n");
      } catch (Exception var16) {
      }
   }

   public static void writeMDLMolFile(File var0, String var1, Atom[] var2) {
      OutputStreamWriter var3 = null;

      try {
         var3 = new FileWriter(var0);
         writeMDLMol(var3, var1, var2);
         var3.flush();
         var3.close();
      } catch (Exception var5) {
      }
   }

   private static String formatMDLInt(int var0, int var1) {
      String var2 = "";
      String var3 = "";
      NumberFormat var4 = NumberFormat.getNumberInstance();
      var4.setParseIntegerOnly(true);
      var4.setMinimumIntegerDigits(1);
      var4.setMaximumIntegerDigits(var1);
      var4.setGroupingUsed(false);
      var2 = var4.format((long)var0);
      var1 -= var2.length();

      for (int var5 = 0; var5 < var1; var5++) {
         var3 = var3 + " ";
      }

      return var3 + var2;
   }

   private static String formatMDLFloat(float var0) {
      String var1 = "";
      String var2 = "";
      NumberFormat var4 = NumberFormat.getNumberInstance(Locale.US);
      var4.setMinimumIntegerDigits(1);
      var4.setMaximumIntegerDigits(4);
      var4.setMinimumFractionDigits(4);
      var4.setMaximumFractionDigits(4);
      var4.setGroupingUsed(false);
      var1 = var4.format((double)var0);
      int var3 = 10 - var1.length();

      for (int var5 = 0; var5 < var3; var5++) {
         var2 = var2 + " ";
      }

      return var2 + var1;
   }

   private static String formatMDLString(String var0, int var1) {
      var0 = var0.trim();
      if (var0.length() > var1) {
         return var0.substring(0, var1);
      } else {
         int var2 = var1 - var0.length();

         for (int var3 = 0; var3 < var2; var3++) {
            var0 = var0 + " ";
         }

         return var0;
      }
   }

   public static Atom[] readLUCYStructure(String var0) {
      int var2 = 0;
      int var3 = 0;
      int var4 = 0;
      int var5 = 0;
      int var6 = 0;
      int[][] var8 = new int[0][0];
      new Vector();
      Atom[] var1 = new Atom[1];

      try {
         System.out.println("Attempt to read file " + var0);
         RandomAccessFile var11 = new RandomAccessFile(var0, "r");
         System.out.println(var11.readLine());
         StringBuffer var12 = new StringBuffer(var11.readLine());
         StringTokenizer var13 = new StringTokenizer(var12.toString());
         var2 = Integer.valueOf(var13.nextToken());
         var3 = Integer.valueOf(var13.nextToken());
         System.out.println("Number of atoms: " + var2);
         System.out.println("Number of bonds: " + var3);
         var8 = new int[var2][var2];
         var1 = new Atom[var8.length];

         for (int var14 = 0; var14 < var2; var14++) {
            var1[var14] = new Atom();
            var12 = new StringBuffer(var11.readLine());
            var13 = new StringTokenizer(var12.toString().trim());
            String var9 = var13.nextToken();
            var1[var14].symbol = var13.nextToken();
            var9 = var13.nextToken();
            var9 = var13.nextToken();
            var1[var14].HCount = Integer.parseInt(var13.nextToken());
            var1[var14].number = var14;
            var1[var14].degree = 0;
         }

         for (int var15 = 0; var15 < var3; var15++) {
            var12 = new StringBuffer(var11.readLine());
            var13 = new StringTokenizer(var12.toString());
            var4 = Integer.valueOf(var13.nextToken());
            var5 = Integer.valueOf(var13.nextToken());
            var6 = Integer.valueOf(var13.nextToken());
            var8[var4 - 1][var5 - 1] = var6;
            var8[var5 - 1][var4 - 1] = var6;
         }

         var11.close();
         System.out.println("File scan successful");
         System.out.println();
      } catch (Exception var18) {
         System.err.println("Error while reading LUCY result file.");
         System.err.println("Reason for failure: " + var18.toString());
      }

      for (int var26 = 0; var26 < var8.length; var26++) {
         for (int var29 = 0; var29 < var8.length; var29++) {
            if (var8[var26][var29] > 0) {
               var1[var26].nodeTable[var1[var26].degree] = var1[var29];
               var1[var26].bondTable[var1[var26].degree] = var8[var26][var29];
               var1[var26].degree++;
            }
         }
      }

      StandardElements var30 = new StandardElements();

      for (int var33 = 0; var33 < var8.length; var33++) {
         var1[var33].maxBondOrderSum = var30.getMaxBondOrderSum(var1[var33].symbol);
         var1[var33].bondOrderSum = var1[var33].getBondCount();
      }

      for (int var34 = 1; var34 < 4; var34++) {
         for (int var35 = 0; var35 < var8.length; var35++) {
            if (var1[var35].degree == var34 && var1[var35].bondOrderSum < var1[var35].maxBondOrderSum - var1[var35].HCount) {
               for (int var16 = 0; var16 < var1[var35].degree; var16++) {
                  Atom var7 = var1[var35].nodeTable[var16];
                  if (var7.bondOrderSum < var7.maxBondOrderSum - var7.HCount) {
                     var1[var35].bondTable[var16]++;
                     var1[var35].bondOrderSum++;

                     for (int var17 = 0; var17 < var7.degree; var17++) {
                        if (var7.nodeTable[var17] == var1[var35]) {
                           var7.bondTable[var17]++;
                           var7.bondOrderSum++;
                           break;
                        }
                     }
                     break;
                  }
               }
            }
         }
      }

      return var1;
   }
}
