package seneca.structgen;

import compchem.Atom;
import compchem.ChemFileUtils;
import compchem.DataStructureTools;
import compchem.IsomorphismTester;
import compchem.MFAnalyser;
import compchem.Molecule;
import compchem.PathUtils;
import compchem.SetOfRingsFinder;
import compchem.ShortestPathFinder;
import java.io.BufferedReader;
import java.io.Serializable;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Date;
import java.util.Vector;
import org.apache.log4j.Category;
import seneca.judges.Judge;
import seneca.judges.JudgeResult;
import seneca.judges.ScoreSummary;

public class StochasticGenerator extends Thread implements Serializable, Cloneable {
   int nrOfAtoms;
   public int[][] bondMatrix;
   int[][] apsp;
   public boolean go = true;
   public int[] sumOfBonds;
   public boolean running = false;
   protected Atom[] atomSet = null;
   protected Atom[] latestResult = null;
   protected String bestStructure = null;
   protected ScoreSummary bestScore = null;
   protected ScoreSummary recentScore = null;
   protected ScoreSummary lastScore = null;
   public boolean report = true;
   public boolean display = false;
   public boolean found = false;
   protected StructureGeneratorResult structureGeneratorResult;
   protected long iteration = 0L;
   protected String name = null;
   protected String datasetName = null;
   public Vector judges;
   protected String version = null;
   boolean goOn = false;
   boolean pleaseStop = false;
   Attic attic = null;
   protected ShortestPathFinder spf = new ShortestPathFinder();
   int uphill = 0;
   long maxUphillSteps = 50L;
   String reportString;
   boolean hasMaxScore = false;
   long maxScore = -1L;
   static Category senecalog;
   static Category senecaresult;
   int lastAnnealingCounter = 0;
   long starttime = -1L;
   long endtime = -1L;
   long runtime = -1L;
   double start_kT = 0.0;
   double end_kT = 0.0;
   long numberOfSteps = 0L;
   double coolingRate = 0.0;
   double current_kT = 0.0;
   long annealingStepCounter;
   Vector schedules = null;
   int currentScheduleNo = 0;
   long plateauSteps = 0L;
   int plateauStepCounter = 0;
   long convergenceStopCount = 0L;
   long convergenceCounter = 0L;
   AnnealingSchedule currentSchedule = null;
   SetOfRingsFinder sorf = new SetOfRingsFinder();
   AnnealingLog al = null;
   int alStepsize = 500;

   public StochasticGenerator(boolean var1) {
      senecalog = Category.getInstance(this.getClass().getName() + ".log");
      senecaresult = Category.getInstance(this.getClass().getName() + ".results");
      this.structureGeneratorResult = new StructureGeneratorResult();
      this.name = "StoIcGent";
      this.attic = new Attic();
      this.al = new AnnealingLog();
      this.alStepsize = this.al.getIterationAxisStepsize();
      this.version = "StoIcGenT V 0.6 beta, (c) 1998, 1999, 2000, Christoph Steinbeck";
   }

   public void execute() {
      this.start();
   }

   public void run() {
      senecalog.info("Start of StoicGent\n");
      this.maxScore = this.getMaxScore();
      this.starttime = System.currentTimeMillis();
      senecalog.info("Start time: " + new Date(this.starttime) + "\n");
      this.running = true;
      this.pleaseStop = false;
      double var2 = 0.3;
      this.setPriority(1);

      try {
         this.lastScore = null;
         this.recentScore = null;
         this.bestScore = null;
         long var5 = 0L;
         double var7 = 0.0;
         double var9 = 0.0;
         boolean var12 = true;
         this.iteration = 0L;
         senecalog.debug(this.version);
         senecalog.debug(this.attic);
         senecalog.debug(this.attic.atomSet);
         senecalog.debug("Structure generation started: " + new Date().toString());
         senecalog.debug("Molecular formula: " + new MFAnalyser(this.atomSet).getMolecularFormula());
         senecalog.debug("Running Random Structure Generation to obtain a starting structure.");
         SingleRandomStructureGenerator var14 = new SingleRandomStructureGenerator();
         var14.setAtomSet(this.atomSet);
         var14.attic = this.attic;
         this.atomSet = var14.generate();
         this.latestResult = this.atomSet;
         this.bondMatrix = DataStructureTools.createConnectionTable(this.atomSet);
         DataStructureTools.synchronizeConnectionTable(this.bondMatrix);
         this.sumOfBonds = new int[this.bondMatrix.length];
         int var11 = 0;
         this.lastScore = new ScoreSummary(0L, "");
         this.bestScore = new ScoreSummary(0L, "");
         senecalog.debug("Random Structure Generation done...");
         this.initJudges();

         do {
            senecalog.debug("InitAnnealing...");
            this.initAnnealing();
            senecalog.debug("InitAnnealing done...");
            senecalog.info("start_kT = " + this.start_kT);
            this.current_kT = this.start_kT;
            this.annealingStepCounter = 0L;

            do {
               int[][] var4 = DataStructureTools.cloneInt2D(this.bondMatrix);
               senecalog.debug("Mutating...");
               this.mutate(var4);
               DataStructureTools.synchronizeBonds(this.attic.atomSet, var4);
               boolean var1 = PathUtils.isConnected(this.attic.atomSet);
               if (var1) {
                  senecalog.debug("New Structure is connected.");
               } else {
                  senecalog.debug("New Structure is not connected.");
               }

               if (var1) {
                  this.recentScore = this.getScore(var4);
                  senecalog.debug("New Score is: " + this.recentScore.score);
                  var5 = this.lastScore.score - this.recentScore.score;
                  var7 = Math.random();
                  var9 = var5 / this.current_kT;
                  senecalog.debug("Last score was: " + this.lastScore.score);
                  senecalog.debug("deltaE is: " + var5);
                  if (var5 <= 0L) {
                     senecalog.debug("Accepted due to a better score.");
                  } else {
                     senecalog.debug("Lower Score. Exp: " + var9 + "; rnd: " + var7 + "; exp(-de/kt):" + Math.exp(-var9));
                     if (var7 < Math.exp(-var9)) {
                        senecalog.debug("Accepted because " + var7 + " < " + Math.exp(-var9));
                     } else {
                        senecalog.debug("Rejected because " + var7 + " > " + Math.exp(-var9));
                     }
                  }

                  if (var5 <= 0L || var7 < Math.exp(-var9)) {
                     if (var5 <= 0L) {
                        this.uphill++;
                     }

                     this.lastScore = this.recentScore;
                     this.bondMatrix = var4;
                     if (var5 < 0L) {
                        this.convergenceCounter = 0L;
                     }

                     if (this.recentScore.score >= this.bestScore.score) {
                        this.latestResult = DataStructureTools.cloneAtomSet(this.atomSet);
                        DataStructureTools.synchronizeBonds(this.latestResult, var4);
                        if (this.notInStock(this.latestResult)) {
                           this.sumBonds(var4);
                           String var13 = this.reportMDLStructure(this.latestResult, "Score: " + this.recentScore.score);
                           senecaresult.info(var13 + "\n$$$$\n");
                           senecalog.info("New best Structure: \n" + this.bestScore + "\n");
                           this.bestScore = this.recentScore;
                           this.bestStructure = var13;
                           this.structureGeneratorResult.structures.push(var13);
                           this.structureGeneratorResult.scoreSummaries.push(this.recentScore);
                        }
                     }
                  }

                  this.convergenceCounter++;
                  this.annealingStepCounter++;
                  this.iteration++;
                  this.cool();
                  if ((double)this.iteration / this.alStepsize == (int)((double)this.iteration / this.alStepsize)) {
                     this.al.addEntry((float)this.current_kT, (float)this.bestScore.score);
                  }

                  if (this.iteration / 1000.0 == (int)(this.iteration / 1000L)) {
                     senecalog.info("Iteration: " + this.iteration + " ");
                     senecalog.info("Current kT = " + this.current_kT + "\n");
                  }

                  senecalog.debug("UphillMoves: " + this.uphill + "/" + this.maxUphillSteps);
                  senecalog.debug("ConvergenceCounter: " + this.convergenceCounter + "/" + this.convergenceStopCount);
                  senecalog.debug("PlateauStepCounter: " + this.plateauStepCounter + "/" + this.plateauSteps + "\n");
                  this.goOn = true;
                  if (this.convergenceCounter >= this.convergenceStopCount) {
                     if (this.bestScore.score < this.maxScore && var2 < 1.0) {
                        this.current_kT = this.current_kT + (this.start_kT - this.current_kT) * var2;
                        this.convergenceCounter = 0L;
                        this.annealingStepCounter = 0L;
                        this.plateauSteps = (long)(this.plateauSteps * 1.5);
                        this.maxUphillSteps = (long)(this.maxUphillSteps * 1.5);
                        if (var2 < 1.0) {
                           var2 += 0.1;
                        }

                        senecalog.info("Calculation converged.\n");
                        senecalog.info("Best score achieved in this run: " + this.bestScore.score + "\n");
                        senecalog.info("Maximum possible score: " + this.maxScore + "\n");
                        senecalog.info("Maximum score not reached: Reheating by " + var2 * 100.0 + "%\n");
                        senecalog.info("Increasing plateauSteps to : " + this.plateauSteps + "\n");
                        senecalog.info("Increasing maximum uphill moves to : " + this.maxUphillSteps + "\n");
                     } else if (var2 >= 1.0) {
                        senecalog.info("Calculation did not converge despite successive parameter modifications.\n");
                        senecalog.info("Best score achieved in this run: " + this.bestScore.score + "\n");
                        senecalog.info("Maximum possible score: " + this.maxScore + "\n");
                        senecalog.info("Maximum score not reached: Aborting.\n");
                        this.goOn = false;
                     } else if (this.bestScore.score >= this.maxScore) {
                        senecalog.info("Calculation converged.\n");
                        senecalog.info("Best score achieved in this run: " + this.bestScore.score + "\n");
                        senecalog.info("Maximum possible score: " + this.maxScore + "\n");
                        senecalog.info("Maximum score reached. Regular end of calculation.\n");
                        this.goOn = false;
                     }
                  }
               }

               Thread.yield();
            } while (this.goOn && !this.pleaseStop);

            this.goOn = false;
            var11++;
            senecalog.debug("currentScheduleNo: " + this.currentScheduleNo + "; schedules.size(): " + this.schedules.size());
         } while (this.currentScheduleNo < this.schedules.size());

         this.endtime = System.currentTimeMillis();
         senecalog.info("Stop: " + new Date(this.endtime) + "\n");
         senecalog.info("Overall runtime: " + (this.endtime - this.starttime) / 1000L + " seconds.\n");
         senecalog.info("Simulated annealing run finished.\n");
         this.running = false;
      } catch (Exception var15) {
         senecalog.fatal("Error in StoicGent.run()");
         var15.printStackTrace();
      }
   }

   public void reset() {
      this.iteration = 0L;
      this.structureGeneratorResult = null;
      this.structureGeneratorResult = new StructureGeneratorResult();
      this.schedules = null;
      this.atomSet = null;
      this.name = null;
      this.currentScheduleNo = 0;
   }

   public Object getStatus() {
      StructureGeneratorStatus var2 = new StructureGeneratorStatus();
      var2.iteration = this.iteration;
      if (this.atomSet == null) {
         var2.molecularFormula = "not available";
      } else {
         var2.molecularFormula = new MFAnalyser(this.atomSet).getMolecularFormula();
      }

      var2.bestStructure = new String(this.bestStructure);
      this.bestScore.maxScore = this.maxScore;
      var2.bestEvaluation = this.bestScore;
      var2.datasetName = this.datasetName;
      var2.al = new AnnealingLog();
      int var3 = this.al.getItemCount(0);

      for (int var4 = this.lastAnnealingCounter; var4 < var3; var4++) {
         Float[] var1 = this.al.getEntry(0, var4);
         var2.al.addEntry(var1[0], var1[1], var1[2]);
      }

      this.lastAnnealingCounter = var3;
      var2.status = StructureGeneratorStatus.statusStrings[StructureGeneratorStatus.IDLE];
      if (this.pleaseStop) {
         var2.status = StructureGeneratorStatus.statusStrings[StructureGeneratorStatus.STOPPED];
      }

      if (!this.pleaseStop && this.goOn) {
         var2.status = StructureGeneratorStatus.statusStrings[StructureGeneratorStatus.RUNNING];
      }

      if (!this.pleaseStop && !this.goOn) {
         var2.status = StructureGeneratorStatus.statusStrings[StructureGeneratorStatus.HOLDING];
      }

      return var2;
   }

   protected void mutate(int[][] var1) {
      int var14 = var1.length;

      while (true) {
         int var2 = (int)(Math.random() * var14);
         int var3 = (int)(Math.random() * var14);
         int var4 = (int)(Math.random() * var14);
         int var5 = (int)(Math.random() * var14);
         Thread.yield();
         if (var2 != var3 && var2 != var4 && var2 != var5 && var3 != var4 && var3 != var5 && var4 != var5) {
            int var6 = var1[var2][var4];
            int var7 = var1[var2][var5];
            int var8 = var1[var3][var5];
            int var9 = var1[var3][var4];
            int[] var15 = new int[]{0, var6 - var8, var6 + var7 - 3, var6 + var9 - 3};
            int[] var16 = new int[]{3, var6 + var7, var6 + var9, var6 - var8 + 3};
            int var11 = this.max(var15);
            int var12 = this.min(var16);
            int var10 = var6;
            if (var12 - var11 > 0 || var6 != var11) {
               do {
                  var10 = (int)(Math.random() * (var12 - var11 + 1)) + var11;
               } while (var6 == var10);
            }

            Thread.yield();
            if (var10 != var6 && var10 >= var11 && var10 <= var12) {
               var1[var2][var4] = var10;
               var1[var2][var5] = var6 + var7 - var10;
               var1[var3][var5] = var8 - var6 + var10;
               var1[var3][var4] = var6 + var9 - var10;
               var1[var4][var2] = var10;
               var1[var5][var2] = var6 + var7 - var10;
               var1[var5][var3] = var8 - var6 + var10;
               var1[var4][var3] = var6 + var9 - var10;
               return;
            }
         }
      }
   }

   protected int max(int[] var1) {
      int var2 = var1[0];

      for (int var3 = 0; var3 < var1.length; var3++) {
         if (var1[var3] > var2) {
            var2 = var1[var3];
         }
      }

      return var2;
   }

   protected int min(int[] var1) {
      int var2 = var1[0];

      for (int var3 = 0; var3 < var1.length; var3++) {
         if (var1[var3] < var2) {
            var2 = var1[var3];
         }
      }

      return var2;
   }

   public void setSASchedules(Vector var1) {
      this.schedules = var1;
   }

   private void initAnnealing() {
      senecalog.debug("Start of initAnnealing");
      this.currentSchedule = (AnnealingSchedule)this.schedules.elementAt(this.currentScheduleNo);
      this.currentScheduleNo++;
      Object var1 = null;
      Object var2 = null;
      ScoreSummary var3 = new ScoreSummary(0L, "");
      long var4 = 0L;
      int var7 = 0;
      senecalog.debug("InitAnnealing: Start of do-loop");

      do {
         Thread.yield();
         int[][] var6 = DataStructureTools.cloneInt2D(this.bondMatrix);
         this.mutate(var6);
         senecalog.debug("InitAnnealing: Mid of do-loop");
         DataStructureTools.synchronizeBonds(this.attic.atomSet, var6);
         if (PathUtils.isConnected(this.attic.atomSet)) {
            var2 = this.getScore(var6);
            var4 += Math.abs(var3.score - ((ScoreSummary)var2).score);
            var3 = (ScoreSummary)var2;
            var7++;
         }
      } while (var7 < 500);

      this.plateauSteps = this.currentSchedule.plateauSteps;
      this.maxUphillSteps = this.currentSchedule.maxUphillSteps;
      this.convergenceCounter = 0L;
      this.convergenceStopCount = this.plateauSteps * 3L;
      var4 /= 500L;
      this.start_kT = -var4 / Math.log(this.currentSchedule.initialAcceptanceProbability);
      this.current_kT = this.start_kT;
      this.coolingRate = this.currentSchedule.asymptoticCoolingFactor;
      senecalog.debug("initialAcceptanceProbability: " + this.currentSchedule.initialAcceptanceProbability);
      senecalog.debug("start_kT: " + this.start_kT);
      senecalog.debug("plateauSteps: " + this.plateauSteps);
      senecalog.debug("coolingRate: " + this.coolingRate);
      senecalog.debug("maxUphillSteps: " + this.maxUphillSteps);
   }

   private void cool() {
      senecalog.debug("Cooling:");
      if (this.plateauStepCounter > this.plateauSteps || this.uphill > this.maxUphillSteps) {
         this.current_kT = this.current_kT * this.coolingRate;
         this.plateauStepCounter = 0;
         this.uphill = 0;
      }

      this.plateauStepCounter++;
   }

   private boolean notInStock(Atom[] var1) {
      senecalog.debug("Isomorphism-Check started...");
      IsomorphismTester var2 = new IsomorphismTester(var1);

      for (int var3 = 0; var3 < this.structureGeneratorResult.structures.size(); var3++) {
         if (var2.isIsomorph(
            ChemFileUtils.readMDLMolFile(new BufferedReader(new StringReader((String)this.structureGeneratorResult.structures.elementAt(var3))))
         )) {
            senecalog.debug("Isomorphism-Check finished: In stock...");
            return false;
         }
      }

      senecalog.debug("Isomorphism-Check finished: Not in stock...");
      return true;
   }

   private ScoreSummary getScore(int[][] var1) {
      senecalog.debug("StoicGent->getScore() start");
      long var2 = 0L;
      String var4 = "";
      Judge var5 = null;
      JudgeResult var6 = null;
      this.reportString = "<score>";

      for (int var7 = 0; var7 < this.judges.size(); var7++) {
         var5 = (Judge)this.judges.elementAt(var7);
         if (var5.getEnabled()) {
            senecalog.debug("StoicGent->getScore()->Judge: " + var5.name);
            var6 = var5.evaluate(var1);
            var2 += var6.score;
            this.reportString = this.reportString + new Long(var6.score).toString() + " ";
            var4 = var4 + var6.scoreDescription + "\n";
         }
      }

      this.reportString = this.reportString + new Long(var2).toString();
      this.reportString = this.reportString + "</score>";
      if (this.report) {
         try {
            senecalog.debug(this.reportString);
         } catch (Exception var9) {
         }
      }

      if (var2 < 0L) {
         var2 = 0L;
      }

      senecalog.debug("StoicGent->getScore() return");
      return new ScoreSummary(var2, var4);
   }

   private void sumBonds(int[][] var1) {
      for (int var2 = 0; var2 < var1.length; var2++) {
         this.sumOfBonds[var2] = 0;

         for (int var3 = 0; var3 < var1.length; var3++) {
            this.sumOfBonds[var2] = this.sumOfBonds[var2] + var1[var2][var3];
         }
      }
   }

   public long getSpeed() {
      long var1 = System.currentTimeMillis();
      int[][] var3 = new int[][]{
         {0, 1, 0, 0, 0, 0, 0, 0, 1},
         {1, 0, 1, 0, 0, 0, 0, 0, 0},
         {0, 1, 0, 1, 0, 0, 0, 0, 0},
         {0, 0, 1, 0, 1, 0, 0, 0, 1},
         {0, 0, 0, 1, 0, 1, 0, 0, 0},
         {0, 0, 0, 0, 1, 0, 1, 1, 0},
         {0, 0, 0, 0, 0, 1, 0, 0, 0},
         {0, 0, 0, 0, 0, 1, 0, 0, 0},
         {1, 0, 0, 1, 0, 0, 0, 0, 0}
      };

      for (int var4 = 1; var4 < 10000; var4++) {
         this.mutate(var3);
      }

      long var5 = System.currentTimeMillis();
      return var5 - var1;
   }

   public void initJudges() {
      for (int var1 = 0; var1 < this.judges.size(); var1++) {
         if (((Judge)this.judges.elementAt(var1)).getEnabled()) {
            ((Judge)this.judges.elementAt(var1)).setAttic(this.attic);
            ((Judge)this.judges.elementAt(var1)).init();
         }
      }
   }

   public void setVerbose(boolean var1) {
      if (!this.running) {
         ;
      }
   }

   public void setJudges(Vector var1) {
      this.judges = var1;
   }

   public Vector getJudges() {
      return this.judges != null ? this.judges : new Vector();
   }

   public void setDatasetName(String var1) {
      this.datasetName = var1;
   }

   public String getDatasetName(String var1) {
      return this.datasetName;
   }

   public Object getResult() {
      return this.structureGeneratorResult.clone();
   }

   public String getVersion() {
      return this.version;
   }

   public void end() {
      this.pleaseStop = true;
   }

   public void setMolecule(Molecule var1) {
      this.atomSet = DataStructureTools.cloneAtomSet(var1.getAtoms());
      this.attic.atomSet = DataStructureTools.cloneAtomSet(var1.getAtoms());
      this.attic.normalized = DataStructureTools.cloneAtomSet(var1.getAtoms());
   }

   private String reportSumBonds() {
      String var1 = "SumOfBonds Array: ";

      for (int var2 = 0; var2 < this.sumOfBonds.length; var2++) {
         var1 = var1 + this.sumOfBonds[var2] + " ";
      }

      return var1;
   }

   private int getMaxScore() {
      Judge var1 = null;
      int var2 = 0;

      for (int var3 = 0; var3 < this.judges.size(); var3++) {
         var1 = (Judge)this.judges.elementAt(var3);
         if (var1.getEnabled()) {
            if (!var1.getHasMaxScore()) {
               return -1;
            }

            var2 += var1.getMaxScore();
         }
      }

      return var2;
   }

   private String reportBM(int[][] var1) {
      String var2 = "Bondmatrix:\n";

      for (int var3 = 0; var3 < var1.length; var3++) {
         for (int var4 = 0; var4 < var1.length; var4++) {
            var2 = var2 + var1[var3][var4] + " ";
         }

         var2 = var2 + "\n";
      }

      return var2;
   }

   private String reportMDLStructure(Atom[] var1, String var2) {
      StringWriter var3 = new StringWriter();
      ChemFileUtils.writeMDLMol(var3, var2, var1);
      return var3.toString();
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
