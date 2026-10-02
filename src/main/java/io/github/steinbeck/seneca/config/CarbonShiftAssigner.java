package io.github.steinbeck.seneca.config;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.chem.MolecularFormula;
import io.github.steinbeck.seneca.model.NMRSignal;
import io.github.steinbeck.seneca.model.NMRSpectrum;
import io.github.steinbeck.seneca.model.SenecaDataset;
import io.github.steinbeck.seneca.model.SpectrumSlot;

/**
 * "Atom Properties -&gt; Autoassignment": 13C shifts and H counts from 1D/DEPT spectra. Port of
 * {@code legacy/original/src/seneca/client/CarbonShiftAssigner.java}; docs/legacy/architecture.md 5.8.
 *
 * <p>Reproduced as is (score-determining configuration, both modes): normalize factors
 * {@code 1/intensity}, then the largest signal's factor doubled until the rounded intensity sum
 * reaches the number of C atoms (rewrites {@code carbon1D}, golden echo stage configured_gui); the
 * one-to-one assignment only if the rounded sum equals the C count; the {@code f += g} walk that skips
 * atoms for multiplicities &gt;= 3; the four independent DEPT tests (a CH2 with a DEPT-90 signal in
 * range gets H 2 and then H 1 and counts 3 H, D12/D16); H 0 for all non-C atoms only if the counted H
 * equal the formula's H. Float arithmetic as in Java 1.3 (all {@code float}). Always returns true.
 *
 * <p>Switch points (DEVIATIONS.md DV-42) for the inputs on which the legacy method fails:
 * <ul>
 *   <li>a 1D carbon signal with intensity 0 (e.g. after "Add Signal") or NaN makes the normalization
 *       loop run forever (NaN sum). STRICT: detected after the normalize factors were written, reported
 *       as {@link IllegalStateException}. DEFAULT: checked before anything is changed; returns false.</li>
 *   <li>no positive 1D intensity (empty spectrum) while the dataset has C atoms: legacy
 *       {@link NullPointerException}. STRICT: the same. DEFAULT: returns false, nothing changed.</li>
 *   <li>the walk runs past the 1D signals or past the atoms (rounding, multiplicities &gt;= 3):
 *       legacy {@link ArrayIndexOutOfBoundsException} with a partial assignment. STRICT: the same.
 *       DEFAULT: the walk stops there, the H balance check still runs.</li>
 * </ul>
 * In every case in which the legacy method returns normally, both modes give identical results.
 */
public final class CarbonShiftAssigner extends SpectrumAssigner {

    /** Legacy picking window for DEPT signals (ppm). */
    public static final float DEPT_PRECISION = 0.05f;

    private final LegacyCompat compat;

    public CarbonShiftAssigner(SenecaDataset sd, LegacyCompat compat) {
        super(sd);
        this.compat = compat;
    }

    @Override
    public boolean assign() {
        HeavyAtom[] nodes = sd.setOfNodes;
        NMRSpectrum carbon1D = sd.spectrum(SpectrumSlot.CARBON1D);
        NMRSpectrum dept90 = sd.spectrum(SpectrumSlot.DEPT90);
        NMRSpectrum dept135 = sd.spectrum(SpectrumSlot.DEPT135);
        // legacy: new MFAnalyser(sd.setOfNodes) -> NPE without atoms
        int carbonCount = MolecularFormula.atomCount(nodes, "C");
        if (!compat.strict() && !normalizable(carbon1D, carbonCount)) return false;

        normalize(carbon1D, carbonCount);
        float intensitySum = sumIntensity(carbon1D);
        int hCounter = 0;
        if (carbonCount == Math.round(intensitySum)) {
            int tempCounter = 0;
            walk:
            for (int f = 0; f < nodes.length; f++) {
                if (!nodes[f].symbol.equals("C")) continue;
                if (!compat.strict() && tempCounter >= carbon1D.size()) break;
                NMRSignal cnmrsig = carbon1D.getSignal(tempCounter);
                for (int g = 0; g < Math.round(cnmrsig.intensity * cnmrsig.normalizeFactor); g++) {
                    f += g;
                    if (!compat.strict() && f >= nodes.length) break walk;
                    float cshift = cnmrsig.getShift(NMRSpectrum.NUC_CARBON);
                    NMRSignal d90sig = dept90.pickClosestSignal(cshift, NMRSpectrum.NUC_CARBON, DEPT_PRECISION);
                    NMRSignal d135sig = dept135.pickClosestSignal(cshift, NMRSpectrum.NUC_CARBON, DEPT_PRECISION);
                    nodes[f].shift13C = cnmrsig.getShift(NMRSpectrum.NUC_CARBON);
                    if (d135sig != null && d135sig.phase == NMRSignal.PHASE_POSITIVE && d90sig == null) {
                        nodes[f].hCount = 3; // methyl
                        hCounter += 3;
                    }
                    if (d135sig != null && d135sig.phase == NMRSignal.PHASE_NEGATIVE) {
                        // legacy prints a warning if a DEPT-90 signal is in range
                        nodes[f].hCount = 2; // methylene
                        hCounter += 2;
                    }
                    if (d90sig != null && d90sig.phase == NMRSignal.PHASE_POSITIVE) {
                        nodes[f].hCount = 1; // methine
                        hCounter += 1;
                    }
                    if (d135sig == null && d90sig == null) {
                        nodes[f].hCount = 0; // quaternary
                    }
                }
                tempCounter++;
            }
        }
        // all H assigned -> every non-carbon gets H 0; otherwise "the user has the problem"
        if (MolecularFormula.atomCount(sd.getMolecularFormula(), "H") == hCounter) {
            for (HeavyAtom a : nodes) {
                if (!a.symbol.equals("C")) a.hCount = 0;
            }
        }
        return true;
    }

    /**
     * DEFAULT pre-check: false exactly where the legacy normalization hangs or throws. The products
     * {@code intensity * (1/intensity)} are about 1, or NaN (intensity 0, NaN, infinite), or +Infinity
     * (subnormal intensity); doubling the largest positive signal never produces NaN. So legacy fails
     * iff the loop is entered (rounded sum &lt; C count) and the sum is NaN (hang) or no signal has a
     * positive intensity (NullPointerException).
     */
    private static boolean normalizable(NMRSpectrum nmr, int carbonCount) {
        float sum = sumAfterReset(nmr);
        if (Math.round(sum) >= carbonCount) return true;
        boolean positive = false;
        for (NMRSignal s : nmr.signals) if (s.intensity > 0f) positive = true;
        return positive && !Float.isNaN(sum);
    }

    /** The intensity sum right after the factors are reset to 1/intensity (without writing them). */
    private static float sumAfterReset(NMRSpectrum nmr) {
        float sum = 0;
        for (NMRSignal s : nmr.signals) sum += s.intensity * (1 / s.intensity);
        return sum;
    }

    private void normalize(NMRSpectrum nmr, int carbonCount) {
        for (NMRSignal s : nmr.signals) s.normalizeFactor = 1 / s.intensity;
        while (Math.round(sumIntensity(nmr)) < carbonCount) {
            NMRSignal largest = getLargestSignal(nmr);
            if (largest == null) throw new NullPointerException("CarbonShiftAssigner: no 1D carbon signal with positive intensity");
            if (Float.isNaN(sumIntensity(nmr))) {
                // legacy: Math.round(NaN) == 0 and doubling never changes that: the loop never ends
                throw new IllegalStateException("CarbonShiftAssigner: the 1D carbon intensity sum is NaN "
                        + "(a signal with intensity 0, NaN or infinite); the 2001 program loops forever here");
            }
            largest.normalizeFactor *= 2;
        }
    }

    private static float sumIntensity(NMRSpectrum nmr) {
        float sum = 0;
        for (NMRSignal s : nmr.signals) sum += s.intensity * s.normalizeFactor;
        return sum;
    }

    /** First signal with the largest raw intensity &gt; 0, or null. */
    private static NMRSignal getLargestSignal(NMRSpectrum nmr) {
        float maxInt = 0;
        NMRSignal r = null;
        for (NMRSignal s : nmr.signals) {
            if (s.intensity > maxInt) {
                r = s;
                maxInt = s.intensity;
            }
        }
        return r;
    }
}
