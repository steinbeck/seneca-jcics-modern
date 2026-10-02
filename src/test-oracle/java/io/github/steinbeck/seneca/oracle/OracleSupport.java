package io.github.steinbeck.seneca.oracle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;

import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IBond;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesParser;
import org.openscience.cdk.tools.manipulator.AtomContainerManipulator;

import compchem.Atom;
import compchem.DataStructureTools;
import compchem.StandardElements;
import io.github.steinbeck.seneca.chem.AtomSets;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.golden.EchoGolden;
import io.github.steinbeck.seneca.golden.GoldenFiles;
import io.github.steinbeck.seneca.golden.GoldenFixtures;
import io.github.steinbeck.seneca.golden.Hashes;
import io.github.steinbeck.seneca.golden.RunGolden;
import io.github.steinbeck.seneca.golden.ScoresGolden;
import io.github.steinbeck.seneca.golden.JsonValue;
import io.github.steinbeck.seneca.golden.JsonValue.JObject;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Shared inputs of the differential (oracle) tests: structures in both representations, the random-walk
 * isomer generator of {@code docs/legacy/harness/LegacyVsCdk.java} (16 seed molecules, Faulon moves with a
 * seeded {@link Random}, every move accepted) and structure sets from the golden master (score
 * structures, annealed result structures of the complete runs, the 13 ring-finder crash candidates and
 * random walks starting from them).
 */
final class OracleSupport {

    private OracleSupport() {
    }

    /** The seed molecules of LegacyVsCdk (Kekulé SMILES; all elements of SENECA's valence table but X). */
    static final String[] SEEDS = {
        "CC1(C)C2CCC1(C)C(=O)C2",                       // camphor
        "CC1=CCC2CC1C2(C)C",                            // alpha-pinene
        "CC(C)C1CCC(C)CC1O",                            // menthol
        "C12C3C4C1C5C2C3C45",                           // cubane
        "C1C2CC3CC1CC(C2)C3",                           // adamantane
        "CN1CCCC1C1=CC=CN=C1",                          // nicotine
        "CN1C=NC2=C1C(=O)N(C)C(=O)N2C",                 // caffeine
        "CC(CCC=C(C)C)C1CCC2(C)C1(C)CCC1C2CCC2C(C)(C)C(O)CCC12C", // lanostane-type C30H52O
        "CSC1=CC=C(Cl)C=C1Br",
        "C[Si](C)(C)OC1CCCCC1",
        "CCP(CC)CC1CC1",
        "ICC1CC1C#CC",
        "FC(F)C1CC1N=O",
        "O=C1OC2=CC=CC=C2C=C1CC(O)C",
        "CC1=CC(=O)OC2=C1C=CC(O)=C2C(=O)CC",
        "CCCCC1OC1C(O)CC#CC#CC",
    };

    /** A labelled structure: symbols, H counts, symmetric bond matrix. */
    record Struct(String name, String[] sym, int[] hc, int[][] bm) {
        Atom[] legacy() {
            return legacyAtoms(sym, hc, bm);
        }

        HeavyAtom[] port() {
            return portAtoms(sym, hc, bm);
        }
    }

    static Atom[] legacyAtoms(String[] sym, int[] hc, int[][] bm) {
        StandardElements se = new StandardElements();
        Atom[] a = new Atom[sym.length];
        for (int i = 0; i < a.length; i++) {
            a[i] = new Atom(sym[i], i);
            a[i].HCount = hc[i];
            a[i].maxBondOrder = se.getMaxBondOrder(sym[i]);
            a[i].maxBondOrderSum = se.getMaxBondOrderSum(sym[i]);
        }
        DataStructureTools.synchronizeBonds(a, bm);
        return a;
    }

    static HeavyAtom[] portAtoms(String[] sym, int[] hc, int[][] bm) {
        HeavyAtom[] a = new HeavyAtom[sym.length];
        for (int i = 0; i < a.length; i++) {
            a[i] = new HeavyAtom(sym[i], i);
            a[i].hCount = hc[i];
            a[i].maxBondOrder = io.github.steinbeck.seneca.chem.StandardElements.getMaxBondOrder(sym[i]);
            a[i].maxBondOrderSum = io.github.steinbeck.seneca.chem.StandardElements.getMaxBondOrderSum(sym[i]);
        }
        AtomSets.synchronizeBonds(a, bm);
        return a;
    }

    static Struct fromSmiles(String smiles) {
        try {
            IAtomContainer m = new SmilesParser(SilentChemObjectBuilder.getInstance()).parseSmiles(smiles);
            AtomContainerManipulator.suppressHydrogens(m);
            int n = m.getAtomCount();
            String[] sym = new String[n];
            int[] hc = new int[n];
            for (int i = 0; i < n; i++) {
                sym[i] = m.getAtom(i).getSymbol();
                hc[i] = m.getAtom(i).getImplicitHydrogenCount();
            }
            int[][] bm = new int[n][n];
            for (IBond b : m.bonds()) {
                int u = m.indexOf(b.getBegin()), v = m.indexOf(b.getEnd());
                bm[u][v] = bm[v][u] = b.getOrder().numeric();
            }
            return new Struct(smiles, sym, hc, bm);
        } catch (Exception e) {
            throw new IllegalStateException(smiles, e);
        }
    }

    /** Dataset atoms of a golden key as symbol/H arrays. */
    static Struct datasetStruct(String key, String name, int[][] bm) {
        HeavyAtom[] atoms = GoldenFixtures.atoms(EchoGolden.load(key));
        String[] sym = new String[atoms.length];
        int[] hc = new int[atoms.length];
        for (int i = 0; i < atoms.length; i++) {
            sym[i] = atoms[i].symbol;
            hc[i] = atoms[i].hCount;
        }
        return new Struct(name, sym, hc, bm);
    }

    static int[][] clone(int[][] m) {
        int[][] r = new int[m.length][];
        for (int i = 0; i < m.length; i++) r[i] = m[i].clone();
        return r;
    }

    /** Verbatim copy of {@code StochasticGenerator.mutate} (Faulon 1996) with an explicit random source. */
    static void mutate(int[][] bm, Random rng) {
        int x1, x2, y1, y2, a11, a12, a22, a21, b11, min, max;
        int n = bm.length;
        do {
            do {
                x1 = (int) (rng.nextDouble() * n);
                x2 = (int) (rng.nextDouble() * n);
                y1 = (int) (rng.nextDouble() * n);
                y2 = (int) (rng.nextDouble() * n);
            } while (!(x1 != x2 && x1 != y1 && x1 != y2 && x2 != y1 && x2 != y2 && y1 != y2));
            a11 = bm[x1][y1];
            a12 = bm[x1][y2];
            a22 = bm[x2][y2];
            a21 = bm[x2][y1];
            min = Math.max(Math.max(0, a11 - a22), Math.max(a11 + a12 - 3, a11 + a21 - 3));
            max = Math.min(Math.min(3, a11 + a12), Math.min(a11 + a21, a11 - a22 + 3));
            b11 = a11;
            if (max - min > 0 || a11 != min) {
                do {
                    b11 = ((int) (rng.nextDouble() * (max - min + 1))) + min;
                } while (a11 == b11);
            }
        } while (!(b11 != a11 && b11 >= min && b11 <= max));
        bm[x1][y1] = b11;
        bm[x1][y2] = a11 + a12 - b11;
        bm[x2][y2] = a22 - a11 + b11;
        bm[x2][y1] = a11 + a21 - b11;
        bm[y1][x1] = b11;
        bm[y2][x1] = a11 + a12 - b11;
        bm[y2][x2] = a22 - a11 + b11;
        bm[y1][x2] = a11 + a21 - b11;
    }

    static boolean connected(int[][] bm) {
        int n = bm.length;
        boolean[] seen = new boolean[n];
        int[] stack = new int[n];
        int sp = 0, count = 1;
        stack[sp++] = 0;
        seen[0] = true;
        while (sp > 0) {
            int v = stack[--sp];
            for (int w = 0; w < n; w++) {
                if (bm[v][w] > 0 && !seen[w]) {
                    seen[w] = true;
                    count++;
                    stack[sp++] = w;
                }
            }
        }
        return count == n;
    }

    /**
     * Random walk of {@code steps} Faulon moves from {@code start}, every move accepted (as LegacyVsCdk);
     * returns the connected structures visited (disconnected ones are returned too if {@code withDisconnected}).
     */
    static List<Struct> randomWalk(Struct start, int steps, Random rng, boolean withDisconnected) {
        List<Struct> l = new ArrayList<>();
        int[][] bm = start.bm();
        for (int s = 0; s < steps; s++) {
            int[][] nb = clone(bm);
            mutate(nb, rng);
            bm = nb;
            if (withDisconnected || connected(nb)) l.add(new Struct(start.name() + " step " + s, start.sym(), start.hc(), nb));
        }
        return l;
    }

    /** The 50 golden score structures of {@code key}. */
    static List<Struct> scoreStructures(String key) {
        List<Struct> l = new ArrayList<>();
        int n = GoldenFixtures.atoms(EchoGolden.load(key)).length;
        for (JObject s : ScoresGolden.load(key).structures())
            l.add(datasetStruct(key, key + " score k=" + s.integer("k"), Hashes.matrixFromCt(n, s.arr("ct").intRows())));
        return l;
    }

    /** Annealed structures: every stored result of every complete golden run of {@code key}, plus crash candidates. */
    static List<Struct> runStructures(String key) {
        List<Struct> l = new ArrayList<>();
        int n = GoldenFixtures.atoms(EchoGolden.load(key)).length;
        for (GoldenFiles.RunId id : GoldenFiles.runs()) {
            if (!id.key().equals(key)) continue;
            RunGolden g = RunGolden.load(id);
            if (g.record.has("results")) {
                for (JsonValue v : g.record.arr("results").items()) {
                    JObject r = v.asObject();
                    l.add(datasetStruct(key, id + " result " + r.integer("i"), Hashes.matrixFromCt(n, r.arr("ct").intRows())));
                }
            }
            if (g.record.has("crash"))
                l.add(datasetStruct(key, id + " crash", Hashes.matrixFromCt(n, g.record.obj("crash").arr("candidateCt").intRows())));
        }
        return l;
    }

    /** The crash candidates of the golden runs (13). */
    static List<Struct> crashCandidates() {
        List<Struct> l = new ArrayList<>();
        for (GoldenFiles.RunId id : GoldenFiles.runs()) {
            RunGolden g = RunGolden.load(id);
            if (!g.record.has("crash")) continue;
            int n = GoldenFixtures.atoms(EchoGolden.load(id.key())).length;
            l.add(datasetStruct(id.key(), id + " crash", Hashes.matrixFromCt(n, g.record.obj("crash").arr("candidateCt").intRows())));
        }
        return l;
    }

    /**
     * Result of a call: the value's string or "exception class: message". For a
     * {@link NullPointerException} only the class (the JDK's helpful message names local variables,
     * which differ between the legacy bytecode and the port).
     */
    static String outcome(Callable<?> c) {
        try {
            return String.valueOf(c.call());
        } catch (NullPointerException e) {
            return e.getClass().getName();
        } catch (Exception e) {
            return e.getClass().getName() + ": " + e.getMessage();
        }
    }

    /** Counts checks and mismatches by name; {@link #assertNone()} fails with up to 3 examples each. */
    static final class Tally {
        final Map<String, long[]> counts = new LinkedHashMap<>();
        final Map<String, List<String>> examples = new LinkedHashMap<>();

        void check(String what, boolean mismatch, String example) {
            long[] c = counts.computeIfAbsent(what, k -> new long[2]);
            c[0]++;
            if (mismatch) {
                c[1]++;
                List<String> l = examples.computeIfAbsent(what, k -> new ArrayList<>());
                if (l.size() < 3) l.add(example);
            }
        }

        void same(String what, Object legacy, Object port, String where) {
            boolean eq = legacy == null ? port == null : legacy.equals(port);
            check(what, !eq, where + ": legacy=" + legacy + " port=" + port);
        }

        long checks(String what) {
            long[] c = counts.get(what);
            return c == null ? 0 : c[0];
        }

        void assertNone() {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, long[]> e : counts.entrySet()) {
                if (e.getValue()[1] > 0) {
                    sb.append(e.getKey()).append(": ").append(e.getValue()[1]).append(" of ").append(e.getValue()[0]).append(" differ\n");
                    for (String x : examples.get(e.getKey())) sb.append("    e.g. ").append(x).append('\n');
                }
            }
            if (sb.length() > 0) fail(sb.toString());
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, long[]> e : counts.entrySet())
                sb.append(String.format("%-50s %9d checked %6d differ%n", e.getKey(), e.getValue()[0], e.getValue()[1]));
            return sb.toString();
        }
    }
}
