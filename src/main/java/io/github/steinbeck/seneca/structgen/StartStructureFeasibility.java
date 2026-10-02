package io.github.steinbeck.seneca.structgen;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import io.github.steinbeck.seneca.chem.HeavyAtom;

/**
 * Decides whether {@link SingleRandomStructureGenerator} can ever build a start structure from a set of
 * atoms, i.e. whether the 2001 program would find one or restart for ever (DEVIATIONS.md DV-60). It draws
 * no random number and does not change the atoms.
 *
 * <p>What the generator can build: it walks the atoms in index order, and every unsaturated atom bonds
 * to <em>any</em> unsaturated atom it is not yet bonded to (each such partner has a positive probability:
 * the scan starts at a random index); the bond order is {@code max(1, (int)(r * m))} with
 * {@code m = min(currentMaxBondOrder)} of the two atoms, i.e. any order in {@code 1 .. max(1, m - 1)}.
 * So a pair is bonded at most once, the order {@code m} itself is never drawn (an O, or any atom with
 * {@code currentMaxBondOrder} 2, only ever gets single bonds; a double bond needs both atoms to have
 * {@code maxBondOrder} 3 and at least 3 free valences left at that moment), and the passes repeat until
 * a pass forms no bond. The result is accepted if every atom is saturated and the structure connected.
 *
 * <p>Decision:
 * <ol>
 *   <li>{@link Verdict#POSSIBLE} if the free valences ({@code maxBondOrderSum - hCount}) are all at least
 *       1 and have a connected realisation with single bonds only (a simple graph): Havel-Hakimi test
 *       plus {@code sum >= 2(n - 1)} (a graphical sequence of positive degrees has a connected
 *       realisation exactly then: if a realisation has two components, one of them contains a cycle,
 *       and swapping a cycle edge with an edge of the other component joins them). The generator
 *       builds every such graph with positive probability (every step can pick an unbuilt edge of the
 *       target graph and order 1).</li>
 *   <li>Otherwise an exhaustive search over the generator's choices (partner and order per step, in
 *       the generator's own atom order), with sound pruning (parity, saturated components, capacity)
 *       and memoisation of visited states. It is exact; if it exceeds {@link #DEFAULT_BUDGET} states
 *       the verdict is {@link Verdict#UNKNOWN}.</li>
 * </ol>
 */
public final class StartStructureFeasibility {

    /** The three possible answers. */
    public enum Verdict {
        /** The generator finds a start structure with probability 1 (legacy terminates). */
        POSSIBLE,
        /** No sequence of the generator's choices gives a saturated connected structure (legacy restarts for ever). */
        IMPOSSIBLE,
        /** The search budget was exhausted before a decision (never seen for real data). */
        UNKNOWN
    }

    /** Number of search states after which the exhaustive search gives up. */
    public static final int DEFAULT_BUDGET = 200_000;

    private StartStructureFeasibility() {
    }

    public static Verdict of(HeavyAtom[] atoms) {
        return of(atoms, DEFAULT_BUDGET);
    }

    public static Verdict of(HeavyAtom[] atoms, int budget) {
        int n = atoms.length;
        int[] free = new int[n];
        int[] maxBo = new int[n];
        long sum = 0;
        boolean allPositive = true;
        for (int i = 0; i < n; i++) {
            free[i] = atoms[i].maxBondOrderSum - atoms[i].hCount;
            maxBo[i] = atoms[i].maxBondOrder;
            // an atom with more hydrogens than valence is never saturated (its bond count cannot be negative)
            if (free[i] < 0) return Verdict.IMPOSSIBLE;
            if (free[i] == 0) allPositive = false;
            sum += free[i];
        }
        if (n == 0) return Verdict.POSSIBLE;
        if (allPositive && sum >= 2L * (n - 1) && graphical(free)) return Verdict.POSSIBLE;
        return exhaustive(atoms, budget);
    }

    /**
     * A saturated, connected structure for these atoms as a symmetric bond-order matrix, or null if
     * there is none or none was found within {@link #DEFAULT_BUDGET} search states (verdict
     * {@link Verdict#UNKNOWN}). Deterministic, draws no random number. Used in DEFAULT mode as the start
     * structure when the random start generator has not succeeded after
     * {@link StochasticGenerator#START_RESTART_LIMIT} restarts (DV-64).
     * <ol>
     *   <li>If the free valences have a connected single-bond realisation (step 1 of {@link #of}): a
     *       Havel-Hakimi construction (largest remaining degree first, lowest index on ties), then, while
     *       the graph has several components, a cycle edge {@code (u,v)} of one component and an edge
     *       {@code (x,y)} of another are replaced by {@code (u,x)} and {@code (v,y)} (degrees kept,
     *       one component fewer).</li>
     *   <li>Otherwise the first success of the exhaustive search of step 2 (a structure the 2001
     *       generator can build).</li>
     * </ol>
     */
    public static int[][] witness(HeavyAtom[] atoms) {
        int n = atoms.length;
        int[] free = new int[n];
        int[] maxBo = new int[n];
        long sum = 0;
        boolean allPositive = true;
        for (int i = 0; i < n; i++) {
            free[i] = atoms[i].maxBondOrderSum - atoms[i].hCount;
            maxBo[i] = atoms[i].maxBondOrder;
            if (free[i] < 0) return null;
            if (free[i] == 0) allPositive = false;
            sum += free[i];
        }
        if (n == 0) return new int[0][0];
        if (allPositive && sum >= 2L * (n - 1) && graphical(free)) {
            int[][] g = connectedSimpleRealisation(free);
            if (g != null) return g;
        }
        if (sum % 2 != 0) return null;
        Search s = new Search(free, maxBo, DEFAULT_BUDGET);
        try {
            return s.pass0() ? s.solution : null;
        } catch (BudgetExceeded e) {
            return null;
        }
    }

    /** Step 1 of {@link #witness}: a connected simple graph with the given (positive, graphical) degrees. */
    static int[][] connectedSimpleRealisation(int[] degrees) {
        int n = degrees.length;
        int[] rem = degrees.clone();
        int[][] g = new int[n][n];
        while (true) {
            int v = 0;
            for (int i = 1; i < n; i++) if (rem[i] > rem[v]) v = i;
            int d = rem[v];
            if (d == 0) break;
            rem[v] = 0;
            for (int k = 0; k < d; k++) {
                int u = -1;
                for (int i = 0; i < n; i++) {
                    if (i == v || rem[i] == 0 || g[v][i] != 0) continue;
                    if (u < 0 || rem[i] > rem[u]) u = i;
                }
                if (u < 0) return null;         // not graphical (excluded by the caller)
                g[v][u] = g[u][v] = 1;
                rem[u]--;
            }
        }
        while (true) {
            int[] comp = components(g);
            boolean single = true;
            for (int c : comp) if (c != 0) single = false;
            if (single) return g;
            int u = -1, v = -1;
            for (int i = 0; i < n && u < 0; i++) {
                for (int j = i + 1; j < n; j++) {
                    if (g[i][j] != 0 && onCycle(g, i, j)) {
                        u = i;
                        v = j;
                        break;
                    }
                }
            }
            if (u < 0) return null;             // cannot happen for sum >= 2(n - 1) and positive degrees
            int x = -1, y = -1;
            for (int i = 0; i < n && x < 0; i++) {
                if (comp[i] == comp[u]) continue;
                for (int j = 0; j < n; j++) {
                    if (g[i][j] != 0) {
                        x = i;
                        y = j;
                        break;
                    }
                }
            }
            if (x < 0) return null;             // every component has an edge (positive degrees)
            g[u][v] = g[v][u] = 0;
            g[x][y] = g[y][x] = 0;
            g[u][x] = g[x][u] = 1;
            g[v][y] = g[y][v] = 1;
        }
    }

    /** True if edge {@code (a,b)} lies on a cycle: {@code b} is still reachable from {@code a} without it. */
    private static boolean onCycle(int[][] g, int a, int b) {
        int n = g.length;
        boolean[] seen = new boolean[n];
        int[] stack = new int[n];
        int top = 0;
        stack[top++] = a;
        seen[a] = true;
        while (top > 0) {
            int w = stack[--top];
            for (int z = 0; z < n; z++) {
                if (g[w][z] == 0 || seen[z] || (w == a && z == b)) continue;
                if (z == b) return true;
                seen[z] = true;
                stack[top++] = z;
            }
        }
        return false;
    }

    /** Connected components of a bond matrix (component index per atom, numbered from 0 in index order). */
    private static int[] components(int[][] g) {
        int n = g.length;
        int[] comp = new int[n];
        Arrays.fill(comp, -1);
        int c = 0;
        int[] stack = new int[n];
        for (int s = 0; s < n; s++) {
            if (comp[s] >= 0) continue;
            int top = 0;
            stack[top++] = s;
            comp[s] = c;
            while (top > 0) {
                int v = stack[--top];
                for (int u = 0; u < n; u++) {
                    if (g[v][u] != 0 && comp[u] < 0) {
                        comp[u] = c;
                        stack[top++] = u;
                    }
                }
            }
            c++;
        }
        return comp;
    }

    /** Step 2 alone (also used by the tests to cross-check step 1). */
    static Verdict exhaustive(HeavyAtom[] atoms, int budget) {
        int n = atoms.length;
        int[] free = new int[n];
        int[] maxBo = new int[n];
        long sum = 0;
        for (int i = 0; i < n; i++) {
            free[i] = atoms[i].maxBondOrderSum - atoms[i].hCount;
            maxBo[i] = atoms[i].maxBondOrder;
            if (free[i] < 0) return Verdict.IMPOSSIBLE;
            sum += free[i];
        }
        if (sum % 2 != 0) return Verdict.IMPOSSIBLE;    // every bond lowers the sum by an even amount
        Search s = new Search(free, maxBo, budget);
        try {
            return s.pass0() ? Verdict.POSSIBLE : Verdict.IMPOSSIBLE;
        } catch (BudgetExceeded e) {
            return Verdict.UNKNOWN;
        }
    }

    /** Havel-Hakimi: is the sequence the degree sequence of a simple graph? */
    static boolean graphical(int[] degrees) {
        int[] d = degrees.clone();
        while (true) {
            Arrays.sort(d);
            int top = d[d.length - 1];
            if (top == 0) return true;
            d[d.length - 1] = 0;
            if (top > d.length - 1) return false;
            for (int k = d.length - 2; k >= d.length - 1 - top; k--) {
                if (d[k] == 0) return false;
                d[k]--;
            }
        }
    }

    private static final class BudgetExceeded extends RuntimeException {
        BudgetExceeded() {
            super(null, null, false, false);
        }
    }

    /** Exhaustive search over the generator's choices; mirrors {@code SingleRandomStructureGenerator.generate}. */
    private static final class Search {
        final int n;
        final int[] rem;
        final int[] maxBo;
        final int[][] order;
        final int budget;
        int visited;
        final Set<Key> failed = new HashSet<>();
        /** Bond orders of the first successful end state ({@link #witness}). */
        int[][] solution;

        Search(int[] free, int[] maxBo, int budget) {
            this.n = free.length;
            this.rem = free.clone();
            this.maxBo = maxBo;
            this.order = new int[n][n];
            this.budget = budget;
        }

        boolean pass0() {
            return step(0, false);
        }

        /** The generator is about to look at atom {@code f}; {@code formed} = a bond was formed in this pass. */
        boolean step(int f, boolean formed) {
            if (f == n) {
                if (formed) return step(0, false);
                if (!(allSaturated() && connected())) return false;
                solution = new int[n][];
                for (int i = 0; i < n; i++) solution[i] = order[i].clone();
                return true;
            }
            if (rem[f] == 0) return step(f + 1, formed);
            if (++visited > budget) throw new BudgetExceeded();
            if (!promising()) return false;
            Key key = key(f, formed);
            if (failed.contains(key)) return false;
            boolean anyPartner = false;
            for (int p = 0; p < n; p++) {
                if (p == f || rem[p] == 0 || order[f][p] != 0) continue;
                anyPartner = true;
                int m = Math.min(Math.min(maxBo[f], rem[f]), Math.min(maxBo[p], rem[p]));
                int maxOrder = Math.max(1, m - 1);
                for (int w = 1; w <= maxOrder; w++) {
                    order[f][p] = order[p][f] = w;
                    rem[f] -= w;
                    rem[p] -= w;
                    boolean ok = step(f + 1, true);
                    order[f][p] = order[p][f] = 0;
                    rem[f] += w;
                    rem[p] += w;
                    if (ok) return true;
                }
            }
            boolean ok = !anyPartner && step(f + 1, formed);
            if (!ok) failed.add(key);
            return ok;
        }

        /**
         * Necessary conditions for reaching a saturated connected structure from here: no atom over-used,
         * every atom can still place its free valence on partners it is not bonded to, and no component
         * other than the whole molecule is already saturated.
         */
        boolean promising() {
            for (int i = 0; i < n; i++) {
                if (rem[i] < 0) return false;
                if (rem[i] == 0) continue;
                int capacity = 0;
                for (int j = 0; j < n; j++) {
                    if (j == i || rem[j] == 0 || order[i][j] != 0) continue;
                    int m = Math.min(Math.min(maxBo[i], rem[i]), Math.min(maxBo[j], rem[j]));
                    capacity += Math.max(1, m - 1);
                }
                if (capacity < rem[i]) return false;
            }
            int[] comp = components();
            int count = 0;
            for (int c : comp) count = Math.max(count, c + 1);
            if (count == 1) return true;
            int[] open = new int[count];
            for (int i = 0; i < n; i++) open[comp[i]] += rem[i];
            for (int c = 0; c < count; c++) if (open[c] == 0) return false;
            return true;
        }

        int[] components() {
            int[] comp = new int[n];
            Arrays.fill(comp, -1);
            int c = 0;
            int[] stack = new int[n];
            for (int s = 0; s < n; s++) {
                if (comp[s] >= 0) continue;
                int top = 0;
                stack[top++] = s;
                comp[s] = c;
                while (top > 0) {
                    int v = stack[--top];
                    for (int u = 0; u < n; u++) {
                        if (order[v][u] != 0 && comp[u] < 0) {
                            comp[u] = c;
                            stack[top++] = u;
                        }
                    }
                }
                c++;
            }
            return comp;
        }

        boolean allSaturated() {
            for (int r : rem) if (r != 0) return false;
            return true;
        }

        boolean connected() {
            for (int c : components()) if (c != 0) return false;
            return true;
        }

        Key key(int f, boolean formed) {
            long h1 = 1469598103934665603L, h2 = 7;
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    int v = order[i][j];
                    h1 = (h1 ^ v) * 1099511628211L;
                    h2 = h2 * 31 + v + 1;
                }
            }
            return new Key(h1, h2, f, formed);
        }
    }

    /** A visited state, identified by two independent 64-bit hashes of its bond matrix. */
    private record Key(long h1, long h2, int f, boolean formed) {
    }
}
