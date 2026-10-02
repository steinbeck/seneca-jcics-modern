package io.github.steinbeck.seneca.verify;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.config.Profile;
import io.github.steinbeck.seneca.golden.GoldenFiles;
import io.github.steinbeck.seneca.golden.JsonDiff;
import io.github.steinbeck.seneca.golden.ReferenceRecords;
import io.github.steinbeck.seneca.golden.RunGolden;
import io.github.steinbeck.seneca.golden.RunRecords;
import io.github.steinbeck.seneca.golden.ScoresGolden;
import io.github.steinbeck.seneca.golden.TrajectoryGolden;
import io.github.steinbeck.seneca.golden.TrajectoryRecords;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Independent verification (verifier "golden"): the golden trajectory and run tests of the port build
 * their generator input from the echo records ({@code GoldenFixtures}), so the full path the CLI and the
 * app use - SML file -&gt; port reader -&gt; GUI recipe -&gt; {@code RunInput.fromDataset} -&gt; generator -
 * is checked by the suite only for pinene. This test runs every golden trajectory and complete run
 * through that path in STRICT mode and compares with golden/ exactly; the only ignored field is
 * {@code $.crash.at} (the legacy stack frame, DEVIATIONS DV-30). It also pins the golden inventory so
 * that a lost file cannot silently shrink the suite.
 */
class RecipePathGoldenVerifyTest {

    static final Set<String> IGNORED = Set.of("$.crash.at");

    @Test
    void goldenInventoryIsComplete() {
        assertEquals(11, GoldenFiles.keys().size(), "dataset keys");
        assertEquals(88, GoldenFiles.trajectories().size(), "trajectories");
        assertEquals(72, GoldenFiles.runs().size(), "complete runs");
        assertEquals(33, GoldenFiles.runs().stream().map(RunGolden::load).filter(RunGolden::slow).count(), "slow runs");
        for (String key : GoldenFiles.keys())
            assertEquals(50, ScoresGolden.load(key).structures().size(), key + ": score structures");
        assertEquals(Set.of("$.crash.at"), RunGolden.IGNORED_PATHS, "run tests may ignore only crash.at");
    }

    /**
     * Whole golden/references files (reference record incl. refAtoms, refCt, kekuleVariants,
     * classDifference; best labelling incl. map, kekule, search) via the port's emitter. The suite checks
     * only the judge breakdown of the recorded labelling and the mappability flag.
     */
    @TestFactory
    List<DynamicTest> referenceFilesEndToEnd() {
        return GoldenFiles.keys().stream().map(key -> DynamicTest.dynamicTest(key, () -> {
            List<String> golden = GoldenFiles.lines(GoldenFiles.reference(key));
            List<String> port = ReferenceRecords.references(GoldenFiles.sml(key), key, GoldenFiles.referencesTsv());
            int n = Math.min(golden.size(), port.size());
            for (int i = 0; i < n; i++) JsonDiff.assertSameRecord(key + " ref line " + (i + 1), golden.get(i), port.get(i), Set.of());
            assertEquals(golden.size(), port.size(), key + ": number of reference records");
        })).toList();
    }

    @TestFactory
    @Execution(ExecutionMode.CONCURRENT)
    List<DynamicTest> trajectoriesViaRecipe() {
        return GoldenFiles.trajectories().stream().map(id -> DynamicTest.dynamicTest(id.toString(), () -> {
            TrajectoryGolden golden = TrajectoryGolden.load(id);
            List<String> port = TrajectoryRecords.trajectory(GoldenFiles.sml(id.key()), id.key(), Profile.byKey(id.profile()),
                    id.seed(), 5000, LegacyCompat.STRICT);
            assertEquals(golden.lines.size(), port.size(), id + ": number of records");
            for (int i = 0; i < port.size(); i++) {
                String g = golden.lines.get(i), p = port.get(i);
                if (g.equals(p)) continue;
                assertTrue(g.contains("\"crash\""), id + " line " + (i + 1) + " differs and is not a crash record");
                JsonDiff.assertSameRecord(id + " line " + (i + 1), g, p, IGNORED);
            }
        })).toList();
    }

    @TestFactory
    @Execution(ExecutionMode.CONCURRENT)
    List<DynamicTest> fastRunsViaRecipe() {
        return runs(false);
    }

    @Tag("slow")
    @TestFactory
    @Execution(ExecutionMode.CONCURRENT)
    List<DynamicTest> slowRunsViaRecipe() {
        return runs(true);
    }

    private static List<DynamicTest> runs(boolean slow) {
        return GoldenFiles.runs().stream().map(RunGolden::load).filter(r -> r.slow() == slow)
                .map(g -> DynamicTest.dynamicTest(g.id.toString(), () -> {
                    String port = RunRecords.run(GoldenFiles.sml(g.id.key()), g.id.key(), Profile.byKey(g.id.profile()),
                            g.id.seed(), RunGolden.ITERATION_CAP, GoldenFiles.referencesTsv(), LegacyCompat.STRICT);
                    if (g.line.equals(port)) return;
                    assertEquals("exception_in_run", g.status(), g.id + ": differs byte-wise and is not a crash record");
                    JsonDiff.assertSameRecord(g.id.toString(), g.line, port, IGNORED);
                })).toList();
    }
}
