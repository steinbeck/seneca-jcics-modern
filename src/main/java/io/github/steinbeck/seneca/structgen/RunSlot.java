package io.github.steinbeck.seneca.structgen;

import java.util.List;

/**
 * One row of the "Structure Generation" run table (legacy: one local "server", F-60): ID
 * {@code "local" + k}, host "local", at most one active run at a time. Created by
 * {@link RunController#addSlot()}; all state changes go through the controller.
 *
 * <p>Thread safety: the fields are volatile and replaced as a whole by the controller (under the slot's
 * monitor); the read methods may be called from any thread.
 */
public final class RunSlot {

    private final String id;
    private volatile StochasticGenerator generator;
    private volatile Thread thread;
    private volatile RunResult lastResult;
    private volatile Throwable failure;

    RunSlot(String id) {
        this.id = id;
    }

    /** {@code "local" + k} (F-61). */
    public String id() {
        return id;
    }

    /** Host column: always "local" (no RMI). */
    public String host() {
        return "local";
    }

    /** Snapshot of the current (or last) run; state IDLE before the first start and after a reset. */
    public RunStatus status() {
        StochasticGenerator g = generator;
        if (g == null) return new RunStatus(null, "not available", 0, null, null, RunStatus.State.IDLE, 0);
        return g.status();
    }

    /** Deep copy of the current (or last) run's results; empty before the first start. */
    public StructureGeneratorResult result() {
        StochasticGenerator g = generator;
        return g == null ? new StructureGeneratorResult() : g.resultCopy();
    }

    /** True if a run was started since the last reset (legacy: the server has a task). */
    public boolean hasRun() {
        return generator != null;
    }

    /** Annealing log entries {@code [from, size)} of the current (or last) run. */
    public List<AnnealingLog.Entry> annealingLogSince(int from) {
        StochasticGenerator g = generator;
        return g == null ? List.of() : g.annealingLogSince(from);
    }

    /** The final result of the last finished run, or null. */
    public RunResult lastResult() {
        return lastResult;
    }

    /**
     * An {@link Error} (or other throwable) that escaped {@link StochasticGenerator#run()} on the run
     * thread (e.g. {@code StackOverflowError}), or null. Engine exceptions are in {@link RunResult#exception()}.
     */
    public Throwable failure() {
        return failure;
    }

    /** True while a run thread is alive. */
    public boolean isRunning() {
        Thread t = thread;
        return t != null && t.isAlive();
    }

    // ---- controller side

    StochasticGenerator generator() {
        return generator;
    }

    Thread thread() {
        return thread;
    }

    void begin(StochasticGenerator g, Thread t) {
        lastResult = null;
        failure = null;
        generator = g;
        thread = t;
    }

    void finished(RunResult r, Throwable f) {
        lastResult = r;
        failure = f;
    }

    void clearRun() {
        generator = null;
        thread = null;
        lastResult = null;
        failure = null;
    }

    @Override
    public String toString() {
        return id;
    }
}
