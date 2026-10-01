package compchem;

import compchem.exceptions.TooManyBondsException;
import java.io.Serializable;
import java.util.Enumeration;
import java.util.Hashtable;

public class Atom implements Serializable {
   public String name = "";
   public String symbol = "";
   public int number;
   public int charge = 0;
   public Atom[] nodeTable = new Atom[10];
   public int[] bondTable = new int[10];
   public int[] intnodeTable = new int[10];
   public int degree = 0;
   public int HCount = 0;
   public int maxBondOrderSum;
   public int maxBondOrder;
   public int bondOrderSum;
   public int[] inRings = new int[10];
   public double x;
   public double y;
   public double z;
   public Hashtable physicalProperties = new Hashtable();
   public int ringCounter;
   public int status = 0;
   public static final int PLACEMENT_NONE = 0;
   public static final int PLACEMENT_PRELIM = 1;
   public static final int PLACEMENT_FINAL = 2;
   public static final String PHYS_PROP_13C_SHIFT = "13C chemical shift";
   public static final String PHYS_PROP_1H_SHIFT = "1H chemical shift";
   public static final String PHYS_PROP_15N_SHIFT = "15N chemical shift";
   public static final String PHYS_PROP_31P_SHIFT = "31P chemical shift";
   public static final String PHYS_PROP_13C_ISOSHIELD = "13C isotropic shielding";
   public static final String PHYS_PROP_1H_ISOSHIELD = "1H isotropic shielding";
   public static final String PHYS_PROP_15N_ISOSHIELD = "15N isotropic shielding";
   public static final String PHYS_PROP_31P_ISOSHIELD = "31P isotropic shielding";
   public static final String PHYS_PROP_PREDICTED_SHIFT = "Shift Prediction";
   public static final int BONDORDER_SINGLE = 1;
   public static final int BONDORDER_DOUBLE = 2;
   public static final int BONDORDER_TRIPLE = 3;
   public static final int BONDORDER_AROMATIC = 4;
   public static final int BONDORDER_SINGLE_UP_AWAY = 5;
   public static final int BONDORDER_SINGLE_UP_TOWARDS = 6;
   public static final int BONDORDER_SINGLE_DOWN_AWAY = 7;
   public static final int BONDORDER_SINGLE_DOWN_TOWARDS = 8;

   public Atom() {
      this.bondOrderSum = this.getBondCount();
      this.physicalProperties = new Hashtable();
   }

   public Atom(String var1) {
      this();
      this.symbol = var1;
   }

   public Atom(String var1, int var2) {
      this();
      this.symbol = var1;
      this.number = var2;
   }

   public Atom(String var1, double var2, double var4, double var6) {
      this();
      this.symbol = var1;
      this.x = var2;
      this.y = var4;
      this.z = var6;
   }

   public Atom(int var1) {
      this();
      this.number = var1;
   }

   public Object clone() {
      Atom var1 = new Atom();
      var1.name = this.name;
      var1.symbol = this.symbol;
      var1.number = this.number;
      var1.charge = this.charge;
      var1.status = this.status;
      var1.degree = this.degree;
      var1.x = this.x;
      var1.y = this.y;
      var1.z = this.z;
      var1.ringCounter = this.ringCounter;
      var1.HCount = this.HCount;
      var1.maxBondOrderSum = this.maxBondOrderSum;
      var1.maxBondOrder = this.maxBondOrder;
      var1.bondOrderSum = this.bondOrderSum;
      System.arraycopy(this.nodeTable, 0, var1.nodeTable, 0, 10);
      System.arraycopy(this.intnodeTable, 0, var1.intnodeTable, 0, 10);
      System.arraycopy(this.bondTable, 0, var1.bondTable, 0, 10);
      System.arraycopy(this.inRings, 0, var1.inRings, 0, 10);
      return var1;
   }

   public void fixBondOrderSum() {
      this.bondOrderSum = this.getBondCount();
   }

   public void reportRings() {
      String var1 = "[ ";

      for (int var2 = 0; var2 < this.ringCounter; var2++) {
         var1 = var1 + this.inRings[var2] + " ";
      }

      var1 = var1 + "]";
   }

   public void addBond(Atom var1, int var2) throws TooManyBondsException {
      if (this.maxBondOrder == 0 || this.maxBondOrderSum == 0) {
         StandardElements var3 = new StandardElements();
         this.maxBondOrderSum = var3.getMaxBondOrderSum(this.symbol);
         this.maxBondOrder = var3.getMaxBondOrder(this.symbol);
      }

      if (!this.isBondedTo(var1)) {
         this.nodeTable[this.degree] = var1;
         this.bondTable[this.degree] = var2;
         this.degree++;
      }

      if (this.bondOrderSum + var2 > this.maxBondOrderSum) {
         throw new TooManyBondsException();
      } else {
         this.bondOrderSum += var2;
      }
   }

   public int getBondCount() {
      float var1 = 0.0F;
      float var2 = 0.0F;

      for (int var3 = 0; var3 < this.degree; var3++) {
         var2 = this.bondTable[var3];
         if (var2 > 3.0F) {
            if (var2 == 4.0F) {
               var2 = 1.5F;
            } else {
               var2 = 1.0F;
            }
         }

         var1 += var2;
      }

      return (int)var1;
   }

   public void saturate() {
      if (this.maxBondOrder == 0 || this.maxBondOrderSum == 0) {
         StandardElements var1 = new StandardElements();
         this.maxBondOrderSum = var1.getMaxBondOrderSum(this.symbol);
         this.maxBondOrder = var1.getMaxBondOrder(this.symbol);
      }

      this.HCount = this.maxBondOrderSum - this.getBondCount();
   }

   public boolean isSaturated() {
      return this.getBondCount() + this.HCount == this.maxBondOrderSum;
   }

   public boolean isBondedTo(Atom var1) {
      for (int var2 = 0; var2 < this.degree; var2++) {
         if (this.nodeTable[var2].equals(var1)) {
            return true;
         }
      }

      return false;
   }

   public int getBondOrder(Atom var1) {
      for (int var2 = 0; var2 < this.degree; var2++) {
         if (this.nodeTable[var2].equals(var1)) {
            return this.bondTable[var2];
         }
      }

      return 0;
   }

   public int setBondOrder(Atom var1, int var2) {
      byte var3 = 0;
      if (var3 >= this.degree) {
         return 0;
      } else {
         if (this.nodeTable[var3].equals(var1)) {
            this.bondTable[var3] = var2;
         }

         return var2;
      }
   }

   public void incrementBondOrder(Atom var1) {
      byte var2 = 0;
      if (var2 < this.degree) {
         if (this.nodeTable[var2].equals(var1)) {
            this.bondTable[var2]++;
         }
      }
   }

   public void decrementBondOrder(Atom var1) {
      byte var2 = 0;
      if (var2 < this.degree) {
         if (this.nodeTable[var2].equals(var1) && this.bondTable[var2] > 0) {
            this.bondTable[var2]--;
         }
      }
   }

   public double getX() {
      return this.x;
   }

   public double getY() {
      return this.y;
   }

   public int getCurrentMaxBondOrder() {
      return Math.min(this.maxBondOrder, this.maxBondOrderSum - this.HCount - this.bondOrderSum);
   }

   public void formBond(Atom var1, int var2) {
      for (int var3 = 0; var3 < this.degree; var3++) {
         if (this.nodeTable[var3].equals(var1)) {
            this.bondTable[var3] = var2;
            this.bondOrderSum = this.getBondCount();
            return;
         }
      }

      this.nodeTable[this.degree] = var1;
      this.bondTable[this.degree] = var2;
      this.degree++;
      this.bondOrderSum += var2;
   }

   public void removeBond(Atom var1) {
      for (int var2 = 0; var2 < this.degree; var2++) {
         if (this.nodeTable[var2].equals(var1)) {
            this.bondOrderSum = this.bondOrderSum - this.bondTable[var2];

            for (int var3 = var2; var3 < this.degree - 1; var3++) {
               this.nodeTable[var3] = this.nodeTable[var3 + 1];
               this.bondTable[var3] = this.bondTable[var3 + 1];
            }

            this.degree--;
            break;
         }
      }
   }

   public void setPhysicalProperty(String var1, Object var2) {
      this.physicalProperties.put(var1, var2);
   }

   public Object getPhysicalProperty(String var1) {
      return this.physicalProperties.get(var1);
   }

   public void clearPhysicalProperties() {
      this.physicalProperties.clear();
   }

   public void reset() {
      this.degree = 0;
      this.bondOrderSum = 0;
   }

   public String toString() {
      Object var2 = null;
      String var1 = "Number: " + this.number;
      var1 = var1 + "; Nodename: " + this.name;
      var1 = var1 + "; Symbol: " + this.symbol;
      var1 = var1 + "; Degree: " + this.degree;
      var1 = var1 + "; HCount: " + this.HCount;
      var1 = var1 + "; Status: " + this.status + "\n";
      var1 = var1 + "maxBondOrder: " + this.maxBondOrder;
      var1 = var1 + "; maxBondOrderSum: " + this.maxBondOrderSum;
      var1 = var1 + "; bondOrderSum: " + this.bondOrderSum + "\n";
      var1 = var1 + "x: " + this.x;
      var1 = var1 + "; y: " + this.y;
      var1 = var1 + "; z: " + this.z + "\n";
      var1 = var1 + "IntNodeTable: ";

      for (int var3 = 0; var3 < this.degree; var3++) {
         var1 = var1 + this.intnodeTable[var3] + " ";
      }

      var1 = var1 + "BondTable: ";

      for (int var4 = 0; var4 < this.degree; var4++) {
         var1 = var1 + this.bondTable[var4] + " ";
      }

      var1 = var1 + "\n\nPhysical Properties:\n";
      Enumeration var5 = this.physicalProperties.keys();

      while (var5.hasMoreElements()) {
         var2 = var5.nextElement();
         var1 = var1 + (String)var2 + ": " + this.physicalProperties.get(var2);
      }

      return var1;
   }
}
