package seneca.client;

import java.util.Vector;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.EventListenerList;
import seneca.core.SenecaDataset;

public class SenecaDatasetCollection extends Vector implements ChangeListener {
   protected transient EventListenerList changeListeners = new EventListenerList();
   protected int currentDatasetNumber;

   public String[] getNames() {
      String[] var1 = new String[this.size()];

      for (int var2 = 0; var2 < var1.length; var2++) {
         var1[var2] = ((SenecaDataset)this.elementAt(var2)).getName();
      }

      return var1;
   }

   public void setCurrentDatasetNumber(int var1) {
      if (var1 != this.currentDatasetNumber) {
         this.currentDatasetNumber = var1;
         this.fireChange();
      }
   }

   public void setCurrentDatasetNumber(String var1) {
      for (int var2 = 0; var2 < this.size(); var2++) {
         if (((SenecaDataset)this.elementAt(var2)).getName().equals(var1) && var2 != this.currentDatasetNumber) {
            this.currentDatasetNumber = var2;
            this.fireChange();
            return;
         }

         if (((SenecaDataset)this.elementAt(var2)).getName().equals(var1) && var2 == this.currentDatasetNumber) {
            return;
         }
      }

      System.err.println("Error: No such dataset!");
   }

   public int getCurrentDatasetNumber() {
      return this.size() == 0 ? -1 : this.currentDatasetNumber;
   }

   public SenecaDataset getCurrentDataset() {
      return this.size() == 0 ? null : (SenecaDataset)this.elementAt(this.currentDatasetNumber);
   }

   public boolean add(Object var1) {
      ((SenecaDataset)var1).addChangeListener(this);
      super.addElement(var1);
      this.setCurrentDatasetNumber(this.size() - 1);
      this.fireChange();
      return true;
   }

   public Object remove(int var1) {
      this.fireChange();
      return super.remove(var1);
   }

   public boolean remove(Object var1) {
      this.fireChange();
      return super.remove(var1);
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

   public void stateChanged(ChangeEvent var1) {
      SenecaDataset var2 = (SenecaDataset)var1.getSource();
      if (var2.destroy) {
         System.out.println("SenecaDatasetCollection says: SenecaDataset " + var2.getName() + " is removed now...");
         System.out.println(this.remove(var2));
         var2 = null;
      }

      this.fireChange();
   }
}
