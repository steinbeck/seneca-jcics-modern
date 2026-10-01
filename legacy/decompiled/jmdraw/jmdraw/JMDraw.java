package jmdraw;

import compchem.Atom;
import compchem.DPoint;
import compchem.DataStructureTools;
import compchem.Molecule;
import compchem.Ring;
import compchem.SetOfRingsFinder;
import compchem.setOfRings;
import java.awt.Dimension;
import java.awt.Graphics;
import java.util.Vector;

public class JMDraw {
   public Atom[] atomSet = null;
   public setOfRings sssr = null;
   public double bondLength = 1.0;
   public double bondLineSep = 0.05;
   private double roundFactor = this.bondLength * 10000.0;
   public static double TWO = 2.0;
   public int ringTracker = 0;
   int ddebug = 0;
   boolean allDone;
   public Dimension preferredSize = new Dimension(300, 200);
   public boolean drawSymbols = true;
   public boolean drawHydrogens = true;
   public boolean enumerate = false;
   public boolean verbose = false;
   public boolean drawProperties = false;
   private RenderingAttributes rA = null;
   private Dimension size;
   SetOfRingsFinder sorf = new SetOfRingsFinder();

   public JMDraw() {
      this.atomSet = new Atom[0];
   }

   public JMDraw(Atom[] var1) {
      this();
      if (var1 != null) {
         this.setAtomSet(var1, true);
      }
   }

   public void setAtomSet(Atom[] var1, boolean var2) {
      for (int var3 = 0; var3 < var1.length; var3++) {
         var1[var3].x = 0.0;
         var1[var3].y = 0.0;
      }

      if (var2) {
         this.atomSet = DataStructureTools.cloneAtomSet(var1);
      } else {
         this.atomSet = var1;
      }

      DataStructureTools.synchronizeIntNodeTable(this.atomSet);
   }

   public Atom[] getAtomSet() {
      DataStructureTools.synchronizeNodeTable(this.atomSet);
      return this.atomSet;
   }

   public void draw() {
      int var1 = 0;
      int var2 = 0;
      int[] var3 = new int[10];
      double[] var7 = new double[10];
      int var8 = 0;

      for (int var9 = 0; var9 < this.atomSet.length; var9++) {
         this.atomSet[var9].status = 0;
         this.atomSet[var9].ringCounter = 0;
      }

      this.sortBondList();
      if (this.verbose) {
         System.out.println("JMDraw->draw(): Structure generation started based on the following atomset: ");
      }

      if (this.verbose) {
         this.reportAtomSet(this.atomSet);
      }

      for (int var10 = 0; var10 < this.atomSet.length; var10++) {
         var8 += this.atomSet[var10].degree;
      }

      var8 /= 2;
      int var11 = var8 - this.atomSet.length + 1;
      if (this.verbose) {
         System.out.println("JMDraw->draw(): noOfEdges=" + var8 + ", atomSet.length=" + this.atomSet.length);
      }

      if (this.verbose) {
         System.out.println("JMDraw->draw(): " + var11 + " rings expected");
      }

      try {
         if (var11 > 0) {
            this.sssr = this.sorf.findSSSR(this.atomSet);
            this.sssr.sort();
            this.sssr.makeNodeRingLists(this.atomSet);
            if (this.verbose) {
               System.out.println("JMDraw->draw(): Ring perception finished. " + this.sssr.size() + " Rings found.");
            }

            if (this.verbose) {
               System.out.println("JMDraw->draw(): Listing rings:");
            }

            if (this.verbose) {
               this.sssr.reportRingList();
            }

            if (this.verbose) {
               System.out.println("JMDraw->draw(): End of ring list.");
            }

            if (this.sssr.size() > 0) {
               int var12 = 0;
               int var15 = 0;
               int var16 = 0;
               int var17 = 0;
               int var22 = -1;
               boolean var23 = false;

               for (int var25 = this.sssr.size() - 1; var25 > -1; var25--) {
                  Ring var24 = (Ring)this.sssr.elementAt(var25);
                  int var14 = var24.getComplexity(this.atomSet);
                  if (var14 > var15) {
                     var15 = var14;
                     var16 = var25;
                     var17 = var24.size();
                     var12 = ((Atom)var24.elementAt(0)).number;
                     int var13 = ((Atom)var24.elementAt(0)).ringCounter;
                  }
               }

               this.positionRingAtoms(var16, new DPoint(0, 0), 0.0, 0, var17 - 1);
               Ring var39 = (Ring)this.sssr.elementAt(var16);
               var39.status = 1;

               for (int var26 = 0; var26 < var39.size(); var26++) {
                  this.atomSet[var39.getNodeNumber(var26)].status = 1;
               }

               while (true) {
                  boolean var20 = false;

                  for (int var27 = 0; var27 < this.sssr.size(); var27++) {
                     if (((Ring)this.sssr.elementAt(var27)).status == 1) {
                        this.positionRingSubstituents(var27);
                        var20 = true;
                     }
                  }

                  if (!var20) {
                     var22 = this.whatToDraw();
                     if (var22 != -1) {
                        this.positionAtom(var22);
                     }

                     if (this.whatToDraw() == -1) {
                        break;
                     }
                  }
               }
            }

            if (this.verbose) {
               System.out.println("JMDraw->draw(): Rings construction finished.");
            }
         } else {
            if (this.verbose) {
               System.out.println("JMDraw->draw(): No ring present. Constructing aliphatic system...");
            }

            var1 = 0;

            while (var1 < this.atomSet.length && this.atomSet[var1].degree <= 1) {
               var1++;
            }

            this.atomSet[var1].x = 0.0;
            this.atomSet[var1].y = 0.0;
            var2 = 0;

            while (
               var2 < this.atomSet[var1].degree
                  && (this.atomSet[this.atomSet[var1].intnodeTable[var2]].degree <= 1 || this.atomSet[var1].intnodeTable[var2] == var1)
            ) {
               var2++;
            }

            var2 = this.atomSet[var1].intnodeTable[var2];
            this.atomSet[var2].x = this.bondLength;
            this.atomSet[var2].y = 0.0;
            this.atomSet[var2].status = 1;
            this.atomSet[var1].status = 1;

            for (int var36 = 0; var36 < 10; var36++) {
               var3[var36] = 0;
            }

            int var4 = this.atomSet[var2].degree;
            boolean var6 = true;
            var7 = this.angleOpt(var2, var3, var6);
            if (var4 > 0) {
               for (int var37 = 0; var37 < var4; var37++) {
                  if (this.atomSet[this.atomSet[var2].intnodeTable[var37]].status < 1) {
                     DPoint var5 = this.placeAtom(
                        new DPoint(this.atomSet[var1].x, this.atomSet[var1].y), new DPoint(this.atomSet[var2].x, this.atomSet[var2].y), this.bondLength
                     );
                     var5 = this.rotAtom(new DPoint(this.atomSet[var2].x, this.atomSet[var2].y), var5, var7[var37]);
                     this.atomSet[this.atomSet[var2].intnodeTable[var37]].x = var5.x;
                     this.atomSet[this.atomSet[var2].intnodeTable[var37]].y = var5.y;
                     this.atomSet[this.atomSet[var2].intnodeTable[var37]].status = 1;
                  }
               }
            }

            this.atomSet[var2].status = 2;
            this.doTheRest();
         }
      } catch (Exception var28) {
         var28.printStackTrace();
      }
   }

   protected int whatToDraw() {
      for (int var1 = 0; var1 < this.atomSet.length; var1++) {
         if (this.atomSet[var1].status == 1) {
            return var1;
         }
      }

      return -1;
   }

   protected int doTheRest() {
      int var2 = -1;

      boolean var3;
      do {
         var3 = true;
         int var1 = -1;

         do {
            if (this.atomSet[++var1].status == 1) {
               var3 = false;
            }
         } while (var1 != this.atomSet.length - 1 && this.atomSet[var1].status != 1);

         if (var3) {
            return var2;
         }

         if (var1 == this.atomSet.length) {
            return var2;
         }

         var2 = this.positionAtom(var1);
      } while (!var3);

      return var2;
   }

   public void reportAtomSet(Atom[] var1) {
      for (int var4 = 0; var4 < var1.length; var4++) {
         Atom var2 = var1[var4];
         String var3 = "Atom " + var4 + ":";

         for (int var5 = 0; var5 < var2.degree; var5++) {
            var3 = var3 + var2.intnodeTable[var5] + " ";
         }

         var3 = var3 + "; Status: " + var2.status;
         var3 = var3 + "; In Rings: ";

         for (int var6 = 0; var6 < var2.inRings.length; var6++) {
            var3 = var3 + var2.inRings[var6] + " ";
         }

         var3 = var3 + "; X: " + var2.x;
         var3 = var3 + "; Y: " + var2.y;
         System.out.println(var3);
      }
   }

   protected void positionRingAtoms(int var1, DPoint var2, double var3, int var5, int var6) {
      Ring var8 = (Ring)this.sssr.elementAt(var1);
      double var9 = Math.PI / 180.0;
      double var11 = 0.0;
      double var13 = 0.0;
      double var15 = 0.0;
      var3 = (var3 / 360.0 - Math.floor(var3 / 360.0)) * 360.0;
      var11 = 180.0 / var8.size();
      var13 = var11 * 2.0;
      var15 = 0.5 * this.bondLength / Math.sin(var9 * var11);

      for (int var21 = var5; var21 <= var6; var21++) {
         int var22 = var8.getNodeNumber(var21);
         if (this.atomSet[var22].status < 1) {
            double var17 = var15 * Math.cos(var3 * var9);
            double var19 = var15 * Math.sin(var3 * var9);
            this.atomSet[var22].x = var17;
            this.atomSet[var22].y = var19;
         }

         var3 += var13;
      }
   }

   protected void positionRingSubstituents(int var1) {
      double var2 = 0.0174532925;
      Ring var4 = (Ring)this.sssr.elementAt(var1);
      int var5 = var4.size();
      Ring var6 = new Ring();
      int var7 = -1;
      new Vector();
      Vector var9 = new Vector();
      Vector var10 = new Vector();
      Vector var11 = new Vector();
      int[] var12 = new int[10];
      new DPoint();
      new DPoint();
      int var18 = -1;
      int var19 = -1;
      int var20 = -1;
      int var21 = -1;
      byte var23 = -1;
      boolean var30 = false;
      int var31 = 0;
      byte var34 = -1;
      double var60 = 0.0;
      boolean var68 = false;
      new JMDraw.FusedRingParams();
      double[] var72 = new double[10];
      int[] var73 = new int[10];

      for (int var74 = 0; var74 < var4.size(); var74++) {
         int var16 = ((Atom)var4.elementAt(var74)).number;
         if (this.atomSet[var16].ringCounter > 1) {
            var11.addElement(new Integer(var16));
         }
      }

      if (var11.size() == 0) {
         this.handleRestOfRingSubstituents(var1);
      } else {
         int var75 = 0;

         do {
            var75++;

            do {
               var68 = false;

               for (int var76 = 0; var76 < var11.size(); var76++) {
                  int var83 = (Integer)var11.elementAt(var76);
                  int var17 = this.atomSet[var83].ringCounter;

                  for (int var77 = 0; var77 < var17; var77++) {
                     var18 = this.atomSet[var83].inRings[var77];
                     if (var18 != var1 && ((Ring)this.sssr.elementAt(var18)).status < 1) {
                        var6 = (Ring)this.sssr.elementAt(var18);
                        var7 = var18;
                        int var35 = this.sorf.getIntersection(var4.getNodeNumberList(), var6.getNodeNumberList()).size();
                        if (var6.notAllPositioned(this.atomSet) && (var35 == 2 && var75 == 1 || var35 > 2 && var75 == 2 || var35 == 1 && var75 == 3)) {
                           break;
                        }
                     }
                  }

                  if (var6.size() > 0 && var6.notAllPositioned(this.atomSet)) {
                     Vector var8 = this.sorf.getIntersection(var4.getNodeNumberList(), var6.getNodeNumberList());
                     if (var8.size() == 1 && var75 == 3) {
                        var31 = this.atomSet[var8.elementAt(0)].degree;
                        var12[0] = var5;

                        for (int var101 = 1; var101 < 10; var101++) {
                           var12[var101] = 0;
                        }

                        var21 = (Integer)var8.elementAt(0);

                        for (int var120 = 0; var120 < var5; var120++) {
                           for (int var102 = 0; var102 < var31; var102++) {
                              if (this.atomSet[var21].intnodeTable[var102] == var4.getNodeNumber(var120)) {
                                 var12[var102 + 1] = var5;
                              }
                           }
                        }

                        for (int var103 = 0; var103 < var6.size(); var103++) {
                           var73[var103] = this.atomSet[var6.getNodeNumber(var103)].status;
                        }

                        var21 = (Integer)var8.elementAt(0);
                        this.positionAtomSubstituents(
                           var21, new DPoint(this.atomSet[var21].x, this.atomSet[var21].y), new DPoint(var4.x, var4.y), var12, var4.size(), var7, var6.size()
                        );

                        for (int var104 = 0; var104 < var6.size(); var104++) {
                           this.atomSet[var6.getNodeNumber(var104)].status = var73[var104];
                        }

                        boolean var67 = true;
                        var72 = this.angleOpt(var21, var12, var67);
                        double var111 = 0.0;

                        for (int var105 = 0; var105 < var31; var105++) {
                           if (this.atomSet[this.atomSet[var21].intnodeTable[var105]].status == 0) {
                              var111 += var72[var105];
                           }
                        }

                        var111 /= 2.0;
                        double var62 = this.bondLength / 2.0 / Math.sin(var2 * (180.0 / var6.size()));
                        DPoint var82 = this.placeAtom(new DPoint(var4.x, var4.y), new DPoint(this.atomSet[var21].x, this.atomSet[var21].y), var62);
                        if (var111 != 0.0) {
                           var82 = this.rotAtom(new DPoint(this.atomSet[var21].x, this.atomSet[var21].y), var82, var111);
                        }

                        var6.x = var82.x;
                        var6.y = var82.y;
                        boolean var115 = true;
                        JMDraw.FusedRingParams var118 = this.getFusedRingParams(
                           new DPoint(var4.x, var4.y), new DPoint(this.atomSet[var21].x, this.atomSet[var21].y), var21, var21, var115, var7
                        );
                        var60 = var118.startAngle + 360.0 / var6.size();
                        var6.sort(var118.startAtomNumber);
                        this.positionRingAtoms(var7, new DPoint(0, 0), var60, 1, var6.size() - 1);
                        var6.status = 1;
                        this.transfer(
                           1,
                           var6.size() - 1,
                           var7,
                           new DPoint(this.atomSet[var118.startAtomNumber].x, this.atomSet[var118.startAtomNumber].y),
                           var118.annulAxisOrientation,
                           var118.toggleStartAngleSign
                        );
                        var68 = true;
                     } else if (var8.size() == 2 && var75 == 1) {
                        new JMDraw.FusedRingParams();
                        var19 = (Integer)var8.elementAt(0);
                        var20 = (Integer)var8.elementAt(1);
                        double var108 = Math.sqrt(
                           Math.pow(this.atomSet[var19].x - this.atomSet[var20].x, TWO) + Math.pow(this.atomSet[var19].y - this.atomSet[var20].y, TWO)
                        );
                        DPoint var80 = this.placeAtom(
                           new DPoint(this.atomSet[var19].x, this.atomSet[var19].y), new DPoint(this.atomSet[var20].x, this.atomSet[var20].y), -var108 / 2.0
                        );
                        double var38 = var6.getRadius(this.bondLength);
                        double var40 = var38 * var38;
                        double var42 = var108 * var108 / 4.0;
                        if (var42 < var40) {
                           var38 = Math.sqrt(var40 - var42);
                        }

                        var80 = this.placeAtom(new DPoint(var4.x, var4.y), var80, var38);
                        var6.x = var80.x;
                        var6.y = var80.y;
                        boolean var114 = false;
                        JMDraw.FusedRingParams var117 = this.getFusedRingParams(new DPoint(var4.x, var4.y), var80, var19, var20, var114, var7);
                        var6.sort(var117.startAtomNumber);
                        if (var117.startAtomNumber == var19) {
                           var21 = var20;
                        } else {
                           var21 = var19;
                        }

                        if (((Atom)var6.elementAt(1)).number == var21) {
                           var6.invert();
                           var6.sort(var117.startAtomNumber);
                        }

                        this.positionRingAtoms(var7, new DPoint(0, 0), var117.startAngle, 1, var6.size() - 2);
                        var6.status = 1;
                        this.transfer(1, var6.size() - 2, var7, new DPoint(var4.x, var4.y), var117.annulAxisOrientation, var117.toggleStartAngleSign);
                        var68 = true;
                        this.handleBridgeHeads(
                           var19, this.atomSet[var19].x, this.atomSet[var19].y, this.atomSet[var20].x, this.atomSet[var20].y, var4.size(), var6.size()
                        );
                        this.handleBridgeHeads(
                           var20, this.atomSet[var20].x, this.atomSet[var20].y, this.atomSet[var19].x, this.atomSet[var19].y, var4.size(), var6.size()
                        );
                     } else if (var8.size() > 2 && var75 == 2) {
                        var9.removeAllElements();
                        var10.removeAllElements();
                        double var46 = 0.0;
                        int var84 = -1;
                        int var88 = -1;

                        for (int var78 = 0; var78 < var8.size(); var78++) {
                           var84 = (Integer)var8.elementAt(var78);

                           for (int var25 = var78 + 1; var25 < var8.size(); var25++) {
                              var88 = (Integer)var8.elementAt(var25);
                              double var36 = Math.sqrt(
                                 this.sqr(this.atomSet[var84].x - this.atomSet[var88].x) + this.sqr(this.atomSet[var84].y - this.atomSet[var88].y)
                              );
                              if (var36 > var46) {
                                 var19 = var84;
                                 var20 = var88;
                                 var46 = var36;
                              }
                           }
                        }

                        int var24 = 0;

                        for (int var22 = 0; var22 < var6.size(); var22++) {
                           if (this.atomSet[var6.getNodeNumber(var22)].status > 0) {
                              var24++;
                           }
                        }

                        var24 = var6.size() - var24;
                        if (var24 != 0) {
                           int var93 = -1;

                           do {
                              var84 = ((Atom)var4.elementAt(++var93)).number;
                           } while (var84 != var19 && var93 < var4.size() - 1);

                           boolean var66 = true;
                           int var26 = var93;
                           int var27 = 0;
                           int var28 = 0;
                           int var29 = var4.size();

                           for (int var100 = 1; var100 < var29; var100++) {
                              if (++var26 >= var29) {
                                 var26 = 0;
                              }

                              if (var4.getNodeNumber(var26) == var20) {
                                 var66 = false;
                                 if (var26 >= var29) {
                                    var26 = -1;
                                 }
                              } else {
                                 if (var66) {
                                    var27++;
                                    var9.addElement(new Integer(var4.getNodeNumber(var26)));
                                 }

                                 if (!var66) {
                                    var28++;
                                    var10.addElement(new Integer(var4.getNodeNumber(var26)));
                                 }
                              }
                           }

                           int var32 = 0;
                           int var33 = 0;

                           for (int var94 = 0; var94 < var27; var94++) {
                              var32 += this.atomSet[var9.elementAt(var94)].degree;
                           }

                           for (int var95 = 0; var95 < var28; var95++) {
                              var33 += this.atomSet[var10.elementAt(var95)].degree;
                           }

                           double var52 = 0.0;
                           double var54 = 0.0;
                           if (var32 > var33) {
                              for (int var96 = 0; var96 < var28; var96++) {
                                 var52 = var52 + this.atomSet[var10.elementAt(var96)].x - var4.x;
                                 var54 = var54 + this.atomSet[var10.elementAt(var96)].y - var4.y;
                              }

                              var52 /= var28;
                              var54 /= var28;
                           } else {
                              for (int var97 = 0; var97 < var27; var97++) {
                                 var52 = var52 + this.atomSet[var9.elementAt(var97)].x - var4.x;
                                 var54 = var54 + this.atomSet[var9.elementAt(var97)].y - var4.y;
                              }

                              var52 /= var27;
                              var54 /= var27;
                           }

                           double var56 = Math.sqrt(var52 * var52 + var54 * var54);
                           double var48 = this.bondLength / 2.0 / Math.sin(var2 * (180.0 / var4.size()));
                           double var50 = this.bondLength / 2.0 / Math.sin(var2 * (180.0 / var6.size()));
                           var6.x = var4.x + 0.8 * (var48 + var50) * var52 / var56;
                           var6.y = var4.y + 0.8 * (var48 + var50) * var54 / var56;
                           DPoint var15 = new DPoint(var6.x, var6.y);
                           boolean var64 = false;
                           JMDraw.FusedRingParams var69 = this.getFusedRingParams(new DPoint(var4.x, var4.y), var15, var19, var20, var64, var7);
                           var6.sort(var69.startAtomNumber);
                           var26 = var6.size();
                           if (this.atomSet[var6.getNodeNumber(1)].status > 0) {
                              var6.invert();
                              var6.sort(var69.startAtomNumber);
                           }

                           double var58 = -(360.0 / var26) * (var24 - 1) / 2.0;
                           var15 = this.placeAtom(new DPoint(var4.x, var4.y), new DPoint(var6.x, var6.y), var50);
                           var6.status = 1;

                           for (int var98 = 0; var98 < var26; var98++) {
                              if (this.atomSet[var6.getNodeNumber(var98)].status <= 0) {
                                 DPoint var14 = this.rotAtom(new DPoint(var6.x, var6.y), var15, var58);
                                 this.atomSet[var6.getNodeNumber(var98)].status = 1;
                                 this.atomSet[var6.getNodeNumber(var98)].x = var14.x;
                                 this.atomSet[var6.getNodeNumber(var98)].y = var14.y;
                                 var58 += 360.0 / var26;
                              }
                           }

                           var68 = true;
                           this.handleBridgeHeads(
                              var19, this.atomSet[var19].x, this.atomSet[var19].y, this.atomSet[var20].x, this.atomSet[var20].y, var4.size(), var6.size()
                           );
                           this.handleBridgeHeads(
                              var20, this.atomSet[var20].x, this.atomSet[var20].y, this.atomSet[var19].x, this.atomSet[var19].y, var4.size(), var6.size()
                           );
                        }
                     }
                  }
               }
            } while (var68);
         } while (var75 < 3);

         this.handleRestOfRingSubstituents(var1);
      }
   }

   protected JMDraw.FusedRingParams getFusedRingParams(DPoint var1, DPoint var2, int var3, int var4, boolean var5, int var6) {
      double var11 = Math.round(var1.x * this.roundFactor) / this.roundFactor;
      double var15 = Math.round(var1.y * this.roundFactor) / this.roundFactor;
      double var13 = Math.round(var2.x * this.roundFactor) / this.roundFactor;
      double var17 = Math.round(var2.y * this.roundFactor) / this.roundFactor;
      double var7 = 360 / ((Ring)this.sssr.elementAt(var6)).size();
      JMDraw.FusedRingParams var19 = new JMDraw.FusedRingParams();
      if (var13 > var11 && var17 >= var15) {
         var19.toggleStartAngleSign = 1.0;
         var19.annulAxisOrientation = 1;
         if (var5) {
            var19.startAtomNumber = var3;
            var19.startAngle = 180.0;
            return var19;
         } else {
            var19.startAngle = 180.0 + var7 + 0.5 * var7;
            if (this.atomSet[var3].y > this.atomSet[var4].y) {
               double var29 = this.atomSet[var4].y;
               var19.startAtomNumber = var4;
            } else {
               double var30 = this.atomSet[var3].y;
               var19.startAtomNumber = var3;
            }

            return var19;
         }
      } else if (var13 > var11 && var17 < var15) {
         var19.toggleStartAngleSign = -1.0;
         var19.annulAxisOrientation = 1;
         if (var5) {
            var19.startAtomNumber = var3;
            var19.startAngle = 180.0;
            return var19;
         } else {
            var19.startAngle = 180.0 + var7 + 0.5 * var7;
            if (this.atomSet[var3].y > this.atomSet[var4].y) {
               double var27 = this.atomSet[var4].y;
               var19.startAtomNumber = var4;
            } else {
               double var28 = this.atomSet[var3].y;
               var19.startAtomNumber = var3;
            }

            return var19;
         }
      } else if (var13 < var11 && var17 <= var15) {
         var19.toggleStartAngleSign = 1.0;
         var19.annulAxisOrientation = 1;
         if (var5) {
            var19.startAtomNumber = var3;
            var19.startAngle = 0.0;
            return var19;
         } else {
            var19.startAngle = var7 + 0.5 * var7;
            if (this.atomSet[var3].y > this.atomSet[var4].y) {
               double var25 = this.atomSet[var3].y;
               var19.startAtomNumber = var3;
            } else {
               double var26 = this.atomSet[var4].y;
               var19.startAtomNumber = var4;
            }

            return var19;
         }
      } else if (var13 < var11 && var17 > var15) {
         var19.toggleStartAngleSign = -1.0;
         var19.annulAxisOrientation = 1;
         if (var5) {
            var19.startAtomNumber = var3;
            var19.startAngle = 0.0;
            return var19;
         } else {
            var19.startAngle = var7 + 0.5 * var7;
            if (this.atomSet[var3].y > this.atomSet[var4].y) {
               double var23 = this.atomSet[var3].y;
               var19.startAtomNumber = var3;
            } else {
               double var24 = this.atomSet[var4].y;
               var19.startAtomNumber = var4;
            }

            return var19;
         }
      } else if (var13 == var11 && var15 > var17) {
         var19.toggleStartAngleSign = -1.0;
         var19.annulAxisOrientation = 0;
         if (var5) {
            var19.startAtomNumber = var3;
            var19.startAngle = 180.0;
            return var19;
         } else {
            var19.startAngle = 180.0 + var7 + 0.5 * var7;
            if (this.atomSet[var3].x > this.atomSet[var4].x) {
               double var21 = this.atomSet[var4].x;
               var19.startAtomNumber = var4;
            } else {
               double var22 = this.atomSet[var3].x;
               var19.startAtomNumber = var3;
            }

            return var19;
         }
      } else if (var13 == var11 && var17 > var15) {
         var19.toggleStartAngleSign = 1.0;
         var19.annulAxisOrientation = 0;
         if (var5) {
            var19.startAtomNumber = var3;
            var19.startAngle = 180.0;
            return var19;
         } else {
            var19.startAngle = 180.0 + var7 + 0.5 * var7;
            if (this.atomSet[var3].x > this.atomSet[var4].x) {
               double var9 = this.atomSet[var3].x;
               var19.startAtomNumber = var3;
            } else {
               double var20 = this.atomSet[var4].x;
               var19.startAtomNumber = var4;
            }

            return var19;
         }
      } else {
         return var19;
      }
   }

   protected void transfer(int var1, int var2, int var3, DPoint var4, int var5, double var6) {
      double var9 = 0.0;
      Ring var23 = (Ring)this.sssr.elementAt(var3);
      new Atom();
      int var25 = 0;
      var23.x = Math.round(var23.x * this.roundFactor) / this.roundFactor;
      var23.y = Math.round(var23.y * this.roundFactor) / this.roundFactor;
      var4.x = Math.round(var4.x * this.roundFactor) / this.roundFactor;
      var4.y = Math.round(var4.y * this.roundFactor) / this.roundFactor;
      double var11 = var23.x - var4.x;
      double var13 = var23.y - var4.y;
      if (var5 == 1 && var11 == 0.0) {
         var9 = Math.PI;
      }

      if (var5 == 1 && var11 != 0.0) {
         var9 = Math.atan2(var13, var11);
         if (var9 < 0.0) {
            var9 = -var9;
         }
      }

      if (var5 == 0.0) {
         var9 = Math.PI / 2;
      }

      if (var9 > Math.PI / 2) {
         var9 = Math.PI - var9;
      }

      if (var6 < 0.0) {
         var9 = -var9;
      }

      for (int var26 = var1; var26 <= var2; var26++) {
         var25 = var23.getNodeNumber(var26);
         if (this.atomSet[var25].status == 0) {
            double var19 = this.atomSet[var25].x;
            double var21 = this.atomSet[var25].y;
            double var15 = var19 * Math.cos(var9) - var21 * Math.sin(var9) + var23.x;
            double var17 = var19 * Math.sin(var9) + var21 * Math.cos(var9) + var23.y;
            this.atomSet[var25].x = var15;
            this.atomSet[var25].y = var17;
            this.atomSet[var25].status = 1;
         }
      }
   }

   private DPoint placeAtom(DPoint var1, DPoint var2, double var3) {
      double var5 = var2.x - var1.x;
      double var7 = var2.y - var1.y;
      double var9 = var3 / Math.sqrt(var5 * var5 + var7 * var7);
      return new DPoint(var2.x + var5 * var9, var2.y + var7 * var9);
   }

   protected DPoint rotAtom(DPoint var1, DPoint var2, double var3) {
      double var5 = 0.0174532925;
      double var7 = var5 * var3;
      double var11 = Math.sin(var7);
      double var9 = Math.cos(var7);
      double var17 = var2.x - var1.x;
      double var19 = var2.y - var1.y;
      double var13 = var9 * var17 - var11 * var19;
      double var15 = var11 * var17 + var9 * var19;
      var17 = var13 + var1.x;
      var19 = var15 + var1.y;
      return new DPoint(var17, var19);
   }

   protected int positionAtomSubstituents(int var1, DPoint var2, DPoint var3, int[] var4, int var5, int var6, int var7) {
      double var8 = 0.0174532925;
      double var28 = var2.x;
      double var30 = var2.y;
      double var32 = var3.x;
      double var34 = var3.y;
      Ring var40 = null;
      new DPoint();
      if (this.sssr != null && this.sssr.size() > 0) {
         var40 = (Ring)this.sssr.elementAt(var6);
      }

      double[] var43 = new double[10];
      byte var11 = 0;
      int var15 = this.atomSet[var1].degree;
      if (var15 <= 1) {
         this.atomSet[var1].status = 2;
         return -1;
      } else {
         boolean var36 = false;
         if (var5 > 1 && var15 <= 2) {
            this.atomSet[var1].status = 2;
            return -1;
         } else {
            if (var5 > 1) {
               var36 = true;
            }

            var43 = this.angleOpt(var1, var4, var36);

            for (int var10 = 0; var10 < var15; var10++) {
               int var42 = this.atomSet[var1].intnodeTable[var10];
               if (this.atomSet[var42].status <= 0) {
                  DPoint var38 = this.placeAtom(var3, var2, this.bondLength);
                  DPoint var41 = this.rotAtom(var2, var38, var43[var10]);
                  this.atomSet[var42].x = var41.x;
                  this.atomSet[var42].y = var41.y;
                  this.atomSet[var42].status = 1;
                  if (this.atomSet[var42].degree == 1) {
                     this.atomSet[var42].status = 2;
                  }
               }
            }

            this.atomSet[var1].status = 2;
            if (var5 != 1) {
               return -1;
            } else {
               double var16 = 0.0;

               for (int var44 = 0; var44 < var15; var44++) {
                  if (var4[var44 + 1] > 0) {
                     var16 += var43[var44];
                     this.atomSet[this.atomSet[var1].intnodeTable[var44]].status = 0;
                  }
               }

               var16 *= 0.5;
               double var22 = this.bondLength / 2.0 / Math.sin(var8 * (180.0 / var7));
               DPoint var46 = this.placeAtom(new DPoint(var32, var34), new DPoint(var28, var30), var22);
               var46 = this.rotAtom(new DPoint(var28, var30), var46, var16);
               var40.x = var46.x;
               var40.y = var46.y;
               boolean var37 = true;
               JMDraw.FusedRingParams var39 = this.getFusedRingParams(new DPoint(var28, var30), var46, var1, var11, var37, var6);
               var40.sort(var39.startAtomNumber);
               this.positionRingAtoms(var6, new DPoint(0, 0), var39.startAngle, 0, var7 - 1);
               if (var6 == 4) {
                  var6 = var6;
               }

               var40.status = 1;
               this.transfer(1, var40.size() - 1, var6, new DPoint(var28, var30), var39.annulAxisOrientation, var39.toggleStartAngleSign);
               return var6;
            }
         }
      }
   }

   private double[] angleOpt(int var1, int[] var2, boolean var3) {
      double[] var4 = new double[10];
      int var5 = 0;
      int[] var19 = new int[10];
      int[] var20 = new int[10];
      double[] var21 = new double[10];
      double[] var22 = new double[10];

      for (int var23 = 0; var23 < 10; var23++) {
         var4[var23] = 0.0;
      }

      boolean var10 = true;
      int var9 = this.atomSet[var1].degree;
      if (var9 == 1) {
         var4[0] = 0.0;
         return var4;
      } else {
         int var7 = 0;

         for (int var6 = 0; var6 < var9; var6++) {
            if (this.atomSet[this.atomSet[var1].intnodeTable[var6]].status != 0) {
               var5 = var6;
               var7++;
            }
         }

         if (var7 == var9) {
            return var4;
         } else {
            double var11 = 0.0;

            for (int var24 = 0; var24 < var9; var24++) {
               var22[var24] = Math.sqrt(this.atomSet[this.atomSet[var1].intnodeTable[var24]].degree);
               if (var2[var24 + 1] != 0 && var2[var24 + 1] != 100) {
                  var22[var24] = 100000.0;
               }

               if (var2[var24 + 1] == 100) {
                  var22[var24] += 500000.0;
               }

               if (var3 && var2[var24 + 1] != 0 && var2[var24 + 1] != 100) {
                  var22[var24] = -var22[var24];
               }
            }

            var9 = this.resort(var22, var9, var1, var5, var2);
            int var8 = var9 + 1;

            for (int var25 = 0; var25 < var8; var25++) {
               if (var22[var25] > 500000.0) {
                  var22[var25] -= 500000.0;
               }

               var11 += var22[var25];
            }

            if (var2[0] == 0) {
               if (!var10) {
                  var10 = true;

                  for (int var29 = 0; var29 < var9; var29++) {
                     var19[var29] = this.atomSet[var1].intnodeTable[var29];
                     var20[var29] = this.atomSet[var1].bondTable[var29];
                     var21[var29] = var22[var29];
                  }

                  var7 = var9 + 1;

                  for (int var30 = 0; var30 < var9; var30++) {
                     this.atomSet[var1].intnodeTable[var30] = var19[--var7];
                     this.atomSet[var1].bondTable[var30] = var20[var7];
                     var22[var30] = var21[var7];
                  }
               } else {
                  var10 = false;
               }

               double var38 = 360.0 / var11;
               double var40 = 180.0 - var22[var9] * var38 / 2.0;

               for (int var31 = 0; var31 < var9; var31++) {
                  var4[var31] = var40 - var22[var31] * var38 / 2.0;
                  var40 -= var22[var31] * var38;
               }

               var4[var9] = 180.0;
               return var4;
            } else {
               var11 = 0.0;
               var8 = var9 + 1;
               if (var3) {
                  var8 = var9;
               }

               for (int var26 = 0; var26 < var8; var26++) {
                  if (Math.abs(var22[var26]) != 100000.0) {
                     var11 += var22[var26];
                  }
               }

               double var15 = 180.0 / var11;
               if (!var3) {
                  double var13 = 180.0 / var2[0];
                  double var39 = 180.0 - var22[var8 - 1] * var15 / 2.0;

                  for (int var28 = 0; var28 < var9; var28++) {
                     if (var2[var28 + 1] == 0) {
                        var4[var28] = var39 - var22[var28] * var15 / 2.0;
                        var39 -= var22[var28] * var15;
                     } else {
                        var4[var28] = var39 - var13;
                        var39 -= 90.0;
                        var13 = 90.0 - var13;
                     }
                  }

                  return var4;
               } else {
                  double var17 = 90.0;

                  for (int var27 = 1; var27 < var9; var27++) {
                     var4[var27] = var17 - var22[var27] * var15 / 2.0;
                     var17 -= var22[var27] * var15;
                  }

                  return var4;
               }
            }
         }
      }
   }

   private void handleBridgeHeads(int var1, double var2, double var4, double var6, double var8, int var10, int var11) {
      double var15 = 0.0;
      double var17 = 0.0;
      double var19 = 0.0;
      double var21 = 0.0;
      double var23 = 0.0;
      double var25 = 0.0;
      double var27 = 0.0;
      new DPoint();
      int var13 = this.atomSet[var1].degree;
      if (var13 <= 3) {
         this.atomSet[var1].status = 2;
      } else {
         var15 = var2 - var6;
         var17 = var4 - var8;
         var19 = Math.sqrt(var15 * var15 + var17 * var17);
         int var14 = 0;
         var21 = 360.0 - 360.0 / var10 - 360.0 / var11;

         for (int var12 = 0; var12 < var13; var12++) {
            if (this.atomSet[this.atomSet[var1].intnodeTable[var12]].status < 1 && this.atomSet[this.atomSet[var1].intnodeTable[var12]].ringCounter == 0) {
               var23 = var2 + this.bondLength * var15 / var19;
               var25 = var4 + this.bondLength * var17 / var19;
               var14++;
               switch (var13) {
                  case 1:
                  case 2:
                  case 3:
                  case 4:
                     this.atomSet[this.atomSet[var1].intnodeTable[var12]].x = var23;
                     this.atomSet[this.atomSet[var1].intnodeTable[var12]].y = var25;
                     break;
                  case 5:
                     if (var14 == 1) {
                        var27 = var21 / 3.0;
                     }

                     if (var14 == 2) {
                        var27 = -var21 / 3.0;
                     }

                     DPoint var36 = this.rotAtom(new DPoint(var2, var4), new DPoint(var23, var25), var27);
                     this.atomSet[this.atomSet[var1].intnodeTable[var12]].x = var36.x;
                     this.atomSet[this.atomSet[var1].intnodeTable[var12]].y = var36.y;
                     break;
                  case 6:
                     if (var14 == 1) {
                        this.atomSet[this.atomSet[var1].intnodeTable[var12]].x = var23;
                        this.atomSet[this.atomSet[var1].intnodeTable[var12]].y = var25;
                     }

                     if (var14 == 2) {
                        var27 = var21 / 4.0;
                     }

                     if (var14 == 3) {
                        var27 = -var21 / 4.0;
                     }

                     if (var14 == 2 || var14 == 3) {
                        DPoint var29 = this.rotAtom(new DPoint(var2, var4), new DPoint(var23, var25), var27);
                        this.atomSet[this.atomSet[var1].intnodeTable[var12]].x = var29.x;
                        this.atomSet[this.atomSet[var1].intnodeTable[var12]].y = var29.y;
                     }
               }

               this.atomSet[this.atomSet[var1].intnodeTable[var12]].status = 1;
               if (this.atomSet[this.atomSet[var1].intnodeTable[var12]].degree == 1) {
                  this.atomSet[this.atomSet[var1].intnodeTable[var12]].status = 2;
               }
            }
         }

         this.atomSet[var1].status = 2;
      }
   }

   private int handleRestOfRingSubstituents(int var1) {
      double var2 = 0.0174532925;
      int var22 = -1;
      int[] var63 = new int[10];
      Ring var67 = (Ring)this.sssr.elementAt(var1);
      boolean var7 = false;
      byte var8 = 0;
      byte var9 = 0;
      if (var1 == 7) {
         var8 = var8;
      }

      int var6 = var67.size();

      for (int var4 = 0; var4 < var6; var4++) {
         if (this.atomSet[var67.getNodeNumber(var4)].status != 2) {
            boolean var64 = true;

            for (int var5 = 0; var5 < 10; var5++) {
               var63[var5] = 0;
            }

            int var12 = this.atomSet[var67.getNodeNumber(var4)].degree;
            if (var12 != 0) {
               var63[0] = var6;

               for (int var68 = 0; var68 < var12; var68++) {
                  int var19 = this.atomSet[this.atomSet[var67.getNodeNumber(var4)].intnodeTable[var68]].ringCounter;
                  if (var19 != 0) {
                     int var13 = 0;

                     while (true) {
                        if (this.atomSet[this.atomSet[var67.getNodeNumber(var4)].intnodeTable[var68]].inRings[++var13 - 1] == var1) {
                           var63[var68 + 1] = var6;
                        }

                        if (var13 >= var19 || this.atomSet[this.atomSet[var67.getNodeNumber(var4)].intnodeTable[var68]].inRings[var13 - 1] == var1) {
                           break;
                        }
                     }
                  }
               }

               var22 = this.positionAtomSubstituents(
                  var67.getNodeNumber(var4),
                  new DPoint(this.atomSet[var67.getNodeNumber(var4)].x, this.atomSet[var67.getNodeNumber(var4)].y),
                  new DPoint(var67.x, var67.y),
                  var63,
                  var67.size(),
                  var8,
                  var9
               );
            }
         }
      }

      var67.status = 2;
      return var22;
   }

   private int positionAtom(int var1) {
      double var2 = 0.0174532925;
      boolean var4 = false;
      int var5 = 0;
      int var6 = 0;
      int var7 = 0;
      int var8 = 0;
      int var9 = 0;
      int var10 = 0;
      boolean var11 = false;
      boolean var12 = false;
      int var13 = 0;
      byte var14 = 0;
      byte var15 = -1;
      int[] var16 = new int[10];
      int[] var17 = new int[10];
      var10 = this.atomSet[var1].degree;
      if (var10 == 0) {
         this.atomSet[var1].status = 2;
         return var15;
      } else if (this.atomSet[var1].ringCounter == 0) {
         var9 = 0;

         for (int var24 = 0; var24 < var10; var24++) {
            if (this.atomSet[this.atomSet[var1].intnodeTable[var24]].status > 0) {
               var9++;
               var7 = this.atomSet[var1].intnodeTable[var24];
            }
         }

         if (var9 == var10) {
            this.atomSet[var1].status = 2;
            return var15;
         } else {
            for (int var25 = 0; var25 < 7; var25++) {
               var17[var25] = 0;
            }

            for (int var26 = 0; var26 < var10; var26++) {
               if (this.atomSet[this.atomSet[var1].intnodeTable[var26]].status > 0) {
                  var17[var26 + 1] = 100;
               }
            }

            var14 = 0;
            return this.positionAtomSubstituents(
               var1, new DPoint(this.atomSet[var1].x, this.atomSet[var1].y), new DPoint(this.atomSet[var7].x, this.atomSet[var7].y), var17, var14, var8, var6
            );
         }
      } else {
         var9 = 0;

         for (int var19 = 0; var19 < var10; var19++) {
            if (this.atomSet[this.atomSet[var1].intnodeTable[var19]].status < 1 && this.atomSet[this.atomSet[var1].intnodeTable[var19]].ringCounter != 0) {
               var16[++var9] = this.atomSet[var1].intnodeTable[var19];
            }
         }

         var8 = -1;
         var6 = 30;

         for (int var20 = 0; var20 < this.sssr.size(); var20++) {
            Ring var18 = (Ring)this.sssr.elementAt(var20);
            if (var18.status < 1) {
               var13 = var18.size();

               for (int var33 = 0; var33 < var13; var33++) {
                  if (var18.getNodeNumber(var33) == var1) {
                     var5 = 0;

                     for (int var32 = 0; var32 < var13; var32++) {
                        if (var18.getNodeNumber(var32) == var16[1] || var18.getNodeNumber(var32) == var16[2]) {
                           var5++;
                        }
                     }

                     if (var5 == 2 && var13 <= var6) {
                        var6 = var13;
                        var8 = var20;
                     }
                     break;
                  }
               }
            }
         }

         if (var8 == -1) {
            this.atomSet[var1].status = 2;
            return var15;
         } else {
            for (int var21 = 0; var21 < var10; var21++) {
               if (this.atomSet[this.atomSet[var1].intnodeTable[var21]].status != 0) {
                  var5 = this.atomSet[var1].intnodeTable[var21];
               }
            }

            var17[0] = var6;

            for (int var22 = 1; var22 < 7; var22++) {
               var17[var22] = 0;
            }

            for (int var23 = 0; var23 < var10; var23++) {
               if (var16[1] == this.atomSet[var1].intnodeTable[var23] || var16[2] == this.atomSet[var1].intnodeTable[var23]) {
                  var17[var23 + 1] = var6;
               }
            }

            var14 = 1;
            return this.positionAtomSubstituents(
               var1, new DPoint(this.atomSet[var1].x, this.atomSet[var1].y), new DPoint(this.atomSet[var5].x, this.atomSet[var5].y), var17, var14, var8, var6
            );
         }
      }
   }

   private double sqr(double var1) {
      return Math.pow(var1, 2.0);
   }

   protected int resort(double[] var1, int var2, int var3, int var4, int[] var5) {
      int[] var13 = new int[10];
      int[] var14 = new int[10];
      int[] var15 = new int[10];
      double[] var16 = new double[10];
      int var8 = 0;

      for (int var6 = 0; var6 < var2; var6++) {
         if (var6 != var4) {
            var16[var8] = var1[var6];
            var13[var8] = this.atomSet[var3].intnodeTable[var6];
            var14[var8] = this.atomSet[var3].bondTable[var6];
            var15[var8] = var5[var6 + 1];
            var8++;
         }
      }

      var1[var2 - 1] = var1[var4];
      this.atomSet[var3].intnodeTable[var2 - 1] = this.atomSet[var3].intnodeTable[var4];
      this.atomSet[var3].bondTable[var2 - 1] = this.atomSet[var3].bondTable[var4];
      var5[var2] = var5[var4 + 1];
      var2--;
      int var9 = 0;

      for (int var18 = 0; var18 < var2; var18++) {
         double var11 = var16[0];
         int var10 = 0;

         for (int var7 = 0; var7 < var2; var7++) {
            if (var16[var7] < var11) {
               var11 = var16[var7];
               var10 = var7;
            }
         }

         var16[var10] = 1.0E30;
         if ((var18 + 1) % 2 != 0) {
            var1[var9] = var11;
            this.atomSet[var3].intnodeTable[var9] = var13[var10];
            this.atomSet[var3].bondTable[var9] = var14[var10];
            var5[var9 + 1] = var15[var10];
         } else {
            var1[var2 - var9 - 1] = var11;
            this.atomSet[var3].intnodeTable[var2 - var9 - 1] = var13[var10];
            this.atomSet[var3].bondTable[var2 - var9 - 1] = var14[var10];
            var5[var2 - var9] = var15[var10];
            var9++;
         }
      }

      return var2;
   }

   protected void sortBondList() {
      boolean var1;
      for (int var4 = 0; var4 < this.atomSet.length; var4++) {
         do {
            var1 = false;

            for (int var5 = 0; var5 < this.atomSet[var4].degree - 1; var5++) {
               if (this.atomSet[var4].intnodeTable[var5] > this.atomSet[var4].intnodeTable[var5 + 1]) {
                  int var2 = this.atomSet[var4].intnodeTable[var5];
                  int var3 = this.atomSet[var4].bondTable[var5];
                  this.atomSet[var4].intnodeTable[var5] = this.atomSet[var4].intnodeTable[var5 + 1];
                  this.atomSet[var4].bondTable[var5] = this.atomSet[var4].bondTable[var5 + 1];
                  this.atomSet[var4].intnodeTable[var5 + 1] = var2;
                  this.atomSet[var4].bondTable[var5 + 1] = var3;
                  var1 = true;
               }
            }
         } while (!var1);
      }
   }

   public void setSize(Dimension var1) {
      this.size = var1;
   }

   public void paint(Graphics var1) {
      if (this.verbose) {
         System.out.println("JMDraw->paint(): Drawing of structure finished.");
      }

      Molecule var2 = new Molecule(this.atomSet);
      if (var2.size() != 0) {
         RenderingEngine var3 = new RenderingEngine();
         RenderingEngine.scaleIntoArea(var2, this.size, 0.8);
         RenderingEngine.translateAllPositive(var2);
         RenderingEngine.center(var2, this.size);
         var3.drawMolecule(var1, var2, this.getRenderingAttributes());
         if (this.verbose) {
            System.out.println("JMDraw->paint(): Drawing of structure finished.");
         }
      }
   }

   public RenderingAttributes getRenderingAttributes() {
      if (this.rA == null) {
         this.rA = new RenderingAttributes(new Molecule(this.atomSet));
      }

      return this.rA;
   }

   public void setRenderingAttributes(RenderingAttributes var1) {
      this.rA = var1;
   }

   class FusedRingParams {
      double startAngle;
      int annulAxisOrientation;
      double toggleStartAngleSign;
      int startAtomNumber;

      public String toString() {
         String var1 = "";
         var1 = var1 + "startAngle = " + this.startAngle + "\n";
         var1 = var1 + "annulAxisOrientation = " + this.annulAxisOrientation + "\n";
         var1 = var1 + "toggleStartAngleSign = " + this.toggleStartAngleSign + "\n";
         return var1 + "startAtomNumber = " + this.startAtomNumber;
      }
   }
}
