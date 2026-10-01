package compchem;

import java.io.Serializable;

public class Element implements Serializable, Cloneable {
   public String symbol = "";
   public int maxBondOrder = -1;
   public int maxBondOrderSum = -1;

   public Element(String var1, int var2, int var3) {
      this.symbol = var1;
      this.maxBondOrder = var2;
      this.maxBondOrderSum = var3;
   }

   public Object clone() {
      Element var1 = new Element(this.symbol, this.maxBondOrder, this.maxBondOrderSum);
      var1.symbol = this.symbol;
      var1.maxBondOrder = this.maxBondOrder;
      var1.maxBondOrderSum = this.maxBondOrderSum;
      return var1;
   }

   public String toString() {
      String var1 = this.symbol + ": maximum bond order = " + this.maxBondOrder;
      return var1 + "; maximum sum of bond orders = " + this.maxBondOrderSum;
   }
}
