package io.github.steinbeck.seneca.judges;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.steinbeck.seneca.chem.AtomSets;

/**
 * The judges of one run in {@code sd.judges} order and the scoring of a candidate exactly like
 * {@code StochasticGenerator.getScore()}, {@code getMaxScore()} and {@code initJudges()}
 * ({@code legacy/original/src/seneca/structgen/StochasticGenerator.java}; architecture.md 4.9, 5).
 *
 * <p>Used by the generator (work package C) and directly by the golden score tests and the CLI
 * (independent scorer of the harness, golden/FORMAT.md (b)). The order of judges is the dataset
 * order created by {@code SenecaDataset.init()}: HHCOSY, HMBC, HeteroBonds, Symmetry, Hybridization,
 * HOSECode, Feature.
 */
public final class JudgeSet {

    /** Result of {@link #score}: what {@code getScore()} returns plus the per-judge breakdown. */
    public static final class Breakdown {
        /** Sum of the enabled judges' scores, clamped at 0 ({@code getScore()}). */
        public final long total;
        /** Unclamped sum. */
        public final long raw;
        /** Enabled judges in order, and their results (same index). */
        public final List<Judge> judges;
        public final List<JudgeResult> results;
        /** Concatenated descriptions, each followed by {@code "\n"} (legacy {@code ScoreSummary.description}). */
        public final String description;

        Breakdown(long total, long raw, List<Judge> judges, List<JudgeResult> results, String description) {
            this.total = total;
            this.raw = raw;
            this.judges = Collections.unmodifiableList(judges);
            this.results = Collections.unmodifiableList(results);
            this.description = description;
        }

        /** {@code new ScoreSummary(total, description)} as {@code getScore()} returns it. */
        public ScoreSummary toScoreSummary() {
            return new ScoreSummary(total, description);
        }
    }

    private final List<Judge> judges;

    /** @param judges all judges of the run (enabled or not), in dataset order; used as given (no copy) */
    public JudgeSet(List<Judge> judges) {
        this.judges = judges;
    }

    /** Deep copies of all judges (legacy {@code serClone(sd.judges)}). */
    public static List<Judge> deepCopy(List<Judge> judges) {
        List<Judge> c = new ArrayList<>(judges.size());
        for (Judge j : judges) c.add(j.copy());
        return c;
    }

    public List<Judge> judges() {
        return judges;
    }

    /** Names of the enabled judges in order. */
    public List<String> enabledNames() {
        List<String> l = new ArrayList<>();
        for (Judge j : judges) if (j.getEnabled()) l.add(j.name);
        return l;
    }

    /** {@code initJudges()}: {@code setAttic(attic); init()} on every enabled judge, in order. */
    public void initJudges(Attic attic) {
        for (Judge j : judges) {
            if (j.getEnabled()) {
                j.setAttic(attic);
                j.init();
            }
        }
    }

    /**
     * {@code getMaxScore()} of the generator: sum of {@code getMaxScore()} over enabled judges in int
     * arithmetic; -1 if an enabled judge has {@code hasMaxScore == false}.
     */
    public int maxScore() {
        int max = 0;
        for (Judge j : judges) {
            if (j.getEnabled()) {
                if (!j.getHasMaxScore()) return -1;
                max += j.getMaxScore();
            }
        }
        return max;
    }

    /**
     * {@code getScore(bm)}: evaluates every enabled judge in order on the candidate already
     * synchronized into the judges' attic, sums in {@code long}, clamps negative sums to 0.
     * Exceptions of a judge propagate (STRICT ring-finder crash).
     */
    public Breakdown score(int[][] bm) {
        long sum = 0;
        StringBuilder description = new StringBuilder();
        List<Judge> used = new ArrayList<>();
        List<JudgeResult> results = new ArrayList<>();
        for (Judge j : judges) {
            if (j.getEnabled()) {
                JudgeResult r = j.evaluate(bm);
                sum += r.score;
                description.append(r.scoreDescription).append('\n');
                used.add(j);
                results.add(r);
            }
        }
        return new Breakdown(sum < 0 ? 0 : sum, sum, used, results, description.toString());
    }

    /**
     * Convenience for fixed structures (golden (b), CLI): {@code AtomSets.synchronizeBonds(attic.atomSet, bm)}
     * then {@link #score}.
     */
    public Breakdown scoreStructure(Attic attic, int[][] bm) {
        AtomSets.synchronizeBonds(attic.atomSet, bm);
        return score(bm);
    }
}
