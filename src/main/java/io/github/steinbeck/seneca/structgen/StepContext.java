package io.github.steinbeck.seneca.structgen;

/**
 * Read access to the generator state during {@link StepObserver} callbacks (generator thread only).
 * Matrices are live references: do not modify, copy if kept.
 */
public interface StepContext {

    /** Completed connected main-loop steps (legacy {@code iteration}). */
    long iteration();

    /** {@code current_kT} (in beforeAcceptance: the value used for this step's Metropolis test). */
    double currentKT();

    /** Score of the candidate of this step ({@code recentScore.score}). */
    long candidateScore();

    /** The candidate matrix of this step. */
    int[][] candidate();

    /** The current structure ({@code bondMatrix}); after an accepted step it is {@link #candidate()}. */
    int[][] current();

    /** In afterStep: whether the candidate was accepted. */
    boolean accepted();

    /** {@code lastScore.score}. */
    long lastScore();

    /** {@code bestScore.score}. */
    long bestScore();

    /** Number of stored results (LIFO queue, at most 30). */
    int resultCount();

    /** Random draws consumed so far. */
    long draws();

    /** Same as the Stop button ({@code end()}); the step in progress completes. */
    void requestStop();

    /** Deep copy of the result list as it is now. */
    StructureGeneratorResult resultCopy();
}
