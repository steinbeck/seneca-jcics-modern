package io.github.steinbeck.seneca.structgen;

import io.github.steinbeck.seneca.judges.ScoreSummary;

/**
 * Immutable progress snapshot for the GUI (replaces {@code StructureGeneratorStatus}, F-60/F-69;
 * {@code legacy/original/src/seneca/structgen/StructureGeneratorStatus.java}). Unlike legacy
 * {@code getStatus()} it never throws (Q1: no best structure yet -&gt; {@code bestStructure == null}),
 * does not mutate the live best summary, and does not drain the annealing log (D7): the GUI fetches
 * entries with {@code StochasticGenerator.annealingLogSince(index)}.
 *
 * @param datasetName      dataset name
 * @param molecularFormula {@code MFAnalyser(atomSet).getMolecularFormula()} or "not available"
 * @param iteration        completed connected steps
 * @param bestStructure    MDL of the best structure, null if none yet
 * @param bestEvaluation   copy of the best summary with {@code maxScore} set (legacy getStatus did this), null if none
 * @param state            the legacy status string as enum
 * @param annealingLogSize number of annealing log entries so far
 */
public record RunStatus(String datasetName, String molecularFormula, long iteration, String bestStructure,
                        ScoreSummary bestEvaluation, State state, int annealingLogSize) {

    /**
     * Legacy status strings. IDLE: before start; STOPPED: a stop was requested ({@code pleaseStop});
     * RUNNING: {@code run()} has not returned; HOLDING: {@code run()} returned without a stop request
     * (legacy derived RUNNING/HOLDING from {@code goOn} and so showed "Holding Results" during the
     * calibration and "Running" after a crash, DEVIATIONS.md DV-61).
     */
    public enum State {
        IDLE("Idle"), RUNNING("Running"), HOLDING("Holding Results"), STOPPED("Stopped by User");

        public final String label;

        State(String label) {
            this.label = label;
        }
    }
}
