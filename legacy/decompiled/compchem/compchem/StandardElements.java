package compchem;

import java.io.Serializable;

public class StandardElements implements Serializable {
   private Element[] elements = new Element[]{
      new Element("H", 1, 1),
      new Element("C", 3, 4),
      new Element("Si", 3, 4),
      new Element("N", 3, 3),
      new Element("P", 3, 3),
      new Element("O", 2, 2),
      new Element("S", 2, 2),
      new Element("F", 1, 1),
      new Element("Cl", 1, 1),
      new Element("Br", 1, 1),
      new Element("I", 1, 1),
      new Element("X", 3, 10)
   };

   public int getSize() {
      return this.elements.length;
   }

   public int getMaxBondOrder(String var1) {
      Element var2 = this.getElement(var1);
      return var2 != null ? var2.maxBondOrder : -1;
   }

   public int getMaxBondOrderSum(String var1) {
      Element var2 = this.getElement(var1);
      return var2 != null ? var2.maxBondOrderSum : -1;
   }

   public Element elementAt(int var1) {
      return var1 >= 0 && var1 < this.elements.length ? (Element)this.elements[var1].clone() : null;
   }

   private void enlargeElementArray() {
      int var1 = (int)(this.elements.length * 1.1);
      Element[] var2 = new Element[var1];
      System.arraycopy(this.elements, 0, var2, 0, this.elements.length);
      this.elements = var2;
   }

   private Element getElement(String var1) {
      for (int var2 = 0; var2 < this.elements.length; var2++) {
         if (this.elements[var2].symbol.equals(var1)) {
            return this.elements[var2];
         }
      }

      return null;
   }
}
