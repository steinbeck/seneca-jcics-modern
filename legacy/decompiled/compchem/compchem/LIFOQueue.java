package compchem;

import java.io.Serializable;
import java.util.Vector;

public class LIFOQueue extends Vector implements Serializable {
   public int size;

   public LIFOQueue(int var1) {
      this.size = var1;
   }

   public void push(Object var1) {
      this.insertElementAt(var1, 0);
      if (this.size() > this.size) {
         this.setSize(this.size);
      }
   }

   public Object pop() {
      Object var1 = this.elementAt(0);
      this.removeElementAt(0);
      return var1;
   }
}
