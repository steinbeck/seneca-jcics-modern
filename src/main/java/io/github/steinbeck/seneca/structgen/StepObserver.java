package io.github.steinbeck.seneca.structgen;

/**
 * Observes a run step by step without influencing it (port replacement of the golden harness's
 * {@code TraceProbe} pseudo-judge, golden/FORMAT.md (c)). Called on the generator thread.
 *
 * <p>Contract: an observer must not modify the matrices it is given, must not draw random numbers
 * and must not throw. The only permitted influence is {@link StepContext#requestStop()} (equivalent to
 * the Stop button, which is how the harness ends a 5000-step trajectory). With no observer the
 * generator must not pay for the hooks beyond a null check.
 */
public interface StepObserver {

    /** The random start structure exists ({@code bondMatrix} after {@code synchronizeConnectionTable}). */
    default void onStartStructure(int[][] bondMatrix) {
    }

    /**
     * One connected calibration sample of {@code initAnnealing()} was scored.
     *
     * @param schedule 0-based schedule index
     * @param sample   1..500
     * @param score    the clamped candidate score ({@code getScore(bm).score})
     * @param bm       the sampled structure
     * @param draws    random draws consumed so far (including this sample's mutation)
     */
    default void onInitEvaluation(int schedule, int sample, long score, int[][] bm, long draws) {
    }

    /** {@code initAnnealing()} finished; values now in effect. */
    default void onScheduleInitialized(int schedule, double startKT, long plateauSteps, long maxUphillSteps,
                                       long convergenceStopCount, double coolingRate) {
    }

    /**
     * A connected main-loop candidate was scored; the Metropolis draw has <b>not</b> been made yet
     * ({@link StepContext#draws()} excludes it). {@code ctx.iteration()} is the number of completed steps.
     */
    default void beforeAcceptance(StepContext ctx) {
    }

    /**
     * The step finished (acceptance, best/result update, {@code iteration++}, {@code cool()}, annealing
     * log, convergence/reheat handling). {@code ctx.iteration()} now includes this step.
     */
    default void afterStep(StepContext ctx) {
    }

    /**
     * A new best structure was stored (legacy: {@code senecaresult.info}, the result queues). Called after
     * the stored state was updated and before {@link #afterStep}; {@code summary} is a private copy.
     */
    default void onNewBest(String mdl, io.github.steinbeck.seneca.judges.ScoreSummary summary) {
    }

    /** The run ended (normally, by stop/cap, by Q1, or by an exception). */
    default void onFinished(RunResult result) {
    }
}
