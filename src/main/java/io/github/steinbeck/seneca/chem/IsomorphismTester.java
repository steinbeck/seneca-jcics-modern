package io.github.steinbeck.seneca.chem;

import java.util.Arrays;

/**
 * compchem {@code IsomorphismTester}: the Morgan-number heuristic SENECA uses as "isomorphism" test
 * (duplicate check {@code StochasticGenerator.notInStock}, {@code StructureGeneratorResult.removeIsomorphism}).
 *
 * <p>Reference: {@code legacy/decompiled/compchem/compchem/IsomorphismTester.java};
 * {@code docs/legacy/compchem-semantics.md} 7.2. It is <b>not</b> an isomorphism test: bond orders
 * enter only via H counts, elements only for atoms with equal Morgan numbers. Ported verbatim: sorted
 * Morgan vectors equal (compared over the base's length, as legacy: a shorter compare structure throws
 * {@link ArrayIndexOutOfBoundsException}), then for every base atom f and every compare atom g with
 * equal Morgan value, symbol and {@code HCount} must match; every base atom needs at least one match.
 * CDK's {@code isomorphism.IsomorphismTester} differs (inverted condition) and must not be used.
 *
 * <p>The base's Morgan numbers are computed in the constructor (legacy {@code setBaseTable}), i.e. from
 * the base's bonds at construction time.
 */
public final class IsomorphismTester {

    private HeavyAtom[] baseNodes;
    private int[] baseTable;
    private int[] sortedBaseTable;

    /** compchem {@code IsomorphismTester(Atom[])}: Morgan numbers of the base are computed here. */
    public IsomorphismTester(HeavyAtom[] base) {
        setBaseTable(base);
    }

    private void setBaseTable(HeavyAtom[] base) {
        this.baseNodes = base;
        this.baseTable = AtomSets.computeMorganMatrix(base);
        this.sortedBaseTable = baseTable.clone();
        Arrays.sort(sortedBaseTable);
    }

    /** compchem {@code isIsomorph(Atom[])}: compare against the base given in the constructor. */
    public boolean isIsomorph(HeavyAtom[] compare) {
        int[] compareTable = AtomSets.computeMorganMatrix(compare);
        int[] sortedCompareTable = compareTable.clone();
        Arrays.sort(sortedCompareTable);
        for (int i = 0; i < sortedBaseTable.length; i++) {
            if (sortedBaseTable[i] != sortedCompareTable[i]) return false;
        }
        for (int f = 0; f < baseTable.length; f++) {
            boolean found = false;
            for (int g = 0; g < compareTable.length; g++) {
                if (baseTable[f] == compareTable[g]) {
                    if (!baseNodes[f].symbol.equals(compare[g].symbol) || baseNodes[f].hCount != compare[g].hCount) {
                        return false;
                    }
                    found = true;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    /** compchem {@code isIsomorph(Atom[], Atom[])}: new base, then compare. */
    public static boolean isIsomorph(HeavyAtom[] base, HeavyAtom[] compare) {
        return new IsomorphismTester(base).isIsomorph(compare);
    }
}
