package seneca.structgen;

import compchem.Atom;
import compchem.ChemFileUtils;
import compchem.IsomorphismTester;
import compchem.LIFOQueue;
import java.io.BufferedReader;
import java.io.Serializable;
import java.io.StringReader;
import seneca.judges.ScoreSummary;

public class StructureGeneratorResult implements Serializable, Cloneable {
   public LIFOQueue structures = new LIFOQueue(30);
   public LIFOQueue scoreSummaries = new LIFOQueue(30);
   int size = 30;

   public StructureGeneratorResult() {
   }

   public StructureGeneratorResult(int var1) {
      if (var1 > 0) {
         this.size = var1;
         this.structures = new LIFOQueue(this.size);
         this.scoreSummaries = new LIFOQueue(this.size);
      }
   }

   public void sort() {
      boolean var1 = false;

      do {
         var1 = false;

         for (int var4 = 0; var4 < this.structures.size() - 1; var4++) {
            if (((ScoreSummary)this.scoreSummaries.elementAt(var4)).score < ((ScoreSummary)this.scoreSummaries.elementAt(var4 + 1)).score) {
               Object var2 = this.structures.elementAt(var4 + 1);
               Object var3 = this.scoreSummaries.elementAt(var4 + 1);
               this.structures.removeElementAt(var4 + 1);
               this.scoreSummaries.removeElementAt(var4 + 1);
               this.structures.insertElementAt(var2, var4);
               this.scoreSummaries.insertElementAt(var3, var4);
               var1 = true;
            }
         }
      } while (var1);
   }

   public int size() {
      return this.structures.size();
   }

   public void removeIsomorphism() {
      this.sort();
      boolean var1 = false;
      boolean var2 = false;
      int var7 = 0;

      do {
         var1 = false;
         var7 = this.structures.size();

         try {
            Atom[] var4 = ChemFileUtils.readMDLMolFile(new BufferedReader(new StringReader((String)this.structures.elementAt(--var7))));
         } catch (Exception var12) {
            var1 = true;
         }
      } while (var1);

      this.structures.setSize(var7 - 1);
      int var8 = 0;
      int var9 = 0;

      do {
         var1 = false;
         IsomorphismTester var6 = new IsomorphismTester();
         Atom[] var16 = ChemFileUtils.readMDLMolFile(new BufferedReader(new StringReader((String)this.structures.elementAt(var8))));
         var7 = this.structures.size();
         var9 = var8 + 1;

         do {
            if (var8 != var9 && ((ScoreSummary)this.scoreSummaries.elementAt(var8)).score == ((ScoreSummary)this.scoreSummaries.elementAt(var9)).score) {
               boolean var3 = false;

               try {
                  var3 = var6.isIsomorph(var16, ChemFileUtils.readMDLMolFile(new BufferedReader(new StringReader((String)this.structures.elementAt(var9)))));
               } catch (Exception var11) {
               }

               if (var3) {
                  this.structures.removeElementAt(var9);
                  this.scoreSummaries.removeElementAt(var9);
                  var1 = true;
                  System.out.println("Removing structure " + var9 + ", which is isomorphic with " + var8);
               } else {
                  var9++;
               }
            } else {
               var9++;
            }
         } while (this.structures.size() > 0 && var9 < this.structures.size());

         System.out.println("i = " + var8);
      } while (this.structures.size() > 0 && ++var8 < this.structures.size());
   }

   public void merge(StructureGeneratorResult var1) {
      this.size = this.size + var1.size();
      this.structures.size = this.structures.size + var1.size();
      this.scoreSummaries.size = this.scoreSummaries.size + var1.size();

      for (int var2 = 0; var2 < var1.size(); var2++) {
         this.structures.push(var1.structures.elementAt(var2));
         this.scoreSummaries.push(var1.scoreSummaries.elementAt(var2));
      }
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
