package io.github.steinbeck.seneca.verify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.config.JudgeConfiguration;
import io.github.steinbeck.seneca.judges.HMBCJudge;
import io.github.steinbeck.seneca.judges.HOSECodeJudge;
import io.github.steinbeck.seneca.judges.HeteroBondsJudge;
import io.github.steinbeck.seneca.judges.HybridizationJudge;
import io.github.steinbeck.seneca.judges.SymmetryJudge;
import io.github.steinbeck.seneca.model.AnnealingSchedule;
import io.github.steinbeck.seneca.model.SenecaDataset;
import io.github.steinbeck.seneca.structgen.EngineLog;
import io.github.steinbeck.seneca.structgen.RunInput;
import io.github.steinbeck.seneca.structgen.RunOptions;
import io.github.steinbeck.seneca.structgen.RunResult;
import io.github.steinbeck.seneca.structgen.StochasticGenerator;

/**
 * Verifier "fidelity", round 3: a crash the 2001 program shows on a GUI path the golden master never
 * takes, and that DEFAULT does not fix (policy point 2). The legacy side is
 * {@code oracle/verify/FidelityRound3OracleTest.legacyCrashesOnStaleJudgeConfiguration}.
 *
 * <p>"General Settings" lets the user edit the molecular formula; {@code SenecaDataset.setMolecularFormula}
 * regenerates the atoms but leaves the judges as they are. A 2D assignment or symmetry class that was
 * configured for the old, larger atom list then indexes past the new one: every evaluation throws
 * {@link ArrayIndexOutOfBoundsException}, so the run ends at the first scored structure with no result
 * (legacy: "Error in StoicGent.run()", silently). Decided fix (DEVIATIONS.md DV-66): DEFAULT rejects such
 * an input at Start with a message ({@code StochasticGenerator.validate}); STRICT keeps the crash.
 */
class FidelityRound3VerifyTest {

    static SenecaDataset datasetWithStaleJudges(boolean hmbc, boolean symmetry) {
        SenecaDataset sd = new SenecaDataset("stale");
        sd.setMolecularFormula("C10H16");
        sd.init();
        sd.annealingSchedules.add(new AnnealingSchedule());
        if (hmbc) {
            HMBCJudge h = sd.getJudge(HMBCJudge.class);
            h.assignment = new boolean[10][10][10];
            h.assignment[9][8][8] = true;
            h.assignment[0][1][1] = true;
            h.setEnabled(true);
        }
        if (symmetry) {
            SymmetryJudge s = sd.getJudge(SymmetryJudge.class);
            boolean[] c = new boolean[10];
            c[2] = c[9] = true;
            s.addSymmetryClass(c);
            s.setEnabled(true);
        }
        // the user corrects the formula: octane, 8 heavy atoms
        sd.setMolecularFormula("C8H18");
        HeavyAtom[] a = sd.getSetOfNodes();
        int[] h = {3, 2, 2, 2, 2, 2, 2, 3};
        for (int i = 0; i < a.length; i++) a[i].hCount = h[i];
        return sd;
    }

    static RunResult run(SenecaDataset sd, LegacyCompat compat) {
        return run(sd, compat, 1L);
    }

    static RunResult run(SenecaDataset sd, LegacyCompat compat, long seed) {
        RunInput in = RunInput.fromDataset(sd, compat);
        return new StochasticGenerator(in, new RunOptions(seed, compat, 2000, null, EngineLog.NONE)).run();
    }

    @Test
    void strictReproducesTheLegacyCrash() {
        RunResult r = run(datasetWithStaleJudges(true, false), LegacyCompat.STRICT);
        assertTrue(r.exception() instanceof ArrayIndexOutOfBoundsException, "STRICT: " + r.exception());
        assertTrue(r.result().size() == 0);
    }

    /**
     * Decided fix (DV-66): DEFAULT rejects the stale input at Start with a message naming the judge, as it
     * rejects other inputs the engine cannot run (DV-05, DV-60), instead of a run that ends at once.
     */
    static void assertRejected(SenecaDataset sd, String judge) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> run(sd, LegacyCompat.DEFAULT));
        assertTrue(e.getMessage().contains(judge) && e.getMessage().contains("refers to atom 10")
                && e.getMessage().contains("8 heavy atoms"), e.getMessage());
    }

    @Test
    void defaultMustNotCrashOnAStaleHmbcAssignment() {
        assertRejected(datasetWithStaleJudges(true, false), "HMBCJudge");
    }

    @Test
    void defaultMustNotCrashOnAStaleSymmetryClass() {
        assertRejected(datasetWithStaleJudges(false, true), "SymmetryJudge");
    }

    /**
     * The rejection covers exactly the configurations on which legacy fails: a symmetry class whose only
     * out-of-range member is never compared with another member, a disabled stale judge, and stale
     * Hybridization/HeteroBonds/HOSE tables do not fail in legacy, so DEFAULT runs them exactly as STRICT.
     */
    @Test
    void defaultRunsStaleConfigurationsOnWhichLegacyDoesNotFail() {
        List<SenecaDataset> cases = new ArrayList<>();
        SenecaDataset lone = datasetWithStaleJudges(false, false);
        boolean[] c = new boolean[10];
        c[9] = true;
        lone.getJudge(SymmetryJudge.class).addSymmetryClass(c);
        lone.getJudge(SymmetryJudge.class).setEnabled(true);
        cases.add(lone);
        SenecaDataset disabled = datasetWithStaleJudges(true, true);
        disabled.getJudge(HMBCJudge.class).setEnabled(false);
        disabled.getJudge(SymmetryJudge.class).setEnabled(false);
        cases.add(disabled);
        SenecaDataset tables = datasetWithStaleJudges(false, false);
        HybridizationJudge hy = tables.getJudge(HybridizationJudge.class);
        hy.initScoreArray(10);
        hy.addHybridizationScore(10, HybridizationJudge.SP2, 1.0);
        hy.setEnabled(true);
        HeteroBondsJudge hb = tables.getJudge(HeteroBondsJudge.class);
        hb.isHeteroBonded = new boolean[10];
        hb.isHeteroBonded[9] = true;
        hb.setEnabled(true);
        HOSECodeJudge hose = tables.getJudge(HOSECodeJudge.class);
        hose.setCarbonShifts(new double[] {10, 20, 30, 40, 50, 60, 70, 80, 90, 100});
        hose.setEnabled(true);
        cases.add(tables);
        for (int i = 0; i < cases.size(); i++) {
            int compared = 0;
            for (long seed = 1; seed <= 40 && compared < 4; seed++) {
                RunResult s = run(copyOf(cases.get(i)), LegacyCompat.STRICT, seed);
                RunResult d = run(copyOf(cases.get(i)), LegacyCompat.DEFAULT, seed);
                assertNull(s.exception(), "case " + i + ": legacy does not fail here");
                if (s.result().size() == 0) continue;     // quirk Q1 ended the legacy run (DV-04): not comparable
                compared++;
                String id = "case " + i + " seed " + seed;
                assertEquals(s.iterations(), d.iterations(), id);
                assertEquals(s.rngDraws(), d.rngDraws(), id);
                assertEquals(s.bestStructure(), d.bestStructure(), id);
                assertEquals(s.result().size(), d.result().size(), id);
                for (int k = 0; k < s.result().size(); k++)
                    assertEquals(s.result().structures.get(k), d.result().structures.get(k), id + " result " + k);
            }
            assertTrue(compared >= 3, "case " + i + ": too few comparable seeds (" + compared + ")");
        }
    }

    private static SenecaDataset copyOf(SenecaDataset sd) {
        SenecaDataset c = new SenecaDataset(sd.getName());
        c.setSetOfNodes(io.github.steinbeck.seneca.chem.AtomSets.cloneAtomSet(sd.getSetOfNodes()));
        for (io.github.steinbeck.seneca.judges.Judge j : sd.judges) c.judges.add(j.copy());
        for (AnnealingSchedule a : sd.annealingSchedules) c.annealingSchedules.add(a.copy());
        return c;
    }

    /** Hybridization/HeteroBonds table edits on stale tables (DV-66): STRICT throws as legacy, DEFAULT enlarges. */
    @Test
    void tableEditsOnStaleTables() {
        for (LegacyCompat compat : LegacyCompat.values()) {
            SenecaDataset sd = new SenecaDataset("grow");
            sd.setMolecularFormula("C4H10");
            sd.init();
            JudgeConfiguration.datasetWindowOpened(sd);   // tables for 4 atoms
            sd.setMolecularFormula("C6H14");              // the formula grows; the window's tables do not
            if (compat.strict()) {
                assertThrows(ArrayIndexOutOfBoundsException.class,
                        () -> JudgeConfiguration.setHybridizationCell(sd, 5, JudgeConfiguration.HYB_COLUMN_SP2, "1.0", compat));
                assertThrows(ArrayIndexOutOfBoundsException.class,
                        () -> JudgeConfiguration.setHeteroBondsCell(sd, 5, JudgeConfiguration.HETERO_COLUMN, true, compat));
            } else {
                JudgeConfiguration.setHybridizationCell(sd, 1, JudgeConfiguration.HYB_COLUMN_SP3, "0.5", compat);
                JudgeConfiguration.setHybridizationCell(sd, 5, JudgeConfiguration.HYB_COLUMN_SP2, "1.0", compat);
                HybridizationJudge hy = sd.getJudge(HybridizationJudge.class);
                assertEquals(6, hy.scores.length);
                assertEquals(50, hy.scores[1][HybridizationJudge.SP3]);
                assertEquals(100, hy.scores[5][HybridizationJudge.SP2]);
                JudgeConfiguration.setHeteroBondsCell(sd, 5, JudgeConfiguration.HETERO_COLUMN, true, compat);
                assertEquals(6, sd.getJudge(HeteroBondsJudge.class).isHeteroBonded.length);
                assertTrue(sd.getJudge(HeteroBondsJudge.class).isHeteroBonded[5]);
            }
        }
    }

    /**
     * "New Dataset" (F-20: {@code init()} plus a default schedule) and "Start" before a formula was
     * entered: the dataset has no atoms. Legacy {@code goAction} fails inside its {@code try}
     * ({@code new Molecule(null)}) and starts nothing. The port's {@code RunInput.fromDataset} throws a
     * {@link NullPointerException} from {@code AtomSets.cloneAtomSet}; DEFAULT's documented contract for
     * inputs the engine cannot run is an {@link IllegalArgumentException} with a message.
     */
    @Test
    void defaultRejectsADatasetWithoutAtomsWithAMessage() {
        SenecaDataset sd = new SenecaDataset("new");
        sd.init();
        sd.annealingSchedules.add(new AnnealingSchedule());
        Throwable t = null;
        try {
            new StochasticGenerator(RunInput.fromDataset(sd, LegacyCompat.DEFAULT),
                    new RunOptions(1L, LegacyCompat.DEFAULT, 100, null, EngineLog.NONE));
        } catch (Throwable e) {
            t = e;
        }
        assertTrue(t instanceof IllegalArgumentException, "DEFAULT Start on a dataset without atoms: " + t);
    }
}
