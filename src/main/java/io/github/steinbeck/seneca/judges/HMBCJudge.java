package io.github.steinbeck.seneca.judges;

/**
 * CH long-range correlation judge. Port of {@code legacy/original/src/seneca/judges/HMBCJudge.java}:
 * name "HMBCJudge", scores {100, 100, 5, 0, 0, 0, 0} (cutoff 3). Class name kept from legacy because
 * golden echo records contain {@code getClass().getSimpleName()}.
 */
public class HMBCJudge extends TwoDSpectrumJudge {

    public HMBCJudge() {
        super("HMBCJudge");
        setScore(100, 0);
        setScore(100, 1);
        setScore(5, 2);
    }

    @Override
    public Judge copy() {
        HMBCJudge c = new HMBCJudge();
        copyTwoDFieldsTo(c);
        return c;
    }
}
