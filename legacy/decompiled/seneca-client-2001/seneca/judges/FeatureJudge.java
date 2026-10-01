package seneca.judges;

import compchem.Atom;
import compchem.Ring;
import compchem.SetOfRingsFinder;
import compchem.setOfRings;
import java.io.Serializable;
import java.util.Enumeration;
import java.util.Hashtable;

public class FeatureJudge extends Judge implements Serializable, Cloneable {
   public Hashtable ringSizeScores = new Hashtable();
   protected int hetHetPenalty = -5;
   protected boolean hetHetPenaltyEnabled = false;
   protected boolean ringsizeRestrictionEnabled = false;
   SetOfRingsFinder sorf = new SetOfRingsFinder();

   public FeatureJudge() {
      super("FeatureJudge");
      this.setRingSetRequired(true);
      this.setHasMaxScore(true);
   }

   public void addRingSizeScore(int var1, int var2) {
      if (var2 != 0) {
         this.ringSizeScores.put(new Integer(var1), new Integer(var2));
      }

      this.fireChanged();
   }

   public int getRingSizeScore(int var1) {
      return this.ringSizeScores.contains(new Integer(var1)) ? (Integer)this.ringSizeScores.get(new Integer(var1)) : 0;
   }

   public void init() {
      if (this.ringSizeScores.size() > 0) {
         this.ringsizeRestrictionEnabled = true;
      }
   }

   public void calcMaxScore() {
      super.maxScore = 0;
      if (this.ringsizeRestrictionEnabled) {
         Enumeration var1 = this.ringSizeScores.elements();

         while (var1.hasMoreElements()) {
            super.maxScore = super.maxScore + (Integer)var1.nextElement();
         }
      }
   }

   public JudgeResult evaluate(int[][] var1) {
      super.scoreSum = 0;
      int var2 = 0;
      int var3 = 0;
      String var4 = "<p>FeatureJudge->";
      if (this.ringsizeRestrictionEnabled) {
         var2 = this.judgeRingScore();
         var2 = this.judgeRingScore();
         super.scoreSum += var2;
         var4 = var4 + "RingScore: " + var2 + ". ";
      }

      if (this.isHetHetPenaltyEnabled()) {
         var3 = this.judgeHetHetScore();
         super.scoreSum += var3;
         var4 = var4 + "HetHetScore: " + var3 + ". ";
      }

      return new JudgeResult(0L, super.scoreSum, 0L, var4);
   }

   public int getHetHetPenalty() {
      return this.hetHetPenalty;
   }

   public void setHetHetPenalty(int var1) {
      this.fireChanged();
      this.hetHetPenalty = var1;
   }

   private int judgeRingScore() {
      int var1 = 0;
      int var2 = 0;
      setOfRings var3 = this.sorf.findSSSR(super.attic.atomSet);

      for (int var4 = 0; var4 < var3.size(); var4++) {
         var2 = ((Ring)var3.elementAt(var4)).size();
         if (this.ringSizeScores.containsKey(new Integer(var2))) {
            var1 += this.ringSizeScores.get(new Integer(var2));
         }
      }

      return var1;
   }

   private int judgeHetHetScore() {
      int var1 = 0;
      Object var2 = null;
      if (Judge.debug) {
         System.out.println("judgeHetHetScore: " + this.isHetHetPenaltyEnabled());
      }

      for (int var3 = 0; var3 < super.attic.atomSet.length; var3++) {
         var2 = super.attic.atomSet[var3];
         if (!((Atom)var2).symbol.equals("C")) {
            for (int var4 = 0; var4 < ((Atom)var2).degree; var4++) {
               if (!((Atom)var2).nodeTable[var4].symbol.equals("C")) {
                  var1 += this.hetHetPenalty;
               }
            }
         }
      }

      if (Judge.debug) {
         System.out.println("judgeHetHetScore->score: " + var1);
      }

      return var1 / 2;
   }

   public boolean isHetHetPenaltyEnabled() {
      return this.hetHetPenaltyEnabled;
   }

   public void setHetHetPenaltyEnabled(boolean var1) {
      this.hetHetPenaltyEnabled = var1;
      this.fireChanged();
   }
}
