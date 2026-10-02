package io.github.steinbeck.seneca.chem;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import javax.vecmath.Point2d;

import org.junit.jupiter.api.Test;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.layout.StructureDiagramGenerator;

import io.github.steinbeck.seneca.golden.GoldenFiles;
import io.github.steinbeck.seneca.golden.RunGolden;

/**
 * {@link Layout2D}: CDK's layout, improved where it has crossing bonds or crowded atoms (the structure
 * drawings of the GUI and "Save MDL"; docs/port/gui-design.md DV-88).
 */
class Layout2DTest {

    static IAtomContainer molecule(String mdl) {
        HeavyAtom[] atoms = LegacyMdlReader.read(mdl);
        return CdkBridge.toAtomContainer(atoms, CdkLayoutTest.ct(atoms));
    }

    static IAtomContainer plainCdk(String mdl) throws Exception {
        IAtomContainer m = molecule(mdl);
        new StructureDiagramGenerator().generateCoordinates(m);
        return m;
    }

    static IAtomContainer improved(String mdl) throws Exception {
        IAtomContainer m = molecule(mdl);
        Layout2D.layout(m);
        return m;
    }

    static Point2d[] coordinates(IAtomContainer m) {
        Point2d[] p = new Point2d[m.getAtomCount()];
        for (int i = 0; i < p.length; i++) p[i] = new Point2d(m.getAtom(i).getPoint2d());
        return p;
    }

    /**
     * The pinene tutorial's best result (2,7,7-trimethylnorbornene): plain CDK draws the bridge's
     * gem-dimethyl bonds across the C=C double bond with atoms 0.32 bond lengths apart; the improved
     * layout crosses no multiple bond, has no atom inside a ring and no crowded atoms.
     */
    @Test
    void pineneBestResultIsReadable() throws Exception {
        String mdl = CdkLayoutTest.pineneBestMdl();
        IAtomContainer cdk = plainCdk(mdl);
        assertTrue(Layout2D.multipleBondCrossings(cdk) > 0 && Layout2D.closeContacts(cdk, 0.5) > 0,
                "plain CDK layout is the known bad case");
        IAtomContainer m = improved(mdl);
        assertEquals(0, Layout2D.multipleBondCrossings(m), "no bond crosses the double bond");
        assertEquals(0, Layout2D.closeContacts(m, 0.5), "no crowded atoms");
        assertEquals(0, Layout2D.substituentsInsideRings(m), "no methyl drawn inside the ring");
        assertTrue(Layout2D.penalty(m) < Layout2D.penalty(cdk) / 2, Layout2D.penalty(m) + " vs " + Layout2D.penalty(cdk));
    }

    /**
     * Every golden best structure: never worse than plain CDK, deterministic, and a clean CDK layout
     * (penalty 0) is kept exactly.
     */
    @Test
    void goldenBestStructuresNeverWorseAndDeterministic() throws Exception {
        List<String> report = new ArrayList<>();
        int clean = 0, improvedCount = 0;
        for (GoldenFiles.RunId id : GoldenFiles.runs()) {
            RunGolden g = RunGolden.load(id);
            if (!g.record.has("bestMDL") || g.record.isNull("bestMDL")) continue;
            String mdl = g.record.str("bestMDL");
            IAtomContainer cdk = plainCdk(mdl);
            IAtomContainer a = improved(mdl), b = improved(mdl);
            assertArrayEquals(coordinates(a), coordinates(b), id + ": deterministic");
            double pc = Layout2D.penalty(cdk), pa = Layout2D.penalty(a);
            assertTrue(pa <= pc + 1e-9, id + ": " + pa + " > CDK " + pc);
            if (pc == 0) {
                clean++;
                assertArrayEquals(coordinates(cdk), coordinates(a), id + ": a clean CDK layout is kept");
            } else if (pa < pc) {
                improvedCount++;
            }
            report.add(id + " CDK " + Math.round(pc) + " -> " + Math.round(pa));
        }
        System.out.println("Layout2DTest: " + clean + " clean, " + improvedCount + " improved\n  " + String.join("\n  ", report));
        assertTrue(improvedCount > 10, "improved " + improvedCount);
    }

    /**
     * Every golden best structure, the cage-like Hyperaspine and bn-ye1 results included: no two
     * non-bonded atoms closer than 0.2 bond lengths and no atom within 0.12 bond lengths of the inner part
     * of a bond it does not belong to (the visual review's limits; such a drawing reads as a different
     * constitution). Plain CDK has such overlaps in many of them.
     */
    @Test
    void goldenBestStructuresHaveNoOverlaps() throws Exception {
        List<String> bad = new ArrayList<>();
        int cdkWithOverlaps = 0, n = 0;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (GoldenFiles.RunId id : GoldenFiles.runs()) {
            RunGolden g = RunGolden.load(id);
            if (!g.record.has("bestMDL") || g.record.isNull("bestMDL")) continue;
            String mdl = g.record.str("bestMDL");
            if (!seen.add(mdl)) continue;
            n++;
            if (Layout2D.overlaps(plainCdk(mdl)) > 0) cdkWithOverlaps++;
            IAtomContainer m = improved(mdl);
            int o = Layout2D.overlaps(m);
            if (o > 0) bad.add(id + ": " + o);
        }
        System.out.println("Layout2DTest: " + n + " best structures, plain CDK with overlaps " + cdkWithOverlaps + ", improved with overlaps "
                + bad.size() + " " + bad);
        assertTrue(cdkWithOverlaps > 0, "plain CDK has overlaps somewhere (else the test proves nothing)");
        assertEquals(List.of(), bad, "layouts with overlapping atoms");
    }
}
