package seneca.client;

import compchem.Atom;
import compchem.MFAnalyser;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.StringTokenizer;
import java.util.Vector;
import seneca.core.NMRSignal;
import seneca.core.NMRSpectrum;
import seneca.core.SenecaDataset;

public class LUCYFileHandler {
   boolean debug = false;

   public SenecaDataset importLUCYDataset(File var1) {
      float[] var9 = new float[2];
      NMRSignal var11 = null;
      NMRSignal var12 = null;
      NMRSignal var13 = null;
      boolean var14 = false;
      Vector var15 = null;
      Vector var16 = null;
      Vector var17 = null;
      NMRSignal var18 = null;
      NMRSignal var19 = null;
      NMRSignal var20 = null;
      String var21 = "";
      String var22 = "";
      String var23 = "";
      int var24 = 0;
      int var25 = 0;
      int var26 = 0;
      int var28 = 0;
      int var29 = 0;
      boolean var30 = false;
      new StringTokenizer("");
      var22 = var1.getParent() + File.separator + var1.getName();
      SenecaDataset var32 = new SenecaDataset(var1.getName());

      try {
         FileReader var33 = new FileReader(var22);
         BufferedReader var34 = new BufferedReader(var33);
         var34.readLine();
         var23 = var34.readLine().trim();
         if (this.debug) {
            System.out.println("Header: " + var23);
         }

         MFAnalyser var35 = new MFAnalyser(var23);
         Atom[] var36 = var35.getSetOfHeavyNodes();
         var32.setMolecularFormula(var23);
         var32.setMolecularMass(var35.getMass());
         var36 = var32.getSetOfNodes();
         var24 = Integer.parseInt(var34.readLine().trim());
         var25 = Integer.parseInt(var34.readLine().trim());

         for (int var37 = 0; var37 < var25; var37++) {
            var34.readLine();
         }

         int var27 = Integer.parseInt(var34.readLine().trim());

         for (int var38 = 0; var38 < var25 - 1; var38++) {
            var34.readLine();
         }

         if (this.debug) {
            System.out.println("Parsing atom characteristics...");
         }

         for (int var39 = 0; var39 < var24; var39++) {
            StringTokenizer var31 = new StringTokenizer(var34.readLine().trim());
            var31.nextToken();
            var36[var39].HCount = Integer.parseInt(var31.nextToken().trim());
         }

         var26 = Integer.parseInt(var34.readLine().trim());
         if (this.debug) {
            for (int var40 = 0; var40 < var26; var40++) {
               System.out.println(var34.readLine());
            }
         }

         for (int var89 = 0; var89 < var24; var89++) {
            var34.readLine();
         }

         if (this.debug) {
            System.out.println("Parsing CH-correlation table...");
         }

         float[] var10 = new float[var27];

         for (int var41 = 0; var41 < var27; var41++) {
            StringTokenizer var85 = new StringTokenizer(var34.readLine().trim());
            float var4 = Float.parseFloat(var85.nextToken().trim());
            float var5 = Float.parseFloat(var85.nextToken().trim());
            float var6 = Float.parseFloat(var85.nextToken().trim());
            var10[var41] = var4;
            var18 = new NMRSignal(NMRSpectrum.SPECTYPE_BB);
            var18.setShift(var4, 0);
            var18.intensity = 1.0F;
            var19 = new NMRSignal(NMRSpectrum.SPECTYPE_BB);
            var19.setShift(var4, 0);
            var20 = new NMRSignal(NMRSpectrum.SPECTYPE_BB);
            var20.setShift(var4, 0);
            switch (var36[var41].HCount) {
               case 0:
               default:
                  break;
               case 1:
                  var19.phase = NMRSignal.PHASE_POSITIVE;
                  var20.phase = NMRSignal.PHASE_POSITIVE;
                  var19.intensity = 1.0F;
                  var20.intensity = 1.0F;
                  var32.dept90.addSignal(var19);
                  var32.dept135.addSignal(var20);
                  break;
               case 2:
                  var20.phase = NMRSignal.PHASE_NEGATIVE;
                  var20.intensity = -1.0F;
                  var32.dept135.addSignal(var20);
                  break;
               case 3:
                  var20.phase = NMRSignal.PHASE_POSITIVE;
                  var20.intensity = 1.0F;
                  var32.dept135.addSignal(var20);
            }

            var32.carbon1D.addSignal(var18);
            if (var5 != 0.0F) {
               var11 = new NMRSignal(NMRSpectrum.SPECTYPE_HSQC);
               var11.setShift(var4, NMRSpectrum.NUC_CARBON);
               var11.setShift(var5, NMRSpectrum.NUC_PROTON);
               var32.hetcor.addSignal(var11);
            }

            if (var6 != 0.0F) {
               var11 = new NMRSignal(NMRSpectrum.SPECTYPE_HSQC);
               var11.setShift(var4, NMRSpectrum.NUC_CARBON);
               var11.setShift(var6, NMRSpectrum.NUC_PROTON);
               var32.hetcor.addSignal(var11);
            }
         }

         for (int var78 = 0; var78 < var24; var78++) {
            var36[var78].HCount = -1;
         }

         boolean[][] var3 = new boolean[var24][var24];
         boolean[][] var2 = new boolean[var24][var24];

         for (int var72 = 0; var72 < var24; var72++) {
            for (int var79 = 0; var79 < var24; var79++) {
               var3[var72][var79] = false;
            }
         }

         for (int var73 = 0; var73 < var24; var73++) {
            for (int var80 = 0; var80 < var24; var80++) {
               var2[var73][var80] = false;
            }
         }

         var26 = Integer.parseInt(var34.readLine().trim());

         for (int var42 = 0; var42 < var26; var42++) {
            StringTokenizer var86 = new StringTokenizer(var34.readLine().trim());
            var28 = Integer.parseInt(var86.nextToken().trim());
            var29 = Integer.parseInt(var86.nextToken().trim());
            var2[var28 - 1][var29 - 1] = true;
            var2[var29 - 1][var28 - 1] = true;
         }

         var26 = Integer.parseInt(var34.readLine().trim());

         for (int var43 = 0; var43 < var26; var43++) {
            StringTokenizer var87 = new StringTokenizer(var34.readLine().trim());
            var28 = Integer.parseInt(var87.nextToken().trim());
            var29 = Integer.parseInt(var87.nextToken().trim());
            var3[var28 - 1][var29 - 1] = true;
            var3[var29 - 1][var28 - 1] = true;
         }

         var33.close();
         if (this.debug) {
            System.out.println("Converting HMBC boolean table to HMBC spectrum...");
         }

         for (int var76 = 0; var76 < var2.length; var76++) {
            for (int var83 = var76 + 1; var83 < var2.length; var83++) {
               if (var2[var76][var83]) {
                  var14 = false;
                  if (this.debug) {
                     System.out
                        .println(
                           "HMBC Correlation found between carbon number "
                              + (var76 + 1)
                              + " at "
                              + var10[var76]
                              + " and number  "
                              + (var83 + 1)
                              + " at "
                              + var10[var83]
                              + " ppm."
                        );
                  }

                  for (int var44 = 0; var44 < 2; var44++) {
                     if (var44 == 0) {
                        var15 = var32.hetcor.pickSignals(var10[var83], NMRSpectrum.NUC_CARBON, 0.01F);
                        if (var15.size() > 0) {
                           for (int var91 = 0; var91 < var15.size(); var91++) {
                              var11 = (NMRSignal)var15.elementAt(var91);
                              if (this.debug) {
                                 System.out
                                    .println(
                                       "Adding HMBC Correlation between carbon at "
                                          + var10[var76]
                                          + " and proton at"
                                          + var11.getShift(NMRSpectrum.NUC_PROTON)
                                          + " ppm."
                                    );
                              }

                              var12 = new NMRSignal(NMRSpectrum.SPECTYPE_HMQC);
                              var12.setShift(var10[var76], NMRSpectrum.NUC_CARBON);
                              var12.setShift(var11.getShift(NMRSpectrum.NUC_PROTON), NMRSpectrum.NUC_PROTON);
                              var32.hetcorlr.addSignal(var12);
                              if (this.debug) {
                                 System.out.println(var12);
                              }
                           }
                           break;
                        }

                        if (this.debug) {
                           System.out.println("Found " + var15.size() + " signals with shift " + var10[var83]);
                        }
                     } else {
                        if (this.debug) {
                           System.out.println("Thus reverting assignment...");
                        }

                        var15 = var32.hetcor.pickSignals(var10[var76], NMRSpectrum.NUC_CARBON, 0.01F);
                        if (this.debug) {
                           System.out.println("Found " + var15.size() + " signals with shift " + var10[var76]);
                        }

                        if (var15.size() > 0) {
                           for (int var45 = 0; var45 < var15.size(); var45++) {
                              var11 = (NMRSignal)var15.elementAt(var45);
                              if (this.debug) {
                                 System.out
                                    .println(
                                       "Adding HMBC Correlation between carbon at "
                                          + var10[var83]
                                          + " and proton at "
                                          + var11.getShift(NMRSpectrum.NUC_PROTON)
                                          + " ppm."
                                    );
                              }

                              var12 = new NMRSignal(NMRSpectrum.SPECTYPE_HMQC);
                              var12.setShift(var10[var83], NMRSpectrum.NUC_CARBON);
                              var12.setShift(var11.getShift(NMRSpectrum.NUC_PROTON), NMRSpectrum.NUC_PROTON);
                              var32.hetcorlr.addSignal(var12);
                              if (this.debug) {
                                 System.out.println(var12);
                              }
                           }
                        }
                     }
                  }
               }
            }
         }

         for (int var77 = 0; var77 < var3.length; var77++) {
            for (int var84 = 0; var84 < var3.length; var84++) {
               if (var3[var77][var84]) {
                  var14 = false;
                  if (this.debug) {
                     System.out.println("HH-COSY Correlation found between protons attached to carbons at " + var10[var77] + " and " + var10[var84] + " ppm.");
                  }

                  var16 = var32.hetcor.pickSignals(var10[var77], NMRSpectrum.NUC_CARBON, 0.01F);
                  var17 = var32.hetcor.pickSignals(var10[var84], NMRSpectrum.NUC_CARBON, 0.01F);

                  for (int var90 = 0; var90 < var16.size(); var90++) {
                     for (int var92 = 0; var92 < var17.size(); var92++) {
                        var11 = (NMRSignal)var16.elementAt(var90);
                        var12 = (NMRSignal)var17.elementAt(var92);
                        var13 = new NMRSignal(NMRSpectrum.SPECTYPE_HHCOSY);
                        var13.setShift(var11.getShift(NMRSpectrum.NUC_PROTON), 0);
                        var13.setShift(var12.getShift(NMRSpectrum.NUC_PROTON), 1);
                        if (this.debug) {
                           System.out
                              .println(
                                 "Adding HHCOSY Correlation between protons at "
                                    + var11.getShift(NMRSpectrum.NUC_PROTON)
                                    + " and "
                                    + var12.getShift(NMRSpectrum.NUC_PROTON)
                                    + " ppm."
                              );
                        }

                        var32.hhcosy.addSignal(var13);
                        if (this.debug) {
                           System.out.println(var13);
                        }
                     }
                  }
               }
            }
         }
      } catch (Exception var46) {
         System.out.println("Error while trying to read LUCY file\n" + var46.toString());
      }

      if (this.debug) {
         var32.carbon1D.report();
      }

      if (this.debug) {
         var32.hetcor.report();
      }

      if (this.debug) {
         var32.hetcorlr.report();
      }

      if (this.debug) {
         var32.hhcosy.report();
      }

      var32.init();
      return var32;
   }

   public String getExtension(File var1) {
      String var2 = null;
      String var3 = var1.getName();
      int var4 = var3.lastIndexOf(46);
      if (var4 > 0 && var4 < var3.length() - 1) {
         var2 = var3.substring(var4 + 1).toLowerCase();
      }

      return var2;
   }
}
