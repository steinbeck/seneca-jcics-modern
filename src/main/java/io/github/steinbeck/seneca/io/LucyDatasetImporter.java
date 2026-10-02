package io.github.steinbeck.seneca.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.StringTokenizer;
import java.util.function.Consumer;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.chem.MolecularFormula;
import io.github.steinbeck.seneca.model.NMRSignal;
import io.github.steinbeck.seneca.model.NMRSpectrum;
import io.github.steinbeck.seneca.model.SenecaDataset;
import io.github.steinbeck.seneca.model.SpectrumSlot;

/**
 * Imports a LUCY {@code .col} dataset. Port of
 * {@code legacy/original/src/seneca/client/LUCYFileHandler.java} ({@code importLUCYDataset}); format
 * and derived spectra in docs/legacy/feature-inventory.md 7.3 (dataset name = file name, all H counts
 * reset to -1 at the end, {@code sd.init()}, no schedule).
 *
 * <p>Line layout: version; formula; N heavy atoms; E elements + E lines; C carbons + E-1 lines; N atom
 * lines (token 2 = H count); K known bonds (+ K lines, see below); N element-symbol lines; C lines
 * {@code 13C 1Ha 1Hb} (0 = no proton); M HMBC pairs + M lines {@code i j}; L COSY pairs + L lines
 * {@code i j} (1-based atom numbers).
 *
 * <p>Derived data, in legacy order: per carbon line a 1D carbon signal (intensity 1, phase NONE); by
 * the H count of heavy atom f (the f-th carbon line is paired with heavy atom f): CH -&gt; DEPT-90 and
 * DEPT-135 positive, CH2 -&gt; DEPT-135 negative (intensity -1), CH3 -&gt; DEPT-135 positive; one 1JCH
 * signal per non-zero proton shift; then all H counts are set to -1; HMBC pairs i &lt; j become CH
 * long-range signals (13C of i with each 1JCH proton of j within 0.01 ppm, otherwise reversed); COSY
 * pairs (both directions) become H,H-COSY signals for all proton combinations.
 *
 * <p>Errors: like legacy, any exception while parsing ends the import; the dataset read so far is
 * returned (after {@code init()}) and the message {@code "Error while trying to read LUCY file\n" +
 * exception} goes to {@code log} (legacy: stdout). A file that cannot be opened throws
 * {@link IOException} in the port (legacy printed the same message and returned an empty dataset).
 *
 * <p>Switch point (data loss, DEVIATIONS.md DV-09): legacy consumes the K known-bond lines only in
 * debug mode, so files with K &gt; 0 are misparsed. STRICT reproduces this; DEFAULT consumes the K lines.
 */
public final class LucyDatasetImporter {

    /** Legacy error message prefix (stdout in 2001). */
    public static final String ERROR_MESSAGE = "Error while trying to read LUCY file\n";

    private LucyDatasetImporter() {
    }

    public static SenecaDataset read(Path colFile, LegacyCompat compat) throws IOException {
        return read(colFile, compat, msg -> { });
    }

    public static SenecaDataset read(Path colFile, LegacyCompat compat, Consumer<String> log) throws IOException {
        SenecaDataset sd = new SenecaDataset(colFile.getFileName().toString());
        try (BufferedReader br = Files.newBufferedReader(colFile, StandardCharsets.ISO_8859_1)) {
            try {
                parse(br, sd, compat);
            } catch (RuntimeException e) {
                log.accept(ERROR_MESSAGE + e);
            }
        }
        sd.init();
        return sd;
    }

    private static void parse(BufferedReader br, SenecaDataset sd, LegacyCompat compat) throws IOException {
        // version line
        br.readLine();
        String mf = line(br);
        // legacy: new MFAnalyser(mf).getSetOfHeavyNodes() (fails first on a bad formula), then
        // sd.setMolecularFormula(mf), sd.setMolecularMass(mfa.getMass())
        MolecularFormula.heavyAtoms(mf);
        sd.setMolecularFormula(mf);
        sd.setMolecularMass(MolecularFormula.mass(mf));
        HeavyAtom[] setOfNodes = sd.getSetOfNodes();
        int heavyAtomsCount = Integer.parseInt(line(br));
        int elementCount = Integer.parseInt(line(br));
        for (int f = 0; f < elementCount; f++) br.readLine();
        int carbonCount = Integer.parseInt(line(br));
        for (int f = 0; f < elementCount - 1; f++) br.readLine();
        for (int f = 0; f < heavyAtomsCount; f++) {
            StringTokenizer st = new StringTokenizer(line(br));
            st.nextToken();
            setOfNodes[f].hCount = Integer.parseInt(st.nextToken().trim());
        }
        int knownBonds = Integer.parseInt(line(br));
        if (!compat.strict()) {
            for (int f = 0; f < knownBonds; f++) br.readLine();
        }
        for (int f = 0; f < heavyAtomsCount; f++) br.readLine();

        NMRSpectrum carbon1D = sd.spectrum(SpectrumSlot.CARBON1D);
        NMRSpectrum dept90 = sd.spectrum(SpectrumSlot.DEPT90);
        NMRSpectrum dept135 = sd.spectrum(SpectrumSlot.DEPT135);
        NMRSpectrum hetcor = sd.spectrum(SpectrumSlot.HETCOR);
        NMRSpectrum hetcorlr = sd.spectrum(SpectrumSlot.HETCORLR);
        NMRSpectrum hhcosy = sd.spectrum(SpectrumSlot.HHCOSY);

        float[] carbonshifts = new float[carbonCount];
        for (int f = 0; f < carbonCount; f++) {
            StringTokenizer st = new StringTokenizer(line(br));
            float carbonshift = Float.parseFloat(st.nextToken().trim());
            float protonshift1 = Float.parseFloat(st.nextToken().trim());
            float protonshift2 = Float.parseFloat(st.nextToken().trim());
            carbonshifts[f] = carbonshift;
            NMRSignal cnmrSignal = new NMRSignal(bb());
            cnmrSignal.setShift(carbonshift, 0);
            cnmrSignal.intensity = 1;
            NMRSignal dept90Signal = new NMRSignal(bb());
            dept90Signal.setShift(carbonshift, 0);
            NMRSignal dept135Signal = new NMRSignal(bb());
            dept135Signal.setShift(carbonshift, 0);
            switch (setOfNodes[f].hCount) {
                case 1 -> {
                    dept90Signal.phase = NMRSignal.PHASE_POSITIVE;
                    dept135Signal.phase = NMRSignal.PHASE_POSITIVE;
                    dept90Signal.intensity = 1;
                    dept135Signal.intensity = 1;
                    dept90.addSignal(dept90Signal);
                    dept135.addSignal(dept135Signal);
                }
                case 2 -> {
                    dept135Signal.phase = NMRSignal.PHASE_NEGATIVE;
                    dept135Signal.intensity = -1;
                    dept135.addSignal(dept135Signal);
                }
                case 3 -> {
                    dept135Signal.phase = NMRSignal.PHASE_POSITIVE;
                    dept135Signal.intensity = 1;
                    dept135.addSignal(dept135Signal);
                }
                default -> {
                }
            }
            carbon1D.addSignal(cnmrSignal);
            if (protonshift1 != 0) hetcor.addSignal(hsqc(carbonshift, protonshift1));
            if (protonshift2 != 0) hetcor.addSignal(hsqc(carbonshift, protonshift2));
        }
        // H counts "undefined"
        for (int j = 0; j < heavyAtomsCount; j++) setOfNodes[j].hCount = -1;

        boolean[][] hhcosyTable = new boolean[heavyAtomsCount][heavyAtomsCount];
        boolean[][] hmbcTable = new boolean[heavyAtomsCount][heavyAtomsCount];
        int pairs = Integer.parseInt(line(br));
        for (int f = 0; f < pairs; f++) {
            StringTokenizer st = new StringTokenizer(line(br));
            int i = Integer.parseInt(st.nextToken().trim());
            int j = Integer.parseInt(st.nextToken().trim());
            hmbcTable[i - 1][j - 1] = true;
            hmbcTable[j - 1][i - 1] = true;
        }
        pairs = Integer.parseInt(line(br));
        for (int f = 0; f < pairs; f++) {
            StringTokenizer st = new StringTokenizer(line(br));
            int i = Integer.parseInt(st.nextToken().trim());
            int j = Integer.parseInt(st.nextToken().trim());
            hhcosyTable[i - 1][j - 1] = true;
            hhcosyTable[j - 1][i - 1] = true;
        }

        // HMBC table -> CH long-range spectrum
        for (int i = 0; i < hmbcTable.length; i++) {
            for (int j = i + 1; j < hmbcTable.length; j++) {
                if (!hmbcTable[i][j]) continue;
                List<NMRSignal> picked = hetcor.pickSignals(carbonshifts[j], NMRSpectrum.NUC_CARBON, 0.01f);
                if (!picked.isEmpty()) {
                    for (NMRSignal p : picked) hetcorlr.addSignal(hmbc(carbonshifts[i], p.getShift(NMRSpectrum.NUC_PROTON)));
                } else {
                    picked = hetcor.pickSignals(carbonshifts[i], NMRSpectrum.NUC_CARBON, 0.01f);
                    for (NMRSignal p : picked) hetcorlr.addSignal(hmbc(carbonshifts[j], p.getShift(NMRSpectrum.NUC_PROTON)));
                }
            }
        }
        // HH-COSY table (both directions) -> H,H-COSY spectrum
        for (int i = 0; i < hhcosyTable.length; i++) {
            for (int j = 0; j < hhcosyTable.length; j++) {
                if (!hhcosyTable[i][j]) continue;
                List<NMRSignal> picked1 = hetcor.pickSignals(carbonshifts[i], NMRSpectrum.NUC_CARBON, 0.01f);
                List<NMRSignal> picked2 = hetcor.pickSignals(carbonshifts[j], NMRSpectrum.NUC_CARBON, 0.01f);
                for (NMRSignal s1 : picked1) {
                    for (NMRSignal s2 : picked2) {
                        NMRSignal cosy = new NMRSignal(new String[] {NMRSpectrum.NUC_PROTON, NMRSpectrum.NUC_PROTON});
                        cosy.setShift(s1.getShift(NMRSpectrum.NUC_PROTON), 0);
                        cosy.setShift(s2.getShift(NMRSpectrum.NUC_PROTON), 1);
                        hhcosy.addSignal(cosy);
                    }
                }
            }
        }
    }

    /** Legacy {@code br.readLine().trim()} (NPE at end of file, caught as a parse error). */
    private static String line(BufferedReader br) throws IOException {
        String l = br.readLine();
        if (l == null) throw new NullPointerException("unexpected end of LUCY file");
        return l.trim();
    }

    private static String[] bb() {
        return new String[] {NMRSpectrum.NUC_CARBON};
    }

    /** 1JCH signal {@code (1H, 13C)}, intensity 0, phase NONE (legacy {@code new NMRSignal(SPECTYPE_HSQC)}). */
    private static NMRSignal hsqc(float carbon, float proton) {
        NMRSignal s = new NMRSignal(new String[] {NMRSpectrum.NUC_PROTON, NMRSpectrum.NUC_CARBON});
        s.setShift(carbon, NMRSpectrum.NUC_CARBON);
        s.setShift(proton, NMRSpectrum.NUC_PROTON);
        return s;
    }

    /** CH long-range signal {@code (1H, 13C)} (legacy {@code new NMRSignal(SPECTYPE_HMQC)}). */
    private static NMRSignal hmbc(float carbon, float proton) {
        return hsqc(carbon, proton);
    }
}
