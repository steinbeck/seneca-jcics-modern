package seneca.structgen;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.Callable;

import compchem.*;
import golden.Json;
import seneca.client.GuiRecipe;
import seneca.core.NMRSignal;
import seneca.core.NMRSpectrum;
import seneca.core.SenecaDataset;
import seneca.judges.*;

/**
 * Golden-master recorder for the legacy (2001) SENECA code in legacy/runnable/classes.
 *
 * Lives in package seneca.structgen to read the generator's package-private/protected state
 * (current_kT, lastScore, iteration, ...) and to call the protected StochasticGenerator.mutate().
 * No legacy class is modified or shadowed. Output format: golden/FORMAT.md.
 *
 * Usage (one dataset per JVM, see HOW-TO-DRIVE quirk Q7). <key> is the dataset key; a key
 * "<name>@hblock" is a variant of <name>.sml (see GuiRecipe.VARIANT_HBLOCK):
 *   echo   <file.sml> <key> <out.jsonl>
 *   scores <file.sml> <key> <out.jsonl>
 *   ref    <file.sml> <key> <references.tsv> <out.jsonl>
 *   traj   <file.sml> <key> <profile> <seed> <steps> <out.jsonl>                     (profile: gui|paper|manual)
 *   full   <file.sml> <key> <profile> <seed> <iterationCap> <wallCapSeconds> <references.tsv> <out.json> <timing.jsonl>
 */
public final class GoldenMaster {

    static final int N_STRUCTURES = 50;
    static final int MOVES_PER_K = 20;
    static final long GEN_TIMEOUT_MS = 60_000;
    static final long TRAJ_TIMEOUT_MS = 600_000;
    static final String[] ECHO_PROFILES = {GuiRecipe.PROFILE_GUI, GuiRecipe.PROFILE_ALL, GuiRecipe.PROFILE_PAPER, GuiRecipe.PROFILE_MANUAL};
    static final String[] SPECTRUM_SLOTS = {"carbon1D", "dept90", "dept135", "hetcor", "hetcorlr", "hhcosy", "noesy"};

    public static void main(String[] a) throws Exception {
        org.apache.log4j.BasicConfigurator.configure();     // console appender (stdout = chatter log)
        org.apache.log4j.Category.getRoot().setPriority(org.apache.log4j.Priority.FATAL);

        String mode = a[0];
        File sml = new File(a[1]);
        String key = a[2];
        int rc;
        switch (mode) {
            case "echo": rc = echo(sml, key, a[3]); break;
            case "scores": rc = scores(sml, key, a[3]); break;
            case "ref": rc = ref(sml, key, new File(a[3]), a[4]); break;
            case "traj": rc = traj(sml, key, a[3], Long.parseLong(a[4]), Integer.parseInt(a[5]), a[6]); break;
            case "full": rc = full(sml, key, a[3], Long.parseLong(a[4]), Long.parseLong(a[5]), Long.parseLong(a[6]),
                    new File(a[7]), a[8], a[9]); break;
            default: throw new IllegalArgumentException("unknown mode " + mode);
        }
        System.out.flush();
        System.exit(rc); // generator threads that timed out are still running
    }

    // =====================================================================================
    // (a) dataset echo
    // =====================================================================================

    static int echo(File sml, String key, String outPath) throws Exception {
        try (PrintWriter out = writer(outPath)) {
            // as read: a plain load, no GUI steps
            SenecaDataset raw = new seneca.core.SpecMLReader(sml).getSenecaDataset();
            if (raw == null || raw.setOfNodes == null) {
                out.print(Json.obj().put("t", "unreadable").put("key", key)
                        .put("reason", raw == null ? "SpecMLReader returned null" : "no atoms") + "\n");
                return 2;
            }
            boolean anyShift = false;
            for (Atom at : raw.setOfNodes) if (at.getPhysicalProperty(Atom.PHYS_PROP_13C_SHIFT) != null) anyShift = true;
            out.print(Json.obj().put("t", "dataset").put("key", key).put("file", sml.getName())
                    .put("title", raw.getName()).put("formula", raw.getMolecularFormula())
                    .put("description", raw.getDescription()).putFloat("molecularMass", raw.getMolecularMass())
                    .put("nAtoms", raw.setOfNodes.length).put("shiftsRead", anyShift)
                    .put("ownStructure", "none: no bundled file contains bonds; see references/") + "\n");
            out.print(atomsRecord("as_read", raw.setOfNodes) + "\n");
            for (String slot : SPECTRUM_SLOTS) out.print(spectrumRecord("as_read", slot, spectrum(raw, slot)) + "\n");
            out.print(schedulesRecord("as_read", raw.annealingSchedules) + "\n");
            out.print(judgesRecord("as_read", raw.judges, false) + "\n");

            for (String profile : ECHO_PROFILES) {
                GuiRecipe.Prepared p = GuiRecipe.prepare(sml, key, profile);
                Go go = goAction(p.sd);
                String st = "configured_" + profile;
                out.print(Json.obj().put("t", "recipe").put("profile", profile)
                        .put("autoAssign", p.autoAssigned).put("scheduleAdded", p.scheduleAdded)
                        .put("hOverrides", Json.strings(p.hOverrides)).put("steps", Json.strings(p.steps)) + "\n");
                if (profile.equals(GuiRecipe.PROFILE_GUI)) {
                    out.print(atomsRecord(st, p.sd.setOfNodes) + "\n");
                    for (String slot : SPECTRUM_SLOTS) out.print(spectrumRecord(st, slot, spectrum(p.sd, slot)) + "\n");
                    out.print(schedulesRecord(st, p.sd.annealingSchedules) + "\n");
                }
                // the judges exactly as handed to the generator (serialized copy, after calcMaxScore)
                out.print(judgesRecord(st, go.judges, true) + "\n");
                out.print(Json.obj().put("t", "generatorInput").put("profile", profile)
                        .put("enabled", Json.strings(GuiRecipe.enabledJudges(p.sd)))
                        .put("maxScore", generatorMaxScore(go.judges)) + "\n");
            }
        }
        return 0;
    }

    static NMRSpectrum spectrum(SenecaDataset sd, String slot) {
        switch (slot) {
            case "carbon1D": return sd.carbon1D;
            case "dept90": return sd.dept90;
            case "dept135": return sd.dept135;
            case "hetcor": return sd.hetcor;
            case "hetcorlr": return sd.hetcorlr;
            case "hhcosy": return sd.hhcosy;
            default: return sd.noesy;
        }
    }

    static Json atomsRecord(String stage, Atom[] atoms) {
        Json arr = Json.arr();
        int hSum = 0;
        for (int f = 0; f < atoms.length; f++) {
            Atom at = atoms[f];
            Object s = at.getPhysicalProperty(Atom.PHYS_PROP_13C_SHIFT);
            hSum += at.HCount;
            arr.add(Json.obj().put("i", f).put("el", at.symbol).put("id", at.name).put("h", at.HCount)
                    .putFloat("shift", (Float) s).put("number", at.number)
                    .put("maxBondOrder", at.maxBondOrder).put("maxBondOrderSum", at.maxBondOrderSum));
        }
        return Json.obj().put("t", "atoms").put("stage", stage).put("hSum", hSum).put("atoms", arr);
    }

    static Json spectrumRecord(String stage, String slot, NMRSpectrum s) {
        Json sig = Json.arr();
        for (int f = 0; f < s.size(); f++) {
            NMRSignal g = (NMRSignal) s.getSignal(f);
            sig.add(Json.obj().put("shift", Json.floats(g.shift)).putFloat("intensity", g.intensity)
                    .putFloat("normalizeFactor", g.normalizeFactor).put("phase", g.phase)
                    .put("nucleus", Json.strings(g.nucleus)));
        }
        return Json.obj().put("t", "spectrum").put("stage", stage).put("slot", slot).put("name", s.name)
                .put("specType", s.specType).put("dim", s.dim).put("nucleus", Json.strings(s.nucleus))
                .putFloat("frequency", s.spectrometerFrequency).put("solvent", s.solvent)
                .put("standard", s.standard).put("nSignals", s.size()).put("signals", sig);
    }

    static Json schedulesRecord(String stage, Vector v) {
        Json arr = Json.arr();
        for (int f = 0; f < v.size(); f++) {
            AnnealingSchedule s = (AnnealingSchedule) v.elementAt(f);
            arr.add(Json.obj().put("initialAcceptanceProbability", s.initialAcceptanceProbability)
                    .put("maxUphillSteps", s.maxUphillSteps).put("plateauSteps", s.plateauSteps)
                    .put("asymptoticCoolingFactor", s.asymptoticCoolingFactor));
        }
        return Json.obj().put("t", "schedules").put("stage", stage).put("schedules", arr);
    }

    static Json judgesRecord(String stage, Vector judges, boolean full) throws Exception {
        Json arr = Json.arr();
        for (int g = 0; g < judges.size(); g++) arr.add(judgeJson((Judge) judges.elementAt(g), full));
        return Json.obj().put("t", "judges").put("stage", stage).put("judges", arr);
    }

    static Json judgeJson(Judge j, boolean full) throws Exception {
        Json o = Json.obj().put("name", j.name).put("class", j.getClass().getSimpleName())
                .put("enabled", j.getEnabled()).put("hasMaxScore", j.getHasMaxScore())
                .put("maxScore", j.getMaxScore()).put("multiplicator", j.getMultiplicator())
                .put("ringSetRequired", j.isRingSetRequired())
                .put("judgeScoreField", ((Integer) get(j, Judge.class, "score")).intValue());
        if (!full) return o;
        if (j instanceof TwoDSpectrumJudge) {
            TwoDSpectrumJudge t = (TwoDSpectrumJudge) j;
            o.put("scores", Json.ints(t.scores));
            if (t.assignment == null) o.putNull("assignment");
            else {
                Json tr = Json.arr();
                int n = t.assignment.length;
                for (int f = 0; f < n; f++)
                    for (int g = 0; g < n; g++)
                        for (int h = 0; h < n; h++)
                            if (t.assignment[f][g][h]) tr.add(Json.arr().add(f).add(g).add(h));
                o.put("assignmentSize", n).put("assignment", tr);
            }
            o.put("rules", t.rules.size())
                    .put("numberOf2DSignals", ((Integer) get(j, TwoDSpectrumJudge.class, "numberOf2DSignals")).intValue())
                    .put("cutoff", ((Integer) get(j, TwoDSpectrumJudge.class, "cutoff")).intValue());
        } else if (j instanceof HOSECodeJudge) {
            o.put("score", ((HOSECodeJudge) j).score)
                    .put("carbonShifts", Json.doubles((double[]) get(j, HOSECodeJudge.class, "carbonShifts")));
        } else if (j instanceof SymmetryJudge) {
            SymmetryJudge s = (SymmetryJudge) j;
            Json cl = Json.arr();
            for (int c = 0; c < s.getSymmetryClassCount(); c++) {
                boolean[] b = s.getSymmetryClass(c);
                Json m = Json.arr();
                for (int f = 0; f < b.length; f++) if (b[f]) m.add(f);
                cl.add(m);
            }
            o.put("classes", cl);
        } else if (j instanceof FeatureJudge) {
            FeatureJudge fj = (FeatureJudge) j;
            TreeMap<Integer, Integer> sorted = new TreeMap<Integer, Integer>();
            for (Object k : fj.ringSizeScores.keySet()) sorted.put((Integer) k, (Integer) fj.ringSizeScores.get(k));
            Json rs = Json.arr();
            for (Map.Entry<Integer, Integer> e : sorted.entrySet()) rs.add(Json.arr().add(e.getKey()).add(e.getValue()));
            o.put("ringSizeScores", rs).put("hetHetPenalty", fj.getHetHetPenalty())
                    .put("hetHetPenaltyEnabled", fj.isHetHetPenaltyEnabled())
                    .put("ringsizeRestrictionEnabled", ((Boolean) get(j, FeatureJudge.class, "ringsizeRestrictionEnabled")).booleanValue());
        } else if (j instanceof HybridizationJudge) {
            HybridizationJudge h = (HybridizationJudge) j;
            Json sc = Json.arr();
            if (h.scores != null) for (int[] row : h.scores) sc.add(Json.ints(row));
            o.put("scores", h.scores == null ? null : sc);
        } else if (j instanceof HeteroBondsJudge) {
            HeteroBondsJudge h = (HeteroBondsJudge) j;
            Json hb = Json.arr();
            if (h.isHeteroBonded != null) for (boolean b : h.isHeteroBonded) hb.add(b);
            o.put("score", h.score).put("isHeteroBonded", h.isHeteroBonded == null ? null : hb)
                    .put("privateMaxScore", ((Integer) get(j, HeteroBondsJudge.class, "maxScore")).intValue());
        }
        return o;
    }

    static Object get(Object o, Class<?> c, String field) throws Exception {
        Field f = c.getDeclaredField(field);
        f.setAccessible(true);
        return f.get(o);
    }

    static void set(Object o, Class<?> c, String field, Object v) throws Exception {
        Field f = c.getDeclaredField(field);
        f.setAccessible(true);
        f.set(o, v);
    }

    /** Same as the private StochasticGenerator.getMaxScore(). */
    static long generatorMaxScore(Vector judges) {
        int max = 0;
        for (int f = 0; f < judges.size(); f++) {
            Judge j = (Judge) judges.elementAt(f);
            if (j.getEnabled()) {
                if (!j.getHasMaxScore()) return -1;
                max += j.getMaxScore();
            }
        }
        return max;
    }

    // =====================================================================================
    // "Start as selected": SGServerPanel.goAction
    // =====================================================================================

    static final class Go {
        Molecule molecule;
        Vector judges;
        Vector schedules;
    }

    static Go goAction(SenecaDataset sd) throws Exception {
        for (int g = 0; g < sd.judges.size(); g++) {
            Judge j = (Judge) sd.judges.elementAt(g);
            if (j.getEnabled()) j.calcMaxScore();
        }
        Vector sched = new Vector();
        for (int g = 0; g < sd.annealingSchedules.size(); g++)
            sched.addElement(((AnnealingSchedule) sd.annealingSchedules.elementAt(g)).clone());
        Go go = new Go();
        go.molecule = (Molecule) serClone(new Molecule(sd.getSetOfNodes()));
        go.judges = (Vector) serClone(sd.judges);
        go.schedules = (Vector) serClone(sched);
        return go;
    }

    static Object serClone(Object o) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(buf);
        oos.writeObject(o);
        oos.flush();
        return new ObjectInputStream(new ByteArrayInputStream(buf.toByteArray())).readObject();
    }

    // =====================================================================================
    // Scoring a fixed connection table exactly like StochasticGenerator.getScore()
    // =====================================================================================

    static final class Scored {
        long total;   // clamped at 0, as getScore() does
        long raw;     // plain sum of enabled judges
        List<JudgeResult> results = new ArrayList<JudgeResult>();
        List<Judge> judges = new ArrayList<Judge>();
    }

    static final class Scorer {
        final Vector judges;
        final Attic attic = new Attic();

        /** judges: a fresh serialized copy (as the generator receives it); init() is called once, as in initJudges(). */
        Scorer(Molecule mol, Vector judges) {
            this.judges = judges;
            attic.atomSet = DataStructureTools.cloneAtomSet(mol.getAtoms());
            attic.normalized = DataStructureTools.cloneAtomSet(mol.getAtoms());
            for (int f = 0; f < judges.size(); f++) {
                Judge j = (Judge) judges.elementAt(f);
                if (j.getEnabled()) {
                    j.setAttic(attic);
                    j.init();
                }
            }
        }

        Scored score(int[][] bm) {
            DataStructureTools.synchronizeBonds(attic.atomSet, bm);
            Scored s = new Scored();
            long sum = 0;
            for (int f = 0; f < judges.size(); f++) {
                Judge j = (Judge) judges.elementAt(f);
                if (j.getEnabled()) {
                    JudgeResult r = j.evaluate(bm);
                    sum += r.score;
                    s.results.add(r);
                    s.judges.add(j);
                }
            }
            s.raw = sum;
            s.total = sum < 0 ? 0 : sum;
            return s;
        }
    }

    static Json scoredJson(Scored s) {
        Json arr = Json.arr();
        for (int i = 0; i < s.results.size(); i++) {
            JudgeResult r = s.results.get(i);
            arr.add(Json.obj().put("name", s.judges.get(i).name).put("score", r.score)
                    .put("max", s.judges.get(i).getMaxScore()).put("resultMax", r.maxScore)
                    .put("prop", r.propertyValue).put("desc", r.scoreDescription));
        }
        return Json.obj().put("total", s.total).put("raw", s.raw).put("judges", arr);
    }

    // =====================================================================================
    // (b) judge scores on fixed structures
    // =====================================================================================

    static int scores(File sml, String key, String outPath) throws Exception {
        GuiRecipe.Prepared pg = GuiRecipe.prepare(sml, key, GuiRecipe.PROFILE_GUI);
        Go gg = goAction(pg.sd);
        GuiRecipe.Prepared pa = GuiRecipe.prepare(sml, key, GuiRecipe.PROFILE_ALL);
        Go ga = goAction(pa.sd);
        GuiRecipe.Prepared pm = GuiRecipe.prepare(sml, key, GuiRecipe.PROFILE_MANUAL);
        Go gm = goAction(pm.sd);
        try (PrintWriter out = writer(outPath)) {
            out.print(Json.obj().put("t", "header").put("key", key)
                    .put("structures", N_STRUCTURES).put("movesPerK", MOVES_PER_K)
                    .put("guiEnabled", Json.strings(GuiRecipe.enabledJudges(pg.sd)))
                    .put("guiMaxScore", generatorMaxScore(gg.judges))
                    .put("allEnabled", Json.strings(GuiRecipe.enabledJudges(pa.sd)))
                    .put("allMaxScore", generatorMaxScore(ga.judges))
                    .put("manualEnabled", Json.strings(GuiRecipe.enabledJudges(pm.sd)))
                    .put("manualMaxScore", generatorMaxScore(gm.judges)) + "\n");
            Scorer sg = new Scorer(gg.molecule, gg.judges);
            Scorer sa = new Scorer(ga.molecule, ga.judges);
            Scorer sm = new Scorer(gm.molecule, gm.judges);
            StochasticGenerator mutator = new StochasticGenerator(true);
            for (int k = 1; k <= N_STRUCTURES; k++) {
                final int kk = k;
                final Molecule mol = gg.molecule;
                final int moves = MOVES_PER_K * (k - 1);
                final long[] drawsAfterStart = new long[1];
                int[][] bm = withTimeout(new Callable<int[][]>() {
                    public int[][] call() throws Exception {
                        CountingRandom rng = seedRng(kk);
                        int[][] b = startStructure(mol);
                        drawsAfterStart[0] = rng.draws;
                        return randomWalk(mutator, mol, b, moves);
                    }
                }, GEN_TIMEOUT_MS);
                if (bm == null) {
                    out.print(Json.obj().put("t", "skipped").put("k", k)
                            .put("reason", "start structure generation (SingleRandomStructureGenerator) did not finish within "
                                    + (GEN_TIMEOUT_MS / 1000) + " s") + "\n");
                    return 3;
                }
                Scored g = sg.score(bm);
                Scored al = sa.score(bm);
                Scored ma = sm.score(bm);
                // judges evaluate independently: a judge enabled in two profiles must give the same result
                assertSubset(g, al, "gui/all");
                assertSubset(ma, al, "manual/all");
                out.print(Json.obj().put("t", "structure").put("k", k).put("seed", (long) k).put("moves", moves)
                        .put("startDraws", drawsAfterStart[0]).put("hash", hash(bm)).put("ct", ct(bm))
                        .put("gui", scoredJson(g)).put("all", scoredJson(al)).put("manual", scoredJson(ma)) + "\n");
            }
        }
        return 0;
    }

    static void assertSubset(Scored sub, Scored all, String what) {
        for (int i = 0; i < sub.results.size(); i++) {
            int ia = all.judges.indexOf(findByName(all.judges, sub.judges.get(i).name));
            if (ia < 0 || all.results.get(ia).score != sub.results.get(i).score)
                throw new IllegalStateException(what + " judge mismatch for " + sub.judges.get(i).name);
        }
    }

    static Judge findByName(List<Judge> l, String name) {
        for (Judge j : l) if (j.name.equals(name)) return j;
        return null;
    }

    /**
     * The start-structure part of StochasticGenerator.run(): same RNG draws and same bond matrix.
     * Differences that do not affect the bond matrix: run() passes the generator's own attic
     * (generate() then aliases attic.atomSet to the generator's atoms and computes attic.apsp),
     * here a fresh Attic is used.
     */
    static int[][] startStructure(Molecule mol) {
        Atom[] atomSet = DataStructureTools.cloneAtomSet(mol.getAtoms());   // setMolecule()
        SingleRandomStructureGenerator dg = new SingleRandomStructureGenerator();
        dg.setAtomSet(atomSet);
        dg.attic = new Attic();
        atomSet = dg.generate();
        int[][] bm = DataStructureTools.createConnectionTable(atomSet);
        DataStructureTools.synchronizeConnectionTable(bm);
        return bm;
    }

    /** `moves` accepted connected mutations with the generator's own mutate(). */
    static int[][] randomWalk(StochasticGenerator mutator, Molecule mol, int[][] bm, int moves) {
        Atom[] tmp = DataStructureTools.cloneAtomSet(mol.getAtoms());
        int done = 0;
        while (done < moves) {
            int[][] b2 = DataStructureTools.cloneInt2D(bm);
            mutator.mutate(b2);
            DataStructureTools.synchronizeBonds(tmp, b2);
            if (PathUtils.isConnected(tmp)) {
                bm = b2;
                done++;
            }
        }
        return bm;
    }

    // =====================================================================================
    // (c) seeded annealing trajectories
    // =====================================================================================

    static int traj(File sml, String key, String profile, long seed, int steps, String outPath) throws Exception {
        if (!profile.equals(GuiRecipe.PROFILE_GUI) && !profile.equals(GuiRecipe.PROFILE_PAPER) && !profile.equals(GuiRecipe.PROFILE_MANUAL))
            throw new IllegalArgumentException("profile " + profile);
        GuiRecipe.Prepared p = GuiRecipe.prepare(sml, key, profile);
        Go go = goAction(p.sd);
        Go goScorer = goAction(GuiRecipe.prepare(sml, key, profile).sd);
        try (PrintWriter out = writer(outPath)) {
            final StochasticGenerator sg = new StochasticGenerator(true);
            sg.setMolecule(go.molecule);
            sg.setJudges(go.judges);
            sg.setDatasetName(p.sd.getName());
            sg.setSASchedules(go.schedules);
            out.print(Json.obj().put("t", "meta").put("key", key).put("profile", profile).put("seed", seed).put("maxSteps", steps)
                    .put("enabled", Json.strings(GuiRecipe.enabledJudges(p.sd)))
                    .put("maxScore", generatorMaxScore(go.judges)) + "\n");
            // The probe is appended AFTER the serialized copy: it is the last judge, enabled, max score 0,
            // score 0, no random draws. It only observes. (Its empty description adds one "\n" to
            // ScoreSummary.description; descriptions are not recorded here.)
            TraceProbe probe = new TraceProbe(sg, new Scorer(goScorer.molecule, goScorer.judges), out, steps);
            sg.judges.addElement(probe);
            final CountingRandom rng = seedRng(seed);
            probe.rng = rng;
            Boolean ok = withTimeout(new Callable<Boolean>() {
                public Boolean call() { sg.run(); return Boolean.TRUE; }
            }, TRAJ_TIMEOUT_MS);
            String reason;
            if (ok == null) reason = "timeout";
            else {
                probe.finish();
                if (sg.running) reason = "exception_in_run";
                else if (probe.done) reason = "step_limit";
                else if (probe.steps == 0) reason = "no_main_step(Q1)";
                else reason = "finished";
            }
            Json end = Json.obj().put("t", "end").put("reason", reason).put("stepsRecorded", probe.recorded)
                    .put("iteration", sg.iteration).put("rngDraws", rng.draws)
                    .put("checks", probe.checks);
            if (ok != null && sg.running) end.put("crash", crashJson(sg, new Scorer(goScorer.molecule, goScorer.judges)));
            out.print(end + "\n");
            if (probe.error != null) throw new IllegalStateException(probe.error);
            return ok == null ? 4 : 0;
        }
    }

    /** A pseudo-judge that observes the generator from inside getScore(). */
    static final class TraceProbe extends Judge {
        final StochasticGenerator sg;
        final Scorer scorer;
        final PrintWriter out;
        final int maxSteps;
        CountingRandom rng;
        int sched = -1, initCount = 0, steps = 0, recorded = 0;
        long checks = 0;
        boolean inInit = false, startDone = false, done = false;
        String error = null;
        int[][] pendingBm = null;
        long pendingN, pendingCand, pendingDraws;
        double pendingKT;
        static final StackWalker WALKER = StackWalker.getInstance();

        TraceProbe(StochasticGenerator sg, Scorer scorer, PrintWriter out, int maxSteps) {
            super("TraceProbe");
            setHasMaxScore(true);
            setEnabled(true);
            this.sg = sg;
            this.scorer = scorer;
            this.out = out;
            this.maxSteps = maxSteps;
        }

        public void init() {}
        public void calcMaxScore() {}

        static boolean calledFromInitAnnealing() {
            return WALKER.walk(s -> s.anyMatch(f -> f.getMethodName().equals("initAnnealing")
                    && f.getClassName().equals(StochasticGenerator.class.getName())));
        }

        public JudgeResult evaluate(int[][] bm) {
            if (done) return new JudgeResult(0, 0, 0, "");
            if (!startDone) {
                startDone = true;
                out.print(Json.obj().put("t", "start").put("hash", hash(sg.bondMatrix)).put("ct", ct(sg.bondMatrix)) + "\n");
            }
            finishPending();
            if (calledFromInitAnnealing()) {
                if (!inInit) { inInit = true; sched++; initCount = 0; }
                initCount++;
                Scored s = scorer.score(bm);
                out.print(Json.obj().put("t", "init").put("sched", sched).put("i", initCount)
                        .put("cand", s.total).put("draws", rng.draws).put("hash", hash(bm)) + "\n");
            } else {
                if (inInit) {
                    inInit = false;
                    out.print(Json.obj().put("t", "schedule").put("sched", sched).put("start_kT", sg.start_kT)
                            .put("plateauSteps", sg.plateauSteps).put("maxUphillSteps", sg.maxUphillSteps)
                            .put("convergenceStopCount", sg.convergenceStopCount).put("coolingRate", sg.coolingRate) + "\n");
                }
                if (steps >= maxSteps) {
                    sg.end();
                    done = true;
                    return new JudgeResult(0, 0, 0, "");
                }
                steps++;
                pendingBm = bm;
                pendingN = sg.iteration + 1;
                pendingKT = sg.current_kT;
                pendingCand = scorer.score(bm).total;
                pendingDraws = rng.draws;
                if (pendingN != steps && error == null) error = "iteration " + pendingN + " != step " + steps;
            }
            return new JudgeResult(0, 0, 0, "");
        }

        void finishPending() {
            if (pendingBm == null) return;
            if (sg.recentScore.score != pendingCand && error == null)
                error = "independent score " + pendingCand + " != generator score " + sg.recentScore.score + " at step " + pendingN;
            checks++;
            boolean acc = sg.bondMatrix == pendingBm;
            Json o = Json.obj().put("t", "step").put("n", pendingN).put("kT", pendingKT).put("cand", pendingCand)
                    .put("acc", acc).put("cur", sg.lastScore.score).put("best", sg.bestScore.score)
                    .put("hash", hash(sg.bondMatrix)).put("draws", pendingDraws)
                    .put("nres", sg.structureGeneratorResult.structures.size());
            if (pendingN % 500 == 0) o.put("ct", ct(sg.bondMatrix));
            out.print(o + "\n");
            recorded++;
            pendingBm = null;
        }

        void finish() {
            if (!done) finishPending();
        }
    }

    // =====================================================================================
    // (d) complete runs
    // =====================================================================================

    /**
     * StochasticGenerator with a deterministic iteration cap: the first mutate() call after
     * `iteration` reached the cap calls end() (the Stop button). mutate() itself is the
     * inherited one, so the RNG stream and every decision up to that point are unchanged.
     * The step in progress completes, then the main loop exits (see run()).
     */
    static final class CappedGenerator extends StochasticGenerator {
        final long cap;
        volatile boolean capped = false;
        CappedGenerator(long cap) { super(true); this.cap = cap; }
        @Override protected void mutate(int[][] bm) {
            if (!capped && iteration >= cap) { capped = true; end(); }
            super.mutate(bm);
        }
    }

    static int full(File sml, String key, String profile, long seed, long iterCap, long capSeconds, File refsFile,
                    String outPath, String timingPath) throws Exception {
        if (!profile.equals(GuiRecipe.PROFILE_GUI) && !profile.equals(GuiRecipe.PROFILE_PAPER) && !profile.equals(GuiRecipe.PROFILE_MANUAL))
            throw new IllegalArgumentException("profile " + profile);
        GuiRecipe.Prepared p = GuiRecipe.prepare(sml, key, profile);
        Go go = goAction(p.sd);
        Go goScorer = goAction(GuiRecipe.prepare(sml, key, profile).sd);
        CappedGenerator sg = new CappedGenerator(iterCap);
        sg.setMolecule(go.molecule);
        sg.setJudges(go.judges);
        sg.setDatasetName(p.sd.getName());
        sg.setSASchedules(go.schedules);
        CountingRandom rng = seedRng(seed);
        long t0 = System.nanoTime();
        sg.execute();                       // Thread.start(), as StructureGeneratorServer.executeTask()
        sg.join(capSeconds * 1000L);
        boolean timeout = sg.isAlive();
        if (timeout) { sg.end(); sg.join(); }
        long wallMs = (System.nanoTime() - t0) / 1_000_000L;

        StructureGeneratorResult r = (StructureGeneratorResult) sg.getResult();
        String status;
        if (timeout) status = "timeout";
        else if (sg.running) status = "exception_in_run";
        else if (sg.capped) status = "iteration_cap";
        else if (r.size() == 0) status = "no_result(Q1)";
        else status = "finished";

        try (PrintWriter timing = new PrintWriter(new OutputStreamWriter(new FileOutputStream(timingPath, true), StandardCharsets.UTF_8))) {
            timing.print(Json.obj().put("key", key).put("profile", profile).put("seed", seed).put("status", status).put("wallMs", wallMs)
                    .put("iterations", sg.iteration).put("iterationCap", iterCap).put("wallCapSeconds", capSeconds)
                    .put("java", System.getProperty("java.version")) + "\n");
        }
        try (PrintWriter out = writer(outPath)) {
            if (timeout) {
                // a harness failure, not golden data: the wall-clock safety net is far above the iteration cap
                out.print(Json.obj().put("t", "run").put("key", key).put("profile", profile).put("seed", seed).put("status", status)
                        .put("note", "wall-clock safety cap of " + capSeconds + " s reached before the iteration cap; not golden data") + "\n");
                return 5;
            }
            RefCheck refCheck = RefCheck.of(refsFile, key, go.molecule);
            Scorer scorer = new Scorer(goScorer.molecule, goScorer.judges);
            Json results = Json.arr();
            Json resultsEqualRef = Json.arr();
            for (int i = 0; i < r.size(); i++) {
                int[][] bm = ctFromMDL((String) r.structures.elementAt(i));
                ScoreSummary ss = (ScoreSummary) r.scoreSummaries.elementAt(i);
                Scored re = scorer.score(bm);
                if (re.total != ss.score) throw new IllegalStateException("rescore mismatch result " + i);
                results.add(Json.obj().put("i", i).put("score", ss.score).put("hash", hash(bm)).put("ct", ct(bm))
                        .put("description", ss.description));
                if (refCheck.mappable && refCheck.equal(bm)) resultsEqualRef.add(i);
            }
            Json rec = Json.obj().put("t", "run").put("key", key).put("profile", profile).put("seed", seed).put("status", status)
                    .put("iterationCap", iterCap)
                    .put("termination", termination(status, sg, r))
                    .put("enabled", Json.strings(GuiRecipe.enabledJudges(p.sd)))
                    .put("iterations", sg.iteration).put("rngDraws", rng.draws)
                    .put("maxScore", sg.maxScore).put("start_kT", sg.start_kT).put("final_kT", sg.current_kT)
                    .put("plateauSteps", sg.plateauSteps).put("maxUphillSteps", sg.maxUphillSteps);
            Boolean bestEq = null;
            if (r.size() > 0) {
                int[][] best = ctFromMDL(sg.bestStructure);
                Scored bs = scorer.score(best);
                rec.put("bestScore", sg.bestScore.score).put("bestHash", hash(best)).put("bestCt", ct(best))
                        .put("bestJudges", scoredJson(bs)).put("bestMDL", sg.bestStructure);
                if (refCheck.defined) bestEq = refCheck.mappable && refCheck.equal(best);
            } else {
                rec.putNull("bestScore");
            }
            if (sg.running) rec.put("crash", crashJson(sg, scorer));
            Json refJ = Json.obj();
            if (!refCheck.defined) refJ.putNull("name");
            else refJ.put("name", refCheck.ref.name).put("status", refCheck.ref.status).put("mappable", refCheck.mappable);
            if (bestEq == null) refJ.putNull("bestEqualsReference"); else refJ.put("bestEqualsReference", bestEq.booleanValue());
            refJ.put("resultsEqualReference", refCheck.defined ? resultsEqualRef : null);
            rec.put("reference", refJ).put("nResults", r.size()).put("results", results);
            out.print(rec + "\n");
        }
        return 0;
    }

    /**
     * Why a run ended: "max_score_reached" (bestScore >= maxScore at a convergence check),
     * "reheat_limit" (converged below maxScore after the reheat factor reached 1.0),
     * or the status itself for every other case.
     */
    static String termination(String status, StochasticGenerator sg, StructureGeneratorResult r) {
        if (!status.equals("finished")) return status;
        return sg.bestScore.score >= sg.maxScore ? "max_score_reached" : "reheat_limit";
    }

    /** Constitution test of labelled structures against the dataset's reference structure (if any). */
    static final class RefCheck {
        boolean defined, mappable;
        RefStructures.Ref ref;
        RefStructures.Graph g;
        String[] el;
        int[] h;

        static RefCheck of(File refsFile, String key, Molecule mol) throws IOException {
            RefCheck c = new RefCheck();
            c.ref = RefStructures.load(refsFile).get(GuiRecipe.baseKey(key));
            c.defined = c.ref != null;
            Atom[] at = mol.getAtoms();
            c.el = new String[at.length];
            c.h = new int[at.length];
            for (int f = 0; f < at.length; f++) { c.el[f] = at[f].symbol; c.h[f] = at[f].HCount; }
            if (c.defined) {
                c.g = RefStructures.parseSmiles(c.ref.smiles);
                c.mappable = RefStructures.classes(c.g.el, c.g.h).equals(RefStructures.classes(c.el, c.h));
            }
            return c;
        }

        boolean equal(int[][] bm) {
            return RefStructures.sameConstitution(el, h, bm, g.el, g.h, g.bm);
        }
    }

    // =====================================================================================
    // (e) reference structures: best labelling of the literature constitution per profile
    // =====================================================================================

    static final String[] REF_PROFILES = {GuiRecipe.PROFILE_GUI, GuiRecipe.PROFILE_PAPER, GuiRecipe.PROFILE_ALL, GuiRecipe.PROFILE_MANUAL};
    // search parameters; the system properties exist only to re-check the search with more effort
    static final long REF_SEARCH_SEED = Long.getLong("gm.refSeed", 2001L);
    static final int REF_RESTARTS = Integer.getInteger("gm.refRestarts", 8);
    static final int REF_STEPS = Integer.getInteger("gm.refSteps", 4000);

    static int ref(File sml, String key, File refsFile, String outPath) throws Exception {
        RefStructures.Ref rf = RefStructures.load(refsFile).get(GuiRecipe.baseKey(key));
        try (PrintWriter out = writer(outPath)) {
            Json head = Json.obj().put("t", "reference").put("key", key);
            if (rf == null) {
                out.print(head.put("defined", false).put("note", "no reference structure: compound not identified") + "\n");
                return 0;
            }
            RefStructures.Graph g = RefStructures.parseSmiles(rf.smiles);
            GuiRecipe.Prepared p0 = GuiRecipe.prepare(sml, key, GuiRecipe.PROFILE_GUI);
            Atom[] at = p0.sd.setOfNodes;
            String[] el = new String[at.length];
            int[] h = new int[at.length];
            for (int f = 0; f < at.length; f++) { el[f] = at[f].symbol; h[f] = at[f].HCount; }
            List<String> rc = RefStructures.classes(g.el, g.h), dc = RefStructures.classes(el, h);
            boolean mappable = rc.equals(dc);
            List<int[][]> variants = RefStructures.kekuleVariants(g);
            Json refAtoms = Json.arr();
            for (int a = 0; a < g.n(); a++) refAtoms.add(Json.obj().put("i", a).put("el", g.el[a]).put("h", g.h[a]));
            head.put("defined", true).put("name", rf.name).put("status", rf.status).put("source", rf.source)
                    .put("smiles", rf.smiles).put("nAtoms", g.n()).put("refAtoms", refAtoms).put("refCt", ct(g.bm))
                    .put("kekuleVariants", variants.size()).put("mappable", mappable)
                    .put("classDifference", RefStructures.classDiff(rc, dc));
            out.print(head + "\n");
            if (!mappable) return 0;
            for (String profile : REF_PROFILES) {
                GuiRecipe.Prepared p = GuiRecipe.prepare(sml, key, profile);
                Go go = goAction(p.sd);
                Scorer scorer = new Scorer(go.molecule, go.judges);
                long max = generatorMaxScore(go.judges);
                Search best = searchLabelling(g, variants, el, h, scorer);
                int[][] bm = RefStructures.place(variants.get(best.variant), best.map);
                Scored sc = scorer.score(bm);
                if (sc.raw != best.raw) throw new IllegalStateException("labelling search rescore mismatch");
                out.print(Json.obj().put("t", "best").put("profile", profile)
                        .put("enabled", Json.strings(GuiRecipe.enabledJudges(p.sd)))
                        .put("maxScore", max).put("total", sc.total).put("raw", sc.raw).put("atMax", sc.total >= max)
                        .put("kekule", best.variant).put("map", Json.ints(best.map)).put("hash", hash(bm)).put("ct", ct(bm))
                        .put("judges", scoredJson(sc))
                        .put("search", Json.obj().put("seed", REF_SEARCH_SEED).put("restarts", REF_RESTARTS)
                                .put("stepsPerRestart", REF_STEPS).put("evaluations", best.evaluations)
                                .put("exceptions", best.exceptions).put("firstException", best.firstException)) + "\n");
            }
        }
        return 0;
    }

    static final class Search {
        int variant;
        int[] map;
        long raw = Long.MIN_VALUE;
        long evaluations, exceptions;
        String firstException;
    }

    /**
     * Maximises the raw score over all labellings of the reference constitution: bijections
     * reference atom -> dataset atom that keep element and H count, times the Kekule variants.
     * Simulated annealing on pair swaps within a class (fixed seed, REF_RESTARTS x REF_STEPS per
     * variant), then steepest-ascent over all swaps until no swap improves. A labelling whose
     * scoring throws (FeatureJudge ring finder) counts as -infinity and is recorded.
     * The result is the best labelling found (a lower bound of the true maximum).
     */
    static Search searchLabelling(RefStructures.Graph g, List<int[][]> variants, String[] el, int[] h, Scorer scorer) {
        int n = g.n();
        Map<String, List<Integer>> dataByClass = new TreeMap<String, List<Integer>>();
        for (int i = 0; i < n; i++) dataByClass.computeIfAbsent(RefStructures.cls(el[i], h[i]), k -> new ArrayList<Integer>()).add(i);
        Map<String, List<Integer>> refByClass = new TreeMap<String, List<Integer>>();
        for (int a = 0; a < n; a++) refByClass.computeIfAbsent(RefStructures.cls(g.el[a], g.h[a]), k -> new ArrayList<Integer>()).add(a);
        List<int[]> swapPairs = new ArrayList<int[]>();      // pairs of reference atoms in one class
        for (List<Integer> l : refByClass.values())
            for (int x = 0; x < l.size(); x++)
                for (int y = x + 1; y < l.size(); y++) swapPairs.add(new int[]{l.get(x), l.get(y)});
        Random rnd = new Random(REF_SEARCH_SEED);
        Search best = new Search();
        for (int v = 0; v < variants.size(); v++) {
            int[][] refBm = variants.get(v);
            for (int r = 0; r < REF_RESTARTS; r++) {
                int[] map = new int[n];
                for (Map.Entry<String, List<Integer>> e : refByClass.entrySet()) {
                    List<Integer> targets = new ArrayList<Integer>(dataByClass.get(e.getKey()));
                    Collections.shuffle(targets, rnd);
                    for (int x = 0; x < e.getValue().size(); x++) map[e.getValue().get(x)] = targets.get(x);
                }
                long cur = eval(refBm, map, scorer, best);
                consider(best, v, map, cur);
                if (swapPairs.isEmpty()) continue;
                double t = 300.0, alpha = Math.pow(1.0 / 300.0, 1.0 / REF_STEPS);
                for (int s = 0; s < REF_STEPS; s++, t *= alpha) {
                    int[] pr = swapPairs.get(rnd.nextInt(swapPairs.size()));
                    swap(map, pr);
                    long sc = eval(refBm, map, scorer, best);
                    if (sc >= cur || (sc != Long.MIN_VALUE && rnd.nextDouble() < Math.exp((sc - cur) / t))) {
                        cur = sc;
                        consider(best, v, map, cur);
                    } else swap(map, pr);
                }
            }
        }
        // steepest ascent from the best labelling found
        int[] map = best.map.clone();
        int[][] refBm = variants.get(best.variant);
        long cur = best.raw;
        boolean improved = true;
        while (improved && !swapPairs.isEmpty()) {
            improved = false;
            int[] bestPair = null;
            long bestSc = cur;
            for (int[] pr : swapPairs) {
                swap(map, pr);
                long sc = eval(refBm, map, scorer, best);
                swap(map, pr);
                if (sc > bestSc) { bestSc = sc; bestPair = pr; }
            }
            if (bestPair != null) { swap(map, bestPair); cur = bestSc; improved = true; consider(best, best.variant, map, cur); }
        }
        return best;
    }

    static void swap(int[] map, int[] pr) { int x = map[pr[0]]; map[pr[0]] = map[pr[1]]; map[pr[1]] = x; }

    static void consider(Search best, int v, int[] map, long sc) {
        if (sc > best.raw) { best.raw = sc; best.variant = v; best.map = map.clone(); }
    }

    static long eval(int[][] refBm, int[] map, Scorer scorer, Search stats) {
        stats.evaluations++;
        try {
            return scorer.score(RefStructures.place(refBm, map)).raw;
        } catch (RuntimeException e) {
            stats.exceptions++;
            if (stats.firstException == null) stats.firstException = e.getClass().getName() + ": " + e.getMessage();
            return Long.MIN_VALUE;
        }
    }

    /**
     * run() caught an exception (it prints the stack trace and leaves running == true).
     * The candidate being scored is still in the generator's attic.atomSet (synchronizeBonds is
     * called before getScore); re-scoring it reproduces the exception, whose class/message/top
     * frame are recorded.
     */
    static Json crashJson(StochasticGenerator sg, Scorer scorer) {
        int[][] bm = DataStructureTools.createConnectionTable(sg.attic.atomSet);
        DataStructureTools.synchronizeConnectionTable(bm);
        Json o = Json.obj().put("iteration", sg.iteration).put("candidateHash", hash(bm)).put("candidateCt", ct(bm))
                .put("currentHash", hash(sg.bondMatrix));
        try {
            scorer.score(bm);
            o.putNull("exception");
        } catch (RuntimeException e) {
            StackTraceElement top = e.getStackTrace().length > 0 ? e.getStackTrace()[0] : null;
            o.put("exception", e.getClass().getName()).put("message", e.getMessage())
                    .put("at", top == null ? null : top.getClassName() + "." + top.getMethodName() + ":" + top.getLineNumber());
        }
        return o;
    }

    static int[][] ctFromMDL(String mdl) {
        Atom[] atoms = ChemFileUtils.readMDLMolFile(new BufferedReader(new StringReader(mdl)));
        int[][] bm = DataStructureTools.createConnectionTable(atoms);
        DataStructureTools.synchronizeConnectionTable(bm);
        return bm;
    }

    // =====================================================================================
    // utilities
    // =====================================================================================

    /** java.util.Random with a draw counter; same sequence as new Random(seed). */
    static final class CountingRandom extends Random {
        long draws = 0;
        CountingRandom(long seed) { super(seed); }
        @Override public double nextDouble() { draws++; return super.nextDouble(); }
    }

    /** Rng.seed(seed), then swap in an equivalent counting instance. */
    static CountingRandom seedRng(long seed) throws Exception {
        Rng.seed(seed);
        CountingRandom c = new CountingRandom(seed);
        set(null, Rng.class, "random", c);
        return c;
    }

    /** Bond list: [i,j,order] for i<j, order != 0, row-major. */
    static Json ct(int[][] bm) {
        Json arr = Json.arr();
        for (int i = 0; i < bm.length; i++)
            for (int j = i + 1; j < bm.length; j++)
                if (bm[i][j] != 0) arr.add(Json.arr().add(i).add(j).add(bm[i][j]));
        return arr;
    }

    /** SHA-1 (lowercase hex) of "i,j,o;i,j,o;..." over the same bond list as ct(), joined by ';'. */
    static String hash(int[][] bm) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bm.length; i++)
            for (int j = i + 1; j < bm.length; j++)
                if (bm[i][j] != 0) {
                    if (sb.length() > 0) sb.append(';');
                    sb.append(i).append(',').append(j).append(',').append(bm[i][j]);
                }
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(sb.toString().getBytes(StandardCharsets.US_ASCII));
            StringBuilder h = new StringBuilder();
            for (byte b : d) h.append(String.format("%02x", b & 0xff));
            return h.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static PrintWriter writer(String path) throws IOException {
        return new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(path), StandardCharsets.UTF_8)));
    }

    /** Runs c in a daemon thread; null on timeout (the thread keeps running until System.exit). */
    static <T> T withTimeout(Callable<T> c, long ms) throws Exception {
        final Object[] res = new Object[1];
        final Throwable[] err = new Throwable[1];
        Thread t = new Thread(() -> {
            try { res[0] = c.call(); } catch (Throwable e) { err[0] = e; }
        });
        t.setDaemon(true);
        t.start();
        t.join(ms);
        if (t.isAlive()) return null;
        if (err[0] instanceof Exception) throw (Exception) err[0];
        if (err[0] != null) throw new RuntimeException(err[0]);
        @SuppressWarnings("unchecked") T r = (T) res[0];
        return r;
    }
}
