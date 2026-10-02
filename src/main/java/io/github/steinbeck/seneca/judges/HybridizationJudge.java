package io.github.steinbeck.seneca.judges;

import java.util.List;

/**
 * Hybridization judge. Port of {@code legacy/original/src/seneca/judges/HybridizationJudge.java};
 * architecture.md 5.5.
 *
 * <ul>
 *   <li>{@code multiplicator = 100}; {@link #addHybridizationScore}{@code (node1Based, hyb, value)}
 *       stores {@code (int)(value * multiplicator)}.</li>
 *   <li>{@link #init()}: {@code if (attic.atomSet == null) return; scores = new int[n][3]} - wipes the
 *       configuration on the generator side (legacy defect D1/Q4, reproduced in both modes: it is
 *       score-determining, not a failure).</li>
 *   <li>{@link #calcMaxScore()}: sum over atoms of max(scores[f][*]).</li>
 *   <li>{@link #evaluate}: carbons by degree and {@code getBondCount()} (table in architecture.md 5.5);
 *       description uses the unrelated base field {@code score} (always 0).</li>
 * </ul>
 */
public class HybridizationJudge extends Judge {

    public static final int SP3 = 0, SP2 = 1, SP = 2;
    public static final List<String> HYBRIDIZATIONSTRINGS = List.of("SP3", "SP2", "SP");

    /** {@code [atom][SP3|SP2|SP]} scores (already multiplied by {@link #multiplicator}). */
    public int[][] scores;

    public HybridizationJudge() {
        super("HybridizationJudge");
        multiplicator = 100;
        hasMaxScore = true;
    }

    public void initScoreArray(int length) {
        scores = new int[length][3];
    }

    public void addHybridizationScore(int nodeNumber, int hybridization, double score) {
        scores[nodeNumber - 1][hybridization] = (int) (score * multiplicator);
    }

    @Override
    public void init() {
        if (attic.atomSet == null) return;
        scores = new int[attic.atomSet.length][3];
    }

    @Override
    public void calcMaxScore() {
        int max;
        maxScore = 0;
        for (int f = 0; f < scores.length; f++) {
            max = scores[f][0];
            for (int g = 1; g < 3; g++) {
                if (scores[f][g] > max) max = scores[f][g];
            }
            maxScore += max;
        }
    }

    @Override
    public JudgeResult evaluate(int[][] bm) {
        scoreSum = 0;
        for (int f = 0; f < attic.atomSet.length; f++) {
            int bondCount = attic.atomSet[f].getBondCount();
            if (attic.atomSet[f].symbol.equals("C")) {
                int degree = attic.atomSet[f].degree;
                if (degree == 1) {
                    if (bondCount == 1) scoreSum += scores[f][SP3];
                    if (bondCount == 2) scoreSum += scores[f][SP2];
                    if (bondCount == 3) scoreSum += scores[f][SP];
                }
                if (degree == 2) {
                    if (bondCount == 2) scoreSum += scores[f][SP3];
                    if (bondCount == 3) scoreSum += scores[f][SP2];
                    if (bondCount == 4) scoreSum += scores[f][SP];
                }
                if (degree == 3) {
                    if (bondCount == 3) scoreSum += scores[f][SP3];
                    if (bondCount == 4) scoreSum += scores[f][SP2];
                }
                if (degree == 4) {
                    scoreSum += scores[f][SP3];
                }
            }
        }
        // legacy: the description uses the unrelated base field `score` (always 0)
        return new JudgeResult(maxScore, scoreSum, 0, "<p>" + score + " from a possible " + maxScore
                + " points for hybridization state were given.</p>");
    }

    @Override
    public Judge copy() {
        HybridizationJudge c = new HybridizationJudge();
        copyBaseFieldsTo(c);
        if (scores != null) {
            c.scores = new int[scores.length][];
            for (int f = 0; f < scores.length; f++) c.scores[f] = scores[f].clone();
        } else {
            c.scores = null;
        }
        return c;
    }
}
