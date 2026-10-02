package io.github.steinbeck.seneca.config;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.golden.GoldenFiles;
import io.github.steinbeck.seneca.io.SpecMLReader;
import io.github.steinbeck.seneca.model.SenecaDataset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The README tutorial workflow (Eurabidiol) on the Atom Properties logic (F-42). */
@Tag("B")
class AtomPropertiesTest {

    @Test
    void eurabidiolTutorial() throws Exception {
        SenecaDataset sd = SpecMLReader.read(GoldenFiles.sml("eurabidiol"));
        assertTrue(AtomProperties.autoassign(sd, LegacyCompat.DEFAULT));
        AtomProperties.Balance b = AtomProperties.balance(sd);
        assertFalse(b.balanced());
        assertEquals("HCount in molecular formula: 28 - Sum of HCounts in this table: 26", b.message());
        AtomProperties.setHydrogens(sd, 15, "1");
        AtomProperties.setHydrogens(sd, 16, "5");   // out of range: ignored
        AtomProperties.setHydrogens(sd, 16, "one"); // not a number: ignored
        assertEquals(0, sd.setOfNodes[16].hCount);
        AtomProperties.setHydrogens(sd, 16, "1");
        sd.setOfNodes[0].hCount = -1;
        assertEquals("undefined", AtomProperties.hydrogensText(sd.setOfNodes[0]));
        b = AtomProperties.balance(sd);
        assertTrue(b.balanced(), b.message());
        assertEquals(0, sd.setOfNodes[0].hCount, "balanced: undefined H counts become 0");
        assertEquals("74.3", AtomProperties.shiftText(sd.setOfNodes[0]));
        assertEquals("-", AtomProperties.shiftText(sd.setOfNodes[15]));
        AtomProperties.setAssignedShift(sd, 15, "70.5");
        assertEquals(70.5f, sd.setOfNodes[15].shift13C);
        assertThrows(NumberFormatException.class, () -> AtomProperties.setAssignedShift(sd, 15, "x"));
    }
}
