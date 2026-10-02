import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.openscience.cdk.depict.Depiction;
import org.openscience.cdk.depict.DepictionGenerator;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.renderer.color.UniColor;
import org.openscience.cdk.renderer.generators.standard.StandardGenerator;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesParser;

/**
 * The application icon of Seneca.app (scripts/build-app.sh): alpha-pinene, the first example dataset,
 * laid out and drawn by CDK (cdk-sdg + cdk-depict, the same libraries that draw the structures in the
 * application) in white on a blue rounded square, with a 13C stick spectrum underneath. Writes the PNG
 * files of a macOS icon set (icon_16x16.png ... icon_512x512@2x.png), each drawn at its own size, into
 * the directory given as the only argument; iconutil turns that directory into an .icns file.
 *
 *   java -cp <runtime classpath> scripts/gui/MakeIcon.java <dir>/Seneca.iconset
 */
public class MakeIcon {

    /** alpha-pinene (pinene.sml: C10H16) */
    static final String SMILES = "CC1=CCC2CC1C2(C)C";

    /** alpha-pinene 13C shifts (ppm), drawn as stick spectrum 0..160 ppm */
    static final double[] SHIFTS = {144.5, 116.0, 47.1, 40.8, 38.0, 31.5, 31.3, 26.4, 23.0, 20.8};

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args.length > 0 ? args[0] : "Seneca.iconset");
        Files.createDirectories(dir);
        IAtomContainer mol = new SmilesParser(SilentChemObjectBuilder.getInstance()).parseSmiles(SMILES);
        int[][] sizes = {{16, 1}, {16, 2}, {32, 1}, {32, 2}, {128, 1}, {128, 2}, {256, 1}, {256, 2}, {512, 1}, {512, 2}};
        for (int[] s : sizes) {
            int px = s[0] * s[1];
            String name = "icon_" + s[0] + "x" + s[0] + (s[1] == 2 ? "@2x" : "") + ".png";
            ImageIO.write(draw(mol, px), "png", dir.resolve(name).toFile());
            System.out.println(dir.resolve(name));
        }
        // a large copy for the documentation and the About box review
        ImageIO.write(draw(mol, 1024), "png", new File(dir.toFile().getParentFile(), "Seneca-1024.png"));
    }

    static BufferedImage draw(IAtomContainer mol, int px) throws Exception {
        BufferedImage img = new BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        // macOS icon grid: a rounded square of 824/1024 of the canvas, centred, with a soft shadow
        double u = px / 1024.0;
        double inset = 100 * u, side = 824 * u, arc = 370 * u;
        if (px >= 64) {
            for (int i = 6; i >= 1; i--) {
                g.setColor(new Color(0, 0, 0, 10));
                g.fill(new RoundRectangle2D.Double(inset - i * u * 2, inset + 8 * u - i * u, side + i * u * 4, side + i * u * 4, arc, arc));
            }
        }
        RoundRectangle2D plate = new RoundRectangle2D.Double(inset, inset, side, side, arc, arc);
        g.setPaint(new GradientPaint((float) inset, (float) inset, new Color(0x2E7FC1),
                (float) inset, (float) (inset + side), new Color(0x163A6E)));
        g.fill(plate);
        g.setClip(plate);

        // 13C stick spectrum along the bottom
        double base = inset + side * 0.86, left = inset + side * 0.12, right = inset + side * 0.88;
        float stroke = (float) Math.max(1.0, 10 * u);
        g.setColor(new Color(0x9FE3FF));
        g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(left, base, right, base));
        if (px >= 32) {
            for (int i = 0; i < SHIFTS.length; i++) {
                double x = right - (SHIFTS[i] / 160.0) * (right - left);
                double h = side * (0.07 + 0.05 * ((i * 7) % 3));
                g.draw(new Line2D.Double(x, base, x, base - h));
            }
        }

        // the molecule, white, fitted into the upper part of the plate
        int box = (int) Math.round(side * 0.62);
        int boxH = (int) Math.round(side * 0.50);
        double strokeRatio = px <= 32 ? 3.0 : px <= 64 ? 2.4 : 2.0;     // thicker bonds at small sizes
        Depiction dep = new DepictionGenerator()
                .withSize(box, boxH)
                .withFillToFit()
                .withPadding(0)
                .withMargin(0)
                .withAtomColors(new UniColor(Color.WHITE))
                .withBackgroundColor(new Color(0, 0, 0, 0))
                .withParam(StandardGenerator.StrokeRatio.class, strokeRatio)
                .depict(mol);
        BufferedImage m = dep.toImg();
        int mx = (int) Math.round(inset + (side - m.getWidth()) / 2.0);
        int my = (int) Math.round(inset + side * 0.09 + (side * 0.54 - m.getHeight()) / 2.0);
        g.drawImage(m, mx, my, null);
        g.dispose();
        return img;
    }
}
