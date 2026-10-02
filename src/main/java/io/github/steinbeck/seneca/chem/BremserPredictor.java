package io.github.steinbeck.seneca.chem;

/**
 * compchem {@code BremserOneSphereHOSECodePredictor}: 1-sphere HOSE code -&gt; 13C shift and
 * confidence limit (Bremser, Magn. Reson. Chem. 1985, 23, 271).
 *
 * <p>Reference: {@code legacy/decompiled/compchem/compchem/BremserOneSphereHOSECodePredictor.java};
 * {@code docs/legacy/compchem-semantics.md} section 5. The table ({@link BremserTable}) is the verbatim
 * list of 651 puts, 648 distinct keys, the <b>later</b> put wins for the three duplicates
 * ({@code =CC$C(//)}, {@code =CC$(//)}, {@code PP(//)}); the corrupted key {@code C$C§CN(//)} is kept
 * (unreachable). Values are {@code double}, as in compchem. The table is immutable and shared by all
 * instances (legacy: one {@code Hashtable} per instance with identical content).
 * Unknown code: {@link UnknownCodeException} (legacy {@code Exception("No such HOSE code")}), which
 * {@code HOSECodeJudge} swallows.
 */
public final class BremserPredictor {

    /** Legacy {@code Exception("No such HOSE code")}. */
    public static final class UnknownCodeException extends Exception {
        private static final long serialVersionUID = 1L;

        public UnknownCodeException(String code) {
            super("No such HOSE code: " + code);
        }
    }

    private static BremserTable.Row row(String hoseCode) throws UnknownCodeException {
        BremserTable.Row r = BremserTable.get(hoseCode);
        if (r == null) throw new UnknownCodeException(hoseCode);
        return r;
    }

    /** compchem {@code predict(code)}: the shift. */
    public double predict(String hoseCode) throws UnknownCodeException {
        return row(hoseCode).shift();
    }

    /** compchem {@code getConfidenceLimit(code)}. */
    public double getConfidenceLimit(String hoseCode) throws UnknownCodeException {
        return row(hoseCode).confidenceLimit();
    }

    /** compchem {@code predictFull(code)}: the row's {@code toString()} (GUI/diagnostics only). */
    public String predictFull(String hoseCode) throws UnknownCodeException {
        BremserTable.Row r = row(hoseCode);
        return "delta_C: " + r.shift() + " for code " + r.code() + " with confidence limit " + r.confidenceLimit()
                + " in shift range " + r.maxShift() + "-" + r.minShift() + ". ";
    }

    /** Number of distinct codes in the table (648). */
    public static int tableSize() {
        return BremserTable.size();
    }
}
