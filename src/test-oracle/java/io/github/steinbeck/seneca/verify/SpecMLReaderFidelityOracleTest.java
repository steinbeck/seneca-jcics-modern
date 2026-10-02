package io.github.steinbeck.seneca.verify;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.steinbeck.seneca.io.SpecMLReader;
import io.github.steinbeck.seneca.model.NMRSpectrum;
import io.github.steinbeck.seneca.model.SenecaDataset;
import io.github.steinbeck.seneca.model.SpectrumSlot;

/**
 * Verifier "fidelity": SpecML reader on hand-written documents that no bundled file contains.
 *
 * <p>The 2001 {@code readSpectrumInfo} reassigns its loop variable ({@code node = newNode}) when it meets
 * the first {@code <spectruminfo>}, so the outer loop continues over the children of that element and a
 * second {@code <spectruminfo>} block of the same spectrum is never read. The port reads every block.
 */
@Tag("verify")
class SpecMLReaderFidelityOracleTest {

    @TempDir
    Path dir;

    static final String DOC = """
            <?xml version="1.0" ?>
            <senecadataset title="two-infos">
              <formula>C2H6</formula>
              <spectrum type="NMR Experiment" id="carbon1d" convention="Seneca">
                <spectruminfo>
                  <dimension>1</dimension>
                  <type>Carbon1D</type>
                  <axisinfo role="f1">
                    <nucleus>13C</nucleus>
                    <property>shift</property>
                    <unit>ppm</unit>
                  </axisinfo>
                </spectruminfo>
                <spectruminfo>
                  <type>DEPT-90</type>
                  <axisinfo role="f2">
                    <nucleus>1H</nucleus>
                    <property>shift</property>
                    <unit>ppm</unit>
                  </axisinfo>
                </spectruminfo>
                <signal id="carbon1d.p0">
                  <location role="f1">10.0</location>
                  <intensity type="relative">1.0</intensity>
                </signal>
              </spectrum>
            </senecadataset>
            """;

    static seneca.core.SenecaDataset legacyRead(Path file) throws Exception {
        Field docStart = seneca.core.SpecMLReader.class.getDeclaredField("docStart");
        docStart.setAccessible(true);
        docStart.setBoolean(null, false);
        Field startNode = seneca.core.SpecMLReader.class.getDeclaredField("startNode");
        startNode.setAccessible(true);
        startNode.set(null, null);
        return new seneca.core.SpecMLReader(file.toFile()).getSenecaDataset();
    }

    @Test
    void secondSpectrumInfoBlock() throws Exception {
        Path f = dir.resolve("two-infos.sml");
        Files.writeString(f, DOC, StandardCharsets.UTF_8);
        seneca.core.SenecaDataset legacy = legacyRead(f);
        SenecaDataset port = SpecMLReader.read(f);

        String legacyC = legacy.carbon1D.specType + " dim=" + legacy.carbon1D.dim + " nuclei=" + Arrays.toString(legacy.carbon1D.nucleus)
                + " signals=" + legacy.carbon1D.size();
        String legacyD = legacy.dept90.specType + " signals=" + legacy.dept90.size();
        NMRSpectrum pc = port.spectrum(SpectrumSlot.CARBON1D);
        NMRSpectrum pd = port.spectrum(SpectrumSlot.DEPT90);
        String portC = pc.specType + " dim=" + pc.dim + " nuclei=" + Arrays.toString(pc.nucleus) + " signals=" + pc.size();
        String portD = pd.specType + " signals=" + pd.size();
        System.out.println("[verify] legacy carbon1D: " + legacyC + " | dept90: " + legacyD);
        System.out.println("[verify] port   carbon1D: " + portC + " | dept90: " + portD);
        assertEquals(legacyC, portC, "carbon1D slot");
        assertEquals(legacyD, portD, "dept90 slot");
    }
}
