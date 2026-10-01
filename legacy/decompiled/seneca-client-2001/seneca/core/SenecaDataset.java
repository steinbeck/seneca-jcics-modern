package seneca.core;

import compchem.Atom;
import compchem.MFAnalyser;
import compchem.StandardElements;
import java.io.Serializable;
import java.util.Vector;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.EventListenerList;
import seneca.judges.FeatureJudge;
import seneca.judges.HHCOSYJudge;
import seneca.judges.HMBCJudge;
import seneca.judges.HOSECodeJudge;
import seneca.judges.HeteroBondsJudge;
import seneca.judges.HybridizationJudge;
import seneca.judges.SymmetryJudge;

public class SenecaDataset implements Serializable, ChangeListener {
   public static String carbon1d_name = "Carbon1D";
   public static String dept90_name = "DEPT-90";
   public static String dept135_name = "DEPT-135";
   public static String hetcor_name = "1JCH correlation";
   public static String hetcorlr_name = "CH long-range correlation";
   public static String hhcosy_name = "H,H-COSY";
   public static String noesy_name = "NOESY";
   public boolean debug = false;
   public boolean destroy = false;
   private String name = null;
   private String description = "none";
   private String molecularFormula = null;
   private float molecularMass = 0.0F;
   public NMRSpectrum carbon1D = new NMRSpectrum(NMRSpectrum.SPECTYPE_BB, "carbon1d");
   public NMRSpectrum dept90 = new NMRSpectrum(NMRSpectrum.SPECTYPE_DEPT, "dept90");
   public NMRSpectrum dept135 = new NMRSpectrum(NMRSpectrum.SPECTYPE_DEPT, "dept135");
   public NMRSpectrum hetcor = new NMRSpectrum(NMRSpectrum.SPECTYPE_HSQC, "hetcor");
   public NMRSpectrum hetcorlr = new NMRSpectrum(NMRSpectrum.SPECTYPE_HSQC, "hetcorlr");
   public NMRSpectrum hhcosy = new NMRSpectrum(NMRSpectrum.SPECTYPE_HHCOSY, "hhcosy");
   public NMRSpectrum noesy = new NMRSpectrum(NMRSpectrum.SPECTYPE_NOESY, "noesy");
   public Vector annealingSchedules = new Vector();
   public Atom[] setOfNodes;
   public Vector judges = new Vector();
   public Vector sessions = new Vector();
   public Vector serverList = new Vector();
   protected transient EventListenerList changeListeners = new EventListenerList();

   public SenecaDataset() {
      this.carbon1D.specType = carbon1d_name;
      this.dept90.specType = dept90_name;
      this.dept135.specType = dept135_name;
      this.hetcor.specType = hetcor_name;
      this.hetcorlr.specType = hetcorlr_name;
      this.hhcosy.specType = hhcosy_name;
      this.noesy.specType = noesy_name;
      this.carbon1D.addChangeListener(this);
      this.dept90.addChangeListener(this);
      this.dept135.addChangeListener(this);
      this.hetcor.addChangeListener(this);
      this.hetcorlr.addChangeListener(this);
      this.hhcosy.addChangeListener(this);
      this.noesy.addChangeListener(this);
   }

   public SenecaDataset(String var1) {
      this();
      this.name = var1;
   }

   public void setName(String var1) {
      this.name = var1;
      this.fireChange();
   }

   public void setMolecularFormula(String var1) {
      this.molecularFormula = var1;
      MFAnalyser var2 = new MFAnalyser(var1);
      this.setOfNodes = var2.getSetOfHeavyNodes();
      this.initializeNodes();
      this.fireChange();
   }

   public void setMolecularFormula(Atom[] var1) {
      this.setOfNodes = var1;
      this.molecularFormula = new MFAnalyser(this.setOfNodes).analyseSetOfNodes();
      this.initializeNodes();
      this.fireChange();
   }

   public void setMolecularMass(float var1) {
      this.molecularMass = var1;
      this.fireChange();
   }

   public String getName() {
      return this.name;
   }

   public void setDescription(String var1) {
      this.description = var1;
      this.fireChange();
   }

   public String getDescription() {
      return this.description;
   }

   public void setCurrent() {
      this.fireChange();
   }

   public String getMolecularFormula() {
      return this.molecularFormula;
   }

   public float getMolecularMass() {
      return this.molecularMass;
   }

   public void setSetOfNodes(Atom[] var1) {
      this.setOfNodes = var1;
      this.initializeNodes();
      if (this.molecularFormula == null) {
         this.molecularFormula = this.analyseSetOfNodes();
      }

      this.fireChange();
   }

   public Atom[] getSetOfNodes() {
      return this.setOfNodes;
   }

   public void destroy() {
      this.destroy = true;
      System.out.println("SenecaDataset " + this.getName() + " says: Arrgh - I´m being destroyed...");
      this.fireChange();
   }

   public Object getJudge(String var1) {
      if (!var1.startsWith("seneca.judges")) {
         var1 = "seneca.judges." + var1;
      }

      for (int var2 = 0; var2 < this.judges.size(); var2++) {
         if (var1.equals(this.judges.elementAt(var2).getClass().getName())) {
            return this.judges.elementAt(var2);
         }
      }

      try {
         return Class.forName(var1).newInstance();
      } catch (Exception var4) {
         System.out.println("Could not instantiate class " + var1);
         return null;
      }
   }

   private String analyseSetOfNodes() {
      String var2 = "";
      int var5 = 0;
      int var6 = 0;
      String[] var3 = new String[this.setOfNodes.length];
      int[] var4 = new int[this.setOfNodes.length];

      for (int var8 = 0; var8 < this.setOfNodes.length; var8++) {
         String var1 = this.setOfNodes[var8].symbol;
         var5 += this.setOfNodes[var8].HCount;
         boolean var7 = false;

         for (int var9 = 0; var9 < var6; var9++) {
            if (var3[var9].equals(var1)) {
               var4[var9]++;
               var7 = true;
               break;
            }
         }

         if (!var7) {
            var3[var6] = var1;
            var4[var6]++;
            var6++;
         }
      }

      for (int var10 = 0; var10 < var6; var10++) {
         var2 = var2 + var3[var10];
         if (var4[var10] > 1) {
            var2 = var2 + new Integer(var4[var10]).toString();
         }

         if (var10 == 0 && var5 > 0) {
            var2 = var2 + "H";
            if (var5 > 1) {
               var2 = var2 + new Integer(var5).toString();
            }
         }
      }

      return var2;
   }

   public boolean init() {
      this.initializeNodes();
      if (this.debug) {
         System.out.println("This is sd.init()!");
      }

      this.judges.addElement(new HHCOSYJudge());
      this.judges.addElement(new HMBCJudge());
      this.judges.addElement(new HeteroBondsJudge());
      this.judges.addElement(new SymmetryJudge());
      this.judges.addElement(new HybridizationJudge());
      this.judges.addElement(new HOSECodeJudge());
      this.judges.addElement(new FeatureJudge());

      for (int var1 = 0; var1 < this.judges.size(); var1++) {
         if (this.debug) {
            System.out.println(this.judges.elementAt(var1));
         }
      }

      return true;
   }

   private void initializeNodes() {
      if (this.setOfNodes != null) {
         StandardElements var1 = new StandardElements();

         for (int var2 = 0; var2 < this.setOfNodes.length; var2++) {
            this.setOfNodes[var2].number = var2;
            this.setOfNodes[var2].maxBondOrder = var1.getMaxBondOrder(this.setOfNodes[var2].symbol);
            this.setOfNodes[var2].maxBondOrderSum = var1.getMaxBondOrderSum(this.setOfNodes[var2].symbol);
         }
      }
   }

   public void addChangeListener(ChangeListener var1) {
      this.changeListeners
         .add(
            class$javax$swing$event$ChangeListener == null
               ? (class$javax$swing$event$ChangeListener = class$("javax.swing.event.ChangeListener"))
               : class$javax$swing$event$ChangeListener,
            var1
         );
      var1.stateChanged(new ChangeEvent(this));
   }

   public void removeChangeListener(ChangeListener var1) {
      this.changeListeners
         .remove(
            class$javax$swing$event$ChangeListener == null
               ? (class$javax$swing$event$ChangeListener = class$("javax.swing.event.ChangeListener"))
               : class$javax$swing$event$ChangeListener,
            var1
         );
   }

   protected void fireChange() {
      ChangeEvent var1 = new ChangeEvent(this);
      Object[] var2 = this.changeListeners.getListenerList();

      for (int var3 = var2.length - 2; var3 >= 0; var3 -= 2) {
         if (var2[var3]
            == (
               class$javax$swing$event$ChangeListener == null
                  ? (class$javax$swing$event$ChangeListener = class$("javax.swing.event.ChangeListener"))
                  : class$javax$swing$event$ChangeListener
            )) {
            ChangeListener var4 = (ChangeListener)var2[var3 + 1];
            var4.stateChanged(var1);
         }
      }
   }

   public void stateChanged(ChangeEvent var1) {
      if (this.debug) {
         System.out.println("StateChanged in SenecaDataset");
      }

      this.fireChange();
   }
}
