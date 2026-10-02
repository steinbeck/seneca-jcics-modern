package io.github.steinbeck.seneca.structgen;

/**
 * Push notifications from runs (alternative to polling {@link RunSlot#status()} every 2 s as the
 * legacy GUI does). Called on the run's thread: a Swing GUI must hop to the EDT
 * ({@code SwingUtilities.invokeLater}). Implementations must be fast and must not throw.
 */
public interface RunListener {

    /** Throttled progress (at most every 500 iterations and on state changes). */
    default void onStatus(RunSlot slot, RunStatus status) {
    }

    /** An engine INFO message (Logbook). */
    default void onLog(RunSlot slot, String message) {
    }

    /** A new best structure was stored (MDL text, deep-copied summary). */
    default void onNewBest(RunSlot slot, String mdl, io.github.steinbeck.seneca.judges.ScoreSummary summary) {
    }

    /** The run ended. */
    default void onFinished(RunSlot slot, RunResult result) {
    }
}
