package io.github.steinbeck.seneca.chem;

/**
 * The parts of compchem {@code DataStructureTools} that SENECA's score and search path uses.
 *
 * <p>Reference: {@code legacy/decompiled/compchem/compchem/DataStructureTools.java};
 * {@code docs/legacy/compchem-semantics.md} 3.1, 7.1, 11. All methods are verbatim ports.
 * {@link #synchronizeBonds} defines the adjacency order (ascending atom index) that the ring finder,
 * HOSE codes and Morgan numbers see; do not "optimise" it.
 */
public final class AtomSets {

    private AtomSets() {
    }

    /**
     * compchem {@code cloneAtomSet}: {@link HeavyAtom#copy()} of each atom, then neighbour references
     * remapped to the copies by identity search. Order preserved. 13C shifts are not copied.
     */
    public static HeavyAtom[] cloneAtomSet(HeavyAtom[] atoms) {
        HeavyAtom[] c = new HeavyAtom[atoms.length];
        for (int i = 0; i < atoms.length; i++) c[i] = atoms[i].copy();
        for (int i = 0; i < atoms.length; i++) {
            for (int k = 0; k < atoms[i].degree; k++) {
                for (int j = 0; j < atoms.length; j++) {
                    if (atoms[j] == atoms[i].nodeTable[k]) {
                        c[i].nodeTable[k] = c[j];
                        break;
                    }
                }
            }
        }
        return c;
    }

    /**
     * compchem {@code createConnectionTable}: {@code ct[i][index(nodeTable[k])] = bondTable[k]}
     * (not symmetrised; use {@link #synchronizeConnectionTable} afterwards as the legacy code does).
     */
    public static int[][] createConnectionTable(HeavyAtom[] atoms) {
        int[][] ct = new int[atoms.length][atoms.length];
        for (int i = 0; i < atoms.length; i++) {
            for (int k = 0; k < atoms[i].degree; k++) {
                ct[i][indexOf(atoms, atoms[i].nodeTable[k])] = atoms[i].bondTable[k];
            }
        }
        return ct;
    }

    /** compchem {@code synchronizeConnectionTable}: copies the upper triangle into the lower one. */
    public static void synchronizeConnectionTable(int[][] ct) {
        int n = ct.length;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                ct[j][i] = ct[i][j];
            }
        }
    }

    /** compchem {@code cloneInt2D}: deep copy of a square matrix. */
    public static int[][] cloneInt2D(int[][] m) {
        int n = m.length;
        int[][] c = new int[n][n];
        for (int i = 0; i < n; i++) System.arraycopy(m[i], 0, c[i], 0, n);
        return c;
    }

    /**
     * compchem {@code synchronizeBonds}: rebuilds every atom's neighbour list from the symmetric bond
     * matrix, iterating {@code i < j} row-major, so neighbours are in ascending index. Does not touch
     * {@code bondOrderSum}. If the sizes differ, compchem prints an error and does nothing.
     */
    public static void synchronizeBonds(HeavyAtom[] atoms, int[][] bm) {
        if (bm.length != atoms.length) {
            System.err.println("Error in DataStructureTools->synchronizeBonds: incompatible array sizes!");
            return;
        }
        for (HeavyAtom a : atoms) a.degree = 0;
        for (int i = 0; i < atoms.length - 1; i++) {
            for (int j = i + 1; j < atoms.length; j++) {
                if (bm[i][j] > 0) {
                    HeavyAtom ai = atoms[i], aj = atoms[j];
                    ai.nodeTable[ai.degree] = aj;
                    ai.bondTable[ai.degree] = bm[i][j];
                    aj.nodeTable[aj.degree] = ai;
                    aj.bondTable[aj.degree] = bm[i][j];
                    ai.degree++;
                    aj.degree++;
                }
            }
        }
    }

    /**
     * compchem {@code computeMorganMatrix}: start with the heavy-atom degree, then {@code n} times
     * replace every value by the sum over its neighbours (own value excluded). 32-bit {@code int}
     * arithmetic; overflow wraps exactly as in Java 1.1 (and every later JVM).
     */
    public static int[] computeMorganMatrix(HeavyAtom[] atoms) {
        int n = atoms.length;
        int[] next = new int[n];
        int[] cur = new int[n];
        for (int i = 0; i < n; i++) {
            next[i] = atoms[i].degree;
            cur[i] = atoms[i].degree;
        }
        for (int it = 0; it < n; it++) {
            for (int i = 0; i < n; i++) {
                next[i] = 0;
                for (int k = 0; k < atoms[i].degree; k++) {
                    next[i] += cur[indexOf(atoms, atoms[i].nodeTable[k])];
                }
            }
            System.arraycopy(next, 0, cur, 0, n);
        }
        return cur;
    }

    /** compchem {@code faculty}: int factorial; overflows (wraps) for n >= 13 as in the legacy code. */
    public static int faculty(int n) {
        int f = 1;
        for (int i = 2; i <= n; i++) f *= i;
        return f;
    }

    /** compchem {@code getNumber}: index of {@code atom} in {@code atoms} by identity, -1 if absent. */
    public static int indexOf(HeavyAtom[] atoms, HeavyAtom atom) {
        for (int i = 0; i < atoms.length; i++) {
            if (atoms[i] == atom) return i;
        }
        return -1;
    }
}
