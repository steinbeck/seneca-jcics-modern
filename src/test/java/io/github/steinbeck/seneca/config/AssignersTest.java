package io.github.steinbeck.seneca.config;

import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.judges.HHCOSYJudge;
import io.github.steinbeck.seneca.judges.HMBCJudge;
import io.github.steinbeck.seneca.model.NMRSignal;
import io.github.steinbeck.seneca.model.NMRSpectrum;
import io.github.steinbeck.seneca.model.SenecaDataset;
import io.github.steinbeck.seneca.model.SpectrumSlot;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The automatic assigners on synthetic datasets: legacy defects kept (f += g walk, DEPT double count,
 * duplicate candidates, j instead of k, no clearing) and the CarbonShiftAssigner switch points (DV-42).
 * Equality with the 2001 classes on the same inputs: {@code oracle/DatasetIoOracleTest}.
 */
@Tag("B")
class AssignersTest {

    static NMRSignal c(float shift, float intensity, int phase) {
        return new NMRSignal(new String[] {"13C"}, new float[] {shift}, intensity, phase);
    }

    static NMRSignal hc(float h, float c) {
        return new NMRSignal(new String[] {"1H", "13C"}, new float[] {h, c}, 1f, NMRSignal.PHASE_POSITIVE);
    }

    static SenecaDataset dataset(String formula) {
        SenecaDataset sd = new SenecaDataset("t");
        sd.setMolecularFormula(formula);
        sd.init();
        return sd;
    }

    static int[] h(SenecaDataset sd) {
        int[] r = new int[sd.setOfNodes.length];
        for (int i = 0; i < r.length; i++) r[i] = sd.setOfNodes[i].hCount;
        return r;
    }

    static Float[] shifts(SenecaDataset sd) {
        Float[] r = new Float[sd.setOfNodes.length];
        for (int i = 0; i < r.length; i++) r[i] = sd.setOfNodes[i].shift13C;
        return r;
    }

    @Test
    void isopropanol() {
        SenecaDataset sd = dataset("C3H8O");
        sd.spectrum(SpectrumSlot.CARBON1D).addSignal(c(63.0f, 1f, 1));
        sd.spectrum(SpectrumSlot.CARBON1D).addSignal(c(25.0f, 2f, 1));
        sd.spectrum(SpectrumSlot.DEPT90).addSignal(c(63.02f, 1f, NMRSignal.PHASE_POSITIVE));
        sd.spectrum(SpectrumSlot.DEPT135).addSignal(c(62.99f, 1f, NMRSignal.PHASE_POSITIVE));
        sd.spectrum(SpectrumSlot.DEPT135).addSignal(c(25.0f, 1f, NMRSignal.PHASE_POSITIVE));
        assertTrue(new CarbonShiftAssigner(sd, LegacyCompat.STRICT).assign());
        assertArrayEquals(new int[] {1, 3, 3, 0}, h(sd), "7 of 8 H: O keeps its formula H 0");
        assertArrayEquals(new Float[] {63.0f, 25.0f, 25.0f, null}, shifts(sd));
        NMRSpectrum c1 = sd.spectrum(SpectrumSlot.CARBON1D);
        assertEquals(1.0f, c1.getSignal(0).normalizeFactor);
        assertEquals(1.0f, c1.getSignal(1).normalizeFactor, "0.5 doubled once");
    }

    /** Multiplicity 4: the walk assigns atoms f, f+1, f+3, f+6 (an O!) and never uses the second signal. */
    @Test
    void walkSkipsAtomsForHighMultiplicity() {
        for (LegacyCompat compat : LegacyCompat.values()) {
            SenecaDataset sd = dataset("C5H12O2");
            sd.spectrum(SpectrumSlot.CARBON1D).addSignal(c(30.0f, 4f, 1));
            sd.spectrum(SpectrumSlot.CARBON1D).addSignal(c(32.0f, 1f, 1));
            sd.spectrum(SpectrumSlot.DEPT135).addSignal(c(30.0f, 1f, NMRSignal.PHASE_POSITIVE));
            assertTrue(new CarbonShiftAssigner(sd, compat).assign());
            assertArrayEquals(new Float[] {30.0f, 30.0f, null, 30.0f, null, null, 30.0f}, shifts(sd), compat.name());
            // 4 x CH3 counted = 12 = formula H -> all non-C atoms get H 0 (overwriting the O's 3)
            assertArrayEquals(new int[] {3, 3, 0, 3, 0, 0, 0}, h(sd), compat.name());
        }
    }

    @Test
    void walkPastTheAtoms() {
        SenecaDataset strict = dataset("C5H12");
        strict.spectrum(SpectrumSlot.CARBON1D).addSignal(c(30.0f, 4f, 1));
        strict.spectrum(SpectrumSlot.CARBON1D).addSignal(c(32.0f, 1f, 1));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> new CarbonShiftAssigner(strict, LegacyCompat.STRICT).assign());
        assertArrayEquals(new Float[] {30.0f, 30.0f, null, 30.0f, null}, shifts(strict), "partial assignment");

        SenecaDataset def = dataset("C5H12");
        def.spectrum(SpectrumSlot.CARBON1D).addSignal(c(30.0f, 4f, 1));
        def.spectrum(SpectrumSlot.CARBON1D).addSignal(c(32.0f, 1f, 1));
        assertTrue(new CarbonShiftAssigner(def, LegacyCompat.DEFAULT).assign());
        assertArrayEquals(new Float[] {30.0f, 30.0f, null, 30.0f, null}, shifts(def));
    }

    @Test
    void methyleneWithDept90IsCountedTwice() {
        SenecaDataset sd = dataset("C2H6");
        sd.spectrum(SpectrumSlot.CARBON1D).addSignal(c(40.0f, 1f, 1));
        sd.spectrum(SpectrumSlot.CARBON1D).addSignal(c(20.0f, 1f, 1));
        sd.spectrum(SpectrumSlot.DEPT90).addSignal(c(40.01f, 1f, NMRSignal.PHASE_POSITIVE));
        sd.spectrum(SpectrumSlot.DEPT135).addSignal(c(40.0f, -1f, NMRSignal.PHASE_NEGATIVE));
        sd.spectrum(SpectrumSlot.DEPT135).addSignal(c(20.0f, 1f, NMRSignal.PHASE_POSITIVE));
        new CarbonShiftAssigner(sd, LegacyCompat.STRICT).assign();
        assertArrayEquals(new int[] {1, 3}, h(sd), "CH2 then CH: H 1 but 3 H counted (D12/D16)");
    }

    @Test
    void zeroIntensityHangsInLegacy() {
        SenecaDataset strict = dataset("C2H6");
        strict.spectrum(SpectrumSlot.CARBON1D).addSignal(c(40.0f, 1f, 1));
        strict.spectrum(SpectrumSlot.CARBON1D).newSignal();
        assertThrows(IllegalStateException.class, () -> new CarbonShiftAssigner(strict, LegacyCompat.STRICT).assign());

        SenecaDataset def = dataset("C2H6");
        def.spectrum(SpectrumSlot.CARBON1D).addSignal(c(40.0f, 1f, 1));
        def.spectrum(SpectrumSlot.CARBON1D).newSignal();
        assertFalse(new CarbonShiftAssigner(def, LegacyCompat.DEFAULT).assign());
        assertEquals(1f, def.spectrum(SpectrumSlot.CARBON1D).getSignal(0).normalizeFactor, "nothing changed");
        assertNull(def.setOfNodes[0].shift13C);
    }

    @Test
    void emptySpectrum() {
        SenecaDataset strict = dataset("C2H6");
        assertThrows(NullPointerException.class, () -> new CarbonShiftAssigner(strict, LegacyCompat.STRICT).assign());
        assertFalse(new CarbonShiftAssigner(dataset("C2H6"), LegacyCompat.DEFAULT).assign());
        // no carbons: legacy returns normally and sets every H to 0 (formula H 0 == 0 counted)
        for (LegacyCompat compat : LegacyCompat.values()) {
            SenecaDataset sd = dataset("N2");
            sd.setOfNodes[0].hCount = 2;
            assertTrue(new CarbonShiftAssigner(sd, compat).assign());
            assertArrayEquals(new int[] {0, 0}, h(sd));
        }
    }

    /** C1 (10.0), C2 (32.0), C3 and C4 both 69.0. */
    static SenecaDataset hmbcDataset() {
        SenecaDataset sd = dataset("C4H10");
        float[] s = {10.0f, 32.0f, 69.0f, 69.0f};
        for (int i = 0; i < 4; i++) sd.setOfNodes[i].shift13C = s[i];
        NMRSpectrum hetcor = sd.spectrum(SpectrumSlot.HETCOR);
        hetcor.addSignal(hc(0.9f, 10.02f));
        hetcor.addSignal(hc(1.45f, 32.0f));
        hetcor.addSignal(hc(3.7f, 69.0f));
        NMRSpectrum lr = sd.spectrum(SpectrumSlot.HETCORLR);
        lr.addSignal(hc(3.7f, 10.0f));   // from C1 to {C3, C3'} (duplicate quirk)
        lr.addSignal(hc(0.905f, 32.01f)); // from C2 to C1
        return sd;
    }

    @Test
    void pickClosestCarbonsAddsTheClosestAtomAgainForEqualShifts() {
        SenecaDataset sd = hmbcDataset();
        List<HeavyAtom> l = HMBCAssigner.pickClosestCarbons(sd.setOfNodes, 69.01f, 0.05f);
        assertEquals(2, l.size());
        assertSame(sd.setOfNodes[2], l.get(0));
        assertSame(sd.setOfNodes[2], l.get(1), "legacy adds setOfNodes[thisPosition], not the equal atom");
        assertTrue(HMBCAssigner.pickClosestCarbons(sd.setOfNodes, 50f, 0.05f).isEmpty());
    }

    @Test
    void hmbcAssignment() {
        SenecaDataset sd = hmbcDataset();
        HMBCJudge j = sd.getJudge(HMBCJudge.class);
        assertTrue(new HMBCAssigner(sd, LegacyCompat.STRICT).assign());
        assertSame(j, new HMBCAssigner(sd, LegacyCompat.STRICT).judge());
        assertTrue(j.assignment[0][2][2]);
        assertTrue(j.assignment[1][0][0]);
        assertEquals(2, count(j.assignment));
        // a second run adds to the matrix, never clears it
        sd.spectrum(SpectrumSlot.HETCORLR).signals.clear();
        sd.spectrum(SpectrumSlot.HETCORLR).addSignal(hc(1.45f, 69.0f));
        assertTrue(new HMBCAssigner(sd, LegacyCompat.STRICT).assign());
        assertEquals(3, count(j.assignment));
        assertTrue(j.assignment[2][1][1]);
    }

    @Test
    void hmbcRefusesWithoutShiftsAndThrowsWithoutPartner() {
        SenecaDataset sd = hmbcDataset();
        sd.setOfNodes[3].shift13C = null;
        assertFalse(new HMBCAssigner(sd, LegacyCompat.STRICT).assign());
        assertNull(sd.getJudge(HMBCJudge.class).assignment);

        SenecaDataset sd2 = hmbcDataset();
        sd2.spectrum(SpectrumSlot.HETCORLR).addSignal(hc(5.0f, 10.0f));
        assertThrows(NullPointerException.class, () -> new HMBCAssigner(sd2, LegacyCompat.STRICT).assign());
        assertEquals(2, count(sd2.getJudge(HMBCJudge.class).assignment), "correlations before the failure stay");

        SenecaDataset sd3 = hmbcDataset();
        sd3.spectrum(SpectrumSlot.HETCOR).addSignal(hc(2.0f, 50.0f));
        sd3.spectrum(SpectrumSlot.HETCORLR).addSignal(hc(2.0f, 10.0f));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> new HMBCAssigner(sd3, LegacyCompat.STRICT).assign(), "empty 'to' list");
    }

    @Test
    void hhcosyAssignmentAndFailures() {
        SenecaDataset sd = hmbcDataset();
        sd.spectrum(SpectrumSlot.HHCOSY).addSignal(new NMRSignal(new String[] {"1H", "1H"}, new float[] {0.9f, 1.45f}, 1f, 1));
        HHCOSYJudge j = sd.getJudge(HHCOSYJudge.class);
        assertTrue(new HHCOSYAssigner(sd, LegacyCompat.STRICT).assign());
        assertTrue(j.assignment[1][0][0], "from = carbon of proton 2, to = carbon of proton 1");
        assertEquals(1, count(j.assignment));

        sd.spectrum(SpectrumSlot.HHCOSY).addSignal(new NMRSignal(new String[] {"1H", "1H"}, new float[] {0.9f, 9.9f}, 1f, 1));
        assertFalse(new HHCOSYAssigner(sd, LegacyCompat.STRICT).assign(), "exceptions -> false");
        assertEquals(1, count(j.assignment));
    }

    /** to = [D, D] (two equal shifts), from = [A, A, A]: the alternative loop indexes "to" with j -> j = 2 fails. */
    @Test
    void hhcosyUsesTheFromIndexForAlternatives() {
        SenecaDataset sd = dataset("C5H12");
        float[] s = {30.0f, 30.0f, 30.0f, 40.0f, 40.0f};
        for (int i = 0; i < 5; i++) sd.setOfNodes[i].shift13C = s[i];
        sd.spectrum(SpectrumSlot.HETCOR).addSignal(hc(1.0f, 30.0f));
        sd.spectrum(SpectrumSlot.HETCOR).addSignal(hc(2.0f, 40.0f));
        sd.spectrum(SpectrumSlot.HHCOSY).addSignal(new NMRSignal(new String[] {"1H", "1H"}, new float[] {2.0f, 1.0f}, 1f, 1));
        HHCOSYJudge j = sd.getJudge(HHCOSYJudge.class);
        assertFalse(new HHCOSYAssigner(sd, LegacyCompat.STRICT).assign());
        assertTrue(j.assignment[0][3][3]);
        assertEquals(1, count(j.assignment));
    }

    /** DV-46: in DEFAULT a failing H,H-COSY autoconfigure leaves the old matrix (here: none) untouched. */
    @Test
    void hhcosyFailureChangesNothingInDefault() {
        SenecaDataset sd = dataset("C5H12");
        float[] s = {30.0f, 30.0f, 30.0f, 40.0f, 40.0f};
        for (int i = 0; i < 5; i++) sd.setOfNodes[i].shift13C = s[i];
        sd.spectrum(SpectrumSlot.HETCOR).addSignal(hc(1.0f, 30.0f));
        sd.spectrum(SpectrumSlot.HETCOR).addSignal(hc(2.0f, 40.0f));
        sd.spectrum(SpectrumSlot.HHCOSY).addSignal(new NMRSignal(new String[] {"1H", "1H"}, new float[] {2.0f, 1.0f}, 1f, 1));
        HHCOSYJudge j = sd.getJudge(HHCOSYJudge.class);
        boolean[][][] before = j.assignment;
        assertFalse(new HHCOSYAssigner(sd, j, io.github.steinbeck.seneca.LegacyCompat.DEFAULT).assign());
        org.junit.jupiter.api.Assertions.assertSame(before, j.assignment);
        // and a successful DEFAULT assignment equals the STRICT one
        SenecaDataset ok = hmbcDataset();
        ok.spectrum(SpectrumSlot.HHCOSY).addSignal(new NMRSignal(new String[] {"1H", "1H"}, new float[] {0.9f, 1.45f}, 1f, 1));
        HHCOSYJudge k = ok.getJudge(HHCOSYJudge.class);
        assertTrue(new HHCOSYAssigner(ok, k, io.github.steinbeck.seneca.LegacyCompat.DEFAULT).assign());
        assertTrue(k.assignment[1][0][0]);
        assertEquals(1, count(k.assignment));
    }

    static int count(boolean[][][] a) {
        int n = 0;
        for (boolean[][] x : a) for (boolean[] y : x) for (boolean z : y) if (z) n++;
        return n;
    }
}
