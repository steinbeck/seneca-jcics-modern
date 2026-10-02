package io.github.steinbeck.seneca.config;

import java.util.List;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.judges.HHCOSYJudge;
import io.github.steinbeck.seneca.model.NMRSignal;
import io.github.steinbeck.seneca.model.NMRSpectrum;
import io.github.steinbeck.seneca.model.SenecaDataset;
import io.github.steinbeck.seneca.model.SpectrumSlot;

/**
 * Autoconfigure of the HHCOSYJudge. Port of {@code legacy/original/src/seneca/client/HHCOSYAssigner.java};
 * architecture.md 5.8.
 *
 * <ul>
 *   <li>Returns false (nothing changed) unless every C atom has an assigned shift.</li>
 *   <li>Per H,H-COSY signal, in order: each proton shift (axis 0 and 1) is looked up in the 1JCH
 *       spectrum (closest within 0.01 ppm); "to" = carbons of proton 1, "from" = carbons of proton 2
 *       ({@link HMBCAssigner#pickClosestCarbons}, 0.05 ppm). {@code assignment[from][to][to] = true};
 *       the alternative index uses {@code toCarbonAtoms.elementAt(j)} with the <em>from</em> loop
 *       index {@code j} instead of {@code k} (legacy defect, kept).</li>
 *   <li>Any exception (no 1JCH partner, empty candidate list, index errors) is caught and the method
 *       returns false. STRICT (legacy): correlations set before the exception stay, so re-ticking
 *       "Activate this Judge?" runs with a partial matrix. DEFAULT: the assignment is built on a copy
 *       and the old matrix is restored on failure, as for the HMBC assigner (DV-46). The matrix is never
 *       cleared.</li>
 * </ul>
 * The judge is bound at construction ({@code sd.getJudge("HHCOSYJudge")}); {@link #setSenecaDataset}
 * does not redirect the assigner (legacy field shadowing, kept). The legacy debug print of every atom
 * number is dropped.
 */
public final class HHCOSYAssigner extends SpectrumAssigner {

    private final SenecaDataset ds;
    private final HHCOSYJudge hhcosyJudge;
    private final LegacyCompat compat;

    /** Assigner on the dataset's own judge; DEFAULT keeps the old matrix when a signal fails. */
    public HHCOSYAssigner(SenecaDataset sd, LegacyCompat compat) {
        this(sd, sd.getJudge(HHCOSYJudge.class), compat);
    }

    /** Assigner writing into {@code judge} (the configurator's judge instance); DEFAULT keeps the old matrix when a signal fails. */
    public HHCOSYAssigner(SenecaDataset sd, HHCOSYJudge judge, LegacyCompat compat) {
        super(sd);
        this.ds = sd;
        this.hhcosyJudge = judge == null ? new HHCOSYJudge() : judge;
        this.compat = compat;
    }

    public HHCOSYJudge judge() {
        return hhcosyJudge;
    }

    @Override
    public boolean assign() {
        if (compat.strict()) return correlate();
        boolean[][][] before = hhcosyJudge.assignment;
        hhcosyJudge.assignment = copyAssignment(before);
        boolean ok = correlate();
        if (!ok) hhcosyJudge.assignment = before;       // nothing changed
        return ok;
    }

    /** The legacy method body (exceptions caught, false). */
    private boolean correlate() {
        try {
            HeavyAtom[] nodes = ds.setOfNodes;
            for (HeavyAtom a : nodes) {
                if (a.symbol.equals("C") && a.shift13C == null) return false;
            }
            NMRSpectrum hhcosy = ds.spectrum(SpectrumSlot.HHCOSY);
            NMRSpectrum hetcor = ds.spectrum(SpectrumSlot.HETCOR);
            int n = nodes.length;
            for (int i = 0; i < hhcosy.size(); i++) {
                NMRSignal cosy = hhcosy.getSignal(i);
                float protonshift1 = cosy.getShift(0);
                float protonshift2 = cosy.getShift(1);
                NMRSignal hetcorSignal1 = hetcor.pickClosestSignal(protonshift1, NMRSpectrum.NUC_PROTON, HMBCAssigner.PROTON_PRECISION);
                float carbonshift1 = hetcorSignal1.getShift(NMRSpectrum.NUC_CARBON);
                List<HeavyAtom> toCarbonAtoms = HMBCAssigner.pickClosestCarbons(nodes, carbonshift1, HMBCAssigner.CARBON_PRECISION);
                NMRSignal hetcorSignal2 = hetcor.pickClosestSignal(protonshift2, NMRSpectrum.NUC_PROTON, HMBCAssigner.PROTON_PRECISION);
                float carbonshift2 = hetcorSignal2.getShift(NMRSpectrum.NUC_CARBON);
                List<HeavyAtom> fromCarbonAtoms = HMBCAssigner.pickClosestCarbons(nodes, carbonshift2, HMBCAssigner.CARBON_PRECISION);
                for (int j = 0; j < fromCarbonAtoms.size(); j++) {
                    int from = fromCarbonAtoms.get(j).number;
                    int to = HMBCAssigner.elementAt(toCarbonAtoms, 0).number;
                    if (hhcosyJudge.assignment == null || hhcosyJudge.assignment.length == 0) {
                        hhcosyJudge.assignment = new boolean[n][n][n];
                    }
                    hhcosyJudge.assignment[from][to][to] = true;
                    for (int k = 1; k < toCarbonAtoms.size(); k++) {
                        int toAlt = HMBCAssigner.elementAt(toCarbonAtoms, j).number;
                        hhcosyJudge.assignment[from][to][toAlt] = true;
                    }
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
