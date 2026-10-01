package compchem;

import java.io.Serializable;

class Bond implements Serializable {
   Atom[] atoms = new Atom[2];
   int order;

   Bond(Atom var1, Atom var2, int var3) {
      this.atoms[0] = var1;
      this.atoms[1] = var2;
      this.order = var3;
   }

   Bond(Atom var1, Atom var2) {
      this.atoms[0] = var1;
      this.atoms[1] = var2;
      this.order = 1;
   }

   public Atom getAtom(int var1) {
      return this.atoms[var1];
   }

   public String toString() {
      return "Bond from atom " + this.atoms[0] + " to atom " + this.atoms[1] + " with order " + this.order + "\n";
   }
}
