package compchem;

import java.io.Serializable;
import java.util.Enumeration;
import java.util.Vector;

public class SetOfRingsFinder implements Serializable {
   public boolean debug = false;
   public boolean debug1 = false;
   public boolean debug2 = false;
   public boolean debug3 = false;
   public int ringCounter = 0;
   public boolean storeRings = true;
   Atom[] FGsource;

   public setOfRings findSSSR(Atom[] var1) {
      this.ringCounter = 0;
      setOfRings var2 = new setOfRings();
      Atom[] var3 = DataStructureTools.cloneAtomSet(var1);
      int var4 = 0;

      while (var4 < var3.length) {
         var3[var4].number = var4++;
      }

      this.FGsource = new Atom[var3.length];
      Vector var11 = new Vector();
      Vector var12 = new Vector();
      Vector var13 = new Vector();

      for (int var14 = 0; var14 < var3.length; var14++) {
         var11.addElement(var3[var14]);
      }

      do {
         int var5 = 7;
         Atom var6 = null;
         var13.removeAllElements();

         for (int var15 = 0; var15 < var3.length; var15++) {
            if (var3[var15].degree == 0 && !var12.contains(var3[var15])) {
               var12.addElement(var3[var15]);
            }

            if (var3[var15].degree == 2 && !var12.contains(var3[var15])) {
               var13.addElement(var3[var15]);
            }

            if (var3[var15].degree < var5 && var3[var15].degree > 0) {
               var6 = var3[var15];
               var5 = var3[var15].degree;
            }
         }

         if (var6 == null) {
            break;
         }

         if (var6.degree == 1) {
            this.trim(var6, var3);
            var12.addElement(var6);
         } else if (var6.degree == 2) {
            Atom[] var9 = new Atom[var13.size()];
            int var10 = 0;

            for (int var16 = 0; var16 < var13.size(); var16++) {
               Ring var7 = this.findSRing((Atom)var13.elementAt(var16), var3);
               if (var7.size() > 0 && !var2.ringAlreadyInSet(var7)) {
                  var2.addElement(var7);
                  var9[var10] = (Atom)var13.elementAt(var16);
                  var10++;
               }
            }

            if (var10 == 0) {
               var10 = 1;
               var9[0] = (Atom)var13.elementAt(0);
            }

            for (int var17 = 0; var17 < var10; var17++) {
               this.breakBond(var9[var17], var3);
            }
         } else if (var6.degree == 3) {
            Ring var18 = this.findSRing(var6, var3);
            if (var18.size() > 0 && !var2.ringAlreadyInSet(var18)) {
               var2.addElement(var18);
            }

            Bond var8 = this.checkEdges(var18, var3);
            this.breakBond(var8.getAtom(0), var8.getAtom(1), var3);
         }
      } while (var12.size() < var11.size());

      this.ringCounter = var2.size();
      return var2;
   }

   public Ring findSRing(Atom var1, Atom[] var2) {
      int var8 = var2.length;
      Queue var9 = new Queue();
      new Ring();
      Vector[] var11 = new Vector[var8];
      new Vector();
      new Vector();

      for (int var14 = 0; var14 < var8; var14++) {
         var11[var14] = new Vector();
      }

      for (int var15 = 0; var15 < var1.degree; var15++) {
         Atom var4 = var1.nodeTable[var15];
         if (var4.degree > 0) {
            this.FGsource[var4.number] = var1;
            var9.push(var4);
            var11[var4.number].addElement(var1.nodeTable[var15]);
            var11[var4.number].addElement(var1);
         }
      }

      while (var9.size() > 0) {
         Atom var3 = (Atom)var9.pop();
         Atom var7 = var3;
         Atom var5 = this.FGsource[var3.number];

         for (int var16 = 0; var16 < var3.degree; var16++) {
            Atom var6 = var3.nodeTable[var16];
            if (var6.degree > 0 && var6 != var5) {
               if (var11[var6.number].size() > 0) {
                  Vector var12 = this.getIntersection(var11[var7.number], var11[var6.number]);
                  if (var12.size() == 1) {
                     if (this.debug) {
                        System.out.println("Ring closure found: " + var6.number + ", source is " + var5.number);
                        System.out.println(var6 + ", " + var5);
                     }

                     Vector var13 = this.getUnion(var11[var7.number], var11[var6.number]);
                     return this.prepareRing(var13, var2);
                  }
               } else {
                  var11[var6.number] = this.merge(var11[var6.number], var11[var7.number]);
                  var11[var6.number].insertElementAt(var6, 0);
                  this.FGsource[var6.number] = var7;
                  var9.push(var6);
               }
            }
         }
      }

      return new Ring();
   }

   public Vector getIntersection(Vector var1, Vector var2) {
      Vector var3 = new Vector();

      for (int var4 = 0; var4 < var1.size(); var4++) {
         if (var2.contains(var1.elementAt(var4))) {
            var3.addElement(var1.elementAt(var4));
         }
      }

      return var3;
   }

   public Vector getUnion(Vector var1, Vector var2) {
      Vector var3 = this.shallowClone(var1);

      for (int var4 = var2.size() - 1; var4 > -1; var4--) {
         if (!var1.contains(var2.elementAt(var4))) {
            var3.addElement(var2.elementAt(var4));
         }
      }

      return var3;
   }

   public Vector merge(Vector var1, Vector var2) {
      Vector var3 = this.shallowClone(var1);

      for (int var4 = 0; var4 < var2.size(); var4++) {
         var3.addElement(var2.elementAt(var4));
      }

      return var3;
   }

   public Ring prepareRing(Vector var1, Atom[] var2) {
      Ring var3 = new Ring();

      for (int var4 = 0; var4 < var1.size(); var4++) {
         var3.addElement(var1.elementAt(var4));
      }

      var3.sort();
      return var3;
   }

   public void trim(Atom var1, Atom[] var2) {
      for (int var4 = 0; var4 < var1.degree; var4++) {
         Atom var3 = var1.nodeTable[var4];

         for (int var5 = 0; var5 < var3.degree; var5++) {
            if (var3.nodeTable[var5].equals(var1)) {
               if (var5 < var3.nodeTable.length - 1) {
                  for (int var6 = var5; var6 < var3.degree - 1; var6++) {
                     var3.nodeTable[var6] = var3.nodeTable[var6 + 1];
                  }
               }

               var3.nodeTable[var3.degree - 1] = null;
               var3.degree--;
            }
         }

         var1.nodeTable[var4] = null;
      }

      var1.degree = 0;
   }

   public void breakBond(Atom var1, Atom[] var2) {
      int var3 = var1.degree;
      Atom var4 = var1.nodeTable[var3 - 1];
      var1.nodeTable[var3 - 1] = null;
      var1.degree--;

      for (int var5 = 0; var5 < var4.degree; var5++) {
         if (var4.nodeTable[var5] == var1) {
            var4.nodeTable[var5] = null;

            for (int var6 = var5; var6 < var4.degree - 1; var6++) {
               var4.nodeTable[var6] = var4.nodeTable[var6 + 1];
            }

            var4.nodeTable[var4.degree - 1] = null;
            var4.degree--;
            break;
         }
      }
   }

   public void breakBond(Atom var1, Atom var2, Atom[] var3) {
      for (int var5 = 0; var5 < var1.degree; var5++) {
         if (var1.nodeTable[var5] == var2) {
            int var4 = var1.degree;

            for (int var6 = var5; var6 < var1.degree - 1; var6++) {
               var1.nodeTable[var6] = var1.nodeTable[var6 + 1];
            }

            var1.nodeTable[var1.degree - 1] = null;
            var1.degree--;
            break;
         }
      }

      for (int var8 = 0; var8 < var2.degree; var8++) {
         if (var2.nodeTable[var8] == var1) {
            for (int var7 = var8; var7 < var2.degree - 1; var7++) {
               var2.nodeTable[var7] = var2.nodeTable[var7 + 1];
            }

            var2.nodeTable[var2.degree - 1] = null;
            var2.degree--;
            break;
         }
      }
   }

   protected void restoreBond(Atom var1, Atom var2, Atom[] var3) {
      var1.nodeTable[var1.degree] = var2;
      var1.degree++;
      var2.nodeTable[var2.degree] = var1;
      var2.degree++;
   }

   protected Bond checkEdges(Ring var1, Atom[] var2) {
      setOfRings var6 = new setOfRings();

      for (int var9 = 0; var9 < var1.size(); var9++) {
         Bond var3 = var1.getEdge(var9);
         this.breakBond(var3.getAtom(0), var3.getAtom(1), var2);
         Ring var4 = this.findSRing(var3.getAtom(0), var2);
         Ring var5 = this.findSRing(var3.getAtom(1), var2);
         if (var4.size() > var5.size()) {
            var6.addElement(var4);
         } else {
            var6.addElement(var4);
         }

         this.restoreBond(var3.getAtom(0), var3.getAtom(1), var2);
      }

      int var8 = ((Ring)var6.elementAt(0)).size();
      int var7 = 0;

      for (int var10 = 0; var10 < var6.size(); var10++) {
         if (((Ring)var6.elementAt(var10)).size() < var8) {
            var8 = ((Ring)var6.elementAt(var10)).size();
            var7 = var10;
         }
      }

      return var1.getEdge(var7);
   }

   public RingSet getAllRings(Atom[] var1) {
      long var2 = System.currentTimeMillis();
      this.ringCounter = 0;
      RingSet var6 = new RingSet();
      Vector var7 = new Vector(10000);
      if (this.debug1) {
         System.out.println("This is  method getAllRings(Atom[]) in class SetOfRingsFinder");
      }

      if (this.debug1) {
         System.out.println("Converting molecule to path graph");
      }

      Molecule var8 = new Molecule(DataStructureTools.cloneAtomSet(var1));
      this.removeAliphatic(var8);

      for (int var9 = 0; var9 < var1.length; var9++) {
         for (int var10 = 0; var10 < var1[var9].degree; var10++) {
            if (var1[var9].number > var1[var9].nodeTable[var10].number) {
               var7.addElement(new Path(var1[var9].number, var1[var9].nodeTable[var10].number, var1.length));
            }
         }
      }

      if (this.debug1) {
         System.out.println("Convertion to path graph done.");
      }

      if (this.debug1) {
         System.out.println("Reducing path graph...");
      }

      while (var8.size() > 0) {
         this.remove(this.selectAtom(var8), var8, var7, var6);
      }

      long var4 = System.currentTimeMillis();
      if (this.debug1) {
         System.out.println("Reduction of path graph done.");
      }

      if (this.debug) {
         System.out.println("SetOfAllRings search done: ");
      }

      if (this.debug) {
         System.out.println("Found " + this.ringCounter + " rings.");
      }

      if (this.debug) {
         System.out.println(DataStructureTools.getDuration(var2, var4));
      }

      return var6;
   }

   private void remove(Atom var1, Molecule var2, Vector var3, Vector var4) {
      if (this.debug) {
         System.out.println("*** Removing atom number " + var1.number + " ***");
      }

      Vector var5 = new Vector();
      new Vector();
      Path var7 = null;
      Object var8 = null;
      Path var9 = null;
      int var10 = var1.number;
      int var11 = 0;
      boolean[] var12 = new boolean[var3.size()];

      for (int var13 = 0; var13 < var3.size(); var13++) {
         var7 = (Path)var3.elementAt(var13);
         if (var10 == var7.getLast() || var10 == var7.getFirst()) {
            for (int var14 = var13 + 1; var14 < var3.size(); var14++) {
               var8 = (Path)var3.elementAt(var14);
               if (var10 == ((Path)var8).getLast() || var10 == ((Path)var8).getFirst()) {
                  var11 = var7.getIntersectionSize((Path)var8);
                  if (var11 < 3) {
                     if (this.debug2) {
                        System.out.println("Ready to merge pathes " + var7 + " and " + var8);
                     }

                     if (this.debug2) {
                        System.out.println("Intersection size = " + var11);
                     }

                     var9 = Path.join(var7, (Path)var8, var10);
                     if (var11 == 1) {
                        if (this.debug2) {
                           System.out.println("### Adding union " + var9);
                        }

                        if (this.debug2) {
                           System.out.println("Deleting " + var7 + " and " + var8);
                        }

                        var5.addElement(var9);
                        var12[var13] = true;
                        var12[var14] = true;
                     } else if (var9.getLast() == var9.getFirst()) {
                        int var15 = var9.getLast();
                        var9.removeLast();
                        var9.isMember[var15] = true;
                        if (this.storeRings) {
                           var4.addElement(var9);
                        }

                        this.ringCounter++;
                        var12[var13] = true;
                        var12[var14] = true;
                     }
                  }
               }
            }
         }
      }

      for (int var20 = var12.length - 1; var20 >= 0; var20--) {
         if (var12[var20]) {
            var3.removeElementAt(var20);
         }
      }

      for (int var21 = 0; var21 < var5.size(); var21++) {
         var3.addElement(var5.elementAt(var21));
      }

      var2.removeAtom(var1);
      if (this.debug) {
         System.out.println("*** Pathes left after removal of atom number " + var1.number + ": " + var3.size() + " ***");
      }

      if (this.debug) {
         System.out.println("*** Rings after removal of atom number " + var1.number + ": " + var4.size() + " ***");
      }
   }

   private Atom selectAtom(Molecule var1) {
      int var2 = 99999;
      Atom var3 = null;

      for (int var4 = 0; var4 < var1.size(); var4++) {
         if (var1.atomAt(var4).degree < var2) {
            var3 = var1.atomAt(var4);
         }
      }

      return var3;
   }

   private void removeAliphatic(Molecule var1) {
      Object var3 = null;

      boolean var2;
      do {
         var2 = false;
         Enumeration var4 = var1.getAtomsVector().elements();

         while (var4.hasMoreElements()) {
            var3 = (Atom)var4.nextElement();
            if (((Atom)var3).degree == 1) {
               var1.removeAtom((Atom)var3);
               if (this.debug1) {
                  System.out.println("Removed aliphatic atom no " + ((Atom)var3).number);
               }

               var2 = true;
            }
         }
      } while (var2);
   }

   public Vector shallowClone(Vector var1) {
      Vector var2 = new Vector();

      for (int var3 = 0; var3 < var1.size(); var3++) {
         var2.addElement(var1.elementAt(var3));
      }

      return var2;
   }

   class FGSearchAtom extends Atom {
      Atom source;
   }
}
