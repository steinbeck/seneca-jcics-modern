package seneca.structgen;

import compchem.Atom;
import compchem.DataStructureTools;
import compchem.Molecule;
import java.io.Serializable;

public class SingleRandomStructureGenerator extends StructureGenerator implements Serializable, Cloneable {
   Atom[] latestResult;
   int[][] maxBondOrders;

   public SingleRandomStructureGenerator() {
      super("SingleRandomGent");
   }

   public void reset() {
   }

   public void execute() {
   }

   public Object getStatus() {
      return null;
   }

   public Atom[] generate() {
      boolean var1 = false;
      super.nrOfAtoms = super.atomSet.length;

      for (int var9 = 0; var9 < super.atomSet.length; var9++) {
         super.atomSet[var9].reset();
      }

      do {
         super.iteration++;
         var1 = false;

         boolean var2;
         do {
            var2 = false;

            for (int var10 = 0; var10 < super.atomSet.length; var10++) {
               Thread.yield();
               if (!super.atomSet[var10].isSaturated()) {
                  int var3 = this.getAnotherUnsaturatedNode(var10);
                  if (var3 != -1) {
                     int var5 = Math.min(super.atomSet[var10].getCurrentMaxBondOrder(), super.atomSet[var3].getCurrentMaxBondOrder());
                     int var6 = Math.max(1, (int)(Math.random() * var5));
                     super.atomSet[var10].formBond(super.atomSet[var3], var6);
                     super.atomSet[var3].formBond(super.atomSet[var10], var6);
                     var2 = true;
                  }
               }
            }
         } while (var2);

         int var7 = this.getUnsaturatedCount();
         super.attic.atomSet = super.atomSet;
         super.bondMatrix = DataStructureTools.createConnectionTable(super.attic.atomSet);
         super.attic.apsp = super.spf.computeFloydAPSP(super.bondMatrix);
         if (var7 == 0 && this.structureConnected(super.bondMatrix)) {
            return super.atomSet;
         }

         for (int var12 = 0; var12 < super.atomSet.length; var12++) {
            super.atomSet[var12].reset();
         }
      } while (!var1);

      return null;
   }

   int getAnotherUnsaturatedNode(int var1) {
      int var2 = (int)(Math.random() * super.atomSet.length);

      for (int var3 = var2; var3 < super.atomSet.length; var3++) {
         if (!super.atomSet[var3].isSaturated() && var3 != var1 && !super.atomSet[var3].isBondedTo(super.atomSet[var1])) {
            return var3;
         }
      }

      for (int var4 = 0; var4 < var2; var4++) {
         if (!super.atomSet[var4].isSaturated() && var4 != var1 && !super.atomSet[var4].isBondedTo(super.atomSet[var1])) {
            return var4;
         }
      }

      return -1;
   }

   private int getUnsaturatedCount() {
      int var1 = 0;

      for (int var2 = 0; var2 < super.atomSet.length; var2++) {
         if (!super.atomSet[var2].isSaturated()) {
            var1++;
         }
      }

      return var1;
   }

   private boolean nodeStatusOk(int var1) {
      return super.atomSet[var1].maxBondOrderSum >= super.atomSet[var1].HCount + super.sumOfBonds[var1];
   }

   private boolean nodeStatusPerfect(int var1) {
      return super.atomSet[var1].maxBondOrderSum == super.atomSet[var1].HCount + super.sumOfBonds[var1];
   }

   private boolean endOfMatrix(int var1, int var2) {
      return var2 >= super.nrOfAtoms - 1 && var1 >= super.nrOfAtoms - 2;
   }

   private void sortNodes() {
      int var3 = 0;

      while (var3 < super.atomSet.length) {
         super.atomSet[var3].number = var3++;
      }

      boolean var1;
      do {
         var1 = true;

         for (int var4 = 0; var4 < super.atomSet.length - 1; var4++) {
            if (super.atomSet[var4].HCount > super.atomSet[var4 + 1].HCount) {
               Atom var2 = super.atomSet[var4 + 1];
               super.atomSet[var4 + 1] = super.atomSet[var4];
               super.atomSet[var4] = var2;
               var1 = false;
            }
         }
      } while (!var1);

      int var5 = 0;

      while (var5 < super.atomSet.length) {
         super.atomSet[var5].number = var5++;
      }
   }

   public void setMolecule(Molecule var1) {
   }

   public long getSpeed() {
      return -1L;
   }

   public Object clone() {
      Object var1 = null;

      try {
         var1 = super.clone();
      } catch (CloneNotSupportedException var3) {
         System.err.println("MyObject can't clone");
      }

      return var1;
   }
}
