package io.github.steinbeck.seneca.app;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import io.github.steinbeck.seneca.structgen.RunController;
import io.github.steinbeck.seneca.structgen.RunListener;
import io.github.steinbeck.seneca.structgen.RunResult;
import io.github.steinbeck.seneca.structgen.RunSlot;
import io.github.steinbeck.seneca.structgen.RunStatus;

/**
 * Whether any structure generation run of the application is going on, across all dataset windows
 * (every window's {@link RunController} is {@linkplain #watch watched}). Each change between "no run" and
 * "at least one run" is passed to the sink: the application tells macOS not to throttle it while a run is
 * going on ({@link MacActivity}; without it, App Nap slowed a run in the background about fourfold,
 * gui-design.md DV-89).
 *
 * <p>A slot counts as running from its first status with state {@code RUNNING} until a status with
 * another state, or one sent after the run ended (result or failure stored), or {@code onFinished}.
 * Thread-safe; the sink is called on the run thread that caused the change, under this object's lock.
 */
public final class RunActivity {

    private final Set<RunSlot> running = new HashSet<>();
    private Consumer<Boolean> sink = active -> { };
    private boolean active;

    private final RunListener listener = new RunListener() {
        @Override
        public void onStatus(RunSlot slot, RunStatus status) {
            boolean ended = slot.lastResult() != null || slot.failure() != null;
            update(slot, status.state() == RunStatus.State.RUNNING && !ended);
        }

        @Override
        public void onFinished(RunSlot slot, RunResult result) {
            update(slot, false);
        }
    };

    /** Where changes go ({@code true}: a run started while none was going on; {@code false}: the last one ended). */
    public synchronized void setSink(Consumer<Boolean> sink) {
        this.sink = sink;
        if (active) sink.accept(true);
    }

    /** Follows the runs of {@code runs} (a dataset window's controller). */
    public void watch(RunController runs) {
        runs.addListener(listener);
    }

    /** Stops following {@code runs}; its slots no longer count. */
    public void unwatch(RunController runs) {
        runs.removeListener(listener);
        synchronized (this) {
            running.removeAll(runs.slots());
            changed();
        }
    }

    /** True while at least one run is going on. */
    public synchronized boolean active() {
        return active;
    }

    private synchronized void update(RunSlot slot, boolean isRunning) {
        if (isRunning) running.add(slot);
        else running.remove(slot);
        changed();
    }

    private void changed() {
        boolean now = !running.isEmpty();
        if (now == active) return;
        active = now;
        try {
            sink.accept(now);
        } catch (RuntimeException ignored) {
            // never disturbs a run
        }
    }
}
