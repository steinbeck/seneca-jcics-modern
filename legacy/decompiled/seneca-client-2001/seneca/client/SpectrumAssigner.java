package seneca.client;

import seneca.core.SenecaDataset;

public class SpectrumAssigner {
   SenecaDataset sd = null;
   boolean debug = false;

   public SpectrumAssigner(SenecaDataset var1) {
      this.sd = var1;
   }

   public SpectrumAssigner() {
   }

   public void setSenecaDataset(SenecaDataset var1) {
      this.sd = var1;
   }

   public boolean assign() {
      return false;
   }

   public void setVerbose(boolean var1) {
      this.debug = var1;
   }
}
