package io.github.steinbeck.seneca.structgen;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.chem.StandardElements;
import io.github.steinbeck.seneca.golden.GoldenFiles;
import io.github.steinbeck.seneca.golden.GoldenFixtures;
import io.github.steinbeck.seneca.judges.Attic;
import io.github.steinbeck.seneca.structgen.StartStructureFeasibility.Verdict;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link StartStructureFeasibility} (DV-60): known cases, the golden inputs, and a cross-check of both
 * decision steps against each other and against the real start generator on random small inputs.
 */
@Tag("C")
class StartStructureFeasibilityTest {

    static HeavyAtom atom(String symbol, int number, int h) {
        HeavyAtom a = new HeavyAtom(symbol, number);
        a.hCount = h;
        a.maxBondOrder = StandardElements.getMaxBondOrder(symbol);
        a.maxBondOrderSum = StandardElements.getMaxBondOrderSum(symbol);
        return a;
    }

    @Test
    void knownCases() {
        // acetone and acetic acid need C=O, which the generator never forms
        assertEquals(Verdict.IMPOSSIBLE, StartStructureFeasibility.of(new HeavyAtom[]{
                atom("C", 0, 3), atom("C", 1, 0), atom("C", 2, 3), atom("O", 3, 0)}));
        assertEquals(Verdict.IMPOSSIBLE, StartStructureFeasibility.of(new HeavyAtom[]{
                atom("C", 0, 3), atom("C", 1, 0), atom("O", 2, 0), atom("O", 3, 1)}));
        // tetramethylethylene: the C=C between the two quaternary carbons is reachable
        assertEquals(Verdict.POSSIBLE, StartStructureFeasibility.of(new HeavyAtom[]{
                atom("C", 0, 3), atom("C", 1, 3), atom("C", 2, 0), atom("C", 3, 0), atom("C", 4, 3), atom("C", 5, 3)}));
        // benzene CH x 6: a 3-regular simple graph (prism) exists
        HeavyAtom[] benzene = new HeavyAtom[6];
        for (int i = 0; i < 6; i++) benzene[i] = atom("C", i, 1);
        assertEquals(Verdict.POSSIBLE, StartStructureFeasibility.of(benzene));
        // odd sum, too many hydrogens, single atoms
        assertEquals(Verdict.IMPOSSIBLE, StartStructureFeasibility.of(new HeavyAtom[]{
                atom("C", 0, 3), atom("C", 1, 2), atom("C", 2, 2), atom("C", 3, 2)}));
        assertEquals(Verdict.IMPOSSIBLE, StartStructureFeasibility.of(new HeavyAtom[]{atom("O", 0, 3), atom("C", 1, 3)}));
        assertEquals(Verdict.POSSIBLE, StartStructureFeasibility.of(new HeavyAtom[]{atom("C", 0, 4)}));
        assertEquals(Verdict.IMPOSSIBLE, StartStructureFeasibility.of(new HeavyAtom[]{atom("C", 0, 3)}));
    }

    @Test
    void everyGoldenInputIsPossible() {
        for (String key : GoldenFiles.keys()) {
            for (String profile : List.of("gui", "paper", "all", "manual")) {
                assertEquals(Verdict.POSSIBLE, StartStructureFeasibility.of(GoldenFixtures.runInput(key, profile).atoms()), key + "/" + profile);
            }
        }
    }

    /**
     * Random molecules of 4 to 7 atoms (C, N, O with random H counts): the single-bond criterion never
     * contradicts the exhaustive search, an IMPOSSIBLE verdict is never contradicted by the generator,
     * and the generator finds a start structure for every POSSIBLE one.
     */
    @Test
    void verdictsAgreeWithTheGenerator() {
        Random rnd = new Random(2001);
        String[] el = {"C", "C", "C", "N", "O"};
        int possible = 0, impossible = 0, needDouble = 0;
        for (int t = 0; t < 1500; t++) {
            int n = 4 + rnd.nextInt(4);
            HeavyAtom[] atoms = new HeavyAtom[n];
            for (int i = 0; i < n; i++) {
                String s = el[rnd.nextInt(el.length)];
                int v = StandardElements.getMaxBondOrderSum(s);
                atoms[i] = atom(s, i, rnd.nextInt(v));       // 1 .. v free valences
            }
            Verdict fast = StartStructureFeasibility.of(atoms);
            Verdict full = StartStructureFeasibility.exhaustive(atoms, 5_000_000);
            assertTrue(full != Verdict.UNKNOWN, "budget");
            assertEquals(full, fast, "trial " + t + ": " + describe(atoms));
            int[] restarts = {0};
            if (full == Verdict.POSSIBLE) {
                possible++;
                HeavyAtom[] start = SingleRandomStructureGenerator.generate(atoms, new Attic(), RunRandom.seeded(t),
                        () -> ++restarts[0] > 200_000);
                assertNotNull(start, "trial " + t + ": generator found nothing for a POSSIBLE input " + describe(atoms));
                if (!singleBondsOnly(atoms)) needDouble++;
            } else {
                impossible++;
                HeavyAtom[] start = SingleRandomStructureGenerator.generate(atoms, new Attic(), RunRandom.seeded(t),
                        () -> ++restarts[0] > 2_000);
                assertNull(start, "trial " + t + ": generator built an IMPOSSIBLE input " + describe(atoms));
            }
        }
        System.out.println("[feasibility] possible " + possible + " (needing a double bond: " + needDouble + "), impossible " + impossible);
        assertTrue(possible > 100 && impossible > 100 && needDouble > 10);
    }

    /**
     * DV-64: {@link StartStructureFeasibility#witness} gives a saturated connected structure for every
     * POSSIBLE input (both construction paths) and null for every IMPOSSIBLE one; random molecules of 4
     * to 7 atoms and some of up to 30 atoms (C, N, O, F, Cl, S with random H counts) plus perfluoroalkanes.
     */
    @Test
    void witnessIsASaturatedConnectedStructureExactlyForPossibleInputs() {
        Random rnd = new Random(64);
        String[] el = {"C", "C", "C", "C", "N", "O", "F", "Cl", "S"};
        int possible = 0, viaSearch = 0;
        for (int t = 0; t < 2300; t++) {
            int n = 4 + rnd.nextInt(t < 2000 ? 4 : 27);
            HeavyAtom[] atoms = new HeavyAtom[n];
            for (int i = 0; i < n; i++) {
                String s = el[rnd.nextInt(el.length)];
                int v = StandardElements.getMaxBondOrderSum(s);
                atoms[i] = atom(s, i, rnd.nextInt(v));
            }
            Verdict verdict = StartStructureFeasibility.of(atoms);
            int[][] w = StartStructureFeasibility.witness(atoms);
            if (verdict == Verdict.POSSIBLE) {
                possible++;
                if (!singleBondsOnly(atoms)) viaSearch++;
                assertNotNull(w, "trial " + t + ": no witness for a POSSIBLE input " + describe(atoms));
                HeavyAtom[] built = SingleRandomStructureGenerator.fromBondMatrix(atoms, new Attic(), w);   // throws if invalid
                assertEquals(n, built.length);
            } else if (verdict == Verdict.IMPOSSIBLE) {
                assertNull(w, "trial " + t + ": witness for an IMPOSSIBLE input " + describe(atoms));
            }
        }
        for (int c = 1; c <= 12; c++) {
            HeavyAtom[] pf = new HeavyAtom[3 * c + 2];
            for (int i = 0; i < c; i++) pf[i] = atom("C", i, 0);
            for (int i = c; i < pf.length; i++) pf[i] = atom("F", i, 0);
            SingleRandomStructureGenerator.fromBondMatrix(pf, new Attic(), StartStructureFeasibility.witness(pf));
        }
        System.out.println("[witness] possible " + possible + " (via the search: " + viaSearch + ")");
        assertTrue(possible > 500 && viaSearch > 10, possible + " / " + viaSearch);
    }

    private static boolean singleBondsOnly(HeavyAtom[] atoms) {
        int n = atoms.length;
        int[] d = new int[n];
        long sum = 0;
        for (int i = 0; i < n; i++) {
            d[i] = atoms[i].maxBondOrderSum - atoms[i].hCount;
            if (d[i] <= 0) return false;
            sum += d[i];
        }
        return sum >= 2L * (n - 1) && StartStructureFeasibility.graphical(d);
    }

    private static String describe(HeavyAtom[] atoms) {
        List<String> l = new ArrayList<>();
        for (HeavyAtom a : atoms) l.add(a.symbol + "H" + a.hCount);
        return l.toString();
    }
}
