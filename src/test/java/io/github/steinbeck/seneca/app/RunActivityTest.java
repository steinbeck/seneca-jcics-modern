package io.github.steinbeck.seneca.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import io.github.steinbeck.seneca.golden.GoldenFixtures;
import io.github.steinbeck.seneca.structgen.RunController;
import io.github.steinbeck.seneca.structgen.RunOptions;
import io.github.steinbeck.seneca.structgen.RunSlot;

/**
 * {@link RunActivity} (whether any run is going on: macOS App Nap is suspended meanwhile, DV-89) and the
 * native part {@link MacActivity}.
 */
class RunActivityTest {

    /** One run, two concurrent runs, a stopped run: exactly one "on" and one "off" per busy period. */
    @Test
    void activeExactlyWhileARunIsGoingOn() throws Exception {
        RunController rc = new RunController();
        RunActivity act = new RunActivity();
        List<Boolean> events = new CopyOnWriteArrayList<>();
        act.setSink(events::add);
        act.watch(rc);

        RunSlot a = rc.addSlot(), b = rc.addSlot();
        rc.start(a, GoldenFixtures.runInput("pinene", "gui"), RunOptions.golden(1));
        assertTrue(GuiTestSupport.waitFor(() -> a.lastResult() != null, 120_000));
        assertTrue(GuiTestSupport.waitFor(() -> !act.active(), 5_000));
        assertEquals(List.of(true, false), events);

        events.clear();
        rc.start(a, GoldenFixtures.runInput("pinene", "gui"), RunOptions.golden(1));
        rc.start(b, GoldenFixtures.runInput("pinene", "gui"), RunOptions.golden(2));
        assertTrue(GuiTestSupport.waitFor(() -> a.lastResult() != null && b.lastResult() != null, 120_000));
        assertTrue(GuiTestSupport.waitFor(() -> !act.active(), 5_000));
        assertEquals(List.of(true, false), events, "two overlapping runs: one busy period");

        // a run that does not converge (eupatorio gui seed 1), stopped by the user
        events.clear();
        rc.start(a, GoldenFixtures.runInput("eupatorio", "gui"), RunOptions.golden(1));
        assertTrue(GuiTestSupport.waitFor(act::active, 60_000));
        rc.stop(a);
        assertTrue(GuiTestSupport.waitFor(() -> !act.active(), 60_000));
        assertEquals(List.of(true, false), events);

        // a window closed while its run goes on: clear, then unwatch
        events.clear();
        rc.start(a, GoldenFixtures.runInput("eupatorio", "gui"), RunOptions.golden(1));
        assertTrue(GuiTestSupport.waitFor(act::active, 60_000));
        rc.clear();
        act.unwatch(rc);
        assertFalse(act.active());
        assertEquals(List.of(true, false), events);
    }

    /** macOS: the activity can be begun and ended through the Objective-C runtime. */
    @Test
    void macActivityBeginsAndEnds() {
        Assumptions.assumeTrue(MacActivity.supported(), "macOS only");
        assertTrue(MacActivity.set(true), "begun");
        assertTrue(MacActivity.held());
        assertTrue(MacActivity.set(true), "begun only once");
        assertFalse(MacActivity.set(false), "ended");
        assertFalse(MacActivity.held());
        assertFalse(MacActivity.set(false));
    }
}
