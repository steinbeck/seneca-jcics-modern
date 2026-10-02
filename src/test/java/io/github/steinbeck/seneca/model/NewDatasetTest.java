package io.github.steinbeck.seneca.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.steinbeck.seneca.judges.Judge;

/** {@link SenecaDataset#newDataset}: "New Dataset" with the D10 fix (docs/port/gui-design.md). */
class NewDatasetTest {

    @Test
    void newDatasetHasTheSevenDisabledJudgesAndOneDefaultSchedule() {
        SenecaDataset sd = SenecaDataset.newDataset("untitled42");
        assertEquals("untitled42", sd.getName());
        assertEquals("none", sd.getDescription());
        assertNull(sd.getMolecularFormula());
        assertNull(sd.getSetOfNodes());
        assertEquals(List.of("HHCOSYJudge", "HMBCJudge", "HeteroBondsJudge", "SymmetryJudge", "HybridizationJudge",
                "HOSECodeJudge", "FeatureJudge"), sd.judges.stream().map(j -> j.getClass().getSimpleName()).toList());
        for (Judge j : sd.judges) assertFalse(j.getEnabled(), j.name);
        assertEquals(1, sd.annealingSchedules.size());
        AnnealingSchedule s = sd.annealingSchedules.get(0);
        assertEquals(0.8, s.initialAcceptanceProbability);
        assertEquals(500, s.plateauSteps);
        assertEquals(0.95, s.asymptoticCoolingFactor);
        assertEquals(50, s.maxUphillSteps);
        for (SpectrumSlot slot : SpectrumSlot.values()) assertEquals(0, sd.spectrum(slot).size(), slot.key);
    }
}
