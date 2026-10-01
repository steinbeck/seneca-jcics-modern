package seneca.client;

import java.awt.BorderLayout;
import javax.swing.JPanel;

public class AnnealingViewPanel extends JPanel {
   private int[] score;
   private int[] temperature;

   AnnealingViewPanel() {
      this.setLayout(new BorderLayout());
   }

   public void paint() {
   }
}
