package seneca.core;

import java.io.Serializable;
import java.util.Vector;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.EventListenerList;

public class NMRSpectrum extends Vector implements Serializable {
   public boolean debug = false;
   public String name = "";
   public String specType = "";
   public Vector[] shiftList;
   public float[] pickPrecision;
   public int dim = 1;
   public String[] nucleus;
   public float spectrometerFrequency;
   public String solvent = "";
   public String standard = "";
   public static String NUC_PROTON = "1H";
   public static String NUC_CARBON = "13C";
   public static String NUC_NITROGEN = "15N";
   public static String NUC_PHOSPHORUS = "31P";
   public static String[] SPECTYPE_BB = new String[]{NUC_CARBON};
   public static String[] SPECTYPE_DEPT = new String[]{NUC_CARBON};
   public static String[] SPECTYPE_HMQC = new String[]{NUC_PROTON, NUC_CARBON};
   public static String[] SPECTYPE_HSQC = new String[]{NUC_PROTON, NUC_CARBON};
   public static String[] SPECTYPE_HMBC = new String[]{NUC_PROTON, NUC_CARBON};
   public static String[] SPECTYPE_HHCOSY = new String[]{NUC_PROTON, NUC_PROTON};
   public static String[] SPECTYPE_NOESY = new String[]{NUC_PROTON, NUC_PROTON};
   protected transient EventListenerList changeListeners = new EventListenerList();

   NMRSpectrum(String[] var1, String var2) {
      this.dim = var1.length;
      this.nucleus = var1;
      this.shiftList = new Vector[this.dim];

      for (int var3 = 0; var3 < this.dim; var3++) {
         this.shiftList[var3] = new Vector();
      }

      this.name = var2;
   }

   public int getSignalNumber(int var1) {
      return this.shiftList[var1].size();
   }

   public void addSignal(NMRSignal var1) {
      this.addElement(var1);
      this.updateShiftLists();
      this.fireChange();
   }

   public void newSignal() {
      this.addElement(new NMRSignal(this.nucleus));
      this.updateShiftLists();
      this.fireChange();
   }

   public Object getSignal(int var1) {
      return this.elementAt(var1);
   }

   public void setSpectrometerFrequency(float var1) {
      this.spectrometerFrequency = var1;
   }

   public float getSpectrometerFrequency() {
      return this.spectrometerFrequency;
   }

   public void setSolvent(String var1) {
      this.solvent = var1;
   }

   public String getSolvent() {
      return this.solvent;
   }

   public void setStandard(String var1) {
      this.standard = var1;
   }

   public String getStandard() {
      return this.standard;
   }

   public Object pickClosestSignal(float var1, String var2, float var3) {
      int var4 = -1;
      int var5 = -1;
      float var6 = Float.MAX_VALUE;
      float var7 = 0.0F;

      for (int var8 = 0; var8 < this.nucleus.length; var8++) {
         if (this.nucleus[var8].equals(var2)) {
            var4 = var8;
            break;
         }
      }

      for (int var9 = 0; var9 < this.size(); var9++) {
         if (var6 > Math.abs(((NMRSignal)this.elementAt(var9)).shift[var4] - var1)) {
            var6 = Math.abs(((NMRSignal)this.elementAt(var9)).shift[var4] - var1);
            var5 = var9;
         }
      }

      return var6 < var3 ? this.elementAt(var5) : null;
   }

   public Vector pickSignals(float var1, String var2, float var3) {
      int var4 = -1;
      byte var5 = -1;
      float var6 = Float.MAX_VALUE;
      float var7 = 0.0F;
      Vector var8 = new Vector();

      for (int var9 = 0; var9 < this.nucleus.length; var9++) {
         if (this.nucleus[var9].equals(var2)) {
            if (this.debug) {
               System.out.println("Searching dimension " + var9 + " of type " + var2 + " for shift " + var1);
            }

            var4 = var9;
            break;
         }
      }

      for (int var10 = 0; var10 < this.size(); var10++) {
         if (this.debug) {
            System.out.println(((NMRSignal)this.elementAt(var10)).shift[var4]);
         }

         if (var3 > Math.abs(((NMRSignal)this.elementAt(var10)).shift[var4] - var1)) {
            var8.add(this.elementAt(var10));
         }
      }

      return var8;
   }

   protected void updateShiftLists() {
      boolean var1 = false;
      boolean var2 = false;

      for (int var5 = 0; var5 < this.size(); var5++) {
         NMRSignal var6 = (NMRSignal)this.elementAt(var5);

         for (int var7 = 0; var7 < var6.shift.length; var7++) {
            Float var3 = new Float(var6.shift[var7]);
            if (!this.shiftList[var7].contains(var3)) {
               this.shiftList[var7].add(var3);
            }
         }
      }
   }

   public void report() {
      Object var1 = "";
      System.out.println("Report for nmr spectrum " + this.name + " of type " + this.specType + ": ");

      for (int var2 = 0; var2 < this.shiftList.length; var2++) {
         System.out.println("ShiftList for dimension " + (var2 + 1) + ":");

         for (int var3 = 0; var3 < this.shiftList[var2].size(); var3++) {
            var1 = var1 + (Float)this.shiftList[var2].elementAt(var3) + "; ";
         }

         System.out.println(var1 + "\n");
         var1 = "";
      }
   }

   public void addChangeListener(ChangeListener var1) {
      this.changeListeners
         .add(
            class$javax$swing$event$ChangeListener == null
               ? (class$javax$swing$event$ChangeListener = class$("javax.swing.event.ChangeListener"))
               : class$javax$swing$event$ChangeListener,
            var1
         );
      var1.stateChanged(new ChangeEvent(this));
   }

   public void removeChangeListener(ChangeListener var1) {
      this.changeListeners
         .remove(
            class$javax$swing$event$ChangeListener == null
               ? (class$javax$swing$event$ChangeListener = class$("javax.swing.event.ChangeListener"))
               : class$javax$swing$event$ChangeListener,
            var1
         );
   }

   protected void fireChange() {
      ChangeEvent var1 = new ChangeEvent(this);
      Object[] var2 = this.changeListeners.getListenerList();

      for (int var3 = var2.length - 2; var3 >= 0; var3 -= 2) {
         if (var2[var3]
            == (
               class$javax$swing$event$ChangeListener == null
                  ? (class$javax$swing$event$ChangeListener = class$("javax.swing.event.ChangeListener"))
                  : class$javax$swing$event$ChangeListener
            )) {
            ChangeListener var4 = (ChangeListener)var2[var3 + 1];
            var4.stateChanged(var1);
         }
      }
   }
}
