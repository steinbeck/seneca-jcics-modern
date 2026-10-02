package io.github.steinbeck.seneca.judges;

import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.chem.AtomSets;
import io.github.steinbeck.seneca.chem.CdkBridge;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.chem.RingFinder;
import io.github.steinbeck.seneca.chem.Structures;
import io.github.steinbeck.seneca.chem.Structures.S;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests of the judges: the legacy quirks that are kept on purpose, the DEFAULT ring-finder fallback
 * (DEVIATIONS.md DV-01..03) and the deep-copy semantics of {@link Judge#copy()}. Exact agreement with the
 * legacy judges is shown by the golden tests and by {@code oracle/JudgeOracleTest}.
 */
@Tag("A")
class JudgesTest {

    static Attic attic(HeavyAtom[] atoms, LegacyCompat compat) {
        Attic a = new Attic();
        a.atomSet = AtomSets.cloneAtomSet(atoms);
        a.compat = compat;
        return a;
    }

    static FeatureJudge featureJudge() {
        FeatureJudge f = new FeatureJudge();
        f.addRingSizeScore(3, -500);
        f.addRingSizeScore(4, -300);
        f.setHetHetPenaltyEnabled(true);
        f.setEnabled(true);
        return f;
    }

    static int expectedCdkRingScore(S s) {
        int score = 0;
        for (int size : CdkBridge.sssrRingSizes(s.atoms(), s.bm())) {
            if (size == 3) score -= 500;
            if (size == 4) score -= 300;
        }
        return score;
    }

    @Test
    void featureJudgeStrictCrashesAndDefaultFallsBack() {
        for (S c : Structures.crashCandidates()) {
            FeatureJudge strict = featureJudge();
            FeatureJudge dflt = featureJudge();
            new JudgeSet(List.of(strict)).initJudges(attic(c.atoms(), LegacyCompat.STRICT));
            new JudgeSet(List.of(dflt)).initJudges(attic(c.atoms(), LegacyCompat.DEFAULT));
            AtomSets.synchronizeBonds(strict.getAttic().atomSet, c.bm());
            AtomSets.synchronizeBonds(dflt.getAttic().atomSet, c.bm());
            ArrayIndexOutOfBoundsException e = assertThrows(ArrayIndexOutOfBoundsException.class, () -> strict.evaluate(c.bm()), c.name());
            assertEquals("Index -1 out of bounds for length 10", e.getMessage());
            JudgeResult r = dflt.evaluate(c.bm());
            assertTrue(r.scoreDescription.startsWith("<p>FeatureJudge->RingScore: " + expectedCdkRingScore(c) + ". "), r.scoreDescription);
        }
    }

    @Test
    void featureJudgeHangAndDegree3Failure() {
        for (S s : List.of(Structures.k5(), Structures.threeK4())) {
            FeatureJudge strict = featureJudge();
            FeatureJudge dflt = featureJudge();
            new JudgeSet(List.of(strict)).initJudges(attic(s.atoms(), LegacyCompat.STRICT));
            new JudgeSet(List.of(dflt)).initJudges(attic(s.atoms(), LegacyCompat.DEFAULT));
            AtomSets.synchronizeBonds(strict.getAttic().atomSet, s.bm());
            AtomSets.synchronizeBonds(dflt.getAttic().atomSet, s.bm());
            RuntimeException e = assertThrows(RuntimeException.class, () -> strict.evaluate(s.bm()));
            assertTrue(e instanceof RingFinder.HangDetected || e instanceof ArrayIndexOutOfBoundsException, e.toString());
            assertEquals(expectedCdkRingScore(s), dflt.evaluate(s.bm()).score, s.name());
        }
    }

    @Test
    void featureJudgeDefaultEqualsStrictWhereLegacyWorks() {
        for (String smi : new String[] {"C1CC1CC1CCC1O", "C12C3C1C3C4C5C2C54", "OOC1CC=CC1N", "CCCCO"}) {
            S s = Structures.smiles(smi);
            FeatureJudge strict = featureJudge();
            FeatureJudge dflt = featureJudge();
            new JudgeSet(List.of(strict)).initJudges(attic(s.atoms(), LegacyCompat.STRICT));
            new JudgeSet(List.of(dflt)).initJudges(attic(s.atoms(), LegacyCompat.DEFAULT));
            AtomSets.synchronizeBonds(strict.getAttic().atomSet, s.bm());
            AtomSets.synchronizeBonds(dflt.getAttic().atomSet, s.bm());
            JudgeResult a = strict.evaluate(s.bm()), b = dflt.evaluate(s.bm());
            assertEquals(a.score, b.score, smi);
            assertEquals(a.scoreDescription, b.scoreDescription, smi);
        }
        // legacy ring finder, not CDK: [3,3,3,4,6] instead of CDK's [3,3,3,3,6]
        S s = Structures.smiles("C12C3C1C3C4C5C2C54");
        FeatureJudge f = featureJudge();
        new JudgeSet(List.of(f)).initJudges(attic(s.atoms(), LegacyCompat.DEFAULT));
        AtomSets.synchronizeBonds(f.getAttic().atomSet, s.bm());
        assertEquals(-1800, f.evaluate(s.bm()).score);
    }

    @Test
    void featureJudgeQuirks() {
        FeatureJudge f = new FeatureJudge();
        f.addRingSizeScore(3, -500);
        f.addRingSizeScore(5, 3);
        f.addRingSizeScore(6, 0); // score 0 is not stored
        assertEquals(2, f.ringSizeScores.size());
        f.calcMaxScore();
        assertEquals(0, f.getMaxScore(), "Q3: calcMaxScore before init() gives 0");
        // getRingSizeScore tests the values (Hashtable.contains)
        assertEquals(-500, f.getRingSizeScore(3));
        assertEquals(0, f.getRingSizeScore(5));
        f.addRingSizeScore(4, 7);
        assertThrows(NullPointerException.class, () -> f.getRingSizeScore(7));
        f.init();
        f.calcMaxScore();
        assertEquals(-490, f.getMaxScore());
    }

    @Test
    void hybridizationAndHeteroBondsInitWipeConfiguration() {
        S s = Structures.smiles("CC(=O)OC");
        HybridizationJudge h = new HybridizationJudge();
        h.initScoreArray(5);
        h.addHybridizationScore(2, HybridizationJudge.SP2, 1.0);
        h.addHybridizationScore(1, HybridizationJudge.SP3, 0.5);
        h.setEnabled(true);
        h.calcMaxScore();
        assertEquals(150, h.getMaxScore());
        HeteroBondsJudge hb = new HeteroBondsJudge();
        hb.isHeteroBonded = new boolean[] {false, true, false, false, true};
        hb.setEnabled(true);
        hb.calcMaxScore();
        assertEquals(0, hb.getMaxScore(), "Q4: the base field");
        assertEquals(200, hb.getPrivateMaxScore());
        JudgeSet js = new JudgeSet(List.of(h, hb));
        assertEquals(150, js.maxScore());
        Attic a = attic(s.atoms(), LegacyCompat.STRICT);
        js.initJudges(a);
        JudgeSet.Breakdown b = js.scoreStructure(a, s.bm());
        assertEquals(0, b.total, "D1: init() wiped both configurations");
        assertEquals("<p>0 from a possible 150 points for hybridization state were given.</p>", b.results.get(0).scoreDescription);
        assertEquals(150, b.results.get(0).maxScore);
        assertEquals("<p>0 of 2 carbons assigned to be heterobonded are indeed hetero-bonded in the structure.</p>", b.results.get(1).scoreDescription);
        assertEquals(200, b.results.get(1).maxScore, "private maxScore in the result");
    }

    @Test
    void symmetryJudge() {
        S s = Structures.smiles("CC(C)(C)O");
        SymmetryJudge j = new SymmetryJudge();
        j.addSymmetryClass(new boolean[] {true, false, true, true, false});
        j.addSymmetryClass(new boolean[] {true, true, false, false, false});
        assertEquals(2, j.getClassMember(0, 2));
        assertEquals(-1, j.getClassMember(0, 4));
        j.setEnabled(true);
        j.calcMaxScore();
        assertEquals(3 + 1, j.getMaxScore(), "D2: sum of k!/2");
        Attic a = attic(s.atoms(), LegacyCompat.STRICT);
        new JudgeSet(List.of(j)).initJudges(a);
        AtomSets.synchronizeBonds(a.atomSet, s.bm());
        JudgeResult r = j.evaluate(s.bm());
        // class 2 pairs the methyl 0 with the quaternary carbon 1: after 5 Morgan iterations both are 64
        assertEquals(2000, r.score);
        assertEquals("4 symmetry classes are completly filled.", r.scoreDescription);
    }

    @Test
    void twoDJudgeAndHoseJudgeTexts() {
        S s = Structures.smiles("CC(=O)CC");
        HMBCJudge h = new HMBCJudge();
        assertEquals(new JudgeResult(0, 0, 0, "").score, h.evaluate(s.bm()).score);
        assertEquals("<p>No signals available for HMBCJudge</p>", h.evaluate(s.bm()).scoreDescription);
        h.assignment = new boolean[5][5][5];
        h.assignment[0][3][3] = true;
        h.assignment[0][4][4] = true;
        h.setEnabled(true);
        h.calcMaxScore();
        assertEquals(200, h.getMaxScore());
        HOSECodeJudge hose = new HOSECodeJudge();
        hose.setCarbonShifts(new double[] {30.0, 209.0, 36.0, 8.0});
        hose.setEnabled(true);
        hose.calcMaxScore();
        Attic a = attic(s.atoms(), LegacyCompat.STRICT);
        JudgeSet js = new JudgeSet(List.of(h, hose));
        js.initJudges(a);
        JudgeSet.Breakdown b = js.scoreStructure(a, s.bm());
        assertEquals(3, h.getCutoff());
        assertEquals("<p>2/2 Signals satisfied in HMBCJudge. Score 105/200</p>", b.results.get(0).scoreDescription, "path 2 -> 100, path 3 -> 5");
        // carbonShifts has 4 entries for 4 carbons, indexed by atom index: atom 4 (a carbon) is out of range and skipped
        assertTrue(b.results.get(1).scoreDescription.startsWith("<p>Score: "), b.results.get(1).scoreDescription);
        HOSECodeJudge empty = new HOSECodeJudge();
        empty.setCarbonShifts(new double[0]);
        empty.setAttic(a);
        assertEquals("<p>Score: 0/0, Carbon shift medium deveation: NaN</p>", empty.evaluate(s.bm()).scoreDescription);
    }

    @Test
    void hoseJudgeWildcardNeighbour() {
        // C0-X1, C2-C0: the wildcard X has no 2001 major isotope, compchem's HOSE code generator throws
        S s = Structures.of("CCX", new String[] {"C", "X", "C"}, new int[] {2, 0, 3}, new int[][] {{0, 1, 1}, {1, 0, 0}, {1, 0, 0}});
        for (LegacyCompat compat : LegacyCompat.values()) {
            HOSECodeJudge h = new HOSECodeJudge();
            h.setCarbonShifts(new double[] {40.0, 0.0, 20.0});
            h.setEnabled(true);
            h.calcMaxScore();
            Attic a = attic(s.atoms(), compat);
            new JudgeSet(List.of(h)).initJudges(a);
            AtomSets.synchronizeBonds(a.atomSet, s.bm());
            if (compat.strict()) {
                assertThrows(NullPointerException.class, () -> h.evaluate(s.bm()));
            } else {
                // atom 0 is skipped like an unknown code; atom 2 (code "C(//)") is scored as usual
                JudgeResult r = h.evaluate(s.bm());
                assertTrue(r.scoreDescription.startsWith("<p>Score: "), r.scoreDescription);
                assertFalse(r.scoreDescription.contains("NaN"), r.scoreDescription);
            }
        }
    }

    @Test
    void copiesAreDeepAndIndependent() {
        HMBCJudge h = new HMBCJudge();
        h.assignment = new boolean[3][3][3];
        h.assignment[0][1][1] = true;
        h.setEnabled(true);
        h.setJudgeListener(() -> { });
        HMBCJudge hc = (HMBCJudge) h.copy();
        h.assignment[0][2][2] = true;
        h.scores[0] = 1;
        assertFalse(hc.assignment[0][2][2]);
        assertEquals(100, hc.scores[0]);
        assertTrue(hc.getEnabled());
        assertNotSame(h.getAttic(), hc.getAttic());
        assertEquals(null, hc.judgeListener, "listeners are not copied");

        HOSECodeJudge o = new HOSECodeJudge();
        o.setCarbonShifts(new double[] {1, 2});
        HOSECodeJudge oc = (HOSECodeJudge) o.copy();
        o.getCarbonShifts()[0] = 9;
        assertEquals(1.0, oc.getCarbonShifts()[0]);

        SymmetryJudge s = new SymmetryJudge();
        boolean[] cls = {true, true};
        s.addSymmetryClass(cls);
        SymmetryJudge sc = (SymmetryJudge) s.copy();
        cls[1] = false;
        assertTrue(sc.getSymmetryClass(0)[1]);
        assertEquals(sc.getSymmetryClass(0), sc.symmetryClasses[0].symmetricAtoms, "list and init() array share the class objects");

        FeatureJudge f = featureJudge();
        FeatureJudge fc = (FeatureJudge) f.copy();
        f.addRingSizeScore(5, -1);
        assertEquals(2, fc.ringSizeScores.size());
        assertTrue(fc.isHetHetPenaltyEnabled());

        HybridizationJudge y = new HybridizationJudge();
        y.initScoreArray(2);
        HybridizationJudge yc = (HybridizationJudge) y.copy();
        y.addHybridizationScore(1, 0, 1.0);
        assertEquals(0, yc.scores[0][0]);
        assertEquals(100, yc.getMultiplicator());

        HeteroBondsJudge b = new HeteroBondsJudge();
        b.isHeteroBonded = new boolean[] {true};
        b.calcMaxScore();
        HeteroBondsJudge bc = (HeteroBondsJudge) b.copy();
        b.isHeteroBonded[0] = false;
        assertTrue(bc.isHeteroBonded[0]);
        assertEquals(100, bc.getPrivateMaxScore());
        assertEquals(1, bc.getTrueFlagsCount());
    }
}
