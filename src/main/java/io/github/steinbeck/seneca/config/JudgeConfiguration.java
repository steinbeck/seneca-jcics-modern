package io.github.steinbeck.seneca.config;

import java.util.List;
import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.judges.FeatureJudge;
import io.github.steinbeck.seneca.judges.HHCOSYJudge;
import io.github.steinbeck.seneca.judges.HMBCJudge;
import io.github.steinbeck.seneca.judges.HOSECodeJudge;
import io.github.steinbeck.seneca.judges.HeteroBondsJudge;
import io.github.steinbeck.seneca.judges.HybridizationJudge;
import io.github.steinbeck.seneca.judges.Judge;
import io.github.steinbeck.seneca.judges.SymmetryJudge;
import io.github.steinbeck.seneca.judges.TwoDSpectrumJudge;
import io.github.steinbeck.seneca.model.SenecaDataset;

/**
 * Non-Swing core of the seven judge configurator panels (policy point 5): everything the GUI's
 * "Judges" tab does to the judges, so that the GUI only calls these methods.
 *
 * <p>References ({@code legacy/original/src/seneca/client/}): {@code JudgeConfigurator} (AutoAssignAction:
 * {@code setEnabled(true)} then {@code autoconfigure()}; EnableAction), {@code TwoDSpectrumJudgeConfigurator}
 * (assigner, disable on failure, "Report Judgeconfig" = {@code judge.toString()}),
 * {@code HOSECodeJudgeConfigurator} (compact list of assigned shifts as double), {@code SymmetryJudgeConfigurator}
 * (classes from exactly equal float shifts, with the "inherits the previous atom's shift" quirk),
 * {@code FeatureJudgeConfigurator} (ring 3 -&gt; -500, ring 4 -&gt; -300, het-het on),
 * {@code HybridizationJudgeConfigurator} / {@code HeteroBondsJudgeConfigurator} (constructors set
 * {@code judge.getAttic().atomSet = sd.setOfNodes} and call {@code judge.init()}; table models'
 * {@code setValueAt}; inherited no-op autoconfigure prints "dummy").
 *
 * <p>The judge a configurator works on is {@code sd.getJudge(<class name>)} when the dataset window
 * opens (the first instance of that class in {@code sd.judges}; a new unregistered instance if there
 * is none). The methods that take a judge act on that judge; the table methods look it up the same way.
 * Console chatter of the legacy panels is dropped (DEVIATIONS.md DV-28).
 */
public final class JudgeConfiguration {

    /** Ring size scores set by the FeatureJudge autoconfigure. */
    public static final int RING3_SCORE = -500, RING4_SCORE = -300;

    /** Combo box values of the HybridizationJudge table (sp3/sp2/sp columns), top to bottom. */
    public static final List<String> HYBRIDIZATION_VALUES = List.of("1.0", "0.9", "0.8", "0.7", "0.6", "0.5", "0.4", "0.3", "0.2", "0.1", "0.0");

    /** Table columns of the HybridizationJudge panel. */
    public static final int HYB_COLUMN_SP3 = 3, HYB_COLUMN_SP2 = 4, HYB_COLUMN_SP = 5;
    /** Table column "hetero-attached?" of the HeteroBondsJudge panel. */
    public static final int HETERO_COLUMN = 3;

    private JudgeConfiguration() {
    }

    /**
     * Side effects of opening the dataset window: the 7 configurators are constructed in
     * {@code SenecaDatasetFrame} tab order (HHCOSY, HMBC, HOSECode, Symmetry, Feature, Hybridization,
     * HeteroBonds); only the Hybridization and HeteroBonds constructors change data: they set the
     * judge's attic atom set to {@code sd.setOfNodes} and call {@code init()} (allocating empty tables).
     */
    public static void datasetWindowOpened(SenecaDataset sd) {
        HybridizationJudge hy = sd.getJudge(HybridizationJudge.class);
        hy.getAttic().atomSet = sd.getSetOfNodes();
        hy.init();
        HeteroBondsJudge hb = sd.getJudge(HeteroBondsJudge.class);
        hb.getAttic().atomSet = sd.getSetOfNodes();
        hb.init();
    }

    /**
     * "Autoconfigure Judge" button: {@code judge.setEnabled(true)} then the judge-specific autoconfigure
     * (2D judges: assigner; failure disables the judge). STRICT: exceptions of the HMBC assigner
     * propagate, as on the legacy event thread (the judge then stays enabled with a partial assignment).
     * DEFAULT: the HMBC and H,H-COSY assigners fail without changing anything and the judge is disabled
     * (DV-46; STRICT H,H-COSY keeps the correlations set before the failure, as legacy).
     */
    public static void autoconfigure(SenecaDataset sd, Judge judge, LegacyCompat compat) {
        judge.setEnabled(true);
        switch (judge) {
            case HHCOSYJudge h -> autoconfigureTwoD(h, new HHCOSYAssigner(sd, h, compat));
            case HMBCJudge h -> autoconfigureTwoD(h, new HMBCAssigner(sd, h, compat));
            case HOSECodeJudge h -> autoconfigureHoseCode(sd, h);
            case SymmetryJudge s -> autoconfigureSymmetry(sd, s);
            case FeatureJudge f -> autoconfigureFeature(f);
            default -> {
                // HybridizationJudge, HeteroBondsJudge: inherited no-op (legacy prints "dummy")
            }
        }
    }

    /** {@code TwoDSpectrumJudgeConfigurator.autoconfigure}. */
    private static void autoconfigureTwoD(TwoDSpectrumJudge judge, SpectrumAssigner sa) {
        if (!sa.assign()) {
            judge.setEnabled(false);
            return;
        }
        judge.setEnabled(true);
    }

    /** {@code HOSECodeJudgeConfigurator.autoconfigure}: the assigned shifts in atom order, as double. */
    private static void autoconfigureHoseCode(SenecaDataset sd, HOSECodeJudge judge) {
        HeavyAtom[] nodes = sd.setOfNodes;
        if (nodes == null) return;
        double[] temp = new double[nodes.length];
        int ccount = 0;
        for (HeavyAtom a : nodes) {
            if (a.shift13C != null) temp[ccount++] = (double) a.shift13C.floatValue();
        }
        double[] shifts = new double[ccount];
        System.arraycopy(temp, 0, shifts, 0, ccount);
        judge.setCarbonShifts(shifts);
    }

    /**
     * {@code SymmetryJudgeConfigurator.autoconfigure}: clears the classes; for every atom f, the atoms
     * g &gt; f whose shift equals f's shift exactly form a class together with f. An atom without a shift
     * keeps the shift of the last atom before it that had one (legacy quirk), and atoms already in a class
     * start their own (overlapping) class.
     */
    private static void autoconfigureSymmetry(SenecaDataset sd, SymmetryJudge judge) {
        HeavyAtom[] nodes = sd.setOfNodes;
        if (nodes == null) return;
        judge.clearSymmetryClasses();
        float shift1 = 0;
        boolean symClassExists = false;
        boolean[] symClass = new boolean[nodes.length];
        for (int f = 0; f < nodes.length; f++) {
            if (nodes[f].shift13C != null) shift1 = nodes[f].shift13C;
            for (int g = f + 1; g < nodes.length; g++) {
                if (nodes[g].shift13C != null) {
                    float shift2 = nodes[g].shift13C;
                    if (shift1 == shift2) {
                        if (!symClassExists) {
                            symClassExists = true;
                            symClass = new boolean[nodes.length];
                            symClass[f] = true;
                        }
                        symClass[g] = true;
                    }
                }
            }
            if (symClassExists) judge.addSymmetryClass(symClass);
            symClassExists = false;
        }
    }

    /** {@code FeatureJudgeConfigurator.autoconfigure}. */
    private static void autoconfigureFeature(FeatureJudge judge) {
        judge.addRingSizeScore(3, RING3_SCORE);
        judge.addRingSizeScore(4, RING4_SCORE);
        judge.setHetHetPenaltyEnabled(true);
    }

    /** Checkbox "Activate this Judge?" ({@code EnableAction}). */
    public static void setActivated(Judge judge, boolean selected) {
        judge.setEnabled(selected);
    }

    /** FeatureJudge checkbox "Hetero-Hetero Atom Bonds forbidden" ({@code CheckBoxListener}). */
    public static void setHetHetBondsForbidden(SenecaDataset sd, boolean selected) {
        sd.getJudge(FeatureJudge.class).setHetHetPenaltyEnabled(selected);
    }

    /**
     * HybridizationJudge table cell edit ({@code HybridizationTableModel.setValueAt}): {@code column}
     * 3 = sp3, 4 = sp2, 5 = sp; {@code comboValue} "1.0" ... "0.0" (stored as
     * {@code (int)(value * multiplicator)}); other columns are not editable (no effect).
     *
     * <p>Switch point (DV-66). The table is allocated by {@link #datasetWindowOpened} for the atoms of that
     * moment. STRICT (legacy): a table that was never allocated throws {@link NullPointerException}, a
     * row beyond the table (the molecular formula grew after the window opened) throws
     * {@link ArrayIndexOutOfBoundsException}; the edit is lost. DEFAULT: in exactly these two cases the
     * table is first enlarged to cover the row and the dataset's atoms (existing entries kept); every
     * edit legacy accepts gives the same table in both modes.
     */
    public static void setHybridizationCell(SenecaDataset sd, int row, int column, String comboValue, LegacyCompat compat) {
        HybridizationJudge hy = sd.getJudge(HybridizationJudge.class);
        int hybridization = switch (column) {
            case HYB_COLUMN_SP3 -> HybridizationJudge.SP3;
            case HYB_COLUMN_SP2 -> HybridizationJudge.SP2;
            case HYB_COLUMN_SP -> HybridizationJudge.SP;
            default -> -1;      // No., Symb., Shift: not changeable
        };
        if (hybridization < 0) return;
        if (!compat.strict() && row >= 0 && (hy.scores == null || row >= hy.scores.length)) {
            int[][] grown = new int[Math.max(row + 1, atomCount(sd))][];
            for (int f = 0; f < grown.length; f++) {
                grown[f] = hy.scores != null && f < hy.scores.length ? hy.scores[f] : new int[3];
            }
            hy.scores = grown;
        }
        hy.addHybridizationScore(row + 1, hybridization, Double.valueOf(comboValue));
    }

    /**
     * HeteroBondsJudge table cell edit ({@code HeteroBondsTableModel.setValueAt}), column 3 =
     * hetero-attached?. Switch point (DV-66) as {@link #setHybridizationCell}: STRICT throws for a table
     * that was never allocated or a row beyond it; DEFAULT enlarges the table first.
     */
    public static void setHeteroBondsCell(SenecaDataset sd, int row, int column, boolean value, LegacyCompat compat) {
        HeteroBondsJudge hb = sd.getJudge(HeteroBondsJudge.class);
        if (column != HETERO_COLUMN) return;
        if (!compat.strict() && row >= 0 && (hb.isHeteroBonded == null || row >= hb.isHeteroBonded.length)) {
            boolean[] grown = new boolean[Math.max(row + 1, atomCount(sd))];
            if (hb.isHeteroBonded != null) System.arraycopy(hb.isHeteroBonded, 0, grown, 0, hb.isHeteroBonded.length);
            hb.isHeteroBonded = grown;
        }
        hb.isHeteroBonded[row] = value;
    }

    private static int atomCount(SenecaDataset sd) {
        return sd.getSetOfNodes() == null ? 0 : sd.getSetOfNodes().length;
    }

    /** "Report Judgeconfig": 2D judges' {@code toString()}; other judges legacy prints "dummy" (returns null). */
    public static String report(Judge judge) {
        if (judge instanceof TwoDSpectrumJudge t) return t.toString();
        return null;
    }
}
