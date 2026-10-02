package io.github.steinbeck.seneca.app;

import static io.github.steinbeck.seneca.app.GuiTestSupport.click;
import static io.github.steinbeck.seneca.app.GuiTestSupport.flushEdt;
import static io.github.steinbeck.seneca.app.GuiTestSupport.onEdt;
import static io.github.steinbeck.seneca.app.GuiTestSupport.shot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JTable;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.app.GuiTestSupport.TestApp;
import io.github.steinbeck.seneca.app.dataset.DatasetFrame;
import io.github.steinbeck.seneca.app.edit.RecipeThroughPanelsTest;
import io.github.steinbeck.seneca.app.run.StructureGenerationPanel;
import io.github.steinbeck.seneca.app.structure.StructureDisplayPanel;
import io.github.steinbeck.seneca.app.ui.EdtRunListener;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.config.GuiRecipe;
import io.github.steinbeck.seneca.config.Profile;
import io.github.steinbeck.seneca.golden.EchoRecords;
import io.github.steinbeck.seneca.golden.JsonDiff;
import io.github.steinbeck.seneca.golden.RunGolden;
import io.github.steinbeck.seneca.model.SenecaDataset;
import io.github.steinbeck.seneca.structgen.RunController;
import io.github.steinbeck.seneca.structgen.RunInput;
import io.github.steinbeck.seneca.structgen.RunListener;
import io.github.steinbeck.seneca.structgen.RunResult;
import io.github.steinbeck.seneca.structgen.RunSlot;
import io.github.steinbeck.seneca.structgen.RunStatus;

/**
 * Scripted end-to-end runs (docs/port/gui-design.md, "Testing without a human"): what a user does in the
 * GUI, from File &gt; Open to the results, with the run seeded through the hidden hook ({@link TestHooks},
 * {@code seneca.seed}), compared with the golden runs (golden/runs/&lt;key&gt;.&lt;profile&gt;.seed&lt;s&gt;.json).
 *
 * <p>Covered: pinene and eurabidiol with the golden "gui" and "paper" recipes, polycarpol with "paper"
 * (tagged slow like every golden run above 200,000 iterations; about 15 s). polycarpol "gui" has no complete golden run (the 2001 ring finder crashed in
 * all three seeds, Q1), eurabidiol seed 2 and pinene seed 3 neither (no result in 2001); the seeds used
 * here are the first ones with a complete golden run.
 */
class GuiEndToEndTest {

    static final long CAP = RunGolden.ITERATION_CAP;

    @Test
    void pineneGuiRecipeThroughTheGuiReproducesGoldenSeed1() throws Exception {
        endToEnd("pinene", Profile.GUI, 1);
    }

    @Test
    void pinenePaperRecipeThroughTheGuiReproducesGoldenSeed1() throws Exception {
        endToEnd("pinene", Profile.PAPER, 1);
    }

    @Test
    void eurabidiolGuiRecipeThroughTheGuiReproducesGoldenSeed1() throws Exception {
        endToEnd("eurabidiol", Profile.GUI, 1);
    }

    @Test
    void eurabidiolPaperRecipeThroughTheGuiReproducesGoldenSeed1() throws Exception {
        endToEnd("eurabidiol", Profile.PAPER, 1);
    }

    /** polycarpol "paper" seed 2 (435,377 iterations; seed 1 has 761,973). */
    @Tag("slow")
    @Test
    void polycarpolPaperRecipeThroughTheGuiReproducesGoldenSeed2() throws Exception {
        endToEnd("polycarpol", Profile.PAPER, 2);
    }

    /**
     * Everything through the GUI, as a user does it:
     * <ol>
     *   <li>toolbar "Open a file" (scripted file dialog answers {@code legacy/original/sml/<key>.sml});</li>
     *   <li>the recipe in the dataset window ({@code app.edit.RecipeThroughPanelsTest#configure}): Atom
     *       Properties &gt; Autoassignment iff no shifts were read, the H counts typed into the "Hydrogens"
     *       column, Judges &gt; "Autoconfigure Judge" in the profile's order, unticking "Activate this
     *       Judge?" of a 2D judge left without assignment, Annealing Schedule &gt; "New -&gt;" iff none;
     *       the dataset must then equal {@code GuiRecipe.prepare(..., DEFAULT)} record by record;</li>
     *   <li>Structure Generation: "Add run slot" {@code seed} times (slot local&lt;k&gt; runs seed
     *       base + k with base 1, so the last slot runs {@code seed}), select that row, "Start as
     *       selected";</li>
     *   <li>the run equals the golden run (status, iterations, random draws, kT, best score, best
     *       structure, every result), and the GUI shows it: the table row (status, iterations), the
     *       best-structure drawing and the evaluation after a poll, "Summarize Result" tiles (one per
     *       result, the first one the best score), the Logbook.</li>
     * </ol>
     */
    static void endToEnd(String key, Profile profile, int seed) throws Exception {
        RunGolden golden = GoldenRunAssert.golden(key, profile.key, seed);
        String shots = "e2e-" + key + "-" + profile.key + "-seed" + seed;
        try (TestApp t = GuiTestSupport.newApp(shots, new TestHooks(1L, CAP))) {
            // 1. toolbar "Open a file" > pinene.sml
            t.ui.answerFiles(GuiTestSupport.sml(key).toFile());
            click(GuiTestSupport.find(t.view, ComponentNames.TOOLBAR + ".openAll", JButton.class));
            flushEdt();
            SenecaDataset sd = onEdt(t.app.datasets::current);
            assertNotNull(sd, "no dataset opened");
            DatasetFrame f = onEdt(() -> t.app.frameOf(sd));
            assertNotNull(f, "no dataset window");

            // 2. the recipe, by hand in the window; the result equals the core's recipe
            SenecaDataset recipe = GuiRecipe.prepare(GuiTestSupport.sml(key), key, profile, t.app.compat).sd;
            RecipeThroughPanelsTest.configure(f, key, profile, recipe);
            List<String> want = EchoRecords.configuredRecords(recipe, profile, LegacyCompat.DEFAULT);
            List<String> got = onEdt(() -> EchoRecords.configuredRecords(sd, profile, LegacyCompat.DEFAULT));
            assertEquals(want.size(), got.size());
            for (int i = 0; i < want.size(); i++)
                JsonDiff.assertSameRecord(key + "/" + profile.key + " configured record " + i, want.get(i), got.get(i), Set.of());
            assertSameConfiguration(recipe, sd);
            List<String> enabled = onEdt(() -> GuiRecipe.enabledJudges(sd));
            shot(t.view, 1000, 700, shots + "-1-configured");

            // 3. Structure Generation: Add run slot (seed times), select the last row, Start as selected
            onEdt(() -> f.showTab("Structure Generation"));
            StructureGenerationPanel panel = onEdt(() -> {
                for (Component c : GuiTestSupport.all(f)) if (c instanceof StructureGenerationPanel p) return p;
                return null;
            });
            assertNotNull(panel, "Structure Generation tab");
            onEdt(() -> panel.timer().stop());         // the test polls itself, deterministically
            AbstractButton add = GuiTestSupport.find(f, ComponentNames.RUN_ADD_SLOT, AbstractButton.class);
            assertNotNull(add, "Add run slot");
            for (int i = 0; i < seed; i++) click(add);
            t.app.awaitControl(60_000);
            flushEdt();
            int row = seed - 1;
            JTable table = GuiTestSupport.find(f, ComponentNames.RUN_TABLE, JTable.class);
            assertNotNull(table, "run table");
            assertEquals(seed, onEdt(table::getRowCount));
            onEdt(() -> table.setRowSelectionInterval(row, row));
            click(GuiTestSupport.find(f, ComponentNames.RUN_START, AbstractButton.class));
            t.app.awaitControl(60_000);
            RunSlot slot = f.runs().slots().get(row);
            assertEquals("local" + row, slot.id());
            assertEquals(Long.valueOf(seed), t.app.runOptions(slot).seed());
            assertTrue(GuiTestSupport.waitFor(() -> slot.lastResult() != null || slot.failure() != null, 900_000), "run did not end");
            assertNull(slot.failure());
            for (int i = 0; i < row; i++) assertFalse(f.runs().slots().get(i).hasRun(), "row " + (i + 1) + " was not selected");
            flushEdt();

            // 4a. the run equals the golden run
            RunResult r = slot.lastResult();
            GoldenRunAssert.assertMatches(golden, enabled, r);

            // 4b. and the GUI shows it
            onEdt(() -> {
                for (int i = 0; i <= row; i++) panel.poll();      // round robin: the last tick shows the run
            });
            assertEquals("Holding Results", onEdt(() -> String.valueOf(table.getValueAt(row, 5))));
            assertEquals(String.valueOf(golden.record.lng("iterations")), onEdt(() -> String.valueOf(table.getValueAt(row, 6))));
            assertEquals("Best structure of no." + seed, onEdt(panel::bestStructureTitle));
            String bestMdl = golden.record.str("bestMDL");
            assertTrue(Arrays.deepEquals(GoldenRunAssert.ct(bestMdl),
                    GoldenRunAssert.ct(onEdt(() -> panel.bestStructureView().structure().mdl("x")))), "drawn structure = golden best structure");
            assertTrue(onEdt(() -> panel.bestStructureView().structure().drawable()), "best structure drawn");
            long best = golden.record.lng("bestScore");
            String eval = onEdt(() -> panel.evaluationPane().getText());
            assertTrue(eval.contains("Score: " + best + "/" + golden.record.lng("maxScore")), eval);
            shot(t.view, 1000, 700, shots + "-2-finished");

            click(GuiTestSupport.find(f, ComponentNames.RUN_SUMMARIZE, AbstractButton.class));
            ScriptedUi.Window w = t.ui.lastWindow();
            assertTrue(w.title.startsWith("Structure Display - "), w.title);   // dataset name in the title (DV-88)
            StructureDisplayPanel display = (StructureDisplayPanel) w.content;
            List<StructureDisplayPanel.Tile> tiles = onEdt(display::tileList);
            assertEquals(golden.record.lng("nResults"), tiles.size(), "one tile per result");
            assertEquals("Score " + best, tiles.get(0).title());
            assertTrue(onEdt(() -> tiles.get(0).structure().drawable()), "first tile drawn");
            shot(display.tiles(), 600, Math.min(4, (tiles.size() + 1) / 2) * 200, shots + "-3-summarize");

            assertTrue(onEdt(t.app.logbook::text).contains("Simulated annealing run finished."));
        }
    }

    /**
     * The GUI's run path without the editing panels: the dataset prepared by the golden recipe (core),
     * opened in a dataset window, started in that window's {@link RunController} with
     * {@link AppContext#runOptions} (hidden seed hook, shared engine log), events delivered to the EDT by
     * {@link EdtRunListener}. Must reproduce golden/runs/pinene.gui.seed1.json; the Logbook and the log
     * files in the scratch directory receive the engine output.
     */
    @Test
    void seedHookAndAppRunPathReproduceGoldenSeed1() throws Exception {
        try (TestApp t = GuiTestSupport.newApp("e2e-hook", new TestHooks(1L, CAP))) {
            SenecaDataset sd = GuiRecipe.prepare(GuiTestSupport.sml("pinene"), "pinene", Profile.GUI, t.app.compat).sd;
            List<String> enabled = GuiRecipe.enabledJudges(sd);
            DatasetFrame f = onEdt(() -> t.app.addDataset(sd));
            RunController rc = f.runs();
            RunSlot slot = rc.addSlot();
            assertEquals("local0", slot.id());
            assertEquals(Long.valueOf(1), t.app.runOptions(slot).seed());
            assertEquals(CAP, t.app.runOptions(slot).iterationCap());

            CountDownLatch finished = new CountDownLatch(1);
            AtomicBoolean offEdt = new AtomicBoolean();
            AtomicReference<RunResult> result = new AtomicReference<>();
            List<RunStatus> statuses = new ArrayList<>();
            List<String> logs = new ArrayList<>();
            rc.addListener(new EdtRunListener(new RunListener() {
                @Override
                public void onStatus(RunSlot s, RunStatus status) {
                    if (!SwingUtilities.isEventDispatchThread()) offEdt.set(true);
                    statuses.add(status);
                }

                @Override
                public void onLog(RunSlot s, String message) {
                    if (!SwingUtilities.isEventDispatchThread()) offEdt.set(true);
                    logs.add(message);
                }

                @Override
                public void onFinished(RunSlot s, RunResult r) {
                    if (!SwingUtilities.isEventDispatchThread()) offEdt.set(true);
                    result.set(r);
                    finished.countDown();
                }
            }));
            RunInput in = onEdt(() -> RunInput.fromDataset(sd, t.app.compat));
            t.app.control(() -> rc.start(slot, in, t.app.runOptions(slot)));
            assertTrue(finished.await(5, TimeUnit.MINUTES), "run did not finish");
            flushEdt();
            assertFalse(offEdt.get(), "a listener callback ran off the EDT");
            assertFalse(statuses.isEmpty());
            assertEquals(RunStatus.State.HOLDING, statuses.get(statuses.size() - 1).state());

            GoldenRunAssert.assertMatches(GoldenRunAssert.golden("pinene", "gui", 1), enabled, result.get());

            // Logbook (EDT) = the engine's INFO messages, in order; seneca.log = the same with "\n" after each
            String logbook = onEdt(t.app.logbook::text);
            assertEquals(String.join("", logs), logbook);
            assertTrue(logbook.contains("Simulated annealing run finished."));
            String log = Files.readString(t.app.paths.logFile());
            assertTrue(log.contains("Simulated annealing run finished."));
            String sdf = Files.readString(t.app.paths.resultsFile());
            assertTrue(sdf.contains(result.get().bestStructure() + "\n$$$$\n"), "best structure in seneca.results.sdf");
            assertTrue(t.app.paths.logFile().startsWith(t.dir));
        }
    }

    /** Same atoms (symbol, H count, assigned shift), same enabled judges, same schedules. */
    static void assertSameConfiguration(SenecaDataset expected, SenecaDataset actual) {
        assertEquals(expected.getSetOfNodes().length, actual.getSetOfNodes().length);
        for (int i = 0; i < expected.getSetOfNodes().length; i++) {
            HeavyAtom e = expected.getSetOfNodes()[i], a = actual.getSetOfNodes()[i];
            assertEquals(e.symbol, a.symbol, "atom " + i);
            assertEquals(e.hCount, a.hCount, "H count of atom " + i);
            assertEquals(e.shift13C, a.shift13C, "shift of atom " + i);
        }
        assertEquals(GuiRecipe.enabledJudges(expected), GuiRecipe.enabledJudges(actual));
        assertEquals(expected.annealingSchedules.size(), actual.annealingSchedules.size());
        for (int i = 0; i < expected.annealingSchedules.size(); i++)
            assertEquals(expected.annealingSchedules.get(i).toString(), actual.annealingSchedules.get(i).toString());
    }
}
