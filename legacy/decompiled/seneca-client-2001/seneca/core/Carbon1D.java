package seneca.core;

import java.io.Serializable;

public class Carbon1D extends NMRSpectrum implements Serializable {
   public static final int DEPT_POSITIVE = 1;
   public static final int DEPT_NEGATIVE = 2;
   public static final int DEPT_NULL = 0;
   public static final int BB = 0;
   public static final int GD = 1;
   public static final int DEPT135 = 2;
   public static final int DEPT90 = 3;
   public static final int DEPT45 = 4;
   public int DEPT135Phase = 0;
   public int DEPT90Phase = 0;
   public int DEPT45Phase = 0;
   static String[] nucleus = new String[]{NMRSpectrum.NUC_CARBON};

   Carbon1D() {
      super(nucleus, "carbon1d");
      super.specType = "Carbon1D";
   }

   public void addSignal(CNMRSignal var1) {
      this.fireChange();
      this.addElement(var1);
      this.updateShiftLists();
   }

   public void replaceSignalAt(CNMRSignal var1, int var2) {
      this.fireChange();
      this.setElementAt(var1, var2);
      this.updateShiftLists();
   }
}
