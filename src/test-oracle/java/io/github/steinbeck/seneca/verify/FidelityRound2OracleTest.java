package io.github.steinbeck.seneca.verify;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.chem.AtomSets;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.chem.StandardElements;
import io.github.steinbeck.seneca.io.SpecMLReader;
import io.github.steinbeck.seneca.judges.Attic;
import io.github.steinbeck.seneca.judges.ScoreSummary;
import io.github.steinbeck.seneca.model.AnnealingSchedule;
import io.github.steinbeck.seneca.structgen.RunInput;
import io.github.steinbeck.seneca.structgen.RunRandom;
import io.github.steinbeck.seneca.structgen.SingleRandomStructureGenerator;
import io.github.steinbeck.seneca.structgen.StochasticGenerator;
import io.github.steinbeck.seneca.structgen.StructureGeneratorResult;
import io.github.steinbeck.seneca.verify.EngineDiffSupport.Outcome;
import io.github.steinbeck.seneca.verify.EngineDiffSupport.Spec;

/**
 * Verifier "fidelity", round 2: differential checks against the 2001 classes on inputs that neither the
 * golden master nor round 1 exercise.
 */
@Tag("verify")
class FidelityRound2OracleTest {

    @TempDir
    Path dir;

    // ------------------------------------------------------------------ 1. legacy hang on star molecules

    /**
     * Legacy side of {@code FidelityRound2VerifyTest}: the 2001 engine never leaves {@code mutate} for
     * isobutane, neopentane, tert-butanol and CCl4 (their bond-order sums have exactly one realisation,
     * so there is no Faulon move). The legacy random source is replaced by one that throws after 2,000,000
     * draws; the throw ends the run through the outer {@code catch (Exception)} with iteration 0.
     */
    @Test
    void legacyNeverLeavesMutateOnStarMolecules() throws Exception {
        String[][] syms = {{"C", "C", "C", "C"}, {"C", "C", "C", "C", "C"}, {"C", "C", "C", "C", "O"}, {"C", "Cl", "Cl", "Cl", "Cl"}};
        int[][] hs = {{1, 3, 3, 3}, {0, 3, 3, 3, 3}, {0, 3, 3, 3, 1}, {0, 0, 0, 0, 0}};
        for (int k = 0; k < syms.length; k++) {
            Class<?> c = isolatedLoader().loadClass(LegacyHang.class.getName());
            long[] r = (long[]) c.getMethod("run", String[].class, int[].class).invoke(null, syms[k], hs[k]);
            System.out.println("[verify] legacy " + String.join("", syms[k]) + ": draws=" + r[0] + " iteration=" + r[1]
                    + " results=" + r[2]);
            assertEquals(2_000_000L, r[0], "legacy consumed the draw limit (it never stops drawing)");
            assertEquals(0L, r[1], "no main-loop iteration");
        }
    }

    /** Fresh child-first loader (own legacy statics, yields stripped). */
    static ClassLoader isolatedLoader() {
        ClassLoader parent = FidelityRound2OracleTest.class.getClassLoader();
        return new ClassLoader(parent) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                boolean mine = name.startsWith("seneca.") || name.startsWith("compchem.") || name.startsWith(LegacyHang.class.getName());
                if (!mine) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> cl = findLoadedClass(name);
                    if (cl == null) {
                        byte[] b;
                        try (InputStream is = parent.getResourceAsStream(name.replace('.', '/') + ".class")) {
                            if (is == null) throw new ClassNotFoundException(name);
                            b = is.readAllBytes();
                        } catch (java.io.IOException e) {
                            throw new ClassNotFoundException(name, e);
                        }
                        if (name.equals("seneca.structgen.StochasticGenerator") || name.equals("seneca.structgen.SingleRandomStructureGenerator")) {
                            b = LegacyDriver.YieldFreeLoader.stripYield(b);
                        }
                        cl = defineClass(name, b, 0, b.length);
                    }
                    if (resolve) resolveClass(cl);
                    return cl;
                }
            }
        };
    }

    /** Runs inside {@link #isolatedLoader()}. */
    public static final class LegacyHang {
        public static final class LimitedRandom extends Random {
            private static final long serialVersionUID = 1L;
            long draws;

            LimitedRandom() {
                super(1);
            }

            @Override
            public double nextDouble() {
                if (++draws >= 2_000_000L) throw new IllegalStateException("draw limit");
                return super.nextDouble();
            }
        }

        public static long[] run(String[] sym, int[] h) throws Exception {
            compchem.StandardElements se = new compchem.StandardElements();
            compchem.Atom[] atoms = new compchem.Atom[sym.length];
            for (int i = 0; i < atoms.length; i++) {
                atoms[i] = new compchem.Atom(sym[i], i);
                atoms[i].HCount = h[i];
                atoms[i].maxBondOrder = se.getMaxBondOrder(sym[i]);
                atoms[i].maxBondOrderSum = se.getMaxBondOrderSum(sym[i]);
            }
            seneca.structgen.StochasticGenerator sg = new seneca.structgen.StochasticGenerator(true);
            sg.setMolecule(new compchem.Molecule(atoms));
            java.util.Vector<Object> judges = new java.util.Vector<>();
            seneca.judges.FeatureJudge f = new seneca.judges.FeatureJudge();
            f.setHetHetPenaltyEnabled(true);
            f.setEnabled(true);
            f.calcMaxScore();
            judges.add(f);
            sg.setJudges(judges);
            java.util.Vector<Object> sched = new java.util.Vector<>();
            sched.add(new seneca.structgen.AnnealingSchedule());
            sg.setSASchedules(sched);
            LimitedRandom rnd = new LimitedRandom();
            Field rf = seneca.structgen.Rng.class.getDeclaredField("random");
            rf.setAccessible(true);
            rf.set(null, rnd);
            PrintStream err = System.err;
            try {
                System.setErr(new PrintStream(new ByteArrayOutputStream()));
                sg.run();
            } finally {
                System.setErr(err);
            }
            Field it = seneca.structgen.StochasticGenerator.class.getDeclaredField("iteration");
            it.setAccessible(true);
            seneca.structgen.StructureGeneratorResult res = (seneca.structgen.StructureGeneratorResult) sg.getResult();
            return new long[] {rnd.draws, (Long) it.get(sg), res.size()};
        }
    }

    // ------------------------------------------------------------------ 2. random molecules, all judges

    static final String[] ELEMENTS = {"C", "C", "C", "C", "C", "C", "C", "C", "C", "C", "C", "C", "O", "O", "O", "N", "N", "S",
            "Cl", "F", "Br", "P", "Si", "I"};

    /** A random saturated connected molecule with random judge configuration; null if unusable. */
    static Spec randomSpec(long seed) {
        Random r = new Random(seed);
        int n = 6 + r.nextInt(15);
        String[] sym = new String[n];
        int[] val = new int[n], mbo = new int[n];
        // the first atom must be able to take several bonds
        for (int i = 0; i < n; i++) {
            sym[i] = i == 0 ? "C" : ELEMENTS[r.nextInt(ELEMENTS.length)];
            val[i] = StandardElements.getMaxBondOrderSum(sym[i]);
            mbo[i] = StandardElements.getMaxBondOrder(sym[i]);
        }
        int[][] bm = new int[n][n];
        int[] used = new int[n];
        for (int i = 1; i < n; i++) {
            List<Integer> parents = new ArrayList<>();
            for (int p = 0; p < i; p++) if (val[p] - used[p] >= 1 && (val[p] > 1 || used[p] == 0)) parents.add(p);
            // valence-1 atoms that are already bonded cannot be parents
            parents.removeIf(p -> val[p] == 1 && used[p] > 0);
            if (parents.isEmpty()) return null;
            int p = parents.get(r.nextInt(parents.size()));
            bm[i][p] = bm[p][i] = 1;
            used[i]++;
            used[p]++;
        }
        int extra = r.nextInt(n + 4);
        for (int t = 0; t < extra * 4 && extra > 0; t++) {
            int i = r.nextInt(n), j = r.nextInt(n);
            if (i == j || used[i] >= val[i] || used[j] >= val[j]) continue;
            if (bm[i][j] >= Math.min(mbo[i], mbo[j])) continue;
            bm[i][j]++;
            bm[j][i]++;
            used[i]++;
            used[j]++;
            extra--;
        }
        int[] h = new int[n];
        for (int i = 0; i < n; i++) {
            h[i] = val[i] - used[i];
            if (h[i] < 0) return null;
        }
        Spec s = new Spec();
        s.name = "random#" + seed + " " + String.join("", sym);
        s.sym = sym;
        s.h = h;
        s.seed = 1 + r.nextInt(1000);
        if (r.nextBoolean()) {
            s.hoseShifts = new double[n];
            for (int i = 0; i < n; i++) s.hoseShifts[i] = Math.round(r.nextDouble() * 2000) / 10.0;
        }
        if (r.nextInt(4) != 0) {
            s.featureEnabled = true;
            if (r.nextInt(3) != 0) {
                List<int[]> rs = new ArrayList<>();
                for (int size = 3; size <= 8; size++) if (r.nextInt(3) == 0) rs.add(new int[] {size, r.nextInt(1001) - 500});
                if (rs.isEmpty()) rs.add(new int[] {6, 100});
                s.ringScores = rs.toArray(new int[0][]);
            }
            s.hetHet = r.nextBoolean();
            s.hetHetPenalty = -1 - r.nextInt(10);
        }
        if (r.nextBoolean()) {
            List<int[]> p = new ArrayList<>();
            for (int k = 0; k < 2 + r.nextInt(5); k++) p.add(new int[] {r.nextInt(n), r.nextInt(n)});
            s.hmbcPairs = p.toArray(new int[0][]);
            if (r.nextBoolean()) {
                s.hmbcScores = new int[7];
                for (int k = 0; k < 7; k++) s.hmbcScores[k] = r.nextInt(3) == 0 ? 0 : r.nextInt(150);
            }
        }
        if (r.nextBoolean()) {
            List<int[]> p = new ArrayList<>();
            for (int k = 0; k < 1 + r.nextInt(4); k++) p.add(new int[] {r.nextInt(n), r.nextInt(n)});
            s.cosyPairs = p.toArray(new int[0][]);
        }
        if (r.nextInt(3) == 0) {
            boolean[][] cls = new boolean[1 + r.nextInt(2)][n];
            for (boolean[] c : cls) for (int k = 0; k < n; k++) c[k] = r.nextInt(3) == 0;
            s.symmetryClasses = cls;
            s.symmetryScore = r.nextBoolean() ? 500 : 1 + r.nextInt(400);
        }
        s.hybridization = r.nextInt(4) == 0;
        s.heteroBonds = r.nextInt(4) == 0;
        int ns = 1 + r.nextInt(3);
        for (int k = 0; k < ns; k++) {
            s.schedules.add(new double[] {0.3 + r.nextDouble() * 0.65, 1 + r.nextInt(60), 1 + r.nextInt(80), 0.8 + r.nextDouble() * 0.18});
        }
        s.cap = 1000 + r.nextInt(3000);
        return s;
    }

    /** True if the engine can run on this input without the known hangs (start structure has a connected move). */
    static boolean runnable(Spec s) {
        HeavyAtom[] atoms = EngineDiffSupport.portAtoms(s);
        List<AnnealingSchedule> sched = List.of(new AnnealingSchedule());
        if (StochasticGenerator.validate(new RunInput(atoms, List.of(), sched, "x")) != null) return false;
        HeavyAtom[] start = SingleRandomStructureGenerator.generate(atoms, new Attic(), RunRandom.seeded(s.seed));
        int[][] bm = AtomSets.createConnectionTable(start);
        AtomSets.synchronizeConnectionTable(bm);
        return FidelityRound2VerifyTest.neighbours(bm, start)[1] > 0;
    }

    @TestFactory
    List<DynamicTest> randomMoleculesLegacyVsPort() {
        List<DynamicTest> tests = new ArrayList<>();
        int count = Integer.getInteger("verify.random.count", 40);
        long seed = 1000;
        while (tests.size() < count) {
            Spec s = randomSpec(seed++);
            if (s == null || !runnable(s)) continue;
            tests.add(DynamicTest.dynamicTest(s.name, () -> {
                Outcome legacy = withTimeout(s);
                Outcome port = EngineDiffSupport.runPort(s, LegacyCompat.STRICT);
                System.out.println("[verify] " + s.name + "\n  legacy " + legacy.summary() + "\n  port   " + port.summary());
                assertEquals(legacy.exception() != null, port.exception() != null, "exception status");
                assertEquals(legacy.iterations(), port.iterations(), "iterations");
                assertEquals(legacy.draws(), port.draws(), "random draws");
                assertEquals(legacy.maxScore(), port.maxScore(), "maxScore");
                assertEquals(Double.doubleToRawLongBits(legacy.startKT()), Double.doubleToRawLongBits(port.startKT()), "start_kT");
                assertEquals(Double.doubleToRawLongBits(legacy.finalKT()), Double.doubleToRawLongBits(port.finalKT()), "current_kT");
                assertEquals(legacy.plateau(), port.plateau(), "plateauSteps");
                assertEquals(legacy.uphill(), port.uphill(), "maxUphillSteps");
                assertEquals(legacy.scores(), port.scores(), "result scores");
                assertEquals(legacy.structures(), port.structures(), "result MDL strings");
                assertEquals(legacy.descriptions(), port.descriptions(), "result descriptions");
            }));
        }
        return tests;
    }

    private static Outcome withTimeout(Spec s) throws Exception {
        ExecutorService ex = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "legacy-engine");
            t.setDaemon(true);
            return t;
        });
        try {
            Future<Outcome> f = ex.submit(() -> EngineDiffSupport.runLegacy(s));
            try {
                return f.get(300, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                throw new AssertionError("legacy engine did not finish within 300 s (hang?) for " + s.name);
            }
        } finally {
            ex.shutdownNow();
        }
    }

    // ------------------------------------------------------------------ 3. removeIsomorphism with one structure

    static final String MDL = """
            Score: 10



              2  1  0  0  0  0  0  0  0  0  1 V2000
                0.0000    0.0000    0.0000 C   0  0  0  0  0  0  0  0  0  0  0  0
                0.0000    0.0000    0.0000 C   0  0  0  0  0  0  0  0  0  0  0  0
              1  2  1  0  0  0  0
            M  END
            """;

    @Test
    void removeIsomorphismWithOneStructureSameException() {
        for (int capacity : new int[] {30, 60, 90}) {
            seneca.structgen.StructureGeneratorResult legacy = new seneca.structgen.StructureGeneratorResult(capacity);
            legacy.structures.push(MDL);
            legacy.scoreSummaries.push(new seneca.judges.ScoreSummary(10, ""));
            String le;
            PrintStream out = System.out;
            try {
                System.setOut(new PrintStream(new ByteArrayOutputStream()));
                legacy.removeIsomorphism();
                le = "returned";
            } catch (RuntimeException e) {
                le = e.toString();
            } finally {
                System.setOut(out);
            }
            StructureGeneratorResult port = new StructureGeneratorResult(capacity);
            port.push(MDL, new ScoreSummary(10, ""));
            String pe;
            try {
                port.removeIsomorphism(LegacyCompat.STRICT);
                pe = "returned";
            } catch (RuntimeException e) {
                pe = e.toString();
            }
            System.out.println("[verify] removeIsomorphism, 1 structure, capacity " + capacity + ": legacy " + le + " | port " + pe);
            assertEquals(le, pe, "exception of Summarize Result with a single structure (capacity " + capacity + ")");
        }
    }

    // ------------------------------------------------------------------ 4. "Summarize Result" drains a local run

    /**
     * Legacy {@code StochasticGenerator.getResult()} returns {@code structureGeneratorResult.clone()}, a
     * shallow {@code Object.clone()}: the queues are shared, and Summarize's {@code pop()} on a local server
     * empties the running generator's own result list (D6). The generator's duplicate test
     * ({@code notInStock}) then no longer sees the earlier structures and stores them again, and a second
     * Summarize finds nothing. The port copies in both modes (DV-22), i.e. STRICT does not reproduce this.
     */
    @Test
    void legacyGetResultIsShallowAndSummarizeDrainsTheRun() throws Exception {
        seneca.structgen.StochasticGenerator sg = new seneca.structgen.StochasticGenerator(true);
        Field f = seneca.structgen.StochasticGenerator.class.getDeclaredField("structureGeneratorResult");
        f.setAccessible(true);
        seneca.structgen.StructureGeneratorResult live = (seneca.structgen.StructureGeneratorResult) f.get(sg);
        live.structures.push(MDL);
        live.scoreSummaries.push(new seneca.judges.ScoreSummary(10, ""));
        seneca.structgen.StructureGeneratorResult got = (seneca.structgen.StructureGeneratorResult) sg.getResult();
        got.structures.pop();          // what ShowCurrentAction does for every entry
        got.scoreSummaries.pop();
        System.out.println("[verify] legacy live result size after Summarize popped the copy: " + live.size());
        assertEquals(0, live.size(), "legacy: Summarize drained the live run's result list");
        // the port: summarize never changes the run, in both modes (documented policy exception, DV-22:
        // the in-process controller has the RMI semantics that remote servers had in 2001)
    }

    // ------------------------------------------------------------------ 5. SpecML reader: elements legacy does not read

    static final String HEAD = """
            <?xml version="1.0" ?>
            <senecadataset title="t">
              <formula>C4H10</formula>
            """;

    static seneca.core.SenecaDataset legacyRead(Path file) throws Exception {
        Field docStart = seneca.core.SpecMLReader.class.getDeclaredField("docStart");
        docStart.setAccessible(true);
        docStart.setBoolean(null, false);
        Field startNode = seneca.core.SpecMLReader.class.getDeclaredField("startNode");
        startNode.setAccessible(true);
        startNode.set(null, null);
        return new seneca.core.SpecMLReader(file.toFile()).getSenecaDataset();
    }

    static String outcome(Path f, boolean legacy) {
        try {
            if (legacy) {
                seneca.core.SenecaDataset sd = legacyRead(f);
                return sd == null ? "null" : "read " + sd.getMolecularFormula() + " mass=" + sd.getMolecularMass() + " desc=" + sd.getDescription();
            }
            var sd = SpecMLReader.read(f);
            return "read " + sd.getMolecularFormula() + " mass=" + sd.getMolecularMass() + " desc=" + sd.getDescription();
        } catch (Exception e) {
            return "fails (" + e.getClass().getSimpleName() + ")";
        }
    }

    @TestFactory
    List<DynamicTest> specMlElementsTheLegacyReaderIgnoresOrRejects() {
        Map<String, String> docs = Map.of(
                "molecularmass not a number", HEAD + "  <molecularmass>136,2</molecularmass>\n</senecadataset>\n",
                "empty molecularmass", HEAD + "  <molecularmass/>\n</senecadataset>\n",
                "serverinfo with non-numeric speed", HEAD + "  <serverinfo><hostname>h</hostname><speed>fast</speed></serverinfo>\n</senecadataset>\n",
                "description element", HEAD + "  <description>from a newer program</description>\n</senecadataset>\n");
        List<DynamicTest> t = new ArrayList<>();
        for (var e : docs.entrySet()) {
            t.add(DynamicTest.dynamicTest(e.getKey(), () -> {
                Path f = dir.resolve(e.getKey().replace(' ', '_') + ".sml");
                Files.writeString(f, e.getValue(), StandardCharsets.UTF_8);
                PrintStream err = System.err;
                String l;
                try {
                    System.setErr(new PrintStream(new ByteArrayOutputStream()));
                    l = outcome(f, true);
                } finally {
                    System.setErr(err);
                }
                String p = outcome(f, false);
                System.out.println("[verify] SpecML " + e.getKey() + ": legacy " + l + " | port " + p);
                if (e.getKey().startsWith("serverinfo")) {
                    // documented deviation DV-45 (both modes): the RMI server list is dropped, so a malformed
                    // <serverinfo> block no longer makes the file unreadable
                    assertEquals("fails", l.substring(0, 5), "legacy " + l);
                    assertEquals("read", p.substring(0, 4), "port " + p);
                    return;
                }
                assertEquals(l.startsWith("read"), p.startsWith("read"), "readable in legacy vs port: legacy " + l + ", port " + p);
            }));
        }
        return t;
    }
}
