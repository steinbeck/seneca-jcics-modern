package seneca.structgen;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Reference (literature) structures for the golden master: a minimal Kekule-SMILES reader,
 * Kekule-structure enumeration, a constitution (graph-isomorphism) test, and the class
 * structure needed to map a reference onto the dataset's labelled atoms.
 * Pure harness code; no legacy class is involved. See golden/FORMAT.md, section (e).
 */
final class RefStructures {

    /** One line of legacy/harness/references.tsv. */
    static final class Ref {
        String key, name, smiles, status, source;
    }

    /** Heavy-atom graph with implicit H. */
    static final class Graph {
        final String[] el;
        final int[] h;
        final int[][] bm;
        Graph(String[] el, int[] h, int[][] bm) { this.el = el; this.h = h; this.bm = bm; }
        int n() { return el.length; }
    }

    // ------------------------------------------------------------------ references.tsv

    /** key -> Ref; lines: key TAB name TAB smiles TAB status TAB source; '#' comments. */
    static Map<String, Ref> load(File tsv) throws IOException {
        Map<String, Ref> m = new LinkedHashMap<String, Ref>();
        for (String line : java.nio.file.Files.readAllLines(tsv.toPath(), StandardCharsets.UTF_8)) {
            if (line.trim().isEmpty() || line.startsWith("#")) continue;
            String[] c = line.split("\t");
            if (c.length != 5) throw new IllegalArgumentException("references.tsv: 5 columns expected: " + line);
            Ref r = new Ref();
            r.key = c[0]; r.name = c[1]; r.smiles = c[2]; r.status = c[3]; r.source = c[4];
            m.put(r.key, r);
        }
        return m;
    }

    // ------------------------------------------------------------------ SMILES (Kekule, organic subset)

    static int valence(String el) {
        switch (el) {
            case "C": return 4;
            case "N": case "P": return 3;
            case "O": case "S": return 2;
            case "F": case "Cl": case "Br": case "I": return 1;
            default: throw new IllegalArgumentException("element " + el);
        }
    }

    /**
     * Parses a Kekule SMILES (no aromatic lower-case atoms, no charges). Stereo marks
     * (@, /, \) are ignored. Implicit H = default valence - bond order sum, or the bracket H count.
     */
    static Graph parseSmiles(String s) {
        List<String> el = new ArrayList<String>();
        List<Integer> hExplicit = new ArrayList<Integer>();
        List<int[]> bonds = new ArrayList<int[]>();
        Deque<Integer> stack = new ArrayDeque<Integer>();
        Map<Integer, int[]> rings = new HashMap<Integer, int[]>();
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
                else {
                    int o = Math.max(order, open[1]);
                    bonds.add(new int[]{open[0], prev, o});
                }
                order = 1;
                continue;
            }
            String sym;
            int hx = -1;
            if (c == '[') {
                int e = s.indexOf(']', i);
                String in = s.substring(i + 1, e);
                int k = 0;
                while (k < in.length() && Character.isDigit(in.charAt(k))) k++;     // isotope
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

    // ------------------------------------------------------------------ Kekule structures

    /**
     * All bond matrices that differ from g only by moving the ring double bonds: the perfect
     * matchings of the graph (atoms that carry a ring double bond, ring bonds between them).
     * Every atom keeps its bond-order sum. The input structure is always the first element.
     */
    static List<int[][]> kekuleVariants(Graph g) {
        int n = g.n();
        boolean[][] ring = new boolean[n][n];
        for (int a = 0; a < n; a++)
            for (int b = a + 1; b < n; b++)
                if (g.bm[a][b] != 0 && inCycle(g.bm, a, b)) { ring[a][b] = ring[b][a] = true; }
        boolean[] inA = new boolean[n];
        for (int a = 0; a < n; a++)
            for (int b = 0; b < n; b++)
                if (ring[a][b] && g.bm[a][b] == 2) inA[a] = true;
        int[][] base = new int[n][n];
        for (int a = 0; a < n; a++) base[a] = g.bm[a].clone();
        for (int a = 0; a < n; a++)
            for (int b = 0; b < n; b++)
                if (inA[a] && inA[b] && ring[a][b]) base[a][b] = 1;        // all candidate ring bonds single
        List<int[][]> out = new ArrayList<int[][]>();
        out.add(copy(g.bm));
        Set<String> seen = new HashSet<String>();
        seen.add(GoldenMaster.hash(g.bm));
        matchings(base, ring, inA, new boolean[n], 0, out, seen);
        return out;
    }

    private static void matchings(int[][] bm, boolean[][] ring, boolean[] inA, boolean[] used, int from,
                                  List<int[][]> out, Set<String> seen) {
        int n = bm.length;
        int a = from;
        while (a < n && (!inA[a] || used[a])) a++;
        if (a == n) {
            String h = GoldenMaster.hash(bm);
            if (seen.add(h)) out.add(copy(bm));
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
        Deque<Integer> q = new ArrayDeque<Integer>();
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
    static String cls(String el, int h) { return el + "H" + h; }

    /** Sorted multiset of class labels. */
    static List<String> classes(String[] el, int[] h) {
        List<String> l = new ArrayList<String>();
        for (int i = 0; i < el.length; i++) l.add(cls(el[i], h[i]));
        Collections.sort(l);
        return l;
    }

    /** Human-readable difference of two class multisets ("" if equal). */
    static String classDiff(List<String> ref, List<String> data) {
        Map<String, Integer> c = new TreeMap<String, Integer>();
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

    /** Dataset bond matrix of a reference bond matrix under map (reference atom -> dataset atom). */
    static int[][] place(int[][] refBm, int[] map) {
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
    static boolean sameConstitution(String[] elA, int[] hA, int[][] a, String[] elB, int[] hB, int[][] b) {
        int n = elA.length;
        if (elB.length != n) return false;
        long[] ca = new long[n], cb = new long[n];
        Map<String, Long> ids = new HashMap<String, Long>();
        for (int i = 0; i < n; i++) {
            ca[i] = ids.computeIfAbsent(cls(elA[i], hA[i]), k -> (long) ids.size());
            cb[i] = ids.computeIfAbsent(cls(elB[i], hB[i]), k -> (long) ids.size());
        }
        for (int round = 0; round < n; round++) {
            Map<String, Long> next = new HashMap<String, Long>();
            long[] na = refine(ca, a, next), nb = refine(cb, b, next);
            boolean stable = distinct(na) == distinct(ca) && distinct(nb) == distinct(cb);
            ca = na; cb = nb;
            if (stable) break;
        }
        long[] sa = ca.clone(), sb = cb.clone();
        Arrays.sort(sa); Arrays.sort(sb);
        if (!Arrays.equals(sa, sb)) return false;
        int[] map = new int[n];
        Arrays.fill(map, -1);
        return extend(0, map, new boolean[n], ca, cb, a, b);
    }

    private static long[] refine(long[] c, int[][] bm, Map<String, Long> ids) {
        int n = c.length;
        long[] out = new long[n];
        for (int i = 0; i < n; i++) {
            List<String> nb = new ArrayList<String>();
            for (int j = 0; j < n; j++) if (bm[i][j] != 0) nb.add(c[j] + ":" + bm[i][j]);
            Collections.sort(nb);
            String key = c[i] + "|" + nb;
            Long id = ids.get(key);
            if (id == null) { id = (long) ids.size(); ids.put(key, id); }
            out[i] = id;
        }
        return out;
    }

    private static int distinct(long[] c) {
        Set<Long> s = new HashSet<Long>();
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
