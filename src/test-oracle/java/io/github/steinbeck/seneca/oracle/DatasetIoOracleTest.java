package io.github.steinbeck.seneca.oracle;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.config.CarbonShiftAssigner;
import io.github.steinbeck.seneca.config.HHCOSYAssigner;
import io.github.steinbeck.seneca.config.HMBCAssigner;
import io.github.steinbeck.seneca.golden.GoldenFiles;
import io.github.steinbeck.seneca.io.CMLWriter;
import io.github.steinbeck.seneca.io.LucyDatasetImporter;
import io.github.steinbeck.seneca.io.SpecMLReader;
import io.github.steinbeck.seneca.io.SpecMLWriter;
import io.github.steinbeck.seneca.io.WinNmrPeakPicking;
import io.github.steinbeck.seneca.judges.HHCOSYJudge;
import io.github.steinbeck.seneca.judges.HMBCJudge;
import io.github.steinbeck.seneca.model.NMRSignal;
import io.github.steinbeck.seneca.model.NMRSpectrum;
import io.github.steinbeck.seneca.model.SenecaDataset;
import io.github.steinbeck.seneca.model.SpectrumSlot;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Differential tests of work package B against the 2001 classes (legacy/runnable/classes on the
 * test classpath, {@code mvn -Poracle test}): SpecML reading and STRICT writing (byte identity with
 * {@code SpecMLGenerator}), the CML writer, the three assigners, the LUCY import and the WinNMR paste,
 * on the 10 bundled datasets and on synthetic inputs. Datasets are compared through their SML text
 * (legacy {@code SpecMLGenerator.convert} vs port {@code SpecMLWriter.write(STRICT)}: name, formula,
 * atoms with H counts and shifts, every spectrum signal with intensity, normalize factor and phase,
 * schedules), plus the 2D assignment matrices, the molecular mass and the outcome (exception class).
 */
@Tag("B")
class DatasetIoOracleTest {

    @TempDir
    Path dir;

    // ------------------------------------------------------------------ legacy access

    /** The legacy reader keeps static state (quirk Q7); reset it before every read. */
    static seneca.core.SenecaDataset legacyRead(Path file) throws Exception {
        Field docStart = seneca.core.SpecMLReader.class.getDeclaredField("docStart");
        docStart.setAccessible(true);
        docStart.setBoolean(null, false);
        Field startNode = seneca.core.SpecMLReader.class.getDeclaredField("startNode");
        startNode.setAccessible(true);
        startNode.set(null, null);
        return new seneca.core.SpecMLReader(file.toFile()).getSenecaDataset();
    }

    static String legacySml(seneca.core.SenecaDataset sd) {
        return new seneca.core.SpecMLGenerator().convert(sd);
    }

    static String portSml(SenecaDataset sd) {
        return SpecMLWriter.write(sd, LegacyCompat.STRICT);
    }

    /** Outcome of an action: "ok" or the exception class name. */
    static String outcome(ThrowingRunnable r) {
        try {
            r.run();
            return "ok";
        } catch (Throwable t) {
            return t.getClass().getName();
        }
    }

    interface ThrowingRunnable {
        void run() throws Exception;
    }

    static void assertSameMatrix(boolean[][][] legacy, boolean[][][] port, String what) {
        if (legacy == null || port == null) {
            assertEquals(legacy == null, port == null, what + ": null matrix");
            return;
        }
        assertEquals(legacy.length, port.length, what);
        for (int f = 0; f < legacy.length; f++)
            for (int g = 0; g < legacy.length; g++) assertArrayEquals(legacy[f][g], port[f][g], what + " [" + f + "][" + g + "]");
    }

    /** Same state: SML text and both 2D matrices. */
    static void assertSameDataset(seneca.core.SenecaDataset legacy, SenecaDataset port, String what) {
        assertEquals(legacySml(legacy), portSml(port), what + ": SML text");
        assertSameMatrix(((seneca.judges.TwoDSpectrumJudge) legacy.getJudge("HMBCJudge")).assignment,
                port.getJudge(HMBCJudge.class).assignment, what + ": HMBC");
        assertSameMatrix(((seneca.judges.TwoDSpectrumJudge) legacy.getJudge("HHCOSYJudge")).assignment,
                port.getJudge(HHCOSYJudge.class).assignment, what + ": HHCOSY");
    }

    /** Runs the same operation on both datasets: same outcome, same resulting state. */
    static void compareOperation(String what, seneca.core.SenecaDataset legacy, SenecaDataset port,
                                 ThrowingRunnable onLegacy, ThrowingRunnable onPort) {
        String lo = outcome(onLegacy), po = outcome(onPort);
        assertEquals(lo, po, what + ": outcome");
        assertSameDataset(legacy, port, what);
    }

    // ------------------------------------------------------------------ bundled datasets

    @TestFactory
    List<DynamicTest> bundledDatasets() {
        List<DynamicTest> l = new ArrayList<>();
        for (String key : GoldenFiles.keys()) {
            if (key.contains("@")) continue;
            l.add(DynamicTest.dynamicTest(key, () -> {
                Path f = GoldenFiles.sml(key);
                seneca.core.SenecaDataset legacy = legacyRead(f);
                SenecaDataset port = SpecMLReader.read(f);
                assertSameDataset(legacy, port, key + " as read");
                assertEquals(new seneca.core.CMLGenerator().convert(legacy), CMLWriter.write(port), key + " CML as read");
                compareOperation(key + " CarbonShiftAssigner", legacy, port,
                        () -> new seneca.client.CarbonShiftAssigner(legacy).assign(),
                        () -> new CarbonShiftAssigner(port, LegacyCompat.STRICT).assign());
                compareOperation(key + " HHCOSYAssigner", legacy, port,
                        () -> assertEquals(new seneca.client.HHCOSYAssigner(legacy).assign(), new HHCOSYAssigner(port, io.github.steinbeck.seneca.LegacyCompat.STRICT).assign()),
                        () -> { });
                compareOperation(key + " HMBCAssigner", legacy, port,
                        () -> new seneca.client.HMBCAssigner(legacy).assign(),
                        () -> new HMBCAssigner(port, LegacyCompat.STRICT).assign());
                // second press: the matrices accumulate
                compareOperation(key + " HMBCAssigner again", legacy, port,
                        () -> new seneca.client.HMBCAssigner(legacy).assign(),
                        () -> new HMBCAssigner(port, LegacyCompat.STRICT).assign());
                assertEquals(new seneca.core.CMLGenerator().convert(legacy), CMLWriter.write(port), key + " CML configured");
            }));
        }
        return l;
    }

    // ------------------------------------------------------------------ synthetic assigner inputs

    static NMRSignal c(float shift, float intensity, int phase) {
        return new NMRSignal(new String[] {"13C"}, new float[] {shift}, intensity, phase);
    }

    static NMRSignal hc(float h, float c) {
        return new NMRSignal(new String[] {"1H", "13C"}, new float[] {h, c}, 1f, NMRSignal.PHASE_POSITIVE);
    }

    static NMRSignal hh(float a, float b) {
        return new NMRSignal(new String[] {"1H", "1H"}, new float[] {a, b}, 1f, NMRSignal.PHASE_POSITIVE);
    }

    static SenecaDataset synthetic(String formula, Float[] shifts, Consumer<SenecaDataset> spectra) {
        SenecaDataset sd = new SenecaDataset("synthetic");
        sd.setMolecularFormula(formula);
        if (shifts != null) {
            for (int i = 0; i < shifts.length; i++) {
                sd.setOfNodes[i].shift13C = shifts[i];
                sd.setOfNodes[i].hCount = -1;
            }
        }
        spectra.accept(sd);
        return sd;
    }

    record Case(String name, SenecaDataset sd) {
    }

    static List<Case> assignerCases() {
        List<Case> l = new ArrayList<>();
        l.add(new Case("isopropanol", synthetic("C3H8O", null, sd -> {
            sd.spectrum(SpectrumSlot.CARBON1D).addSignal(c(63.0f, 1f, 1));
            sd.spectrum(SpectrumSlot.CARBON1D).addSignal(c(25.0f, 2f, 1));
            sd.spectrum(SpectrumSlot.DEPT90).addSignal(c(63.02f, 1f, 1));
            sd.spectrum(SpectrumSlot.DEPT135).addSignal(c(62.99f, 1f, 1));
            sd.spectrum(SpectrumSlot.DEPT135).addSignal(c(25.0f, 1f, 1));
        })));
        l.add(new Case("multiplicity 4 onto an O", synthetic("C5H12O2", null, sd -> {
            sd.spectrum(SpectrumSlot.CARBON1D).addSignal(c(30.0f, 4f, 1));
            sd.spectrum(SpectrumSlot.CARBON1D).addSignal(c(32.0f, 1f, 1));
            sd.spectrum(SpectrumSlot.DEPT135).addSignal(c(30.0f, 1f, 1));
        })));
        l.add(new Case("multiplicity 4 past the atoms", synthetic("C5H12", null, sd -> {
            sd.spectrum(SpectrumSlot.CARBON1D).addSignal(c(30.0f, 4f, 1));
            sd.spectrum(SpectrumSlot.CARBON1D).addSignal(c(32.0f, 1f, 1));
        })));
        l.add(new Case("CH2 with DEPT-90", synthetic("C2H6", null, sd -> {
            sd.spectrum(SpectrumSlot.CARBON1D).addSignal(c(40.0f, 1f, 1));
            sd.spectrum(SpectrumSlot.CARBON1D).addSignal(c(20.0f, 1f, 1));
            sd.spectrum(SpectrumSlot.DEPT90).addSignal(c(40.01f, 1f, 1));
            sd.spectrum(SpectrumSlot.DEPT135).addSignal(c(40.0f, -1f, 2));
            sd.spectrum(SpectrumSlot.DEPT135).addSignal(c(20.0f, 1f, 1));
        })));
        l.add(new Case("odd intensities", synthetic("C6H12", null, sd -> {
            sd.spectrum(SpectrumSlot.CARBON1D).addSignal(c(130.0f, 0.3f, 1));
            sd.spectrum(SpectrumSlot.CARBON1D).addSignal(c(120.0f, 7.25f, 1));
            sd.spectrum(SpectrumSlot.CARBON1D).addSignal(c(30.0f, 0.71f, 1));
            sd.spectrum(SpectrumSlot.CARBON1D).addSignal(c(20.0f, -2f, 1));
            sd.spectrum(SpectrumSlot.DEPT135).addSignal(c(30.0f, -1f, 2));
        })));
        l.add(new Case("empty carbon spectrum", synthetic("C2H6", null, sd -> { })));
        l.add(new Case("no carbons", synthetic("N2O", null, sd -> sd.setOfNodes[0].hCount = 2)));
        Consumer<SenecaDataset> twoD = sd -> {
            sd.spectrum(SpectrumSlot.HETCOR).addSignal(hc(0.9f, 10.02f));
            sd.spectrum(SpectrumSlot.HETCOR).addSignal(hc(1.45f, 32.0f));
            sd.spectrum(SpectrumSlot.HETCOR).addSignal(hc(3.7f, 69.0f));
            sd.spectrum(SpectrumSlot.HETCORLR).addSignal(hc(3.7f, 10.0f));
            sd.spectrum(SpectrumSlot.HETCORLR).addSignal(hc(0.905f, 32.01f));
            sd.spectrum(SpectrumSlot.HETCORLR).addSignal(hc(1.45f, 69.0f));
            sd.spectrum(SpectrumSlot.HHCOSY).addSignal(hh(0.9f, 1.45f));
            sd.spectrum(SpectrumSlot.HHCOSY).addSignal(hh(1.45f, 3.7f));
        };
        Float[] s4 = {10.0f, 32.0f, 69.0f, 69.0f};
        l.add(new Case("2D", synthetic("C4H10", s4, twoD)));
        l.add(new Case("2D missing 1JCH partner", synthetic("C4H10", s4, twoD.andThen(sd -> {
            sd.spectrum(SpectrumSlot.HETCORLR).addSignal(hc(5.0f, 10.0f));
            sd.spectrum(SpectrumSlot.HHCOSY).addSignal(hh(0.9f, 9.9f));
        }))));
        l.add(new Case("2D empty candidate list", synthetic("C4H10", s4, twoD.andThen(sd -> {
            sd.spectrum(SpectrumSlot.HETCOR).addSignal(hc(2.0f, 50.0f));
            sd.spectrum(SpectrumSlot.HETCORLR).addSignal(hc(2.0f, 10.0f));
            sd.spectrum(SpectrumSlot.HHCOSY).addSignal(hh(2.0f, 0.9f));
        }))));
        l.add(new Case("2D carbon without shift", synthetic("C4H10", new Float[] {10.0f, null, 69.0f, 69.0f}, twoD)));
        l.add(new Case("COSY alternative index j", synthetic("C5H12", new Float[] {30f, 30f, 30f, 40f, 40f}, sd -> {
            sd.spectrum(SpectrumSlot.HETCOR).addSignal(hc(1.0f, 30.0f));
            sd.spectrum(SpectrumSlot.HETCOR).addSignal(hc(2.0f, 40.0f));
            sd.spectrum(SpectrumSlot.HHCOSY).addSignal(hh(2.0f, 1.0f));
            sd.spectrum(SpectrumSlot.HETCORLR).addSignal(hc(2.0f, 30.0f));
        })));
        return l;
    }

    /**
     * Each synthetic case is written once (DEFAULT writer: {@code <cml:molecule>}, which both readers
     * read) and read by both programs; then the same assigner sequence runs on both.
     */
    @TestFactory
    List<DynamicTest> syntheticAssignerInputs() {
        return assignerCases().stream().map(cs -> DynamicTest.dynamicTest(cs.name(), () -> {
            Path f = dir.resolve("case.sml");
            Files.writeString(f, SpecMLWriter.write(cs.sd(), LegacyCompat.DEFAULT), StandardCharsets.ISO_8859_1);
            seneca.core.SenecaDataset legacy = legacyRead(f);
            SenecaDataset port = SpecMLReader.read(f);
            assertSameDataset(legacy, port, cs.name() + " as read");
            boolean anyShift = false;
            for (var a : port.setOfNodes) if (a.shift13C != null) anyShift = true;
            if (!anyShift) {
                compareOperation(cs.name() + " CarbonShiftAssigner", legacy, port,
                        () -> new seneca.client.CarbonShiftAssigner(legacy).assign(),
                        () -> new CarbonShiftAssigner(port, LegacyCompat.STRICT).assign());
            }
            compareOperation(cs.name() + " HHCOSYAssigner", legacy, port,
                    () -> assertEquals(new seneca.client.HHCOSYAssigner(legacy).assign(), new HHCOSYAssigner(port, io.github.steinbeck.seneca.LegacyCompat.STRICT).assign()),
                    () -> { });
            compareOperation(cs.name() + " HMBCAssigner", legacy, port,
                    () -> new seneca.client.HMBCAssigner(legacy).assign(),
                    () -> new HMBCAssigner(port, LegacyCompat.STRICT).assign());
            compareOperation(cs.name() + " CML", legacy, port, () -> { }, () -> { });
            assertEquals(new seneca.core.CMLGenerator().convert(legacy), CMLWriter.write(port), cs.name() + " CML");
        })).toList();
    }

    // ------------------------------------------------------------------ LUCY import

    static String col(String formula, String[] atomLines, String[] bonds, String[] carbonLines, String[] hmbc, String[] cosy) {
        List<String> l = new ArrayList<>(List.of("LUCY 1.0", formula, String.valueOf(atomLines.length),
                "3", "C", "H", "O", String.valueOf(carbonLines.length), "x", "y"));
        l.addAll(List.of(atomLines));
        l.add(String.valueOf(bonds.length));
        l.addAll(List.of(bonds));
        for (int i = 0; i < atomLines.length; i++) l.add("C");
        l.addAll(List.of(carbonLines));
        l.add(String.valueOf(hmbc.length));
        l.addAll(List.of(hmbc));
        l.add(String.valueOf(cosy.length));
        l.addAll(List.of(cosy));
        return String.join("\n", l) + "\n";
    }

    static List<String[]> lucyFiles() {
        String[] atoms = {"1 3", "2 2", "3 1", "4 0", "5 1"};
        String[] carbons = {"10.0 0.9 0", "32.0 1.45 1.50", "69.0 3.7 0", "23.0 0 0"};
        String[] hmbc = {"1 3", "1 4", "2 3", "3 4"};
        String[] cosy = {"1 2", "2 3"};
        String ok = col("C4H10O", atoms, new String[0], carbons, hmbc, cosy);
        List<String[]> l = new ArrayList<>();
        l.add(new String[] {"plain", ok});
        l.add(new String[] {"known bonds", col("C4H10O", atoms, new String[] {"1 2 1", "2 3 1"}, carbons, hmbc, cosy)});
        l.add(new String[] {"truncated", ok.substring(0, ok.indexOf("4\n1 3"))});
        l.add(new String[] {"HMBC to the O", col("C4H10O", atoms, new String[0], carbons, new String[] {"1 3", "2 5"}, cosy)});
        l.add(new String[] {"CH4 and C with 4 H", col("C5H12O", new String[] {"1 4", "2 3", "3 3", "4 0", "5 1", "6 1"},
                new String[0], new String[] {"5.0 0.2 0", "11.0 0.8 0", "12.0 0.81 0", "40.0 0 0", "70.0 3.5 0"},
                new String[] {"4 1", "4 2", "5 3"}, new String[] {"1 1"})});
        l.add(new String[] {"bad formula", col("", atoms, new String[0], carbons, hmbc, cosy)});
        return l;
    }

    @TestFactory
    List<DynamicTest> lucyImport() {
        return lucyFiles().stream().map(c -> DynamicTest.dynamicTest(c[0], () -> {
            Path f = dir.resolve("data.col");
            Files.writeString(f, c[1], StandardCharsets.ISO_8859_1);
            seneca.core.SenecaDataset legacy = new seneca.client.LUCYFileHandler().importLUCYDataset(f.toFile());
            SenecaDataset port = LucyDatasetImporter.read(f, LegacyCompat.STRICT);
            assertEquals(legacy.getName(), port.getName());
            assertEquals(legacy.getMolecularMass(), port.getMolecularMass(), c[0] + " mass");
            assertEquals(legacy.annealingSchedules.size(), port.annealingSchedules.size());
            if (legacy.setOfNodes == null) {
                assertEquals(null, port.setOfNodes);
                assertEquals(legacy.getMolecularFormula(), port.getMolecularFormula());
            } else {
                assertEquals(legacySml(legacy), portSml(port), c[0] + " STRICT");
            }
        })).toList();
    }

    // ------------------------------------------------------------------ WinNMR paste

    static final String PP_1D = """
            WIN-NMR 1D
            Peak Picking results:
            Peak   Point   Frequency   ppm   Intensity   %Int
            1   1024   7543.21   75.0123   1234.5   100.0
            2   2048   5432.10   54.25   -617.2   -50.0
            3   3000   3210.00   32.1   0.0   0.0
            4   3500   1000.00   10.5   12.0   1.0
            """;

    static Object legacyPanel(seneca.core.NMRSpectrum spectrum) throws Exception {
        Field uf = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        uf.setAccessible(true);
        Object unsafe = uf.get(null);
        Method alloc = unsafe.getClass().getMethod("allocateInstance", Class.class);
        Object panel = alloc.invoke(unsafe, seneca.client.NMRSpectrumTablePanel.class);
        seneca.client.NMRSpectrumTablePanel.class.getField("nmrSpectrum").set(panel, spectrum);
        return panel;
    }

    /** Legacy {@code pasteWinNMRpp} without the clipboard: the same marker test, then the import method. */
    static void legacyPaste(String text, seneca.core.NMRSpectrum spectrum) throws Exception {
        Object panel = legacyPanel(spectrum);
        String m = text.indexOf("Peak Picking results:") > 0 ? "importWinNMR1D" : "importWinNMR2D";
        Method im = seneca.client.NMRSpectrumTablePanel.class.getDeclaredMethod(m, String.class);
        im.setAccessible(true);
        try {
            im.invoke(panel, text);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof Exception x) throw x;
            throw e;
        }
    }

    static List<String> signals(List<NMRSignal> l) {
        List<String> r = new ArrayList<>();
        for (NMRSignal g : l) r.add(java.util.Arrays.toString(g.shift) + " " + g.intensity + " " + g.phase + " " + g.normalizeFactor);
        return r;
    }

    static List<String> legacySignals(seneca.core.NMRSpectrum s) {
        List<String> r = new ArrayList<>();
        for (int i = 0; i < s.size(); i++) {
            seneca.core.NMRSignal g = (seneca.core.NMRSignal) s.elementAt(i);
            r.add(java.util.Arrays.toString(g.shift) + " " + g.intensity + " " + g.phase + " " + g.normalizeFactor);
        }
        return r;
    }

    @TestFactory
    List<DynamicTest> winNmrPaste() throws IOException {
        // targets: the eurabidiol carbon1D (1D), hetcor (2D) and hhcosy (read empty: dim 0)
        String[][] cases = {
            {"1D", "carbon1D", PP_1D},
            {"1D truncated", "carbon1D", PP_1D.substring(0, PP_1D.indexOf("   1.0\n"))},
            {"2D", "hetcor", "1.25 74.3\n2.5 41.5\n3.75 20.0\n"},
            {"2D odd", "hetcor", "1.25 74.3 2.5"},
            {"2D into a dim-0 spectrum", "hhcosy", "1.0 2.0 3.0 4.0"},
            {"1D into a dim-0 spectrum", "hhcosy", PP_1D},
            {"empty", "hetcor", ""},
        };
        List<DynamicTest> l = new ArrayList<>();
        for (String[] c : cases) {
            l.add(DynamicTest.dynamicTest(c[0], () -> {
                Path f = GoldenFiles.sml("eurabidiol");
                seneca.core.SenecaDataset legacy = legacyRead(f);
                SenecaDataset port = SpecMLReader.read(f);
                seneca.core.NMRSpectrum ls = (seneca.core.NMRSpectrum) seneca.core.SenecaDataset.class.getField(c[1]).get(legacy);
                NMRSpectrum ps = port.spectrum(switch (c[1]) {
                    case "carbon1D" -> SpectrumSlot.CARBON1D;
                    case "hetcor" -> SpectrumSlot.HETCOR;
                    default -> SpectrumSlot.HHCOSY;
                });
                String lo = outcome(() -> legacyPaste(c[2], ls));
                String po = outcome(() -> WinNmrPeakPicking.paste(c[2], ps, LegacyCompat.STRICT));
                assertEquals(lo, po, c[0] + ": outcome");
                assertEquals(legacySignals(ls), signals(ps.signals), c[0] + ": signals");
            }));
        }
        return l;
    }
}
