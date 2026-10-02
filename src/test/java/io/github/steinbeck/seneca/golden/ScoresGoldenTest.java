package io.github.steinbeck.seneca.golden;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;

import io.github.steinbeck.seneca.chem.AtomSets;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.chem.PathUtils;
import io.github.steinbeck.seneca.golden.JsonValue.JObject;
import io.github.steinbeck.seneca.judges.Attic;
import io.github.steinbeck.seneca.judges.JudgeSet;
import io.github.steinbeck.seneca.structgen.RunInput;
import io.github.steinbeck.seneca.structgen.RunRandom;
import io.github.steinbeck.seneca.structgen.SingleRandomStructureGenerator;
import io.github.steinbeck.seneca.structgen.StochasticGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Golden (b): judge scores of 50 fixed structures per dataset, profiles gui, all, manual
 * (golden/FORMAT.md (b)). Three independent parts:
 * <ol>
 *   <li>{@link #judgeScores()} (work package A): every recorded structure ({@code ct}) scored by the
 *       port's judges, configured from the echo ({@link GoldenFixtures}); the per-judge breakdown
 *       (score, max, resultMax, prop, desc) must equal the golden one exactly; plus the header max scores.</li>
 *   <li>{@link #structureConstruction()} (work package C): seeded start structure + 20*(k-1) connected
 *       Faulon moves give the recorded {@code hash}, {@code ct} and {@code startDraws}.</li>
 *   <li>{@link #endToEnd()} (A+B+C): the whole file via {@code golden.ScoreRecords.scores(sml, key)}.</li>
 * </ol>
 */
class ScoresGoldenTest {

    static final String[] PROFILES = {"gui", "all", "manual"};

    @Tag("A")
    @TestFactory
    List<DynamicTest> judgeScores() {
        List<DynamicTest> tests = new ArrayList<>();
        for (String key : GoldenFiles.keys()) {
            for (String profile : PROFILES) {
                tests.add(DynamicTest.dynamicTest(key + " " + profile, () -> {
                    ScoresGolden golden = ScoresGolden.load(key);
                    RunInput in = Port.call(() -> GoldenFixtures.runInput(key, profile));
                    GoldenFixtures.Scorer scorer = Port.call(() -> GoldenFixtures.scorer(in));
                    assertEquals(golden.header().lng(profile + "MaxScore"), (long) scorer.judges.maxScore(), key + " " + profile + " max score");
                    assertEquals(golden.header().arr(profile + "Enabled").strings(), scorer.judges.enabledNames(), key + " " + profile + " enabled");
                    int n = in.atoms().length;
                    for (JObject s : golden.structures()) {
                        int[][] bm = Hashes.matrixFromCt(n, s.arr("ct").intRows());
                        JudgeSet.Breakdown b = Port.call(() -> scorer.score(bm));
                        String expected = s.obj(profile).toJson();
                        String actual = ScoreRecords.breakdown(b).toString();
                        JsonDiff.assertSameRecord(key + " " + profile + " structure k=" + s.integer("k"), expected, actual, Set.of());
                    }
                }));
            }
        }
        return tests;
    }

    @Tag("C")
    @TestFactory
    List<DynamicTest> structureConstruction() {
        return GoldenFiles.keys().stream().map(key -> DynamicTest.dynamicTest(key, () -> {
            ScoresGolden golden = ScoresGolden.load(key);
            HeavyAtom[] atoms = GoldenFixtures.atoms(EchoGolden.load(key));
            for (JObject s : golden.structures()) {
                int k = s.integer("k");
                RunRandom rng = RunRandom.seeded(k);
                // GoldenMaster.startStructure: setMolecule clone, generate() with a fresh Attic
                HeavyAtom[] start = Port.call(() -> SingleRandomStructureGenerator.generate(AtomSets.cloneAtomSet(atoms), new Attic(), rng));
                int[][] bm = AtomSets.createConnectionTable(start);
                AtomSets.synchronizeConnectionTable(bm);
                assertEquals(s.lng("startDraws"), rng.draws(), key + " k=" + k + " startDraws");
                // GoldenMaster.randomWalk: `moves` connected mutations, generator's own mutate()
                HeavyAtom[] tmp = AtomSets.cloneAtomSet(atoms);
                int moves = s.integer("moves");
                for (int done = 0; done < moves; ) {
                    int[][] b2 = AtomSets.cloneInt2D(bm);
                    Port.run(() -> StochasticGenerator.mutate(b2, rng));
                    AtomSets.synchronizeBonds(tmp, b2);
                    if (PathUtils.isConnected(tmp)) {
                        bm = b2;
                        done++;
                    }
                }
                assertEquals(s.str("hash"), Hashes.hash(bm), key + " k=" + k + " structure hash (ct " + Hashes.ct(bm) + ")");
            }
        })).toList();
    }

    @Tag("A")
    @Tag("B")
    @Tag("C")
    @TestFactory
    List<DynamicTest> endToEnd() {
        return GoldenFiles.keys().stream().map(key -> DynamicTest.dynamicTest(key, () -> {
            ScoresGolden golden = ScoresGolden.load(key);
            List<String> port = Port.call(() -> ScoreRecords.scores(GoldenFiles.sml(key), key));
            int n = Math.min(golden.lines.size(), port.size());
            for (int i = 0; i < n; i++)
                JsonDiff.assertSameRecord(key + " scores line " + (i + 1), golden.lines.get(i), port.get(i), Set.of());
            assertEquals(golden.lines.size(), port.size(), key + ": number of score records");
        })).toList();
    }
}
