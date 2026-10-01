package seneca.client;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

class Seneca$1 extends WindowAdapter {
   Seneca$1(Seneca var1) {
      this.this$0 = var1;
   }

   public void windowClosing(WindowEvent var1) {
      this.this$0.trySystemExit();
   }
}
