package seneca.judges;

import compchem.DataStructureTools;
import java.io.Serializable;
import java.util.Vector;

public class SymmetryJudge extends Judge implements Serializable, Cloneable {
   protected Vector symmetryClassesVector = new Vector();
   protected SymmetryJudge.SymmetryClass[] symmetryClasses;

   public SymmetryJudge() {
      super("SymmetryJudge");
      super.score = 500;
      super.hasMaxScore = true;
   }

   public int getSymmetryClassCount() {
      return this.symmetryClassesVector.size();
   }

   public void addSymmetryClass(boolean[] var1) {
      this.symmetryClassesVector.addElement(new SymmetryJudge.SymmetryClass(var1));
      this.init();
   }

   public void clearSymmetryClasses() {
      this.symmetryClassesVector.clear();
   }

   public boolean[] getSymmetryClass(int var1) {
      return var1 < this.symmetryClassesVector.size() ? ((SymmetryJudge.SymmetryClass)this.symmetryClassesVector.elementAt(var1)).symmetricAtoms : null;
   }

   public int getClassMember(int var1, int var2) {
      if (this.symmetryClasses.length > var1) {
         int var3 = 0;

         for (int var4 = 0; var4 < this.symmetryClasses[var1].symmetricAtoms.length; var4++) {
            if (this.symmetryClasses[var1].symmetricAtoms[var4]) {
               var3++;
            }

            if (var3 == var2) {
               return var4;
            }
         }
      }

      return -1;
   }

   public void setScore(int var1) {
      super.score = var1;
   }

   public void init() {
      this.symmetryClasses = new SymmetryJudge.SymmetryClass[this.symmetryClassesVector.size()];

      for (int var1 = 0; var1 < this.symmetryClassesVector.size(); var1++) {
         this.symmetryClasses[var1] = (SymmetryJudge.SymmetryClass)this.symmetryClassesVector.elementAt(var1);
      }
   }

   public void calcMaxScore() {
      super.maxScore = 0;

      for (int var1 = 0; var1 < this.symmetryClassesVector.size(); var1++) {
         super.maxScore = super.maxScore + DataStructureTools.faculty(this.symmetryClasses[var1].symmetricAtomsCount) / 2;
      }
   }

   public JudgeResult evaluate(int[][] var1) {
      int var2 = 0;
      int[] var3 = DataStructureTools.computeMorganMatrix(super.attic.atomSet);

      for (int var4 = 0; var4 < this.symmetryClasses.length; var4++) {
         int var5 = 0;

         for (int var6 = 0; var6 < this.symmetryClasses[var4].symmetricAtoms.length; var6++) {
            if (var6 < this.symmetryClasses[var4].symmetricAtoms.length - 1 && this.symmetryClasses[var4].symmetricAtoms[var6]) {
               for (int var7 = var6 + 1; var7 < this.symmetryClasses[var4].symmetricAtoms.length; var7++) {
                  if (this.symmetryClasses[var4].symmetricAtoms[var7] && var3[var6] == var3[var7]) {
                     var2 += super.score;
                     var5++;
                  }
               }
            }
         }
      }

      String var8 = "" + var2 / super.score + " symmetry classes are completly filled.";
      return new JudgeResult(super.maxScore, var2, 0L, var8);
   }

   class SymmetryClass implements Serializable {
      boolean[] symmetricAtoms;
      int symmetricAtomsCount = 0;

      SymmetryClass(int var2) {
         this.symmetricAtoms = new boolean[var2];
      }

      SymmetryClass(boolean[] var2) {
         this.symmetricAtoms = var2;
         this.symmetricAtomsCount = 0;

         for (int var3 = 0; var3 < var2.length; var3++) {
            if (var2[var3]) {
               this.symmetricAtomsCount++;
            }
         }
      }
   }
}
