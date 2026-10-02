package io.github.steinbeck.seneca.chem;

import java.util.ArrayList;
import java.util.List;

/**
 * compchem {@code HOSECodeGenerator.getHOSECode(atoms, root, maxSphere)}: compchem's own HOSE code format
 * (e.g. {@code "=OCC(//)"}), no centre code, no H count, no ring flag.
 *
 * <p>Reference: {@code legacy/decompiled/compchem/compchem/HOSECodeGenerator.java};
 * {@code docs/legacy/compchem-semantics.md} section 4 (constants 4.1, algorithm 4.2, one-sphere form
 * 4.3). The general algorithm is ported statement by statement (SENECA only calls {@code maxSphere = 1}):
 * element ranks C 9000, O 8900, N 8800, S 8700, P 8600, Si 8500, B 8400, F 8300, Cl 8200, Br 8100,
 * ";" 8000, I 7900, "#" 1200, "&amp;" 1100, "," 1000, other symbols {@code 800000 - atomicMass} of the
 * 2001 major isotope (H 799999; a symbol without isotope entry, e.g. the wildcard "X", throws
 * {@link NullPointerException} exactly like compchem); bond ranks {0, 0, 200000, 300000, 100000}; bond
 * symbols {"", "", "=", "%", "*"}; output symbols Si-&gt;Q, Cl-&gt;X, Br-&gt;Y, ","-&gt;"". Stable bubble sort,
 * descending, {@code long} scores. CDK 2.13 + {@code makeBremserCompliant()} is identical on SENECA's
 * domain (0 mismatches in 287,849 atoms) and serves as test oracle only.
 *
 * <p>Not thread-safe (per-call state in fields, as legacy); every judge owns its own instance.
 */
public final class HoseCodeGenerator {

    private static final String[] RANKED_SYMBOLS = {"C", "O", "N", "S", "P", "Si", "B", "F", "Cl", "Br", ";", "I", "#", "&", ","};
    private static final int[] SYMBOL_RANKINGS = {9000, 8900, 8800, 8700, 8600, 8500, 8400, 8300, 8200, 8100, 8000, 7900, 1200, 1100, 1000};
    private static final int[] BOND_RANKINGS = {0, 0, 200000, 300000, 100000};
    private static final String[] SPHERE_DELIMITERS = {"(", "/", "/", ")", "/", "/", "/", "/", "/", "/", "/", "/"};
    private static final String[] BOND_SYMBOLS = {"", "", "=", "%", "*"};

    /** compchem {@code HOSECodeGenerator.TreeNode}. */
    private static final class TreeNode {
        final String symbol;
        final HeavyAtom source;
        final HeavyAtom number;
        final int bondType;
        long score;

        TreeNode(String symbol, HeavyAtom source, HeavyAtom number, int bondType, long score) {
            this.symbol = symbol;
            this.source = source;
            this.number = number;
            this.bondType = bondType;
            this.score = score;
        }
    }

    private boolean[] visited;
    private HeavyAtom[] setOfNodes;
    private int sphere;
    private int maxSphere;
    private StringBuilder hoseCode;

    /** HOSE code of {@code root} within {@code atoms} (neighbours via {@code nodeTable}, visited by {@code number}). */
    public String getHOSECode(HeavyAtom[] atoms, HeavyAtom root, int maxSphere) {
        this.setOfNodes = atoms;
        this.maxSphere = maxSphere;
        this.visited = new boolean[atoms.length];
        this.visited[root.number] = true;
        this.hoseCode = new StringBuilder();
        breadthFirstSearch(root);
        fillUpSphereDelimiters();
        while (hoseCode.charAt(0) == 'H') hoseCode.deleteCharAt(0);
        return hoseCode.toString();
    }

    private void breadthFirstSearch(HeavyAtom root) {
        sphere = 0;
        List<TreeNode> sphereNodes = new ArrayList<>();
        for (int k = 0; k < root.degree; k++) {
            HeavyAtom nb = root.nodeTable[k];
            sphereNodes.add(new TreeNode(root.nodeTable[k].symbol, root, nb, root.bondTable[k], 0L));
            visited[nb.number] = true;
        }
        nextSphere(sphereNodes);
    }

    private void nextSphere(List<TreeNode> nodes) {
        // legacy recursion; one level per sphere
        while (true) {
            sphere++;
            if (sphere > maxSphere) return;
            hoseCode.append(getSphereCode(nodes));
            List<TreeNode> next = new ArrayList<>();
            for (TreeNode t : nodes) {
                if ("&;#:,".indexOf(t.symbol) >= 0) continue;
                HeavyAtom a = t.number;
                if (a.degree == 1) {
                    next.add(new TreeNode(",", t.number, null, 0, t.score * 1000000L));
                } else {
                    for (int k = 0; k < a.degree; k++) {
                        HeavyAtom to = a.nodeTable[k];
                        if (!visited[to.number]) {
                            next.add(new TreeNode(setOfNodes[to.number].symbol, t.number, to, a.bondTable[k], t.score * 1000000L));
                            visited[to.number] = true;
                        } else if (to != t.source) {
                            next.add(new TreeNode("&", t.number, to, a.bondTable[k], t.score * 1000000L));
                        }
                    }
                }
            }
            nodes = next;
        }
    }

    private String getSphereCode(List<TreeNode> nodes) {
        if (nodes == null || nodes.size() < 1) return "";
        boolean[] done = new boolean[nodes.size()];
        StringBuilder code = new StringBuilder();
        calculateNodeScores(nodes);
        sortNodes(nodes);
        HeavyAtom source = nodes.get(0).source;
        int start = 0;
        HeavyAtom nextSource;
        do {
            nextSource = null;
            for (int i = start; i < nodes.size(); i++) {
                TreeNode t = nodes.get(i);
                if (t.source == source && !done[i]) {
                    if (t.bondType <= 4) code.append(BOND_SYMBOLS[t.bondType]);
                    code.append(getElementSymbol(t.symbol));
                    done[i] = true;
                }
                if (t.source != source && nextSource == null && !done[i]) nextSource = t.source;
            }
            if (nextSource != null) {
                code.append(",");
                source = nextSource;
                start++;
            }
        } while (nextSource != null);
        code.append(SPHERE_DELIMITERS[sphere - 1]);
        return code.toString();
    }

    private static void calculateNodeScores(List<TreeNode> nodes) {
        for (TreeNode t : nodes) {
            t.score = t.score + getElementRank(t.symbol);
            if (t.bondType <= 4) t.score = t.score + BOND_RANKINGS[t.bondType];
        }
    }

    /** Stable bubble sort, descending (moves an element only when the next one is strictly greater). */
    private static void sortNodes(List<TreeNode> nodes) {
        boolean changed;
        do {
            changed = false;
            for (int i = 0; i < nodes.size() - 1; i++) {
                if (nodes.get(i + 1).score > nodes.get(i).score) {
                    TreeNode t = nodes.remove(i + 1);
                    nodes.add(i, t);
                    changed = true;
                }
            }
        } while (changed);
    }

    private static int getElementRank(String symbol) {
        for (int i = 0; i < RANKED_SYMBOLS.length; i++) {
            if (RANKED_SYMBOLS[i].equals(symbol)) return SYMBOL_RANKINGS[i];
        }
        StandardIsotopes.Isotope iso = StandardIsotopes.getMajorIsotope(symbol);
        if (iso == null) throw new NullPointerException("HOSECodeGenerator: no major isotope for symbol '" + symbol + "'");
        return 800000 - iso.atomicMass();
    }

    private static String getElementSymbol(String symbol) {
        if (symbol.equals("Si")) return "Q";
        if (symbol.equals("Cl")) return "X";
        if (symbol.equals("Br")) return "Y";
        return symbol.equals(",") ? "" : symbol;
    }

    private void fillUpSphereDelimiters() {
        for (int s = sphere; s < 5; s++) hoseCode.append(SPHERE_DELIMITERS[s - 1]);
    }
}
