package io.github.steinbeck.seneca.io;

import java.util.ArrayList;
import java.util.List;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.model.AnnealingSchedule;
import io.github.steinbeck.seneca.model.NMRSignal;
import io.github.steinbeck.seneca.model.NMRSpectrum;
import io.github.steinbeck.seneca.model.SenecaDataset;
import io.github.steinbeck.seneca.model.SpectrumSlot;

/**
 * Writes a SENECA dataset as {@code .sml}. Port of {@code legacy/original/src/seneca/core/SpecMLGenerator.java};
 * format in docs/legacy/feature-inventory.md 7.1 (two-space indent, bond ids from b2, axisinfo only for
 * spectra with signals, taken from the first signal's nuclei; judges not written, D9).
 *
 * <p><b>STRICT</b>: the text of {@code SpecMLGenerator.convert} byte for byte: the molecule block is
 * written as {@code <molecule xmlns=...>}, which the reader ignores (D8: H counts and shift assignments
 * are lost on save + load), values are not XML-escaped, description and molecular mass are not
 * written, and a dataset without atoms throws {@link NullPointerException} like 2001.
 *
 * <p><b>DEFAULT</b> (DEVIATIONS.md DV-08): the same document, except
 * <ul>
 *   <li>the molecule block is written as {@code <cml:molecule xmlns:cml="http://www.xml-cml.org/cml.dtd"
 *       title="ConnectionTable">} ... {@code </cml:molecule>}, the form of narelin/preconen/triterpen.sml
 *       that the 2001 reader (and the port's reader) reads: H counts and assigned shifts survive a
 *       save/load round trip, also when the file is opened with the 2001 program;</li>
 *   <li>{@code <description>} and {@code <molecularmass>} (only when the mass is not 0) follow
 *       {@code <formula>}; the 2001 reader ignores unknown elements;</li>
 *   <li>text and attribute values are XML-escaped ({@code & < > "}), so names such as "A&amp;B" no
 *       longer produce an unreadable file;</li>
 *   <li>a dataset without atoms is written without a molecule block instead of throwing;</li>
 *   <li>a spectrum without solvent or calibration standard gets empty {@code <solvent>} and
 *       {@code <standard>} elements (2001: the text "null", which read back as the solvent "null").</li>
 * </ul>
 * Reading of existing (2001) files is unchanged in both modes. The legacy writer's static
 * {@code indentLevel} (not reentrant, left wrong after an exception) is not reproduced.
 */
public final class SpecMLWriter {

    private static final String INDENT = "  ";

    private SpecMLWriter() {
    }

    public static String write(SenecaDataset sd, LegacyCompat compat) {
        return new Writer(compat).convert(sd);
    }

    private static final class Writer {
        private final boolean strict;
        private int indentLevel = 0;

        Writer(LegacyCompat compat) {
            this.strict = compat.strict();
        }

        private String indent() {
            return INDENT.repeat(indentLevel);
        }

        /** Value text: verbatim in STRICT, XML-escaped in DEFAULT. */
        /** STRICT: like {@link #v} ("null" for null, which reads back as the text "null"); DEFAULT: empty for null. */
        private String optional(Object o) {
            return o == null && !strict ? "" : v(o);
        }

        private String v(Object o) {
            String s = String.valueOf(o);
            if (strict) return s;
            StringBuilder b = new StringBuilder(s.length());
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '&' -> b.append("&amp;");
                    case '<' -> b.append("&lt;");
                    case '>' -> b.append("&gt;");
                    case '"' -> b.append("&quot;");
                    default -> b.append(c);
                }
            }
            return b.toString();
        }

        String convert(SenecaDataset sd) {
            StringBuilder cml = new StringBuilder();
            cml.append("<?xml version=\"1.0\" ?>\n");
            cml.append("<senecadataset title=\"").append(v(sd.getName())).append("\">\n");
            indentLevel++;
            cml.append(indent()).append("<formula>").append(v(sd.getMolecularFormula())).append("</formula>\n");
            if (!strict) {
                cml.append(indent()).append("<description>").append(v(sd.getDescription())).append("</description>\n");
                if (sd.getMolecularMass() != 0f) {
                    cml.append(indent()).append("<molecularmass>").append(sd.getMolecularMass()).append("</molecularmass>\n");
                }
            }
            HeavyAtom[] atoms = sd.getSetOfNodes();
            if (atoms == null && strict) throw new NullPointerException("dataset has no atoms (legacy SpecMLGenerator.convert(Atom[]))");
            if (atoms != null) cml.append(convert(atoms));
            for (SpectrumSlot slot : SpectrumSlot.values()) cml.append(convert(sd.spectrum(slot)));
            for (AnnealingSchedule s : sd.annealingSchedules) cml.append(convert(s));
            // legacy: sd.serverList (<serverinfo>, RMI) - always empty in the port
            indentLevel--;
            cml.append("</senecadataset>\n");
            return cml.toString();
        }

        private String convert(HeavyAtom[] son) {
            StringBuilder cml = new StringBuilder();
            List<HeavyAtom> printedAtoms = new ArrayList<>();
            String element = strict ? "molecule" : "cml:molecule";
            String ns = strict ? "xmlns=\"http://www.xml-cml.org/cml.dtd\"" : "xmlns:cml=\"http://www.xml-cml.org/cml.dtd\"";
            cml.append(indent()).append('<').append(element).append(' ').append(ns).append(" title=\"ConnectionTable\">\n");
            indentLevel++;
            for (HeavyAtom a : son) {
                cml.append(indent()).append("<atom id=\"a").append(a.number).append("\">\n");
                indentLevel++;
                cml.append(indent()).append("<string builtin=\"elementType\">").append(v(a.symbol)).append("</string>\n");
                if (a.hCount >= 0) {
                    cml.append(indent()).append("<integer builtin=\"hydrogenCount\">").append(a.hCount).append("</integer>\n");
                }
                if (a.shift13C != null) {
                    cml.append(indent()).append("<float title=\"assignedCarbonShift\">").append(a.shift13C).append("</float>\n");
                }
                indentLevel--;
                cml.append(indent()).append("</atom>\n");
            }
            int bondId = 1;
            for (HeavyAtom a : son) {
                printedAtoms.add(a);
                for (int j = 0; j < a.degree; j++) {
                    if (!containsIdentity(printedAtoms, a.nodeTable[j])) {
                        bondId++;
                        cml.append(indent()).append("<bond id=\"b").append(bondId).append("\" atomRefs=\"a").append(a.number)
                                .append(" a").append(a.nodeTable[j].number).append("\">\n");
                        indentLevel++;
                        cml.append(indent()).append("<string builtin=\"order\">").append(a.bondTable[j]).append("</string>\n");
                        indentLevel--;
                        cml.append(indent()).append("</bond>\n");
                    }
                }
            }
            indentLevel--;
            cml.append(indent()).append("</").append(element).append(">\n");
            return cml.toString();
        }

        private String convert(NMRSpectrum s) {
            StringBuilder cml = new StringBuilder();
            cml.append(indent()).append("<spectrum xmlns=\"http://www.nmrshiftdb.org/\" type=\"NMR Experiment\" id=\"")
                    .append(v(s.name)).append("\" convention=\"Seneca\">\n");
            indentLevel++;
            cml.append(indent()).append("<spectruminfo>\n");
            indentLevel++;
            cml.append(indent()).append("<dimension>").append(s.dim).append("</dimension>\n");
            cml.append(indent()).append("<type>").append(v(s.specType)).append("</type>\n");
            cml.append(indent()).append("<frequency unit=\"MHz\">").append(s.getSpectrometerFrequency()).append("</frequency>\n");
            cml.append(indent()).append("<solvent>").append(optional(s.getSolvent())).append("</solvent>\n");
            cml.append(indent()).append("<standard role=\"calibration\">").append(optional(s.getStandard())).append("</standard>\n");
            if (s.size() == 0) {
                indentLevel--;
                cml.append(indent()).append("</spectruminfo>\n");
                indentLevel--;
                cml.append(indent()).append("</spectrum>\n");
                return cml.toString();
            }
            String[] nuclei = s.getSignal(0).nucleus;
            for (int i = 0; i < nuclei.length; i++) {
                cml.append(indent()).append("<axisinfo role=\"f").append(i + 1).append("\">\n");
                indentLevel++;
                cml.append(indent()).append("<nucleus>").append(v(nuclei[i])).append("</nucleus>\n");
                cml.append(indent()).append("<property>shift</property>\n");
                cml.append(indent()).append("<unit>ppm</unit>\n");
                indentLevel--;
                cml.append(indent()).append("</axisinfo>\n");
            }
            indentLevel--;
            cml.append(indent()).append("</spectruminfo>\n");
            for (int i = 0; i < s.size(); i++) {
                NMRSignal g = s.getSignal(i);
                cml.append(indent()).append("<signal id=\"").append(v(s.name)).append(".p").append(i).append("\">\n");
                indentLevel++;
                for (int j = 0; j < g.shift.length; j++) {
                    cml.append(indent()).append("<location role=\"f").append(j + 1).append("\">").append(g.shift[j]).append("</location>\n");
                }
                cml.append(indent()).append("<intensity type=\"relative\">").append(g.intensity).append("</intensity>\n");
                cml.append(indent()).append("<normalizefactor>").append(g.normalizeFactor).append("</normalizefactor>\n");
                cml.append(indent()).append("<phase>").append(g.phase).append("</phase>\n");
                indentLevel--;
                cml.append(indent()).append("</signal>\n");
            }
            indentLevel--;
            cml.append(indent()).append("</spectrum>\n");
            return cml.toString();
        }

        private String convert(AnnealingSchedule a) {
            StringBuilder cml = new StringBuilder();
            cml.append(indent()).append("<annealingschedule>\n");
            indentLevel++;
            cml.append(indent()).append("<initialAcceptanceProbability>").append(a.initialAcceptanceProbability).append("</initialAcceptanceProbability>\n");
            cml.append(indent()).append("<maxUphillSteps>").append(a.maxUphillSteps).append("</maxUphillSteps>\n");
            cml.append(indent()).append("<plateauSteps>").append(a.plateauSteps).append("</plateauSteps>\n");
            cml.append(indent()).append("<asymptoticCoolingFactor>").append(a.asymptoticCoolingFactor).append("</asymptoticCoolingFactor>\n");
            indentLevel--;
            cml.append(indent()).append("</annealingschedule>\n");
            return cml.toString();
        }
    }

    /** {@code Vector.contains} on atoms = identity (compchem atoms do not override equals). */
    static boolean containsIdentity(List<HeavyAtom> list, HeavyAtom a) {
        for (HeavyAtom x : list) if (x == a) return true;
        return false;
    }
}
