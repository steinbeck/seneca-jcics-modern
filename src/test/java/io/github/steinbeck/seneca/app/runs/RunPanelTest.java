package io.github.steinbeck.seneca.app.runs;

import static io.github.steinbeck.seneca.app.GuiTestSupport.onEdt;
import static io.github.steinbeck.seneca.app.GuiTestSupport.shot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.MouseEvent;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.junit.jupiter.api.Test;

import io.github.steinbeck.seneca.app.ComponentNames;
import io.github.steinbeck.seneca.app.GoldenRunAssert;
import io.github.steinbeck.seneca.app.GuiTestSupport;
import io.github.steinbeck.seneca.app.GuiTestSupport.TestApp;
import io.github.steinbeck.seneca.app.ScriptedUi;
import io.github.steinbeck.seneca.app.TestHooks;
import io.github.steinbeck.seneca.app.log.AppEngineLog;
import io.github.steinbeck.seneca.app.run.AnnealingChartPanel;
import io.github.steinbeck.seneca.app.structure.StructureDisplayPanel;
import io.github.steinbeck.seneca.config.GuiRecipe;
import io.github.steinbeck.seneca.config.Profile;
import io.github.steinbeck.seneca.golden.Hashes;
import io.github.steinbeck.seneca.golden.JsonValue;
import io.github.steinbeck.seneca.structgen.AnnealingLog;
import io.github.steinbeck.seneca.structgen.RunInput;
import io.github.steinbeck.seneca.structgen.RunOptions;
import io.github.steinbeck.seneca.structgen.RunResult;
import io.github.steinbeck.seneca.structgen.StochasticGenerator;
import io.github.steinbeck.seneca.structgen.StructureGeneratorResult;

/**
 * The "Structure Generation" tab driven like a user (docs/port/gui-design.md sections 6, 9, 10): run
 * slots, seeded starts through the hidden {@code seneca.seed} hook, polling, chart, best structure,
 * evaluation, "Summarize Result", "Log Annealing Data", Logbook and log files, compared with the golden
 * runs and with a 2001 run recorded by {@code scripts/legacy-run-shots.sh} (pinene, "gui" recipe, seed 1;
 * src/test/resources/io/github/steinbeck/seneca/app/runs/legacy-pinene-gui-seed1).
 */
class RunPanelTest {

    static final String LEGACY = "legacy-pinene-gui-seed1/";

    static String legacy(String file) throws IOException {
        try (InputStream in = RunPanelTest.class.getResourceAsStream(LEGACY + file)) {
            assertNotNull(in, file);
            return new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }

    /**
     * Three slots (seeds 1, 2, 3 by the hook), rows 1 and 3 started: seed 1 reproduces the golden run,
     * seed 3 (legacy: no result, Q1) the core's DEFAULT run; the table, the round-robin display, the
     * chart (score and kT), Summarize and Log Annealing Data show exactly these runs.
     */
    @Test
    void seededRunsThroughTheTabMatchGoldenAndTheDisplayShowsThem() throws Exception {
        try (TestApp t = GuiTestSupport.newApp("runs-panel", new TestHooks(1L, RunFixture.CAP))) {
            RunFixture fx = RunFixture.open(t, "pinene");
            String name = fx.sd.getName();
            shot(t.view, 1000, 700, "runs-0-empty");

            fx.addSlots(3);
            assertEquals(List.of("1", "local0", "local", "none", "none", "idle", "none"), fx.row(0));
            assertEquals(List.of("3", "local2", "local", "none", "none", "idle", "none"), fx.row(2));
            assertEquals(List.of("No.", "ID", "Host", "Dataset", "MF", "Status ", "Iter."), onEdt(() -> {
                List<String> l = new ArrayList<>();
                for (int c = 0; c < fx.panel.table().getColumnCount(); c++) l.add(fx.panel.table().getColumnName(c));
                return l;
            }));
            fx.select(0, 2);
            shot(t.view, 1000, 700, "runs-1-slots");
            List<String> enabled = fx.enabledJudges();
            fx.press(ComponentNames.RUN_START);
            fx.settle();
            RunResult r1 = fx.awaitResult(0);
            RunResult r3 = fx.awaitResult(2);
            assertFalse(fx.slot(1).hasRun(), "row 2 was not selected");
            GoldenRunAssert.assertMatches(GoldenRunAssert.golden("pinene", "gui", 1), enabled, r1);
            // seed 3: legacy stops without a result (Q1); DEFAULT goes on - the GUI run equals the core's
            RunInput in = RunInput.fromDataset(GuiRecipe.prepare(GuiTestSupport.sml("pinene"), "pinene", Profile.GUI, t.app.compat).sd, t.app.compat);
            RunResult core3 = new StochasticGenerator(in, RunOptions.application().withSeed(3L).withIterationCap(RunFixture.CAP)).run();
            RunFixture.assertSameRun("seed 3", core3, r3);
            GuiTestSupport.flushEdt();

            // table: every row each tick
            fx.poll();
            assertEquals(List.of("1", "local0", "local", name, "C10H16", "Holding Results", "21248"), fx.row(0));
            assertEquals(List.of("2", "local1", "local", "none", "none", "idle", "none"), fx.row(1));
            assertEquals(List.of("3", "local2", "local", name, "C10H16", "Holding Results", String.valueOf(r3.iterations())), fx.row(2));

            // the first tick polled row 1: best structure and evaluation of no. 1
            assertEquals("Best structure of no.1", onEdt(fx.panel::bestStructureTitle));
            assertEquals("Evaluation of no. 1", onEdt(fx.panel::evaluationTitle));
            assertTrue(onEdt(() -> fx.panel.bestStructureView().structure().drawable()));
            assertTrue(java.util.Arrays.deepEquals(GoldenRunAssert.ct(r1.bestStructure()),
                    GoldenRunAssert.ct(onEdt(() -> fx.panel.bestStructureView().structure().mdl("x")))), "drawn structure = best structure");
            String eval = onEdt(() -> fx.panel.evaluationPane().getText());
            assertTrue(eval.contains("Score: 2400/2400"), eval);
            assertTrue(eval.contains("Signals satisfied in HMBCJudge"), eval);
            // ticks 2 and 3: row 2 has no run, row 3 polled (it shows its best if it has one)
            fx.poll();
            assertEquals("Best structure of no.1", onEdt(fx.panel::bestStructureTitle));
            fx.poll();
            if (r3.bestStructure() != null) assertEquals("Best structure of no.3", onEdt(fx.panel::bestStructureTitle));

            // chart: host 1..3 (row 2 empty) + max. Score, every log entry of the runs exactly once
            AnnealingChartPanel chart = fx.panel.chart();
            XYSeriesCollection ds = onEdt(chart::dataset);
            assertEquals(List.of("host 1", "host 2", "host 3", "max. Score"), keys(ds));
            List<AnnealingLog.Entry> log1 = fx.slot(0).annealingLogSince(0);
            List<AnnealingLog.Entry> log3 = fx.slot(2).annealingLogSince(0);
            assertEquals(42, log1.size(), "21248 iterations: one entry per 500");
            assertSeries(log1, ds.getSeries(0), false);
            assertEquals(0, ds.getSeries(1).getItemCount());
            assertSeries(log3, ds.getSeries(2), false);
            assertEquals(2400.0, ds.getSeries(3).getY(0).doubleValue());
            assertEquals("Score", onEdt(() -> chart.getChart().getXYPlot().getRangeAxis().getLabel()));
            fx.poll();      // polling again adds nothing (no entry is fetched twice)
            assertEquals(log1.size(), onEdt(chart::dataset).getSeries(0).getItemCount());
            shot(t.view, 1000, 700, "runs-2-finished-score");
            shot(chart, 500, 350, "runs-chart-score");

            // a click on the chart: kT (no max. Score line), another: score again
            onEdt(() -> chart.dispatchEvent(new MouseEvent(chart, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 20, 20, 1, false)));
            ds = onEdt(chart::dataset);
            assertEquals("kT", onEdt(() -> chart.getChart().getXYPlot().getRangeAxis().getLabel()));
            assertEquals(List.of("host 1", "host 2", "host 3"), keys(ds));
            assertSeries(log1, ds.getSeries(0), true);
            shot(chart, 500, 350, "runs-chart-kt");
            shot(t.view, 1000, 700, "runs-3-finished-kt");
            onEdt(() -> chart.dispatchEvent(new MouseEvent(chart, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 20, 20, 1, false)));
            assertEquals("Score", onEdt(() -> chart.getChart().getXYPlot().getRangeAxis().getLabel()));

            // Log Annealing Data: header + one line per item, stdout and seneca.annealing.log
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            onEdt(() -> fx.panel.setStdout(new PrintStream(out, true, StandardCharsets.UTF_8)));
            fx.press(ComponentNames.RUN_LOG_ANNEALING);
            List<String> expected = new ArrayList<>();
            expected.add("Iteration; Server 1; Server 2; Server 3; ");
            int rows = Math.max(log1.size(), log3.size());
            for (int y = 0; y < rows; y++) {
                Float x = y < log3.size() ? log3.get(y).iteration() : log1.get(y).iteration();
                expected.add(x + "; " + (y < log1.size() ? log1.get(y).bestScore() : "") + "; ; "
                        + (y < log3.size() ? log3.get(y).bestScore() : "") + "; ");
            }
            String table = String.join("\n", expected) + "\n";
            assertEquals(table, out.toString(StandardCharsets.UTF_8).replace(System.lineSeparator(), "\n"));
            assertEquals(table, Files.readString(t.app.paths.annealingLogFile()));
            assertTrue(Files.readString(t.app.paths.logFile()).endsWith(String.join("\n\n", expected) + "\n\n"));

            // Summarize Result: a new Structure Display window with the merged, sorted result
            int windows = t.ui.windows.size();
            fx.press(ComponentNames.RUN_SUMMARIZE);
            assertEquals(windows + 1, t.ui.windows.size());
            ScriptedUi.Window w = t.ui.lastWindow();
            assertTrue(w.title.startsWith("Structure Display - "), w.title);   // dataset name in the title (DV-88)
            StructureDisplayPanel display = (StructureDisplayPanel) w.content;
            StructureGeneratorResult merged = fx.frame.runs().summarize(t.app.compat);
            List<StructureDisplayPanel.Tile> tiles = onEdt(display::tileList);
            assertEquals(merged.size(), tiles.size());
            for (int i = 0; i < tiles.size(); i++) {
                StructureDisplayPanel.Tile tile = tiles.get(i);
                long score = merged.scoreSummaries.get(i).score;
                assertEquals("Score " + score, tile.title());
                assertEquals("<html><b>Score " + score + ": </b>" + merged.scoreSummaries.get(i).description + "</html>",
                        onEdt(() -> tile.getToolTipText()));
                assertTrue(onEdt(() -> tile.structure().drawable()), "tile " + i + " drawn");
                if (i > 0) assertTrue(score <= merged.scoreSummaries.get(i - 1).score, "sorted");
            }
            // seed 1's results (golden) are all there, marked "Result from host 1" - or, where host 3 found
            // the same structure with the same score, as host 3's copy (removeIsomorphism keeps one of them)
            JsonValue.JArray golden1 = GoldenRunAssert.golden("pinene", "gui", 1).record.arr("results");
            for (int g = 0; g < golden1.size(); g++) {
                JsonValue.JObject e = golden1.get(g).asObject();
                boolean found = false;
                for (int i = 0; i < merged.size() && !found; i++) {
                    String d = merged.scoreSummaries.get(i).description;
                    boolean sameScore = merged.scoreSummaries.get(i).score == e.lng("score");
                    if (sameScore && d.equals(e.str("description") + "<p>Result from host 1</p>"))
                        found = Hashes.hash(GoldenRunAssert.ct(merged.structures.get(i))).equals(e.str("hash"));
                    else if (sameScore && d.equals(e.str("description") + "<p>Result from host 3</p>"))
                        found = new io.github.steinbeck.seneca.chem.IsomorphismTester(
                                io.github.steinbeck.seneca.chem.LegacyMdlReader.read(merged.structures.get(i)))
                                .isIsomorph(io.github.steinbeck.seneca.chem.LegacyMdlReader.read(r1.result().structures.get(g)));
                }
                assertTrue(found, "golden result " + g + " in the summary");
            }
            shot(w.content, "runs-4-summarize-window");
            shot(display.tiles(), 600, ((tiles.size() + 1) / 2) * 200, "runs-5-summarize-tiles");

            // Logbook tab
            onEdt(() -> fx.frame.showTab("Logbook"));
            GuiTestSupport.flushEdt();
            shot(t.view, 1000, 700, "runs-6-logbook");
        }
    }

    /**
     * The same run as the 2001 client (scripts/legacy-run-shots.sh, pinene, "gui" recipe, seed 1, one
     * slot, polled once after the end, then "Log Annealing Data"): Logbook text, {@code seneca.log},
     * {@code seneca.results.sdf}, {@code seneca.annealing.log} and stdout identical (apart from the wall
     * clock), the Structure Display tiles those of 2001 plus the two lowest results that 2001's
     * {@code removeIsomorphism} dropped (D5, DEFAULT keeps them).
     */
    @Test
    void outputsEqualThoseOfThe2001Client() throws Exception {
        try (TestApp t = GuiTestSupport.newApp("runs-legacy", new TestHooks(1L, RunFixture.CAP))) {
            RunFixture fx = RunFixture.open(t, "pinene");
            ByteArrayOutputStream fatal = new ByteArrayOutputStream();
            ((AppEngineLog) t.app.engineLog).setStdout(new PrintStream(fatal, true, StandardCharsets.UTF_8));
            fx.addSlots(1);
            fx.select(0);
            fx.press(ComponentNames.RUN_START);
            fx.settle();
            fx.awaitResult(0);
            GuiTestSupport.flushEdt();
            fx.poll();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            onEdt(() -> fx.panel.setStdout(new PrintStream(out, true, StandardCharsets.ISO_8859_1)));
            fx.press(ComponentNames.RUN_LOG_ANNEALING);
            GuiTestSupport.flushEdt();

            assertEquals(RunFixture.maskTimes(legacy("logbook.txt")), RunFixture.maskTimes(onEdt(t.app.logbook::text)), "Logbook");
            assertEquals(RunFixture.maskTimes(legacy("seneca.log")), RunFixture.maskTimes(Files.readString(t.app.paths.logFile())), "seneca.log");
            assertEquals(legacy("seneca.results.sdf"), Files.readString(t.app.paths.resultsFile()), "seneca.results.sdf");
            assertEquals(legacy("seneca.annealing.log"), Files.readString(t.app.paths.annealingLogFile()), "seneca.annealing.log");
            assertEquals(legacy("stdout-annealing.txt"), out.toString(StandardCharsets.ISO_8859_1).replace(System.lineSeparator(), "\n"), "stdout");
            assertEquals("", fatal.toString(StandardCharsets.UTF_8), "no FATAL line");

            fx.press(ComponentNames.RUN_SUMMARIZE);
            StructureDisplayPanel display = (StructureDisplayPanel) t.ui.lastWindow().content;
            List<String> port = new ArrayList<>();
            for (StructureDisplayPanel.Tile tile : onEdt(display::tileList))
                port.add(tile.title() + "\t" + onEdt(() -> tile.getToolTipText()).replace("\n", "\\n"));
            List<String> old = legacy("summarize.txt").lines().toList();
            assertEquals(GoldenRunAssert.golden("pinene", "gui", 1).record.lng("nResults"), port.size());
            assertEquals(old.size() + 2, port.size(), "2001 dropped the two lowest results (D5)");
            assertEquals(old, port.subList(0, old.size()));
        }
    }

    static List<String> keys(XYSeriesCollection ds) {
        List<String> l = new ArrayList<>();
        for (int i = 0; i < ds.getSeriesCount(); i++) l.add(String.valueOf(ds.getSeriesKey(i)));
        return l;
    }

    static void assertSeries(List<AnnealingLog.Entry> log, XYSeries s, boolean kT) {
        assertEquals(log.size(), s.getItemCount(), String.valueOf(s.getKey()));
        for (int i = 0; i < log.size(); i++) {
            assertEquals(log.get(i).iteration(), s.getX(i).floatValue(), 0f);
            assertEquals(kT ? log.get(i).kT() : log.get(i).bestScore(), s.getY(i).floatValue(), 0f);
        }
    }
}
