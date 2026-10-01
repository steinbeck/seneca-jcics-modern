package seneca.judges;

import java.io.Serializable;

public class TwoDRule implements Serializable, Cloneable {
   int atom1;
   int atom2;

   public TwoDRule(int var1, int var2) {
      this.atom1 = var1;
      this.atom2 = var2;
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
