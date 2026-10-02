package io.github.steinbeck.seneca.golden;

import java.util.List;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.config.Profile;
import io.github.steinbeck.seneca.structgen.RunInput;

/**
 * Golden (d): complete seeded runs (STRICT): status, termination, iterations, draws, max score, kT
 * values, plateau/uphill after reheats, best score/structure/MDL/judge breakdown, crash data, reference
 * check, and the full result list with descriptions (golden/FORMAT.md (d)). Runs with more than
 * {@value RunGolden#SLOW_ITERATIONS} iterations are tagged "slow" (excluded by default; {@code mvn -Pslow}).
 * Work package C (needs A's judges). Inputs come from the echo ({@link GoldenFixtures}).
 */
@Tag("C")
class RunsGoldenTest {

    @TestFactory
    @Execution(ExecutionMode.CONCURRENT)
    List<DynamicTest> fastRuns() {
        return tests(false);
    }

    @Tag("slow")
    @TestFactory
    @Execution(ExecutionMode.CONCURRENT)
    List<DynamicTest> slowRuns() {
        return tests(true);
    }

    private static List<DynamicTest> tests(boolean slow) {
        return GoldenFiles.runs().stream().map(RunGolden::load).filter(r -> r.slow() == slow)
                .map(g -> DynamicTest.dynamicTest(g.id + " (" + g.iterations() + " it, " + g.status() + ")", () -> {
                    RunInput in = Port.call(() -> GoldenFixtures.runInput(g.id.key(), g.id.profile()));
                    String port = Port.call(() -> RunRecords.run(g.id.key(), Profile.byKey(g.id.profile()), in, g.id.seed(),
                            RunGolden.ITERATION_CAP, GoldenFiles.referencesTsv(), LegacyCompat.STRICT));
                    JsonDiff.assertSameRecord(g.id.toString(), g.line, port, RunGolden.IGNORED_PATHS);
                })).toList();
    }
}
