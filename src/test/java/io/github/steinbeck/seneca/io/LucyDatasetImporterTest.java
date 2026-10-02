package io.github.steinbeck.seneca.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.chem.MolecularFormula;
import io.github.steinbeck.seneca.model.NMRSignal;
import io.github.steinbeck.seneca.model.NMRSpectrum;
import io.github.steinbeck.seneca.model.SenecaDataset;
import io.github.steinbeck.seneca.model.SpectrumSlot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LUCY {@code .col} import (feature-inventory 7.3). No LUCY sample exists in the repository, so the
 * files are synthetic: a C4H10O dataset (atom 4 deliberately without protons to exercise the reversed
 * HMBC lookup). Equality with the 2001 {@code LUCYFileHandler} is checked in
 * {@code oracle/DatasetIoOracleTest}.
 */
@Tag("B")
class LucyDatasetImporterTest {

    @TempDir
    Path dir;

    /** Lines of a .col file; {@code knownBonds} lines are inserted after the K line. */
    static String col(String... knownBonds) {
        List<String> l = new ArrayList<>(List.of(
                "LUCY 1.0",
                "C4H10O",
                "5",
                "3", "C 4", "H 10", "O 1",
                "4", "x", "y",
                "1 3", "2 2", "3 1", "4 0", "5 1",
                String.valueOf(knownBonds.length)));
        l.addAll(List.of(knownBonds));
        l.addAll(List.of("C", "C", "C", "C", "O",
                "10.0 0.9 0", "32.0 1.45 1.50", "69.0 3.7 0", "23.0 0 0",
                "3", "1 3", "1 4", "2 3",
                "1", "1 2"));
        return String.join("\n", l) + "\n";
    }

    Path write(String name, String text) throws IOException {
        Path p = dir.resolve(name);
        Files.writeString(p, text, StandardCharsets.ISO_8859_1);
        return p;
    }

    static List<String> signals(NMRSpectrum s) {
        List<String> l = new ArrayList<>();
        for (NMRSignal g : s.signals) {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < g.shift.length; i++) b.append(g.nucleus[i]).append('=').append(g.shift[i]).append(' ');
            b.append("I=").append(g.intensity).append(" P=").append(g.phase);
            l.add(b.toString());
        }
        return l;
    }

    static void assertComplete(SenecaDataset sd) {
        assertEquals("test.col", sd.getName());
        assertEquals("C4H10O", sd.getMolecularFormula());
        assertEquals(MolecularFormula.mass("C4H10O"), sd.getMolecularMass());
        HeavyAtom[] a = sd.getSetOfNodes();
        assertEquals(5, a.length);
        for (HeavyAtom x : a) assertEquals(-1, x.hCount, "all H counts reset to undefined");
        assertEquals(List.of("13C=10.0 I=1.0 P=0", "13C=32.0 I=1.0 P=0", "13C=69.0 I=1.0 P=0", "13C=23.0 I=1.0 P=0"),
                signals(sd.spectrum(SpectrumSlot.CARBON1D)));
        assertEquals(List.of("13C=69.0 I=1.0 P=1"), signals(sd.spectrum(SpectrumSlot.DEPT90)));
        assertEquals(List.of("13C=10.0 I=1.0 P=1", "13C=32.0 I=-1.0 P=2", "13C=69.0 I=1.0 P=1"),
                signals(sd.spectrum(SpectrumSlot.DEPT135)));
        assertEquals(List.of("1H=0.9 13C=10.0 I=0.0 P=0", "1H=1.45 13C=32.0 I=0.0 P=0", "1H=1.5 13C=32.0 I=0.0 P=0",
                "1H=3.7 13C=69.0 I=0.0 P=0"), signals(sd.spectrum(SpectrumSlot.HETCOR)));
        // pairs (1,3): protons of C3 with C1; (1,4): C4 has no proton -> reversed: protons of C1 with C4; (2,3)
        assertEquals(List.of("1H=3.7 13C=10.0 I=0.0 P=0", "1H=0.9 13C=23.0 I=0.0 P=0", "1H=3.7 13C=32.0 I=0.0 P=0"),
                signals(sd.spectrum(SpectrumSlot.HETCORLR)));
        assertEquals(List.of("1H=0.9 1H=1.45 I=0.0 P=0", "1H=0.9 1H=1.5 I=0.0 P=0", "1H=1.45 1H=0.9 I=0.0 P=0",
                "1H=1.5 1H=0.9 I=0.0 P=0"), signals(sd.spectrum(SpectrumSlot.HHCOSY)));
        assertEquals(0, sd.spectrum(SpectrumSlot.NOESY).size());
        assertTrue(sd.annealingSchedules.isEmpty(), "no schedule is created");
        assertEquals(7, sd.judges.size());
    }

    @Test
    void importsTheDerivedSpectra() throws IOException {
        Path p = write("test.col", col());
        List<String> log = new ArrayList<>();
        assertComplete(LucyDatasetImporter.read(p, LegacyCompat.STRICT, log::add));
        assertComplete(LucyDatasetImporter.read(p, LegacyCompat.DEFAULT, log::add));
        assertTrue(log.isEmpty(), log.toString());
    }

    @Test
    void knownBondLinesAreConsumedOnlyInDefault() throws IOException {
        Path p = write("test.col", col("1 2 1", "2 3 1"));
        assertComplete(LucyDatasetImporter.read(p, LegacyCompat.DEFAULT));

        // STRICT: the bond lines are read as element symbols, the carbon lines start at "C" -> parse error
        List<String> log = new ArrayList<>();
        SenecaDataset sd = LucyDatasetImporter.read(p, LegacyCompat.STRICT, log::add);
        assertEquals(1, log.size());
        assertTrue(log.get(0).startsWith(LucyDatasetImporter.ERROR_MESSAGE + "java.lang.NumberFormatException"), log.get(0));
        assertEquals(0, sd.spectrum(SpectrumSlot.CARBON1D).size(), "partial dataset");
        assertEquals(3, sd.getSetOfNodes()[0].hCount, "H counts were not reset yet");
        assertEquals(7, sd.judges.size(), "init() runs after a parse error, as legacy");
    }

    @Test
    void truncatedFileGivesAPartialDataset() throws IOException {
        String text = col();
        Path p = write("test.col", text.substring(0, text.indexOf("3\n1 3")));
        List<String> log = new ArrayList<>();
        SenecaDataset sd = LucyDatasetImporter.read(p, LegacyCompat.DEFAULT, log::add);
        assertEquals(1, log.size());
        assertTrue(log.get(0).contains("NullPointerException"), log.get(0));
        assertEquals(4, sd.spectrum(SpectrumSlot.CARBON1D).size());
        assertEquals(0, sd.spectrum(SpectrumSlot.HETCORLR).size());
    }

    @Test
    void missingFileThrows() {
        assertThrows(IOException.class, () -> LucyDatasetImporter.read(dir.resolve("none.col"), LegacyCompat.DEFAULT));
    }
}
