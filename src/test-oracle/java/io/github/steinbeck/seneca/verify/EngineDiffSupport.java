package io.github.steinbeck.seneca.verify;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.chem.StandardElements;
import io.github.steinbeck.seneca.judges.FeatureJudge;
import io.github.steinbeck.seneca.judges.HHCOSYJudge;
import io.github.steinbeck.seneca.judges.HMBCJudge;
import io.github.steinbeck.seneca.judges.HOSECodeJudge;
import io.github.steinbeck.seneca.judges.HeteroBondsJudge;
import io.github.steinbeck.seneca.judges.HybridizationJudge;
import io.github.steinbeck.seneca.judges.Judge;
import io.github.steinbeck.seneca.judges.JudgeSet;
import io.github.steinbeck.seneca.judges.ScoreSummary;
import io.github.steinbeck.seneca.judges.SymmetryJudge;
import io.github.steinbeck.seneca.model.AnnealingSchedule;
import io.github.steinbeck.seneca.structgen.EngineLog;
import io.github.steinbeck.seneca.structgen.RunInput;
import io.github.steinbeck.seneca.structgen.RunOptions;
import io.github.steinbeck.seneca.structgen.RunResult;
import io.github.steinbeck.seneca.structgen.StochasticGenerator;

/**
 * Verifier "fidelity": builds one synthetic generator input twice (legacy 2001 classes and the port)
 * and runs both engines with the same seed, so that whole runs can be compared on inputs the golden
 * master never exercises (other elements, every judge option, several schedules, stop by cap).
 */
final class EngineDiffSupport {

    private EngineDiffSupport() {
    }

    /** Declarative input: atoms, judge configuration, schedules. */
    static final class Spec {
        String name = "verify";
        String[] sym;
        int[] h;
        double[] hoseShifts;                 // HOSECodeJudge (indexed by atom), null = disabled
        int[][] ringScores;                  // FeatureJudge {size, score}, null = none
        boolean hetHet;                      // FeatureJudge het-het penalty
        int hetHetPenalty = -5;
        boolean featureEnabled;
        int[][] hmbcPairs;                   // {from, to}: assignment[from][to][to]; null = disabled
        int[] hmbcScores;                    // null = default
        int[][] cosyPairs;
        boolean[][] symmetryClasses;         // null = disabled
        int symmetryScore = 500;
        boolean hybridization;               // enabled with sp3 1.0 on every C
        boolean heteroBonds;                 // enabled with every C flagged
        List<double[]> schedules = new ArrayList<>(); // {p0, uphill, plateau, cooling}
        long seed = 1;
        long cap = 0;
        boolean stopBeforeRun;              // end() called on the fresh generator before run()
    }

    /** Comparable outcome of one run. */
    record Outcome(long iterations, long draws, long maxScore, double startKT, double finalKT, long plateau,
                   long uphill, List<String> structures, List<Long> scores, List<String> descriptions,
                   String exception) {
        String summary() {
            return "it=" + iterations + " draws=" + draws + " max=" + maxScore + " kT0=" + startKT + " kT=" + finalKT
                    + " plateau=" + plateau + " uphill=" + uphill + " results=" + structures.size() + " scores=" + scores
                    + " exc=" + exception;
        }
    }

    // ------------------------------------------------------------------ port

    static HeavyAtom[] portAtoms(Spec s) {
        HeavyAtom[] a = new HeavyAtom[s.sym.length];
        for (int i = 0; i < a.length; i++) {
            a[i] = new HeavyAtom(s.sym[i], i);
            a[i].hCount = s.h[i];
            a[i].maxBondOrder = StandardElements.getMaxBondOrder(s.sym[i]);
            a[i].maxBondOrderSum = StandardElements.getMaxBondOrderSum(s.sym[i]);
        }
        return a;
    }

    static List<Judge> portJudges(Spec s) {
        int n = s.sym.length;
        HHCOSYJudge cosy = new HHCOSYJudge();
        HMBCJudge hmbc = new HMBCJudge();
        HeteroBondsJudge het = new HeteroBondsJudge();
        SymmetryJudge sym = new SymmetryJudge();
        HybridizationJudge hyb = new HybridizationJudge();
        HOSECodeJudge hose = new HOSECodeJudge();
        FeatureJudge feat = new FeatureJudge();
        if (s.cosyPairs != null) {
            cosy.assignment = new boolean[n][n][n];
            for (int[] p : s.cosyPairs) cosy.assignment[p[0]][p[1]][p[1]] = true;
            cosy.setEnabled(true);
        }
        if (s.hmbcPairs != null) {
            hmbc.assignment = new boolean[n][n][n];
            for (int[] p : s.hmbcPairs) hmbc.assignment[p[0]][p[1]][p[1]] = true;
            if (s.hmbcScores != null) hmbc.setScores(s.hmbcScores.clone());
            hmbc.setEnabled(true);
        }
        if (s.heteroBonds) {
            het.isHeteroBonded = new boolean[n];
            for (int i = 0; i < n; i++) het.isHeteroBonded[i] = s.sym[i].equals("C");
            het.setEnabled(true);
        }
        if (s.symmetryClasses != null) {
            for (boolean[] c : s.symmetryClasses) sym.addSymmetryClass(c.clone());
            sym.setScore(s.symmetryScore);
            sym.setEnabled(true);
        }
        if (s.hybridization) {
            hyb.initScoreArray(n);
            for (int i = 0; i < n; i++) if (s.sym[i].equals("C")) hyb.addHybridizationScore(i + 1, HybridizationJudge.SP3, 1.0);
            hyb.setEnabled(true);
        }
        if (s.hoseShifts != null) {
            hose.setCarbonShifts(s.hoseShifts.clone());
            hose.setEnabled(true);
        }
        if (s.featureEnabled) {
            if (s.ringScores != null) for (int[] r : s.ringScores) feat.addRingSizeScore(r[0], r[1]);
            feat.setHetHetPenaltyEnabled(s.hetHet);
            feat.setHetHetPenalty(s.hetHetPenalty);
            feat.setEnabled(true);
        }
        List<Judge> l = List.of(cosy, hmbc, het, sym, hyb, hose, feat);
        List<Judge> all = new ArrayList<>(l);
        for (Judge j : all) if (j.getEnabled()) j.calcMaxScore();     // goAction
        return JudgeSet.deepCopy(all);
    }

    static Outcome runPort(Spec s, LegacyCompat compat) {
        List<AnnealingSchedule> sched = new ArrayList<>();
        for (double[] d : s.schedules) sched.add(new AnnealingSchedule(d[0], (long) d[1], (long) d[2], d[3]));
        RunInput in = new RunInput(portAtoms(s), portJudges(s), sched, s.name);
        RunOptions o = new RunOptions(s.seed, compat, s.cap, null, EngineLog.NONE);
        StochasticGenerator g = new StochasticGenerator(in, o);
        if (s.stopBeforeRun) g.end();
        RunResult r = g.run();
        List<String> st = new ArrayList<>();
        List<Long> sc = new ArrayList<>();
        List<String> de = new ArrayList<>();
        for (int i = 0; i < r.result().size(); i++) {
            st.add(r.result().structures.get(i));
            ScoreSummary ss = r.result().scoreSummaries.get(i);
            sc.add(ss.score);
            de.add(ss.description);
        }
        return new Outcome(r.iterations(), r.rngDraws(), r.maxScore(), r.startKT(), r.finalKT(), r.plateauSteps(),
                r.maxUphillSteps(), st, sc, de, r.exception() == null ? null : r.exception().getClass().getSimpleName());
    }

    // ------------------------------------------------------------------ legacy

    /** Runs the 2001 engine (yield-free loader, see {@link LegacyDriver}). */
    @SuppressWarnings("unchecked")
    static Outcome runLegacy(Spec s) throws Exception {
        Map<String, Object> in = new HashMap<>();
        in.put("name", s.name);
        in.put("sym", s.sym);
        in.put("h", s.h);
        in.put("hoseShifts", s.hoseShifts);
        in.put("ringScores", s.ringScores);
        in.put("featureEnabled", s.featureEnabled);
        in.put("hetHet", s.hetHet);
        in.put("hetHetPenalty", s.hetHetPenalty);
        in.put("hmbcPairs", s.hmbcPairs);
        in.put("hmbcScores", s.hmbcScores);
        in.put("cosyPairs", s.cosyPairs);
        in.put("symmetryClasses", s.symmetryClasses);
        in.put("symmetryScore", s.symmetryScore);
        in.put("hybridization", s.hybridization);
        in.put("heteroBonds", s.heteroBonds);
        in.put("schedules", s.schedules.toArray(new double[0][]));
        in.put("seed", s.seed);
        in.put("cap", s.cap);
        in.put("stopBeforeRun", s.stopBeforeRun);
        Map<String, Object> o = LegacyDriver.runIsolated(in);
        return new Outcome((Long) o.get("iterations"), (Long) o.get("draws"), (Long) o.get("maxScore"),
                (Double) o.get("startKT"), (Double) o.get("finalKT"), (Long) o.get("plateau"), (Long) o.get("uphill"),
                (List<String>) o.get("structures"), (List<Long>) o.get("scores"), (List<String>) o.get("descriptions"),
                (String) o.get("exception"));
    }
}
