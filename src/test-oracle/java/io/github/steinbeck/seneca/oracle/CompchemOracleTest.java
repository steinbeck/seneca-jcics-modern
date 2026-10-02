package io.github.steinbeck.seneca.oracle;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;

import compchem.Atom;
import compchem.ChemFileUtils;
import compchem.DataStructureTools;
import compchem.HOSECodeGenerator;
import compchem.Ring;
import compchem.SetOfRingsFinder;
import compchem.StandardElements;
import compchem.setOfRings;
import io.github.steinbeck.seneca.chem.AtomSets;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.chem.HoseCodeGenerator;
import io.github.steinbeck.seneca.chem.IsomorphismTester;
import io.github.steinbeck.seneca.chem.LegacyMdlWriter;
import io.github.steinbeck.seneca.chem.PathUtils;
import io.github.steinbeck.seneca.chem.RingFinder;
import io.github.steinbeck.seneca.golden.EchoGolden;
import io.github.steinbeck.seneca.golden.GoldenFiles;
import io.github.steinbeck.seneca.golden.GoldenFixtures;
import io.github.steinbeck.seneca.golden.Hashes;
import io.github.steinbeck.seneca.golden.JsonValue.JObject;
import io.github.steinbeck.seneca.golden.Port;
import io.github.steinbeck.seneca.golden.RunGolden;
import io.github.steinbeck.seneca.golden.ScoresGolden;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Differential tests: the port's re-implementations of compchem against the original
 * {@code legacy/original/jar/compchem.jar}, on identical inputs (the 550 golden score structures and
 * the 13 golden crash candidates), requiring 0 mismatches (docs/legacy/compchem-semantics.md 14 is
 * the template). Only compiled and run with {@code mvn -Poracle test} (work package A extends it:
 * HOSE codes, Bremser, ring finder, isomorphism tester, MDL writer/reader, formula parser).
 */
@Tag("A")
class CompchemOracleTest {

    /** One structure in both representations, bonds synchronized from the same matrix. */
    record Pair(String name, Atom[] legacy, HeavyAtom[] port, int[][] bm) {
    }

    static Atom[] legacyAtoms(HeavyAtom[] atoms, int[][] bm) {
        StandardElements se = new StandardElements();
        Atom[] a = new Atom[atoms.length];
        for (int i = 0; i < a.length; i++) {
            a[i] = new Atom(atoms[i].symbol, i);
            a[i].HCount = atoms[i].hCount;
            a[i].maxBondOrder = se.getMaxBondOrder(atoms[i].symbol);
            a[i].maxBondOrderSum = se.getMaxBondOrderSum(atoms[i].symbol);
        }
        DataStructureTools.synchronizeBonds(a, bm);
        return a;
    }

    static HeavyAtom[] portAtoms(HeavyAtom[] atoms, int[][] bm) {
        HeavyAtom[] p = AtomSets.cloneAtomSet(atoms);
        AtomSets.synchronizeBonds(p, bm);
        return p;
    }

    /** The 50 golden score structures of every dataset. */
    static List<Pair> scoreStructures() {
        List<Pair> l = new ArrayList<>();
        for (String key : GoldenFiles.keys()) {
            HeavyAtom[] atoms = GoldenFixtures.atoms(EchoGolden.load(key));
            for (JObject s : ScoresGolden.load(key).structures()) {
                int[][] bm = Hashes.matrixFromCt(atoms.length, s.arr("ct").intRows());
                l.add(new Pair(key + " k=" + s.integer("k"), legacyAtoms(atoms, bm), portAtoms(atoms, bm), bm));
            }
        }
        return l;
    }

    /** The candidates on which the 2001 ring finder crashed in the golden runs. */
    static List<Pair> crashCandidates() {
        List<Pair> l = new ArrayList<>();
        for (GoldenFiles.RunId id : GoldenFiles.runs()) {
            RunGolden g = RunGolden.load(id);
            if (!g.record.has("crash")) continue;
            HeavyAtom[] atoms = GoldenFixtures.atoms(EchoGolden.load(id.key()));
            int[][] bm = Hashes.matrixFromCt(atoms.length, g.record.obj("crash").arr("candidateCt").intRows());
            l.add(new Pair(id.toString(), legacyAtoms(atoms, bm), portAtoms(atoms, bm), bm));
        }
        return l;
    }

    static List<DynamicTest> each(List<Pair> pairs, BiConsumer<Pair, String> check) {
        return pairs.stream().map(p -> DynamicTest.dynamicTest(p.name(), () -> check.accept(p, p.name()))).toList();
    }

    @TestFactory
    List<DynamicTest> morganNumbers() {
        return each(scoreStructures(), (p, n) ->
                assertArrayEquals(DataStructureTools.computeMorganMatrix(p.legacy()), AtomSets.computeMorganMatrix(p.port()), n));
    }

    @TestFactory
    List<DynamicTest> pathsAndConnectivity() {
        return each(scoreStructures(), (p, n) -> {
            assertEquals(compchem.PathUtils.isConnected(p.legacy()), PathUtils.isConnected(p.port()), n + " isConnected");
            for (int cutoff = 1; cutoff <= 3; cutoff++)
                for (int i = 0; i < p.port().length; i++)
                    for (int j = 0; j < p.port().length; j++)
                        assertEquals(compchem.PathUtils.getPathLength(p.legacy(), p.legacy()[i], p.legacy()[j], cutoff),
                                PathUtils.getPathLength(p.port(), p.port()[i], p.port()[j], cutoff), n + " path " + i + "-" + j + " cutoff " + cutoff);
        });
    }

    @TestFactory
    List<DynamicTest> hoseCodes() {
        return each(scoreStructures(), (p, n) -> {
            HOSECodeGenerator lg = new HOSECodeGenerator();
            HoseCodeGenerator pg = new HoseCodeGenerator();
            for (int i = 0; i < p.port().length; i++) {
                int ii = i;
                assertEquals(lg.getHOSECode(p.legacy(), p.legacy()[i], 1), Port.call(() -> pg.getHOSECode(p.port(), p.port()[ii], 1)), n + " atom " + i);
            }
        });
    }

    /** Ring sizes in discovery order, or the exception "class: message". */
    static String legacyRings(Atom[] atoms) {
        try {
            setOfRings s = new SetOfRingsFinder().findSSSR(atoms);
            List<Integer> sizes = new ArrayList<>();
            for (int i = 0; i < s.size(); i++) sizes.add(((Ring) s.elementAt(i)).size());
            return sizes.toString();
        } catch (RuntimeException e) {
            return e.getClass().getName() + ": " + e.getMessage();
        }
    }

    static String portRings(HeavyAtom[] atoms) {
        try {
            List<Integer> sizes = new ArrayList<>();
            for (var r : new RingFinder().findSSSR(atoms)) sizes.add(r.size());
            return sizes.toString();
        } catch (RuntimeException e) {
            return e.getClass().getName() + ": " + e.getMessage();
        }
    }

    @TestFactory
    List<DynamicTest> ringFinder() {
        return each(scoreStructures(), (p, n) -> assertEquals(legacyRings(p.legacy()), portRings(p.port()), n));
    }

    @TestFactory
    List<DynamicTest> ringFinderCrashCandidates() {
        return each(crashCandidates(), (p, n) -> assertEquals(legacyRings(p.legacy()), portRings(p.port()), n));
    }

    @TestFactory
    List<DynamicTest> isomorphismTester() {
        List<Pair> s = scoreStructures();
        List<DynamicTest> t = new ArrayList<>();
        for (int i = 0; i + 1 < s.size(); i++) {
            Pair a = s.get(i), b = s.get(i + 1);
            if (a.port().length != b.port().length) continue;
            t.add(DynamicTest.dynamicTest(a.name() + " vs " + b.name(), () -> {
                assertEquals(new compchem.IsomorphismTester(a.legacy()).isIsomorph(b.legacy()),
                        (boolean) Port.call(() -> new IsomorphismTester(a.port()).isIsomorph(b.port())));
                assertEquals(new compchem.IsomorphismTester(a.legacy()).isIsomorph(a.legacy()),
                        (boolean) Port.call(() -> new IsomorphismTester(a.port()).isIsomorph(a.port())));
            }));
        }
        return t;
    }

    @TestFactory
    List<DynamicTest> mdlWriter() {
        return each(scoreStructures(), (p, n) -> {
            StringWriter w = new StringWriter();
            ChemFileUtils.writeMDLMol(w, "Score: 1234", p.legacy());
            assertEquals(w.toString(), Port.call(() -> LegacyMdlWriter.write("Score: 1234", p.port())), n);
        });
    }
}
