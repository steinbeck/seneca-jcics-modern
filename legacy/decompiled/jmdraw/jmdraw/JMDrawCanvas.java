package jmdraw;

import java.awt.Canvas;
import java.awt.Graphics;

public class JMDrawCanvas extends Canvas {
   JMDraw jmd = null;

   public JMDrawCanvas(JMDraw var1) {
      this.jmd = var1;
   }

   public void paint(Graphics var1) {
      this.jmd.paint(var1);
   }
}
