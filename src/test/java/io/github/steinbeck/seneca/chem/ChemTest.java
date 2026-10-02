package io.github.steinbeck.seneca.chem;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.steinbeck.seneca.chem.Structures.S;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests of the compchem re-implementations (values from docs/legacy/compchem-semantics.md; the
 * differential tests against compchem.jar are in src/test-oracle, {@code mvn -Poracle test}).
 */
@Tag("A")
class ChemTest {

    static List<String> symbols(HeavyAtom[] atoms) {
        List<String> l = new ArrayList<>();
        for (HeavyAtom a : atoms) l.add(a.symbol);
        return l;
    }

    @Test
    void molecularFormula() {
        HeavyAtom[] a = MolecularFormula.heavyAtoms("C10H16O");
        assertEquals(11, a.length);
        assertEquals("O", a[10].symbol);
        assertEquals(List.of("C", "C", "O", "O", "N"), symbols(MolecularFormula.heavyAtoms("C2H4O2N")));
        assertEquals(16, MolecularFormula.atomCount("C10H16O", "H"));
        assertEquals(1, MolecularFormula.atomCount("C10H16O", "O"));
        assertEquals("152.12016", Float.toString(MolecularFormula.mass("C10H16O")));
        assertEquals(0.0F, MolecularFormula.mass("C10Xx"));
        assertThrows(NullPointerException.class, () -> MolecularFormula.heavyAtoms(""));
        // formula string: element order of first appearance, H after the first element
        S s = Structures.smiles("OC(=O)CN");
        assertEquals("O2H5C2N", MolecularFormula.formulaOf(s.atoms()));
        assertEquals(5, MolecularFormula.atomCount(s.atoms(), "H"));
        assertEquals(2, MolecularFormula.atomCount(s.atoms(), "O"));
    }

    @Test
    void bremserTable() throws Exception {
        assertEquals(651, BremserTable.PUTS);
        assertEquals(648, BremserPredictor.tableSize());
        BremserPredictor p = new BremserPredictor();
        // the later put wins
        assertEquals(113.5, p.predict("=CC$C(//)"));
        assertEquals(21.5, p.getConfidenceLimit("=CC$C(//)"));
        assertEquals(109.8, p.predict("=CC$(//)"));
        assertEquals(5.0, p.getConfidenceLimit("=CC$(//)"));
        assertEquals(35.9, p.predict("PP(//)"));
        assertEquals(32.9, p.getConfidenceLimit("PP(//)"));
        assertEquals(70.8, p.predict("C$C§CN(//)"));
        assertThrows(BremserPredictor.UnknownCodeException.class, () -> p.predict("CNNN(//)"));
    }

    @Test
    void hoseCodes() {
        HoseCodeGenerator g = new HoseCodeGenerator();
        S ketone = Structures.smiles("CC(=O)CC");
        assertEquals("=OCC(//)", g.getHOSECode(ketone.atoms(), ketone.atoms()[1], 1));
        assertEquals("C(//)", g.getHOSECode(ketone.atoms(), ketone.atoms()[0], 1));
        S nitrile = Structures.smiles("CC#N");
        assertEquals("%NC(//)", g.getHOSECode(nitrile.atoms(), nitrile.atoms()[1], 1));
        S ccl3 = Structures.smiles("ClC(Cl)(Cl)C");
        assertEquals("CXXX(//)", g.getHOSECode(ccl3.atoms(), ccl3.atoms()[1], 1));
        S si = Structures.smiles("C[Si](C)(C)Br");
        assertEquals("CCCY(//)", g.getHOSECode(si.atoms(), si.atoms()[1], 1));
        assertEquals("Q(//)", g.getHOSECode(si.atoms(), si.atoms()[0], 1));
        // isolated atom: no sphere code, delimiters from sphere 2 on
        S single = Structures.of("C", new String[] {"C"}, new int[] {4}, new int[1][1]);
        assertEquals("//)", g.getHOSECode(single.atoms(), single.atoms()[0], 1));
        // two spheres (not used by SENECA, ported for fidelity)
        S propanone = Structures.smiles("CC(=O)C");
        assertEquals("C(=OC//)", g.getHOSECode(propanone.atoms(), propanone.atoms()[0], 2));
        // the wildcard X has no major isotope: compchem throws a NullPointerException
        S x = Structures.of("CX", new String[] {"C", "X"}, new int[] {3, 0}, new int[][] {{0, 1}, {1, 0}});
        assertThrows(NullPointerException.class, () -> g.getHOSECode(x.atoms(), x.atoms()[0], 1));
    }

    static List<Integer> ringSizes(HeavyAtom[] atoms) {
        List<Integer> l = new ArrayList<>();
        for (Ring r : new RingFinder().findSSSR(atoms)) l.add(r.size());
        return l;
    }

    static List<Integer> sorted(List<Integer> l) {
        List<Integer> s = new ArrayList<>(l);
        s.sort(null);
        return s;
    }

    @Test
    void ringFinder() {
        assertEquals(List.of(6), ringSizes(Structures.smiles("C1CCCCC1").atoms()));
        assertEquals(List.of(), ringSizes(Structures.smiles("CCCC(C)C").atoms()));
        // values confirmed with compchem.jar (oracle tests); not a minimum cycle basis (compchem-semantics.md 6.2)
        assertEquals(List.of(6, 6), ringSizes(Structures.smiles("C1=CC2C=CC1C=C2").atoms()));
        assertEquals(List.of(3, 4, 6, 3, 3), ringSizes(Structures.smiles("C12C3C1C3C4C5C2C54").atoms()));
        assertEquals(List.of(4, 4, 4, 4, 4), ringSizes(Structures.smiles("C12C3C4C1C5C2C3C45").atoms()));
        // the input is untouched
        S s = Structures.smiles("C1CC2CCC1C2");
        int[][] before = AtomSets.createConnectionTable(s.atoms());
        new RingFinder().findSSSR(s.atoms());
        assertArrayEquals(before, AtomSets.createConnectionTable(s.atoms()));
        // ring normalisation: smallest number first, towards its smaller neighbour
        Ring r = new RingFinder().findSSSR(Structures.smiles("C1CCCCC1").atoms()).get(0);
        assertEquals(0, r.atoms.get(0).number);
        assertEquals(1, r.atoms.get(1).number);
        assertEquals("0-1-2-3-4-5", r.key());
    }

    @Test
    void ringFinderFailureModes() {
        // mode 1: every golden crash candidate, legacy exception class and message
        List<S> crashes = Structures.crashCandidates();
        assertEquals(13, crashes.size());
        for (S c : crashes) {
            ArrayIndexOutOfBoundsException e = assertThrows(ArrayIndexOutOfBoundsException.class, () -> new RingFinder().findSSSR(c.atoms()), c.name());
            assertEquals("Index -1 out of bounds for length 10", e.getMessage(), c.name());
        }
        // mode 2: degree-3 atom on no cycle
        ArrayIndexOutOfBoundsException e2 = assertThrows(ArrayIndexOutOfBoundsException.class,
                () -> new RingFinder().findSSSR(Structures.threeK4().atoms()));
        assertEquals("0 >= 0", e2.getMessage());
        // mode 3: legacy hangs, the port detects it
        RingFinder.HangDetected e3 = assertThrows(RingFinder.HangDetected.class, () -> new RingFinder().findSSSR(Structures.k5().atoms()));
        assertTrue(e3.getMessage().contains("degree is 4"), e3.getMessage());
    }

    @Test
    void cdkBridge() {
        S s = Structures.smiles("C1=CC2C=CC1C=C2");
        int[] sizes = CdkBridge.sssrRingSizes(s.atoms(), s.bm());
        Arrays.sort(sizes);
        assertArrayEquals(new int[] {6, 6}, sizes);
        var m = CdkBridge.toAtomContainer(s.atoms(), s.bm());
        assertEquals(8, m.getAtomCount());
        assertEquals(9, m.getBondCount());
        int[] k5 = CdkBridge.sssrRingSizes(Structures.k5().atoms(), Structures.k5().bm());
        assertEquals(6, k5.length);
        for (S c : Structures.crashCandidates()) assertTrue(CdkBridge.sssrRingSizes(c.atoms(), c.bm()).length > 0, c.name());
    }

    @Test
    void mdlWriterAndReader() {
        S s = Structures.smiles("CC(=O)C#N");
        String mdl = LegacyMdlWriter.write("Score: 1200", s.atoms());
        String expected = "Score: 1200\n\n\n"
                + "  5  4  0  0  0  0  0  0  0  0  1 V2000\n"
                + "    0.0000    0.0000    0.0000 C   0  0  0  0  0  0  0  0  0  0  0  0\n"
                + "    0.0000    0.0000    0.0000 C   0  0  0  0  0  0  0  0  0  0  0  0\n"
                + "    0.0000    0.0000    0.0000 O   0  0  0  0  0  0  0  0  0  0  0  0\n"
                + "    0.0000    0.0000    0.0000 C   0  0  0  0  0  0  0  0  0  0  0  0\n"
                + "    0.0000    0.0000    0.0000 N   0  0  0  0  0  0  0  0  0  0  0  0\n"
                + "  1  2  1  0  0  0  0\n"
                + "  2  3  2  0  0  0  0\n"
                + "  2  4  1  0  0  0  0\n"
                + "  4  5  3  0  0  0  0\n"
                + "M  END\n";
        assertEquals(expected, mdl);
        HeavyAtom[] back = LegacyMdlReader.read(mdl);
        assertEquals(5, back.length);
        assertArrayEquals(AtomSets.createConnectionTable(s.atoms()), AtomSets.createConnectionTable(back));
        for (int i = 0; i < 5; i++) {
            assertEquals(s.atoms()[i].hCount, back[i].hCount, "H count after saturate(), atom " + i);
            assertEquals(i, back[i].number);
        }
        // a truncated file gives the partial structure
        HeavyAtom[] partial = LegacyMdlReader.read(mdl.substring(0, mdl.indexOf("  1  2  1")));
        assertEquals(5, partial.length);
        assertEquals(0, partial[0].degree);
        assertEquals(0, LegacyMdlReader.read("").length);
        // 3 integer digits at most, higher digits dropped (legacy NumberFormat)
        assertEquals("000", LegacyMdlWriter.formatMDLInt(1000, 3));
        assertEquals("   -1.5000", LegacyMdlWriter.formatMDLFloat(-1.5f));
    }

    @Test
    void isomorphismHeuristic() {
        // atoms are matched by Morgan value, not by index: another numbering of the same molecule is "isomorphic"
        S a = Structures.smiles("CC(C)C");
        S b = Structures.smiles("C(C)(C)C");
        assertTrue(new IsomorphismTester(a.atoms()).isIsomorph(b.atoms()));
        assertTrue(IsomorphismTester.isIsomorph(b.atoms(), a.atoms()));
        // false negatives (values confirmed with compchem.jar): atoms with different element or H count share a
        // Morgan value, so these molecules are not even "isomorphic" to themselves
        S ethanol = Structures.smiles("CCO");
        assertFalse(new IsomorphismTester(ethanol.atoms()).isIsomorph(ethanol.atoms()));
        S methylbutane = Structures.smiles("CC(C)CC");
        assertArrayEquals(new int[] {34, 48, 34, 48, 20}, AtomSets.computeMorganMatrix(methylbutane.atoms()));
        assertFalse(new IsomorphismTester(methylbutane.atoms()).isIsomorph(methylbutane.atoms()));
        assertFalse(IsomorphismTester.isIsomorph(ethanol.atoms(), Structures.smiles("CCC").atoms()));
    }

    @Test
    void lucyStructureReader(@TempDir Path dir) throws IOException {
        Path f = dir.resolve("acetone.lucy");
        Files.writeString(f, "acetone\n4 3\n1 C 1 1 3\n2 C 1 1 0\n3 O 1 1 0\n4 C 1 1 3\n1 2 1\n2 3 1\n2 4 1\n", StandardCharsets.ISO_8859_1);
        HeavyAtom[] a = LucyStructureReader.read(f);
        assertEquals(4, a.length);
        assertEquals(2, a[1].getBondOrder(a[2]), "C=O promoted");
        assertEquals(1, a[1].getBondOrder(a[0]));
        assertEquals(2, a[2].getBondOrder(a[1]));
        HeavyAtom[] missing = LucyStructureReader.read(dir.resolve("nope"));
        assertEquals(1, missing.length);
        assertNull(missing[0]);
    }
}
