package io.github.steinbeck.seneca.verify;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Vector;

import compchem.Atom;
import compchem.Molecule;

/**
 * Drives the 2001 engine ({@code legacy/runnable/classes} + compchem.jar). Loaded through
 * {@link #loader()}, a child-first class loader that removes only the {@code Thread.yield()} calls of
 * {@code StochasticGenerator} and {@code SingleRandomStructureGenerator} (no effect on results,
 * DEVIATIONS.md DV-27; on a loaded machine they slow the legacy engine down by orders of magnitude).
 * Everything else is the original bytecode. Input/output are JDK types only.
 */
public final class LegacyDriver {

    private LegacyDriver() {
    }

    private static volatile ClassLoader loader;

    static ClassLoader loader() {
        if (loader == null) {
            synchronized (LegacyDriver.class) {
                if (loader == null) loader = new YieldFreeLoader(LegacyDriver.class.getClassLoader());
            }
        }
        return loader;
    }

    /** Calls {@link #run(Map)} inside the yield-free loader. */
    @SuppressWarnings("unchecked")
    static synchronized Map<String, Object> runIsolated(Map<String, Object> in) throws Exception {
        Class<?> c = loader().loadClass(LegacyDriver.class.getName());
        return (Map<String, Object>) c.getMethod("run", Map.class).invoke(null, in);
    }

    static final class YieldFreeLoader extends ClassLoader {
        YieldFreeLoader(ClassLoader parent) {
            super(parent);
        }

        private static boolean mine(String name) {
            return name.startsWith("seneca.") || name.startsWith("compchem.") || name.startsWith(LegacyDriver.class.getName());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!mine(name)) return super.loadClass(name, resolve);
            synchronized (getClassLoadingLock(name)) {
                Class<?> c = findLoadedClass(name);
                if (c == null) {
                    String res = name.replace('.', '/') + ".class";
                    byte[] b;
                    try (InputStream is = getParent().getResourceAsStream(res)) {
                        if (is == null) throw new ClassNotFoundException(name);
                        b = is.readAllBytes();
                    } catch (java.io.IOException e) {
                        throw new ClassNotFoundException(name, e);
                    }
                    if (name.equals("seneca.structgen.StochasticGenerator") || name.equals("seneca.structgen.SingleRandomStructureGenerator")) {
                        b = stripYield(b);
                    }
                    c = defineClass(name, b, 0, b.length);
                }
                if (resolve) resolveClass(c);
                return c;
            }
        }

        static byte[] stripYield(byte[] bytes) {
            ClassFile cf = ClassFile.of();
            CodeTransform ct = (builder, element) -> {
                if (element instanceof InvokeInstruction i && i.opcode() == Opcode.INVOKESTATIC
                        && i.name().equalsString("yield") && i.type().equalsString("()V")) {
                    return; // drop
                }
                builder.with(element);
            };
            return cf.transformClass(cf.parse(bytes), ClassTransform.transformingMethodBodies(ct));
        }
    }

    // ------------------------------------------------------------------ inside the loader

    /** Legacy generator with the harness's iteration-cap hook (GoldenMaster.CappedGenerator). */
    public static final class LegacyCapped extends seneca.structgen.StochasticGenerator {
        final long cap;
        boolean capped;

        LegacyCapped(long cap) {
            super(true);
            this.cap = cap;
        }

        @Override
        protected void mutate(int[][] bm) {
            if (cap > 0 && !capped && iteration >= cap) {
                capped = true;
                end();
            }
            super.mutate(bm);
        }

        long iterations() {
            return iteration;
        }
    }

    public static final class CountingRandom extends Random {
        private static final long serialVersionUID = 1L;
        long draws;

        CountingRandom(long seed) {
            super(seed);
        }

        @Override
        public double nextDouble() {
            draws++;
            return super.nextDouble();
        }
    }

    static Object serClone(Object o) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(buf)) {
            oos.writeObject(o);
        }
        return new ObjectInputStream(new ByteArrayInputStream(buf.toByteArray())).readObject();
    }

    static Object get(Object o, String field) throws Exception {
        Class<?> c = o.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(field);
                f.setAccessible(true);
                return f.get(o);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(field);
    }

    static Vector<Object> judges(Map<String, Object> s) throws Exception {
        String[] sym = (String[]) s.get("sym");
        int n = sym.length;
        seneca.judges.HHCOSYJudge cosy = new seneca.judges.HHCOSYJudge();
        seneca.judges.HMBCJudge hmbc = new seneca.judges.HMBCJudge();
        seneca.judges.HeteroBondsJudge het = new seneca.judges.HeteroBondsJudge();
        seneca.judges.SymmetryJudge symj = new seneca.judges.SymmetryJudge();
        seneca.judges.HybridizationJudge hyb = new seneca.judges.HybridizationJudge();
        seneca.judges.HOSECodeJudge hose = new seneca.judges.HOSECodeJudge();
        seneca.judges.FeatureJudge feat = new seneca.judges.FeatureJudge();
        int[][] cosyPairs = (int[][]) s.get("cosyPairs");
        if (cosyPairs != null) {
            cosy.assignment = new boolean[n][n][n];
            for (int[] p : cosyPairs) cosy.assignment[p[0]][p[1]][p[1]] = true;
            cosy.setEnabled(true);
        }
        int[][] hmbcPairs = (int[][]) s.get("hmbcPairs");
        if (hmbcPairs != null) {
            hmbc.assignment = new boolean[n][n][n];
            for (int[] p : hmbcPairs) hmbc.assignment[p[0]][p[1]][p[1]] = true;
            int[] sc = (int[]) s.get("hmbcScores");
            if (sc != null) hmbc.setScores(sc.clone());
            hmbc.setEnabled(true);
        }
        if ((Boolean) s.get("heteroBonds")) {
            het.isHeteroBonded = new boolean[n];
            for (int i = 0; i < n; i++) het.isHeteroBonded[i] = sym[i].equals("C");
            het.setEnabled(true);
        }
        boolean[][] classes = (boolean[][]) s.get("symmetryClasses");
        if (classes != null) {
            for (boolean[] c : classes) symj.addSymmetryClass(c.clone());
            symj.setScore((Integer) s.get("symmetryScore"));
            symj.setEnabled(true);
        }
        if ((Boolean) s.get("hybridization")) {
            hyb.initScoreArray(n);
            for (int i = 0; i < n; i++) if (sym[i].equals("C")) hyb.addHybridizationScore(i + 1, seneca.judges.HybridizationJudge.SP3, 1.0);
            hyb.setEnabled(true);
        }
        double[] shifts = (double[]) s.get("hoseShifts");
        if (shifts != null) {
            hose.setCarbonShifts(shifts.clone());
            hose.setEnabled(true);
        }
        if ((Boolean) s.get("featureEnabled")) {
            int[][] rs = (int[][]) s.get("ringScores");
            if (rs != null) for (int[] r : rs) feat.addRingSizeScore(r[0], r[1]);
            feat.setHetHetPenaltyEnabled((Boolean) s.get("hetHet"));
            feat.setHetHetPenalty((Integer) s.get("hetHetPenalty"));
            feat.setEnabled(true);
        }
        Vector<Object> v = new Vector<>();
        v.add(cosy);
        v.add(hmbc);
        v.add(het);
        v.add(symj);
        v.add(hyb);
        v.add(hose);
        v.add(feat);
        for (Object o : v) {
            seneca.judges.Judge j = (seneca.judges.Judge) o;
            if (j.getEnabled()) j.calcMaxScore();
        }
        @SuppressWarnings("unchecked")
        Vector<Object> copy = (Vector<Object>) serClone(v);
        return copy;
    }

    /** Entry point inside the loader. */
    public static Map<String, Object> run(Map<String, Object> s) throws Exception {
        String[] sym = (String[]) s.get("sym");
        int[] h = (int[]) s.get("h");
        compchem.StandardElements se = new compchem.StandardElements();
        Atom[] atoms = new Atom[sym.length];
        for (int i = 0; i < atoms.length; i++) {
            atoms[i] = new Atom(sym[i], i);
            atoms[i].HCount = h[i];
            atoms[i].maxBondOrder = se.getMaxBondOrder(sym[i]);
            atoms[i].maxBondOrderSum = se.getMaxBondOrderSum(sym[i]);
        }
        LegacyCapped sg = new LegacyCapped((Long) s.get("cap"));
        sg.setMolecule((Molecule) serClone(new Molecule(atoms)));
        sg.setJudges(judges(s));
        sg.setDatasetName((String) s.get("name"));
        Vector<Object> sched = new Vector<>();
        for (double[] d : (double[][]) s.get("schedules")) {
            seneca.structgen.AnnealingSchedule a = new seneca.structgen.AnnealingSchedule();
            a.initialAcceptanceProbability = d[0];
            a.maxUphillSteps = (long) d[1];
            a.plateauSteps = (long) d[2];
            a.asymptoticCoolingFactor = d[3];
            sched.add(a);
        }
        sg.setSASchedules(sched);
        long seed = (Long) s.get("seed");
        seneca.structgen.Rng.seed(seed);
        CountingRandom rng = new CountingRandom(seed);
        Field rf = seneca.structgen.Rng.class.getDeclaredField("random");
        rf.setAccessible(true);
        rf.set(null, rng);
        PrintStream err = System.err;
        PrintStream out = System.out;
        try {
            System.setErr(new PrintStream(new ByteArrayOutputStream()));
            System.setOut(new PrintStream(new ByteArrayOutputStream()));
            if (Boolean.TRUE.equals(s.get("stopBeforeRun"))) sg.end();
            sg.run();
        } finally {
            System.setErr(err);
            System.setOut(out);
        }
        seneca.structgen.StructureGeneratorResult r = (seneca.structgen.StructureGeneratorResult) sg.getResult();
        List<String> st = new ArrayList<>();
        List<Long> sc = new ArrayList<>();
        List<String> de = new ArrayList<>();
        for (int i = 0; i < r.size(); i++) {
            st.add((String) r.structures.elementAt(i));
            seneca.judges.ScoreSummary ss = (seneca.judges.ScoreSummary) r.scoreSummaries.elementAt(i);
            sc.add(ss.score);
            de.add(ss.description);
        }
        Map<String, Object> o = new HashMap<>();
        o.put("iterations", sg.iterations());
        o.put("draws", rng.draws);
        o.put("maxScore", get(sg, "maxScore"));
        o.put("startKT", get(sg, "start_kT"));
        o.put("finalKT", get(sg, "current_kT"));
        o.put("plateau", get(sg, "plateauSteps"));
        o.put("uphill", get(sg, "maxUphillSteps"));
        o.put("structures", st);
        o.put("scores", sc);
        o.put("descriptions", de);
        o.put("exception", sg.running ? "exception_in_run" : null);
        return o;
    }
}
