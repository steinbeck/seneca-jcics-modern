package io.github.steinbeck.seneca.judges;

/**
 * Hetero-attached carbons judge. Port of
 * {@code legacy/original/src/seneca/judges/HeteroBondsJudge.java}; architecture.md 5.6.
 *
 * <ul>
 *   <li>{@link #score} = 100 and the private {@code maxScore} <b>hide</b> the base fields (kept):
 *       {@link #calcMaxScore()} writes the private one, {@link #getMaxScore()} returns the base one (0),
 *       {@link #evaluate} reports the private one in {@code JudgeResult.maxScore} (quirk Q4).</li>
 *   <li>{@link #init()}: {@code if (attic.atomSet == null) return; isHeteroBonded = new boolean[n]} -
 *       wipes the configuration (D1, both modes).</li>
 *   <li>{@link #evaluate}: per flagged carbon +score if any neighbour is not "C"; description
 *       {@code "<p>k of n carbons assigned to be heterobonded are indeed hetero-bonded in the structure.</p>"}
 *       with n = {@code trueFlagsCount} from calcMaxScore.</li>
 * </ul>
 */
public class HeteroBondsJudge extends Judge {

    /** Hides {@code Judge.score}. */
    public int score = 100;
    public boolean[] isHeteroBonded;
    /** Hides {@code Judge.maxScore} (legacy private field). */
    private int maxScore = 0;
    private int trueFlagsCount = 0;

    public HeteroBondsJudge() {
        super("HeteroBondsJudge");
        hasMaxScore = true;
    }

    public void setScore(int s) {
        score = s;
    }

    /** The hiding private field (golden echo {@code privateMaxScore}). */
    public int getPrivateMaxScore() {
        return maxScore;
    }

    public int getTrueFlagsCount() {
        return trueFlagsCount;
    }

    @Override
    public void init() {
        if (attic.atomSet == null) return;
        isHeteroBonded = new boolean[attic.atomSet.length];
    }

    @Override
    public void calcMaxScore() {
        maxScore = 0;
        trueFlagsCount = 0;
        for (int f = 0; f < isHeteroBonded.length; f++) {
            if (isHeteroBonded[f]) {
                maxScore += score;
                trueFlagsCount++;
            }
        }
    }

    @Override
    public JudgeResult evaluate(int[][] bm) {
        scoreSum = 0;
        int hetAttachedNo = 0;
        for (int f = 0; f < isHeteroBonded.length; f++) {
            if (isHeteroBonded[f] && attic.atomSet[f].symbol.equals("C")) {
                for (int h = 0; h < attic.atomSet[f].degree; h++) {
                    if (!attic.atomSet[f].nodeTable[h].symbol.equals("C")) {
                        scoreSum += score;
                        hetAttachedNo++;
                        break;
                    }
                }
            }
        }
        String message = "<p>" + hetAttachedNo + " of " + trueFlagsCount
                + " carbons assigned to be heterobonded are indeed hetero-bonded in the structure.</p>";
        // the private (hiding) maxScore goes into the result, quirk Q4
        return new JudgeResult(maxScore, scoreSum, 0, message);
    }

    @Override
    public Judge copy() {
        HeteroBondsJudge c = new HeteroBondsJudge();
        copyBaseFieldsTo(c);
        c.score = score;
        c.isHeteroBonded = isHeteroBonded == null ? null : isHeteroBonded.clone();
        c.maxScore = maxScore;
        c.trueFlagsCount = trueFlagsCount;
        return c;
    }
}
