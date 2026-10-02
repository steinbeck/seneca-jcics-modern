package io.github.steinbeck.seneca.app.ui;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.SwingUtilities;

import io.github.steinbeck.seneca.judges.ScoreSummary;
import io.github.steinbeck.seneca.structgen.RunListener;
import io.github.steinbeck.seneca.structgen.RunResult;
import io.github.steinbeck.seneca.structgen.RunSlot;
import io.github.steinbeck.seneca.structgen.RunStatus;

/**
 * Moves {@link RunListener} callbacks from the run threads to the event dispatch thread
 * (docs/port/gui-design.md, section "Threading"). Register an instance with
 * {@code RunController.addListener}; the wrapped {@code target} is only ever called on the EDT, in the
 * order the run produced the events.
 *
 * <ul>
 *   <li>The callbacks on the run thread only enqueue and return at once (they must not block the run).</li>
 *   <li>Status updates are coalesced per slot: if the EDT is behind, only the newest pending
 *       {@link RunStatus} of a slot is delivered (a status is a complete snapshot).</li>
 *   <li>Log messages, new best structures and the end of a run are delivered one by one, in order.</li>
 *   <li>One drain task is pending on the EDT at a time; it delivers everything queued so far.</li>
 *   <li>An exception of the target is logged and swallowed (it must not stop later deliveries).</li>
 * </ul>
 */
public final class EdtRunListener implements RunListener {

    private static final Logger LOG = Logger.getLogger(EdtRunListener.class.getName());

    private final RunListener target;
    private final Executor edt;
    private final Queue<Runnable> queue = new ConcurrentLinkedQueue<>();
    private final Map<RunSlot, RunStatus> pendingStatus = new ConcurrentHashMap<>();
    private final AtomicBoolean scheduled = new AtomicBoolean();

    /** Delivers to {@code target} on the Swing event dispatch thread. */
    public EdtRunListener(RunListener target) {
        this(target, SwingUtilities::invokeLater);
    }

    /** Delivers through {@code edt} (tests may pass a direct or recording executor). */
    public EdtRunListener(RunListener target, Executor edt) {
        this.target = target;
        this.edt = edt;
    }

    @Override
    public void onStatus(RunSlot slot, RunStatus status) {
        // only the first status since the last delivery enqueues a task; later ones replace the value
        if (pendingStatus.put(slot, status) == null) {
            enqueue(() -> {
                RunStatus s = pendingStatus.remove(slot);
                if (s != null) target.onStatus(slot, s);
            });
        }
    }

    @Override
    public void onLog(RunSlot slot, String message) {
        enqueue(() -> target.onLog(slot, message));
    }

    @Override
    public void onNewBest(RunSlot slot, String mdl, ScoreSummary summary) {
        enqueue(() -> target.onNewBest(slot, mdl, summary));
    }

    @Override
    public void onFinished(RunSlot slot, RunResult result) {
        enqueue(() -> target.onFinished(slot, result));
    }

    private void enqueue(Runnable r) {
        queue.add(r);
        if (scheduled.compareAndSet(false, true)) edt.execute(this::drain);
    }

    private void drain() {
        scheduled.set(false);
        Runnable r;
        while ((r = queue.poll()) != null) {
            try {
                r.run();
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "run listener failed on the EDT", e);
            }
        }
    }
}
