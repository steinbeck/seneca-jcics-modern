package io.github.steinbeck.seneca.verify;

import static io.github.steinbeck.seneca.app.GuiTestSupport.click;
import static io.github.steinbeck.seneca.app.GuiTestSupport.flushEdt;
import static io.github.steinbeck.seneca.app.GuiTestSupport.menuItem;
import static io.github.steinbeck.seneca.app.GuiTestSupport.onEdt;
import static io.github.steinbeck.seneca.app.GuiTestSupport.shot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.datatransfer.DataFlavor;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JTable;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.io.iterator.IteratingSDFReader;
import org.openscience.cdk.silent.SilentChemObjectBuilder;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.app.ComponentNames;
import io.github.steinbeck.seneca.app.GoldenRunAssert;
import io.github.steinbeck.seneca.app.GuiTestSupport;
import io.github.steinbeck.seneca.app.GuiTestSupport.TestApp;
import io.github.steinbeck.seneca.app.ScriptedUi;
import io.github.steinbeck.seneca.app.TestHooks;
import io.github.steinbeck.seneca.app.dataset.DatasetFrame;
import io.github.steinbeck.seneca.app.edit.RecipeThroughPanelsTest;
import io.github.steinbeck.seneca.app.run.StructureGenerationPanel;
import io.github.steinbeck.seneca.app.structure.StructureDisplayPanel;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.config.GuiRecipe;
import io.github.steinbeck.seneca.config.Profile;
import io.github.steinbeck.seneca.golden.EchoRecords;
import io.github.steinbeck.seneca.golden.Hashes;
import io.github.steinbeck.seneca.golden.JsonDiff;
import io.github.steinbeck.seneca.golden.JsonValue;
import io.github.steinbeck.seneca.golden.RunGolden;
import io.github.steinbeck.seneca.io.SpecMLWriter;
import io.github.steinbeck.seneca.model.SenecaDataset;
import io.github.steinbeck.seneca.structgen.RunResult;
import io.github.steinbeck.seneca.structgen.RunSlot;

/**
 * Independent end-to-end verification of the application (verifier "e2e-app"): everything driven through
 * the GUI (toolbar / menus / buttons, scripted dialogs), seeded through the hidden hook and compared with
 * golden/runs, on datasets and profiles the packaging agent's GuiEndToEndTest does not use; plus unseeded
 * runs, stop/reset mid-run, concurrent slots in two dataset windows, open/save/reopen round trip, and
 * export of results (Save MDL, Save SDF, Copy, Log Annealing Data, log files).
 */
class E2eAppVerifyTest {

    static final long CAP = RunGolden.ITERATION_CAP;

    // ------------------------------------------------------------------ helpers

    /** Toolbar "Open a file" with {@code file}; the new dataset window. */
    static DatasetFrame openViaToolbar(TestApp t, Path file) {
        t.ui.answerFiles(file.toFile());
        click(GuiTestSupport.find(t.view, ComponentNames.TOOLBAR + ".openAll", JButton.class));
        flushEdt();
        SenecaDataset sd = onEdt(t.app.datasets::current);
        assertNotNull(sd, "no dataset opened from " + file);
        DatasetFrame f = onEdt(() -> t.app.frameOf(sd));
        assertNotNull(f, "no dataset window for " + file);
        return f;
    }

    /** Opens {@code key} through the toolbar and applies the recipe through the panels; checks it. */
    static DatasetFrame openAndConfigure(TestApp t, String key, Profile profile) throws Exception {
        return configureChecked(t, openViaToolbar(t, GuiTestSupport.sml(key)), key, profile, Set.of());
    }

    static DatasetFrame configureChecked(TestApp t, DatasetFrame f, String key, Profile profile, Set<String> ignore) throws Exception {
        SenecaDataset recipe = GuiRecipe.prepare(GuiTestSupport.sml(key), key, profile, t.app.compat).sd;
        RecipeThroughPanelsTest.configure(f, key, profile, recipe);
        SenecaDataset sd = f.dataset();
        List<String> want = EchoRecords.configuredRecords(recipe, profile, LegacyCompat.DEFAULT);
        List<String> got = onEdt(() -> EchoRecords.configuredRecords(sd, profile, LegacyCompat.DEFAULT));
        assertEquals(want.size(), got.size(), key + "/" + profile.key + " configured record count");
        for (int i = 0; i < want.size(); i++)
            JsonDiff.assertSameRecord(key + "/" + profile.key + " configured record " + i, want.get(i), got.get(i), ignore);
        return f;
    }

    /** The "Structure Generation" tab of {@code f}, with its timer stopped (the test polls). */
    static StructureGenerationPanel runPanel(DatasetFrame f) {
        onEdt(() -> f.showTab("Structure Generation"));
        StructureGenerationPanel p = onEdt(() -> {
            for (Component c : GuiTestSupport.all(f)) if (c instanceof StructureGenerationPanel s) return s;
            return null;
        });
        assertNotNull(p, "Structure Generation tab");
        onEdt(() -> p.timer().stop());
        return p;
    }

    static void press(Component root, String name) {
        AbstractButton b = GuiTestSupport.find(root, name, AbstractButton.class);
        assertNotNull(b, "button " + name);
        click(b);
    }

    static void select(StructureGenerationPanel p, int... rows) {
        onEdt(() -> {
            p.table().clearSelection();
            for (int r : rows) p.table().addRowSelectionInterval(r, r);
        });
    }

    static void settle(TestApp t) throws Exception {
        t.app.awaitControl(120_000);
        flushEdt();
        flushEdt();
    }

    static RunResult await(RunSlot s, long millis) {
        assertTrue(GuiTestSupport.waitFor(() -> s.lastResult() != null || s.failure() != null, millis), "run " + s.id() + " did not end");
        assertNull(s.failure(), "run " + s.id() + " failed");
        return s.lastResult();
    }

    static String cell(StructureGenerationPanel p, int row, int col) {
        return onEdt(() -> String.valueOf(p.model().getValueAt(row, col)));
    }

    // ------------------------------------------------------------------ 1. seeded runs vs golden

    /** One seeded GUI run: {@code slots} slots added, the last one started (seed = base + slots - 1). */
    record Case(String key, Profile profile, int seed, long base) {
        int slots() {
            return (int) (seed - base + 1);
        }

        @Override
        public String toString() {
            return key + "." + profile.key + ".seed" + seed + " (base " + base + ")";
        }
    }

    static void seededRun(Case c) throws Exception {
        RunGolden golden = GoldenRunAssert.golden(c.key(), c.profile().key, c.seed());
        String shots = "verify-e2e-" + c.key() + "-" + c.profile().key + "-seed" + c.seed();
        try (TestApp t = GuiTestSupport.newApp(shots, new TestHooks(c.base(), CAP))) {
            DatasetFrame f = openAndConfigure(t, c.key(), c.profile());
            List<String> enabled = onEdt(() -> GuiRecipe.enabledJudges(f.dataset()));
            StructureGenerationPanel p = runPanel(f);
            for (int i = 0; i < c.slots(); i++) press(f, ComponentNames.RUN_ADD_SLOT);
            settle(t);
            int row = c.slots() - 1;
            assertEquals(c.slots(), onEdt(() -> p.table().getRowCount()));
            select(p, row);
            press(f, ComponentNames.RUN_START);
            settle(t);
            RunSlot slot = f.runs().slots().get(row);
            assertEquals(Long.valueOf(c.seed()), t.app.runOptions(slot).seed());
            RunResult r = await(slot, 900_000);
            for (int i = 0; i < row; i++) assertFalse(f.runs().slots().get(i).hasRun(), "unselected row ran");
            flushEdt();
            GoldenRunAssert.assertMatches(golden, enabled, r);

            onEdt(() -> {
                for (int i = 0; i <= row; i++) p.poll();
            });
            assertEquals("Holding Results", cell(p, row, 5));
            assertEquals(String.valueOf(golden.record.lng("iterations")), cell(p, row, 6));
            if (!golden.record.isNull("bestScore")) {
                String eval = onEdt(() -> p.evaluationPane().getText());
                long max = golden.record.lng("maxScore");
                String expect = "Score: " + golden.record.lng("bestScore") + (max > 0 ? "/" + max : "");
                assertTrue(eval.contains(expect), "evaluation shows " + expect + ": " + eval);
                assertTrue(onEdt(() -> p.bestStructureView().structure().drawable()), "best structure drawn");
            }
            shot(t.view, 1000, 700, shots);

            press(f, ComponentNames.RUN_SUMMARIZE);
            StructureDisplayPanel display = (StructureDisplayPanel) t.ui.lastWindow().content;
            List<StructureDisplayPanel.Tile> tiles = onEdt(display::tileList);
            assertEquals(golden.record.lng("nResults"), tiles.size(), "one tile per result");
            Set<String> goldenHashes = goldenResultHashes(golden);
            for (StructureDisplayPanel.Tile tile : tiles)
                assertTrue(goldenHashes.contains(Hashes.hash(GoldenRunAssert.ct(onEdt(() -> tile.structure().mdl("x"))))),
                        "tile " + tile.title() + " is a golden result");
        }
    }

    static Set<String> goldenResultHashes(RunGolden g) {
        Set<String> s = new HashSet<>();
        JsonValue.JArray results = g.record.arr("results");
        for (int i = 0; i < results.size(); i++) s.add(results.get(i).asObject().str("hash"));
        return s;
    }

    @TestFactory
    List<DynamicTest> seededGuiRunsReproduceGolden() {
        List<Case> cases = List.of(
                new Case("Hyperaspine", Profile.GUI, 1, 1),
                new Case("eupatorio", Profile.PAPER, 1, 1),
                new Case("eupatorio", Profile.GUI, 2, 2),        // base 2, slot local0 -> seed 2
                new Case("bn-ye1", Profile.PAPER, 1, 1),
                new Case("pinene", Profile.PAPER, 2, 1),         // two slots, row 2
                new Case("eurabidiol", Profile.GUI, 3, 1),       // three slots, row 3
                new Case("Hyperaspine", Profile.GUI, 3, 3),
                // round 2
                new Case("eupatorio", Profile.GUI, 3, 1),       // three slots, row 3
                new Case("bn-ye1", Profile.GUI, 2, 1),
                new Case("Hyperaspine", Profile.GUI, 2, 2),
                new Case("eurabidiol", Profile.PAPER, 3, 3));
        List<DynamicTest> l = new ArrayList<>();
        for (Case c : cases) l.add(DynamicTest.dynamicTest(c.toString(), () -> seededRun(c)));
        return l;
    }

    /** Longer golden runs (manual profile with Symmetry/Hybridization/HeteroBonds; 390k-1.1M iterations). */
    @Tag("slow")
    @TestFactory
    List<DynamicTest> seededGuiRunsReproduceGoldenSlow() {
        List<Case> cases = List.of(
                new Case("pinene", Profile.MANUAL, 1, 1),
                new Case("monochaetin", Profile.PAPER, 1, 1),
                new Case("Hyperaspine", Profile.PAPER, 2, 1),
                // round 2
                new Case("preconen", Profile.MANUAL, 1, 1),
                new Case("triterpen", Profile.PAPER, 2, 1),
                new Case("polycarpol", Profile.PAPER, 1, 1),
                new Case("monochaetin", Profile.PAPER, 3, 3));
        List<DynamicTest> l = new ArrayList<>();
        for (Case c : cases) l.add(DynamicTest.dynamicTest(c.toString(), () -> seededRun(c)));
        return l;
    }

    // ------------------------------------------------------------------ 2. concurrency

    /**
     * Two dataset windows at once: Hyperaspine "gui" with 3 slots (seeds 1-3) and eupatorio "paper" with
     * 2 slots (seeds 1-2), all five started before any finishes; each must equal its golden run.
     */
    @Test
    void fiveConcurrentRunsInTwoWindowsReproduceGolden() throws Exception {
        try (TestApp t = GuiTestSupport.newApp("verify-e2e-concurrent", new TestHooks(1L, CAP))) {
            DatasetFrame fa = openAndConfigure(t, "Hyperaspine", Profile.GUI);
            List<String> enA = onEdt(() -> GuiRecipe.enabledJudges(fa.dataset()));
            StructureGenerationPanel pa = runPanel(fa);
            for (int i = 0; i < 3; i++) press(fa, ComponentNames.RUN_ADD_SLOT);

            DatasetFrame fb = openAndConfigure(t, "eupatorio", Profile.PAPER);
            List<String> enB = onEdt(() -> GuiRecipe.enabledJudges(fb.dataset()));
            StructureGenerationPanel pb = runPanel(fb);
            for (int i = 0; i < 2; i++) press(fb, ComponentNames.RUN_ADD_SLOT);
            settle(t);

            select(pa, 0, 1, 2);
            select(pb, 0, 1);
            press(fa, ComponentNames.RUN_START);
            press(fb, ComponentNames.RUN_START);
            settle(t);
            // concurrently: at least two runs alive at the same moment
            boolean overlap = GuiTestSupport.waitFor(() -> {
                int n = 0;
                for (RunSlot s : fa.runs().slots()) if (s.isRunning()) n++;
                for (RunSlot s : fb.runs().slots()) if (s.isRunning()) n++;
                return n >= 2;
            }, 10_000);
            assertTrue(overlap, "runs did not overlap");

            for (int i = 0; i < 3; i++) {
                RunResult r = await(fa.runs().slots().get(i), 600_000);
                GoldenRunAssert.assertMatches(GoldenRunAssert.golden("Hyperaspine", "gui", i + 1), enA, r);
            }
            for (int i = 0; i < 2; i++) {
                RunResult r = await(fb.runs().slots().get(i), 600_000);
                GoldenRunAssert.assertMatches(GoldenRunAssert.golden("eupatorio", "paper", i + 1), enB, r);
            }
            flushEdt();
            for (int i = 0; i < 3; i++) onEdt(pa::poll);
            for (int i = 0; i < 2; i++) onEdt(pb::poll);
            for (int i = 0; i < 3; i++) assertEquals("Holding Results", cell(pa, i, 5));
            for (int i = 0; i < 2; i++) assertEquals("Holding Results", cell(pb, i, 5));
            // the chart shows one line per slot (+ max score)
            assertTrue(onEdt(() -> pa.chart().data().hasData()));
            shot(t.view, 1000, 700, "verify-e2e-concurrent");

            // Logbook: five finished runs; seneca.results.sdf has every best structure
            String logbook = onEdt(t.app.logbook::text);
            assertEquals(5, count(logbook, "Simulated annealing run finished."), logbook);
            String sdf = Files.readString(t.app.paths.resultsFile());
            for (DatasetFrame f : List.of(fa, fb))
                for (RunSlot s : f.runs().slots())
                    assertTrue(sdf.contains(s.lastResult().bestStructure()), "best structure of " + s.id() + " in seneca.results.sdf");

            // Summarize merges the three Hyperaspine runs: best tile = best of the three
            press(fa, ComponentNames.RUN_SUMMARIZE);
            StructureDisplayPanel display = (StructureDisplayPanel) t.ui.lastWindow().content;
            List<StructureDisplayPanel.Tile> tiles = onEdt(display::tileList);
            long best = 0;
            for (RunSlot s : fa.runs().slots()) best = Math.max(best, s.lastResult().bestScore().score);
            assertEquals("Score " + best, tiles.get(0).title());
            assertTrue(tiles.size() >= 30 && tiles.size() <= 90, "merged tiles " + tiles.size());
        }
    }

    static int count(String s, String what) {
        int n = 0;
        for (int i = s.indexOf(what); i >= 0; i = s.indexOf(what, i + 1)) n++;
        return n;
    }

    // ------------------------------------------------------------------ 3. unseeded runs

    /** The application default: no seed. Two slots, run, then run again: all finish, the draws differ. */
    @Test
    void unseededRunsFinishAndDiffer() throws Exception {
        try (TestApp t = GuiTestSupport.newApp("verify-e2e-unseeded", TestHooks.none())) {
            DatasetFrame f = openAndConfigure(t, "eupatorio", Profile.PAPER);
            StructureGenerationPanel p = runPanel(f);
            press(f, ComponentNames.RUN_ADD_SLOT);
            press(f, ComponentNames.RUN_ADD_SLOT);
            settle(t);
            select(p, 0, 1);
            press(f, ComponentNames.RUN_START);
            settle(t);
            RunSlot s0 = f.runs().slots().get(0), s1 = f.runs().slots().get(1);
            assertNull(t.app.runOptions(s0).seed(), "unseeded by default");
            assertEquals(0, t.app.runOptions(s0).iterationCap(), "no cap by default");
            RunResult r0 = await(s0, 600_000), r1 = await(s1, 600_000);
            assertTrue(r0.bestScore().score <= r0.maxScore());
            assertTrue(r1.bestScore().score <= r1.maxScore());
            assertNotEquals(r0.rngDraws(), r1.rngDraws(), "two unseeded runs used the same random numbers");
            // run row 1 again (Start on a finished slot)
            select(p, 0);
            press(f, ComponentNames.RUN_START);
            settle(t);
            assertTrue(GuiTestSupport.waitFor(() -> s0.lastResult() != r0 && s0.lastResult() != null || s0.failure() != null, 600_000));
            assertNull(s0.failure());
            RunResult again = s0.lastResult();
            assertNotEquals(r0.rngDraws(), again.rngDraws(), "a second unseeded run repeated the first");
            flushEdt();
            onEdt(p::poll);
            onEdt(p::poll);
            assertEquals("Holding Results", cell(p, 0, 5));
            assertEquals(String.valueOf(again.iterations()), cell(p, 0, 6));
            press(f, ComponentNames.RUN_SUMMARIZE);
            StructureDisplayPanel display = (StructureDisplayPanel) t.ui.lastWindow().content;
            assertFalse(onEdt(display::tileList).isEmpty());
        }
    }

    // ------------------------------------------------------------------ 4. stop / reset mid-run, export

    /**
     * narelin "paper" seed 1 (2.1 million iterations): stop while running, results of the stopped run can
     * be summarized and exported (Save MDL, Save SDF, Copy, Log Annealing Data); reset clears; restart.
     */
    @Test
    void stopMidRunThenExportThenResetAndRestart() throws Exception {
        try (TestApp t = GuiTestSupport.newApp("verify-e2e-stop", new TestHooks(1L, CAP))) {
            DatasetFrame f = openAndConfigure(t, "narelin", Profile.PAPER);
            StructureGenerationPanel p = runPanel(f);
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            onEdt(() -> p.setStdout(new PrintStream(stdout, true, StandardCharsets.UTF_8)));
            press(f, ComponentNames.RUN_ADD_SLOT);
            settle(t);
            select(p, 0);
            press(f, ComponentNames.RUN_START);
            settle(t);
            RunSlot s = f.runs().slots().get(0);
            assertTrue(GuiTestSupport.waitFor(() -> s.isRunning() && s.status().iteration() > 20_000
                    && s.status().annealingLogSize() >= 3, 120_000), "run under way");
            onEdt(p::poll);
            assertEquals("Running", cell(p, 0, 5));
            assertTrue(onEdt(() -> p.chart().data().hasData()), "chart has data while running");
            shot(t.view, 1000, 700, "verify-e2e-stop-1-running");

            press(f, ComponentNames.RUN_STOP);
            RunResult r = await(s, 60_000);
            assertTrue(r.stoppedByUser());
            assertTrue(r.iterations() < 2_094_988, "stopped before the end");
            flushEdt();
            onEdt(p::poll);
            assertEquals(String.valueOf(r.iterations()), cell(p, 0, 6));
            assertNotEquals("Running", cell(p, 0, 5));

            // export: Summarize -> Save MDL, Save SDF, Copy
            press(f, ComponentNames.RUN_SUMMARIZE);
            ScriptedUi.Window w = t.ui.lastWindow();
            StructureDisplayPanel display = (StructureDisplayPanel) w.content;
            List<StructureDisplayPanel.Tile> tiles = onEdt(display::tileList);
            assertEquals(r.result().size(), tiles.size(), "tiles = results of the stopped run");
            Set<String> resultHashes = new HashSet<>();
            for (int i = 0; i < r.result().structures.size(); i++)
                resultHashes.add(Hashes.hash(GoldenRunAssert.ct(r.result().structures.get(i))));

            Path out = Files.createDirectories(t.dir.resolve("export"));
            t.ui.answerFiles(out.resolve("narelin.mol").toFile());
            press(display, ComponentNames.STRUCTURE_SAVE_MDL);
            for (int g = 0; g < tiles.size(); g++) {
                Path mol = out.resolve("narelin" + g + ".mol");
                assertTrue(Files.exists(mol), mol + " written");
                String text = Files.readString(mol, StandardCharsets.ISO_8859_1);
                assertTrue(resultHashes.contains(Hashes.hash(GoldenRunAssert.ct(text))), mol + " is a run result");
                assertCdkReads(text, 1, mol.toString());
            }
            t.ui.answerFiles(out.resolve("narelin-results").toFile());
            press(display, ComponentNames.STRUCTURE_SAVE_SDF);
            Path sdfFile = out.resolve("narelin-results.sdf");
            assertTrue(Files.exists(sdfFile), "Save SDF appends .sdf");
            String sdf = Files.readString(sdfFile, StandardCharsets.ISO_8859_1);
            assertEquals(tiles.size(), count(sdf, "$$$$"));
            assertCdkReads(sdf, tiles.size(), sdfFile.toString());

            onEdt(() -> display.select(tiles.get(0)));
            press(display, ComponentNames.STRUCTURE_COPY);
            assertNotNull(t.ui.clipboardContents, "Copy put something on the clipboard");
            String copied = (String) t.ui.clipboardContents.getTransferData(DataFlavor.stringFlavor);
            assertTrue(resultHashes.contains(Hashes.hash(GoldenRunAssert.ct(copied))), "copied MDL is a result");

            // Load MDL of the exported files into a fresh Structure Display
            click(menuItem(t.menuBar, "View", "Current Structures"));
            ScriptedUi.Window w2 = t.ui.lastWindow();
            if (w2 != w) {
                StructureDisplayPanel d2 = (StructureDisplayPanel) w2.content;
                t.ui.answerFiles(out.resolve("narelin0.mol").toFile(), out.resolve("narelin1.mol").toFile());
                press(d2, ComponentNames.STRUCTURE_LOAD_MDL);
                assertEquals(2, onEdt(d2::tileList).size(), "Load MDL of two exported files");
            }

            // Log Annealing Data: stdout and seneca.annealing.log
            press(f, ComponentNames.RUN_LOG_ANNEALING);
            String logged = stdout.toString(StandardCharsets.UTF_8);
            assertFalse(logged.isBlank(), "Log Annealing Data printed nothing");
            String annealingFile = Files.readString(t.app.paths.annealingLogFile());
            assertEquals(logged.replace("\r\n", "\n"), annealingFile);

            // Reset selected: chart empty, row idle; Start again runs again
            press(f, ComponentNames.RUN_RESET);
            settle(t);
            assertFalse(onEdt(() -> p.chart().data().hasData()));
            assertFalse(s.hasRun());
            assertEquals("idle", cell(p, 0, 5));
            press(f, ComponentNames.RUN_START);
            settle(t);
            assertTrue(GuiTestSupport.waitFor(() -> s.isRunning() && s.status().iteration() > 1000, 60_000), "restarted");
            // closing the window while running (answer No): the run stops
            t.ui.answerDialog(JOptionPane.NO_OPTION);
            press(f, ComponentNames.DATASET_CLOSE);
            settle(t);
            assertTrue(GuiTestSupport.waitFor(() -> !s.isRunning(), 30_000), "closing the window stopped the run");
            assertTrue(onEdt(() -> t.app.datasetFrames().isEmpty()));
        }
    }

    static void assertCdkReads(String sdf, int expected, String what) throws Exception {
        int n = 0;
        try (IteratingSDFReader rd = new IteratingSDFReader(new StringReader(sdf.endsWith("$$$$\n") ? sdf : sdf + "$$$$\n"),
                SilentChemObjectBuilder.getInstance())) {
            while (rd.hasNext()) {
                IAtomContainer m = rd.next();
                assertTrue(m.getAtomCount() > 0, what + ": empty molecule");
                n++;
            }
        }
        assertEquals(expected, n, what + ": molecules CDK reads");
    }

    // ------------------------------------------------------------------ 5. open / save / reopen

    /**
     * Open, configure through the panels, File &gt; Save &gt; SENECA Dataset, close (No), File &gt; Open the
     * saved file, configure again (judges are not saved, D9), run seed 1: equals golden. Saving the
     * reopened dataset again gives the same bytes.
     */
    @TestFactory
    List<DynamicTest> saveReopenRunReproducesGolden() {
        return List.of(
                DynamicTest.dynamicTest("Hyperaspine gui seed1", () -> roundTrip("Hyperaspine", Profile.GUI, 1)),
                DynamicTest.dynamicTest("eupatorio paper seed1", () -> roundTrip("eupatorio", Profile.PAPER, 1)));
    }

    static void roundTrip(String key, Profile profile, int seed) throws Exception {
        try (TestApp t = GuiTestSupport.newApp("verify-e2e-roundtrip-" + key, new TestHooks(1L, CAP))) {
            DatasetFrame f = openAndConfigure(t, key, profile);
            SenecaDataset before = f.dataset();
            Path out = Files.createDirectories(t.dir.resolve("saved"));
            t.ui.answerFiles(out.resolve(key + "-configured").toFile());
            click(menuItem(t.menuBar, "File", "Save", "SENECA Dataset"));
            flushEdt();
            Path saved = out.resolve(key + "-configured.sml");
            assertTrue(Files.exists(saved), "saved " + saved);
            String text1 = Files.readString(saved, StandardCharsets.UTF_8);

            t.ui.answerDialog(JOptionPane.NO_OPTION);
            press(f, ComponentNames.DATASET_CLOSE);
            settle(t);
            assertTrue(onEdt(() -> t.app.datasetFrames().isEmpty()), "closed");

            t.ui.answerFiles(saved.toFile());
            click(menuItem(t.menuBar, "File", "Open", "SENECA Dataset"));
            flushEdt();
            SenecaDataset back = onEdt(t.app.datasets::current);
            assertNotNull(back, "reopened");
            DatasetFrame g = onEdt(() -> t.app.frameOf(back));
            assertEquals(before.getSetOfNodes().length, back.getSetOfNodes().length);
            for (int i = 0; i < before.getSetOfNodes().length; i++) {
                HeavyAtom a = before.getSetOfNodes()[i], b = back.getSetOfNodes()[i];
                assertEquals(a.symbol, b.symbol, "atom " + i);
                assertEquals(a.hCount, b.hCount, "H of atom " + i);
                assertEquals(a.shift13C, b.shift13C, "shift of atom " + i);
            }
            assertEquals(before.annealingSchedules.size(), back.annealingSchedules.size());
            for (int i = 0; i < before.annealingSchedules.size(); i++)
                assertEquals(before.annealingSchedules.get(i).toString(), back.annealingSchedules.get(i).toString());
            String text2 = onEdt(() -> SpecMLWriter.write(back, LegacyCompat.DEFAULT));
            assertEquals(text1, text2, "saving the reopened dataset gives the same SML");

            // configure again and run
            // ignored after the round trip (both as in 2001): SpecMLWriter writes atom ids "a0".. that the 2001 files
            // lack (read back as ""), and a null solvent as the text "null"
            configureChecked(t, g, key, profile, Set.of("$.atoms[*].id"));
            List<String> enabled = onEdt(() -> GuiRecipe.enabledJudges(back));
            StructureGenerationPanel p = runPanel(g);
            for (int i = 0; i < seed; i++) press(g, ComponentNames.RUN_ADD_SLOT);
            settle(t);
            select(p, seed - 1);
            press(g, ComponentNames.RUN_START);
            settle(t);
            RunResult r = await(g.runs().slots().get(seed - 1), 600_000);
            GoldenRunAssert.assertMatches(GoldenRunAssert.golden(key, profile.key, seed), enabled, r);
        }
    }

    // ------------------------------------------------------------------ 5b. round 2: Clear Slot List, best-structure window

    /**
     * Clear Slot List while a run is going: the run stops, the table empties, the next slot is local0 again
     * (2001), so with seed base 2 it runs seed 2 and reproduces golden eupatorio.paper.seed2.
     */
    @Test
    void clearSlotListStopsRunsAndRestartsNumbering() throws Exception {
        try (TestApp t = GuiTestSupport.newApp("verify-e2e-clear", new TestHooks(2L, CAP))) {
            DatasetFrame f = openAndConfigure(t, "narelin", Profile.PAPER);
            StructureGenerationPanel p = runPanel(f);
            for (int i = 0; i < 3; i++) press(f, ComponentNames.RUN_ADD_SLOT);
            settle(t);
            select(p, 2);
            press(f, ComponentNames.RUN_START);
            settle(t);
            RunSlot running = f.runs().slots().get(2);
            assertEquals("local2", running.id());
            assertTrue(GuiTestSupport.waitFor(() -> running.isRunning() && running.status().iteration() > 5000, 60_000));
            press(f, ComponentNames.RUN_CLEAR);
            settle(t);
            assertTrue(GuiTestSupport.waitFor(() -> !running.isRunning(), 30_000), "Clear Slot List stopped the run");
            assertEquals(0, onEdt(() -> p.table().getRowCount()));
            assertTrue(f.runs().slots().isEmpty());

            // reconfigure: a different dataset in a second window would be another test; here the slot ids
            press(f, ComponentNames.RUN_ADD_SLOT);
            settle(t);
            RunSlot s0 = f.runs().slots().get(0);
            assertEquals("local0", s0.id(), "slot numbering restarts after Clear Slot List");
            assertEquals(Long.valueOf(2L), t.app.runOptions(s0).seed());
            List<String> enabled = onEdt(() -> GuiRecipe.enabledJudges(f.dataset()));
            // a short golden run for the check: eupatorio paper seed 2 in another window, slot local0 of it
            DatasetFrame g = openAndConfigure(t, "eupatorio", Profile.PAPER);
            List<String> enG = onEdt(() -> GuiRecipe.enabledJudges(g.dataset()));
            StructureGenerationPanel pg = runPanel(g);
            press(g, ComponentNames.RUN_ADD_SLOT);
            press(g, ComponentNames.RUN_ADD_SLOT);
            settle(t);
            press(g, ComponentNames.RUN_CLEAR);
            settle(t);
            press(g, ComponentNames.RUN_ADD_SLOT);
            settle(t);
            assertEquals(1, g.runs().slots().size());
            RunSlot gs = g.runs().slots().get(0);
            assertEquals("local0", gs.id());
            select(pg, 0);
            press(g, ComponentNames.RUN_START);
            settle(t);
            GoldenRunAssert.assertMatches(GoldenRunAssert.golden("eupatorio", "paper", 2), enG, await(gs, 300_000));
            assertFalse(enabled.isEmpty());
        }
    }

    /**
     * Double click on the best structure opens it in a Structure Display window; Save MDL and Save SDF there
     * export exactly the run's best structure; seneca.results.sdf is readable by CDK.
     */
    @Test
    void bestStructureWindowExportsTheBestStructure() throws Exception {
        try (TestApp t = GuiTestSupport.newApp("verify-e2e-best", new TestHooks(1L, CAP))) {
            DatasetFrame f = openAndConfigure(t, "Hyperaspine", Profile.GUI);
            StructureGenerationPanel p = runPanel(f);
            press(f, ComponentNames.RUN_ADD_SLOT);
            settle(t);
            select(p, 0);
            press(f, ComponentNames.RUN_START);
            settle(t);
            RunResult r = await(f.runs().slots().get(0), 300_000);
            flushEdt();
            onEdt(p::poll);
            Component view = GuiTestSupport.find(f, ComponentNames.RUN_BEST_STRUCTURE, Component.class);
            assertNotNull(view);
            int before = t.ui.windows.size();
            onEdt(() -> {
                java.awt.event.MouseEvent e = new java.awt.event.MouseEvent(view, java.awt.event.MouseEvent.MOUSE_CLICKED,
                        System.currentTimeMillis(), 0, 10, 10, 2, false, java.awt.event.MouseEvent.BUTTON1);
                for (java.awt.event.MouseListener l : view.getMouseListeners()) l.mouseClicked(e);
            });
            flushEdt();
            assertEquals(before + 1, t.ui.windows.size(), "double click opened a window");
            StructureDisplayPanel d = (StructureDisplayPanel) t.ui.lastWindow().content;
            List<StructureDisplayPanel.Tile> tiles = onEdt(d::tileList);
            assertEquals(1, tiles.size());
            String bestHash = Hashes.hash(GoldenRunAssert.ct(r.bestStructure()));
            assertEquals(bestHash, Hashes.hash(GoldenRunAssert.ct(onEdt(() -> tiles.get(0).structure().mdl("x")))));
            Path out = Files.createDirectories(t.dir.resolve("best"));
            t.ui.answerFiles(out.resolve("best.mol").toFile());
            press(d, ComponentNames.STRUCTURE_SAVE_MDL);
            Path mol = out.resolve("best0.mol");
            if (!Files.exists(mol)) mol = out.resolve("best.mol");
            assertTrue(Files.exists(mol), "Save MDL wrote " + mol + "; dir: " + listing(out));
            assertEquals(bestHash, Hashes.hash(GoldenRunAssert.ct(Files.readString(mol, StandardCharsets.ISO_8859_1))));
            t.ui.answerFiles(out.resolve("best.sdf").toFile());
            press(d, ComponentNames.STRUCTURE_SAVE_SDF);
            assertCdkReads(Files.readString(out.resolve("best.sdf"), StandardCharsets.ISO_8859_1), 1, "best.sdf");
            // seneca.results.sdf of the run (log file) is a readable SD file with the best structure
            String results = Files.readString(t.app.paths.resultsFile(), StandardCharsets.ISO_8859_1);
            assertTrue(results.contains(r.bestStructure()));
            assertCdkReads(results, count(results, "$$$$"), "seneca.results.sdf");
        }
    }

    static String listing(Path dir) throws Exception {
        try (var s = Files.list(dir)) {
            return s.map(x -> x.getFileName().toString()).sorted().toList().toString();
        }
    }

    // ------------------------------------------------------------------ 6. the real home directory

    /** Nothing in this class may have touched the user's real ~/seneca.* (user.home is redirected). */
    @Test
    void logFilesOfTheTestAppAreInTheScratchDirectory() throws Exception {
        try (TestApp t = GuiTestSupport.newApp("verify-e2e-paths", TestHooks.none())) {
            String realHome = System.getenv("HOME");
            assertNotNull(realHome);
            assertFalse(t.app.paths.logFile().startsWith(Path.of(realHome).resolve("seneca.log")));
            assertTrue(t.app.paths.logFile().startsWith(t.dir));
            assertTrue(t.app.paths.preferencesFile().startsWith(t.dir));
        }
    }
}
