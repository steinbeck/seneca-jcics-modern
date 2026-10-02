package io.github.steinbeck.seneca.verify;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.chem.StandardElements;
import io.github.steinbeck.seneca.golden.GoldenFiles;
import io.github.steinbeck.seneca.golden.GoldenFixtures;
import io.github.steinbeck.seneca.golden.Hashes;
import io.github.steinbeck.seneca.golden.RunGolden;
import io.github.steinbeck.seneca.model.AnnealingSchedule;
import io.github.steinbeck.seneca.structgen.RunController;
import io.github.steinbeck.seneca.structgen.RunInput;
import io.github.steinbeck.seneca.structgen.RunListener;
import io.github.steinbeck.seneca.structgen.RunOptions;
import io.github.steinbeck.seneca.structgen.RunResult;
import io.github.steinbeck.seneca.structgen.RunSlot;
import io.github.steinbeck.seneca.structgen.StepContext;
import io.github.steinbeck.seneca.structgen.StepObserver;
import io.github.steinbeck.seneca.structgen.StochasticGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Policy verifier, round 2. The first test is a property check of policy point 2 beyond the golden
 * seeds; the others document gaps found in round 2 (they fail until the gap is fixed).
 */
class PolicyRound2VerifyTest {

    /** Every main-loop step (draws before the Metropolis draw, candidate, current, scores) as a digest. */
    private static final class Trace implements StepObserver {
        final List<String> steps = new ArrayList<>();

        @Override
        public void beforeAcceptance(StepContext ctx) {
            steps.add(ctx.iteration() + "|" + ctx.draws() + "|" + Hashes.hash(ctx.candidate()) + "|" + ctx.candidateScore()
                    + "|" + ctx.lastScore() + "|" + ctx.bestScore() + "|" + ctx.currentKT());
        }

        @Override
        public void onScheduleInitialized(int schedule, double startKT, long plateauSteps, long maxUphillSteps,
                                          long convergenceStopCount, double coolingRate) {
            steps.add("schedule " + schedule + " " + startKT + " " + plateauSteps + " " + maxUphillSteps);
        }
    }

    private static String digest(RunResult r) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append(r.iterations()).append('|').append(r.rngDraws()).append('|').append(r.maxScore()).append('|')
                .append(r.startKT()).append('|').append(r.finalKT()).append('|').append(r.plateauSteps()).append('|')
                .append(r.maxUphillSteps()).append('|').append(r.bestScore().score).append('|').append(r.bestScore().description)
                .append('|').append(r.bestStructure()).append('|').append(r.goldenStatus()).append('|').append(r.goldenTermination());
        for (int i = 0; i < r.result().size(); i++) {
            sb.append('|').append(r.result().structures.get(i)).append('|').append(r.result().scoreSummaries.get(i).score)
                    .append('|').append(r.result().scoreSummaries.get(i).description);
        }
        if (r.currentStructure() != null) sb.append('|').append(Hashes.hash(r.currentStructure()));
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(sb.toString().getBytes("UTF-8")));
    }

    private static RunInput withSchedules(RunInput in, List<AnnealingSchedule> schedules) {
        RunInput c = in.copy();
        return new RunInput(c.atoms(), new ArrayList<>(c.judges()), schedules, c.datasetName());
    }

    /**
     * Policy point 2 as a property: on non-golden seeds, every dataset and profile, single and
     * multi-schedule (fast schedules, so that reheats, aborts and the second schedule are exercised),
     * DEFAULT is identical to STRICT - every step and the whole result - wherever STRICT does not fail;
     * where STRICT fails (ring-finder crash, Q1 leaving the run without a result) DEFAULT is identical up to
     * the failure point.
     */
    @Test
    void defaultEqualsStrictOnEveryNonFailingRunBeyondTheGoldenSeeds() throws Exception {
        int compared = 0, failingStrict = 0;
        List<String> mismatches = new ArrayList<>();
        for (String key : GoldenFiles.keys()) {
            for (String profile : List.of("gui", "paper", "all", "manual")) {
                RunInput base = GoldenFixtures.runInput(key, profile);
                List<List<AnnealingSchedule>> variants = List.of(
                        List.of(new AnnealingSchedule()),
                        List.of(new AnnealingSchedule(0.8, 5, 20, 0.5), new AnnealingSchedule(0.5, 10, 30, 0.7),
                                new AnnealingSchedule(0.9, 3, 10, 0.3)));
                for (int v = 0; v < variants.size(); v++) {
                    for (long seed = 101; seed <= 103; seed++) {
                        Trace ts = new Trace(), td = new Trace();
                        RunResult s = new StochasticGenerator(withSchedules(base, variants.get(v)),
                                RunOptions.golden(seed).withIterationCap(3000).withObserver(ts)).run();
                        RunResult d = new StochasticGenerator(withSchedules(base, variants.get(v)),
                                RunOptions.golden(seed).withCompat(LegacyCompat.DEFAULT).withIterationCap(3000).withObserver(td)).run();
                        String id = key + "/" + profile + "/v" + v + "/seed" + seed;
                        // Q1 is a legacy failure only where it leaves the run without any result (DV-04);
                        // an empty schedule followed by others, or after a result, is normal legacy behaviour
                        boolean q1Failure = firstEmptySchedule(ts.steps) >= 0 && s.result().size() == 0;
                        boolean strictFailed = s.exception() != null || q1Failure;
                        if (d.exception() != null) mismatches.add(id + ": DEFAULT threw " + d.exception());
                        if (!strictFailed) {
                            compared++;
                            if (!ts.steps.equals(td.steps)) mismatches.add(id + ": step trace differs");
                            if (!digest(s).equals(digest(d))) mismatches.add(id + ": result differs");
                        } else {
                            failingStrict++;
                            // identical up to the failure point: the STRICT crash happens while scoring, before the
                            // candidate is traced; a Q1 failure ends the STRICT trace with the last schedule record
                            int prefix = ts.steps.size();
                            if (td.steps.size() < prefix || !ts.steps.subList(0, prefix).equals(td.steps.subList(0, prefix)))
                                mismatches.add(id + ": DEFAULT differs before the STRICT failure (" + s.goldenStatus() + ")");
                        }
                    }
                }
            }
        }
        assertTrue(compared > 150, "too few comparable runs: " + compared + " (STRICT failed in " + failingStrict + ")");
        assertEquals(List.of(), mismatches);
    }

    // ------------------------------------------------------------------ round-2 gaps (fail until fixed)

    /**
     * Gap: the generator "takes ownership" of the input's judges without copying them, and
     * {@code initJudges()} points every judge's attic at its own run. One {@link RunInput} handed to two
     * slots (e.g. "Start as selected" with two rows selected, if the GUI builds the input once) makes the
     * runs score each other's candidates: silently wrong scores. Legacy gave every server its own
     * serialized copy. Either copy in the generator or reject an input that is already in use.
     */
    @Test
    void oneRunInputStartedInTwoSlotsMustNotCorruptEitherRun() throws Exception {
        RunGolden g1 = golden("pinene", "paper", 1), g2 = golden("pinene", "paper", 2);
        RunInput shared = GoldenFixtures.runInput("pinene", "paper");
        RunController rc = new RunController();
        CountDownLatch done = new CountDownLatch(2);
        java.util.Map<String, RunResult> fin = new java.util.concurrent.ConcurrentHashMap<>();
        rc.addListener(new RunListener() {
            @Override
            public void onFinished(RunSlot slot, RunResult result) {
                fin.put(slot.id(), result);
                done.countDown();
            }
        });
        RunSlot a = rc.addSlot(), b = rc.addSlot();
        try {
            rc.start(a, shared, RunOptions.application().withSeed(1L).withIterationCap(RunGolden.ITERATION_CAP));
            rc.start(b, shared, RunOptions.application().withSeed(2L).withIterationCap(RunGolden.ITERATION_CAP));
        } catch (IllegalArgumentException | IllegalStateException rejected) {
            rc.shutdown();
            return; // rejecting a shared input is an acceptable fix
        }
        assertTrue(done.await(3, TimeUnit.MINUTES));
        rc.shutdown();
        assertEquals(g1.record.str("bestMDL"), fin.get(a.id()).bestStructure(), "slot a (seed 1) corrupted by the shared input");
        assertEquals(g2.record.str("bestMDL"), fin.get(b.id()).bestStructure(), "slot b (seed 2) corrupted by the shared input");
        assertEquals(g1.record.lng("rngDraws"), fin.get(a.id()).rngDraws());
        assertEquals(g2.record.lng("rngDraws"), fin.get(b.id()).rngDraws());
    }

    /**
     * Gap: {@code RunController.start} stops and joins the slot's active run <em>before</em> the new
     * generator's constructor validates the input; in DEFAULT a rejected input (IllegalArgumentException)
     * therefore kills the run that was going on in that slot although nothing new starts.
     */
    @Test
    void aRejectedStartMustNotStopTheRunningRun() throws Exception {
        RunController rc = new RunController();
        RunSlot slot = rc.addSlot();
        rc.start(slot, GoldenFixtures.runInput("triterpen", "paper"), RunOptions.application().withSeed(5L));
        Thread.sleep(200);
        assertTrue(slot.isRunning());
        HeavyAtom[] acetone = {atom("C", 0, 3), atom("C", 1, 0), atom("C", 2, 3), atom("O", 3, 0)};
        RunInput bad = new RunInput(acetone, List.of(), List.of(new AnnealingSchedule()), "acetone");
        assertThrows(IllegalArgumentException.class, () -> rc.start(slot, bad, RunOptions.application()));
        try {
            assertTrue(slot.isRunning(), "the running run was stopped by a Start that was rejected");
        } finally {
            rc.shutdown();
        }
    }

    /** {@code n} copies of an atom. */
    private static List<HeavyAtom> n(int count, String symbol, int h) {
        List<HeavyAtom> l = new ArrayList<>();
        for (int i = 0; i < count; i++) l.add(atom(symbol, 0, h));
        return l;
    }

    private static HeavyAtom[] molecule(List<List<HeavyAtom>> parts) {
        List<HeavyAtom> all = new ArrayList<>();
        parts.forEach(all::addAll);
        for (int i = 0; i < all.size(); i++) all.get(i).number = i;
        return all.toArray(new HeavyAtom[0]);
    }

    /**
     * Was a gap (hang, DEFAULT): {@code StartStructureFeasibility} says POSSIBLE whenever the start
     * generator succeeds with <em>positive</em> probability, but for molecules with many monovalent
     * atoms (F, Cl, CH3, OH) that probability is tiny: the generator bonds two monovalent atoms to each
     * other (saturated, disconnected) in almost every attempt and restarts. Legacy loops practically for
     * ever (C8F18: no start structure after 1,000,000 restarts) and ignores Stop there.
     * Fixed in DEFAULT (DV-64): after {@code START_RESTART_LIMIT} restarts the run starts from a
     * constructed structure, and from {@code START_STOP_GRACE} restarts on a Stop is honoured.
     * The legacy restart counts (seed 1) are printed for reference.
     */
    @Test
    void startGeneratorMustNotRunEndlesslyAndUnstoppablyInDefault() throws Exception {
        record Case(String name, HeavyAtom[] atoms) {
        }
        List<Case> cases = List.of(
                new Case("CCl4", molecule(List.of(n(1, "C", 0), n(4, "Cl", 0)))),
                new Case("C2Cl6 hexachloroethane", molecule(List.of(n(2, "C", 0), n(6, "Cl", 0)))),
                new Case("C(CH3)3-C(CH3)3 2,2,3,3-tetramethylbutane", molecule(List.of(n(2, "C", 0), n(6, "C", 3)))),
                new Case("C4F10 perfluorobutane", molecule(List.of(n(4, "C", 0), n(10, "F", 0)))),
                new Case("C6F14 perfluorohexane", molecule(List.of(n(6, "C", 0), n(14, "F", 0)))),
                new Case("C8F18 perfluorooctane", molecule(List.of(n(8, "C", 0), n(18, "F", 0)))));
        List<String> report = new ArrayList<>(), endless = new ArrayList<>();
        for (Case c : cases) {
            assertEquals(io.github.steinbeck.seneca.structgen.StartStructureFeasibility.Verdict.POSSIBLE,
                    io.github.steinbeck.seneca.structgen.StartStructureFeasibility.of(c.atoms()), c.name());
            long[] restarts = {0};
            HeavyAtom[] start = io.github.steinbeck.seneca.structgen.SingleRandomStructureGenerator.generate(c.atoms(),
                    new io.github.steinbeck.seneca.judges.Attic(), io.github.steinbeck.seneca.structgen.RunRandom.seeded(1),
                    () -> ++restarts[0] >= StochasticGenerator.START_RESTART_LIMIT);
            report.add(c.name() + ": legacy " + (start == null ? "none after " : "") + restarts[0] + " restarts");
            // the DEFAULT run must build a start structure and end
            RunInput in = new RunInput(c.atoms(), List.of(), List.of(new AnnealingSchedule()), c.name());
            RunResult[] r = new RunResult[1];
            Thread t = new Thread(() -> r[0] = new StochasticGenerator(in,
                    RunOptions.application().withSeed(1L).withIterationCap(300)).run(), "start-gen");
            t.setDaemon(true);
            t.start();
            t.join(120_000);
            if (t.isAlive() || r[0] == null || r[0].currentStructure() == null || r[0].exception() != null) {
                endless.add(c.name() + (t.isAlive() ? " still running" : " ended without a start structure: " + r[0]));
            }
        }
        System.out.println("start generator restarts: " + report);
        assertEquals(List.of(), endless, "DEFAULT run without a start structure: " + report);

        // Stop during the start generator is honoured in DEFAULT once the grace is over
        HeavyAtom[] c8f18 = molecule(List.of(n(8, "C", 0), n(18, "F", 0)));
        StochasticGenerator g = new StochasticGenerator(new RunInput(c8f18, List.of(), List.of(new AnnealingSchedule()), "C8F18"),
                RunOptions.application().withSeed(1L));
        RunResult[] r = new RunResult[1];
        Thread t = new Thread(() -> r[0] = g.run(), "start-gen-stop");
        t.setDaemon(true);
        t.start();
        Thread.sleep(300);
        g.end();
        t.join(10_000);
        assertTrue(!t.isAlive(), "Stop ignored during the start generator");
        assertNull(r[0].currentStructure(), "stopped before a start structure was found");
    }

    /**
     * Was a gap (hang, DEFAULT; fixed by DV-65): when the start structure admits no valid Faulon move at all, the
     * legacy {@code mutate} draws for ever (its loop only ends on a valid move) - inside
     * {@code initAnnealing}, where no stop request is ever checked. This happens for every molecule whose
     * free valences have exactly one connected realisation, a star: isobutane CH(CH3)3, chloroform
     * CHCl3, CCl4, neopentane, tert-butanol, tetramethylsilane. {@code validate} only rejects fewer than
     * 4 atoms (DV-60 calls that the only {@code mutate} hang), so DEFAULT accepts these inputs, the run
     * thread spins for ever, Stop has no effect and {@code RunController.reset/clear} give up after
     * JOIN_MILLIS and orphan the thread (D15). DEFAULT must reject such inputs or end the run.
     */
    @Test
    void starMoleculesMustNotHangInDefault() throws Exception {
        List<HeavyAtom[]> stars = List.of(
                new HeavyAtom[] {atom("C", 0, 1), atom("C", 1, 3), atom("C", 2, 3), atom("C", 3, 3)},      // isobutane
                new HeavyAtom[] {atom("C", 0, 1), atom("Cl", 1, 0), atom("Cl", 2, 0), atom("Cl", 3, 0)},   // chloroform
                new HeavyAtom[] {atom("C", 0, 0), atom("Cl", 1, 0), atom("Cl", 2, 0), atom("Cl", 3, 0), atom("Cl", 4, 0)}); // CCl4
        List<String> hung = new ArrayList<>();
        for (HeavyAtom[] atoms : stars) {
            RunInput in = new RunInput(atoms, List.of(), List.of(new AnnealingSchedule()), "star" + atoms.length);
            StochasticGenerator g;
            try {
                g = new StochasticGenerator(in, RunOptions.application().withSeed(1L).withIterationCap(1000));
            } catch (IllegalArgumentException rejected) {
                continue; // rejecting it is an acceptable fix
            }
            Thread t = new Thread(g::run, "star-run");
            t.setDaemon(true);
            t.start();
            t.join(5_000);
            if (t.isAlive()) {
                g.end();
                t.join(2_000);
                hung.add(atoms.length + " atoms, " + atoms[1].symbol + ": accepted in DEFAULT, still running after 5 s"
                        + (t.isAlive() ? " and after Stop (thread orphaned)" : ""));
            }
        }
        assertEquals(List.of(), hung);
    }

    // ------------------------------------------------------------------ helpers

    private static RunGolden golden(String key, String profile, long seed) {
        return GoldenFiles.runs().stream().filter(r -> r.key().equals(key) && r.profile().equals(profile) && r.seed() == seed)
                .map(RunGolden::load).findFirst().orElseThrow();
    }

    private static HeavyAtom atom(String symbol, int number, int h) {
        HeavyAtom a = new HeavyAtom(symbol, number);
        a.hCount = h;
        a.maxBondOrder = StandardElements.getMaxBondOrder(symbol);
        a.maxBondOrderSum = StandardElements.getMaxBondOrderSum(symbol);
        return a;
    }

    /**
     * Index of the first schedule marker in a STRICT trace that is followed by no step (another marker or
     * the end): the schedule ended before its first connected candidate (Q1, or a stop). -1 if none.
     */
    private static int firstEmptySchedule(List<String> steps) {
        for (int i = 0; i < steps.size(); i++) {
            if (!steps.get(i).startsWith("schedule ")) continue;
            if (i + 1 == steps.size() || steps.get(i + 1).startsWith("schedule ")) return i;
        }
        return -1;
    }
}
