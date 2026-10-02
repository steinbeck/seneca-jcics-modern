package io.github.steinbeck.seneca.oracle;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import compchem.Atom;
import compchem.BremserOneSphereHOSECodePredictor;
import compchem.DataStructureTools;
import compchem.MFAnalyser;
import compchem.SetOfRingsFinder;
import io.github.steinbeck.seneca.chem.BremserPredictor;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.chem.MolecularFormula;
import io.github.steinbeck.seneca.chem.RingFinder;
import io.github.steinbeck.seneca.oracle.OracleSupport.Struct;
import io.github.steinbeck.seneca.oracle.OracleSupport.Tally;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tables and parsers against compchem.jar: the complete Bremser table (every key of the legacy
 * {@code Hashtable}, read by reflection: shift and confidence limit, and against CDK's
 * {@code BremserOneSphereHOSECodePredictor}), {@code MFAnalyser} (formula parsing incl. odd inputs,
 * masses, atom counts) and the ring finder's failure modes 2 and 3 on constructed structures.
 */
@Tag("A")
class TablesOracleTest {

    @SuppressWarnings("unchecked")
    static Hashtable<String, Object> legacyBremserTable() throws Exception {
        BremserOneSphereHOSECodePredictor p = new BremserOneSphereHOSECodePredictor();
        Field f = BremserOneSphereHOSECodePredictor.class.getDeclaredField("ht");
        f.setAccessible(true);
        return (Hashtable<String, Object>) f.get(p);
    }

    @Test
    void bremserTableComplete() throws Exception {
        BremserOneSphereHOSECodePredictor lp = new BremserOneSphereHOSECodePredictor();
        BremserPredictor pp = new BremserPredictor();
        org.openscience.cdk.tools.BremserOneSphereHOSECodePredictor cp = new org.openscience.cdk.tools.BremserOneSphereHOSECodePredictor();
        Hashtable<String, Object> ht = legacyBremserTable();
        assertEquals(648, ht.size());
        assertEquals(ht.size(), BremserPredictor.tableSize());
        int cdkCompared = 0;
        for (Enumeration<String> e = ht.keys(); e.hasMoreElements(); ) {
            String code = e.nextElement();
            assertEquals(Double.doubleToRawLongBits(lp.predict(code)), Double.doubleToRawLongBits(pp.predict(code)), code + " shift");
            assertEquals(Double.doubleToRawLongBits(lp.getConfidenceLimit(code)), Double.doubleToRawLongBits(pp.getConfidenceLimit(code)), code + " limit");
            assertEquals(lp.predictFull(code), pp.predictFull(code), code + " predictFull");
            if (code.indexOf('§') < 0) {
                assertEquals(lp.predict(code), cp.predict(code), code + " CDK shift");
                assertEquals(lp.getConfidenceLimit(code), cp.getConfidenceLimit(code), code + " CDK limit");
                cdkCompared++;
            }
        }
        assertEquals(647, cdkCompared);
        assertThrows(BremserPredictor.UnknownCodeException.class, () -> pp.predict("CNNN(//)"));
        assertThrows(Exception.class, () -> lp.predict("CNNN(//)"));
    }

    static final String[] FORMULAS = {
        "C10H16O", "C15H28O2", "C19H30N2O3", "C13H14O3", "C30H48O2", "C10H16", "CH4", "C2H5Br", "C6H5Cl",
        "C6H18OSi2", "C4H11P", "CHClBrI", "C2HF3", "C8H10N4O2", "H2O", "O2H2", "C1H0", "Si", "c", "2C", "C10 H16 O",
        "C10H16O.", "C-10", "Xx2C", "B2H6", "C2H6S", "C12H22O11", "N2", "C100H202", "ClCH2Cl", "c6h6", "C6H6x",
    };

    @Test
    void mfAnalyser() {
        Tally t = new Tally();
        for (String mf : FORMULAS) {
            MFAnalyser a = new MFAnalyser(mf);
            List<String> heavy = new ArrayList<>();
            for (Atom x : a.getSetOfHeavyNodes()) heavy.add(x.symbol + "/" + x.HCount + "/" + x.number + "/" + x.maxBondOrderSum);
            List<String> port = new ArrayList<>();
            for (HeavyAtom x : MolecularFormula.heavyAtoms(mf)) port.add(x.symbol + "/" + x.hCount + "/" + x.number + "/" + x.maxBondOrderSum);
            t.same("heavy atoms", heavy, port, mf);
            for (String sym : new String[] {"C", "H", "O", "N", "Si", "Cl", "c", ""})
                t.same("atom count " + sym, a.getAtomCount(sym), MolecularFormula.atomCount(mf, sym), mf);
            t.same("mass", Float.floatToRawIntBits(DataStructureTools.getMass(mf)), Float.floatToRawIntBits(MolecularFormula.mass(mf)), mf);
            t.same("mass (MFAnalyser)", Float.floatToRawIntBits(a.getMass()), Float.floatToRawIntBits(MolecularFormula.mass(mf)), mf);
        }
        t.same("empty formula heavy atoms", OracleSupport.outcome(() -> new MFAnalyser("").getSetOfHeavyNodes()),
                OracleSupport.outcome(() -> MolecularFormula.heavyAtoms("")), "");
        t.same("empty formula mass", OracleSupport.outcome(() -> DataStructureTools.getMass("")),
                OracleSupport.outcome(() -> MolecularFormula.mass("")), "");
        t.same("overflowing count", OracleSupport.outcome(() -> new MFAnalyser("C99999999999").getSetOfHeavyNodes().length),
                OracleSupport.outcome(() -> MolecularFormula.heavyAtoms("C99999999999").length), "");
        t.assertNone();
        assertTrue(t.checks("heavy atoms") == FORMULAS.length);
    }

    /** Runs the legacy ring finder with a time limit: its result, its exception, or "hang". */
    static String legacyRingsWithTimeout(Atom[] atoms) throws Exception {
        ExecutorService ex = Executors.newSingleThreadExecutor(r -> {
            Thread th = new Thread(r, "legacy-ring-finder");
            th.setDaemon(true);
            return th;
        });
        try {
            Future<String> f = ex.submit(() -> RandomIsomerOracleTest.legacyRings(atoms));
            return f.get(3, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            return "hang";
        } finally {
            ex.shutdownNow(); // the legacy loop does not react to interrupts; the daemon thread is abandoned
        }
    }

    /** Failure mode 3: K5 of quaternary carbons - every atom has degree 4, legacy loops forever. */
    @Test
    void ringFinderHang() throws Exception {
        int[][] bm = new int[5][5];
        for (int i = 0; i < 5; i++) for (int j = 0; j < 5; j++) if (i != j) bm[i][j] = 1;
        Struct s = new Struct("K5", new String[] {"C", "C", "C", "C", "C"}, new int[5], bm);
        assertEquals("hang", legacyRingsWithTimeout(s.legacy()));
        assertThrows(RingFinder.HangDetected.class, () -> new RingFinder().findSSSR(s.port()));
    }

    /**
     * Failure mode 2: a degree-3 atom on no cycle is the first atom of minimal degree 3: atom 0 joins three
     * K4 units by bridges, so {@code findSRing(atom 0)} is empty and {@code checkEdges} reads
     * {@code elementAt(0)} of an empty vector.
     */
    @Test
    void ringFinderDegree3EmptyRing() throws Exception {
        int n = 13;
        int[][] bm = new int[n][n];
        for (int k = 0; k < 3; k++) {
            int base = 1 + 4 * k;
            for (int i = base; i < base + 4; i++) for (int j = base; j < base + 4; j++) if (i != j) bm[i][j] = 1;
            bm[0][base] = bm[base][0] = 1;
        }
        String[] sym = new String[n];
        java.util.Arrays.fill(sym, "C");
        Struct s = new Struct("3xK4", sym, new int[n], bm);
        String legacy = legacyRingsWithTimeout(s.legacy());
        assertEquals("java.lang.ArrayIndexOutOfBoundsException: 0 >= 0", legacy);
        assertEquals(legacy, RandomIsomerOracleTest.portRings(s.port()));
    }

    /** Failure mode 1 on all 13 golden crash candidates: same exception class and message. */
    @Test
    void ringFinderCrashCandidates() {
        List<Struct> l = OracleSupport.crashCandidates();
        assertEquals(13, l.size());
        for (Struct s : l) {
            String legacy = RandomIsomerOracleTest.legacyRings(s.legacy());
            assertEquals("java.lang.ArrayIndexOutOfBoundsException: Index -1 out of bounds for length 10", legacy, s.name());
            assertEquals(legacy, RandomIsomerOracleTest.portRings(s.port()), s.name());
        }
        // the finder reached through SetOfRingsFinder directly, as FeatureJudge does
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> new SetOfRingsFinder().findSSSR(l.get(0).legacy()));
    }
}
