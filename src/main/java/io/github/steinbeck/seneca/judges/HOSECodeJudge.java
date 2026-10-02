package io.github.steinbeck.seneca.judges;

import io.github.steinbeck.seneca.chem.BremserPredictor;
import io.github.steinbeck.seneca.chem.HoseCodeGenerator;

/**
 * 13C shift judge via 1-sphere HOSE codes. Port of
 * {@code legacy/original/src/seneca/judges/HOSECodeJudge.java}; architecture.md 5.2.
 *
 * <ul>
 *   <li>{@link #score} = 100 hides {@code Judge.score}; {@code setScore(int)} is a no-op (legacy).</li>
 *   <li>{@link #calcMaxScore()}: {@code carbonShifts.length * score} (NPE if not configured).</li>
 *   <li>{@link #evaluate}: for every atom f with symbol "C": code, predicted shift and confidence limit;
 *       unknown code or any exception (incl. {@code carbonShifts[f]} out of range: the array is
 *       compact but indexed by atom index) contributes nothing and is not counted;
 *       {@code deviation = Math.abs(shift - carbonShifts[f])}; +score if {@code deviation < limit}.
 *       Description {@code "<p>Score: " + scoreSum + "/" + maxScore + ", Carbon shift medium deveation: "
 *       + (mediumDeviation/carbonCount) + "</p>"} (double division; NaN for 0 carbons, as bn-ye1).</li>
 *   <li><b>Switch point</b> ({@code attic.compat}, DEVIATIONS.md DV-50): the HOSE code generator throws a
 *       {@link NullPointerException} for a neighbour symbol without a 2001 major isotope (the wildcard
 *       "X"); legacy calls it outside the {@code try}, so the run ends. STRICT keeps this; DEFAULT treats
 *       such a carbon like one with an unknown code (no score, not counted). DEFAULT catches any
 *       {@link RuntimeException} of the generator, not only this NPE (the X case is the only one known).</li>
 * </ul>
 */
public class HOSECodeJudge extends Judge {

    /** Score for an optimum fit (hides {@code Judge.score}). */
    public int score = 100;
    protected HoseCodeGenerator hcg = new HoseCodeGenerator();
    protected BremserPredictor predictor = new BremserPredictor();
    protected double[] carbonShifts;

    public HOSECodeJudge() {
        super("HOSECodeJudge");
        hasMaxScore = true;
    }

    /** Legacy no-op. */
    public void setScore(int s) {
    }

    public void setCarbonShifts(double[] shifts) {
        this.carbonShifts = shifts;
    }

    public double[] getCarbonShifts() {
        return carbonShifts;
    }

    @Override
    public void init() {
        // legacy: nothing
    }

    @Override
    public void calcMaxScore() {
        maxScore = carbonShifts.length * score;
    }

    @Override
    public JudgeResult evaluate(int[][] bm) {
        scoreSum = 0;
        double shift, confidenceLimit, deviation, mediumDeviation = 0;
        int carbonCount = 0;
        for (int f = 0; f < attic.atomSet.length; f++) {
            if (attic.atomSet[f].symbol.equals("C")) {
                // legacy calls the code generator outside its try, so a failure ends the run: STRICT
                // rethrows; DEFAULT skips this carbon (switch point DV-50)
                String hoseCode;
                try {
                    hoseCode = hcg.getHOSECode(attic.atomSet, attic.atomSet[f], 1);
                } catch (RuntimeException e) {
                    if (attic.compat == null || attic.compat.strict()) throw e;
                    continue; // DEFAULT: like an unknown code - this carbon contributes nothing
                }
                try {
                    shift = predictor.predict(hoseCode);
                    confidenceLimit = predictor.getConfidenceLimit(hoseCode);
                    deviation = Math.abs(shift - carbonShifts[f]);
                    carbonCount++;
                    mediumDeviation += deviation;
                    if (deviation < confidenceLimit) scoreSum += score;
                } catch (Exception exc) {
                    // legacy: unknown code or carbonShifts[f] out of range -> this carbon contributes nothing
                }
            }
        }
        String message = "<p>Score: " + scoreSum + "/" + maxScore + ", Carbon shift medium deveation: "
                + (mediumDeviation / carbonCount) + "</p>";
        return new JudgeResult(maxScore, scoreSum, 0, message);
    }

    @Override
    public Judge copy() {
        HOSECodeJudge c = new HOSECodeJudge();
        copyBaseFieldsTo(c);
        c.score = score;
        c.carbonShifts = carbonShifts == null ? null : carbonShifts.clone();
        return c;
    }
}
