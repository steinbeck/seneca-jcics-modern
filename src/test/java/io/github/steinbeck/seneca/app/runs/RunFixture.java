package io.github.steinbeck.seneca.app.runs;

import static io.github.steinbeck.seneca.app.GuiTestSupport.click;
import static io.github.steinbeck.seneca.app.GuiTestSupport.find;
import static io.github.steinbeck.seneca.app.GuiTestSupport.onEdt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.AbstractButton;

import io.github.steinbeck.seneca.app.ComponentNames;
import io.github.steinbeck.seneca.app.GuiTestSupport;
import io.github.steinbeck.seneca.app.GuiTestSupport.TestApp;
import io.github.steinbeck.seneca.app.dataset.DatasetFrame;
import io.github.steinbeck.seneca.app.run.StructureGenerationPanel;
import io.github.steinbeck.seneca.config.GuiRecipe;
import io.github.steinbeck.seneca.config.Profile;
import io.github.steinbeck.seneca.golden.RunGolden;
import io.github.steinbeck.seneca.model.SenecaDataset;
import io.github.steinbeck.seneca.structgen.RunResult;
import io.github.steinbeck.seneca.structgen.RunSlot;

/**
 * A dataset window with the golden "gui" recipe of a dataset (prepared by the core's {@link GuiRecipe},
 * the editing tabs are G1's and tested there) and its "Structure Generation" tab, whose 2-second timer
 * is stopped so that a test polls deterministically with {@link StructureGenerationPanel#poll()}.
 * Buttons are pressed with {@code doClick()} on the EDT.
 */
final class RunFixture {

    static final long CAP = RunGolden.ITERATION_CAP;

    final TestApp t;
    final SenecaDataset sd;
    final DatasetFrame frame;
    final StructureGenerationPanel panel;

    private RunFixture(TestApp t, SenecaDataset sd, DatasetFrame frame, StructureGenerationPanel panel) {
        this.t = t;
        this.sd = sd;
        this.frame = frame;
        this.panel = panel;
    }

    /** {@code key} prepared with the "gui" recipe, opened in a dataset window, tab "Structure Generation". */
    static RunFixture open(TestApp t, String key) throws java.io.IOException {
        SenecaDataset sd = GuiRecipe.prepare(GuiTestSupport.sml(key), key, Profile.GUI, t.app.compat).sd;
        return open(t, sd);
    }

    static RunFixture open(TestApp t, SenecaDataset sd) {
        DatasetFrame f = onEdt(() -> t.app.addDataset(sd));
        StructureGenerationPanel p = onEdt(() -> {
            for (Component c : GuiTestSupport.all(f)) if (c instanceof StructureGenerationPanel s) return s;
            return null;
        });
        assertNotNull(p, "Structure Generation tab");
        onEdt(() -> {
            p.timer().stop();
            f.showTab("Structure Generation");
        });
        return new RunFixture(t, sd, f, p);
    }

    AbstractButton button(String name) {
        AbstractButton b = find(frame, name, AbstractButton.class);
        assertNotNull(b, name);
        return b;
    }

    void press(String name) {
        click(button(name));
    }

    /** "Add run slot" n times. */
    void addSlots(int n) {
        for (int i = 0; i < n; i++) press(ComponentNames.RUN_ADD_SLOT);
    }

    /** Selects these table rows (only these). */
    void select(int... rows) {
        onEdt(() -> {
            panel.table().clearSelection();
            for (int r : rows) panel.table().addRowSelectionInterval(r, r);
        });
    }

    /** Waits until every run-control task submitted so far is done and the EDT has caught up. */
    void settle() throws Exception {
        t.app.awaitControl(120_000);
        GuiTestSupport.flushEdt();
        GuiTestSupport.flushEdt();
    }

    RunSlot slot(int row) {
        return frame.runs().slots().get(row);
    }

    /** Waits until the run of {@code row} has ended; its result. */
    RunResult awaitResult(int row) {
        RunSlot s = slot(row);
        assertTrue(GuiTestSupport.waitFor(() -> s.lastResult() != null || s.failure() != null, 600_000), "run in row " + row + " did not end");
        assertNull(s.failure());
        return s.lastResult();
    }

    /** The table row as shown. */
    List<String> row(int r) {
        return onEdt(() -> {
            List<String> l = new ArrayList<>();
            for (int c = 0; c < panel.model().getColumnCount(); c++) l.add(String.valueOf(panel.model().getValueAt(r, c)));
            return l;
        });
    }

    void poll() {
        onEdt(panel::poll);
    }

    List<String> enabledJudges() {
        return onEdt(() -> GuiRecipe.enabledJudges(sd));
    }

    /** Engine log text without the wall-clock parts (start/stop time, runtime). */
    static String maskTimes(String s) {
        s = s.replaceAll("Start time: [^\n]*", "Start time: <date>");
        s = s.replaceAll("Stop: [^\n]*", "Stop: <date>");
        Matcher m = Pattern.compile("Overall runtime: \\d+ seconds\\.").matcher(s);
        return m.replaceAll("Overall runtime: <n> seconds.");
    }

    static void assertSameRun(String what, RunResult expected, RunResult actual) {
        assertEquals(expected.iterations(), actual.iterations(), what + " iterations");
        assertEquals(expected.rngDraws(), actual.rngDraws(), what + " rngDraws");
        assertEquals(expected.maxScore(), actual.maxScore(), what + " maxScore");
        assertEquals(expected.startKT(), actual.startKT(), what + " startKT");
        assertEquals(expected.finalKT(), actual.finalKT(), what + " finalKT");
        assertEquals(expected.bestScore().score, actual.bestScore().score, what + " best score");
        assertEquals(expected.bestStructure(), actual.bestStructure(), what + " best structure");
        assertEquals(expected.result().size(), actual.result().size(), what + " results");
        for (int i = 0; i < expected.result().size(); i++) {
            assertEquals(expected.result().structures.get(i), actual.result().structures.get(i), what + " result " + i);
            assertEquals(expected.result().scoreSummaries.get(i).description, actual.result().scoreSummaries.get(i).description,
                    what + " description " + i);
        }
    }
}
