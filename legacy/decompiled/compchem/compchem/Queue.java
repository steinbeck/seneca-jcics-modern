package compchem;

import java.io.Serializable;
import java.util.Vector;

public class Queue extends Vector implements Serializable {
   public void push(Object var1) {
      this.addElement(var1);
   }

   public Object pop() {
      Object var1 = this.elementAt(0);
      this.removeElementAt(0);
      return var1;
   }
}
