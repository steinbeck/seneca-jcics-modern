package io.github.steinbeck.seneca.golden;

import java.util.ArrayList;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.chem.AtomSets;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.chem.LegacyMdlReader;
import io.github.steinbeck.seneca.structgen.RunInput;
import io.github.steinbeck.seneca.structgen.RunResult;

/**
 * Pieces shared by {@link TrajectoryRecords} and {@link RunRecords}: the harness's independent scorer,
 * {@code GoldenMaster.ctFromMDL} and {@code GoldenMaster.crashJson}.
 */
final class RunSupport {

    private RunSupport() {
    }

    /**
     * {@code new Scorer(goScorer.molecule, goScorer.judges)}: the judges of a private copy of the input
     * (take it before the run), initialised once on an unbonded clone of the atoms; scoring in the run's
     * compatibility mode (so that DEFAULT runs are re-scored with the DEFAULT ring-finder fallback).
     */
    static ScoreRecords.Scorer scorer(RunInput copy, LegacyCompat compat) {
        return new ScoreRecords.Scorer(copy.atoms(), new ArrayList<>(copy.judges()), compat);
    }

    /** {@code GoldenMaster.ctFromMDL}: bond matrix of a stored MDL string (atom order = dataset order). */
    static int[][] ctFromMdl(String mdl) {
        HeavyAtom[] atoms = LegacyMdlReader.read(mdl);
        int[][] bm = AtomSets.createConnectionTable(atoms);
        AtomSets.synchronizeConnectionTable(bm);
        return bm;
    }

    /**
     * {@code GoldenMaster.crashJson}: iteration, the candidate being scored when the run died
     * ({@code attic.atomSet}), the current structure, and class/message/top frame of the exception
     * obtained by re-scoring the candidate. {@code at} is {@code <class>.<method>} of the port's top frame
     * (no line number, DV-30).
     */
    static Json crashJson(RunResult r, ScoreRecords.Scorer scorer) {
        int[][] bm = r.crashCandidate();
        Json o = Json.obj().put("iteration", r.iterations()).put("candidateHash", Hashes.hash(bm)).put("candidateCt", Hashes.ct(bm))
                .put("currentHash", r.currentStructure() == null ? null : Hashes.hash(r.currentStructure()));
        try {
            scorer.score(bm);
            o.putNull("exception");
        } catch (RuntimeException e) {
            StackTraceElement top = e.getStackTrace().length > 0 ? e.getStackTrace()[0] : null;
            o.put("exception", e.getClass().getName()).put("message", e.getMessage())
                    // class and method only: a line number would change the record with every edit of
                    // the port's source (legacy wrote "compchem.SetOfRingsFinder.breakBond:302"; DV-30)
                    .put("at", top == null ? null : top.getClassName() + "." + top.getMethodName());
        }
        return o;
    }
}
