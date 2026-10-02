package io.github.steinbeck.seneca.chem;

import java.util.ArrayList;
import java.util.List;

/**
 * compchem {@code PathUtils} (sphere-wise BFS over {@code nodeTable}, visited by {@code number}).
 *
 * <p>Reference: {@code legacy/decompiled/compchem/compchem/PathUtils.java};
 * {@code docs/legacy/compchem-semantics.md} section 8. The legacy implementation is recursive
 * ({@code nextSphere}); this iterative form has identical results.
 */
public final class PathUtils {

    private PathUtils() {
    }

    /**
     * compchem {@code isConnected}: BFS from {@code atoms[0]}; true iff every atom is reached.
     * Requires {@code atoms[i].number == i}.
     */
    public static boolean isConnected(HeavyAtom[] atoms) {
        boolean[] visited = new boolean[atoms.length];
        visited[0] = true;
        List<HeavyAtom> sphere = new ArrayList<>();
        sphere.add(atoms[0]);
        while (!sphere.isEmpty()) {
            List<HeavyAtom> next = new ArrayList<>();
            for (HeavyAtom a : sphere) {
                for (int k = 0; k < a.degree; k++) {
                    HeavyAtom b = a.nodeTable[k];
                    if (!visited[b.number]) {
                        next.add(b);
                        visited[b.number] = true;
                    }
                }
            }
            sphere = next;
        }
        for (boolean v : visited) {
            if (!v) return false;
        }
        return true;
    }

    /**
     * compchem {@code getPathLength(atoms, from, to, cutoff)}: the sphere number (topological
     * distance) at which {@code to} is first reached from {@code from}, or -1 if it is not reached
     * within {@code cutoff} spheres. {@code from == to} returns -1 ({@code from} is pre-marked visited).
     */
    public static int getPathLength(HeavyAtom[] atoms, HeavyAtom from, HeavyAtom to, int cutoff) {
        boolean[] visited = new boolean[atoms.length];
        visited[from.number] = true;
        List<HeavyAtom> sphere = new ArrayList<>();
        sphere.add(from);
        int depth = 0;
        while (true) {
            if (++depth > cutoff) return -1;
            List<HeavyAtom> next = new ArrayList<>();
            for (HeavyAtom a : sphere) {
                for (int k = 0; k < a.degree; k++) {
                    HeavyAtom b = a.nodeTable[k];
                    if (!visited[b.number]) {
                        if (b == to) return depth;
                        next.add(b);
                        visited[b.number] = true;
                    }
                }
            }
            sphere = next;
        }
    }
}
