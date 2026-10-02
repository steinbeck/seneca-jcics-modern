package io.github.steinbeck.seneca.app.structure;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.image.BufferedImage;

import javax.swing.JComponent;
import javax.swing.ToolTipManager;

/**
 * A structure drawing (port of {@code JMDrawPanel}, feature-inventory F-85): the best structure in the
 * "Structure Generation" tab (F-86) and every tile of the "Structure Display" window (F-90). Paints
 * the {@link Depictions.Structure} on white inside its border (legacy: white once a structure was set),
 * fitted to 80 % of the area and centred; without a structure the component shows only its border
 * (legacy: grey panel, "No structure available").
 *
 * <p>The layout is computed once per structure ({@link Depictions#fromMdl} etc.); the rendered image is
 * cached for the current size and device scale, so painting never lays out or depicts again. EDT only.
 */
public class StructureView extends JComponent {

    private Depictions.Structure structure;
    private BufferedImage cache;
    private int cacheW, cacheH;
    private double cacheScale;

    public StructureView() {
        setOpaque(false);
        // legacy JMDrawPanel: long tooltip dismiss delay (the evaluation HTML is long)
        ToolTipManager.sharedInstance().setDismissDelay(100000);
    }

    /** Shows {@code s} (null: nothing). */
    public void setStructure(Depictions.Structure s) {
        this.structure = s;
        this.cache = null;
        repaint();
    }

    public Depictions.Structure structure() {
        return structure;
    }

    /** The drawing at {@code width x height} (white background), e.g. for the clipboard. */
    public BufferedImage image(int width, int height) {
        return Depictions.render(structure, width, height, 1.0);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (structure == null) return;
        Insets in = getInsets();
        int w = getWidth() - in.left - in.right;
        int h = getHeight() - in.top - in.bottom;
        if (w <= 0 || h <= 0) return;
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            double scale = Math.max(1.0, g2.getTransform().getScaleX());
            if (cache == null || cacheW != w || cacheH != h || cacheScale != scale) {
                cache = Depictions.render(structure, w, h, scale);
                cacheW = w;
                cacheH = h;
                cacheScale = scale;
            }
            g2.setColor(Color.white);
            g2.fillRect(in.left, in.top, w, h);
            g2.drawImage(cache, in.left, in.top, w, h, null);
        } finally {
            g2.dispose();
        }
    }
}
