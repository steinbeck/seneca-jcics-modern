package io.github.steinbeck.seneca.chem;

/**
 * compchem {@code StandardElements}: the only valence model of SENECA.
 *
 * <p>Reference: {@code legacy/decompiled/compchem/compchem/StandardElements.java};
 * {@code docs/legacy/compchem-semantics.md} 3.2. Unknown symbols return -1 (e.g. "B"). CDK's valence
 * models differ (hypervalent S/P, charged N, B) and must not be used for anything that affects scores.
 */
public final class StandardElements {

    private static final String[] SYMBOL = {"H", "C", "Si", "N", "P", "O", "S", "F", "Cl", "Br", "I", "X"};
    private static final int[] MAX_BOND_ORDER = {1, 3, 3, 3, 3, 2, 2, 1, 1, 1, 1, 3};
    private static final int[] MAX_BOND_ORDER_SUM = {1, 4, 4, 3, 3, 2, 2, 1, 1, 1, 1, 10};

    private StandardElements() {
    }

    private static int index(String symbol) {
        for (int i = 0; i < SYMBOL.length; i++) {
            if (SYMBOL[i].equals(symbol)) return i;
        }
        return -1;
    }

    /** @return the maximum bond order of the element, -1 if unknown */
    public static int getMaxBondOrder(String symbol) {
        int i = index(symbol);
        return i < 0 ? -1 : MAX_BOND_ORDER[i];
    }

    /** @return the valence (maximum bond-order sum) of the element, -1 if unknown */
    public static int getMaxBondOrderSum(String symbol) {
        int i = index(symbol);
        return i < 0 ? -1 : MAX_BOND_ORDER_SUM[i];
    }

    /** @return true if the symbol is in the table */
    public static boolean isKnown(String symbol) {
        return index(symbol) >= 0;
    }
}
