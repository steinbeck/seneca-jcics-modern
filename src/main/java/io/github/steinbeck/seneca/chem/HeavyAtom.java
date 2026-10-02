package io.github.steinbeck.seneca.chem;

/**
 * A heavy atom of a SENECA structure: the port of {@code compchem.Atom} as far as SENECA uses it.
 *
 * <p>Reference: {@code legacy/decompiled/compchem/compchem/Atom.java}; semantics in
 * {@code docs/legacy/compchem-semantics.md} section 3. This is the molecule representation of the
 * port (decision and rationale: docs/port/design.md section 3). A structure is a {@code HeavyAtom[]}
 * in <b>dataset order</b> plus, in the generator, a symmetric {@code int[n][n]} bond matrix.
 *
 * <p>Semantics that the legacy algorithms depend on and that must not be changed:
 * <ul>
 *   <li>{@link #number} equals the array index (HOSE codes, PathUtils and the ring finder index
 *       {@code boolean[]}/{@code List[]} by it).</li>
 *   <li>Neighbours live in {@link #nodeTable}{@code [0..degree-1]} with bond orders in
 *       {@link #bondTable}; capacity is fixed at {@value #CAPACITY}. Accessing index -1 or 10 throws
 *       {@link ArrayIndexOutOfBoundsException} exactly like compchem (the ring finder crash of
 *       docs/legacy/compchem-semantics.md 6.3 relies on {@code nodeTable[-1]} with length 10).</li>
 *   <li>Equality is identity (no {@code equals}/{@code hashCode} override).</li>
 *   <li>{@link #copy()} copies the arrays including stale entries beyond {@code degree}, as
 *       {@code Atom.clone()} does; {@link AtomSets#cloneAtomSet} then remaps neighbour references.
 *       Like {@code Atom.clone()}, the copy does <b>not</b> carry the assigned 13C shift
 *       (compchem's physical properties are not cloned).</li>
 * </ul>
 * Fields that compchem has but SENECA never reads ({@code intnodeTable}, {@code inRings},
 * {@code ringCounter}, {@code status}) are omitted.
 */
public final class HeavyAtom {

    /** Fixed neighbour capacity of compchem atoms. */
    public static final int CAPACITY = 10;

    /** compchem {@code Atom.name}: the CML atom id ("a0", ...) or "" for formula atoms. */
    public String name = "";
    /** Element symbol ("C", "O", ..., "X" wildcard). */
    public String symbol = "";
    /** Index of this atom in its atom array (dataset order). */
    public int number;
    /** Formal charge (only set by the MDL reader; never used for scoring). */
    public int charge = 0;
    /** Neighbours, {@code [0..degree-1]} valid. */
    public HeavyAtom[] nodeTable = new HeavyAtom[CAPACITY];
    /** Bond orders to {@link #nodeTable} entries (1-3 in SENECA; 4 aromatic, 5-8 stereo singles from MDL). */
    public int[] bondTable = new int[CAPACITY];
    /** Number of valid neighbour entries. */
    public int degree = 0;
    /** Implicit hydrogen count from the dataset ({@code -1} = undefined). compchem {@code HCount}. */
    public int hCount = 0;
    /** Valence ({@link StandardElements}), compchem {@code maxBondOrderSum}. */
    public int maxBondOrderSum;
    /** Highest bond order ({@link StandardElements}), compchem {@code maxBondOrder}. */
    public int maxBondOrder;
    /** Running bond-order sum maintained by {@link #formBond}/{@link #reset} (start structure only). */
    public int bondOrderSum;
    /** 2D/3D coordinates (MDL I/O and depiction only). */
    public double x, y, z;
    /**
     * Assigned 13C shift, compchem physical property {@code "13C chemical shift"} ({@code Float});
     * {@code null} = not assigned. Not copied by {@link #copy()}.
     */
    public Float shift13C;

    public HeavyAtom() {
        this.bondOrderSum = getBondCount();
    }

    public HeavyAtom(String symbol) {
        this();
        this.symbol = symbol;
    }

    public HeavyAtom(String symbol, int number) {
        this(symbol);
        this.number = number;
    }

    /**
     * compchem {@code Atom.clone()}: copies scalar fields and the neighbour arrays (references still
     * point to the original neighbours; see {@link AtomSets#cloneAtomSet}). The 13C shift is not copied.
     */
    public HeavyAtom copy() {
        HeavyAtom a = new HeavyAtom();
        a.name = name;
        a.symbol = symbol;
        a.number = number;
        a.charge = charge;
        a.degree = degree;
        a.x = x;
        a.y = y;
        a.z = z;
        a.hCount = hCount;
        a.maxBondOrderSum = maxBondOrderSum;
        a.maxBondOrder = maxBondOrder;
        a.bondOrderSum = bondOrderSum;
        System.arraycopy(nodeTable, 0, a.nodeTable, 0, CAPACITY);
        System.arraycopy(bondTable, 0, a.bondTable, 0, CAPACITY);
        return a;
    }

    /**
     * compchem {@code getBondCount()}: float sum of bond orders (aromatic 4 counts 1.5, stereo
     * singles 5-8 count 1), truncated to int.
     */
    public int getBondCount() {
        float sum = 0.0F;
        float o;
        for (int k = 0; k < degree; k++) {
            o = bondTable[k];
            if (o > 3.0F) {
                if (o == 4.0F) o = 1.5F;
                else o = 1.0F;
            }
            sum += o;
        }
        return (int) sum;
    }

    /** compchem {@code isSaturated()}: {@code getBondCount() + HCount == maxBondOrderSum}. */
    public boolean isSaturated() {
        return getBondCount() + hCount == maxBondOrderSum;
    }

    /**
     * compchem {@code saturate()}: fills the valence data from {@link StandardElements} if either is 0,
     * then sets {@code hCount = maxBondOrderSum - getBondCount()}. Used by the MDL/LUCY readers only.
     */
    public void saturate() {
        if (maxBondOrder == 0 || maxBondOrderSum == 0) {
            maxBondOrderSum = StandardElements.getMaxBondOrderSum(symbol);
            maxBondOrder = StandardElements.getMaxBondOrder(symbol);
        }
        hCount = maxBondOrderSum - getBondCount();
    }

    /** compchem {@code isBondedTo(Atom)} (identity). */
    public boolean isBondedTo(HeavyAtom other) {
        for (int k = 0; k < degree; k++) {
            if (nodeTable[k] == other) return true;
        }
        return false;
    }

    /** compchem {@code getBondOrder(Atom)}: order to {@code other}, 0 if not bonded. */
    public int getBondOrder(HeavyAtom other) {
        for (int k = 0; k < degree; k++) {
            if (nodeTable[k] == other) return bondTable[k];
        }
        return 0;
    }

    /** compchem {@code getCurrentMaxBondOrder()}: {@code min(maxBondOrder, maxBondOrderSum - HCount - bondOrderSum)}. */
    public int getCurrentMaxBondOrder() {
        return Math.min(maxBondOrder, maxBondOrderSum - hCount - bondOrderSum);
    }

    /**
     * compchem {@code formBond(Atom, int)}: if {@code other} is already a neighbour, set the order and
     * recompute {@code bondOrderSum = getBondCount()}; otherwise append and add the order.
     */
    public void formBond(HeavyAtom other, int order) {
        for (int k = 0; k < degree; k++) {
            if (nodeTable[k] == other) {
                bondTable[k] = order;
                bondOrderSum = getBondCount();
                return;
            }
        }
        nodeTable[degree] = other;
        bondTable[degree] = order;
        degree++;
        bondOrderSum += order;
    }

    /** compchem {@code removeBond(Atom)}: removes the first entry for {@code other}, shifting left. */
    public void removeBond(HeavyAtom other) {
        for (int k = 0; k < degree; k++) {
            if (nodeTable[k] == other) {
                bondOrderSum = bondOrderSum - bondTable[k];
                for (int m = k; m < degree - 1; m++) {
                    nodeTable[m] = nodeTable[m + 1];
                    bondTable[m] = bondTable[m + 1];
                }
                degree--;
                break;
            }
        }
    }

    /** compchem {@code reset()}: {@code degree = 0; bondOrderSum = 0} (array entries are not cleared). */
    public void reset() {
        degree = 0;
        bondOrderSum = 0;
    }

    @Override
    public String toString() {
        return "HeavyAtom[" + number + " " + symbol + " H" + hCount + " deg " + degree + "]";
    }
}
