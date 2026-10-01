package seneca.structgen;

import compchem.Atom;
import compchem.DataStructureTools;
import compchem.ShortestPathFinder;
import java.io.Serializable;
import java.util.Vector;
import seneca.judges.Judge;
import seneca.judges.ScoreSummary;

public abstract class StructureGenerator extends Thread implements Serializable, Cloneable {
   int nrOfAtoms;
   public int[][] bondMatrix;
   int[][] apsp;
   public boolean go = true;
   public long slowness = 100L;
   public int[] sumOfBonds;
   public boolean running = false;
   protected Atom[] atomSet = null;
   protected Atom[] latestResult = null;
   protected Atom[] bestStructure = null;
   protected ScoreSummary bestScore = null;
   protected ScoreSummary recentScore = null;
   protected ScoreSummary lastScore = null;
   public boolean debug = false;
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

   public StructureGenerator() {
      this("StructGen");
   }

   public StructureGenerator(String var1) {
      this.structureGeneratorResult = new StructureGeneratorResult();
      this.name = var1;
      this.attic = new Attic();
   }

   public abstract void execute();

   public Object getResult() {
      return this.structureGeneratorResult;
   }

   public abstract Object getStatus();

   public String getVersion() {
      return this.version;
   }

   public void end() {
      this.pleaseStop = true;
   }

   public void setAtomSet(Atom[] var1) {
      this.atomSet = DataStructureTools.cloneAtomSet(var1);
      this.attic.atomSet = DataStructureTools.cloneAtomSet(var1);
      this.attic.normalized = DataStructureTools.cloneAtomSet(var1);
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

   public void setVerbose(boolean var1) {
      if (!this.running) {
         this.debug = var1;
      }
   }

   protected void printMatrix() {
      for (int var2 = 0; var2 < this.bondMatrix.length; var2++) {
         String var1 = "";

         for (int var3 = 0; var3 < this.bondMatrix.length; var3++) {
            var1 = var1 + this.bondMatrix[var2][var3] + " ";
         }

         var1 = var1 + " - " + this.sumOfBonds[var2];
         System.out.println(var1);
      }
   }

   protected boolean structureConnected(int[][] var1) {
      if (this.debug) {
         System.out.println("StructureGenerator->structureConnected()->start");
      }

      int var2 = this.attic.apsp.length;
      if (this.debug) {
         DataStructureTools.printInt2D(this.attic.apsp);
      }

      for (int var3 = 0; var3 < var2; var3++) {
         for (int var4 = var3 + 1; var4 < var2; var4++) {
            if (this.attic.apsp[var3][var4] > this.attic.apsp.length) {
               if (this.debug) {
                  System.out.println("StructureGenerator->structureConnected()->not connected");
               }

               return false;
            }
         }
      }

      if (this.debug) {
         System.out.println("StructureGenerator->structureConnected()->conntected");
      }

      return true;
   }

   public void initJudges() {
      for (int var1 = 0; var1 < this.judges.size(); var1++) {
         if (((Judge)this.judges.elementAt(var1)).getEnabled()) {
            ((Judge)this.judges.elementAt(var1)).setAttic(this.attic);
            ((Judge)this.judges.elementAt(var1)).init();
         }
      }
   }

   public abstract long getSpeed();
}
