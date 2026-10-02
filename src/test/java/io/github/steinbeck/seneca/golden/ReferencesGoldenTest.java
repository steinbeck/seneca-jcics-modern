package io.github.steinbeck.seneca.golden;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;

import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.golden.JsonValue.JObject;
import io.github.steinbeck.seneca.judges.JudgeSet;
import io.github.steinbeck.seneca.structgen.RunInput;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Golden (e): the literature structures. For every mappable reference and profile the recorded best
 * labelling ({@code ct} in dataset order) is scored by the port; total, raw, atMax, max score, enabled
 * judges and the per-judge breakdown must equal the golden record (work package A). The mappability
 * check of the reference data itself ({@code golden.ReferenceCheck}, used by the run records) is
 * work package C. The harness's labelling search is not re-run (it is harness code, not legacy).
 */
class ReferencesGoldenTest {

    @Tag("A")
    @TestFactory
    List<DynamicTest> bestLabellingScores() {
        List<DynamicTest> tests = new ArrayList<>();
        for (String key : GoldenFiles.keys()) {
            ReferenceGolden ref = ReferenceGolden.load(key);
            for (JObject best : ref.best()) {
                String profile = best.str("profile");
                tests.add(DynamicTest.dynamicTest(key + " " + profile, () -> {
                    RunInput in = Port.call(() -> GoldenFixtures.runInput(key, profile));
                    GoldenFixtures.Scorer scorer = Port.call(() -> GoldenFixtures.scorer(in));
                    assertEquals(best.arr("enabled").strings(), scorer.judges.enabledNames(), "enabled");
                    assertEquals(best.lng("maxScore"), (long) scorer.judges.maxScore(), "max score");
                    int[][] bm = Hashes.matrixFromCt(in.atoms().length, best.arr("ct").intRows());
                    JudgeSet.Breakdown b = Port.call(() -> scorer.score(bm));
                    assertEquals(best.lng("total"), b.total, "total");
                    assertEquals(best.lng("raw"), b.raw, "raw");
                    assertEquals(best.bool("atMax"), b.total >= scorer.judges.maxScore(), "atMax");
                    JsonDiff.assertSameRecord(key + " " + profile + " judges", best.obj("judges").toJson(),
                            ScoreRecords.breakdown(b).toString(), Set.of());
                }));
            }
        }
        return tests;
    }

    @Tag("C")
    @TestFactory
    List<DynamicTest> mappability() {
        return GoldenFiles.keys().stream().map(key -> DynamicTest.dynamicTest(key, () -> {
            ReferenceGolden ref = ReferenceGolden.load(key);
            HeavyAtom[] atoms = GoldenFixtures.atoms(EchoGolden.load(key));
            ReferenceCheck c = Port.call(() -> ReferenceCheck.of(GoldenFiles.referencesTsv(), key, atoms));
            assertEquals(ref.defined(), c.defined(), "defined");
            if (!ref.defined()) return;
            assertEquals(ref.mappable(), c.mappable(), "mappable");
            for (JObject best : ref.best()) {
                int[][] bm = Hashes.matrixFromCt(atoms.length, best.arr("ct").intRows());
                assertEquals(true, c.equalConstitution(bm), key + " " + best.str("profile") + ": best labelling is the reference");
            }
        })).toList();
    }
}
