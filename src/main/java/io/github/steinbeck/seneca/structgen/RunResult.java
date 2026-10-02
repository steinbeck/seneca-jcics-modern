package io.github.steinbeck.seneca.structgen;

import io.github.steinbeck.seneca.judges.ScoreSummary;

/**
 * Final state of a run (immutable; deep copies). Field meanings follow golden/FORMAT.md (d).
 *
 * @param iterations       connected main-loop steps ({@code iteration})
 * @param rngDraws         random draws consumed by the whole run
 * @param maxScore         the generator's max score ({@code getMaxScore()}, -1 if a judge has none)
 * @param startKT          {@code start_kT} of the last initialised schedule (signed zero preserved)
 * @param finalKT          {@code current_kT} at the end
 * @param plateauSteps     {@code plateauSteps} at the end (after reheats)
 * @param maxUphillSteps   {@code maxUphillSteps} at the end
 * @param bestScore        copy of {@code bestScore} (score 0, "" if no result)
 * @param bestStructure    MDL text of the best structure, null if no result
 * @param result           deep copy of the result list
 * @param exception        the exception that ended {@code run()} (legacy: caught, "Error in StoicGent.run()"), else null
 * @param crashCandidate   with an exception: the candidate being scored ({@code attic.atomSet} as matrix), else null
 * @param currentStructure {@code bondMatrix} at the end (null if no start structure)
 * @param capped           ended by {@code RunOptions.iterationCap}
 * @param stoppedByUser    {@code end()} was called (by the user or an observer), excluding the cap
 */
public record RunResult(long iterations, long rngDraws, long maxScore, double startKT, double finalKT,
                        long plateauSteps, long maxUphillSteps, ScoreSummary bestScore, String bestStructure,
                        StructureGeneratorResult result, Throwable exception, int[][] crashCandidate,
                        int[][] currentStructure, boolean capped, boolean stoppedByUser) {

    /**
     * Golden {@code status}: "exception_in_run", "iteration_cap", "no_result(Q1)" (no result stored)
     * or "finished" - derived exactly as the harness does (GoldenMaster.full).
     */
    public String goldenStatus() {
        if (exception != null) return "exception_in_run";
        if (capped) return "iteration_cap";
        if (result.size() == 0) return "no_result(Q1)";
        return "finished";
    }

    /** Golden {@code termination}: for "finished", "max_score_reached" or "reheat_limit"; else the status. */
    public String goldenTermination() {
        String s = goldenStatus();
        if (!s.equals("finished")) return s;
        return bestScore.score >= maxScore ? "max_score_reached" : "reheat_limit";
    }
}
