package io.github.steinbeck.seneca.golden;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.config.GuiRecipe;

/**
 * Literature reference structures (legacy/harness/references.tsv) and the constitution test of the
 * golden run records ({@code reference.mappable}, {@code bestEqualsReference},
 * {@code resultsEqualReference}): a bijection of atoms preserving element, H count and bond orders.
 * Port of {@code legacy/harness/src/seneca/structgen/RefStructures.java} (Kekulé SMILES reader, Kekulé
 * variants, (element, H) classes, colour refinement + backtracking) and of {@code GoldenMaster.RefCheck}.
 * Harness code, not legacy semantics; it is transcribed rather than replaced by CDK so that the labelling
 * search of {@link ReferenceRecords} sees the same atom order and Kekulé variants as the harness.
 */
public final class ReferenceCheck {

    /** One line of references.tsv. */
    public record Ref(String key, String name, String smiles, String status, String source) {
    }

    /** Heavy-atom graph with implicit H (reference atom order = SMILES order). */
    public record Graph(String[] el, int[] h, int[][] bm) {
        public int n() {
            return el.length;
        }
    }

    private final boolean defined;
    private final boolean mappable;
    private final Ref ref;
    private final Graph graph;
    private final String[] el;
    private final int[] h;

    private ReferenceCheck(boolean defined, boolean mappable, Ref ref, Graph graph, String[] el, int[] h) {
        this.defined = defined;
        this.mappable = mappable;
        this.ref = ref;
        this.graph = graph;
        this.el = el;
        this.h = h;
    }

    /** Loads the reference for {@code GuiRecipe.baseKey(key)}; {@code defined == false} if none. */
    public static ReferenceCheck of(Path referencesTsv, String key, HeavyAtom[] datasetAtoms) throws IOException {
        Ref r = load(referencesTsv).get(GuiRecipe.baseKey(key));
        String[] el = new String[datasetAtoms.length];
        int[] h = new int[datasetAtoms.length];
        for (int f = 0; f < datasetAtoms.length; f++) {
            el[f] = datasetAtoms[f].symbol;
            h[f] = datasetAtoms[f].hCount;
        }
        if (r == null) return new ReferenceCheck(false, false, null, null, el, h);
        Graph g = parseSmiles(r.smiles());
        boolean mappable = classes(g.el(), g.h()).equals(classes(el, h));
        return new ReferenceCheck(true, mappable, r, g, el, h);
    }

    public boolean defined() {
        return defined;
    }

    public boolean mappable() {
        return mappable;
    }

    /** The reference (null if not defined). */
    public Ref ref() {
        return ref;
    }

    /** The parsed reference graph (null if not defined). */
    public Graph graph() {
        return graph;
    }

    /** True if the labelled structure {@code bm} (dataset order) has the reference's constitution. */
    public boolean equalConstitution(int[][] bm) {
        if (!defined) return false;
        return sameConstitution(el, h, bm, graph.el(), graph.h(), graph.bm());
    }

    // ------------------------------------------------------------------ references.tsv

    /** key -&gt; Ref; lines: key TAB name TAB smiles TAB status TAB source; '#' comments. */
    public static Map<String, Ref> load(Path tsv) throws IOException {
        Map<String, Ref> m = new LinkedHashMap<>();
        for (String line : Files.readAllLines(tsv, StandardCharsets.UTF_8)) {
            if (line.trim().isEmpty() || line.startsWith("#")) continue;
            String[] c = line.split("\t");
            if (c.length != 5) throw new IllegalArgumentException("references.tsv: 5 columns expected: " + line);
            m.put(c[0], new Ref(c[0], c[1], c[2], c[3], c[4]));
        }
        return m;
    }

    // ------------------------------------------------------------------ SMILES (Kekulé, organic subset)

    static int valence(String el) {
        return switch (el) {
            case "C" -> 4;
            case "N", "P" -> 3;
            case "O", "S" -> 2;
            case "F", "Cl", "Br", "I" -> 1;
            default -> throw new IllegalArgumentException("element " + el);
        };
    }

    /**
     * Parses a Kekulé SMILES (no aromatic lower-case atoms, no charges). Stereo marks (@, /, \) are
     * ignored. Implicit H = default valence - bond order sum, or the bracket H count.
     */
    public static Graph parseSmiles(String s) {
        List<String> el = new ArrayList<>();
        List<Integer> hExplicit = new ArrayList<>();
        List<int[]> bonds = new ArrayList<>();
        Deque<Integer> stack = new ArrayDeque<>();
        Map<Integer, int[]> rings = new HashMap<>();
        int prev = -1, order = 1;
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '(') { stack.push(prev); i++; continue; }
            if (c == ')') { prev = stack.pop(); i++; continue; }
            if (c == '-') { order = 1; i++; continue; }
            if (c == '=') { order = 2; i++; continue; }
            if (c == '#') { order = 3; i++; continue; }
            if (c == '/' || c == '\\') { i++; continue; }
            if (Character.isDigit(c) || c == '%') {
                int num;
                if (c == '%') { num = Integer.parseInt(s.substring(i + 1, i + 3)); i += 3; }
                else { num = c - '0'; i++; }
                int[] open = rings.remove(num);
                if (open == null) rings.put(num, new int[]{prev, order});
                else bonds.add(new int[]{open[0], prev, Math.max(order, open[1])});
                order = 1;
                continue;
            }
            String sym;
            int hx = -1;
            if (c == '[') {
                int e = s.indexOf(']', i);
                String in = s.substring(i + 1, e);
                int k = 0;
                while (k < in.length() && Character.isDigit(in.charAt(k))) k++;
                int st = k;
                k++;
                if (k < in.length() && Character.isLowerCase(in.charAt(k))) k++;
                sym = in.substring(st, k);
                hx = 0;
                while (k < in.length()) {
                    char d = in.charAt(k);
                    if (d == 'H') {
                        hx = 1;
                        if (k + 1 < in.length() && Character.isDigit(in.charAt(k + 1))) { hx = in.charAt(k + 1) - '0'; k++; }
                    } else if (d == '+' || d == '-') throw new IllegalArgumentException("charges not supported: " + s);
                    k++;
                }
                i = e + 1;
            } else if (s.startsWith("Cl", i) || s.startsWith("Br", i)) { sym = s.substring(i, i + 2); i += 2; }
            else if (Character.isUpperCase(c)) { sym = String.valueOf(c); i++; }
            else throw new IllegalArgumentException("unsupported SMILES character '" + c + "' (Kekule form required): " + s);
            valence(sym);
            int idx = el.size();
            el.add(sym);
            hExplicit.add(hx);
            if (prev >= 0) bonds.add(new int[]{prev, idx, order});
            prev = idx;
            order = 1;
        }
        if (!rings.isEmpty()) throw new IllegalArgumentException("unclosed ring in " + s);
        int n = el.size();
        int[][] bm = new int[n][n];
        for (int[] b : bonds) { bm[b[0]][b[1]] = b[2]; bm[b[1]][b[0]] = b[2]; }
        int[] h = new int[n];
        for (int a = 0; a < n; a++) {
            int sum = 0;
            for (int b = 0; b < n; b++) sum += bm[a][b];
            h[a] = hExplicit.get(a) >= 0 ? hExplicit.get(a) : valence(el.get(a)) - sum;
            if (h[a] < 0) throw new IllegalArgumentException("valence exceeded at atom " + a + " in " + s);
        }
        return new Graph(el.toArray(new String[0]), h, bm);
    }

    // ------------------------------------------------------------------ Kekulé structures

    /**
     * All bond matrices that differ from g only by moving the ring double bonds (perfect matchings of
     * the ring double-bond atoms over ring bonds). The input structure is always the first element.
     */
    public static List<int[][]> kekuleVariants(Graph g) {
        int n = g.n();
        boolean[][] ring = new boolean[n][n];
        for (int a = 0; a < n; a++)
            for (int b = a + 1; b < n; b++)
                if (g.bm()[a][b] != 0 && inCycle(g.bm(), a, b)) ring[a][b] = ring[b][a] = true;
        boolean[] inA = new boolean[n];
        for (int a = 0; a < n; a++)
            for (int b = 0; b < n; b++)
                if (ring[a][b] && g.bm()[a][b] == 2) inA[a] = true;
        int[][] base = copy(g.bm());
        for (int a = 0; a < n; a++)
            for (int b = 0; b < n; b++)
                if (inA[a] && inA[b] && ring[a][b]) base[a][b] = 1;
        List<int[][]> out = new ArrayList<>();
        out.add(copy(g.bm()));
        Set<String> seen = new HashSet<>();
        seen.add(Hashes.hash(g.bm()));
        matchings(base, ring, inA, new boolean[n], 0, out, seen);
        return out;
    }

    private static void matchings(int[][] bm, boolean[][] ring, boolean[] inA, boolean[] used, int from,
                                  List<int[][]> out, Set<String> seen) {
        int n = bm.length;
        int a = from;
        while (a < n && (!inA[a] || used[a])) a++;
        if (a == n) {
            if (seen.add(Hashes.hash(bm))) out.add(copy(bm));
            return;
        }
        used[a] = true;
        for (int b = a + 1; b < n; b++) {
            if (!inA[b] || used[b] || !ring[a][b]) continue;
            used[b] = true;
            bm[a][b] = bm[b][a] = 2;
            matchings(bm, ring, inA, used, a + 1, out, seen);
            bm[a][b] = bm[b][a] = 1;
            used[b] = false;
        }
        used[a] = false;
    }

    /** Is bond a-b in a cycle (a and b still connected without it)? */
    static boolean inCycle(int[][] bm, int a, int b) {
        int n = bm.length;
        boolean[] seen = new boolean[n];
        Deque<Integer> q = new ArrayDeque<>();
        q.add(a);
        seen[a] = true;
        while (!q.isEmpty()) {
            int x = q.poll();
            for (int y = 0; y < n; y++) {
                if (bm[x][y] == 0 || seen[y]) continue;
                if ((x == a && y == b) || (x == b && y == a)) continue;
                if (y == b) return true;
                seen[y] = true;
                q.add(y);
            }
        }
        return false;
    }

    static int[][] copy(int[][] m) {
        int[][] c = new int[m.length][];
        for (int i = 0; i < m.length; i++) c[i] = m[i].clone();
        return c;
    }

    // ------------------------------------------------------------------ classes and mapping

    /** Class label of an atom: element and H count (the generator never changes either). */
    public static String cls(String el, int h) {
        return el + "H" + h;
    }

    /** Sorted multiset of class labels. */
    public static List<String> classes(String[] el, int[] h) {
        List<String> l = new ArrayList<>();
        for (int i = 0; i < el.length; i++) l.add(cls(el[i], h[i]));
        Collections.sort(l);
        return l;
    }

    /** Human-readable difference of two class multisets ("" if equal). */
    public static String classDiff(List<String> ref, List<String> data) {
        Map<String, Integer> c = new TreeMap<>();
        for (String s : ref) c.merge(s, 1, Integer::sum);
        for (String s : data) c.merge(s, -1, Integer::sum);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : c.entrySet()) {
            if (e.getValue() == 0) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(e.getKey()).append(e.getValue() > 0 ? " +" + e.getValue() + " in reference" : " +" + (-e.getValue()) + " in dataset");
        }
        return sb.toString();
    }

    /** Dataset bond matrix of a reference bond matrix under map (reference atom -&gt; dataset atom). */
    public static int[][] place(int[][] refBm, int[] map) {
        int n = refBm.length;
        int[][] bm = new int[n][n];
        for (int a = 0; a < n; a++)
            for (int b = 0; b < n; b++)
                bm[map[a]][map[b]] = refBm[a][b];
        return bm;
    }

    // ------------------------------------------------------------------ constitution equality

    /**
     * True iff the two labelled structures have the same constitution: a bijection of atoms that
     * preserves element, H count and every bond order. Colour refinement, then backtracking.
     */
    public static boolean sameConstitution(String[] elA, int[] hA, int[][] a, String[] elB, int[] hB, int[][] b) {
        int n = elA.length;
        if (elB.length != n) return false;
        long[] ca = new long[n], cb = new long[n];
        Map<String, Long> ids = new HashMap<>();
        for (int i = 0; i < n; i++) {
            ca[i] = ids.computeIfAbsent(cls(elA[i], hA[i]), k -> (long) ids.size());
            cb[i] = ids.computeIfAbsent(cls(elB[i], hB[i]), k -> (long) ids.size());
        }
        for (int round = 0; round < n; round++) {
            Map<String, Long> next = new HashMap<>();
            long[] na = refine(ca, a, next), nb = refine(cb, b, next);
            boolean stable = distinct(na) == distinct(ca) && distinct(nb) == distinct(cb);
            ca = na;
            cb = nb;
            if (stable) break;
        }
        long[] sa = ca.clone(), sb = cb.clone();
        Arrays.sort(sa);
        Arrays.sort(sb);
        if (!Arrays.equals(sa, sb)) return false;
        int[] map = new int[n];
        Arrays.fill(map, -1);
        return extend(0, map, new boolean[n], ca, cb, a, b);
    }

    private static long[] refine(long[] c, int[][] bm, Map<String, Long> ids) {
        int n = c.length;
        long[] out = new long[n];
        for (int i = 0; i < n; i++) {
            List<String> nb = new ArrayList<>();
            for (int j = 0; j < n; j++) if (bm[i][j] != 0) nb.add(c[j] + ":" + bm[i][j]);
            Collections.sort(nb);
            String key = c[i] + "|" + nb;
            Long id = ids.get(key);
            if (id == null) {
                id = (long) ids.size();
                ids.put(key, id);
            }
            out[i] = id;
        }
        return out;
    }

    private static int distinct(long[] c) {
        Set<Long> s = new HashSet<>();
        for (long x : c) s.add(x);
        return s.size();
    }

    private static boolean extend(int i, int[] map, boolean[] used, long[] ca, long[] cb, int[][] a, int[][] b) {
        int n = map.length;
        if (i == n) return true;
        for (int j = 0; j < n; j++) {
            if (used[j] || ca[i] != cb[j]) continue;
            boolean ok = true;
            for (int k = 0; k < i && ok; k++) if (a[i][k] != b[j][map[k]]) ok = false;
            if (!ok) continue;
            map[i] = j;
            used[j] = true;
            if (extend(i + 1, map, used, ca, cb, a, b)) return true;
            used[j] = false;
            map[i] = -1;
        }
        return false;
    }
}
