package io.github.steinbeck.seneca.chem;

import java.util.Vector;

/**
 * compchem {@code SetOfRingsFinder.findSSSR}: the legacy variant of Figueras' algorithm used by
 * {@code FeatureJudge}. Re-implemented <b>verbatim</b>, including its adjacency-order dependence
 * and its failure modes. Do not substitute CDK ({@code Cycles.sssr} gives different ring sizes for
 * 3.0 % of random isomers).
 *
 * <p>Reference: {@code legacy/decompiled/compchem/compchem/SetOfRingsFinder.java}, {@code Ring.java},
 * {@code setOfRings.java}, {@code Queue.java}; algorithm and failure modes in
 * {@code docs/legacy/compchem-semantics.md} 6.1-6.3. Works on {@link AtomSets#cloneAtomSet clones} with
 * {@code number = index}; the input is untouched. Neighbour lists of the input must be in ascending
 * index ({@link AtomSets#synchronizeBonds}). {@code restoreBond} appends (permutes adjacency order) -
 * reproduced. Lists are {@link Vector}s accessed with {@code elementAt}, as in compchem, so that every
 * index failure has the legacy exception class and message.
 *
 * <p>Failure modes (all three reproduced; the caller, {@code judges.FeatureJudge}, decides what to do
 * in {@code LegacyCompat.DEFAULT}):
 * <ol>
 *   <li>degree-2 branch: {@code breakBond(Atom)} on an atom whose degree already dropped to 0 reads
 *       {@code nodeTable[-1]} and throws {@code ArrayIndexOutOfBoundsException("Index -1 out of bounds
 *       for length 10")}. The port throws this exception explicitly (same class and message), so that the
 *       message survives the JVM's fast-throw optimisation of hot implicit exceptions.</li>
 *   <li>degree-3 branch: {@code checkEdges} on an empty candidate list throws
 *       {@code ArrayIndexOutOfBoundsException("0 >= 0")} ({@code Vector.elementAt(0)} on an empty
 *       vector) - falls out of the port as well.</li>
 *   <li>hang: when the atom of smallest non-zero degree has degree 4, 5 or 6 the legacy loop changes
 *       nothing and never terminates. This state is detected before the (no-op) iteration and reported
 *       as {@link HangDetected} (DEVIATIONS.md DV-03). As a safety net against any other
 *       non-terminating input, more than {@value #MAX_ITERATIONS} iterations also throw
 *       {@link HangDetected} (every legitimate iteration removes at least one bond, so this bound is far
 *       above anything a SENECA structure can need).</li>
 * </ol>
 *
 * <p>Not thread-safe ({@code FGsource} is an instance field, as legacy); every judge owns its own finder.
 */
public final class RingFinder {

    /** Iteration bound of the hang safety net (see failure mode 3). */
    static final int MAX_ITERATIONS = 1_000_000;

    /** Thrown instead of the legacy infinite loop (failure mode 3). */
    public static final class HangDetected extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public HangDetected(String message) {
            super(message);
        }
    }

    /** compchem {@code FGsource}: BFS predecessor per atom number, rewritten before every read. */
    private HeavyAtom[] fgSource;

    /** compchem {@code ringCounter}: number of rings of the last {@link #findSSSR}. */
    public int ringCounter = 0;

    /**
     * compchem {@code findSSSR(Atom[])}: rings in discovery order (legacy {@code setOfRings}).
     *
     * @throws ArrayIndexOutOfBoundsException failure modes 1 and 2 (legacy-exact)
     * @throws HangDetected failure mode 3
     */
    public Vector<Ring> findSSSR(HeavyAtom[] atoms) {
        ringCounter = 0;
        Vector<Ring> sssr = new Vector<>();
        HeavyAtom[] g = AtomSets.cloneAtomSet(atoms);
        for (int i = 0; i < g.length; i++) g[i].number = i;
        fgSource = new HeavyAtom[g.length];
        Vector<HeavyAtom> all = new Vector<>();
        Vector<HeavyAtom> trimmed = new Vector<>();
        Vector<HeavyAtom> nodesN2 = new Vector<>();
        for (HeavyAtom a : g) all.addElement(a);
        int iterations = 0;
        do {
            int minDegree = 7;
            HeavyAtom smallest = null;
            nodesN2.removeAllElements();
            for (int i = 0; i < g.length; i++) {
                if (g[i].degree == 0 && !trimmed.contains(g[i])) trimmed.addElement(g[i]);
                if (g[i].degree == 2 && !trimmed.contains(g[i])) nodesN2.addElement(g[i]);
                if (g[i].degree < minDegree && g[i].degree > 0) {
                    smallest = g[i];
                    minDegree = g[i].degree;
                }
            }
            if (smallest == null) break;
            if (++iterations > MAX_ITERATIONS) {
                throw new HangDetected("compchem SetOfRingsFinder.findSSSR: no termination after " + MAX_ITERATIONS + " iterations");
            }
            if (smallest.degree == 1) {
                trim(smallest);
                trimmed.addElement(smallest);
            } else if (smallest.degree == 2) {
                HeavyAtom[] broken = new HeavyAtom[nodesN2.size()];
                int nBroken = 0;
                for (int i = 0; i < nodesN2.size(); i++) {
                    Ring r = findSRing(nodesN2.elementAt(i), g);
                    if (r.size() > 0 && !ringAlreadyInSet(sssr, r)) {
                        sssr.addElement(r);
                        broken[nBroken] = nodesN2.elementAt(i);
                        nBroken++;
                    }
                }
                if (nBroken == 0) {
                    nBroken = 1;
                    broken[0] = nodesN2.elementAt(0);
                }
                for (int i = 0; i < nBroken; i++) breakBond(broken[i]);
            } else if (smallest.degree == 3) {
                Ring r = findSRing(smallest, g);
                if (r.size() > 0 && !ringAlreadyInSet(sssr, r)) sssr.addElement(r);
                HeavyAtom[] edge = checkEdges(r, g);
                breakBond(edge[0], edge[1]);
            } else {
                // degree 4..6: legacy does nothing and loops forever (failure mode 3)
                throw new HangDetected("compchem SetOfRingsFinder.findSSSR would loop forever: smallest non-zero degree is "
                        + smallest.degree + " (atom " + smallest.number + ")");
            }
        } while (trimmed.size() < all.size());
        ringCounter = sssr.size();
        return sssr;
    }

    /** compchem {@code setOfRings.ringAlreadyInSet}. */
    static boolean ringAlreadyInSet(Vector<Ring> set, Ring r) {
        String key = r.key();
        for (int i = 0; i < set.size(); i++) {
            if (key.equals(set.elementAt(i).key())) return true;
        }
        return false;
    }

    /** compchem {@code findSRing(Atom, Atom[])}: BFS for the smallest ring through {@code root}. */
    Ring findSRing(HeavyAtom root, HeavyAtom[] g) {
        int n = g.length;
        Vector<HeavyAtom> queue = new Vector<>();
        @SuppressWarnings("unchecked")
        Vector<HeavyAtom>[] path = new Vector[n];
        for (int i = 0; i < n; i++) path[i] = new Vector<>();
        for (int k = 0; k < root.degree; k++) {
            HeavyAtom nb = root.nodeTable[k];
            if (nb.degree > 0) {
                fgSource[nb.number] = root;
                queue.addElement(nb);
                path[nb.number].addElement(root.nodeTable[k]);
                path[nb.number].addElement(root);
            }
        }
        while (queue.size() > 0) {
            HeavyAtom node = queue.elementAt(0);
            queue.removeElementAt(0);
            HeavyAtom source = fgSource[node.number];
            for (int k = 0; k < node.degree; k++) {
                HeavyAtom m = node.nodeTable[k];
                if (m.degree > 0 && m != source) {
                    if (path[m.number].size() > 0) {
                        Vector<HeavyAtom> intersection = getIntersection(path[node.number], path[m.number]);
                        if (intersection.size() == 1) {
                            Vector<HeavyAtom> union = getUnion(path[node.number], path[m.number]);
                            return prepareRing(union);
                        }
                    } else {
                        path[m.number] = merge(path[m.number], path[node.number]);
                        path[m.number].insertElementAt(m, 0);
                        fgSource[m.number] = node;
                        queue.addElement(m);
                    }
                }
            }
        }
        return new Ring();
    }

    private static Vector<HeavyAtom> getIntersection(Vector<HeavyAtom> a, Vector<HeavyAtom> b) {
        Vector<HeavyAtom> r = new Vector<>();
        for (int i = 0; i < a.size(); i++) {
            if (b.contains(a.elementAt(i))) r.addElement(a.elementAt(i));
        }
        return r;
    }

    private static Vector<HeavyAtom> getUnion(Vector<HeavyAtom> a, Vector<HeavyAtom> b) {
        Vector<HeavyAtom> r = shallowClone(a);
        for (int i = b.size() - 1; i > -1; i--) {
            if (!a.contains(b.elementAt(i))) r.addElement(b.elementAt(i));
        }
        return r;
    }

    private static Vector<HeavyAtom> merge(Vector<HeavyAtom> a, Vector<HeavyAtom> b) {
        Vector<HeavyAtom> r = shallowClone(a);
        for (int i = 0; i < b.size(); i++) r.addElement(b.elementAt(i));
        return r;
    }

    private static Vector<HeavyAtom> shallowClone(Vector<HeavyAtom> a) {
        Vector<HeavyAtom> r = new Vector<>();
        for (int i = 0; i < a.size(); i++) r.addElement(a.elementAt(i));
        return r;
    }

    private static Ring prepareRing(Vector<HeavyAtom> atoms) {
        Ring r = new Ring();
        for (int i = 0; i < atoms.size(); i++) r.atoms.addElement(atoms.elementAt(i));
        r.sort();
        return r;
    }

    /** compchem {@code trim(Atom, Atom[])}: removes all bonds of {@code a}. */
    private static void trim(HeavyAtom a) {
        for (int k = 0; k < a.degree; k++) {
            HeavyAtom b = a.nodeTable[k];
            for (int m = 0; m < b.degree; m++) {
                if (b.nodeTable[m] == a) {
                    if (m < b.nodeTable.length - 1) {
                        for (int s = m; s < b.degree - 1; s++) b.nodeTable[s] = b.nodeTable[s + 1];
                    }
                    b.nodeTable[b.degree - 1] = null;
                    b.degree--;
                }
            }
            a.nodeTable[k] = null;
        }
        a.degree = 0;
    }

    /**
     * compchem {@code breakBond(Atom, Atom[])}: removes {@code a}'s <b>last</b> neighbour. No degree check:
     * for degree 0 it reads {@code nodeTable[-1]} (failure mode 1).
     */
    private static void breakBond(HeavyAtom a) {
        int degree = a.degree;
        if (degree < 1 || degree > a.nodeTable.length) {
            // the JVM's own exception for nodeTable[degree - 1], constructed explicitly: an implicit array
            // exception thrown often from compiled code may lose its message (-XX:+OmitStackTraceInFastThrow)
            throw new ArrayIndexOutOfBoundsException("Index " + (degree - 1) + " out of bounds for length " + a.nodeTable.length);
        }
        HeavyAtom b = a.nodeTable[degree - 1];
        a.nodeTable[degree - 1] = null;
        a.degree--;
        for (int k = 0; k < b.degree; k++) {
            if (b.nodeTable[k] == a) {
                b.nodeTable[k] = null;
                for (int s = k; s < b.degree - 1; s++) b.nodeTable[s] = b.nodeTable[s + 1];
                b.nodeTable[b.degree - 1] = null;
                b.degree--;
                break;
            }
        }
    }

    /** compchem {@code breakBond(Atom, Atom, Atom[])}: removes the bond u-v from both lists (shift left). */
    private static void breakBond(HeavyAtom u, HeavyAtom v) {
        for (int k = 0; k < u.degree; k++) {
            if (u.nodeTable[k] == v) {
                for (int s = k; s < u.degree - 1; s++) u.nodeTable[s] = u.nodeTable[s + 1];
                u.nodeTable[u.degree - 1] = null;
                u.degree--;
                break;
            }
        }
        for (int k = 0; k < v.degree; k++) {
            if (v.nodeTable[k] == u) {
                for (int s = k; s < v.degree - 1; s++) v.nodeTable[s] = v.nodeTable[s + 1];
                v.nodeTable[v.degree - 1] = null;
                v.degree--;
                break;
            }
        }
    }

    /** compchem {@code restoreBond}: <b>appends</b> v to u and u to v (permutes adjacency order). */
    private static void restoreBond(HeavyAtom u, HeavyAtom v) {
        u.nodeTable[u.degree] = v;
        u.degree++;
        v.nodeTable[v.degree] = u;
        v.degree++;
    }

    /**
     * compchem {@code checkEdges(Ring, Atom[])}: for every ring edge, break it, find the smallest ring
     * through its first atom (the second atom's search result is computed and discarded, legacy bug),
     * restore it; return the edge with the first strictly smallest candidate ring. An empty ring gives an
     * empty candidate list and {@code elementAt(0)} throws (failure mode 2).
     */
    private HeavyAtom[] checkEdges(Ring ring, HeavyAtom[] g) {
        Vector<Ring> candidates = new Vector<>();
        for (int i = 0; i < ring.size(); i++) {
            HeavyAtom[] edge = ring.getEdge(i);
            breakBond(edge[0], edge[1]);
            Ring r1 = findSRing(edge[0], g);
            Ring r2 = findSRing(edge[1], g);
            if (r1.size() > r2.size()) candidates.addElement(r1);
            else candidates.addElement(r1);
            restoreBond(edge[0], edge[1]);
        }
        int minSize = candidates.elementAt(0).size();
        int minIndex = 0;
        for (int i = 0; i < candidates.size(); i++) {
            if (candidates.elementAt(i).size() < minSize) {
                minSize = candidates.elementAt(i).size();
                minIndex = i;
            }
        }
        return ring.getEdge(minIndex);
    }
}
