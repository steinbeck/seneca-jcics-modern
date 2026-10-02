package io.github.steinbeck.seneca.structgen;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.judges.ScoreSummary;

/**
 * In-process replacement of {@code StructureGeneratorServer} + {@code Compute} + {@code GateKeeper}
 * and of the run-control half of {@code SGServerPanel} (architecture.md 3.1; feature-inventory F-60..F-70).
 * The GUI calls only this class; no Swing here.
 *
 * <ul>
 *   <li>{@link #addSlot()} = "Start local server" (F-61).</li>
 *   <li>{@link #start} = "Start as selected" for one slot (F-64): stops and joins a run already active
 *       in that slot first (legacy orphaned it, D15), then runs {@link StochasticGenerator#run()} on a new
 *       daemon platform thread named {@code "seneca-" + slot.id()}. The caller builds the input with
 *       {@link RunInput#fromDataset}; the generator takes its own deep copy, so one input may be started
 *       in several slots. In DEFAULT mode the generator's constructor rejects inputs on which the legacy
 *       engine fails or hangs ({@link IllegalArgumentException}, thrown here on the caller's thread
 *       before anything else happens: nothing is started and a run already active in the slot goes on).</li>
 *   <li>{@link #stop} = "Stop Selected" (F-65): {@code end()}, does not wait.</li>
 *   <li>{@link #reset} = "Reset selected" (F-66): stop, wait for the thread, discard run and log data.</li>
 *   <li>{@link #clear} = "Clear Server List" (F-63): stop all, remove all slots.</li>
 *   <li>{@link #summarize} = "Summarize Result" (F-67): deep copies of every slot's result merged into a
 *       {@code StructureGeneratorResult(nSlots * 30)} in slot order (each slot's entries popped from the
 *       copy and pushed, as legacy), then {@code removeIsomorphism(compat)} and {@code sort()};
 *       descriptions get {@code "<p>Result from host " + (i + 1) + "</p>"} appended on the copies only.</li>
 *   <li>{@link #shutdown} stops all runs (application exit).</li>
 * </ul>
 * Several runs execute concurrently and independently (own {@link RunRandom}, own judge copies).
 * All methods are thread-safe. Listener callbacks run on the run's thread (see {@link RunListener});
 * an exception thrown by a listener is swallowed so that it cannot change a run.
 */
public final class RunController {

    /** How long {@link #reset}, {@link #clear}, {@link #start} and {@link #shutdown} wait for a stopped run. */
    public static final long JOIN_MILLIS = 30_000;

    private final List<RunSlot> slots = new CopyOnWriteArrayList<>();
    private final List<RunListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicInteger nextId = new AtomicInteger();

    public RunSlot addSlot() {
        RunSlot s = new RunSlot("local" + nextId.getAndIncrement());
        slots.add(s);
        return s;
    }

    public List<RunSlot> slots() {
        return List.copyOf(slots);
    }

    public void start(RunSlot slot, RunInput input, RunOptions options) {
        requireOwn(slot);
        synchronized (slot) {
            // construct (and so validate) first: a rejected input must not stop the slot's running run
            Hooks hooks = new Hooks(slot, options.observer(), options.log());
            StochasticGenerator g = new StochasticGenerator(input, options.withObserver(hooks).withLog(hooks));
            stopAndJoin(slot);
            hooks.generator = g;
            Thread t = new Thread(() -> runThread(slot, g, hooks), "seneca-" + slot.id());
            t.setDaemon(true);
            slot.begin(g, t);
            t.start();
        }
    }

    public void stop(RunSlot slot) {
        requireOwn(slot);
        StochasticGenerator g = slot.generator();
        if (g != null) g.end();
    }

    public void reset(RunSlot slot) {
        requireOwn(slot);
        synchronized (slot) {
            stopAndJoin(slot);
            slot.clearRun();
        }
    }

    public void clear() {
        for (RunSlot s : slots) {
            StochasticGenerator g = s.generator();
            if (g != null) g.end();
        }
        for (RunSlot s : slots) {
            synchronized (s) {
                stopAndJoin(s);
                s.clearRun();
            }
        }
        slots.clear();
    }

    /**
     * "Summarize Result": see class doc. STRICT reproduces the legacy collection (it ends at the first
     * slot without a run, DV-47) and {@code removeIsomorphism} (throws with 1 or 2 structures in total,
     * {@link IllegalStateException} instead of the endless loop with 0). DEFAULT skips slots without a
     * run and never fails.
     */
    public StructureGeneratorResult summarize(LegacyCompat compat) {
        List<RunSlot> list = slots();
        int capacity = list.size() * StructureGeneratorResult.DEFAULT_CAPACITY;
        StructureGeneratorResult merged = capacity > 0 ? new StructureGeneratorResult(capacity) : new StructureGeneratorResult();
        for (int i = 0; i < list.size(); i++) {
            RunSlot s = list.get(i);
            if (!s.hasRun()) {
                // legacy: getTaskResult() of a server that never ran throws NPE inside the one try around
                // the loop, so this and every later server contribute nothing (DV-47); DEFAULT skips it
                if (compat.strict()) break;
                continue;
            }
            StructureGeneratorResult r = s.result();
            int members = r.size();
            for (int f = 0; f < members; f++) {
                merged.structures.push(r.structures.pop());
                ScoreSummary ss = r.scoreSummaries.pop();
                ss.description += "<p>Result from host " + (i + 1) + "</p>";
                merged.scoreSummaries.push(ss);
            }
        }
        merged.removeIsomorphism(compat);
        merged.sort();
        return merged;
    }

    public void addListener(RunListener listener) {
        listeners.add(listener);
    }

    public void removeListener(RunListener listener) {
        listeners.remove(listener);
    }

    public void shutdown() {
        for (RunSlot s : slots) {
            StochasticGenerator g = s.generator();
            if (g != null) g.end();
        }
        for (RunSlot s : slots) join(s);
    }

    // ------------------------------------------------------------------ internals

    private void requireOwn(RunSlot slot) {
        if (!slots.contains(slot)) throw new IllegalArgumentException("slot " + slot + " does not belong to this controller");
    }

    private static void stopAndJoin(RunSlot slot) {
        StochasticGenerator g = slot.generator();
        if (g != null) g.end();
        join(slot);
    }

    private static void join(RunSlot slot) {
        Thread t = slot.thread();
        if (t == null || t == Thread.currentThread()) return;
        try {
            t.join(JOIN_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void runThread(RunSlot slot, StochasticGenerator g, Hooks hooks) {
        RunResult r = null;
        Throwable failure = null;
        try {
            hooks.status();
            r = g.run();
        } catch (Throwable t) {
            failure = t;
        }
        slot.finished(r, failure);
        hooks.status();
        if (r != null) {
            for (RunListener l : listeners) {
                try {
                    l.onFinished(slot, r);
                } catch (RuntimeException ignored) {
                    // a listener must not influence the run
                }
            }
        }
    }

    /** Observer and log of one run: forwards to the caller's observer/log and to the listeners. */
    private final class Hooks implements StepObserver, EngineLog {
        final RunSlot slot;
        final StepObserver user;
        final EngineLog userLog;
        volatile StochasticGenerator generator;

        Hooks(RunSlot slot, StepObserver user, EngineLog userLog) {
            this.slot = slot;
            this.user = user;
            this.userLog = userLog;
        }

        void status() {
            StochasticGenerator g = generator;
            if (g == null || listeners.isEmpty()) return;
            RunStatus s = g.status();
            for (RunListener l : listeners) {
                try {
                    l.onStatus(slot, s);
                } catch (RuntimeException ignored) {
                    // a listener must not influence the run
                }
            }
        }

        @Override
        public void onStartStructure(int[][] bondMatrix) {
            if (user != null) user.onStartStructure(bondMatrix);
        }

        @Override
        public void onInitEvaluation(int schedule, int sample, long score, int[][] bm, long draws) {
            if (user != null) user.onInitEvaluation(schedule, sample, score, bm, draws);
        }

        @Override
        public void onScheduleInitialized(int schedule, double startKT, long plateauSteps, long maxUphillSteps,
                                          long convergenceStopCount, double coolingRate) {
            if (user != null) user.onScheduleInitialized(schedule, startKT, plateauSteps, maxUphillSteps, convergenceStopCount, coolingRate);
            status();
        }

        @Override
        public void beforeAcceptance(StepContext ctx) {
            if (user != null) user.beforeAcceptance(ctx);
        }

        @Override
        public void onNewBest(String mdl, ScoreSummary summary) {
            if (user != null) user.onNewBest(mdl, summary);
            for (RunListener l : listeners) {
                try {
                    l.onNewBest(slot, mdl, summary.copy());
                } catch (RuntimeException ignored) {
                    // a listener must not influence the run
                }
            }
        }

        @Override
        public void afterStep(StepContext ctx) {
            if (user != null) user.afterStep(ctx);
            if (ctx.iteration() % AnnealingLog.ITERATION_AXIS_STEPSIZE == 0) status();
        }

        @Override
        public void onFinished(RunResult result) {
            if (user != null) user.onFinished(result);
        }

        @Override
        public void info(String message) {
            userLog.info(message);
            for (RunListener l : listeners) {
                try {
                    l.onLog(slot, message);
                } catch (RuntimeException ignored) {
                    // a listener must not influence the run
                }
            }
        }

        @Override
        public void result(String sdfBlock) {
            userLog.result(sdfBlock);
        }
    }
}
