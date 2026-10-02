package io.github.steinbeck.seneca.judges;

/**
 * Overall score of a structure. Port of {@code legacy/original/src/seneca/judges/ScoreSummary.java}.
 *
 * <p>{@code description} is the concatenation of the enabled judges' descriptions, each followed by
 * {@code "\n"}. {@code maxScore} stays -1 unless a status poll writes it (legacy {@code getStatus()}
 * mutates the live best summary; the port's status snapshot works on a copy, DEVIATIONS.md).
 */
public final class ScoreSummary {
    public long score = 0;
    public long maxScore = -1;
    public String description = "";

    public ScoreSummary(long score, String description) {
        this.score = score;
        this.description = description;
    }

    public ScoreSummary copy() {
        ScoreSummary s = new ScoreSummary(score, description);
        s.maxScore = maxScore;
        return s;
    }

    /** Legacy text: {@code description + "Overall Score:" + score [+ "/" + maxScore if maxScore > 0]}. */
    @Override
    public String toString() {
        String s = description + "Overall Score:" + score;
        if (maxScore > 0) s += "/" + maxScore;
        return s;
    }
}
