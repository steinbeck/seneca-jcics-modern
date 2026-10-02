package io.github.steinbeck.seneca.golden;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.config.Profile;
import io.github.steinbeck.seneca.structgen.RunInput;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Golden (c): the first 5000 annealing steps of 88 seeded runs, bit-exact per step (golden/FORMAT.md
 * (c)): start structure, the 500 calibration evaluations with their draw counts, {@code start_kT}
 * (incl. -0.0), and per step kT, candidate score, acceptance, current/best score, structure hash,
 * draws and result count; the end record (reason, iteration, total draws, crash). STRICT mode.
 * Work package C (needs A's judges). Inputs come from the echo ({@link GoldenFixtures}), so the
 * test does not depend on B.
 */
@Tag("C")
class TrajectoryGoldenTest {

    public static final int MAX_STEPS = 5000;

    /** Crash records name the legacy top frame (compchem.SetOfRingsFinder.breakBond:302). */
    static final Set<String> IGNORED = Set.of("$.crash.at");

    @TestFactory
    @Execution(ExecutionMode.CONCURRENT)
    List<DynamicTest> trajectories() {
        return GoldenFiles.trajectories().stream().map(id -> DynamicTest.dynamicTest(id.toString(), () -> {
            TrajectoryGolden golden = TrajectoryGolden.load(id);
            RunInput in = Port.call(() -> GoldenFixtures.runInput(id.key(), id.profile()));
            List<String> port = Port.call(() -> TrajectoryRecords.trajectory(id.key(), Profile.byKey(id.profile()), in,
                    id.seed(), MAX_STEPS, LegacyCompat.STRICT));
            assertSameLines(id.toString(), golden.lines, port, golden.lines.size());
        })).toList();
    }

    /** Compares the first {@code count} golden lines with the port's, then the line counts if {@code count} is all. */
    static void assertSameLines(String what, List<String> golden, List<String> port, int count) {
        int n = Math.min(count, port.size());
        for (int i = 0; i < n; i++) JsonDiff.assertSameRecord(what + " line " + (i + 1), golden.get(i), port.get(i), IGNORED);
        if (port.size() < count)
            throw new AssertionError(what + ": port trajectory ends after " + port.size() + " records, golden has " + golden.size()
                    + "; next golden record: " + JsonValue.abbreviate(golden.get(port.size())));
        if (count == golden.size()) assertEquals(golden.size(), port.size(), what + ": number of records");
    }
}
