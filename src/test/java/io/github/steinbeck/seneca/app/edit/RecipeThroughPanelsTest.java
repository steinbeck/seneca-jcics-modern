package io.github.steinbeck.seneca.app.edit;

import static io.github.steinbeck.seneca.app.GuiTestSupport.click;
import static io.github.steinbeck.seneca.app.GuiTestSupport.onEdt;
import static io.github.steinbeck.seneca.app.GuiTestSupport.shot;
import static io.github.steinbeck.seneca.app.edit.EditSupport.edit;
import static io.github.steinbeck.seneca.app.edit.EditSupport.named;
import static io.github.steinbeck.seneca.app.edit.EditSupport.press;
import static io.github.steinbeck.seneca.app.edit.EditSupport.showTab;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.swing.JCheckBox;
import javax.swing.JTable;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.app.ComponentNames;
import io.github.steinbeck.seneca.app.GuiTestSupport;
import io.github.steinbeck.seneca.app.GuiTestSupport.TestApp;
import io.github.steinbeck.seneca.app.TestHooks;
import io.github.steinbeck.seneca.app.dataset.DatasetFrame;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.config.GuiRecipe;
import io.github.steinbeck.seneca.config.JudgeConfiguration;
import io.github.steinbeck.seneca.config.Profile;
import io.github.steinbeck.seneca.golden.EchoGolden;
import io.github.steinbeck.seneca.golden.EchoRecords;
import io.github.steinbeck.seneca.golden.GoldenFiles;
import io.github.steinbeck.seneca.golden.JsonDiff;
import io.github.steinbeck.seneca.golden.JsonValue;
import io.github.steinbeck.seneca.judges.Judge;
import io.github.steinbeck.seneca.judges.TwoDSpectrumJudge;
import io.github.steinbeck.seneca.model.SenecaDataset;

/**
 * The golden "GUI recipe" ({@code config.GuiRecipe}, golden/FORMAT.md) done by hand in the dataset window,
 * for every bundled dataset and the golden profiles gui, paper and manual (plus all, and the variant
 * polycarpol@hblock): File &gt; Open, Atom Properties &gt; Autoassignment (iff no shifts were read), the H
 * counts a 2001 user had to type into the "Hydrogens" column, Judges &gt; "Autoconfigure Judge" in the
 * profile's order (unticking "Activate this Judge?" of a 2D judge left without assignment), for manual the
 * Hybridization and Heterobond tables edited cell by cell, and Annealing Schedule &gt; "New -&gt;" iff the
 * file has no schedule.
 *
 * <p>The resulting dataset must equal {@code GuiRecipe.prepare(file, key, profile, DEFAULT)} record by
 * record ({@link EchoRecords#configuredRecords}: atoms, the 7 spectra, schedules, the judges handed to the
 * generator, enabled judges and max score), and in STRICT the configured records of the golden echo file.
 */
public class RecipeThroughPanelsTest {

    static final List<String> KEYS = List.of("Hyperaspine", "bn-ye1", "eupatorio", "eurabidiol", "monochaetin", "narelin",
            "pinene", "polycarpol", "preconen", "triterpen");

    @TestFactory
    List<DynamicTest> recipeThroughTheGuiEqualsTheCoreRecipe() {
        List<DynamicTest> tests = new ArrayList<>();
        for (String key : KEYS)
            for (Profile p : List.of(Profile.GUI, Profile.PAPER, Profile.MANUAL, Profile.ALL))
                tests.add(DynamicTest.dynamicTest(key + " " + p.key, () -> run(key, p)));
        for (Profile p : List.of(Profile.GUI, Profile.PAPER, Profile.MANUAL))
            tests.add(DynamicTest.dynamicTest("polycarpol@hblock " + p.key, () -> run("polycarpol@hblock", p)));
        return tests;
    }

    static void run(String key, Profile profile) throws Exception {
        try (TestApp t = GuiTestSupport.newApp("recipe-" + key.replace('@', '_') + "-" + profile.key, TestHooks.none())) {
            SenecaDataset expected = GuiRecipe.prepare(GoldenFiles.sml(key), key, profile, t.app.compat).sd;
            DatasetFrame f = EditSupport.open(t, GoldenFiles.sml(key));
            SenecaDataset sd = f.dataset();
            configure(f, key, profile, expected);

            List<String> want = EchoRecords.configuredRecords(expected, profile, LegacyCompat.DEFAULT);
            List<String> got = onEdt(() -> EchoRecords.configuredRecords(sd, profile, LegacyCompat.DEFAULT));
            assertEquals(want.size(), got.size());
            for (int i = 0; i < want.size(); i++)
                JsonDiff.assertSameRecord(key + "/" + profile.key + " record " + i, want.get(i), got.get(i), Set.of());

            // and the golden echo file (recorded with the 2001 code, STRICT)
            List<String> strict = onEdt(() -> EchoRecords.configuredRecords(sd, profile, LegacyCompat.STRICT));
            List<String> golden = goldenConfigured(key, profile);
            List<String> compared = profile == Profile.GUI ? strict : strict.subList(strict.size() - 2, strict.size());
            assertEquals(golden.size(), compared.size());
            for (int i = 0; i < golden.size(); i++)
                JsonDiff.assertSameRecord(key + "/" + profile.key + " golden record " + i, golden.get(i), compared.get(i), Set.of());

            showTab(f, profile == Profile.MANUAL ? "Judges" : "Atom Properties");
            if (profile == Profile.MANUAL) onEdt(() -> named(f, ComponentNames.JUDGE_TABS, javax.swing.JTabbedPane.class).setSelectedIndex(5));
            shot(t.view, 1000, 700, "recipe-" + key.replace('@', '_') + "-" + profile.key);
        }
    }

    /**
     * What a user does in the window (the steps of {@code GuiRecipe.prepare}); also used by the scripted
     * end-to-end test {@code app.GuiEndToEndTest}.
     */
    public static void configure(DatasetFrame f, String key, Profile profile, SenecaDataset expected) {
        SenecaDataset sd = f.dataset();
        // Atom Properties: Autoassignment iff no atom has a 13C shift after loading
        showTab(f, "Atom Properties");
        boolean anyShift = false;
        for (HeavyAtom a : sd.getSetOfNodes()) anyShift |= a.shift13C != null;
        if (!anyShift) press(f, ComponentNames.AUTOASSIGN);

        // the H counts typed into the "Hydrogens" column
        JTable atoms = named(f, ComponentNames.ATOM_TABLE, JTable.class);
        if (key.endsWith(GuiRecipe.VARIANT_HBLOCK)) {
            for (int i = 0; i < expected.getSetOfNodes().length; i++) {
                int h = expected.getSetOfNodes()[i].hCount;
                if (sd.getSetOfNodes()[i].hCount != h) assertTrue(edit(atoms, i, 2, String.valueOf(h)));
            }
        } else {
            for (GuiRecipe.HOverride o : GuiRecipe.H_OVERRIDES)
                if (o.key().equals(key)) assertTrue(edit(atoms, o.atom(), 2, String.valueOf(o.hCount())));
        }

        // Judges: Autoconfigure Judge in the profile's order
        showTab(f, "Judges");
        List<String> press = new ArrayList<>(List.of("HHCOSYJudge", "HMBCJudge", "HOSECodeJudge"));
        if (profile == Profile.ALL || profile == Profile.MANUAL) press.add("SymmetryJudge");
        if (profile == Profile.GUI || profile == Profile.ALL) press.add("FeatureJudge");
        if (profile == Profile.ALL) press.addAll(List.of("HybridizationJudge", "HeteroBondsJudge"));
        for (String judge : press) {
            press(f, ComponentNames.JUDGE_AUTOCONFIGURE + judge);
            Judge j = sd.getJudge(judge);
            JCheckBox activate = named(f, ComponentNames.JUDGE_ACTIVATE + judge, JCheckBox.class);
            assertEquals(j.getEnabled(), EditSupport.selected(activate), judge + ": check box follows the judge");
            if (j instanceof TwoDSpectrumJudge two && j.getEnabled() && two.assignment == null) {
                click(activate);
                assertTrue(!j.getEnabled(), judge + " unticked");
            }
        }

        // manual: tick "Activate this Judge?" and edit the Hybridization and Heterobond tables
        if (profile == Profile.MANUAL) {
            JCheckBox hy = named(f, ComponentNames.JUDGE_ACTIVATE + "HybridizationJudge", JCheckBox.class);
            if (!EditSupport.selected(hy)) click(hy);
            JTable hyTable = named(f, ComponentNames.JUDGE_TABLE + "HybridizationJudge", JTable.class);
            for (int i = 0; i < sd.getSetOfNodes().length; i++) {
                HeavyAtom a = sd.getSetOfNodes()[i];
                if (a.shift13C == null || !a.symbol.equals("C")) continue;
                int col = a.shift13C >= 100f ? JudgeConfiguration.HYB_COLUMN_SP2 : JudgeConfiguration.HYB_COLUMN_SP3;
                assertTrue(edit(hyTable, i, col, "1.0"));
            }
            JCheckBox hb = named(f, ComponentNames.JUDGE_ACTIVATE + "HeteroBondsJudge", JCheckBox.class);
            if (!EditSupport.selected(hb)) click(hb);
            JTable hbTable = named(f, ComponentNames.JUDGE_TABLE + "HeteroBondsJudge", JTable.class);
            for (int i = 0; i < sd.getSetOfNodes().length; i++) {
                HeavyAtom a = sd.getSetOfNodes()[i];
                if (a.shift13C == null || !a.symbol.equals("C")) continue;
                if (a.shift13C >= 50f && a.shift13C < 100f) assertTrue(edit(hbTable, i, JudgeConfiguration.HETERO_COLUMN, Boolean.TRUE));
            }
        }

        // Annealing Schedule: New -> iff the file has none
        showTab(f, "Annealing Schedule");
        if (sd.annealingSchedules.isEmpty()) press(f, ComponentNames.SCHEDULE_NEW);
    }

    /** The configured records of the golden echo file for {@code profile} (all for gui, else judges + generatorInput). */
    static List<String> goldenConfigured(String key, Profile p) {
        String st = "configured_" + p.key;
        List<String> out = new ArrayList<>();
        for (String line : EchoGolden.load(key).lines) {
            JsonValue.JObject r = JsonValue.parse(line).asObject();
            boolean stage = r.has("stage") && st.equals(r.str("stage"));
            boolean gi = "generatorInput".equals(r.type()) && p.key.equals(r.str("profile"));
            if (stage || gi) out.add(line);
        }
        return out;
    }
}
