package seneca.core;

import java.io.Serializable;

public class CNMRSignal extends NMRSignal implements Serializable {
   int DEPT135Phase = 0;
   int DEPT90Phase = 0;
   int DEPT45Phase = 0;

   CNMRSignal() {
      super(NMRSpectrum.SPECTYPE_BB);
   }

   CNMRSignal(float var1, float var2, int var3) {
      this();
      super.shift[0] = var1;
      super.intensity = var2;
      super.phase = var3;
   }

   public String toString() {
      String var1 = "CNMRSignal at " + super.shift[0] + " ppm, ";
      var1 = var1 + "%Int: " + super.intensity;
      return var1 + ", Phase: " + super.phase;
   }
}
