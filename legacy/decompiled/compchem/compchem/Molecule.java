package compchem;

import compchem.exceptions.TooManyBondsException;
import java.io.Serializable;
import java.util.Date;
import java.util.Vector;

public class Molecule implements Serializable {
   public int number;
   public String[] chemname = new String[]{""};
   public String autonomname = "";
   public String casrn = "";
   public String brn = "";
   public Date date;
   private Vector atoms = null;
   public Vector bonds = null;

   public Molecule() {
      this.atoms = new Vector();
   }

   public Molecule(Vector var1) {
      this.atoms = var1;
   }

   public Molecule(Atom[] var1) {
      this();

      for (int var2 = 0; var2 < var1.length; var2++) {
         this.atoms.addElement(var1[var2]);
      }
   }

   public Molecule(Atom[] var1, boolean var2) {
      this();
      Atom[] var3 = var1;
      if (var2) {
         var3 = DataStructureTools.cloneAtomSet(var1);
      }

      for (int var4 = 0; var4 < var3.length; var4++) {
         this.atoms.addElement(var3[var4]);
      }
   }

   public Molecule(int var1, String[] var2, String var3, String var4, String var5, Atom[] var6) {
      this(var6);
      this.number = var1;
      this.chemname = var2;
      this.autonomname = var3;
      this.casrn = var4;
      this.brn = var5;
   }

   public int size() {
      return this.atoms.size();
   }

   public Atom[] getAtoms() {
      Atom[] var1 = new Atom[this.atoms.size()];
      Object[] var2 = new Object[this.atoms.size()];
      this.atoms.copyInto(var2);

      for (int var3 = 0; var3 < var1.length; var3++) {
         var1[var3] = (Atom)var2[var3];
      }

      return var1;
   }

   public Vector getAtomsVector() {
      return this.atoms;
   }

   public Atom atomAt(int var1) {
      return (Atom)this.atoms.elementAt(var1);
   }

   public void saturate() {
      for (int var1 = 0; var1 < this.size(); var1++) {
         ((Atom)this.atoms.elementAt(var1)).saturate();
      }
   }

   public void normalize() {
      DataStructureTools.normalize(this.getAtoms());
   }

   public void addAtom(Atom var1) {
      this.atoms.addElement(var1);
   }

   public void addBond(Atom var1, Atom var2, int var3) throws TooManyBondsException {
      var1.addBond(var2, var3);
      var2.addBond(var1, var3);
   }

   public void addBond(int var1, int var2, int var3) throws TooManyBondsException {
      this.atomAt(var1).addBond(this.atomAt(var2), var3);
      this.atomAt(var2).addBond(this.atomAt(var1), var3);
   }

   public void removeAtom(Atom var1) {
      for (int var2 = 0; var2 < this.size(); var2++) {
         ((Atom)this.atoms.elementAt(var2)).removeBond(var1);
         this.atoms.removeElement(var1);
      }
   }

   public String toString() {
      StringBuffer var1 = new StringBuffer();
      var1.append("Molecule Number " + this.number + "\n");
      var1.append("Autonom name: " + this.autonomname + "\n");
      var1.append("Other chemical names: \n");

      for (int var2 = 0; var2 < this.chemname.length; var2++) {
         var1.append(this.chemname[var2] + "\n");
      }

      var1.append("CAS registry number: " + this.casrn + "\n");
      var1.append("Beilstein registry number: " + this.brn + "\n");

      for (int var3 = 0; var3 < this.size(); var3++) {
         var1.append(((Atom)this.atoms.elementAt(var3)).toString());
      }

      return var1.toString();
   }
}
