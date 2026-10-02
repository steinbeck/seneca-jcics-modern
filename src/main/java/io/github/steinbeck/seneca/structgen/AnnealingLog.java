package io.github.steinbeck.seneca.structgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Annealing log of one run: one entry per {@code alStepsize} (500) connected iterations. Port of
 * {@code legacy/original/src/seneca/structgen/AnnealingLog.java} for the single-series use of the
 * generator (the GUI's multi-series chart keeps its own data).
 *
 * <p>Legacy semantics kept: values are {@code float}; the x value is {@code itemCount * 500} at the
 * time of adding, i.e. lagged (the entry added at iteration 500 has x = 0); the generator adds
 * {@code addEntry((float) current_kT, (float) bestScore.score)} after {@code cool()} when
 * {@code iteration % 500 == 0} (legacy compares doubles: {@code (double)it/500 == (int)((double)it/500)}).
 * The legacy class stores {x, score, kT}; the client-side copy {x, kT, score} - both are just this record.
 *
 * <p>Thread safety: appended by the generator thread, read by pollers - synchronized.
 */
public final class AnnealingLog {

    /** One log entry (all legacy {@code Float}s). */
    public record Entry(float iteration, float kT, float bestScore) {
    }

    public static final int ITERATION_AXIS_STEPSIZE = 500;

    private final List<Entry> entries = new ArrayList<>();

    /** Legacy {@code addEntry(float temperature, float score)}: x = itemCount * 500. */
    public synchronized void addEntry(float temperature, float score) {
        entries.add(new Entry((float) (entries.size() * ITERATION_AXIS_STEPSIZE), temperature, score));
    }

    public synchronized int size() {
        return entries.size();
    }

    /** Entries {@code [from, size)} (D7 fix: every consumer tracks its own index; nothing is drained). */
    public synchronized List<Entry> since(int from) {
        return Collections.unmodifiableList(new ArrayList<>(entries.subList(Math.min(from, entries.size()), entries.size())));
    }

    public synchronized List<Entry> all() {
        return since(0);
    }
}
