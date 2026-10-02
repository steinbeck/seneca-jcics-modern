package io.github.steinbeck.seneca.golden;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.config.GuiRecipe;
import io.github.steinbeck.seneca.config.Profile;

/**
 * {@link EchoRecords#configuredRecords} (added for the GUI tests, which compare a dataset configured
 * through the GUI with the recipe's): on the recipe's dataset (STRICT) it reproduces the configured
 * records of the echo file of every dataset key and profile (atoms, spectra and schedules exist in the
 * echo file only for "gui").
 */
class ConfiguredRecordsTest {

    @TestFactory
    List<DynamicTest> configuredRecordsEqualTheEchoFile() {
        List<DynamicTest> tests = new ArrayList<>();
        for (String key : GoldenFiles.keys()) {
            for (Profile p : EchoRecords.ECHO_PROFILES) {
                tests.add(DynamicTest.dynamicTest(key + " " + p.key, () -> {
                    EchoGolden golden = EchoGolden.load(key);
                    GuiRecipe.Prepared prep = GuiRecipe.prepare(GoldenFiles.sml(key), key, p, LegacyCompat.STRICT);
                    List<String> port = EchoRecords.configuredRecords(prep.sd, p, LegacyCompat.STRICT);
                    assertEquals(11, port.size());
                    String st = "configured_" + p.key;
                    List<String> expected = new ArrayList<>();
                    for (String line : golden.lines) {
                        JsonValue.JObject r = JsonValue.parse(line).asObject();
                        boolean stage = r.has("stage") && st.equals(r.str("stage"));
                        boolean gi = "generatorInput".equals(r.type()) && p.key.equals(r.str("profile"));
                        if (stage || gi) expected.add(line);
                    }
                    List<String> compared = p == Profile.GUI ? port : port.subList(9, 11);
                    assertEquals(expected.size(), compared.size(), key + "/" + p.key + ": number of records");
                    for (int i = 0; i < expected.size(); i++)
                        JsonDiff.assertSameRecord(key + "/" + p.key + " record " + i, expected.get(i), compared.get(i), Set.of());
                }));
            }
        }
        return tests;
    }
}
