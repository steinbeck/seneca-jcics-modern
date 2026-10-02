package io.github.steinbeck.seneca.chem;

import javax.vecmath.Point2d;

import org.openscience.cdk.config.Elements;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.graph.Cycles;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IBond;
import org.openscience.cdk.interfaces.IChemObjectBuilder;
import org.openscience.cdk.interfaces.IPseudoAtom;
import org.openscience.cdk.silent.SilentChemObjectBuilder;

/**
 * The only place where the legacy-style model meets CDK (design.md section 3).
 *
 * <ul>
 *   <li>{@link #toAtomContainer}: heavy atoms in dataset order (CDK atom index = dataset index),
 *       implicit H = {@code hCount} (an undefined, negative count becomes 0), bonds from the bond matrix
 *       ({@code i < j} row-major; 1/2/3 single/double/triple, 4 single flagged aromatic, 5-8 single:
 *       MDL stereo codes are not carried). Unknown symbols (e.g. the wildcard "X") become pseudo atoms.
 *       For depiction (GUI), SMILES output, the CLI reference check and oracle tests.</li>
 *   <li>{@link #sssrRingSizes}: ring sizes of CDK {@code Cycles.sssr} - the documented
 *       {@code LegacyCompat.DEFAULT} fallback of {@code FeatureJudge} when the legacy ring finder
 *       fails on a structure (DEVIATIONS.md DV-01..03). Never used in STRICT mode.</li>
 *   <li>{@link #layout2D}: 2D coordinates of a structure for display (GUI structure drawings, replacing
 *       jmdraw's {@code JMDraw.draw()}) and for "Save MDL" (legacy: "coordinates from the drawing
 *       layout"); CDK {@code StructureDiagramGenerator} with the improvements of {@link Layout2D}.</li>
 * </ul>
 */
public final class CdkBridge {

    private static final IChemObjectBuilder BUILDER = SilentChemObjectBuilder.getInstance();

    private CdkBridge() {
    }

    public static IAtomContainer toAtomContainer(HeavyAtom[] atoms, int[][] bondMatrix) {
        IAtomContainer m = BUILDER.newAtomContainer();
        for (HeavyAtom a : atoms) {
            Elements e = Elements.ofString(a.symbol);
            IAtom atom;
            if (e == Elements.Unknown) {
                IPseudoAtom p = BUILDER.newInstance(IPseudoAtom.class, a.symbol);
                atom = p;
            } else {
                atom = BUILDER.newInstance(IAtom.class, e.symbol());
            }
            atom.setImplicitHydrogenCount(Math.max(0, a.hCount));
            atom.setFormalCharge(a.charge);
            m.addAtom(atom);
        }
        for (int i = 0; i < atoms.length; i++) {
            for (int j = i + 1; j < atoms.length; j++) {
                int o = bondMatrix[i][j];
                if (o <= 0) continue;
                IBond.Order order = switch (o) {
                    case 2 -> IBond.Order.DOUBLE;
                    case 3 -> IBond.Order.TRIPLE;
                    default -> IBond.Order.SINGLE;
                };
                IBond b = BUILDER.newInstance(IBond.class, m.getAtom(i), m.getAtom(j), order);
                if (o == 4) {
                    b.setIsAromatic(true);
                    m.getAtom(i).setIsAromatic(true);
                    m.getAtom(j).setIsAromatic(true);
                }
                m.addBond(b);
            }
        }
        return m;
    }

    /**
     * 2D layout of {@code atoms} (bonds from their adjacency, {@code createConnectionTable} +
     * {@code synchronizeConnectionTable}, as every legacy consumer reads them) with CDK's
     * {@code StructureDiagramGenerator} (bond length 1.5, disconnected fragments side by side), improved by
     * {@link Layout2D} where CDK's layout has crossing bonds or crowded atoms. The
     * coordinates are copied into {@code HeavyAtom.x/y} ({@code z = 0}), so that
     * {@link LegacyMdlWriter#write} writes them; the returned container (CDK atom index = dataset index)
     * carries the same coordinates for depiction. Only display data change: no bond, H count or charge.
     *
     * @throws IllegalArgumentException if CDK cannot lay the structure out
     */
    public static IAtomContainer layout2D(HeavyAtom[] atoms) {
        int[][] bm = AtomSets.createConnectionTable(atoms);
        AtomSets.synchronizeConnectionTable(bm);
        IAtomContainer m = toAtomContainer(atoms, bm);
        try {
            Layout2D.layout(m);
        } catch (CDKException | RuntimeException e) {
            throw new IllegalArgumentException("no 2D layout: " + e.getMessage(), e);
        }
        for (int i = 0; i < atoms.length; i++) {
            Point2d p = m.getAtom(i).getPoint2d();
            atoms[i].x = p.x;
            atoms[i].y = p.y;
            atoms[i].z = 0;
        }
        return m;
    }

    /** Ring sizes of the smallest set of smallest rings (CDK {@code Cycles.sssr}), in CDK's order. */
    public static int[] sssrRingSizes(HeavyAtom[] atoms, int[][] bondMatrix) {
        int[][] paths = Cycles.sssr(toAtomContainer(atoms, bondMatrix)).paths();
        int[] sizes = new int[paths.length];
        for (int i = 0; i < paths.length; i++) sizes[i] = paths[i].length - 1;
        return sizes;
    }
}
