package io.github.steinbeck.seneca.judges;

import java.util.ArrayList;
import java.util.List;

import io.github.steinbeck.seneca.chem.PathUtils;

/**
 * Base of {@link HMBCJudge} and {@link HHCOSYJudge}. Port of
 * {@code legacy/original/src/seneca/judges/TwoDSpectrumJudge.java}; architecture.md 5.1.
 *
 * <ul>
 *   <li>{@link #init()}: append a {@link TwoDRule}(f, h) for every true {@code assignment[f][g][h]}
 *       (rules are not cleared); {@code cutoff = 1 + highest i with scores[i] > 0}.</li>
 *   <li>{@link #calcMaxScore()}: number of true entries x max(scores); prints
 *       {@code name + ": " + assignment} to stdout in legacy (drop the print); NPE if
 *       {@code assignment == null} (STRICT keeps the NPE; DEFAULT treats null as no signals,
 *       DEVIATIONS.md).</li>
 *   <li>{@link #evaluate}: null assignment -&gt; 0 and {@code "<p>No signals available for <name></p>"};
 *       else per rule {@code PathUtils.getPathLength(attic.atomSet, a1, a2, cutoff)}, add
 *       {@code scores[p-1]} if {@code p > 0}; "satisfied" counts every rule (quirk Q5). Description and
 *       {@code JudgeResult(maxScore, scoreSum, satisfied, text)} exactly as legacy.</li>
 * </ul>
 */
public abstract class TwoDSpectrumJudge extends Judge {

    /** {@code [from][to][alt]}: correlation between atoms {@code from} and {@code to} (alt = alternative partner). */
    public boolean[][][] assignment;
    protected int numberOf2DSignals = 0;
    private int cutoff;
    /** Score per path length (index = path length - 1), 7 entries. */
    public int[] scores = new int[7];
    public List<TwoDRule> rules = new ArrayList<>();

    protected TwoDSpectrumJudge(String name) {
        super(name);
        hasMaxScore = true;
    }

    /**
     * Must run once per fresh copy before evaluation (legacy comment: "must be called before this Judge
     * is passed to the Structure Generators"): appends one rule per true {@code assignment[f][g][h]}
     * (rules are not cleared) and sets the cutoff. NPE if {@code assignment == null} (legacy); in DEFAULT
     * mode ({@code attic.compat}, DEVIATIONS.md DV-06) a null assignment adds no rules (the judge then
     * scores 0 with "No signals available").
     */
    @Override
    public void init() {
        Attic a = getAttic();
        boolean skipRules = assignment == null && a != null && a.compat != null && !a.compat.strict();
        if (!skipRules) for (int f = 0; f < assignment.length; f++)
            for (int g = 0; g < assignment.length; g++)
                for (int h = 0; h < assignment.length; h++)
                    if (assignment[f][g][h]) rules.add(new TwoDRule(f, h));
        for (int i = 0; i < scores.length; i++) {
            if (scores[i] > 0) cutoff = i + 1;
        }
    }

    /** Number of true entries x max(scores). NPE if {@code assignment == null} (legacy; see RunInput for DEFAULT). */
    @Override
    public void calcMaxScore() {
        int max = 0;
        for (int f = 0; f < scores.length; f++) {
            if (scores[f] > max) max = scores[f];
        }
        numberOf2DSignals = 0;
        for (int f = 0; f < assignment.length; f++)
            for (int g = 0; g < assignment.length; g++)
                for (int h = 0; h < assignment.length; h++)
                    if (assignment[f][g][h]) numberOf2DSignals += 1;
        maxScore = numberOf2DSignals * max;
    }

    @Override
    public JudgeResult evaluate(int[][] bm) {
        if (assignment == null) {
            return new JudgeResult(0, 0, 0, "<p>No signals available for " + name + "</p>");
        }
        scoreSum = 0;
        int satisfiedSignals = 0;
        for (int f = 0; f < rules.size(); f++) {
            TwoDRule rule = rules.get(f);
            int plength = PathUtils.getPathLength(attic.atomSet, attic.atomSet[rule.atom1], attic.atomSet[rule.atom2], cutoff);
            if (plength > 0) scoreSum += scores[plength - 1];
            satisfiedSignals++;
        }
        String resultString = "<p>" + satisfiedSignals + "/" + numberOf2DSignals + " Signals satisfied in " + name
                + ". Score " + scoreSum + "/" + maxScore + "</p>";
        return new JudgeResult(maxScore, scoreSum, satisfiedSignals, resultString);
    }

    public void setScores(int[] scores) {
        this.scores = scores;
    }

    /** Legacy {@code setScore(score, position)}: ignored if position is out of range. */
    public void setScore(int score, int position) {
        if (position >= scores.length || position < 0) return;
        scores[position] = score;
    }

    public int getCutoff() {
        return cutoff;
    }

    protected void setCutoff(int cutoff) {
        this.cutoff = cutoff;
    }

    public int getNumberOf2DSignals() {
        return numberOf2DSignals;
    }

    /** Copies the 2D fields (deep) into {@code t}; helper for subclass {@link #copy()}. */
    protected final void copyTwoDFieldsTo(TwoDSpectrumJudge t) {
        copyBaseFieldsTo(t);
        if (assignment != null) {
            t.assignment = new boolean[assignment.length][][];
            for (int f = 0; f < assignment.length; f++) {
                t.assignment[f] = new boolean[assignment[f].length][];
                for (int g = 0; g < assignment[f].length; g++) t.assignment[f][g] = assignment[f][g].clone();
            }
        }
        t.numberOf2DSignals = numberOf2DSignals;
        t.cutoff = cutoff;
        t.scores = scores.clone();
        t.rules = new ArrayList<>(rules);
    }

    /** Legacy {@code toString()} = the "Report Judgeconfig" text (F-81). */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (name == null || assignment == null) return "";
        sb.append("Configuration of TwoDSpectrumJudge ").append(name).append(":\n");
        sb.append("Listing relation of nuclei by number...\n");
        for (int f = 0; f < assignment.length; f++)
            for (int g = 0; g < assignment.length; g++)
                for (int h = 0; h < assignment.length; h++)
                    if (assignment[f][g][h]) sb.append(f).append('-').append(h).append('\n');
        sb.append("End of listing");
        return sb.toString();
    }
}
