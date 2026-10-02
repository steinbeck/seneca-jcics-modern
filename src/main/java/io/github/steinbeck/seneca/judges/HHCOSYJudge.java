package io.github.steinbeck.seneca.judges;

/**
 * H,H-COSY judge. Port of {@code legacy/original/src/seneca/judges/HHCOSYJudge.java}: name
 * "HHCOSYJudge", scores {100, 0, ...} (cutoff 1: vicinal carbons bonded).
 */
public class HHCOSYJudge extends TwoDSpectrumJudge {

    public HHCOSYJudge() {
        super("HHCOSYJudge");
        setScore(100, 0);
    }

    @Override
    public Judge copy() {
        HHCOSYJudge c = new HHCOSYJudge();
        copyTwoDFieldsTo(c);
        return c;
    }
}
