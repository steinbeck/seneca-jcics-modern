package io.github.steinbeck.seneca.golden;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.config.GuiRecipe;
import io.github.steinbeck.seneca.config.Profile;
import io.github.steinbeck.seneca.structgen.RunInput;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Golden (a): the dataset as the port's SML reader sees it and after the port's GUI recipe, for all
 * 11 dataset keys, record by record (golden/FORMAT.md (a)). Work package B (needs A's judge
 * {@code calcMaxScore}/{@code copy} for the configured judge records).
 */
@Tag("B")
class DatasetEchoGoldenTest {

    /** Whole echo file, line by line, via the port's emitter {@code golden.EchoRecords}. */
    @TestFactory
    List<DynamicTest> echoRecords() {
        return GoldenFiles.keys().stream().map(key -> DynamicTest.dynamicTest(key, () -> {
            EchoGolden golden = EchoGolden.load(key);
            List<String> port = Port.call(() -> EchoRecords.echo(GoldenFiles.sml(key), key));
            int n = Math.min(golden.lines.size(), port.size());
            for (int i = 0; i < n; i++)
                JsonDiff.assertSameRecord(key + " echo line " + (i + 1), golden.lines.get(i), port.get(i), Set.of());
            assertEquals(golden.lines.size(), port.size(), key + ": number of echo records");
        })).toList();
    }

    /**
     * The recipe end to end down to the generator input: enabled judges and the generator's max score
     * per profile ({@code generatorInput} records), via {@code GuiRecipe} + {@code RunInput.fromDataset}.
     */
    @TestFactory
    List<DynamicTest> generatorInput() {
        return GoldenFiles.keys().stream().flatMap(key -> java.util.stream.Stream.of(Profile.values()).map(p ->
                DynamicTest.dynamicTest(key + " " + p.key, () -> {
                    EchoGolden golden = EchoGolden.load(key);
                    GuiRecipe.Prepared prepared = Port.call(() -> GuiRecipe.prepare(GoldenFiles.sml(key), key, p));
                    List<String> enabled = GuiRecipe.enabledJudges(prepared.sd);
                    RunInput in = Port.call(() -> RunInput.fromDataset(prepared.sd, LegacyCompat.STRICT));
                    int max = new io.github.steinbeck.seneca.judges.JudgeSet(new java.util.ArrayList<>(in.judges())).maxScore();
                    var gi = golden.generatorInput(p.key);
                    assertEquals(gi.arr("enabled").strings(), enabled, key + "/" + p.key + " enabled judges");
                    assertEquals(gi.lng("maxScore"), max, key + "/" + p.key + " generator max score");
                }))).toList();
    }
}
