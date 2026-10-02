package io.github.steinbeck.seneca.verify;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.verify.EngineDiffSupport.Outcome;
import io.github.steinbeck.seneca.verify.EngineDiffSupport.Spec;

/**
 * Verifier "fidelity": complete seeded runs of the 2001 engine against the port on inputs the golden
 * master does not contain - several annealing schedules, heteroatoms other than N/O, every judge
 * option (custom 2D scores up to path length 7, ring-size scores incl. negative ones, het-het penalty,
 * symmetry classes, hybridization/hetero tables), degenerate schedules (plateau 0, p0 = 1, p0 &gt; 1,
 * cooling 1.0, uphill 0) and a stop by iteration cap with schedules left.
 */
@Tag("verify")
class EngineFidelityOracleTest {

    static Spec pinene() {
        Spec s = new Spec();
        s.name = "pinene-synthetic";
        s.sym = new String[] {"C", "C", "C", "C", "C", "C", "C", "C", "C", "C"};
        s.h = new int[] {3, 3, 3, 2, 2, 1, 1, 1, 0, 0};
        s.hoseShifts = new double[] {20.8, 22.9, 26.4, 31.3, 31.5, 38.0, 40.8, 47.1, 116.0, 144.5};
        s.featureEnabled = true;
        s.ringScores = new int[][] {{6, 100}, {4, 50}, {3, -30}};
        s.hmbcPairs = new int[][] {{0, 8}, {1, 6}, {2, 6}, {7, 9}, {5, 3}};
        s.hmbcScores = new int[] {100, 100, 5, 3, 2, 1, 1};
        s.symmetryClasses = new boolean[][] {{false, true, true, false, false, false, false, false, false, false}};
        return s;
    }

    static Spec hetero() {
        Spec s = new Spec();
        s.name = "hetero-synthetic";
        s.sym = new String[] {"C", "C", "C", "C", "C", "C", "C", "C", "S", "Cl", "Br", "N", "O", "P", "F", "Si", "I"};
        s.h = new int[] {2, 2, 2, 1, 1, 0, 2, 1, 0, 0, 0, 1, 0, 0, 0, 2, 0};
        s.hoseShifts = new double[] {30, 45, 60, 120, 128, 140, 35, 70, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        s.featureEnabled = true;
        s.hetHet = true;
        s.hetHetPenalty = -7;
        s.ringScores = new int[][] {{5, 50}, {6, 100}, {3, -20}};
        s.hmbcPairs = new int[][] {{0, 8}, {3, 4}, {5, 9}, {6, 13}};
        s.cosyPairs = new int[][] {{0, 1}, {1, 2}};
        s.symmetryClasses = new boolean[][] {{false, true, true, false, false, false, false, false, false, false, false, false, false, false, false, false, false}};
        s.symmetryScore = 300;
        s.hybridization = true;
        s.heteroBonds = true;
        return s;
    }

    static List<Spec> cases() {
        List<Spec> l = new ArrayList<>();
        for (long seed : new long[] {1, 2, 3}) {
            Spec s = pinene();
            s.name = "pinene 3 schedules seed " + seed;
            s.seed = seed;
            s.schedules.add(new double[] {0.8, 5, 10, 0.9});
            s.schedules.add(new double[] {0.5, 20, 30, 0.8});
            s.schedules.add(new double[] {0.9, 50, 15, 0.95});
            l.add(s);
        }
        for (long seed : new long[] {1, 2, 3, 4}) {
            Spec s = hetero();
            s.name = "hetero 2 schedules seed " + seed;
            s.seed = seed;
            s.schedules.add(new double[] {0.8, 10, 20, 0.9});
            s.schedules.add(new double[] {0.6, 5, 10, 0.85});
            s.cap = 20000;
            l.add(s);
        }
        {
            Spec s = pinene();
            s.name = "cap inside schedule 1 of 3";
            s.seed = 5;
            s.schedules.add(new double[] {0.8, 50, 500, 0.95});
            s.schedules.add(new double[] {0.8, 50, 500, 0.95});
            s.schedules.add(new double[] {0.8, 50, 500, 0.95});
            s.cap = 700;
            l.add(s);
        }
        {
            Spec s = pinene();
            s.name = "degenerate: plateau 0, cooling 1.0, uphill 0";
            s.seed = 6;
            s.schedules.add(new double[] {0.8, 0, 0, 1.0});
            s.schedules.add(new double[] {0.8, 0, 1, 0.5});
            l.add(s);
        }
        {
            Spec s = pinene();
            s.name = "p0 = 1 (kT infinite) and p0 > 1";
            s.seed = 7;
            s.schedules.add(new double[] {1.0, 10, 5, 0.9});
            s.schedules.add(new double[] {1.5, 10, 5, 0.9});
            s.schedules.add(new double[] {0.0, 10, 5, 0.9});
            s.cap = 20000;
            l.add(s);
        }
        {
            Spec s = hetero();
            s.name = "hetero, symmetry score 0 (legacy divides by score)";
            s.seed = 8;
            s.symmetryScore = 0;
            s.schedules.add(new double[] {0.8, 10, 20, 0.9});
            s.cap = 5000;
            l.add(s);
        }
        {
            Spec s = pinene();
            s.name = "stop requested before run() starts";
            s.seed = 9;
            s.schedules.add(new double[] {0.8, 50, 100, 0.9});
            s.cap = 3000;
            s.stopBeforeRun = true;
            l.add(s);
        }
        return l;
    }

    @TestFactory
    List<DynamicTest> legacyVsPort() {
        List<DynamicTest> tests = new ArrayList<>();
        String only = System.getProperty("verify.case");
        for (Spec s : cases()) {
            if (only != null && !s.name.contains(only)) continue;
            tests.add(DynamicTest.dynamicTest(s.name, () -> {
                Outcome legacy = withTimeout(s);
                Outcome port = EngineDiffSupport.runPort(s, LegacyCompat.STRICT);
                System.out.println("[verify] " + s.name + "\n  legacy " + legacy.summary() + "\n  port   " + port.summary());
                assertEquals(legacy.exception() != null, port.exception() != null, "exception status");
                assertEquals(legacy.iterations(), port.iterations(), "iterations");
                assertEquals(legacy.draws(), port.draws(), "random draws");
                assertEquals(legacy.maxScore(), port.maxScore(), "maxScore");
                assertEquals(Double.doubleToRawLongBits(legacy.startKT()), Double.doubleToRawLongBits(port.startKT()), "start_kT " + legacy.startKT() + " vs " + port.startKT());
                assertEquals(Double.doubleToRawLongBits(legacy.finalKT()), Double.doubleToRawLongBits(port.finalKT()), "current_kT");
                assertEquals(legacy.plateau(), port.plateau(), "plateauSteps");
                assertEquals(legacy.uphill(), port.uphill(), "maxUphillSteps");
                assertEquals(legacy.scores(), port.scores(), "result scores");
                assertEquals(legacy.structures(), port.structures(), "result MDL strings");
                assertEquals(legacy.descriptions(), port.descriptions(), "result descriptions");
                // DEFAULT vs STRICT: differs only where quirk Q1 would end the run without a result (DV-04), see ScheduleStepsProbeTest
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
}
