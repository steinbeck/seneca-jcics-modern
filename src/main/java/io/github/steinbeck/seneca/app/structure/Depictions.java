package io.github.steinbeck.seneca.app.structure;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.openscience.cdk.depict.Depiction;
import org.openscience.cdk.depict.DepictionGenerator;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IBond;

import io.github.steinbeck.seneca.chem.CdkBridge;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.chem.LegacyMdlReader;
import io.github.steinbeck.seneca.chem.LegacyMdlWriter;
import io.github.steinbeck.seneca.chem.LucyStructureReader;

/**
 * Structure drawings with CDK instead of jmdraw (docs/port/gui-design.md section 8; feature-inventory
 * F-85). jmdraw was display-only in SENECA: {@code JMDraw.draw()} computed 2D coordinates and the
 * {@code RenderingEngine} drew the molecule scaled to 80 % of the panel, centred, black on white,
 * carbon atoms without a label (unless isolated), every other atom as its symbol with the implicit
 * hydrogens ("OH", "NH2"). The port does the same with CDK:
 *
 * <ol>
 *   <li>MDL text ({@link LegacyMdlReader}, the reader the engine itself uses for its result strings) or
 *       a LUCY result file ({@link LucyStructureReader}) gives {@code HeavyAtom[]} with H counts;</li>
 *   <li>{@link CdkBridge#layout2D} lays the structure out (CDK {@code StructureDiagramGenerator}) and
 *       stores the coordinates in the atoms ("Save MDL" writes them);</li>
 *   <li>{@link #render} draws it with CDK {@code DepictionGenerator} (default style: black, heteroatoms
 *       with their hydrogens, carbons unlabelled) into an image, fitted to 80 % of the area, centred on
 *       white.</li>
 * </ol>
 * A structure that cannot be read or laid out is kept as a {@link Structure} with an {@code error}: its
 * tile shows the title and an empty white area; the problem is logged.
 */
public final class Depictions {

    private static final Logger LOG = Logger.getLogger(Depictions.class.getName());

    /** Fraction of the area the drawing fills (jmdraw {@code scaleIntoArea(molecule, size, 0.8)}). */
    public static final double FILL = 0.8;

    /** Upper bound of the zoom relative to CDK's standard bond length (very small molecules). */
    public static final double MAX_ZOOM = 3.0;

    private Depictions() {
    }

    /**
     * A structure ready for drawing: the atoms (with layout coordinates), the laid-out CDK molecule, or
     * the reason why there is none.
     */
    public static final class Structure {
        private final HeavyAtom[] atoms;
        private final IAtomContainer molecule;
        private final String error;
        private double naturalWidth = -1, naturalHeight = -1;

        Structure(HeavyAtom[] atoms, IAtomContainer molecule, String error) {
            this.atoms = atoms;
            this.molecule = molecule;
            this.error = error;
        }

        /** The atoms with the 2D layout coordinates (null if the input could not be read). */
        public HeavyAtom[] atoms() {
            return atoms;
        }

        /** The laid-out molecule, null if there is nothing to draw. */
        public IAtomContainer molecule() {
            return molecule;
        }

        /** Why there is nothing to draw, or null. */
        public String error() {
            return error;
        }

        /** True if there is a drawing. */
        public boolean drawable() {
            return molecule != null && molecule.getAtomCount() > 0;
        }

        /**
         * The structure as legacy MDL text with the layout coordinates ({@link LegacyMdlWriter}, the
         * format of "Save MDL"); null if the structure could not be read.
         */
        public String mdl(String title) {
            return atoms == null ? null : LegacyMdlWriter.write(title, atoms);
        }
    }

    /** Structure of an MDL molfile text (result strings, "Load MDL"). */
    public static Structure fromMdl(String mdl) {
        if (mdl == null) return new Structure(null, null, "no structure");
        HeavyAtom[] atoms;
        try {
            atoms = LegacyMdlReader.read(mdl);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "unreadable MDL structure", e);
            return new Structure(null, null, "unreadable MDL: " + e);
        }
        return fromAtoms(atoms);
    }

    /** Structure of a LUCY result file ("Load LUCY Result", feature-inventory 7.5). */
    public static Structure fromLucyFile(Path file) {
        HeavyAtom[] atoms;
        try {
            atoms = LucyStructureReader.read(file);
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "unreadable LUCY result " + file, e);
            return new Structure(null, null, "unreadable LUCY result: " + e);
        }
        return fromAtoms(atoms);
    }

    /** Lays {@code atoms} out ({@link CdkBridge#layout2D}); a failure gives an empty structure with the reason. */
    public static Structure fromAtoms(HeavyAtom[] atoms) {
        if (atoms == null) return new Structure(null, null, "no structure");
        for (HeavyAtom a : atoms) {
            if (a == null) {
                LOG.warning("incomplete structure (unread atoms)");
                return new Structure(null, null, "incomplete structure");
            }
        }
        if (atoms.length == 0) return new Structure(atoms, null, "no atoms");
        try {
            IAtomContainer m = CdkBridge.layout2D(atoms);
            // SENECA structures carry no stereo: no wavy "unspecified" bonds in the drawing
            for (IBond b : m.bonds()) b.setStereo(IBond.Stereo.NONE);
            return new Structure(atoms, m, null);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "structure layout failed", e);
            return new Structure(atoms, null, "layout failed: " + e.getMessage());
        }
    }

    /**
     * Draws {@code s} into a new {@code width x height} image (white background; nothing else if the
     * structure is not drawable): CDK depiction fitted to {@link #FILL} of the area (at most
     * {@link #MAX_ZOOM} times the standard bond length), centred. {@code scale} is the device scale
     * (2 on a Retina display): the image has {@code scale} times the pixels, for drawing at
     * {@code width x height} logical pixels.
     */
    public static BufferedImage render(Structure s, int width, int height, double scale) {
        int pw = Math.max(1, (int) Math.round(width * scale));
        int ph = Math.max(1, (int) Math.round(height * scale));
        BufferedImage img = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(Color.white);
            g.fillRect(0, 0, pw, ph);
            if (s == null || !s.drawable()) return img;
            BufferedImage d = depict(s, width, height, scale);
            if (d == null) return img;
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(d, (pw - d.getWidth()) / 2, (ph - d.getHeight()) / 2, null);
            return img;
        } finally {
            g.dispose();
        }
    }

    /** The depiction alone (its own size, white background), fitted into {@code width x height}; null on failure. */
    static BufferedImage depict(Structure s, int width, int height, double scale) {
        try {
            synchronized (s) {
                if (s.naturalWidth < 0) {
                    BufferedImage natural = generator(1.0).depict(s.molecule).toImg();
                    s.naturalWidth = natural.getWidth();
                    s.naturalHeight = natural.getHeight();
                }
            }
            double zoom = Math.min(FILL * width / s.naturalWidth, FILL * height / s.naturalHeight);
            zoom = Math.min(zoom, MAX_ZOOM);
            if (zoom <= 0) return null;
            Depiction d = generator(zoom * scale).depict(s.molecule);
            return d.toImg();
        } catch (CDKException | RuntimeException e) {
            LOG.log(Level.WARNING, "structure depiction failed", e);
            return null;
        }
    }

    private static DepictionGenerator generator(double zoom) {
        return new DepictionGenerator().withBackgroundColor(Color.white).withMargin(0).withZoom(zoom);
    }
}
