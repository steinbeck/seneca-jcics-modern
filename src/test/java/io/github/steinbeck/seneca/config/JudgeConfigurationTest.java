package io.github.steinbeck.seneca.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.judges.FeatureJudge;
import io.github.steinbeck.seneca.judges.HHCOSYJudge;
import io.github.steinbeck.seneca.judges.HMBCJudge;
import io.github.steinbeck.seneca.judges.HOSECodeJudge;
import io.github.steinbeck.seneca.judges.HeteroBondsJudge;
import io.github.steinbeck.seneca.judges.HybridizationJudge;
import io.github.steinbeck.seneca.judges.SymmetryJudge;
import io.github.steinbeck.seneca.model.SenecaDataset;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The non-Swing judge configurator logic (F-50 to F-56). */
@Tag("B")
class JudgeConfigurationTest {

    static SenecaDataset dataset(Float... shifts) {
        SenecaDataset sd = new SenecaDataset("t");
        sd.setMolecularFormula("C" + shifts.length);
        sd.init();
        for (int i = 0; i < shifts.length; i++) sd.setOfNodes[i].shift13C = shifts[i];
        return sd;
    }

    @Test
    void windowOpenAllocatesTheTables() {
        SenecaDataset sd = dataset(1f, 2f, 3f);
        JudgeConfiguration.datasetWindowOpened(sd);
        HybridizationJudge hy = sd.getJudge(HybridizationJudge.class);
        HeteroBondsJudge hb = sd.getJudge(HeteroBondsJudge.class);
        assertSame(sd.setOfNodes, hy.getAttic().atomSet);
        assertEquals(3, hy.scores.length);
        assertArrayEquals(new int[3], hy.scores[2]);
        assertArrayEquals(new boolean[3], hb.isHeteroBonded);
    }

    @Test
    void hoseCodeTakesTheAssignedShiftsCompactly() {
        SenecaDataset sd = dataset(74.3f, null, 20.1f);
        HOSECodeJudge h = sd.getJudge(HOSECodeJudge.class);
        JudgeConfiguration.autoconfigure(sd, h, LegacyCompat.STRICT);
        assertTrue(h.getEnabled());
        assertArrayEquals(new double[] {(double) 74.3f, (double) 20.1f}, h.getCarbonShifts());
    }

    @Test
    void symmetryClassesWithTheInheritedShiftQuirk() {
        SenecaDataset sd = dataset(10f, null, 10f, 20f, null, 20f);
        SymmetryJudge s = sd.getJudge(SymmetryJudge.class);
        s.addSymmetryClass(new boolean[6]);
        JudgeConfiguration.autoconfigure(sd, s, LegacyCompat.STRICT);
        List<List<Integer>> classes = new ArrayList<>();
        for (int c = 0; c < s.getSymmetryClassCount(); c++) {
            List<Integer> m = new ArrayList<>();
            boolean[] b = s.getSymmetryClass(c);
            for (int i = 0; i < b.length; i++) if (b[i]) m.add(i);
            classes.add(m);
        }
        // atoms 1 and 4 have no shift and inherit 10 and 20; old classes are cleared
        assertEquals(List.of(List.of(0, 2), List.of(1, 2), List.of(3, 5), List.of(4, 5)), classes);
    }

    @Test
    void featureJudge() {
        SenecaDataset sd = dataset(1f);
        FeatureJudge f = sd.getJudge(FeatureJudge.class);
        JudgeConfiguration.autoconfigure(sd, f, LegacyCompat.STRICT);
        assertEquals(Map.of(3, -500, 4, -300), f.ringSizeScores);
        assertTrue(f.isHetHetPenaltyEnabled());
        JudgeConfiguration.setHetHetBondsForbidden(sd, false);
        assertFalse(f.isHetHetPenaltyEnabled());
    }

    @Test
    void twoDJudgeIsDisabledWhenTheAssignerFails() {
        SenecaDataset sd = dataset(1f, null);
        HHCOSYJudge cosy = sd.getJudge(HHCOSYJudge.class);
        JudgeConfiguration.autoconfigure(sd, cosy, LegacyCompat.STRICT);
        assertFalse(cosy.getEnabled(), "a carbon without shift: assign() == false");
        HMBCJudge hmbc = sd.getJudge(HMBCJudge.class);
        sd.setOfNodes[1].shift13C = 2f;
        JudgeConfiguration.autoconfigure(sd, hmbc, LegacyCompat.STRICT);
        assertTrue(hmbc.getEnabled(), "assign() == true without signals");
        assertNull(hmbc.assignment);
        assertEquals("", JudgeConfiguration.report(hmbc));
        hmbc.assignment = new boolean[2][2][2];
        hmbc.assignment[1][0][0] = true;
        assertEquals("Configuration of TwoDSpectrumJudge HMBCJudge:\nListing relation of nuclei by number...\n1-0\nEnd of listing",
                JudgeConfiguration.report(hmbc));
        assertNull(JudgeConfiguration.report(sd.getJudge(FeatureJudge.class)));
    }

    @Test
    void tableJudgesHaveNoAutoconfigureButEditableTables() {
        SenecaDataset sd = dataset(150f, 30f, 60f);
        JudgeConfiguration.datasetWindowOpened(sd);
        HybridizationJudge hy = sd.getJudge(HybridizationJudge.class);
        HeteroBondsJudge hb = sd.getJudge(HeteroBondsJudge.class);
        JudgeConfiguration.autoconfigure(sd, hy, LegacyCompat.STRICT);
        JudgeConfiguration.autoconfigure(sd, hb, LegacyCompat.STRICT);
        assertTrue(hy.getEnabled() && hb.getEnabled());
        JudgeConfiguration.setHybridizationCell(sd, 0, JudgeConfiguration.HYB_COLUMN_SP2, "1.0", LegacyCompat.STRICT);
        JudgeConfiguration.setHybridizationCell(sd, 1, JudgeConfiguration.HYB_COLUMN_SP3, "0.9", LegacyCompat.STRICT);
        JudgeConfiguration.setHybridizationCell(sd, 2, JudgeConfiguration.HYB_COLUMN_SP, "0.3", LegacyCompat.STRICT);
        JudgeConfiguration.setHybridizationCell(sd, 2, 2, "0.5", LegacyCompat.STRICT);
        assertArrayEquals(new int[] {0, 100, 0}, hy.scores[0]);
        assertArrayEquals(new int[] {90, 0, 0}, hy.scores[1]);
        assertArrayEquals(new int[] {0, 0, 30}, hy.scores[2]);
        JudgeConfiguration.setHeteroBondsCell(sd, 2, JudgeConfiguration.HETERO_COLUMN, true, LegacyCompat.STRICT);
        JudgeConfiguration.setHeteroBondsCell(sd, 1, 1, true, LegacyCompat.STRICT);
        assertArrayEquals(new boolean[] {false, false, true}, hb.isHeteroBonded);
        JudgeConfiguration.setActivated(hy, false);
        assertFalse(hy.getEnabled());
    }
}
