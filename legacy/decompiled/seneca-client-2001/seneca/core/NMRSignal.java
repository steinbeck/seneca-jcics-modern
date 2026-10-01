package seneca.core;

import java.io.Serializable;

public class NMRSignal implements Serializable {
   public int dim;
   public float[] shift;
   public String[] nucleus;
   public float intensity;
   public float normalizeFactor = 1.0F;
   public int phase;
   public static int DIM_ONE = 1;
   public static int DIM_TWO = 2;
   public static int DIM_THREE = 3;
   public static int DIM_FOUR = 4;
   public static int SHIFT_PROTON = 0;
   public static int SHIFT_HETERO = 1;
   public static int PHASE_NEGATIVE = 2;
   public static int PHASE_POSITIVE = 1;
   public static int PHASE_NONE = 0;
   public static String[] PHASENAMES = new String[]{"NONE", "POSITIVE", "NEGATIVE"};

   public NMRSignal(String[] var1) {
      this.dim = var1.length;
      this.shift = new float[this.dim];
      this.nucleus = var1;

      for (int var2 = 0; var2 < this.dim; var2++) {
         this.shift[var2] = 0.0F;
      }

      this.intensity = 0.0F;
      this.normalizeFactor = 1.0F;
      this.phase = PHASE_NONE;
   }

   public NMRSignal(String[] var1, float[] var2, float var3, int var4) {
      this.dim = var1.length;
      this.shift = var2;
      this.nucleus = var1;
      this.intensity = var3;
      this.normalizeFactor = 1.0F;
      this.phase = var4;
   }

   public void setShift(float var1, String var2) {
      byte var3 = -1;

      for (int var4 = 0; var4 < this.nucleus.length; var4++) {
         if (this.nucleus[var4].equals(var2)) {
            this.shift[var4] = var1;
            break;
         }
      }
   }

   public void setShift(float var1, int var2) {
      this.shift[var2] = var1;
   }

   public float getShift(String var1) {
      byte var2 = -1;

      for (int var3 = 0; var3 < this.nucleus.length; var3++) {
         if (this.nucleus[var3].equals(var1)) {
            return this.shift[var3];
         }
      }

      return Float.MAX_VALUE;
   }

   public float getShift(int var1) {
      return this.shift[var1];
   }

   public String toString() {
      String var1 = "";
      var1 = var1 + this.dim + " -dimensional NMRSignal for nuclei ";

      for (int var2 = 0; var2 < this.nucleus.length; var2++) {
         var1 = var1 + this.nucleus[var2] + "; ";
      }

      var1 = var1 + "\nShiftlist: ";

      for (int var3 = 0; var3 < this.shift.length; var3++) {
         var1 = var1 + this.shift[var3] + "; ";
      }

      return var1 + "\n\n";
   }
}
