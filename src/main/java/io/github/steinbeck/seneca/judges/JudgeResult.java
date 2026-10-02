package io.github.steinbeck.seneca.judges;

/**
 * Result of one judge evaluation. Port of {@code legacy/original/src/seneca/judges/JudgeResult.java}
 * (constructor argument order {@code (maxScore, score, propertyValue, description)} as in legacy).
 */
public final class JudgeResult {
    public long score;
    public long maxScore;
    /** e.g. the "satisfied signals" counter of the 2D judges (quirk Q5: counts every rule). */
    public long propertyValue;
    /** HTML fragment shown in the GUI; part of {@code ScoreSummary.description}. */
    public String scoreDescription;

    public JudgeResult() {
    }

    public JudgeResult(long maxScore, long score, long propertyValue, String scoreDescription) {
        this.maxScore = maxScore;
        this.score = score;
        this.propertyValue = propertyValue;
        this.scoreDescription = scoreDescription;
    }
}
