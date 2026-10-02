package io.github.steinbeck.seneca.structgen;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.golden.GoldenFiles;
import io.github.steinbeck.seneca.golden.GoldenFixtures;
import io.github.steinbeck.seneca.golden.RunGolden;
import io.github.steinbeck.seneca.judges.ScoreSummary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The in-process run API ({@link RunController}, {@link RunSlot}): concurrent seeded runs reproduce the
 * golden runs, listeners, stop, reset, clear, summarize (design.md 7 and 8).
 */
@Tag("C")
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class RunControllerTest {

    static RunGolden golden(String key, String profile, long seed) {
        return GoldenFiles.runs().stream().filter(r -> r.key().equals(key) && r.profile().equals(profile) && r.seed() == seed)
                .map(RunGolden::load).findFirst().orElseThrow();
    }

    static void awaitIdle(RunSlot s) throws InterruptedException {
        long end = System.currentTimeMillis() + 120_000;
        while (s.isRunning() && System.currentTimeMillis() < end) Thread.sleep(20);
        assertFalse(s.isRunning(), s + " still running");
    }

    @Test
    void concurrentSeededRunsReproduceTheGoldenRuns() throws Exception {
        RunController rc = new RunController();
        CountDownLatch done = new CountDownLatch(4);
        Map<String, RunResult> finished = new ConcurrentHashMap<>();
        AtomicInteger newBest = new AtomicInteger(), logs = new AtomicInteger(), statuses = new AtomicInteger();
        rc.addListener(new RunListener() {
            @Override
            public void onFinished(RunSlot slot, RunResult result) {
                finished.put(slot.id(), result);
                done.countDown();
            }

            @Override
            public void onNewBest(RunSlot slot, String mdl, ScoreSummary summary) {
                newBest.incrementAndGet();
            }

            @Override
            public void onLog(RunSlot slot, String message) {
                logs.incrementAndGet();
            }

            @Override
            public void onStatus(RunSlot slot, RunStatus status) {
                statuses.incrementAndGet();
                throw new IllegalStateException("a failing listener must not influence the run");
            }
        });
        long[][] seeds = {{1}, {2}, {1}, {3}};
        String[] profiles = {"paper", "paper", "paper", "gui"};
        List<RunSlot> slots = List.of(rc.addSlot(), rc.addSlot(), rc.addSlot(), rc.addSlot());
        assertEquals(List.of("local0", "local1", "local2", "local3"), slots.stream().map(RunSlot::id).toList());
        for (int i = 0; i < 4; i++) {
            RunOptions o = RunOptions.golden(seeds[i][0]).withIterationCap(RunGolden.ITERATION_CAP);
            rc.start(slots.get(i), GoldenFixtures.runInput("pinene", profiles[i]), o);
        }
        assertTrue(done.await(4, TimeUnit.MINUTES));
        for (int i = 0; i < 4; i++) {
            RunGolden g = golden("pinene", profiles[i], seeds[i][0]);
            RunResult r = slots.get(i).lastResult();
            assertEquals(r, finished.get(slots.get(i).id()));
            assertEquals(g.status(), r.goldenStatus(), g.id.toString());
            assertEquals(g.iterations(), r.iterations(), g.id.toString());
            assertEquals(g.record.lng("rngDraws"), r.rngDraws(), g.id.toString());
            if (!g.record.isNull("bestScore")) {
                assertEquals(g.record.lng("bestScore"), r.bestScore().score, g.id.toString());
                assertEquals(g.record.str("bestMDL"), r.bestStructure(), g.id.toString());
            }
            assertEquals(g.record.lng("nResults"), slots.get(i).result().size());
            assertNull(slots.get(i).failure());
        }
        assertTrue(newBest.get() > 0 && logs.get() > 0 && statuses.get() > 0);
        assertEquals(RunStatus.State.HOLDING, slots.get(0).status().state());

        // Summarize Result: every slot's results, host tags on the copies, duplicates removed, sorted
        StructureGeneratorResult merged = rc.summarize(LegacyCompat.DEFAULT);
        assertTrue(merged.size() > 0);
        for (int i = 0; i + 1 < merged.size(); i++)
            assertTrue(merged.scoreSummaries.get(i).score >= merged.scoreSummaries.get(i + 1).score);
        assertTrue(merged.scoreSummaries.get(0).description.contains("<p>Result from host "));
        assertFalse(slots.get(0).result().scoreSummaries.get(0).description.contains("Result from host"), "slots unchanged");
        // slot 0 and slot 2 ran the same seed: their identical structures collapse
        int total = 0;
        for (RunSlot s : slots) total += s.result().size();
        assertTrue(merged.size() < total);

        rc.clear();
        assertTrue(rc.slots().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> rc.stop(slots.get(0)));
    }

    /** 2001 {@code "local" + sgServerProcesses.size()}: after "Clear Server List" the next slot is local0. */
    @Test
    void slotIdsRestartAfterClear() {
        RunController rc = new RunController();
        rc.addSlot();
        rc.addSlot();
        rc.clear();
        assertEquals("local0", rc.addSlot().id());
        assertEquals("local1", rc.addSlot().id());
    }

    @Test
    void stopResetAndRestart() throws Exception {
        RunController rc = new RunController();
        RunSlot s = rc.addSlot();
        assertEquals(RunStatus.State.IDLE, s.status().state());
        assertEquals(0, s.result().size());
        assertTrue(s.annealingLogSince(0).isEmpty());
        // eupatorio gui seed 1 never converges (kT = -0.0, golden: stopped by the 5,000,000 cap)
        CountDownLatch some = new CountDownLatch(1);
        rc.addListener(new RunListener() {
            @Override
            public void onStatus(RunSlot slot, RunStatus status) {
                if (status.iteration() >= 2000) some.countDown();
            }
        });
        rc.start(s, GoldenFixtures.runInput("eupatorio", "gui"), RunOptions.golden(1));
        assertTrue(some.await(2, TimeUnit.MINUTES));
        assertTrue(s.isRunning());
        assertEquals(RunStatus.State.RUNNING, s.status().state());
        rc.stop(s);
        awaitIdle(s);
        RunResult r = s.lastResult();
        assertNotNull(r);
        assertTrue(r.stoppedByUser());
        assertEquals(RunStatus.State.STOPPED, s.status().state());
        assertTrue(s.annealingLogSince(0).size() >= 4);
        assertTrue(s.result().size() > 0);

        // restart in the same slot while running: the first run is stopped and joined first
        rc.start(s, GoldenFixtures.runInput("eupatorio", "gui"), RunOptions.golden(1));
        rc.start(s, GoldenFixtures.runInput("pinene", "paper"), RunOptions.golden(1));
        awaitIdle(s);
        assertEquals(golden("pinene", "paper", 1).iterations(), s.lastResult().iterations());

        rc.reset(s);
        assertEquals(RunStatus.State.IDLE, s.status().state());
        assertNull(s.lastResult());
        assertEquals(0, s.result().size());

        // DEFAULT rejects inputs on which legacy fails before producing anything; nothing is started
        RunInput noSchedule = new RunInput(GoldenFixtures.runInput("pinene", "paper").atoms(),
                GoldenFixtures.runInput("pinene", "paper").judges(), List.of(), "pinene");
        assertThrows(IllegalArgumentException.class, () -> rc.start(s, noSchedule, RunOptions.application()));
        assertFalse(s.isRunning());
        rc.shutdown();
    }

    @Test
    void summarizeStrictReproducesLegacyFailures() {
        RunController rc = new RunController();
        rc.addSlot();
        assertThrows(IllegalStateException.class, () -> rc.summarize(LegacyCompat.STRICT), "legacy loops for ever on no results");
        assertEquals(0, rc.summarize(LegacyCompat.DEFAULT).size());
    }
}
