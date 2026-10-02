package io.github.steinbeck.seneca.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.golden.GoldenFiles;
import io.github.steinbeck.seneca.io.SpecMLReader;
import io.github.steinbeck.seneca.io.SpecMLWriter;

/** {@link NMRSpectrum#ensureAxes}: axes for a spectrum that was read empty (gui-design.md DV-85). */
class EnsureAxesTest {

    @Test
    void spectrumReadEmptyGetsTheSlotAxesAndKeepsThemThroughSaveAndLoad() throws Exception {
        SenecaDataset sd = SpecMLReader.read(GoldenFiles.sml("eurabidiol"));
        NMRSpectrum cosy = sd.spectrum(SpectrumSlot.HHCOSY);
        assertEquals(0, cosy.dim);
        assertEquals(0, cosy.nucleus.length);
        AtomicInteger changes = new AtomicInteger();
        cosy.addChangeListener(changes::incrementAndGet);

        assertTrue(cosy.ensureAxes(SpectrumSlot.HHCOSY.defaultNuclei()));
        assertEquals(1, changes.get());
        assertEquals(2, cosy.dim);
        assertArrayEquals(new String[] {"1H", "1H"}, cosy.nucleus);
        cosy.newSignal();
        cosy.getSignal(0).shift[0] = 1.5f;
        cosy.getSignal(0).shift[1] = 2.5f;
        assertFalse(cosy.ensureAxes(SpectrumSlot.CARBON1D.defaultNuclei()), "has axes now");

        String sml = SpecMLWriter.write(sd, LegacyCompat.DEFAULT);
        SenecaDataset back = SpecMLReader.read(new java.io.ByteArrayInputStream(sml.getBytes(java.nio.charset.StandardCharsets.UTF_8)), "x.sml");
        NMRSpectrum c2 = back.spectrum(SpectrumSlot.HHCOSY);
        assertEquals(2, c2.dim);
        assertEquals(1, c2.size());
        assertEquals(2.5f, c2.getSignal(0).shift[1]);
    }

    @Test
    void spectrumWithAxesOrSignalsIsLeftAlone() {
        NMRSpectrum s = new NMRSpectrum(new String[] {"13C"}, "carbon1d");
        assertFalse(s.ensureAxes(new String[] {"1H", "13C"}));
        assertEquals(1, s.dim);
        NMRSpectrum empty = new NMRSpectrum(new String[0], "x");
        empty.addSignal(new NMRSignal(new String[0]));
        assertFalse(empty.ensureAxes(new String[] {"13C"}), "already has a signal");
        assertEquals(0, empty.dim);
        assertFalse(new NMRSpectrum(new String[0], "y").ensureAxes(new String[0]));
    }
}
