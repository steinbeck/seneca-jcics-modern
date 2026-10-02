package io.github.steinbeck.seneca.io;

import java.util.StringTokenizer;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.model.NMRSignal;
import io.github.steinbeck.seneca.model.NMRSpectrum;

/**
 * "Paste WinNMR pp from clipboard" (F-41) as a plain text parser. Port of {@code pasteWinNMRpp},
 * {@code importWinNMR1D} and {@code importWinNMR2D} in
 * {@code legacy/original/src/seneca/client/NMRSpectrumTablePanel.java}; feature-inventory 7.6.
 *
 * <ul>
 *   <li>1D, if {@code "Peak Picking results:"} occurs at an index &gt; 0: the text from that marker,
 *       then from the first {@code "1"}; groups of 6 whitespace tokens (peak no., data point, frequency,
 *       <b>ppm</b>, intensity, <b>percent intensity</b>) until no token is left; shift = ppm, intensity
 *       = percent, phase POSITIVE if &gt; 0, NEGATIVE if &lt; 0, otherwise the phase of the previous
 *       peak (NONE for the first).</li>
 *   <li>2D otherwise: token pairs {@code <f1> <f2>}, intensity 1, POSITIVE.</li>
 *   <li>Signals take the target spectrum's nucleus array; malformed text throws (NoSuchElementException,
 *       NumberFormatException, StringIndexOutOfBoundsException) after the signals parsed so far were
 *       appended, as on the legacy event thread.</li>
 * </ul>
 *
 * <p>Switch points (data loss, DEVIATIONS.md DV-40, DV-41):
 * <ul>
 *   <li>Legacy 2D import allocates the shift array once, so every pasted 2D signal shares it and all
 *       of them end up with the last pair's shifts. STRICT reproduces the aliasing; DEFAULT gives each
 *       signal its own shifts.</li>
 *   <li>Legacy {@code NMRSpectrum.addSignal} updates per-axis shift lists sized by the spectrum's
 *       dimension; a signal with more shifts than the spectrum has axes (pasting into a spectrum that
 *       was read empty from SML, dim 0) is appended and then an {@link ArrayIndexOutOfBoundsException}
 *       aborts the paste. STRICT reproduces this; DEFAULT appends all signals.</li>
 * </ul>
 */
public final class WinNmrPeakPicking {

    public static final String MARKER = "Peak Picking results:";

    private WinNmrPeakPicking() {
    }

    /** Appends the parsed signals to {@code target}; see the class comment for the two switch points. */
    public static void paste(String clipboardText, NMRSpectrum target, LegacyCompat compat) {
        if (clipboardText.indexOf(MARKER) > 0) import1D(clipboardText, target, compat);
        else import2D(clipboardText, target, compat);
    }

    private static void import1D(String s, NMRSpectrum target, LegacyCompat compat) {
        int phase = NMRSignal.PHASE_NONE;
        s = s.substring(s.indexOf(MARKER));
        s = s.substring(s.indexOf("1"));
        StringTokenizer tok = new StringTokenizer(s);
        do {
            tok.nextToken(); // number of peak
            tok.nextToken(); // data point
            tok.nextToken(); // frequency
            float ppm = Float.parseFloat(tok.nextToken().trim());
            tok.nextToken(); // intensity
            float procInt = Float.valueOf(tok.nextToken().trim()); // percent intensity (legacy new Float(String))
            float[] shifts = {ppm};
            if (procInt > 0) phase = NMRSignal.PHASE_POSITIVE;
            if (procInt < 0) phase = NMRSignal.PHASE_NEGATIVE;
            add(target, new NMRSignal(target.nucleus, shifts, procInt, phase), compat);
        } while (tok.hasMoreTokens());
    }

    private static void import2D(String s, NMRSpectrum target, LegacyCompat compat) {
        float[] shared = new float[2];
        StringTokenizer tok = new StringTokenizer(s);
        do {
            float[] shifts = compat.strict() ? shared : new float[2];
            shifts[0] = Float.valueOf(tok.nextToken().trim());
            shifts[1] = Float.valueOf(tok.nextToken().trim());
            add(target, new NMRSignal(target.nucleus, shifts, 1f, NMRSignal.PHASE_POSITIVE), compat);
        } while (tok.hasMoreTokens());
    }

    private static void add(NMRSpectrum target, NMRSignal signal, LegacyCompat compat) {
        target.addSignal(signal);
        if (compat.strict() && signal.shift.length > target.dim) {
            // legacy updateShiftLists(): shiftList[j] for every shift j of the signal, shiftList.length == dim
            throw new ArrayIndexOutOfBoundsException("Index " + target.dim + " out of bounds for length " + target.dim);
        }
    }
}
