package io.github.steinbeck.seneca.config;

import java.util.List;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.judges.HMBCJudge;
import io.github.steinbeck.seneca.model.NMRSignal;
import io.github.steinbeck.seneca.model.NMRSpectrum;
import io.github.steinbeck.seneca.model.SenecaDataset;
import io.github.steinbeck.seneca.model.SpectrumSlot;

/**
 * Autoconfigure of the HMBCJudge: CH long-range signals -&gt; {@code assignment[from][to][alt]}. Port of
 * {@code legacy/original/src/seneca/client/HMBCAssigner.java}; architecture.md 5.8.
 *
 * <ul>
 *   <li>Returns false (nothing changed) unless every C atom has an assigned shift.</li>
 *   <li>Per CH long-range signal, in order: "from" = the carbons closest to its 13C shift (0.05 ppm,
 *       {@link #pickClosestCarbons}); its 1H shift is looked up in the 1JCH spectrum (closest signal
 *       within 0.01 ppm) and that signal's 13C shift gives "to". For each "from":
 *       {@code assignment[from][to][to] = true} and {@code [from][to][toAlt]} for the further "to"
 *       candidates. The matrix is allocated on first use and never cleared (a second Autoconfigure
 *       adds to the old correlations).</li>
 *   <li><b>Switch point</b> (DV-46). STRICT: no exception handling (as legacy): a long-range proton
 *       without 1JCH partner throws {@link NullPointerException}, an empty "to" list
 *       {@link ArrayIndexOutOfBoundsException} ({@code Vector.elementAt(0)}); the correlations assigned
 *       so far stay. The GUI recipe records the exception and leaves the judge enabled. DEFAULT: the
 *       assignment is built on a copy of the matrix; if any signal fails, {@code assign()} returns
 *       false and the judge's matrix is unchanged (the configurator then disables the judge, as for a
 *       failed H,H-COSY autoconfigure). Inputs on which legacy returns normally give identical
 *       results in both modes.</li>
 * </ul>
 * The judge is bound at construction ({@code sd.getJudge("HMBCJudge")} in legacy; the configurator
 * builds its assigner when the dataset window opens). Legacy {@code HMBCAssigner} shadows the base
 * class's dataset field, so {@link #setSenecaDataset} does not redirect it; the port keeps that.
 */
public final class HMBCAssigner extends SpectrumAssigner {

    public static final float CARBON_PRECISION = 0.05f, PROTON_PRECISION = 0.01f;

    private final SenecaDataset ds;
    private final HMBCJudge hmbcJudge;
    private final LegacyCompat compat;

    public HMBCAssigner(SenecaDataset sd, LegacyCompat compat) {
        this(sd, sd.getJudge(HMBCJudge.class), compat);
    }

    /** Assigner writing into {@code judge} (the configurator's judge instance). */
    public HMBCAssigner(SenecaDataset sd, HMBCJudge judge, LegacyCompat compat) {
        super(sd);
        this.ds = sd;
        this.hmbcJudge = judge == null ? new HMBCJudge() : judge;
        this.compat = compat;
    }

    public HMBCJudge judge() {
        return hmbcJudge;
    }

    @Override
    public boolean assign() {
        HeavyAtom[] nodes = ds.setOfNodes;
        // DV-46: a dataset without atoms ("New Dataset" before a formula is set); legacy NullPointerException
        if (nodes == null && !compat.strict()) return false;
        for (HeavyAtom a : nodes) {
            if (a.symbol.equals("C") && a.shift13C == null) return false;
        }
        NMRSpectrum hetcorlr = ds.spectrum(SpectrumSlot.HETCORLR);
        NMRSpectrum hetcor = ds.spectrum(SpectrumSlot.HETCOR);
        int n = nodes.length;
        if (!compat.strict()) {
            boolean[][][] before = hmbcJudge.assignment;
            hmbcJudge.assignment = copyAssignment(before);
            try {
                correlate(nodes, hetcorlr, hetcor, n);
            } catch (RuntimeException e) {
                hmbcJudge.assignment = before;      // nothing changed
                return false;
            }
            return true;
        }
        correlate(nodes, hetcorlr, hetcor, n);
        return true;
    }

    /** The legacy loop over the long-range signals (no exception handling). */
    private void correlate(HeavyAtom[] nodes, NMRSpectrum hetcorlr, NMRSpectrum hetcor, int n) {
        for (int i = 0; i < hetcorlr.size(); i++) {
            NMRSignal lr = hetcorlr.getSignal(i);
            float carbonshift = lr.getShift(NMRSpectrum.NUC_CARBON);
            float protonshift = lr.getShift(NMRSpectrum.NUC_PROTON);
            List<HeavyAtom> fromCarbonAtoms = pickClosestCarbons(nodes, carbonshift, CARBON_PRECISION);
            NMRSignal hetcorSignal = hetcor.pickClosestSignal(protonshift, NMRSpectrum.NUC_PROTON, PROTON_PRECISION);
            carbonshift = hetcorSignal.getShift(NMRSpectrum.NUC_CARBON);
            List<HeavyAtom> toCarbonAtoms = pickClosestCarbons(nodes, carbonshift, CARBON_PRECISION);
            for (int j = 0; j < fromCarbonAtoms.size(); j++) {
                int from = fromCarbonAtoms.get(j).number;
                int to = elementAt(toCarbonAtoms, 0).number;
                if (hmbcJudge.assignment == null || hmbcJudge.assignment.length == 0) {
                    hmbcJudge.assignment = new boolean[n][n][n];
                }
                hmbcJudge.assignment[from][to][to] = true;
                for (int k = 1; k < toCarbonAtoms.size(); k++) {
                    int toAlt = toCarbonAtoms.get(k).number;
                    hmbcJudge.assignment[from][to][toAlt] = true;
                }
            }
        }
    }

    /**
     * Legacy {@code pickClosestCarbons}: the C atom whose shift is closest to {@code shift} (first on
     * ties, float arithmetic) if closer than {@code precision}; then, for every <em>other</em> C atom
     * with exactly the same shift, the closest atom is added <b>again</b> (legacy defect: it adds
     * {@code setOfNodes[thisPosition]}, not the equal-shift atom). Requires every C atom to have a shift.
     */
    public static List<HeavyAtom> pickClosestCarbons(HeavyAtom[] nodes, float shift, float precision) {
        int thisPosition = -1;
        float diff = Float.MAX_VALUE, referenceShift = 0;
        List<HeavyAtom> carbons = new java.util.ArrayList<>();
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i].symbol.equals("C")) {
                float compareShift = nodes[i].shift13C;
                if (diff > Math.abs(compareShift - shift)) {
                    diff = Math.abs(compareShift - shift);
                    referenceShift = compareShift;
                    thisPosition = i;
                }
            }
        }
        if (diff < precision) carbons.add(nodes[thisPosition]);
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i].symbol.equals("C")) {
                float compareShift = nodes[i].shift13C;
                if (compareShift == referenceShift && i != thisPosition) carbons.add(nodes[thisPosition]);
            }
        }
        return carbons;
    }

    /** {@code Vector.elementAt}: {@link ArrayIndexOutOfBoundsException} outside the list. */
    static <T> T elementAt(List<T> l, int i) {
        if (i >= l.size()) throw new ArrayIndexOutOfBoundsException(i + " >= " + l.size());
        if (i < 0) throw new ArrayIndexOutOfBoundsException("Index " + i + " out of bounds for length " + l.size());
        return l.get(i);
    }
}
