package jmdraw;

import compchem.Atom;
import compchem.Molecule;
import compchem.Ring;
import compchem.SetOfRingsFinder;
import compchem.StandardElements;
import compchem.setOfRings;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Point;
import java.util.Vector;
import javax.vecmath.Point2d;
import javax.vecmath.Vector2d;

public class RenderingEngine {
   String recentSymbol = "C";
   SetOfRingsFinder sorf = new SetOfRingsFinder();
   public double tripleBondSep;
   public double doubleBondSep;
   public double dotGap;

   public void drawMolecule(Graphics var1, Molecule var2, RenderingAttributes var3) {
      if (var2 != null) {
         if (var2.size() != 0) {
            boolean var4 = false;
            if (var3 == null) {
               var3 = new RenderingAttributes(var2);
            }

            Object var9 = null;
            Object var10 = null;
            Atom[] var11 = var2.getAtoms();
            if (var3.getFont() != null) {
               var1.setFont(var3.getFont());
            }

            int var12 = var1.getFont().getSize();
            FontMetrics var13 = var1.getFontMetrics();
            boolean var14 = false;
            boolean var15 = false;
            boolean var16 = false;
            boolean var17 = false;
            int[] var54 = new int[3];
            int[] var55 = new int[3];
            byte var58 = 0;
            byte var59 = 0;
            double var60 = 0.0;
            double var62 = 0.0;
            double var64 = 0.0;
            double var66 = 0.0;
            double var68 = 0.0;
            double var70 = 0.0;
            double var76 = 0.0;
            double var78 = 1.0;
            Object var80 = null;
            byte var81 = 1;
            double var82 = var1.getFont().getSize() * 0.2;
            this.doubleBondSep = var81 * var78 * var82;
            this.tripleBondSep = var81 * var78 * 2.0 * var82;
            this.dotGap = var81 * var78 * var82;
            if (var2.size() > 0) {
               var11 = var2.getAtoms();
               setOfRings var84 = this.sorf.findSSSR(var2.getAtoms());
               Vector var85 = this.getBonds(var11, var84);
               RenderingEngine.Bond var86 = null;
               var1.setColor(var3.foreColor);

               for (int var87 = 0; var87 < var85.size(); var87++) {
                  var86 = (RenderingEngine.Bond)var85.elementAt(var87);
                  int var88 = var86.atom1;
                  int var18 = var86.atom2;
                  var1.setColor(var3.foreColor);
                  Point2d var89 = new Point2d(var58 + (var11[var88].x + Math.abs(var60)) * var78, var59 + (var11[var88].y + Math.abs(var62)) * var78);
                  Point2d var90 = new Point2d(var58 + (var11[var18].x + Math.abs(var60)) * var78, var59 + (var11[var18].y + Math.abs(var62)) * var78);
                  Color var91 = var1.getColor();
                  Color var92 = var1.getColor();
                  if (var3.highlight[var11[var88].number] && var3.highlight[var18]) {
                     if (var3.substructure[var11[var88].number]) {
                        var91 = var3.getSubstructureHighlightColor();
                     } else {
                        var91 = var3.highlightColor;
                     }

                     if (var3.substructure[var11[var18].number]) {
                        var92 = var3.getSubstructureHighlightColor();
                     } else {
                        var92 = var3.highlightColor;
                     }
                  } else {
                     if (var3.substructure[var11[var88].number]) {
                        var91 = var3.getSubstructureColor();
                     }

                     if (var3.substructure[var11[var18].number]) {
                        var92 = var3.getSubstructureColor();
                     }
                  }

                  if (var86.isRingBond) {
                     this.drawSingleBond(var1, var89, var90, var91, var92);
                     var9 = (Ring)var84.elementAt(var86.inRingNumber);
                     var10 = getCenterOfGravity(new Molecule((Vector)var9));
                     if (var86.order == 4) {
                        this.drawAromaticBond(var1, var89, var90, (Point)var10, var91, var92);
                     }

                     if (var86.order == 2) {
                        this.drawRingDoubleBond(var1, var89, var90, (Point)var10, var91, var92);
                     }
                  } else {
                     switch (var86.order) {
                        case 1:
                           this.drawSingleBond(var1, var89, var90, var91, var92);
                           break;
                        case 2:
                           this.drawDoubleBond(var1, var89, var90, var81 * var78 * var82, var91, var92);
                           break;
                        case 3:
                           this.drawTripleBond(var1, var89, var90, var81 * var78 * 2.0 * var82, var91, var92);
                        case 4:
                        default:
                           break;
                        case 5:
                           this.drawWedgeBond(var1, var89, var90, var81 * var78 * 2.0 * var82);
                           break;
                        case 6:
                           this.drawWedgeBond(var1, var90, var89, var81 * var78 * 2.0 * var82);
                           break;
                        case 7:
                           this.drawDashedWedgeBond(var1, var89, var90, var81 * var78 * 2.0 * var82, 4.0);
                           break;
                        case 8:
                           this.drawDashedWedgeBond(var1, var90, var89, var81 * var78 * 2.0 * var82, 4.0);
                     }
                  }
               }

               var1.setColor(var3.foreColor);

               for (int var99 = 0; var99 < var11.length; var99++) {
                  int var5 = new Integer(var13.stringWidth(var11[var99].symbol) / 2);
                  int var6 = new Integer(var13.getAscent() / 2);
                  if (var3.substructure[var11[var99].number]) {
                     var3.foreColor = var1.getColor();
                     var1.setColor(var3.getSubstructureColor());
                     var1.fillRect((int)(var11[var99].getX() - var5 * 0.6), (int)(var11[var99].getY() - var6 * 0.5), (int)(var12 * 0.5), (int)(var12 * 0.5));
                     var1.setColor(var3.foreColor);
                  } else if (var3.highlight[var11[var99].number]) {
                     var3.foreColor = var1.getColor();
                     var1.setColor(var3.highlightColor);
                     var1.fillRect((int)(var11[var99].getX() - var5 * 0.6), (int)(var11[var99].getY() - var6 * 0.5), (int)(var12 * 0.5), (int)(var12 * 0.5));
                     var1.setColor(var3.foreColor);
                  }

                  if (var3.drawSymbols || !var11[var99].symbol.equals("C") || var11[var99].degree == 0) {
                     String var100 = var11[var99].symbol;
                     if (var3.drawImplicitHydrogens) {
                        Atom var101 = var11[var99];
                        StandardElements var103 = new StandardElements();
                        int var105 = var103.getMaxBondOrderSum(var101.symbol);
                        int var93 = var105 - var11[var99].getBondCount() + var11[var99].charge;
                        if (var93 > 0) {
                           var100 = var100 + "H";
                           if (var93 > 1) {
                              var100 = var100 + new Integer(var93).toString();
                           }
                        }
                     }

                     var1.setColor(var3.backColor);
                     if (!var3.highlight[var11[var99].number]) {
                        var1.fillRect((int)(var11[var99].getX() - var5 * 1.8), (int)(var11[var99].getY() - var6 * 0.8), var12 * var100.length(), var12);
                     }

                     var1.setColor(var3.foreColor);
                     var1.drawString(var100, (int)var11[var99].getX() - var5, (int)var11[var99].getY() + var6);
                     if (var11[var99].charge != 0) {
                        Font var102 = var1.getFont();
                        float var104 = var1.getFont().getSize2D();
                        var1.setFont(var102.deriveFont((float)(var104 - 2.0)));
                        String var106 = new Integer(var11[var99].charge).toString();
                        if (var11[var99].charge == 1) {
                           var106 = "+";
                        } else if (var11[var99].charge > 1) {
                           var106 = var106 + "+";
                        } else if (var11[var99].charge == -1) {
                           var106 = "-";
                        } else if (var11[var99].charge < -1) {
                           var106 = var106.substring(1) + "-";
                        }

                        var1.drawString(var106, (int)var11[var99].getX() + var5, (int)var11[var99].getY() - var6);
                        var1.setFont(var102);
                     }

                     if (var3.drawHydrogens && var11[var99].HCount > 0) {
                        int var56 = (int)var11[var99].getX() - var5 + var13.stringWidth(var11[var99].symbol);
                        var1.drawString("H", var56, (int)var11[var99].getY() + var6);
                        if (var11[var99].HCount > 1) {
                           var56 += var13.stringWidth("H");
                           var1.drawString(new Integer(var11[var99].HCount).toString(), var56, (int)var11[var99].getY() + 2 * var6);
                        }
                     }
                  }

                  if (var3.drawNumbers || var3.drawNumber[var99]) {
                     var1.drawString(new Integer(var99).toString(), (int)var11[var99].getX() + var5, (int)var11[var99].getY() - var6);
                  }
               }
            }
         }
      }
   }

   void drawSingleBond(Graphics var1, Point2d var2, Point2d var3) {
      var1.drawLine((int)var2.x, (int)var2.y, (int)var3.x, (int)var3.y);
   }

   void drawSingleBond(Graphics var1, Point2d var2, Point2d var3, Color var4, Color var5) {
      Point2d var6 = new Point2d((var2.x + var3.x) / 2.0, (var2.y + var3.y) / 2.0);
      var1.setColor(var4);
      var1.drawLine((int)var2.x, (int)var2.y, (int)var6.x, (int)var6.y);
      var1.setColor(var5);
      var1.drawLine((int)var3.x, (int)var3.y, (int)var6.x, (int)var6.y);
   }

   void drawTripleBond(Graphics var1, Point2d var2, Point2d var3, double var4) {
      this.drawSingleBond(var1, var2, var3);
      this.drawDoubleBond(var1, var2, var3, var4);
   }

   void drawTripleBond(Graphics var1, Point2d var2, Point2d var3, double var4, Color var6, Color var7) {
      this.drawSingleBond(var1, var2, var3, var6, var7);
      this.drawDoubleBond(var1, var2, var3, var4, var6, var7);
   }

   void drawDoubleBond(Graphics var1, Point2d var2, Point2d var3, double var4) {
      Vector2d var6 = this.calculatePerpendicularUnitVector(var2, var3);
      var6.scale(var4);
      Point2d var7 = new Point2d(var2);
      Point2d var8 = new Point2d(var3);
      var7.add(var6);
      var8.add(var6);
      this.drawSingleBond(var1, var7, var8);
      var2.sub(var6);
      var3.sub(var6);
      this.drawSingleBond(var1, var2, var3);
   }

   void drawDoubleBond(Graphics var1, Point2d var2, Point2d var3, double var4, Color var6, Color var7) {
      Vector2d var8 = this.calculatePerpendicularUnitVector(var2, var3);
      var8.scale(var4);
      Point2d var9 = new Point2d(var2);
      Point2d var10 = new Point2d(var3);
      var9.add(var8);
      var10.add(var8);
      this.drawSingleBond(var1, var9, var10, var6, var7);
      var2.sub(var8);
      var3.sub(var8);
      this.drawSingleBond(var1, var2, var3, var6, var7);
   }

   void drawAromaticBond(Graphics var1, Point2d var2, Point2d var3, Point var4) {
      Vector2d var5 = this.calculateBondVector(var2, var3);
      Vector2d var6 = new Vector2d(var5);
      var5.scale(var2.distance(var3) * 0.5);
      var6.scale(var2.distance(var3) * 0.4);
      Point2d var7 = new Point2d(var2);
      var7.add(var5);
      Point2d var8 = new Point2d(var7);
      Point2d var9 = new Point2d(var7);
      Point2d var10 = new Point2d(var4.x, var4.y);
      Vector2d var11 = this.calculatePerpendicularUnitVector(var8, var3);
      var11.scale(this.tripleBondSep);
      var8.add(var11);
      var9.sub(var11);
      Point2d var12 = new Point2d(var8);
      Point2d var13 = new Point2d(var8);
      Point2d var14 = new Point2d(var9);
      Point2d var15 = new Point2d(var9);
      var12.add(var6);
      var13.sub(var6);
      var14.add(var6);
      var15.sub(var6);
      if (var12.distance(var10) > var14.distance(var10)) {
         this.drawDottedBond(var1, var14, var15);
      } else {
         this.drawDottedBond(var1, var12, var13);
      }
   }

   void drawAromaticBond(Graphics var1, Point2d var2, Point2d var3, Point var4, Color var5, Color var6) {
      Vector2d var7 = this.calculateBondVector(var2, var3);
      Vector2d var8 = new Vector2d(var7);
      var7.scale(var2.distance(var3) * 0.5);
      var8.scale(var2.distance(var3) * 0.4);
      Point2d var9 = new Point2d(var2);
      var9.add(var7);
      Point2d var10 = new Point2d(var9);
      Point2d var11 = new Point2d(var9);
      Point2d var12 = new Point2d(var4.x, var4.y);
      Vector2d var13 = this.calculatePerpendicularUnitVector(var10, var3);
      var13.scale(this.tripleBondSep);
      var10.add(var13);
      var11.sub(var13);
      Point2d var14 = new Point2d(var10);
      Point2d var15 = new Point2d(var10);
      Point2d var16 = new Point2d(var11);
      Point2d var17 = new Point2d(var11);
      var14.add(var8);
      var15.sub(var8);
      var16.add(var8);
      var17.sub(var8);
      if (var14.distance(var12) > var16.distance(var12)) {
         this.drawDottedBond(var1, var16, var17, var5, var6);
      } else {
         this.drawDottedBond(var1, var14, var15, var5, var6);
      }
   }

   void drawRingDoubleBond(Graphics var1, Point2d var2, Point2d var3, Point var4) {
      Vector2d var5 = this.calculateBondVector(var2, var3);
      Vector2d var6 = new Vector2d(var5);
      var5.scale(var2.distance(var3) * 0.5);
      var6.scale(var2.distance(var3) * 0.4);
      Point2d var7 = new Point2d(var2);
      var7.add(var5);
      Point2d var8 = new Point2d(var7);
      Point2d var9 = new Point2d(var7);
      Point2d var10 = new Point2d(var4.x, var4.y);
      Vector2d var11 = this.calculatePerpendicularUnitVector(var8, var3);
      var11.scale(this.tripleBondSep);
      var8.add(var11);
      var9.sub(var11);
      Point2d var12 = new Point2d(var8);
      Point2d var13 = new Point2d(var8);
      Point2d var14 = new Point2d(var9);
      Point2d var15 = new Point2d(var9);
      var12.add(var6);
      var13.sub(var6);
      var14.add(var6);
      var15.sub(var6);
      if (var12.distance(var10) > var14.distance(var10)) {
         this.drawSingleBond(var1, var14, var15);
      } else {
         this.drawSingleBond(var1, var12, var13);
      }
   }

   void drawRingDoubleBond(Graphics var1, Point2d var2, Point2d var3, Point var4, Color var5, Color var6) {
      Vector2d var7 = this.calculateBondVector(var2, var3);
      Vector2d var8 = new Vector2d(var7);
      var7.scale(var2.distance(var3) * 0.5);
      var8.scale(var2.distance(var3) * 0.4);
      Point2d var9 = new Point2d(var2);
      var9.add(var7);
      Point2d var10 = new Point2d(var9);
      Point2d var11 = new Point2d(var9);
      Point2d var12 = new Point2d(var4.x, var4.y);
      Vector2d var13 = this.calculatePerpendicularUnitVector(var10, var3);
      var13.scale(this.tripleBondSep);
      var10.add(var13);
      var11.sub(var13);
      Point2d var14 = new Point2d(var10);
      Point2d var15 = new Point2d(var10);
      Point2d var16 = new Point2d(var11);
      Point2d var17 = new Point2d(var11);
      var14.add(var8);
      var15.sub(var8);
      var16.add(var8);
      var17.sub(var8);
      if (var14.distance(var12) > var16.distance(var12)) {
         this.drawSingleBond(var1, var16, var17, var5, var6);
      } else {
         this.drawSingleBond(var1, var14, var15, var5, var6);
      }
   }

   Vector2d calculatePerpendicularUnitVector(Point2d var1, Point2d var2) {
      Vector2d var3 = new Vector2d();
      var3.sub(var2, var1);
      var3.normalize();
      return new Vector2d(-1.0 * var3.y, var3.x);
   }

   Vector2d calculateBondVector(Point2d var1, Point2d var2) {
      Vector2d var3 = new Vector2d();
      var3.sub(var2, var1);
      var3.normalize();
      return var3;
   }

   void drawDottedBond(Graphics var1, Point2d var2, Point2d var3) {
      double var4 = var2.distance(var3);
      int var6 = (int)(var4 / this.dotGap);
      Vector2d var7 = new Vector2d(var3);
      var7.sub(var2);
      var7.scale(1.0 / var6);
      Point2d var8 = new Point2d(var2);

      for (int var9 = 0; var9 <= var6; var9++) {
         var1.drawLine((int)var8.x, (int)var8.y, (int)var8.x, (int)var8.y);
         var8.add(var7);
      }
   }

   void drawDottedBond(Graphics var1, Point2d var2, Point2d var3, Color var4, Color var5) {
      new Point2d((var2.x + var3.x) / 2.0, (var2.y + var3.y) / 2.0);
      double var7 = var2.distance(var3);
      int var9 = (int)(var7 / this.dotGap);
      Vector2d var10 = new Vector2d(var3);
      var10.sub(var2);
      var10.scale(1.0 / var9);
      Point2d var11 = new Point2d(var2);
      var1.setColor(var5);

      int var12;
      for (var12 = 0; var12 <= var9 / 2; var12++) {
         var1.drawLine((int)var11.x, (int)var11.y, (int)var11.x, (int)var11.y);
         var11.add(var10);
      }

      var1.setColor(var4);

      while (var12 <= var9) {
         var1.drawLine((int)var11.x, (int)var11.y, (int)var11.x, (int)var11.y);
         var11.add(var10);
         var12++;
      }
   }

   void drawWedgeBond(Graphics var1, Point2d var2, Point2d var3, double var4) {
      Vector2d var6 = this.calculatePerpendicularUnitVector(var2, var3);
      var6.scale(var4);
      Point2d var7 = new Point2d(var3);
      Point2d var8 = new Point2d(var3);
      var7.add(var6);
      var8.sub(var6);
      int[] var9 = new int[3];
      int[] var10 = new int[3];
      var9[0] = (int)var2.x;
      var9[1] = (int)var7.x;
      var9[2] = (int)var8.x;
      var10[0] = (int)var2.y;
      var10[1] = (int)var7.y;
      var10[2] = (int)var8.y;
      var1.drawPolygon(var9, var10, 3);
      var1.fillPolygon(var9, var10, 3);
   }

   void drawDashedWedgeBond(Graphics var1, Point2d var2, Point2d var3, double var4, double var6) {
      double var8 = var2.distance(var3);
      int var10 = (int)(var8 / var6);
      double var11 = var4 / var10;
      Vector2d var13 = new Vector2d(var3);
      var13.sub(var2);
      var13.scale(1.0 / var10);
      Vector2d var14 = this.calculatePerpendicularUnitVector(var2, var3);
      Point2d var15 = new Point2d(var2);
      Point2d var16 = new Point2d();
      Point2d var17 = new Point2d();

      for (int var18 = 0; var18 <= var10; var18++) {
         Vector2d var19 = new Vector2d(var14);
         var19.scale(var18 * var11);
         var16.add(var15, var19);
         var17.sub(var15, var19);
         var1.drawLine((int)var16.x, (int)var16.y, (int)var17.x, (int)var17.y);
         var15.add(var13);
      }
   }

   private Vector getBonds(Atom[] var1, setOfRings var2) {
      Vector var3 = new Vector();
      RenderingEngine.Bond var4 = null;
      Object var5 = null;

      for (int var6 = 0; var6 < var1.length; var6++) {
         for (int var7 = 0; var7 < var1[var6].degree; var7++) {
            if (var1[var6].number < var1[var6].nodeTable[var7].number) {
               var4 = new RenderingEngine.Bond(var1[var6].number, var1[var6].nodeTable[var7].number, var1[var6].bondTable[var7]);
               if (var1[var6].bondTable[var7] == 4 || var1[var6].bondTable[var7] == 2) {
                  for (int var8 = 0; var8 < var2.size(); var8++) {
                     var5 = (Ring)var2.elementAt(var8);
                     if (var5.isInRing(var1[var6].number) && var5.isInRing(var1[var6].nodeTable[var7].number)) {
                        var4.isRingBond = true;
                        var4.inRingNumber = var8;
                     }
                  }
               }

               var3.addElement(var4);
            }
         }
      }

      return var3;
   }

   public static void translate(Molecule var0, Dimension var1) {
      Atom[] var2 = var0.getAtoms();
      Object var3 = null;

      for (int var4 = 0; var4 < var2.length; var4++) {
         var2[var4].x = var2[var4].x + var1.width;
         var2[var4].y = var2[var4].y + var1.height;
      }
   }

   public static void translateFromOrigin(Molecule var0, Dimension var1) {
      Atom[] var2 = var0.getAtoms();
      Object var3 = null;
      double var4 = Double.MAX_VALUE;
      double var6 = Double.MAX_VALUE;

      for (int var8 = 0; var8 < var2.length; var8++) {
         if (var2[var8].x < var4) {
            var4 = var2[var8].x;
         }

         if (var2[var8].y < var6) {
            var6 = var2[var8].y;
         }
      }

      for (int var9 = 0; var9 < var2.length; var9++) {
         var2[var9].x = var2[var9].x + (var1.width - var4);
         var2[var9].y = var2[var9].y + (var1.height - var6);
      }
   }

   public static void translateAllPositive(Molecule var0) {
      Atom[] var1 = var0.getAtoms();
      double var2 = 0.0;
      double var4 = 0.0;
      double var6 = var1[0].x;
      double var8 = var1[0].y;
      double var10 = var6;
      double var12 = var8;

      for (int var14 = 0; var14 < var1.length; var14++) {
         if (var1[var14].x > var6) {
            var6 = var1[var14].x;
         }

         if (var1[var14].y > var8) {
            var8 = var1[var14].y;
         }

         if (var1[var14].x < var10) {
            var10 = var1[var14].x;
         }

         if (var1[var14].y < var12) {
            var12 = var1[var14].y;
         }
      }

      if (var10 < 0.0) {
         var2 = Math.abs(var10);
      }

      if (var12 < 0.0) {
         var4 = Math.abs(var12);
      }

      for (int var15 = 0; var15 < var1.length; var15++) {
         var1[var15].x += var2;
         var1[var15].y += var4;
      }
   }

   public static void scale(Molecule var0, double var1) {
      Atom[] var3 = var0.getAtoms();

      for (int var4 = 0; var4 < var3.length; var4++) {
         var3[var4].x *= var1;
         var3[var4].y *= var1;
      }
   }

   public static Dimension getDimension(Molecule var0) {
      Atom[] var1 = var0.getAtoms();
      if (var1.length < 1) {
         return new Dimension(0, 0);
      } else {
         double var2 = 0.0;
         double var4 = 0.0;
         double var6 = Double.MIN_VALUE;
         double var8 = Double.MIN_VALUE;
         double var10 = Double.MAX_VALUE;
         double var12 = Double.MAX_VALUE;

         for (int var14 = 0; var14 < var1.length; var14++) {
            if (var1[var14].x > var6) {
               var6 = var1[var14].x;
            }

            if (var1[var14].y > var8) {
               var8 = var1[var14].y;
            }

            if (var1[var14].x < var10) {
               var10 = var1[var14].x;
            }

            if (var1[var14].y < var12) {
               var12 = var1[var14].y;
            }
         }

         return new Dimension((int)(var6 - var10 + 1.0), (int)(var8 - var12 + 1.0));
      }
   }

   public static void scaleIntoArea(Molecule var0, Dimension var1, double var2) {
      Dimension var4 = getDimension(var0);
      double var5 = (double)var1.width / var4.width;
      double var7 = (double)var1.height / var4.height;
      double var9 = Math.min(var5, var7) * var2;
      scale(var0, var9);
   }

   public static void center(Molecule var0, Dimension var1) {
      Dimension var2 = getDimension(var0);
      int var3 = (var1.width - var2.width) / 2;
      int var4 = (var1.height - var2.height) / 2;
      translateFromOrigin(var0, new Dimension(var3, var4));
   }

   public static Point getCenterOfGravity(Molecule var0) {
      int var1 = 0;
      int var2 = 0;

      for (int var3 = 0; var3 < var0.size(); var3++) {
         var1 = (int)(var1 + var0.atomAt(var3).x);
         var2 = (int)(var2 + var0.atomAt(var3).y);
      }

      return new Point(var1 / var0.size(), var2 / var0.size());
   }

   class Bond {
      int atom1;
      int atom2;
      int order;
      boolean isRingBond = false;
      int inRingNumber;

      Bond(int var2, int var3, int var4) {
         this.atom1 = var2;
         this.atom2 = var3;
         this.order = var4;
      }

      public String toString() {
         String var1 = "Bond from atom " + this.atom1 + " to atom " + this.atom2 + " with order " + this.order + "\n";
         return var1 + "is Ring bond: " + this.isRingBond + "; inRingNumber: " + this.inRingNumber;
      }
   }
}
