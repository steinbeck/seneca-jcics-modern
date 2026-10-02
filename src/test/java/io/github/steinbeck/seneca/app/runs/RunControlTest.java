package io.github.steinbeck.seneca.app.runs;

import static io.github.steinbeck.seneca.app.GuiTestSupport.onEdt;
import static io.github.steinbeck.seneca.app.GuiTestSupport.shot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import javax.swing.JOptionPane;

import org.junit.jupiter.api.Test;

import io.github.steinbeck.seneca.app.ComponentNames;
import io.github.steinbeck.seneca.app.GuiTestSupport;
import io.github.steinbeck.seneca.app.GuiTestSupport.TestApp;
import io.github.steinbeck.seneca.app.TestHooks;
import io.github.steinbeck.seneca.config.GuiRecipe;
import io.github.steinbeck.seneca.config.JudgeConfiguration;
import io.github.steinbeck.seneca.config.Profile;
import io.github.steinbeck.seneca.io.SpecMLReader;
import io.github.steinbeck.seneca.judges.HOSECodeJudge;
import io.github.steinbeck.seneca.judges.Judge;
import io.github.steinbeck.seneca.model.SenecaDataset;
import io.github.steinbeck.seneca.structgen.RunInput;
import io.github.steinbeck.seneca.structgen.RunResult;
import io.github.steinbeck.seneca.structgen.RunSlot;
import io.github.steinbeck.seneca.structgen.RunStatus;
import io.github.steinbeck.seneca.structgen.StochasticGenerator;

/**
 * "Stop Selected", "Reset selected", "Clear Slot List", Start on a running slot, closing the window, and
 * the messages for inputs that cannot run (feature-inventory F-63..F-66; docs/port/gui-design.md
 * section 6). The long run is pinene "gui" seed 2 (1.29 million iterations, ~40 s when not stopped).
 */
class RunControlTest {

    static boolean running(RunSlot s) {
        return s.isRunning() && s.status().iteration() > 2000;
    }

    @Test
    void stopResetRestartAndClear() throws Exception {
        try (TestApp t = GuiTestSupport.newApp("runs-control", new TestHooks(2L, RunFixture.CAP))) {
            RunFixture fx = RunFixture.open(t, "pinene");
            fx.addSlots(2);

            // Start row 1 (seed 2), poll while it runs: "Running", chart data arrive
            fx.select(0);
            fx.press(ComponentNames.RUN_START);
            fx.settle();
            RunSlot s0 = fx.slot(0);
            assertTrue(GuiTestSupport.waitFor(() -> running(s0) && s0.status().annealingLogSize() >= 4, 60_000), "run started");
            fx.poll();
            assertEquals("Running", fx.row(0).get(5));
            assertTrue(onEdt(() -> fx.panel.chart().data().hasData()));
            assertTrue(onEdt(() -> fx.panel.chart().data().series(0).size()) > 0);
            shot(t.view, 1000, 700, "runs-control-1-running");

            // Stop Selected: the run ends, stopped by the user
            fx.press(ComponentNames.RUN_STOP);
            RunResult stopped = fx.awaitResult(0);
            assertTrue(stopped.stoppedByUser());
            GuiTestSupport.flushEdt();
            fx.poll();
            assertEquals(RunStatus.State.STOPPED.label, fx.row(0).get(5));
            assertEquals(String.valueOf(stopped.iterations()), fx.row(0).get(6));

            // Reset selected: chart data gone (all of it), the slot is idle again
            fx.press(ComponentNames.RUN_RESET);
            assertFalse(onEdt(() -> fx.panel.chart().data().hasData()));
            assertEquals(List.of("no data"), RunPanelTest.keys(onEdt(fx.panel.chart()::dataset)));
            fx.settle();
            assertFalse(s0.hasRun());
            assertEquals(List.of("1", "local0", "local", "none", "none", "idle", "none"), fx.row(0));
            shot(t.view, 1000, 700, "runs-control-2-reset");

            // Start again, then Start on the running slot: the first run is stopped, a new one runs
            fx.press(ComponentNames.RUN_START);
            fx.settle();
            assertTrue(GuiTestSupport.waitFor(() -> running(s0), 60_000));
            Thread firstThread = threadOf(s0);
            fx.press(ComponentNames.RUN_START);
            fx.settle();
            assertTrue(GuiTestSupport.waitFor(() -> s0.isRunning(), 60_000));
            assertNotSame(firstThread, threadOf(s0));
            assertFalse(firstThread.isAlive(), "the first run was stopped and joined");
            assertNull(s0.lastResult());

            // Reset with a running run stops it
            fx.press(ComponentNames.RUN_RESET);
            fx.settle();
            assertFalse(s0.hasRun());
            assertFalse(s0.isRunning());

            // Clear Slot List: runs stopped, no rows
            fx.select(1);
            fx.press(ComponentNames.RUN_START);
            fx.settle();
            RunSlot s1 = fx.slot(1);
            assertTrue(GuiTestSupport.waitFor(() -> running(s1), 60_000));
            fx.press(ComponentNames.RUN_CLEAR);
            fx.settle();
            assertEquals(0, onEdt(() -> fx.panel.model().getRowCount()));
            assertTrue(fx.frame.runs().slots().isEmpty());
            assertFalse(s1.isRunning());
            fx.poll();      // nothing to poll
            fx.addSlots(1);
            assertEquals(List.of("1", "local0", "local", "none", "none", "idle", "none"), fx.row(0));   // 2001: numbering restarts

            // closing the window stops the timer
            onEdt(() -> fx.panel.timer().start());
            t.ui.answerDialog(JOptionPane.NO_OPTION);
            fx.press(ComponentNames.DATASET_CLOSE);
            GuiTestSupport.flushEdt();
            assertFalse(onEdt(() -> fx.panel.timer().isRunning()));
        }
    }

    private static Thread threadOf(RunSlot s) {
        // the run thread is named "seneca-" + slot id; find the live one
        for (Thread th : Thread.getAllStackTraces().keySet())
            if (th.getName().equals("seneca-" + s.id()) && th.isAlive()) return th;
        throw new AssertionError("no run thread for " + s);
    }

    @Test
    void startReportsInputsThatCannotRunAndStartsNothing() throws Exception {
        try (TestApp t = GuiTestSupport.newApp("runs-invalid", TestHooks.none())) {
            // New Dataset: no atoms (message from RunInput.fromDataset on the EDT)
            RunFixture fx = RunFixture.open(t, SenecaDataset.newDataset("untitled"));
            fx.addSlots(1);
            fx.select(0);
            fx.press(ComponentNames.RUN_START);
            fx.settle();
            assertEquals("Start as selected", t.ui.lastShown().title());
            assertEquals(StochasticGenerator.NO_ATOMS, t.ui.lastShown().message());
            assertEquals(JOptionPane.ERROR_MESSAGE, t.ui.lastShown().pane().getMessageType());
            assertFalse(fx.slot(0).hasRun());
            shot(t.ui.lastShown().pane(), "runs-dialog-no-atoms");

            // pinene without an annealing schedule: rejected by the generator (control thread), two rows
            SenecaDataset sd = GuiRecipe.prepare(GuiTestSupport.sml("pinene"), "pinene", Profile.GUI, t.app.compat).sd;
            sd.annealingSchedules.clear();
            RunFixture fx2 = RunFixture.open(t, sd);
            fx2.addSlots(2);
            fx2.select(0, 1);
            int shown = t.ui.shown.size();
            fx2.press(ComponentNames.RUN_START);
            fx2.settle();
            assertEquals(shown + 1, t.ui.shown.size(), "one message");
            assertEquals("no annealing schedule", t.ui.lastShown().message());
            assertFalse(fx2.slot(0).hasRun());
            assertFalse(fx2.slot(1).hasRun());
            assertEquals("idle", fx2.row(0).get(5));

            // nothing selected: nothing happens
            fx2.select();
            fx2.press(ComponentNames.RUN_START);
            fx2.settle();
            assertEquals(shown + 1, t.ui.shown.size());
        }
    }

    /**
     * DV-93: a dataset whose enabled judges can score nothing (maximum score 0) is not started. Reported
     * by the user: monochaetin opened, "Autoconfigure Judge" pressed without "Autoassignment" first,
     * Start: every score 0, start_kT = -0.0, every new structure logged as new best (hundreds of MB).
     */
    @Test
    void startRefusesADatasetWhoseMaximumScoreIsZero() throws Exception {
        try (TestApp t = GuiTestSupport.newApp("runs-zero-max", TestHooks.none())) {
            // as read: no shifts, no H counts; HOSECodeJudge autoconfigured without Autoassignment -> max 0
            SenecaDataset sd = SpecMLReader.read(GuiTestSupport.sml("monochaetin"));
            for (Judge j : sd.judges)
                if (j instanceof HOSECodeJudge) JudgeConfiguration.autoconfigure(sd, j, t.app.compat);
            RunFixture fx = RunFixture.open(t, sd);
            fx.addSlots(1);
            fx.select(0);
            int shown = t.ui.shown.size();
            fx.press(ComponentNames.RUN_START);
            fx.settle();
            assertEquals(shown + 1, t.ui.shown.size(), "one message");
            assertEquals("Start as selected", t.ui.lastShown().title());
            assertEquals(RunInput.NO_SCORE, t.ui.lastShown().message());
            assertEquals(JOptionPane.ERROR_MESSAGE, t.ui.lastShown().pane().getMessageType());
            assertFalse(fx.slot(0).hasRun());
            shot(t.ui.lastShown().pane(), "runs-dialog-zero-max-score");

            // the same dataset after Autoassignment + Autoconfigure (the "gui" recipe) starts normally
            SenecaDataset ok = GuiRecipe.prepare(GuiTestSupport.sml("monochaetin"), "monochaetin", Profile.GUI, t.app.compat).sd;
            RunFixture fx2 = RunFixture.open(t, ok);
            fx2.addSlots(1);
            fx2.select(0);
            shown = t.ui.shown.size();
            fx2.press(ComponentNames.RUN_START);
            fx2.settle();
            assertTrue(GuiTestSupport.waitFor(() -> fx2.slot(0).hasRun(), 60_000), "configured dataset starts");
            assertEquals(shown, t.ui.shown.size(), "no message");
            fx2.press(ComponentNames.RUN_STOP);
            fx2.awaitResult(0);
        }
    }
}
