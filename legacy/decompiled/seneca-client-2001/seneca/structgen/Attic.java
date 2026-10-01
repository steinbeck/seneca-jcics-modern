package seneca.structgen;

import compchem.Atom;
import compchem.RingSet;
import java.io.Serializable;

public class Attic implements Serializable, Cloneable {
   public int[][] apsp;
   public Atom[] atomSet;
   public Atom[] normalized;
   public RingSet setOfAllRings;

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
