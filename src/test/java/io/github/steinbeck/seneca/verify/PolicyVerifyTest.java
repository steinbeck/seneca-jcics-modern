package io.github.steinbeck.seneca.verify;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.config.GuiRecipe;
import io.github.steinbeck.seneca.config.JudgeConfiguration;
import io.github.steinbeck.seneca.config.Profile;
import io.github.steinbeck.seneca.golden.GoldenFiles;
import io.github.steinbeck.seneca.golden.GoldenFixtures;
import io.github.steinbeck.seneca.golden.RunGolden;
import io.github.steinbeck.seneca.io.SpecMLReader;
import io.github.steinbeck.seneca.judges.Attic;
import io.github.steinbeck.seneca.judges.HMBCJudge;
import io.github.steinbeck.seneca.judges.HOSECodeJudge;
import io.github.steinbeck.seneca.model.NMRSignal;
import io.github.steinbeck.seneca.model.NMRSpectrum;
import io.github.steinbeck.seneca.model.SenecaDataset;
import io.github.steinbeck.seneca.model.SpectrumSlot;
import io.github.steinbeck.seneca.structgen.RunController;
import io.github.steinbeck.seneca.structgen.RunInput;
import io.github.steinbeck.seneca.structgen.RunListener;
import io.github.steinbeck.seneca.structgen.RunOptions;
import io.github.steinbeck.seneca.structgen.RunRandom;
import io.github.steinbeck.seneca.structgen.RunResult;
import io.github.steinbeck.seneca.structgen.RunSlot;
import io.github.steinbeck.seneca.structgen.SingleRandomStructureGenerator;
import io.github.steinbeck.seneca.structgen.StochasticGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Policy verifier (round 1): checks of port-policy compliance that the work-package tests do not cover.
 * The round-1 gaps are fixed; the tests that documented them now assert the fixed behaviour.
 */
class PolicyVerifyTest {

    // ------------------------------------------------------------------ positive checks

    /** DEFAULT validation must accept every golden input in every profile (else DEFAULT changes a non-failing run). */
    @Test
    void defaultValidationAcceptsEveryGoldenInputInEveryProfile() {
        for (String key : GoldenFiles.keys()) {
            for (String profile : List.of("gui", "paper", "all", "manual")) {
                assertNull(StochasticGenerator.validate(GoldenFixtures.runInput(key, profile)), key + "/" + profile);
            }
        }
    }

    /**
     * The application path (RunController + listeners + log, DEFAULT) on a non-failing golden run must
     * reproduce the golden run exactly (listeners/hooks must not draw or change anything).
     */
    @Test
    void runControllerInDefaultReproducesNonFailingGoldenRun() throws Exception {
        GoldenFiles.RunId id = GoldenFiles.runs().stream()
                .filter(r -> r.key().equals("pinene") && r.profile().equals("paper") && r.seed() == 1).findFirst().orElseThrow();
        RunGolden g = RunGolden.load(id);
        assertTrue(!g.failed());
        RunController rc = new RunController();
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<RunResult> fin = new AtomicReference<>();
        rc.addListener(new RunListener() {
            @Override
            public void onFinished(RunSlot slot, RunResult result) {
                fin.set(result);
                done.countDown();
            }
        });
        RunSlot slot = rc.addSlot();
        RunOptions o = RunOptions.application().withSeed(1L).withIterationCap(RunGolden.ITERATION_CAP);
        assertEquals(LegacyCompat.DEFAULT, o.compat());
        rc.start(slot, GoldenFixtures.runInput("pinene", "paper"), o);
        assertTrue(done.await(3, TimeUnit.MINUTES));
        RunResult r = fin.get();
        assertEquals(g.status(), r.goldenStatus());
        assertEquals(g.iterations(), r.iterations());
        assertEquals(g.record.lng("rngDraws"), r.rngDraws());
        assertEquals(g.record.lng("bestScore"), r.bestScore().score);
        assertEquals(g.record.str("bestMDL"), r.bestStructure());
        rc.shutdown();
    }

    // ------------------------------------------------------------------ round-1 gaps, now fixed

    /**
     * Was a gap (DV-60): the start generator's stop check applied to every input, so a stop during a
     * restart of a solvable input gave no start structure. Now the check is honoured only where legacy
     * would restart for ever. A stop requested before run() is honoured in DEFAULT (DV-62); for all 50
     * seeds the run must still build its start structure and end after the legacy stop path
     * (initAnnealing + one loop body), exactly like legacy with a stop right after the start.
     */
    @Test
    void stopDuringStartGenerationNoLongerAltersSolvableRuns() {
        RunInput base = GoldenFixtures.runInput("pinene", "paper");
        int restartingSeeds = 0;
        for (long seed = 1; seed <= 50; seed++) {
            if (SingleRandomStructureGenerator.generate(base.atoms(), new Attic(), RunRandom.seeded(seed), () -> true) == null)
                restartingSeeds++;
            StochasticGenerator sg = new StochasticGenerator(base.copy(), RunOptions.golden(seed).withCompat(LegacyCompat.DEFAULT));
            sg.end();
            RunResult r = sg.run();
            assertNull(r.exception(), "seed " + seed);
            assertNotNull(r.currentStructure(), "seed " + seed + ": the start structure must be built (legacy ignores the stop there)");
            assertTrue(r.iterations() <= 1, "seed " + seed + ": stop after one loop body");
        }
        assertTrue(restartingSeeds > 0, "no seed restarts; the check would not be exercised");
    }

    /** Was a gap: legacy run() clears the stop flag on entry; STRICT must reproduce the golden run. */
    @Test
    void endBeforeRunIsLostInStrictAsInLegacy() {
        GoldenFiles.RunId id = GoldenFiles.runs().stream()
                .filter(r -> r.key().equals("pinene") && r.profile().equals("paper") && r.seed() == 1).findFirst().orElseThrow();
        RunGolden g = RunGolden.load(id);
        StochasticGenerator sg = new StochasticGenerator(GoldenFixtures.runInput("pinene", "paper"),
                RunOptions.golden(1).withIterationCap(RunGolden.ITERATION_CAP));
        sg.end();
        RunResult r = sg.run();
        assertEquals(g.iterations(), r.iterations());
        assertEquals(g.record.lng("rngDraws"), r.rngDraws());
        assertEquals(g.record.str("bestMDL"), r.bestStructure());
        assertTrue(!r.stoppedByUser());
    }

    /** Was a gap: an activated HOSECodeJudge without Autoconfigure. STRICT keeps the NPE, DEFAULT runs (DV-63). */
    @Test
    void hoseJudgeActivatedWithoutAutoconfigureRunsInDefault() throws Exception {
        for (LegacyCompat compat : LegacyCompat.values()) {
            SenecaDataset sd = SpecMLReader.read(GoldenFiles.sml("pinene"));
            JudgeConfiguration.datasetWindowOpened(sd);
            HOSECodeJudge hose = sd.getJudge(HOSECodeJudge.class);
            JudgeConfiguration.setActivated(hose, true);
            if (compat.strict()) {
                assertThrows(NullPointerException.class, () -> RunInput.fromDataset(sd, compat));
                continue;
            }
            RunInput.fromDataset(sd, compat);
            assertEquals(0, hose.getMaxScore());
            // a runnable dataset (H counts, schedule) whose HOSE judge is enabled but has no shifts
            SenecaDataset ready = GuiRecipe.prepare(GoldenFiles.sml("pinene"), "pinene", Profile.PAPER).sd;
            HOSECodeJudge h2 = ready.getJudge(HOSECodeJudge.class);
            h2.setCarbonShifts(null);
            JudgeConfiguration.setActivated(h2, true);
            RunInput in = RunInput.fromDataset(ready, compat);
            assertEquals(0, h2.getMaxScore());
            RunResult r = new StochasticGenerator(in, RunOptions.golden(1).withCompat(compat).withIterationCap(2000)).run();
            assertNull(r.exception());
            assertEquals("iteration_cap", r.goldenStatus());
        }
    }

    /**
     * Was a gap: HMBC Autoconfigure with a long-range proton that has no 1JCH partner. STRICT keeps the
     * NPE (and the partial assignment); DEFAULT fails without changing anything and unticks the judge (DV-46).
     */
    @Test
    void hmbcAutoconfigureFailureIsCleanInDefault() throws Exception {
        for (LegacyCompat compat : LegacyCompat.values()) {
            SenecaDataset sd = GuiRecipe.prepare(GoldenFiles.sml("pinene"), "pinene", Profile.PAPER).sd;
            HMBCJudge hmbc = sd.getJudge(HMBCJudge.class);
            boolean[][][] before = deepCopy(hmbc.assignment);
            NMRSpectrum lr = sd.spectrum(SpectrumSlot.HETCORLR);
            float carbon = firstShift(sd);
            float[] shifts = new float[lr.nucleus.length];
            for (int i = 0; i < shifts.length; i++) shifts[i] = lr.nucleus[i].equals(NMRSpectrum.NUC_CARBON) ? carbon : 99.9f;
            lr.addSignal(new NMRSignal(lr.nucleus, shifts, 1f, NMRSignal.PHASE_POSITIVE));
            if (compat.strict()) {
                assertThrows(NullPointerException.class, () -> JudgeConfiguration.autoconfigure(sd, hmbc, compat));
                assertTrue(hmbc.getEnabled());
            } else {
                JudgeConfiguration.autoconfigure(sd, hmbc, compat);
                assertTrue(!hmbc.getEnabled(), "failed autoconfigure unticks the judge");
                assertTrue(java.util.Arrays.deepEquals(before, hmbc.assignment), "assignment unchanged");
                RunInput.fromDataset(sd, compat);
            }
        }
    }

    /**
     * Was a gap: acetone (CH3, C, CH3, O) has no structure the legacy start generator can build (it never
     * forms C=O). DEFAULT now rejects it; STRICT restarts as legacy, but a stop request ends the run.
     */
    @Test
    void unbuildableAcetoneIsRejectedInDefaultAndStoppableInStrict() throws Exception {
        HeavyAtom[] acetone = {atom("C", 0, 3), atom("C", 1, 0), atom("C", 2, 3), atom("O", 3, 0)};
        RunInput in = new RunInput(acetone, List.of(), List.of(new io.github.steinbeck.seneca.model.AnnealingSchedule()), "acetone");
        assertNotNull(StochasticGenerator.validate(in), "DEFAULT rejects acetone");
        assertThrows(IllegalArgumentException.class, () -> new StochasticGenerator(in, RunOptions.application()));
        StochasticGenerator sg = new StochasticGenerator(in, RunOptions.golden(1));
        AtomicReference<RunResult> res = new AtomicReference<>();
        Thread t = new Thread(() -> res.set(sg.run()));
        t.setDaemon(true);
        t.start();
        Thread.sleep(300);
        assertTrue(t.isAlive(), "STRICT restarts like legacy");
        sg.end();
        t.join(10_000);
        assertTrue(!t.isAlive(), "stop request honoured between restarts");
        assertNull(res.get().currentStructure());
        assertEquals(0, res.get().result().size());
    }

    private static boolean[][][] deepCopy(boolean[][][] a) {
        if (a == null) return null;
        boolean[][][] c = new boolean[a.length][][];
        for (int i = 0; i < a.length; i++) {
            c[i] = new boolean[a[i].length][];
            for (int j = 0; j < a[i].length; j++) c[i][j] = a[i][j].clone();
        }
        return c;
    }

    private static HeavyAtom atom(String symbol, int number, int h) {
        HeavyAtom a = new HeavyAtom(symbol, number);
        a.hCount = h;
        a.maxBondOrder = io.github.steinbeck.seneca.chem.StandardElements.getMaxBondOrder(symbol);
        a.maxBondOrderSum = io.github.steinbeck.seneca.chem.StandardElements.getMaxBondOrderSum(symbol);
        return a;
    }

    private static float firstShift(SenecaDataset sd) {
        for (HeavyAtom a : sd.setOfNodes) if (a.shift13C != null) return a.shift13C;
        throw new IllegalStateException("no shift");
    }
}
