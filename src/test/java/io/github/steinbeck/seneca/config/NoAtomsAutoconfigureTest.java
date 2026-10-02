package io.github.steinbeck.seneca.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.judges.Judge;
import io.github.steinbeck.seneca.judges.HMBCJudge;
import io.github.steinbeck.seneca.model.SenecaDataset;

/**
 * "Autoconfigure Judge" on a dataset without atoms ("New Dataset" before a formula is typed; found by the
 * GUI tests, app.edit.EmptyDatasetTest). DEFAULT: no judge's autoconfigure throws; the HMBC assigner
 * fails and the judge is disabled (DV-46). STRICT keeps the legacy NullPointerException of the HMBC
 * assigner (the H,H-COSY assigner caught it already in 2001).
 */
class NoAtomsAutoconfigureTest {

    @Test
    void defaultNeverThrowsAndDisablesTheHmbcJudge() {
        SenecaDataset sd = SenecaDataset.newDataset("untitled1");
        JudgeConfiguration.datasetWindowOpened(sd);
        for (Judge j : sd.judges) assertDoesNotThrow(() -> JudgeConfiguration.autoconfigure(sd, j, LegacyCompat.DEFAULT), j.name);
        HMBCJudge hmbc = sd.getJudge(HMBCJudge.class);
        assertFalse(hmbc.getEnabled());
        assertNull(hmbc.assignment);
        assertFalse(new HMBCAssigner(sd, LegacyCompat.DEFAULT).assign());
    }

    @Test
    void strictKeepsTheLegacyException() {
        SenecaDataset sd = SenecaDataset.newDataset("untitled2");
        assertThrows(NullPointerException.class, () -> new HMBCAssigner(sd, LegacyCompat.STRICT).assign());
    }
}
