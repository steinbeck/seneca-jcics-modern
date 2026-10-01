package compchem;

import java.io.Serializable;

public class Isotope implements Cloneable, Serializable {
   public String symbol = "";
   public int atomicMass = -1;
   public double exactMass = -1.0;
   public double naturalAbundance = -1.0;

   public Isotope() {
   }

   public Isotope(int var1, String var2, double var3, double var5) {
      this.atomicMass = var1;
      this.symbol = var2;
      this.exactMass = var3;
      this.naturalAbundance = var5;
   }

   public Object clone() {
      Isotope var1 = new Isotope();
      var1.symbol = this.symbol;
      var1.atomicMass = this.atomicMass;
      var1.exactMass = this.exactMass;
      var1.naturalAbundance = this.naturalAbundance;
      return var1;
   }

   public String toString() {
      String var1 = "[" + this.atomicMass + "]";
      var1 = var1 + this.symbol + ": exact mass = " + this.exactMass;
      return var1 + "; relative natural abundance = " + this.naturalAbundance;
   }
}
