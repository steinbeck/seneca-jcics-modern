package io.github.steinbeck.seneca.verify;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Vector;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.io.SpecMLReader;
import io.github.steinbeck.seneca.model.AnnealingSchedule;
import io.github.steinbeck.seneca.model.NMRSignal;
import io.github.steinbeck.seneca.model.NMRSpectrum;
import io.github.steinbeck.seneca.model.SenecaDataset;
import io.github.steinbeck.seneca.model.SpectrumSlot;
import io.github.steinbeck.seneca.verify.EngineDiffSupport.Outcome;
import io.github.steinbeck.seneca.verify.EngineDiffSupport.Spec;

/**
 * Verifier "fidelity", round 3: differential checks against the 2001 classes on inputs that neither
 * the golden master nor rounds 1-2 exercise.
 *
 * <p>1. SpecML reader fuzz: every bundled {@code .sml} file is mutated at random on the DOM level
 * (elements removed, duplicated, emptied, re-ordered, moved; texts and {@code role}/{@code builtin}/
 * {@code title}/{@code id} attributes changed) and read by the 2001 reader and by the port. Both must
 * agree on readable/unreadable and, if readable, on every field the program uses afterwards (atoms with
 * symbol, H count, shift; formula; every spectrum slot with type, name, axes, solvent, frequency and
 * signals; annealing schedules).
 */
@Tag("verify")
class FidelityRound3OracleTest {

    @TempDir
    Path dir;

    static final Path SML = Path.of("legacy/original/sml");

    static final java.util.concurrent.atomic.AtomicInteger READ_BOTH = new java.util.concurrent.atomic.AtomicInteger();

    @org.junit.jupiter.api.AfterAll
    static void report() {
        System.out.println("[verify] SpecML mutants readable by both programs: " + READ_BOTH.get());
    }

    // ------------------------------------------------------------------ 1. SpecML reader fuzz

    static String text(Document d) throws Exception {
        Transformer t = TransformerFactory.newInstance().newTransformer();
        t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        StringWriter w = new StringWriter();
        t.transform(new DOMSource(d), new StreamResult(w));
        return w.toString();
    }

    static List<Element> elements(Document d) {
        List<Element> l = new ArrayList<>();
        NodeList all = d.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            Element e = (Element) all.item(i);
            if (e == d.getDocumentElement()) continue;
            if (e.getNodeName().equalsIgnoreCase("serverinfo")) continue;   // DV-45 (documented)
            l.add(e);
        }
        return l;
    }

    static final String[] TEXTS = {"", " ", "abc", "-1", "0", "1.5", " 7 ", "1e2", "NaN", "3", "12", "C", "O", "X", "2.0"};
    static final String[] ROLES = {"f1", "f2", "f3", "f4", "f5", "f6", "F1", "x", ""};
    static final String[] BUILTINS = {"elementType", "hydrogenCount", "ELEMENTTYPE", "foo"};

    static String mutate(Document d, Random r) {
        StringBuilder what = new StringBuilder();
        int k = 1 + r.nextInt(2);
        for (int m = 0; m < k; m++) {
            List<Element> els = elements(d);
            if (els.isEmpty()) break;
            Element e = els.get(r.nextInt(els.size()));
            int op = r.nextInt(9);
            what.append(op).append(':').append(e.getNodeName()).append(' ');
            switch (op) {
                case 0 -> e.getParentNode().removeChild(e);
                case 1 -> e.getParentNode().insertBefore(e.cloneNode(true), e.getNextSibling());
                case 2 -> {
                    String t = TEXTS[r.nextInt(TEXTS.length)];
                    what.append('"').append(t).append("\" ");
                    while (e.getFirstChild() != null) e.removeChild(e.getFirstChild());
                    if (!t.isEmpty()) e.appendChild(d.createTextNode(t));
                }
                case 3 -> {
                    if (e.hasAttribute("role")) e.setAttribute("role", ROLES[r.nextInt(ROLES.length)]);
                    else if (e.hasAttribute("builtin")) e.setAttribute("builtin", BUILTINS[r.nextInt(BUILTINS.length)]);
                    else if (e.hasAttribute("title")) e.setAttribute("title", r.nextBoolean() ? "assignedCarbonShift" : "x");
                    else if (e.hasAttribute("id")) e.removeAttribute("id");
                }
                case 4 -> {
                    while (e.getFirstChild() != null) e.removeChild(e.getFirstChild());
                }
                case 5 -> {
                    Node n = e.getNextSibling();
                    while (n != null && n.getNodeType() != Node.ELEMENT_NODE) n = n.getNextSibling();
                    if (n != null) e.getParentNode().insertBefore(n, e);
                }
                case 6 -> {
                    Element target = els.get(r.nextInt(els.size()));
                    boolean ancestor = false;
                    for (Node p = target; p != null; p = p.getParentNode()) if (p == e) ancestor = true;
                    if (!ancestor) target.appendChild(e);
                }
                case 7 -> {
                    // rename to the other case (legacy lower-cases element names)
                    String n = e.getNodeName();
                    d.renameNode(e, null, n.equals(n.toUpperCase()) ? n.toLowerCase() : n.toUpperCase());
                }
                default -> {
                    // add whitespace-only and comment siblings (text-node handling)
                    e.getParentNode().insertBefore(d.createComment("c"), e);
                    e.insertBefore(d.createTextNode("\n  "), e.getFirstChild());
                }
            }
        }
        return what.toString().trim();
    }

    static seneca.core.SenecaDataset legacyRead(Path file) throws Exception {
        Field docStart = seneca.core.SpecMLReader.class.getDeclaredField("docStart");
        docStart.setAccessible(true);
        docStart.setBoolean(null, false);
        Field startNode = seneca.core.SpecMLReader.class.getDeclaredField("startNode");
        startNode.setAccessible(true);
        startNode.set(null, null);
        return new seneca.core.SpecMLReader(file.toFile()).getSenecaDataset();
    }

    static String fingerprint(seneca.core.SenecaDataset sd) {
        StringBuilder b = new StringBuilder();
        b.append("name=").append(sd.getName()).append(" mf=").append(sd.getMolecularFormula()).append('\n');
        if (sd.setOfNodes == null) b.append("atoms=null\n");
        else for (compchem.Atom a : sd.setOfNodes)
            b.append(a.number).append(' ').append(a.symbol).append(" H").append(a.HCount).append(" s=")
                    .append(a.getPhysicalProperty("13C chemical shift")).append(" mbo=").append(a.maxBondOrder)
                    .append('/').append(a.maxBondOrderSum).append(" name=").append(a.name).append('\n');
        seneca.core.NMRSpectrum[] sp = {sd.carbon1D, sd.dept90, sd.dept135, sd.hetcor, sd.hetcorlr, sd.hhcosy, sd.noesy};
        for (seneca.core.NMRSpectrum s : sp) {
            b.append("spec ").append(s.specType).append(" name=").append(s.name).append(" dim=").append(s.dim)
                    .append(" nuc=").append(java.util.Arrays.toString(s.nucleus)).append(" solv=").append(s.solvent)
                    .append(" freq=").append(s.spectrometerFrequency).append('\n');
            for (int i = 0; i < s.size(); i++) {
                seneca.core.NMRSignal g = (seneca.core.NMRSignal) s.getSignal(i);
                b.append("  sig dim=").append(g.dim).append(" shift=").append(java.util.Arrays.toString(g.shift))
                        .append(" nuc=").append(java.util.Arrays.toString(g.nucleus)).append(" int=").append(g.intensity)
                        .append(" nf=").append(g.normalizeFactor).append(" ph=").append(g.phase).append('\n');
            }
        }
        for (Object o : sd.annealingSchedules) {
            seneca.structgen.AnnealingSchedule a = (seneca.structgen.AnnealingSchedule) o;
            b.append("sched ").append(a.initialAcceptanceProbability).append(' ').append(a.maxUphillSteps).append(' ')
                    .append(a.plateauSteps).append(' ').append(a.asymptoticCoolingFactor).append('\n');
        }
        b.append("judges=").append(sd.judges.size()).append('\n');
        return b.toString();
    }

    static String fingerprint(SenecaDataset sd) {
        StringBuilder b = new StringBuilder();
        b.append("name=").append(sd.getName()).append(" mf=").append(sd.getMolecularFormula()).append('\n');
        if (sd.setOfNodes == null) b.append("atoms=null\n");
        else for (HeavyAtom a : sd.setOfNodes)
            b.append(a.number).append(' ').append(a.symbol).append(" H").append(a.hCount).append(" s=")
                    .append(a.shift13C).append(" mbo=").append(a.maxBondOrder)
                    .append('/').append(a.maxBondOrderSum).append(" name=").append(a.name).append('\n');
        for (SpectrumSlot slot : SpectrumSlot.values()) {
            NMRSpectrum s = sd.spectrum(slot);
            b.append("spec ").append(s.specType).append(" name=").append(s.name).append(" dim=").append(s.dim)
                    .append(" nuc=").append(java.util.Arrays.toString(s.nucleus)).append(" solv=").append(s.solvent)
                    .append(" freq=").append(s.spectrometerFrequency).append('\n');
            for (NMRSignal g : s.signals) {
                b.append("  sig dim=").append(g.dim).append(" shift=").append(java.util.Arrays.toString(g.shift))
                        .append(" nuc=").append(java.util.Arrays.toString(g.nucleus)).append(" int=").append(g.intensity)
                        .append(" nf=").append(g.normalizeFactor).append(" ph=").append(g.phase).append('\n');
            }
        }
        for (AnnealingSchedule a : sd.annealingSchedules) {
            b.append("sched ").append(a.initialAcceptanceProbability).append(' ').append(a.maxUphillSteps).append(' ')
                    .append(a.plateauSteps).append(' ').append(a.asymptoticCoolingFactor).append('\n');
        }
        b.append("judges=").append(sd.judges.size()).append('\n');
        return b.toString();
    }

    /** "read\n" + fingerprint, or "fails". */
    static String legacyOutcome(Path f) {
        PrintStream err = System.err, out = System.out;
        try {
            System.setErr(new PrintStream(new ByteArrayOutputStream()));
            System.setOut(new PrintStream(new ByteArrayOutputStream()));
            seneca.core.SenecaDataset sd = legacyRead(f);
            if (sd == null) return "fails (null)";
            return "read\n" + fingerprint(sd);
        } catch (Throwable t) {
            return "fails (" + t.getClass().getSimpleName() + ")";
        } finally {
            System.setErr(err);
            System.setOut(out);
        }
    }

    static String portOutcome(Path f) {
        try {
            return "read\n" + fingerprint(SpecMLReader.read(f));
        } catch (Throwable t) {
            return "fails (" + t.getClass().getSimpleName() + (t.getCause() != null ? "/" + t.getCause().getClass().getSimpleName() : "") + ")";
        }
    }

    @TestFactory
    List<DynamicTest> specMlReaderFuzz() throws Exception {
        List<DynamicTest> tests = new ArrayList<>();
        int perFile = Integer.getInteger("verify.sml.mutants", 60);
        List<Path> files;
        try (var s = Files.list(SML)) {
            files = s.filter(p -> p.toString().endsWith(".sml")).sorted().toList();
        }
        for (Path src : files) {
            for (int k = 0; k < perFile; k++) {
                long seed = src.getFileName().toString().hashCode() * 1000L + k;
                int kk = k;
                tests.add(DynamicTest.dynamicTest(src.getFileName() + " mutant " + k, () -> {
                    DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
                    Document d = f.newDocumentBuilder().parse(src.toFile());
                    String what = mutate(d, new Random(seed));
                    Path file = dir.resolve("m" + seed + ".sml");
                    Files.writeString(file, text(d), StandardCharsets.UTF_8);
                    String l = legacyOutcome(file);
                    String p = portOutcome(file);
                    boolean lr = l.startsWith("read"), pr = p.startsWith("read");
                    if (lr && pr) READ_BOTH.incrementAndGet();
                    if (lr != pr || (lr && !l.equals(p))) {
                        System.out.println("[verify] SML mutant " + src.getFileName() + " #" + kk + " (" + what + ")\n  legacy: "
                                + (lr ? "read" : l) + "\n  port:   " + (pr ? "read" : p));
                    }
                    assertEquals(lr, pr, "readable? mutation " + what + ": legacy " + (lr ? "read" : l) + ", port " + (pr ? "read" : p));
                    if (lr) assertEquals(l, p, "dataset after mutation " + what);
                }));
            }
        }
        return tests;
    }

    // ------------------------------------------------------------------ 2. engine: undefined H counts and X atoms

    /**
     * Random molecules in which some atoms carry one bond order more than their valence (H count -1, the
     * "undefined" value the dataset uses before Autoassignment) and which may contain the wildcard X
     * (valence 10, max. bond order 3). The round-2 generator only produced exact H counts &gt;= 0 and no X.
     */
    static Spec undefinedHSpec(long seed) {
        Random r = new Random(seed);
        String[] pool = {"C", "C", "C", "C", "C", "C", "C", "C", "O", "O", "N", "S", "Cl", "X"};
        int n = 6 + r.nextInt(12);
        String[] sym = new String[n];
        int[] val = new int[n], mbo = new int[n], cap = new int[n];
        for (int i = 0; i < n; i++) {
            sym[i] = i == 0 ? "C" : pool[r.nextInt(pool.length)];
            val[i] = io.github.steinbeck.seneca.chem.StandardElements.getMaxBondOrderSum(sym[i]);
            mbo[i] = io.github.steinbeck.seneca.chem.StandardElements.getMaxBondOrder(sym[i]);
            // a few atoms may take one bond order more than their valence (they get H = -1)
            cap[i] = val[i] + (r.nextInt(4) == 0 ? 1 : 0);
        }
        int[][] bm = new int[n][n];
        int[] used = new int[n];
        for (int i = 1; i < n; i++) {
            List<Integer> parents = new ArrayList<>();
            for (int p = 0; p < i; p++) if (cap[p] - used[p] >= 1 && !(cap[p] == 1 && used[p] > 0)) parents.add(p);
            if (parents.isEmpty()) return null;
            int p = parents.get(r.nextInt(parents.size()));
            bm[i][p] = bm[p][i] = 1;
            used[i]++;
            used[p]++;
        }
        int extra = r.nextInt(n + 4);
        for (int t = 0; t < extra * 4 && extra > 0; t++) {
            int i = r.nextInt(n), j = r.nextInt(n);
            if (i == j || used[i] >= cap[i] || used[j] >= cap[j]) continue;
            if (bm[i][j] >= Math.min(mbo[i], mbo[j])) continue;
            bm[i][j]++;
            bm[j][i]++;
            used[i]++;
            used[j]++;
            extra--;
        }
        int[] h = new int[n];
        boolean undefined = false;
        for (int i = 0; i < n; i++) {
            h[i] = val[i] - used[i];
            if (h[i] < -1) return null;
            if (h[i] == -1) undefined = true;
        }
        if (!undefined && !java.util.Arrays.asList(sym).contains("X")) return null;
        Spec s = new Spec();
        s.name = "undefinedH#" + seed + " " + String.join("", sym) + " H" + java.util.Arrays.toString(h);
        s.sym = sym;
        s.h = h;
        s.seed = 1 + r.nextInt(1000);
        boolean hasX = java.util.Arrays.asList(sym).contains("X");
        if (!hasX && r.nextBoolean()) {   // HOSE judge with an X neighbour: NPE in both (DV-50 covers DEFAULT)
            s.hoseShifts = new double[n];
            for (int i = 0; i < n; i++) s.hoseShifts[i] = Math.round(r.nextDouble() * 2000) / 10.0;
        }
        s.featureEnabled = r.nextInt(4) != 0;
        if (s.featureEnabled) {
            List<int[]> rs = new ArrayList<>();
            for (int size = 3; size <= 8; size++) if (r.nextInt(3) == 0) rs.add(new int[] {size, r.nextInt(1001) - 500});
            if (!rs.isEmpty()) s.ringScores = rs.toArray(new int[0][]);
            s.hetHet = r.nextBoolean();
            s.hetHetPenalty = -1 - r.nextInt(10);
        }
        if (r.nextBoolean()) {
            List<int[]> p = new ArrayList<>();
            for (int k = 0; k < 2 + r.nextInt(5); k++) p.add(new int[] {r.nextInt(n), r.nextInt(n)});
            s.hmbcPairs = p.toArray(new int[0][]);
        }
        if (r.nextInt(3) == 0) {
            boolean[][] cls = new boolean[1][n];
            for (int k = 0; k < n; k++) cls[0][k] = r.nextInt(3) == 0;
            s.symmetryClasses = cls;
        }
        int ns = 1 + r.nextInt(3);
        for (int k = 0; k < ns; k++)
            s.schedules.add(new double[] {0.3 + r.nextDouble() * 0.65, 1 + r.nextInt(60), 1 + r.nextInt(80), 0.8 + r.nextDouble() * 0.18});
        s.cap = 1000 + r.nextInt(3000);
        return s;
    }

    @TestFactory
    List<DynamicTest> undefinedHydrogenAndWildcardLegacyVsPort() {
        List<DynamicTest> tests = new ArrayList<>();
        int count = Integer.getInteger("verify.undefinedH.count", 30);
        long seed = 5000;
        while (tests.size() < count) {
            Spec s = undefinedHSpec(seed++);
            if (s == null || !FidelityRound2OracleTest.runnable(s)) continue;
            tests.add(DynamicTest.dynamicTest(s.name, () -> {
                Outcome legacy = EngineFidelityOracleTestAccess.legacyWithTimeout(s);
                Outcome port = EngineDiffSupport.runPort(s, io.github.steinbeck.seneca.LegacyCompat.STRICT);
                System.out.println("[verify] " + s.name + "\n  legacy " + legacy.summary() + "\n  port   " + port.summary());
                assertEquals(legacy.exception() != null, port.exception() != null, "exception status");
                assertEquals(legacy.iterations(), port.iterations(), "iterations");
                assertEquals(legacy.draws(), port.draws(), "random draws");
                assertEquals(legacy.maxScore(), port.maxScore(), "maxScore");
                assertEquals(Double.doubleToRawLongBits(legacy.startKT()), Double.doubleToRawLongBits(port.startKT()), "start_kT");
                assertEquals(Double.doubleToRawLongBits(legacy.finalKT()), Double.doubleToRawLongBits(port.finalKT()), "current_kT");
                assertEquals(legacy.scores(), port.scores(), "result scores");
                assertEquals(legacy.structures(), port.structures(), "result MDL strings");
                assertEquals(legacy.descriptions(), port.descriptions(), "result descriptions");
                // DEFAULT differs from STRICT on these inputs only where quirk Q1 would end the run without a result (DV-04)
            }));
        }
        return tests;
    }

    // ------------------------------------------------------------------ 3. judges: configuration sequences

    /** Outcome of a call: its value or the exception class and message. */
    static String call(java.util.concurrent.Callable<Object> c) {
        try {
            return String.valueOf(c.call());
        } catch (Throwable t) {
            return "EXC " + t.getClass().getName() + ": " + t.getMessage();
        }
    }

    static String describe(io.github.steinbeck.seneca.judges.JudgeResult r) {
        return r.score + "|" + r.maxScore + "|" + r.propertyValue + "|" + r.scoreDescription;
    }

    static String describe(seneca.judges.JudgeResult r) {
        return r.score + "|" + r.maxScore + "|" + r.propertyValue + "|" + r.scoreDescription;
    }

    /** One configuration step, applied identically to the legacy and the port judge list. */
    interface Op {
        void legacy(List<seneca.judges.Judge> j) throws Exception;

        void port(List<io.github.steinbeck.seneca.judges.Judge> j) throws Exception;
    }

    static final double[] HYB_VALUES = {1.0, 0.9, 0.8, 0.7, 0.6, 0.5, 0.4, 0.3, 0.2, 0.1, 0.0, 0.29, 0.57, 0.58};

    /** Random configuration script over the 7 judges (dataset order: COSY, HMBC, HeteroBonds, Symmetry, Hybridization, HOSE, Feature). */
    static List<Op> script(Random r, int n) {
        List<Op> ops = new ArrayList<>();
        int k = 3 + r.nextInt(12);
        for (int i = 0; i < k; i++) {
            int kind = r.nextInt(16);
            switch (kind) {
                case 0, 1 -> {
                    int which = r.nextInt(2);
                    List<int[]> tr = new ArrayList<>();
                    for (int t = 0; t < 1 + r.nextInt(6); t++) {
                        int f = r.nextInt(n), g = r.nextInt(n);
                        tr.add(new int[] {f, g, g});
                        if (r.nextInt(3) == 0) tr.add(new int[] {f, g, r.nextInt(n)});   // alternative partner
                    }
                    ops.add(new Op() {
                        public void legacy(List<seneca.judges.Judge> j) {
                            boolean[][][] a = new boolean[n][n][n];
                            for (int[] x : tr) a[x[0]][x[1]][x[2]] = true;
                            ((seneca.judges.TwoDSpectrumJudge) j.get(which)).assignment = a;
                        }

                        public void port(List<io.github.steinbeck.seneca.judges.Judge> j) {
                            boolean[][][] a = new boolean[n][n][n];
                            for (int[] x : tr) a[x[0]][x[1]][x[2]] = true;
                            ((io.github.steinbeck.seneca.judges.TwoDSpectrumJudge) j.get(which)).assignment = a;
                        }
                    });
                }
                case 2 -> {
                    int which = r.nextInt(2);
                    int[] sc = new int[1 + r.nextInt(9)];
                    for (int t = 0; t < sc.length; t++) sc[t] = r.nextInt(3) == 0 ? 0 : r.nextInt(250) - 50;
                    ops.add(new Op() {
                        public void legacy(List<seneca.judges.Judge> j) {
                            ((seneca.judges.TwoDSpectrumJudge) j.get(which)).setScores(sc.clone());
                        }

                        public void port(List<io.github.steinbeck.seneca.judges.Judge> j) {
                            ((io.github.steinbeck.seneca.judges.TwoDSpectrumJudge) j.get(which)).setScores(sc.clone());
                        }
                    });
                }
                case 3 -> {
                    int which = r.nextInt(2), v = r.nextInt(200) - 20, pos = r.nextInt(11) - 1;
                    ops.add(new Op() {
                        public void legacy(List<seneca.judges.Judge> j) {
                            ((seneca.judges.TwoDSpectrumJudge) j.get(which)).setScore(v, pos);
                        }

                        public void port(List<io.github.steinbeck.seneca.judges.Judge> j) {
                            ((io.github.steinbeck.seneca.judges.TwoDSpectrumJudge) j.get(which)).setScore(v, pos);
                        }
                    });
                }
                case 4 -> {
                    int size = 2 + r.nextInt(8);
                    int[] choices = {-500, -300, 0, 0, 50, 100, 7};
                    int v = choices[r.nextInt(choices.length)];
                    ops.add(new Op() {
                        public void legacy(List<seneca.judges.Judge> j) {
                            ((seneca.judges.FeatureJudge) j.get(6)).addRingSizeScore(size, v);
                        }

                        public void port(List<io.github.steinbeck.seneca.judges.Judge> j) {
                            ((io.github.steinbeck.seneca.judges.FeatureJudge) j.get(6)).addRingSizeScore(size, v);
                        }
                    });
                }
                case 5 -> {
                    int pen = r.nextInt(21) - 15;
                    boolean on = r.nextBoolean();
                    ops.add(new Op() {
                        public void legacy(List<seneca.judges.Judge> j) {
                            ((seneca.judges.FeatureJudge) j.get(6)).setHetHetPenalty(pen);
                            ((seneca.judges.FeatureJudge) j.get(6)).setHetHetPenaltyEnabled(on);
                        }

                        public void port(List<io.github.steinbeck.seneca.judges.Judge> j) {
                            ((io.github.steinbeck.seneca.judges.FeatureJudge) j.get(6)).setHetHetPenalty(pen);
                            ((io.github.steinbeck.seneca.judges.FeatureJudge) j.get(6)).setHetHetPenaltyEnabled(on);
                        }
                    });
                }
                case 6 -> {
                    boolean[] cls = new boolean[n];
                    for (int t = 0; t < n; t++) cls[t] = r.nextInt(3) == 0;
                    boolean clear = r.nextInt(4) == 0;
                    ops.add(new Op() {
                        public void legacy(List<seneca.judges.Judge> j) {
                            if (clear) ((seneca.judges.SymmetryJudge) j.get(3)).clearSymmetryClasses();
                            ((seneca.judges.SymmetryJudge) j.get(3)).addSymmetryClass(cls.clone());
                        }

                        public void port(List<io.github.steinbeck.seneca.judges.Judge> j) {
                            if (clear) ((io.github.steinbeck.seneca.judges.SymmetryJudge) j.get(3)).clearSymmetryClasses();
                            ((io.github.steinbeck.seneca.judges.SymmetryJudge) j.get(3)).addSymmetryClass(cls.clone());
                        }
                    });
                }
                case 7 -> {
                    int[] choices = {500, 300, 1, -5, 1000};
                    int v = choices[r.nextInt(choices.length)];
                    ops.add(new Op() {
                        public void legacy(List<seneca.judges.Judge> j) {
                            ((seneca.judges.SymmetryJudge) j.get(3)).setScore(v);
                        }

                        public void port(List<io.github.steinbeck.seneca.judges.Judge> j) {
                            ((io.github.steinbeck.seneca.judges.SymmetryJudge) j.get(3)).setScore(v);
                        }
                    });
                }
                case 8 -> {
                    int len = r.nextInt(4) == 0 ? n + r.nextInt(3) - 1 : n;
                    ops.add(new Op() {
                        public void legacy(List<seneca.judges.Judge> j) {
                            ((seneca.judges.HybridizationJudge) j.get(4)).initScoreArray(len);
                        }

                        public void port(List<io.github.steinbeck.seneca.judges.Judge> j) {
                            ((io.github.steinbeck.seneca.judges.HybridizationJudge) j.get(4)).initScoreArray(len);
                        }
                    });
                }
                case 9 -> {
                    int node = 1 + r.nextInt(n), hyb = r.nextInt(3);
                    double v = HYB_VALUES[r.nextInt(HYB_VALUES.length)];
                    ops.add(new Op() {
                        public void legacy(List<seneca.judges.Judge> j) {
                            ((seneca.judges.HybridizationJudge) j.get(4)).addHybridizationScore(node, hyb, v);
                        }

                        public void port(List<io.github.steinbeck.seneca.judges.Judge> j) {
                            ((io.github.steinbeck.seneca.judges.HybridizationJudge) j.get(4)).addHybridizationScore(node, hyb, v);
                        }
                    });
                }
                case 10 -> {
                    boolean[] flags = new boolean[r.nextInt(4) == 0 ? n + 1 : n];
                    for (int t = 0; t < flags.length; t++) flags[t] = r.nextBoolean();
                    int score = r.nextBoolean() ? 100 : r.nextInt(300) - 50;
                    ops.add(new Op() {
                        public void legacy(List<seneca.judges.Judge> j) {
                            ((seneca.judges.HeteroBondsJudge) j.get(2)).isHeteroBonded = flags.clone();
                            ((seneca.judges.HeteroBondsJudge) j.get(2)).setScore(score);
                        }

                        public void port(List<io.github.steinbeck.seneca.judges.Judge> j) {
                            ((io.github.steinbeck.seneca.judges.HeteroBondsJudge) j.get(2)).isHeteroBonded = flags.clone();
                            ((io.github.steinbeck.seneca.judges.HeteroBondsJudge) j.get(2)).setScore(score);
                        }
                    });
                }
                case 11 -> {
                    double[] sh = new double[r.nextInt(n + 3)];
                    for (int t = 0; t < sh.length; t++) sh[t] = r.nextInt(10) == 0 ? Double.NaN : Math.round(r.nextDouble() * 2200) / 10.0;
                    ops.add(new Op() {
                        public void legacy(List<seneca.judges.Judge> j) {
                            ((seneca.judges.HOSECodeJudge) j.get(5)).setCarbonShifts(sh.clone());
                        }

                        public void port(List<io.github.steinbeck.seneca.judges.Judge> j) {
                            ((io.github.steinbeck.seneca.judges.HOSECodeJudge) j.get(5)).setCarbonShifts(sh.clone());
                        }
                    });
                }
                case 12 -> {
                    int which = r.nextInt(7);
                    ops.add(new Op() {
                        public void legacy(List<seneca.judges.Judge> j) {
                            j.get(which).init();
                        }

                        public void port(List<io.github.steinbeck.seneca.judges.Judge> j) {
                            j.get(which).init();
                        }
                    });
                }
                case 13 -> {
                    int which = r.nextInt(7);
                    ops.add(new Op() {
                        public void legacy(List<seneca.judges.Judge> j) {
                            j.get(which).calcMaxScore();
                        }

                        public void port(List<io.github.steinbeck.seneca.judges.Judge> j) {
                            j.get(which).calcMaxScore();
                        }
                    });
                }
                default -> {
                    int which = r.nextInt(7);
                    boolean on = r.nextInt(4) != 0;
                    ops.add(new Op() {
                        public void legacy(List<seneca.judges.Judge> j) {
                            j.get(which).setEnabled(on);
                        }

                        public void port(List<io.github.steinbeck.seneca.judges.Judge> j) {
                            j.get(which).setEnabled(on);
                        }
                    });
                }
            }
        }
        return ops;
    }

    /** A random connected, saturated molecule (symbols, H counts, bond matrix) or null. */
    static Object[] randomMolecule(Random r) {
        String[] pool = {"C", "C", "C", "C", "C", "C", "C", "O", "O", "N", "S", "Cl", "Br", "F", "P", "Si", "I"};
        int n = 5 + r.nextInt(12);
        String[] sym = new String[n];
        int[] val = new int[n], mbo = new int[n];
        for (int i = 0; i < n; i++) {
            sym[i] = i == 0 ? "C" : pool[r.nextInt(pool.length)];
            val[i] = io.github.steinbeck.seneca.chem.StandardElements.getMaxBondOrderSum(sym[i]);
            mbo[i] = io.github.steinbeck.seneca.chem.StandardElements.getMaxBondOrder(sym[i]);
        }
        int[][] bm = new int[n][n];
        int[] used = new int[n];
        for (int i = 1; i < n; i++) {
            List<Integer> parents = new ArrayList<>();
            for (int p = 0; p < i; p++) if (val[p] - used[p] >= 1 && !(val[p] == 1 && used[p] > 0)) parents.add(p);
            if (parents.isEmpty()) return null;
            int p = parents.get(r.nextInt(parents.size()));
            bm[i][p] = bm[p][i] = 1;
            used[i]++;
            used[p]++;
        }
        for (int t = 0; t < 4 * n; t++) {
            int i = r.nextInt(n), j = r.nextInt(n);
            if (i == j || used[i] >= val[i] || used[j] >= val[j] || bm[i][j] >= Math.min(mbo[i], mbo[j])) continue;
            bm[i][j]++;
            bm[j][i]++;
            used[i]++;
            used[j]++;
        }
        int[] h = new int[n];
        for (int i = 0; i < n; i++) h[i] = val[i] - used[i];
        return new Object[] {sym, h, bm};
    }

    static compchem.Atom[] legacyAtoms(String[] sym, int[] h) {
        compchem.StandardElements se = new compchem.StandardElements();
        compchem.Atom[] a = new compchem.Atom[sym.length];
        for (int i = 0; i < a.length; i++) {
            a[i] = new compchem.Atom(sym[i], i);
            a[i].HCount = h[i];
            a[i].maxBondOrder = se.getMaxBondOrder(sym[i]);
            a[i].maxBondOrderSum = se.getMaxBondOrderSum(sym[i]);
        }
        return a;
    }

    static HeavyAtom[] portAtoms(String[] sym, int[] h) {
        HeavyAtom[] a = new HeavyAtom[sym.length];
        for (int i = 0; i < a.length; i++) {
            a[i] = new HeavyAtom(sym[i], i);
            a[i].hCount = h[i];
            a[i].maxBondOrder = io.github.steinbeck.seneca.chem.StandardElements.getMaxBondOrder(sym[i]);
            a[i].maxBondOrderSum = io.github.steinbeck.seneca.chem.StandardElements.getMaxBondOrderSum(sym[i]);
        }
        return a;
    }

    @SuppressWarnings("unchecked")
    @TestFactory
    List<DynamicTest> judgeConfigurationSequences() {
        List<DynamicTest> tests = new ArrayList<>();
        int count = Integer.getInteger("verify.judgecfg.count", 400);
        long seed = 9000;
        while (tests.size() < count) {
            long sd = seed++;
            Random r = new Random(sd);
            Object[] mol = randomMolecule(r);
            if (mol == null) continue;
            String[] sym = (String[]) mol[0];
            int[] h = (int[]) mol[1];
            int[][] bm0 = (int[][]) mol[2];
            int n = sym.length;
            List<Op> ops = script(r, n);
            long walkSeed = r.nextLong();
            tests.add(DynamicTest.dynamicTest("judge config #" + sd + " " + String.join("", sym), () -> {
                List<seneca.judges.Judge> lj = new ArrayList<>(List.of(new seneca.judges.HHCOSYJudge(), new seneca.judges.HMBCJudge(),
                        new seneca.judges.HeteroBondsJudge(), new seneca.judges.SymmetryJudge(), new seneca.judges.HybridizationJudge(),
                        new seneca.judges.HOSECodeJudge(), new seneca.judges.FeatureJudge()));
                List<io.github.steinbeck.seneca.judges.Judge> pj = new ArrayList<>(List.of(new io.github.steinbeck.seneca.judges.HHCOSYJudge(),
                        new io.github.steinbeck.seneca.judges.HMBCJudge(), new io.github.steinbeck.seneca.judges.HeteroBondsJudge(),
                        new io.github.steinbeck.seneca.judges.SymmetryJudge(), new io.github.steinbeck.seneca.judges.HybridizationJudge(),
                        new io.github.steinbeck.seneca.judges.HOSECodeJudge(), new io.github.steinbeck.seneca.judges.FeatureJudge()));
                // the dataset window: Hybridization and HeteroBonds configurators init() with the dataset atoms
                compchem.Atom[] dsL = legacyAtoms(sym, h);
                HeavyAtom[] dsP = portAtoms(sym, h);
                lj.get(4).getAttic().atomSet = dsL;
                lj.get(4).init();
                lj.get(2).getAttic().atomSet = dsL;
                lj.get(2).init();
                pj.get(4).getAttic().atomSet = dsP;
                pj.get(4).init();
                pj.get(2).getAttic().atomSet = dsP;
                pj.get(2).init();
                StringBuilder trace = new StringBuilder();
                for (Op op : ops) {
                    String a = call(() -> {
                        op.legacy(lj);
                        return "ok";
                    });
                    String b = call(() -> {
                        op.port(pj);
                        return "ok";
                    });
                    trace.append(a).append(" / ").append(b).append('\n');
                    assertEquals(a, b, "configuration step outcome\n" + trace);
                }
                // "Start": calcMaxScore on the enabled judges, then a deep copy of all judges
                for (int i = 0; i < 7; i++) {
                    int ii = i;
                    if (lj.get(i).getEnabled() != pj.get(i).getEnabled()) throw new AssertionError("enabled flag " + i);
                    if (!lj.get(i).getEnabled()) continue;
                    String a = call(() -> {
                        lj.get(ii).calcMaxScore();
                        return lj.get(ii).getMaxScore();
                    });
                    String b = call(() -> {
                        pj.get(ii).calcMaxScore();
                        return pj.get(ii).getMaxScore();
                    });
                    assertEquals(a, b, "calcMaxScore of judge " + i + "\n" + trace);
                    if (a.startsWith("EXC")) return;   // legacy "Start" fails here
                }
                Vector<Object> lv = new Vector<>(lj);
                List<seneca.judges.Judge> lc = (List<seneca.judges.Judge>) (List<?>) LegacyDriver.serClone(lv);
                List<io.github.steinbeck.seneca.judges.Judge> pc = io.github.steinbeck.seneca.judges.JudgeSet.deepCopy(pj);
                // generator side: setAttic + init on the enabled judges
                seneca.structgen.Attic la = new seneca.structgen.Attic();
                io.github.steinbeck.seneca.judges.Attic pa = new io.github.steinbeck.seneca.judges.Attic();
                la.atomSet = legacyAtoms(sym, h);
                pa.atomSet = portAtoms(sym, h);
                compchem.DataStructureTools.synchronizeBonds(la.atomSet, bm0);
                io.github.steinbeck.seneca.chem.AtomSets.synchronizeBonds(pa.atomSet, bm0);
                long lmax = 0, pmax = 0;
                for (int i = 0; i < 7; i++) {
                    if (!lc.get(i).getEnabled()) continue;
                    int ii = i;
                    lc.get(i).setAttic(la);
                    pc.get(i).setAttic(pa);
                    String a = call(() -> {
                        lc.get(ii).init();
                        return "ok";
                    });
                    String b = call(() -> {
                        pc.get(ii).init();
                        return "ok";
                    });
                    assertEquals(a, b, "generator init of judge " + i + "\n" + trace);
                    lmax += lc.get(i).getMaxScore();
                    pmax += pc.get(i).getMaxScore();
                }
                assertEquals(lmax, pmax, "generator maxScore");
                // evaluate along a random walk of connected structures
                int[][] bm = io.github.steinbeck.seneca.chem.AtomSets.cloneInt2D(bm0);
                // mutate never returns without any valid Faulon move (DV-65): walk only if a connected move exists
                boolean walkable = n >= 4 && FidelityRound2VerifyTest.neighbours(bm0, portAtoms(sym, h))[1] > 0;
                io.github.steinbeck.seneca.structgen.RunRandom rnd = io.github.steinbeck.seneca.structgen.RunRandom.seeded(walkSeed);
                for (int step = 0; step < 40; step++) {
                    compchem.DataStructureTools.synchronizeBonds(la.atomSet, bm);
                    io.github.steinbeck.seneca.chem.AtomSets.synchronizeBonds(pa.atomSet, bm);
                    for (int i = 0; i < 7; i++) {
                        if (!lc.get(i).getEnabled()) continue;
                        int ii = i;
                        int[][] cbm = bm;
                        String b = call(() -> describe(pc.get(ii).evaluate(cbm)));
                        // the legacy ring finder loops for ever where the port reports HangDetected (DV-03): skip legacy
                        if (b.contains("HangDetected")) continue;
                        String a = call(() -> describe(lc.get(ii).evaluate(cbm)));
                        if (b.startsWith("EXC java.lang.ArrayIndexOutOfBoundsException") && a.startsWith("EXC java.lang.ArrayIndexOutOfBoundsException")) {
                            b = a; // implicit AIOOBE messages are equal by construction of the index; compare the class only
                        }
                        assertEquals(a, b, "evaluate judge " + i + " step " + step + "\n" + trace);
                    }
                    if (n < 4 || !walkable) break;
                    int[][] next;
                    int guard = 0;
                    do {
                        next = io.github.steinbeck.seneca.chem.AtomSets.cloneInt2D(bm);
                        io.github.steinbeck.seneca.structgen.StochasticGenerator.mutate(next, rnd);
                        HeavyAtom[] tmp = portAtoms(sym, h);
                        io.github.steinbeck.seneca.chem.AtomSets.synchronizeBonds(tmp, next);
                        if (io.github.steinbeck.seneca.chem.PathUtils.isConnected(tmp)) break;
                    } while (++guard < 200);
                    if (guard >= 200) break;
                    bm = next;
                }
            }));
        }
        return tests;
    }

    // ------------------------------------------------------------------ 4. engine: odd atom sets

    static List<Spec> oddInputs() {
        List<Spec> l = new ArrayList<>();
        {
            Spec s = new Spec();
            s.name = "ten carbons, every H count undefined (-1)";
            s.sym = new String[] {"C", "C", "C", "C", "C", "C", "C", "C", "C", "C"};
            s.h = new int[] {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
            s.featureEnabled = true;
            // no ring scores: carbons with five bond orders make the legacy ring finder loop for ever (DV-03)
            s.hetHet = true;
            s.hoseShifts = new double[] {20.8, 22.9, 26.4, 31.3, 31.5, 38.0, 40.8, 47.1, 116.0, 144.5};
            s.schedules.add(new double[] {0.8, 20, 30, 0.9});
            s.cap = 3000;
            l.add(s);
        }
        {
            Spec s = new Spec();
            s.name = "wildcard X atoms only";
            s.sym = new String[] {"X", "X", "X", "X", "X", "X"};
            s.h = new int[] {7, 8, 8, 7, 9, 9};
            s.featureEnabled = true;
            s.hetHet = true;   // no ring scores (degree 4-6 atoms: legacy ring finder hang, DV-03)
            s.hmbcPairs = new int[][] {{0, 3}, {1, 4}};
            s.symmetryClasses = new boolean[][] {{true, false, false, true, false, false}};
            s.schedules.add(new double[] {0.8, 20, 30, 0.9});
            s.schedules.add(new double[] {0.6, 10, 10, 0.8});
            s.cap = 3000;
            l.add(s);
        }
        {
            Spec s = new Spec();
            s.name = "explicit hydrogen atoms as heavy atoms with HOSE judge";
            s.sym = new String[] {"C", "C", "C", "O", "H", "H", "H"};
            s.h = new int[] {2, 1, 2, 0, 0, 0, 0};
            s.hoseShifts = new double[] {30.0, 60.0, 15.0, 0, 0, 0, 0};
            s.featureEnabled = true;
            s.hetHet = true;
            s.schedules.add(new double[] {0.8, 20, 30, 0.9});
            s.cap = 3000;
            l.add(s);
        }
        return l;
    }

    @TestFactory
    List<DynamicTest> oddAtomSetsLegacyVsPort() {
        List<DynamicTest> tests = new ArrayList<>();
        for (Spec s : oddInputs()) {
            tests.add(DynamicTest.dynamicTest(s.name, () -> {
                Outcome legacy = EngineFidelityOracleTestAccess.legacyWithTimeout(s);
                Outcome port = EngineDiffSupport.runPort(s, io.github.steinbeck.seneca.LegacyCompat.STRICT);
                System.out.println("[verify] " + s.name + "\n  legacy " + legacy.summary() + "\n  port   " + port.summary());
                assertEquals(legacy.exception() != null, port.exception() != null, "exception status");
                assertEquals(legacy.iterations(), port.iterations(), "iterations");
                assertEquals(legacy.draws(), port.draws(), "random draws");
                assertEquals(legacy.maxScore(), port.maxScore(), "maxScore");
                assertEquals(legacy.scores(), port.scores(), "result scores");
                assertEquals(legacy.structures(), port.structures(), "result MDL strings");
                assertEquals(legacy.descriptions(), port.descriptions(), "result descriptions");
            }));
        }
        return tests;
    }

    // ------------------------------------------------------------------ 5. stale judge configuration (legacy side)

    /**
     * Legacy side of {@code verify/FidelityRound3VerifyTest}: the 2001 engine on octane with an HMBC
     * assignment (or a symmetry class) left over from a 10-atom formula. Every evaluation throws
     * {@code ArrayIndexOutOfBoundsException}; {@code run()} catches it ("Error in StoicGent.run()") and the
     * run ends with no iteration and no result ({@code running} stays true).
     */
    @org.junit.jupiter.api.Test
    void legacyCrashesOnStaleJudgeConfiguration() throws Exception {
        for (boolean hmbc : new boolean[] {true, false}) {
            String[] sym = {"C", "C", "C", "C", "C", "C", "C", "C"};
            int[] h = {3, 2, 2, 2, 2, 2, 2, 3};
            compchem.Atom[] atoms = legacyAtoms(sym, h);
            Vector<Object> judges = new Vector<>();
            seneca.judges.HMBCJudge hm = new seneca.judges.HMBCJudge();
            seneca.judges.SymmetryJudge sj = new seneca.judges.SymmetryJudge();
            if (hmbc) {
                hm.assignment = new boolean[10][10][10];
                hm.assignment[9][8][8] = true;
                hm.assignment[0][1][1] = true;
                hm.setEnabled(true);
                hm.calcMaxScore();
            } else {
                boolean[] c = new boolean[10];
                c[2] = c[9] = true;
                sj.addSymmetryClass(c);
                sj.setEnabled(true);
                sj.calcMaxScore();
            }
            judges.add(hm);
            judges.add(sj);
            seneca.structgen.StochasticGenerator g = new seneca.structgen.StochasticGenerator(true);
            g.setMolecule((compchem.Molecule) LegacyDriver.serClone(new compchem.Molecule(atoms)));
            g.setJudges((Vector<?>) LegacyDriver.serClone(judges));
            Vector<Object> sched = new Vector<>();
            sched.add(new seneca.structgen.AnnealingSchedule());
            g.setSASchedules(sched);
            seneca.structgen.Rng.seed(1);
            PrintStream err = System.err, out = System.out;
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            try {
                System.setErr(new PrintStream(buf));
                System.setOut(new PrintStream(new ByteArrayOutputStream()));
                g.run();
            } finally {
                System.setErr(err);
                System.setOut(out);
            }
            seneca.structgen.StructureGeneratorResult r = (seneca.structgen.StructureGeneratorResult) g.getResult();
            String trace = buf.toString();
            System.out.println("[verify] legacy stale " + (hmbc ? "HMBC assignment" : "symmetry class") + ": running=" + g.running
                    + " results=" + r.size() + " " + trace.lines().filter(l -> l.contains("Exception")).findFirst().orElse("(no exception)"));
            org.junit.jupiter.api.Assertions.assertTrue(g.running, "legacy run ended through the catch (running stays true)");
            org.junit.jupiter.api.Assertions.assertEquals(0, r.size(), "legacy results");
            org.junit.jupiter.api.Assertions.assertTrue(trace.contains("ArrayIndexOutOfBoundsException"), trace);
        }
    }
}
