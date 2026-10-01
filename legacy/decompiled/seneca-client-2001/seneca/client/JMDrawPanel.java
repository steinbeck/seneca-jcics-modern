package seneca.client;

import compchem.Molecule;
import java.awt.Graphics;
import javax.swing.JPanel;
import javax.swing.ToolTipManager;
import jmdraw.JMDraw;
import jmdraw.RenderingEngine;

public class JMDrawPanel extends JPanel {
   JMDraw jmd = null;

   public JMDrawPanel() {
      this(new JMDraw());
   }

   public JMDrawPanel(JMDraw var1) {
      ToolTipManager var2 = ToolTipManager.sharedInstance();
      var2.setDismissDelay(100000);
      this.jmd = var1;
   }

   public void paint(Graphics var1) {
      super.paint(var1);
      if (this.jmd != null && this.jmd.atomSet != null && this.jmd.atomSet.length > 0) {
         this.setBackground(this.jmd.getRenderingAttributes().getBackColor());
         Molecule var2 = new Molecule(this.jmd.getAtomSet());
         RenderingEngine var3 = new RenderingEngine();
         RenderingEngine.scaleIntoArea(var2, this.getSize(), 0.8);
         RenderingEngine.translateAllPositive(var2);
         RenderingEngine.center(var2, this.getSize());
         var3.drawMolecule(var1, var2, this.jmd.getRenderingAttributes());
      }
   }

   public JMDraw getJMDraw() {
      return this.jmd;
   }

   public void setJMDraw(JMDraw var1) {
      this.jmd = var1;
   }
}
