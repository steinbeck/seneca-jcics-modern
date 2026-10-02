package io.github.steinbeck.seneca.golden;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.config.Profile;
import io.github.steinbeck.seneca.golden.JsonValue.JObject;
import io.github.steinbeck.seneca.structgen.RunInput;
import io.github.steinbeck.seneca.structgen.RunOptions;
import io.github.steinbeck.seneca.structgen.RunResult;
import io.github.steinbeck.seneca.structgen.StepContext;
import io.github.steinbeck.seneca.structgen.StepObserver;
import io.github.steinbeck.seneca.structgen.StochasticGenerator;
import io.github.steinbeck.seneca.structgen.StructureGeneratorResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code LegacyCompat.DEFAULT} against the golden master (policy point 2: a fix must not change the
 * random stream or any outcome where the legacy code does not fail):
 * <ul>
 *   <li>trajectories: non-failing ones must be identical to golden; for a failing one (ring-finder
 *       crash, Q1) every golden record before the failure must be identical, and the DEFAULT run must
 *       continue past the failure point;</li>
 *   <li>complete runs: non-failing runs identical to golden (slow ones tagged "slow"); crashed runs
 *       identical up to the crash (result list, best score, current and candidate structure, draws at
 *       the moment the legacy run died) and continuing past it; Q1 runs continue with a first
 *       main-loop step after the point where legacy stopped.</li>
 * </ul>
 * Work package C (with A's DEFAULT ring-finder fallback).
 */
@Tag("C")
class DefaultModeGoldenTest {

    @TestFactory
    @Execution(ExecutionMode.CONCURRENT)
    List<DynamicTest> trajectories() {
        return GoldenFiles.trajectories().stream().map(id -> DynamicTest.dynamicTest(id.toString(), () -> {
            TrajectoryGolden golden = TrajectoryGolden.load(id);
            RunInput in = Port.call(() -> GoldenFixtures.runInput(id.key(), id.profile()));
            List<String> port = Port.call(() -> TrajectoryRecords.trajectory(id.key(), Profile.byKey(id.profile()), in,
                    id.seed(), TrajectoryGoldenTest.MAX_STEPS, LegacyCompat.DEFAULT));
            if (!golden.failed()) {
                TrajectoryGoldenTest.assertSameLines(id + " (DEFAULT)", golden.lines, port, golden.lines.size());
                return;
            }
            // identical up to the failure point: every golden record except "end"
            int prefix = golden.lines.size() - 1;
            TrajectoryGoldenTest.assertSameLines(id + " (DEFAULT, before the legacy failure)", golden.lines, port, prefix);
            JObject end = JsonValue.parse(port.get(port.size() - 1)).asObject();
            assertTrue(end.lng("iteration") > golden.end().lng("iteration"),
                    id + ": DEFAULT must continue past the legacy failure (" + golden.endReason() + "), ended with " + end.toJson());
        })).toList();
    }

    @TestFactory
    @Execution(ExecutionMode.CONCURRENT)
    List<DynamicTest> fastRuns() {
        return runs(false);
    }

    @Tag("slow")
    @TestFactory
    @Execution(ExecutionMode.CONCURRENT)
    List<DynamicTest> slowRuns() {
        return runs(true);
    }

    private static List<DynamicTest> runs(boolean slow) {
        return GoldenFiles.runs().stream().map(RunGolden::load).filter(r -> r.slow() == slow)
                .map(g -> DynamicTest.dynamicTest(g.id + " (" + g.status() + ")", () -> {
                    if (!g.failed()) {
                        RunInput in = Port.call(() -> GoldenFixtures.runInput(g.id.key(), g.id.profile()));
                        String port = Port.call(() -> RunRecords.run(g.id.key(), Profile.byKey(g.id.profile()), in, g.id.seed(),
                                RunGolden.ITERATION_CAP, GoldenFiles.referencesTsv(), LegacyCompat.DEFAULT));
                        JsonDiff.assertSameRecord(g.id + " (DEFAULT)", g.line, port, RunGolden.IGNORED_PATHS);
                    } else if (g.status().equals("exception_in_run")) {
                        crashedRun(g);
                    } else {
                        q1Run(g);
                    }
                })).toList();
    }

    /** State captured when the DEFAULT run reaches the candidate on which the legacy run crashed. */
    private record AtCrash(long draws, String candidateHash, String currentHash, long best, StructureGeneratorResult results) {
    }

    private static void crashedRun(RunGolden g) {
        JObject crash = g.record.obj("crash");
        long crashIteration = crash.lng("iteration");
        AtomicReference<AtCrash> seen = new AtomicReference<>();
        StepObserver obs = new StepObserver() {
            @Override
            public void beforeAcceptance(StepContext ctx) {
                if (ctx.iteration() == crashIteration && seen.get() == null) {
                    seen.set(new AtCrash(ctx.draws(), Hashes.hash(ctx.candidate()), Hashes.hash(ctx.current()),
                            ctx.bestScore(), ctx.resultCopy()));
                    ctx.requestStop();
                }
            }
        };
        RunInput in = Port.call(() -> GoldenFixtures.runInput(g.id.key(), g.id.profile()));
        RunResult r = Port.call(() -> new StochasticGenerator(in, RunOptions.golden(g.id.seed())
                .withCompat(LegacyCompat.DEFAULT).withObserver(obs)).run());
        assertNull(r.exception(), g.id + ": DEFAULT run threw " + r.exception());
        AtCrash at = seen.get();
        assertNotNull(at, g.id + ": DEFAULT run never reached the legacy crash candidate (iteration " + crashIteration + ")");
        assertEquals(g.record.lng("rngDraws"), at.draws(), g.id + ": draws at the crash candidate");
        assertEquals(crash.str("candidateHash"), at.candidateHash(), g.id + ": crash candidate");
        assertEquals(crash.str("currentHash"), at.currentHash(), g.id + ": current structure at the crash");
        assertEquals(g.record.lng("bestScore"), at.best(), g.id + ": best score at the crash");
        List<JsonValue> results = g.record.arr("results").items();
        assertEquals(results.size(), at.results().size(), g.id + ": number of results at the crash");
        for (int i = 0; i < results.size(); i++) {
            JObject expected = results.get(i).asObject();
            assertEquals(expected.lng("score"), at.results().scoreSummaries.get(i).score, g.id + " result " + i + " score");
            assertEquals(expected.str("description"), at.results().scoreSummaries.get(i).description, g.id + " result " + i + " description");
        }
    }

    private static void q1Run(RunGolden g) {
        long legacyDraws = g.record.lng("rngDraws");
        AtomicReference<Long> firstStepDraws = new AtomicReference<>();
        StepObserver obs = new StepObserver() {
            @Override
            public void beforeAcceptance(StepContext ctx) {
                firstStepDraws.compareAndSet(null, ctx.draws());
                ctx.requestStop();
            }
        };
        RunInput in = Port.call(() -> GoldenFixtures.runInput(g.id.key(), g.id.profile()));
        RunResult r = Port.call(() -> new StochasticGenerator(in, RunOptions.golden(g.id.seed())
                .withCompat(LegacyCompat.DEFAULT).withObserver(obs)).run());
        assertNull(r.exception(), g.id + ": DEFAULT run threw " + r.exception());
        assertNotNull(firstStepDraws.get(), g.id + ": DEFAULT must reach a first main-loop step (legacy Q1 stopped after "
                + legacyDraws + " draws)");
        assertTrue(firstStepDraws.get() > legacyDraws, g.id + ": first DEFAULT step must come after the legacy stop point");
    }
}
