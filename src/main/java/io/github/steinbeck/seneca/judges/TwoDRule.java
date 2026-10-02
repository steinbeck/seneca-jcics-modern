package io.github.steinbeck.seneca.judges;

/**
 * One 2D correlation rule (atom pair) of a {@link TwoDSpectrumJudge}. Port of
 * {@code legacy/original/src/seneca/judges/TwoDRule.java}.
 */
public final class TwoDRule {
    public final int atom1;
    public final int atom2;

    public TwoDRule(int atom1, int atom2) {
        this.atom1 = atom1;
        this.atom2 = atom2;
    }
}
