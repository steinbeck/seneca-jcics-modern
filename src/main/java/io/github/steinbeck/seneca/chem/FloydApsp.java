package io.github.steinbeck.seneca.chem;

/**
 * compchem {@code ShortestPathFinder.computeFloydAPSP(int[][])}: Floyd-Warshall on
 * {@code ct[i][j] == 0 ? 999999999 : 1}, diagonal 0, loop order k, i, j.
 *
 * <p>Reference: {@code legacy/decompiled/compchem/compchem/ShortestPathFinder.java}. Only use:
 * connectivity test of the start structure ({@code StructureGenerator.structureConnected}: any entry
 * {@code > n} means disconnected).
 */
public final class FloydApsp {

    /** Distance used for "no bond" before relaxation. */
    public static final int INFINITE = 999999999;

    private FloydApsp() {
    }

    public static int[][] compute(int[][] ct) {
        int n = ct.length;
        int[][] d = new int[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                d[i][j] = ct[i][j] == 0 ? INFINITE : 1;
            }
        }
        for (int i = 0; i < n; i++) d[i][i] = 0;
        for (int k = 0; k < n; k++) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    if (d[i][k] + d[k][j] < d[i][j]) d[i][j] = d[i][k] + d[k][j];
                }
            }
        }
        return d;
    }
}
