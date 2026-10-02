package io.github.steinbeck.seneca.chem;

import java.util.ArrayList;
import java.util.List;

/**
 * compchem {@code MFAnalyser}: formula parsing (defines the atom order of every dataset created from a
 * formula) and the formula string of an atom set.
 *
 * <p>Reference: {@code legacy/decompiled/compchem/compchem/MFAnalyser.java},
 * {@code DataStructureTools.getMass}; {@code docs/legacy/compchem-semantics.md} section 9. CDK's
 * {@code MolecularFormulaManipulator} differs (Hill order, atom order, masses) and must not be used for
 * these functions.
 *
 * <p>Legacy failure kept: an empty formula makes {@code MFAnalyser.analyseMF} return {@code null}, so
 * {@code getSetOfHeavyNodes()}/{@code getMass()} throw a {@link NullPointerException}; {@link #heavyAtoms}
 * and {@link #mass} do the same (the caller decides).
 */
public final class MolecularFormula {

    private MolecularFormula() {
    }

    /**
     * {@code MFAnalyser.analyseMF(mf)}: all atoms of the formula (including H) in formula order, each a
     * {@code new Atom(symbol)}. Returns null for the empty string (legacy).
     *
     * <p>Parser: an upper-case letter starts a symbol (count reset to "0"), a lower-case letter extends
     * it, a digit extends the count; at the last character or before an upper-case letter
     * {@code max(1, Integer("0" + digits))} atoms of the current symbol are emitted. Any other character
     * is ignored (but still triggers the emission check).
     */
    static HeavyAtom[] allAtoms(String mf) {
        if (mf.length() == 0) return null;
        List<HeavyAtom> atoms = new ArrayList<>();
        String symbol = "";
        String count = "0";
        for (int i = 0; i < mf.length(); i++) {
            char c = mf.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                symbol = String.valueOf(c);
                count = "0";
            }
            if (c >= 'a' && c <= 'z') symbol = symbol + c;
            if (c >= '0' && c <= '9') count = count + c;
            if (i == mf.length() - 1 || (mf.charAt(i + 1) >= 'A' && mf.charAt(i + 1) <= 'Z')) {
                int n = Integer.parseInt(count);
                if (n == 0) n = 1;
                for (int k = 0; k < n; k++) atoms.add(new HeavyAtom(symbol));
            }
        }
        return atoms.toArray(new HeavyAtom[0]);
    }

    /**
     * {@code new MFAnalyser(mf).getSetOfHeavyNodes()}: heavy atoms in formula order (e.g.
     * {@code C15H28O2} -&gt; 15 C then 2 O), hCount 0, {@code number}/valence not yet set (the
     * dataset's {@code initializeNodes} sets them).
     *
     * @throws NullPointerException for the empty formula (legacy)
     */
    public static HeavyAtom[] heavyAtoms(String formula) {
        HeavyAtom[] all = allAtoms(formula);
        if (all == null) throw new NullPointerException("MFAnalyser: empty molecular formula");
        List<HeavyAtom> heavy = new ArrayList<>();
        for (HeavyAtom a : all) {
            if (!a.symbol.equals("H")) heavy.add(a);
        }
        return heavy.toArray(new HeavyAtom[0]);
    }

    /**
     * {@code new MFAnalyser(mf).getAtomCount(symbol)} on a formula string: counts the atoms with that
     * symbol, including "H" (the {@code HCount} shortcut only applies to the atom-set constructor).
     * The empty formula throws {@link NullPointerException} as in legacy.
     */
    public static int atomCount(String formula, String symbol) {
        HeavyAtom[] all = allAtoms(formula);
        if (all == null) throw new NullPointerException("MFAnalyser: empty molecular formula");
        int c = 0;
        for (HeavyAtom a : all) {
            if (a.symbol.equals(symbol)) c++;
        }
        return c;
    }

    /**
     * {@code new MFAnalyser(atoms).getMolecularFormula()} ({@code analyseSetOfNodes}): element symbols in
     * order of first appearance with their counts (count 1 omitted); the total H (sum of positive H
     * counts) is inserted after the first element ({@code "H"} alone for 1).
     */
    public static String formulaOf(HeavyAtom[] atoms) {
        String mf = "";
        int hSum = 0;
        int distinct = 0;
        String[] symbols = new String[atoms.length];
        int[] counts = new int[atoms.length];
        for (HeavyAtom atom : atoms) {
            String s = atom.symbol;
            if (atom.hCount > 0) hSum += atom.hCount;
            boolean found = false;
            for (int g = 0; g < distinct; g++) {
                if (symbols[g].equals(s)) {
                    counts[g]++;
                    found = true;
                    break;
                }
            }
            if (!found) {
                symbols[distinct] = s;
                counts[distinct]++;
                distinct++;
            }
        }
        for (int f = 0; f < distinct; f++) {
            mf = mf + symbols[f];
            if (counts[f] > 1) mf = mf + Integer.toString(counts[f]);
            if (f == 0 && hSum > 0) {
                mf = mf + "H";
                if (hSum > 1) mf = mf + Integer.toString(hSum);
            }
        }
        return mf;
    }

    /**
     * {@code new MFAnalyser(atoms).getAtomCount(symbol)}: for "H" the sum of positive H counts if that
     * is &gt; 0, otherwise the number of atoms with the symbol.
     */
    public static int atomCount(HeavyAtom[] atoms, String symbol) {
        int hSum = 0;
        for (HeavyAtom a : atoms) {
            if (a.hCount > 0) hSum += a.hCount;
        }
        if (symbol.equals("H") && hSum > 0) return hSum;
        int c = 0;
        for (HeavyAtom a : atoms) {
            if (a.symbol.equals(symbol)) c++;
        }
        return c;
    }

    /**
     * {@code DataStructureTools.getMass(mf)} / {@code MFAnalyser.getMass()}: sum of the exact masses of
     * the major isotopes (2001 {@code StandardIsotopes} table) over all atoms of the formula incl. H,
     * accumulated as {@code f = (float) (f + exactMass)}; 0 if an element has no major isotope.
     *
     * @throws NullPointerException for the empty formula (legacy)
     */
    public static float mass(String formula) {
        HeavyAtom[] all = allAtoms(formula);
        if (all == null) throw new NullPointerException("MFAnalyser: empty molecular formula");
        float m = 0.0F;
        for (HeavyAtom a : all) {
            StandardIsotopes.Isotope iso = StandardIsotopes.getMajorIsotope(a.symbol);
            if (iso == null) return 0.0F;
            m = (float) (m + iso.exactMass());
        }
        return m;
    }
}
