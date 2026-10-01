package seneca.client;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JComboBox;

class NMRSpectrumTablePanel$1 implements ActionListener {
   NMRSpectrumTablePanel$1(NMRSpectrumTablePanel var1) {
      this.this$0 = var1;
   }

   public void actionPerformed(ActionEvent var1) {
      JComboBox var2 = (JComboBox)var1.getSource();
      String var3 = (String)var2.getSelectedItem();
      if (var3.equals("Paste WinNMR pp from clipboard")) {
         this.this$0.pasteWinNMRpp();
      } else if (var3.equals("Import GIFA pp from file")) {
         this.this$0.loadGIFApp();
      }

      this.this$0.ppSelector.setSelectedIndex(0);
   }
}
