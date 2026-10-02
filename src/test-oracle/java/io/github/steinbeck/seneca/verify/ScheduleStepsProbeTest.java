package io.github.steinbeck.seneca.verify;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.model.AnnealingSchedule;
import io.github.steinbeck.seneca.structgen.EngineLog;
import io.github.steinbeck.seneca.structgen.RunInput;
import io.github.steinbeck.seneca.structgen.RunOptions;
import io.github.steinbeck.seneca.structgen.StepContext;
import io.github.steinbeck.seneca.structgen.StepObserver;
import io.github.steinbeck.seneca.structgen.StochasticGenerator;

/** Diagnostic: main-loop steps per schedule, STRICT vs DEFAULT (explains the DEFAULT/STRICT differences). */
@Tag("verify")
class ScheduleStepsProbeTest {

    static List<Integer> steps(EngineDiffSupport.Spec s, LegacyCompat c) {
        List<Integer> per = new ArrayList<>();
        StepObserver o = new StepObserver() {
            @Override
            public void onScheduleInitialized(int schedule, double startKT, long p, long u, long cs, double cr) {
                per.add(0);
            }

            @Override
            public void afterStep(StepContext ctx) {
                per.set(per.size() - 1, per.get(per.size() - 1) + 1);
            }
        };
        List<AnnealingSchedule> sched = new ArrayList<>();
        for (double[] d : s.schedules) sched.add(new AnnealingSchedule(d[0], (long) d[1], (long) d[2], d[3]));
        RunInput in = new RunInput(EngineDiffSupport.portAtoms(s), EngineDiffSupport.portJudges(s), sched, s.name);
        new StochasticGenerator(in, new RunOptions(s.seed, c, s.cap, o, EngineLog.NONE)).run();
        return per;
    }

    @Test
    void probe() {
        for (EngineDiffSupport.Spec s : EngineFidelityOracleTest.cases()) {
            if (s.stopBeforeRun || s.symmetryScore == 0) continue;
            System.out.println("[verify] steps per schedule " + s.name + ": STRICT " + steps(s, LegacyCompat.STRICT)
                    + " DEFAULT " + steps(s, LegacyCompat.DEFAULT));
        }
    }
}
