package io.github.steinbeck.seneca.chem;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IBond;

import io.github.steinbeck.seneca.golden.GoldenFiles;
import io.github.steinbeck.seneca.golden.RunGolden;

/**
 * {@link CdkBridge#layout2D}: the 2D layout of the GUI's structure drawings and of "Save MDL"
 * (docs/port/gui-design.md section 8). Only coordinates change; the written molfile keeps the legacy
 * format and reads back to the same structure.
 */
class CdkLayoutTest {

    static String pineneBestMdl() {
        GoldenFiles.RunId id = GoldenFiles.runs().stream()
                .filter(r -> r.key().equals("pinene") && r.profile().equals("gui") && r.seed() == 1).findFirst().orElseThrow();
        return RunGolden.load(id).record.str("bestMDL");
    }

    static int[][] ct(HeavyAtom[] atoms) {
        int[][] ct = AtomSets.createConnectionTable(atoms);
        AtomSets.synchronizeConnectionTable(ct);
        return ct;
    }

    @Test
    void layoutSetsCoordinatesAndKeepsTheStructure() {
        String mdl = pineneBestMdl();
        HeavyAtom[] atoms = LegacyMdlReader.read(mdl);
        int[][] before = ct(atoms);
        int[] hBefore = new int[atoms.length];
        for (int i = 0; i < atoms.length; i++) hBefore[i] = atoms[i].hCount;
        // generator results carry zero coordinates
        for (HeavyAtom a : atoms) assertEquals(0.0, a.x);

        IAtomContainer m = CdkBridge.layout2D(atoms);
        assertEquals(atoms.length, m.getAtomCount());
        Set<String> positions = new HashSet<>();
        for (int i = 0; i < atoms.length; i++) {
            assertFalse(Double.isNaN(atoms[i].x) || Double.isNaN(atoms[i].y), "atom " + i);
            assertEquals(m.getAtom(i).getPoint2d().x, atoms[i].x);
            assertEquals(m.getAtom(i).getPoint2d().y, atoms[i].y);
            assertEquals(0.0, atoms[i].z);
            assertEquals(atoms[i].symbol, m.getAtom(i).getSymbol());
            assertEquals(Integer.valueOf(Math.max(0, atoms[i].hCount)), m.getAtom(i).getImplicitHydrogenCount());
            positions.add(String.format("%.3f/%.3f", atoms[i].x, atoms[i].y));
            assertEquals(hBefore[i], atoms[i].hCount, "H count of atom " + i);
        }
        assertEquals(atoms.length, positions.size(), "every atom has its own position");
        // CDK bond length 1.5 (ring bonds of the bridged pinene skeleton may be stretched a little)
        for (IBond b : m.bonds()) {
            double d = b.getBegin().getPoint2d().distance(b.getEnd().getPoint2d());
            assertTrue(d > 0.5 && d < 3.0, "bond length " + d);
        }
        assertArrayEquals(before, ct(atoms), "bonds unchanged");

        // Save MDL: legacy writer with the layout coordinates, reads back to the same structure
        String written = LegacyMdlWriter.write("pinene0.mol", atoms);
        assertTrue(written.startsWith("pinene0.mol\n\n\n"));
        HeavyAtom[] back = LegacyMdlReader.read(written);
        assertArrayEquals(before, ct(back));
        for (int i = 0; i < atoms.length; i++) {
            assertEquals((float) atoms[i].x, (float) back[i].x, 1e-4);
            assertEquals((float) atoms[i].y, (float) back[i].y, 1e-4);
        }
    }

    @Test
    void disconnectedFragmentsAndHeteroatomsAreLaidOut() {
        // ethanol + water as one "structure" (a LUCY or MDL file may hold fragments)
        Structures.S s = Structures.smiles("CCO.O");
        HeavyAtom[] atoms = s.atoms();
        IAtomContainer m = CdkBridge.layout2D(atoms);
        assertEquals(4, m.getAtomCount());
        assertEquals(2, m.getBondCount());
        double d = Math.hypot(atoms[2].x - atoms[3].x, atoms[2].y - atoms[3].y);
        assertTrue(d > 1.0, "fragments do not overlap: " + d);
        assertEquals(Integer.valueOf(1), m.getAtom(2).getImplicitHydrogenCount());
        assertEquals(Integer.valueOf(2), m.getAtom(3).getImplicitHydrogenCount());
    }
}
