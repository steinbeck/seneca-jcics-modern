package io.github.steinbeck.seneca.io;

import java.util.ArrayList;
import java.util.List;

import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.model.NMRSignal;
import io.github.steinbeck.seneca.model.NMRSpectrum;
import io.github.steinbeck.seneca.model.SenecaDataset;
import io.github.steinbeck.seneca.model.SpectrumSlot;

/**
 * The 2001 "CML document" written on close-with-save. Port of
 * {@code legacy/original/src/seneca/core/CMLGenerator.java}; feature-inventory 7.2 (D11). The legacy
 * output is not well-formed XML; whether the port keeps it (export only) or replaces close-with-save
 * by SML is a GUI-phase decision.
 *
 * <p>The text is reproduced byte for byte, defects included: {@code <float title="Frequency">} and
 * the two {@code <stringArray>} elements are closed with {@code </string>}; the closing
 * {@code </floatArray>} is preceded by the current indent instead of a line break; DEPT spectra are
 * omitted; atoms carry only their element type; and the indent level is decremented only for spectra
 * with signals, so every empty spectrum indents all following lines one level deeper. Only the
 * description is escaped ({@code &}, {@code <}). There is no reader.
 */
public final class CMLWriter {

    private static final String INDENT = "  ";

    private CMLWriter() {
    }

    public static String write(SenecaDataset sd) {
        return new Writer().convert(sd);
    }

    private static final class Writer {
        private int indentLevel = 0;

        private String indent() {
            return INDENT.repeat(Math.max(0, indentLevel));
        }

        String convert(SenecaDataset sd) {
            StringBuilder cml = new StringBuilder();
            cml.append("<?xml version=\"1.0\" ?>\n");
            cml.append("<!DOCTYPE molecule SYSTEM \"cml.dtd\" []>\n");
            cml.append("<molecule convention=\"SenecaDocument\">\n");
            indentLevel++;
            cml.append(indent()).append("<string title=\"DocumentName\">").append(sd.getName()).append("</string>\n");
            cml.append(indent()).append("<string title=\"DocumentDescription\">\n");
            indentLevel++;
            cml.append(indent()).append(escape(sd.getDescription())).append("\n");
            indentLevel--;
            cml.append(indent()).append("</string>\n");
            cml.append(indent()).append("<formula>").append(sd.getMolecularFormula()).append("</formula>\n");
            cml.append(indent()).append("<float title=\"Mass\">").append(sd.getMolecularMass()).append("</float>\n");
            cml.append(convert(sd.getSetOfNodes()));
            cml.append(convert(sd.spectrum(SpectrumSlot.CARBON1D)));
            cml.append(convert(sd.spectrum(SpectrumSlot.HETCOR)));
            cml.append(convert(sd.spectrum(SpectrumSlot.HETCORLR)));
            cml.append(convert(sd.spectrum(SpectrumSlot.HHCOSY)));
            cml.append(convert(sd.spectrum(SpectrumSlot.NOESY)));
            indentLevel--;
            cml.append("</molecule>\n");
            return cml.toString();
        }

        static String escape(String s) {
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '&') result.append("&amp;");
                else if (c == '<') result.append("&lt;");
                else result.append(c);
            }
            return result.toString();
        }

        private String convert(HeavyAtom[] son) {
            StringBuilder cml = new StringBuilder();
            List<HeavyAtom> printedAtoms = new ArrayList<>();
            cml.append(indent()).append("<molecule title=\"ConnectionTable\">\n");
            indentLevel++;
            for (HeavyAtom a : son) {
                cml.append(indent()).append("<atom id=\"a").append(a.number).append("\">\n");
                indentLevel++;
                cml.append(indent()).append("<string builtin=\"elementType\">").append(a.symbol).append("</string>\n");
                indentLevel--;
                cml.append(indent()).append("</atom>\n");
            }
            int bondId = 1;
            for (HeavyAtom a : son) {
                printedAtoms.add(a);
                for (int j = 0; j < a.degree; j++) {
                    if (!SpecMLWriter.containsIdentity(printedAtoms, a.nodeTable[j])) {
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
            cml.append(indent()).append("</molecule>\n");
            return cml.toString();
        }

        private String convert(NMRSpectrum s) {
            StringBuilder cml = new StringBuilder();
            StringBuilder nuclei = new StringBuilder();
            StringBuilder properties = new StringBuilder();
            cml.append(indent()).append("<molecule title=\"NMR Experiment\" id=\"").append(s.name).append("\" convention=\"Jena2000NMR\">\n");
            indentLevel++;
            cml.append(indent()).append("<string title=\"ExperimentType\">").append(s.specType).append("</string>\n");
            cml.append(indent()).append("<float title=\"Frequency\">").append(s.getSpectrometerFrequency()).append("</string>\n");
            cml.append(indent()).append("<string title=\"Solvent\">").append(s.getSolvent()).append("</string>\n");
            cml.append(indent()).append("<string title=\"Standard\">").append(s.getStandard()).append("</string>\n");
            if (s.size() > 0) {
                String[] nuc = s.getSignal(0).nucleus;
                for (String n : nuc) {
                    nuclei.append(n).append(' ');
                    properties.append("Shift ");
                }
                cml.append(indent()).append("<stringArray title=\"Nucleus\">").append(nuclei).append("</string>\n");
                cml.append(indent()).append("<stringArray title=\"Properties\">").append(properties).append("</string>\n");
                cml.append(indent()).append("<list title=\"Signals\">\n");
                indentLevel++;
                for (int i = 0; i < s.size(); i++) {
                    NMRSignal g = s.getSignal(i);
                    cml.append(indent()).append("<list title=\"Signal\"  id=\"").append(s.name).append(".p").append(i).append("\">\n");
                    indentLevel++;
                    cml.append(indent()).append("<floatArray title=\"Value\">");
                    for (int j = 0; j < g.shift.length; j++) cml.append(g.shift[j]).append(' ');
                    cml.append(indent()).append("</floatArray>\n");
                    cml.append(indent()).append("<float title=\"Intensity\">").append(g.intensity).append("</float>\n");
                    cml.append(indent()).append("<float title=\"Phase\">").append(g.phase).append("</float>\n");
                    indentLevel--;
                    cml.append(indent()).append("</list>\n");
                }
                indentLevel--;
                cml.append(indent()).append("</list>\n");
                indentLevel--;
            }
            cml.append(indent()).append("</molecule>\n");
            return cml.toString();
        }
    }
}
