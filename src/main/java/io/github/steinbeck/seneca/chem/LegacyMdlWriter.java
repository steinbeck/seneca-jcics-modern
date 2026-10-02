package io.github.steinbeck.seneca.chem;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * compchem {@code ChemFileUtils.writeMDLMol}: the V2000 molfile text that SENECA uses as result
 * representation (result list, best structure, {@code seneca.results.sdf}, golden {@code bestMDL}).
 * <b>Byte-exact.</b>
 *
 * <p>Reference: {@code legacy/decompiled/compchem/compchem/ChemFileUtils.java};
 * {@code docs/legacy/compchem-semantics.md} section 10. Title line, 2 empty lines, counts line
 * {@code %3d%3d  0  0  0  0  0  0  0  0  1 V2000}, atom lines (x/y/z as {@code float} with 4 decimals,
 * width 10, {@code Locale.US}; {@code " " + symbol} padded/truncated to 3, then the zero columns), bond
 * lines for {@code i < j} row-major from {@code createConnectionTable} with order/stereo mapping (5/6 -&gt;
 * order 1 stereo 1, 6 swaps; 7/8 -&gt; order 1 stereo 6, 8 swaps), {@code M  END}. Integer fields use
 * {@code NumberFormat} with at most 3 integer digits (higher digits are dropped, as legacy); the port
 * uses {@code Locale.ROOT} instead of the default locale (DEVIATIONS.md DV-29). CDK's MDLV2000Writer
 * differs.
 */
public final class LegacyMdlWriter {

    private LegacyMdlWriter() {
    }

    /** {@code writeMDLMol(writer, title, atoms)} into a String (atoms' current bonds via nodeTable). */
    public static String write(String title, HeavyAtom[] atoms) {
        int n = atoms.length;
        int[][] ct = AtomSets.createConnectionTable(atoms);
        int bonds = 0;
        for (int i = 0; i < n; i++)
            for (int j = i + 1; j < n; j++)
                if (ct[i][j] > 0) bonds++;
        StringBuilder w = new StringBuilder();
        w.append(title).append('\n');
        w.append('\n');
        w.append('\n');
        w.append(formatMDLInt(atoms.length, 3)).append(formatMDLInt(bonds, 3))
                .append("  0  0  0  0  0  0  0  0  1 V2000").append('\n');
        for (int i = 0; i < n; i++) {
            w.append(formatMDLFloat((float) atoms[i].x))
                    .append(formatMDLFloat((float) atoms[i].y))
                    .append(formatMDLFloat((float) atoms[i].z))
                    .append(' ')
                    .append(formatMDLString(atoms[i].symbol, 3))
                    .append(" 0  0  0  0  0  0  0  0  0  0  0  0")
                    .append('\n');
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (ct[i][j] > 0) {
                    int code = ct[i][j];
                    int order = ct[i][j];
                    int stereo = 0;
                    int from = i;
                    int to = j;
                    if (code == 5) {
                        order = 1;
                        stereo = 1;
                    }
                    if (code == 6) {
                        order = 1;
                        stereo = 1;
                        from = j;
                        to = i;
                    }
                    if (code == 7) {
                        order = 1;
                        stereo = 6;
                    }
                    if (code == 8) {
                        order = 1;
                        stereo = 6;
                        from = j;
                        to = i;
                    }
                    w.append(formatMDLInt(from + 1, 3)).append(formatMDLInt(to + 1, 3))
                            .append(formatMDLInt(order, 3)).append(formatMDLInt(stereo, 3))
                            .append("  0  0  0").append('\n');
                }
            }
        }
        w.append("M  END\n");
        return w.toString();
    }

    static String formatMDLInt(int value, int width) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.ROOT);
        nf.setParseIntegerOnly(true);
        nf.setMinimumIntegerDigits(1);
        nf.setMaximumIntegerDigits(width);
        nf.setGroupingUsed(false);
        String s = nf.format((long) value);
        StringBuilder pad = new StringBuilder();
        for (int i = 0; i < width - s.length(); i++) pad.append(' ');
        return pad.append(s).toString();
    }

    static String formatMDLFloat(float value) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setMinimumIntegerDigits(1);
        nf.setMaximumIntegerDigits(4);
        nf.setMinimumFractionDigits(4);
        nf.setMaximumFractionDigits(4);
        nf.setGroupingUsed(false);
        String s = nf.format((double) value);
        StringBuilder pad = new StringBuilder();
        for (int i = 0; i < 10 - s.length(); i++) pad.append(' ');
        return pad.append(s).toString();
    }

    static String formatMDLString(String s, int width) {
        s = s.trim();
        if (s.length() > width) return s.substring(0, width);
        StringBuilder b = new StringBuilder(s);
        for (int i = 0; i < width - s.length(); i++) b.append(' ');
        return b.toString();
    }
}
