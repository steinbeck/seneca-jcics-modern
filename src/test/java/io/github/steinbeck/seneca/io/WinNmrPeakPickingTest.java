package io.github.steinbeck.seneca.io;

import java.util.NoSuchElementException;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.model.NMRSignal;
import io.github.steinbeck.seneca.model.NMRSpectrum;
import io.github.steinbeck.seneca.model.SenecaDataset;
import io.github.steinbeck.seneca.model.SpectrumSlot;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** WinNMR peak-picking paste (feature-inventory 7.6) and its two switch points (DV-40, DV-41). */
@Tag("B")
class WinNmrPeakPickingTest {

    static final String PP_1D = """
            WIN-NMR 1D
            Peak Picking results:
            Peak   Point   Frequency   ppm   Intensity   %Int
            1   1024   7543.21   75.0123   1234.5   100.0
            2   2048   5432.10   54.25   -617.2   -50.0
            3   3000   3210.00   32.1   0.0   0.0
            4   3500   1000.00   10.5   12.0   1.0
            """;

    @Test
    void oneDimensional() {
        NMRSpectrum s = new SenecaDataset("x").spectrum(SpectrumSlot.CARBON1D);
        WinNmrPeakPicking.paste(PP_1D, s, LegacyCompat.STRICT);
        assertEquals(4, s.size());
        NMRSignal a = s.getSignal(0), b = s.getSignal(1), c = s.getSignal(2), d = s.getSignal(3);
        assertEquals(75.0123f, a.shift[0]);
        assertEquals(100.0f, a.intensity, "percent intensity");
        assertEquals(NMRSignal.PHASE_POSITIVE, a.phase);
        assertEquals(-50.0f, b.intensity);
        assertEquals(NMRSignal.PHASE_NEGATIVE, b.phase);
        assertEquals(NMRSignal.PHASE_NEGATIVE, c.phase, "0 % keeps the previous phase");
        assertEquals(NMRSignal.PHASE_POSITIVE, d.phase);
        assertSame(s.nucleus, a.nucleus);
    }

    @Test
    void oneDimensionalMalformedKeepsWhatWasParsed() {
        NMRSpectrum s = new SenecaDataset("x").spectrum(SpectrumSlot.CARBON1D);
        String text = PP_1D.substring(0, PP_1D.indexOf("   1.0\n"));
        assertThrows(NoSuchElementException.class, () -> WinNmrPeakPicking.paste(text, s, LegacyCompat.STRICT));
        assertEquals(3, s.size());
    }

    @Test
    void twoDimensionalStrictAliasesTheShiftArray() {
        String text = "1.25 74.3\n2.5 41.5\n3.75 20.0\n";
        NMRSpectrum strict = new SenecaDataset("x").spectrum(SpectrumSlot.HETCOR);
        WinNmrPeakPicking.paste(text, strict, LegacyCompat.STRICT);
        assertEquals(3, strict.size());
        for (NMRSignal g : strict.signals) {
            assertArrayEquals(new float[] {3.75f, 20.0f}, g.shift, "legacy: every signal shares one array");
            assertEquals(1f, g.intensity);
            assertEquals(NMRSignal.PHASE_POSITIVE, g.phase);
        }
        NMRSpectrum def = new SenecaDataset("x").spectrum(SpectrumSlot.HETCOR);
        WinNmrPeakPicking.paste(text, def, LegacyCompat.DEFAULT);
        assertArrayEquals(new float[] {1.25f, 74.3f}, def.getSignal(0).shift);
        assertArrayEquals(new float[] {2.5f, 41.5f}, def.getSignal(1).shift);
        assertArrayEquals(new float[] {3.75f, 20.0f}, def.getSignal(2).shift);
    }

    @Test
    void pasteIntoAnEmptySpectrumReadFromSml() {
        // a spectrum read empty from SML has dim 0: legacy appends one signal, then the shift-list update throws
        NMRSpectrum strict = new NMRSpectrum(new String[0], "hhcosy");
        assertThrows(ArrayIndexOutOfBoundsException.class,
                () -> WinNmrPeakPicking.paste("1.0 2.0 3.0 4.0", strict, LegacyCompat.STRICT));
        assertEquals(1, strict.size());
        NMRSpectrum def = new NMRSpectrum(new String[0], "hhcosy");
        WinNmrPeakPicking.paste("1.0 2.0 3.0 4.0", def, LegacyCompat.DEFAULT);
        assertEquals(2, def.size());
    }

    @Test
    void oddTokenCountThrowsAfterTheCompletePairs() {
        NMRSpectrum s = new SenecaDataset("x").spectrum(SpectrumSlot.HHCOSY);
        assertThrows(NoSuchElementException.class, () -> WinNmrPeakPicking.paste("1.0 2.0 3.0", s, LegacyCompat.DEFAULT));
        assertEquals(1, s.size());
        assertThrows(NoSuchElementException.class, () -> WinNmrPeakPicking.paste("", s, LegacyCompat.DEFAULT));
    }
}
