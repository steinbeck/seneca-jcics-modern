package io.github.steinbeck.seneca.config;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.chem.MolecularFormula;
import io.github.steinbeck.seneca.model.SenecaDataset;

/**
 * Non-Swing core of the "Atom Properties" tab (F-42). Port of the table logic in
 * {@code legacy/original/src/seneca/client/AtomPropertyPanel.java}: the editable "Hydrogens" and
 * "Assigned 13C Shift" columns, the H balance message and the "Autoassignment" button.
 *
 * <p>Legacy {@code updateMessageLabel()} runs whenever the table renders a "Hydrogens" cell or a cell
 * is edited; when the H counts balance it sets every negative (undefined) H count to 0 as a side
 * effect. The GUI must call {@link #balance} where the legacy table would have rendered or been
 * edited. (The headless golden recipe never rendered the table, so this side effect is absent there.)
 */
public final class AtomProperties {

    /** Result of {@link #balance}: formula H, table H (MFAnalyser semantics) and the label text. */
    public record Balance(int formulaH, int tableH) {
        public boolean balanced() {
            return formulaH == tableH;
        }

        /** Legacy label text (red if not balanced, dark green otherwise). */
        public String message() {
            return "HCount in molecular formula: " + formulaH + " - Sum of HCounts in this table: " + tableH;
        }
    }

    private AtomProperties() {
    }

    /** "Autoassignment" button. */
    public static boolean autoassign(SenecaDataset sd, LegacyCompat compat) {
        return new CarbonShiftAssigner(sd, compat).assign();
    }

    /**
     * Column "Hydrogens" edited ({@code setValueAt} column 2): an integer 0..4 is stored, anything else
     * (out of range, not a number) is ignored silently.
     */
    public static void setHydrogens(SenecaDataset sd, int row, String value) {
        try {
            int i = Integer.parseInt(value);
            if (i >= 0 && i <= 4) sd.setOfNodes[row].hCount = i;
        } catch (RuntimeException e) {
            // legacy: catch (Exception exc) {}
        }
    }

    /** Column "Assigned 13C Shift" edited ({@code setValueAt} column 3): {@code new Float(String)}, errors propagate. */
    public static void setAssignedShift(SenecaDataset sd, int row, String value) {
        sd.setOfNodes[row].shift13C = Float.valueOf(value);
    }

    /** Cell text of column "Hydrogens": "undefined" for a negative count. */
    public static String hydrogensText(HeavyAtom atom) {
        return atom.hCount < 0 ? "undefined" : String.valueOf(atom.hCount);
    }

    /** Cell text of column "Assigned 13C Shift": the float, or "-" if none. */
    public static String shiftText(HeavyAtom atom) {
        return atom.shift13C == null ? "-" : String.valueOf(atom.shift13C);
    }

    /**
     * Legacy {@code updateMessageLabel()}: table H = sum of positive H counts (MFAnalyser on the atoms),
     * formula H from the formula; if equal, every negative H count becomes 0.
     */
    public static Balance balance(SenecaDataset sd) {
        int is = MolecularFormula.atomCount(sd.getSetOfNodes(), "H");
        int shouldBe = MolecularFormula.atomCount(sd.getMolecularFormula(), "H");
        if (is == shouldBe) {
            for (HeavyAtom a : sd.getSetOfNodes()) if (a.hCount < 0) a.hCount = 0;
        }
        return new Balance(shouldBe, is);
    }
}
