package io.github.steinbeck.seneca.chem;

import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IBond;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesParser;
import org.openscience.cdk.tools.manipulator.AtomContainerManipulator;

import io.github.steinbeck.seneca.golden.EchoGolden;
import io.github.steinbeck.seneca.golden.GoldenFiles;
import io.github.steinbeck.seneca.golden.GoldenFixtures;
import io.github.steinbeck.seneca.golden.Hashes;
import io.github.steinbeck.seneca.golden.RunGolden;

import java.util.ArrayList;
import java.util.List;

/** Test structures for the chem and judges unit tests: SMILES, constructed graphs, golden crash candidates. */
public final class Structures {

    private Structures() {
    }

    /** A structure: atoms with bonds synchronized from {@code bm}. */
    public record S(String name, HeavyAtom[] atoms, int[][] bm) {
    }

    public static S of(String name, String[] sym, int[] hc, int[][] bm) {
        HeavyAtom[] a = new HeavyAtom[sym.length];
        for (int i = 0; i < a.length; i++) {
            a[i] = new HeavyAtom(sym[i], i);
            a[i].hCount = hc[i];
            a[i].maxBondOrder = StandardElements.getMaxBondOrder(sym[i]);
            a[i].maxBondOrderSum = StandardElements.getMaxBondOrderSum(sym[i]);
        }
        AtomSets.synchronizeBonds(a, bm);
        return new S(name, a, bm);
    }

    public static S smiles(String smiles) {
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
            return of(smiles, sym, hc, bm);
        } catch (Exception e) {
            throw new IllegalStateException(smiles, e);
        }
    }

    /** K5 of carbons: every atom has degree 4 (ring finder failure mode 3, the legacy hang). */
    public static S k5() {
        int[][] bm = new int[5][5];
        for (int i = 0; i < 5; i++) for (int j = 0; j < 5; j++) if (i != j) bm[i][j] = 1;
        return of("K5", new String[] {"C", "C", "C", "C", "C"}, new int[5], bm);
    }

    /** Atom 0 bridges three K4 units: a degree-3 atom on no cycle (ring finder failure mode 2). */
    public static S threeK4() {
        int n = 13;
        int[][] bm = new int[n][n];
        for (int k = 0; k < 3; k++) {
            int base = 1 + 4 * k;
            for (int i = base; i < base + 4; i++) for (int j = base; j < base + 4; j++) if (i != j) bm[i][j] = 1;
            bm[0][base] = bm[base][0] = 1;
        }
        String[] sym = new String[n];
        java.util.Arrays.fill(sym, "C");
        int[] hc = new int[n];
        for (int i = 0; i < n; i++) hc[i] = (i == 1 || i == 5 || i == 9) ? 0 : 1;
        return of("3xK4", sym, hc, bm);
    }

    /** The 13 golden crash candidates (ring finder failure mode 1), atoms of the configured dataset. */
    public static List<S> crashCandidates() {
        List<S> l = new ArrayList<>();
        for (GoldenFiles.RunId id : GoldenFiles.runs()) {
            RunGolden g = RunGolden.load(id);
            if (!g.record.has("crash")) continue;
            HeavyAtom[] atoms = GoldenFixtures.atoms(EchoGolden.load(id.key()));
            int[][] bm = Hashes.matrixFromCt(atoms.length, g.record.obj("crash").arr("candidateCt").intRows());
            HeavyAtom[] a = AtomSets.cloneAtomSet(atoms);
            AtomSets.synchronizeBonds(a, bm);
            l.add(new S(id.toString(), a, bm));
        }
        return l;
    }
}
