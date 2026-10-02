package io.github.steinbeck.seneca.io;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.config.GuiRecipe;
import io.github.steinbeck.seneca.config.Profile;
import io.github.steinbeck.seneca.golden.GoldenFiles;
import io.github.steinbeck.seneca.model.AnnealingSchedule;
import io.github.steinbeck.seneca.model.NMRSignal;
import io.github.steinbeck.seneca.model.NMRSpectrum;
import io.github.steinbeck.seneca.model.SenecaDataset;
import io.github.steinbeck.seneca.model.SpectrumSlot;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SpecML reader and writer: legacy reading rules, STRICT writer text, DEFAULT round trip (DV-08).
 * Byte identity of the STRICT writer with the 2001 {@code SpecMLGenerator} is checked against the
 * legacy classes in {@code oracle/DatasetIoOracleTest} ({@code -Poracle}).
 */
@Tag("B")
class SpecMLTest {

    static SenecaDataset parse(String xml) throws IOException {
        return SpecMLReader.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.ISO_8859_1)), "test.sml");
    }

    static final String SMALL = """
            <?xml version="1.0" ?>
            <senecadataset title="SMALL">
              <formula>C3H8O</formula>
              <cml:molecule xmlns:cml="http://www.xml-cml.org/cml.dtd" title="ConnectionTable">
                <atom id="a0">
                  <string builtin="elementType">C</string>
                  <integer builtin="hydrogenCount">3</integer>
                  <float title="assignedCarbonShift">25.5</float>
                </atom>
                <atom id="a1">
                  <string builtin="elementType">C</string>
                  <float title="assignedCarbonShift">64.25</float>
                </atom>
                <atom id="x7">
                  <string builtin="elementType">O</string>
                  <integer builtin="hydrogenCount">1</integer>
                </atom>
              </cml:molecule>
              <spectrum xmlns="http://www.nmrshiftdb.org/" type="NMR Experiment" id="carbon1d" convention="Seneca">
                <spectruminfo>
                  <dimension>1</dimension>
                  <type>Carbon1D</type>
                  <frequency unit="MHz">100.5</frequency>
                  <solvent>CDCl3</solvent>
                  <standard role="calibration">TMS</standard>
                  <axisinfo role="f1"><nucleus>13C</nucleus><property>shift</property><unit>ppm</unit></axisinfo>
                </spectruminfo>
                <signal id="carbon1d.p0">
                  <location role="f1">64.25</location>
                  <intensity type="relative">2.0</intensity>
                  <normalizefactor>0.5</normalizefactor>
                  <phase>1</phase>
                </signal>
              </spectrum>
              <spectrum xmlns="http://www.nmrshiftdb.org/" type="NMR Experiment" id="weird" convention="Seneca">
                <spectruminfo>
                  <dimension>2</dimension>
                  <type>Something else</type>
                </spectruminfo>
              </spectrum>
              <spectrum xmlns="http://www.nmrshiftdb.org/" type="NMR Experiment" id="noesy" convention="Seneca">
                <spectruminfo>
                  <dimension>2</dimension>
                  <type>NOESY</type>
                  <frequency unit="MHz">0.0</frequency>
                  <solvent>null</solvent>
                  <standard role="calibration"></standard>
                </spectruminfo>
              </spectrum>
              <annealingschedule>
                <initialAcceptanceProbability>0.7</initialAcceptanceProbability>
                <noOfSteps>5</noOfSteps>
                <plateauSteps>1000</plateauSteps>
              </annealingschedule>
              <judge title="HMBCJudge"></judge>
            </senecadataset>
            """;

    @Test
    void readerFollowsLegacyRules() throws IOException {
        SenecaDataset sd = parse(SMALL);
        assertEquals("SMALL", sd.getName());
        assertEquals("C3H8O", sd.getMolecularFormula());
        assertEquals("none", sd.getDescription());
        HeavyAtom[] a = sd.getSetOfNodes();
        assertEquals(3, a.length);
        assertEquals("a0", a[0].name);
        assertEquals(3, a[0].hCount);
        assertEquals(25.5f, a[0].shift13C);
        assertEquals(-1, a[1].hCount, "hydrogenCount absent -> -1 (undefined)");
        assertEquals("x7", a[2].name);
        assertNull(a[2].shift13C);
        for (int i = 0; i < 3; i++) assertEquals(i, a[i].number);
        assertEquals(4, a[0].maxBondOrderSum);
        assertEquals(2, a[2].maxBondOrderSum);

        NMRSpectrum c = sd.spectrum(SpectrumSlot.CARBON1D);
        assertEquals("carbon1d", c.name);
        assertEquals(1, c.dim);
        assertArrayEquals(new String[] {"13C"}, c.nucleus);
        assertEquals(100.5f, c.spectrometerFrequency);
        assertEquals("CDCl3", c.solvent);
        assertEquals("", c.standard, "legacy reads <standard> but never stores it");
        NMRSignal s = c.getSignal(0);
        assertEquals(64.25f, s.shift[0]);
        assertEquals(2.0f, s.intensity);
        assertEquals(0.5f, s.normalizeFactor);
        assertEquals(NMRSignal.PHASE_POSITIVE, s.phase);
        assertSame(c.nucleus, s.nucleus, "legacy signals share the spectrum's nucleus array");

        NMRSpectrum noesy = sd.spectrum(SpectrumSlot.NOESY);
        assertEquals(0, noesy.dim, "an empty spectrum without axisinfo is read as dim 0");
        assertEquals(0, noesy.nucleus.length);
        // unread slots keep the constructor defaults; the unknown type was dropped
        assertEquals(2, sd.spectrum(SpectrumSlot.HETCOR).dim);

        assertEquals(1, sd.annealingSchedules.size());
        AnnealingSchedule as = sd.annealingSchedules.get(0);
        assertEquals(0.7, as.initialAcceptanceProbability);
        assertEquals(1000, as.plateauSteps);
        assertEquals(50, as.maxUphillSteps, "missing field keeps the default");
        assertEquals(0.95, as.asymptoticCoolingFactor);

        assertEquals(7, sd.judges.size(), "init(): the 7 judges");
        assertTrue(sd.judges.stream().noneMatch(j -> j.getEnabled()));
    }

    @Test
    void plainMoleculeIsIgnoredAndFormulaOrderMatters() throws IOException {
        String plain = SMALL.replace("cml:molecule", "molecule");
        SenecaDataset sd = parse(plain);
        HeavyAtom[] a = sd.getSetOfNodes();
        assertEquals(4, a.length, "atoms from the formula C3H8O");
        for (HeavyAtom x : a) {
            assertEquals(0, x.hCount);
            assertNull(x.shift13C);
            assertEquals("", x.name);
        }
        // a <formula> after <cml:molecule> replaces the molecule's atoms again (document order)
        String formulaLast = SMALL.replace("  <formula>C3H8O</formula>\n", "")
                .replace("</cml:molecule>\n", "</cml:molecule>\n  <formula>C3H8O</formula>\n");
        SenecaDataset sd2 = parse(formulaLast);
        assertEquals(4, sd2.getSetOfNodes().length);
        // without <formula> the formula is derived from the molecule (negative H counts subtracted)
        SenecaDataset sd3 = parse(SMALL.replace("  <formula>C3H8O</formula>\n", ""));
        assertEquals("C2H3O", sd3.getMolecularFormula(), "H = 3 + (-1) + 1");
    }

    @Test
    void everyReadIsIndependent() throws IOException {
        // legacy quirk Q7 (static state) is not reproduced
        SenecaDataset a = SpecMLReader.read(GoldenFiles.sml("pinene"));
        SenecaDataset b = SpecMLReader.read(GoldenFiles.sml("narelin"));
        assertEquals("C10H16", a.getMolecularFormula());
        assertEquals("C20H20N2O4", b.getMolecularFormula());
    }

    @Test
    void unreadableFilesThrowIOException() {
        assertThrows(IOException.class, () -> parse("<senecadataset title=\"x\"><formula>C</formula>"));
        assertThrows(IOException.class, () -> parse("<root><other>1</other></root>"));
        assertThrows(IOException.class, () -> parse("<senecadataset><formula>C</formula></senecadataset>"));
        assertThrows(IOException.class, () -> parse(SMALL.replace("<type>NOESY</type>", "")));
        assertThrows(IOException.class, () -> parse(SMALL.replace("<integer builtin=\"hydrogenCount\">3", "<integer builtin=\"hydrogenCount\"> 3")),
                "Integer.parseInt does not trim, as legacy");
    }

    @Test
    void externalEntitiesAreNotResolved() throws IOException {
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE s [<!ENTITY x SYSTEM \"file:///etc/hosts\">]>"
                + "<senecadataset title=\"t\"><description>&x;</description><formula>C2</formula></senecadataset>";
        SenecaDataset sd = parse(xxe);
        assertEquals("", sd.getDescription());
        assertEquals(2, sd.getSetOfNodes().length);
    }

    @Test
    void strictWriterText() throws IOException {
        SenecaDataset sd = parse(SMALL);
        String expected = """
                <?xml version="1.0" ?>
                <senecadataset title="SMALL">
                  <formula>C3H8O</formula>
                  <molecule xmlns="http://www.xml-cml.org/cml.dtd" title="ConnectionTable">
                    <atom id="a0">
                      <string builtin="elementType">C</string>
                      <integer builtin="hydrogenCount">3</integer>
                      <float title="assignedCarbonShift">25.5</float>
                    </atom>
                    <atom id="a1">
                      <string builtin="elementType">C</string>
                      <float title="assignedCarbonShift">64.25</float>
                    </atom>
                    <atom id="a2">
                      <string builtin="elementType">O</string>
                      <integer builtin="hydrogenCount">1</integer>
                    </atom>
                  </molecule>
                  <spectrum xmlns="http://www.nmrshiftdb.org/" type="NMR Experiment" id="carbon1d" convention="Seneca">
                    <spectruminfo>
                      <dimension>1</dimension>
                      <type>Carbon1D</type>
                      <frequency unit="MHz">100.5</frequency>
                      <solvent>CDCl3</solvent>
                      <standard role="calibration"></standard>
                      <axisinfo role="f1">
                        <nucleus>13C</nucleus>
                        <property>shift</property>
                        <unit>ppm</unit>
                      </axisinfo>
                    </spectruminfo>
                    <signal id="carbon1d.p0">
                      <location role="f1">64.25</location>
                      <intensity type="relative">2.0</intensity>
                      <normalizefactor>0.5</normalizefactor>
                      <phase>1</phase>
                    </signal>
                  </spectrum>
                """;
        String text = SpecMLWriter.write(sd, LegacyCompat.STRICT);
        assertTrue(text.startsWith(expected), text);
        assertTrue(text.contains("""
                  <spectrum xmlns="http://www.nmrshiftdb.org/" type="NMR Experiment" id="dept90" convention="Seneca">
                    <spectruminfo>
                      <dimension>1</dimension>
                      <type>DEPT-90</type>
                      <frequency unit="MHz">0.0</frequency>
                      <solvent></solvent>
                      <standard role="calibration"></standard>
                    </spectruminfo>
                  </spectrum>
                """), text);
        assertTrue(text.endsWith("""
                  <annealingschedule>
                    <initialAcceptanceProbability>0.7</initialAcceptanceProbability>
                    <maxUphillSteps>50</maxUphillSteps>
                    <plateauSteps>1000</plateauSteps>
                    <asymptoticCoolingFactor>0.95</asymptoticCoolingFactor>
                  </annealingschedule>
                </senecadataset>
                """), text);
    }

    @Test
    void strictRoundTripLosesTheMoleculeBlock() throws IOException {
        SenecaDataset sd = parse(SMALL);
        SenecaDataset back = parse(SpecMLWriter.write(sd, LegacyCompat.STRICT));
        assertEquals(4, back.getSetOfNodes().length, "D8: atoms come from the formula again");
        for (HeavyAtom x : back.getSetOfNodes()) assertNull(x.shift13C);
    }

    @Test
    void strictWriterFailsLikeLegacyWithoutAtoms() {
        SenecaDataset sd = new SenecaDataset("empty");
        assertThrows(NullPointerException.class, () -> SpecMLWriter.write(sd, LegacyCompat.STRICT));
        String text = SpecMLWriter.write(sd, LegacyCompat.DEFAULT);
        assertFalse(text.contains("molecule"));
    }

    @TestFactory
    List<DynamicTest> defaultRoundTripKeepsTheConfiguredDataset() {
        return GoldenFiles.keys().stream().map(key -> DynamicTest.dynamicTest(key, () -> {
            GuiRecipe.Prepared p = GuiRecipe.prepare(GoldenFiles.sml(key), key, Profile.GUI);
            SenecaDataset sd = p.sd;
            sd.setDescription("Tutorial <dataset> & \"quotes\"");
            sd.setMolecularMass(123.25f);
            sd.setName(sd.getName() + " & <co>");
            String text = SpecMLWriter.write(sd, LegacyCompat.DEFAULT);
            SenecaDataset back = parse(text);
            assertEquals(sd.getName(), back.getName());
            assertEquals(sd.getDescription(), back.getDescription());
            assertEquals(sd.getMolecularMass(), back.getMolecularMass());
            assertEquals(sd.getMolecularFormula(), back.getMolecularFormula());
            HeavyAtom[] a = sd.getSetOfNodes(), b = back.getSetOfNodes();
            assertEquals(a.length, b.length);
            for (int i = 0; i < a.length; i++) {
                assertEquals(a[i].symbol, b[i].symbol);
                assertEquals(a[i].hCount, b[i].hCount, key + " atom " + i + " H");
                assertEquals(a[i].shift13C, b[i].shift13C, key + " atom " + i + " shift");
            }
            // a second DEFAULT write of the reloaded dataset is identical (spectra, schedules included)
            assertEquals(text, SpecMLWriter.write(back, LegacyCompat.DEFAULT));
        })).toList();
    }

    @TestFactory
    List<DynamicTest> legacyFilesReadWriteReadIsStable() {
        return GoldenFiles.keys().stream().filter(k -> !k.contains("@")).map(key -> DynamicTest.dynamicTest(key, () -> {
            Path f = GoldenFiles.sml(key);
            SenecaDataset sd = SpecMLReader.read(f);
            String strict = SpecMLWriter.write(sd, LegacyCompat.STRICT);
            // STRICT text read back and written again is a fixed point when no molecule block was read
            boolean hadMolecule = sd.getSetOfNodes()[0].shift13C != null || sd.getSetOfNodes()[0].hCount != 0;
            if (!hadMolecule) assertEquals(strict, SpecMLWriter.write(parse(strict), LegacyCompat.STRICT));
            String def = SpecMLWriter.write(sd, LegacyCompat.DEFAULT);
            assertEquals(def, SpecMLWriter.write(parse(def), LegacyCompat.DEFAULT));
        })).toList();
    }
}
