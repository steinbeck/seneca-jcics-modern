package jmdraw;

import compchem.Molecule;
import java.awt.Color;
import java.awt.Font;

public class RenderingAttributes {
   Font font = null;
   boolean[] highlight;
   boolean[] substructure;
   boolean[] drawNumber;
   public boolean drawNumbers = false;
   public boolean drawSymbols = false;
   public boolean drawHydrogens = false;
   public boolean drawImplicitHydrogens = true;
   Color highlightColor = Color.lightGray;
   Color substructureColor = Color.red;
   Color substructureHighlightColor = Color.pink;
   Color foreColor = Color.black;
   Color backColor = Color.white;

   public RenderingAttributes(Molecule var1) {
      this.highlight = new boolean[var1.size()];
      this.substructure = new boolean[var1.size()];
      this.drawNumber = new boolean[var1.size()];
   }

   public boolean[] getHighlight() {
      return this.highlight;
   }

   public boolean[] getSubstructure() {
      return this.substructure;
   }

   public void setSubstructure(boolean[] var1) {
      this.substructure = var1;
   }

   public void updateSubstructure(int var1, boolean var2) {
      this.substructure[var1] = var2;
   }

   public Color getSubstructureHighlightColor() {
      return this.substructureHighlightColor;
   }

   public void setSubstructureHighlightColor(Color var1) {
      this.substructureHighlightColor = var1;
   }

   public Color getSubstructureColor() {
      return this.substructureColor;
   }

   public void setSubstructureColor(Color var1) {
      this.substructureColor = var1;
   }

   public void setHighlight(boolean[] var1) {
      this.highlight = var1;
   }

   public void highlight(int var1, boolean var2) {
      this.highlight[var1] = var2;
   }

   public Color getHighlightColor() {
      return this.highlightColor;
   }

   public void setHighlightColor(Color var1) {
      this.highlightColor = var1;
   }

   public Color getBackColor() {
      return this.backColor;
   }

   public void setBackColor(Color var1) {
      this.backColor = var1;
   }

   public Font getFont() {
      return this.font != null ? this.font : null;
   }

   public void setFont(Font var1) {
      this.font = var1;
   }

   public boolean[] getDrawNumber() {
      return this.drawNumber;
   }

   public void setDrawNumber(boolean[] var1) {
      this.drawNumber = var1;
   }
}
