package io.github.steinbeck.seneca.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.model.AnnealingSchedule;
import io.github.steinbeck.seneca.model.NMRSignal;
import io.github.steinbeck.seneca.model.NMRSpectrum;
import io.github.steinbeck.seneca.model.SenecaDataset;
import io.github.steinbeck.seneca.model.SpectrumSlot;

/**
 * Reads a SENECA dataset ({@code .sml}). Port of {@code legacy/original/src/seneca/core/SpecMLReader.java};
 * behaviour in docs/legacy/feature-inventory.md 7.1 and legacy/runnable/HOW-TO-DRIVE.md section 3.
 *
 * <ul>
 *   <li>JDK DOM (JAXP), non-namespace-aware, element names compared lower-case; root found by
 *       recursive search for {@code senecadataset}; {@code title} -&gt; name.</li>
 *   <li>{@code <formula>} -&gt; {@code setMolecularFormula} (atoms from the formula).</li>
 *   <li>Only {@code <cml:molecule>} is read (atoms with id, elementType, hydrogenCount (default -1),
 *       assignedCarbonShift; bonds ignored); a plain {@code <molecule>} is ignored (D8; golden
 *       {@code shiftsRead} depends on this).</li>
 *   <li>{@code <spectrum>}: replaces the slot identified by {@code <type>}; axes from
 *       {@code axisinfo role=f1..f5}; an empty spectrum gives dim 0 / nucleus [] (golden echo).</li>
 *   <li>{@code <annealingschedule>}: the four fields, defaults for missing ones, unknown children ignored.</li>
 *   <li>{@code <judge>}, {@code <serverinfo>}: ignored (D9; the server list is RMI-only and dropped).</li>
 *   <li>After parsing: {@code sd.init()}.</li>
 *   <li>The legacy static state (quirk Q7) is not reproduced: every call is independent (DV-26).</li>
 * </ul>
 * Children are handled in document order, as legacy: a {@code <formula>} after a
 * {@code <cml:molecule>} replaces the molecule's atoms again.
 *
 * <p>Port additions (DEVIATIONS.md DV-43, DV-44): the parser does not load external DTDs or entities;
 * a file the legacy reader cannot read (parse error, no {@code senecadataset} element, missing
 * mandatory attribute, malformed number, ...) raises an {@link IOException} whose cause is the
 * exception the legacy reader threw or printed. Elements {@code <description>} and
 * {@code <molecularmass>}, which only the port's DEFAULT writer emits (DV-08), are read back; no
 * legacy file contains them.
 */
public final class SpecMLReader {

    private SpecMLReader() {
    }

    public static SenecaDataset read(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return read(in, file.toString());
        }
    }

    /** Reads from a stream; {@code systemId} only names the source in error messages. */
    public static SenecaDataset read(InputStream in, String systemId) throws IOException {
        Document document;
        try {
            DocumentBuilder builder = factory().newDocumentBuilder();
            builder.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException e) {
                }

                @Override
                public void error(SAXParseException e) {
                }

                @Override
                public void fatalError(SAXParseException e) throws SAXException {
                    throw e;
                }
            });
            document = builder.parse(in, systemId);
        } catch (SAXException | ParserConfigurationException e) {
            throw new IOException("not a readable SENECA dataset (XML error): " + systemId + ": " + e.getMessage(), e);
        }
        try {
            return new Reader().readDocument(document);
        } catch (RuntimeException e) {
            // the legacy reader threw (NPE on a missing element/attribute, NumberFormatException, ...)
            throw new IOException("not a readable SENECA dataset: " + systemId + " (" + e + ")", e);
        }
    }

    private static DocumentBuilderFactory factory() throws ParserConfigurationException {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(false);
        f.setValidating(false);
        f.setXIncludeAware(false);
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return f;
    }

    /** One read: the legacy instance state ({@code sd}). */
    private static final class Reader {
        private SenecaDataset sd;

        SenecaDataset readDocument(Document document) {
            Node node = getDocStart(document, "senecadataset");
            if (node == null) throw new IllegalStateException("no <senecadataset> element");
            sd = new SenecaDataset(node.getAttributes().getNamedItem("title").getNodeValue());
            handleSDParts(node);
            sd.init();
            return sd;
        }

        private void handleSDParts(Node node) {
            for (Node n : children(node)) {
                if (n.getNodeType() != Node.ELEMENT_NODE) continue;
                String nodeName = n.getNodeName().toLowerCase();
                switch (nodeName) {
                    case "formula" -> readFormula(n);
                    case "cml:molecule" -> readMolecule(n);
                    case "spectrum" -> readSpectrum(n);
                    case "annealingschedule" -> readAnnealingSchedule(n);
                    case "description" -> readDescription(n);
                    case "molecularmass" -> readMolecularMass(n);
                    default -> {
                        // "judge" (readJudge commented out in legacy), "serverinfo" (RMI, dropped),
                        // plain "molecule" (D8) and anything else: ignored
                    }
                }
            }
        }

        private void readFormula(Node node) {
            for (Node n : children(node)) {
                if (n.getNodeType() == Node.TEXT_NODE) sd.setMolecularFormula(n.getNodeValue());
            }
        }

        private void readMolecule(Node node) {
            List<HeavyAtom> atoms = new ArrayList<>();
            for (Node n : children(node)) {
                if (n.getNodeType() == Node.ELEMENT_NODE && n.getNodeName().toLowerCase().equals("atom")) {
                    atoms.add(readAtom(n));
                }
            }
            sd.setSetOfNodes(atoms.toArray(new HeavyAtom[0]));
        }

        private HeavyAtom readAtom(Node node) {
            HeavyAtom atom = new HeavyAtom();
            atom.hCount = -1;
            atom.name = node.getAttributes().getNamedItem("id").getNodeValue();
            for (Node n : children(node)) {
                if (n.getNodeType() != Node.ELEMENT_NODE) continue;
                String name = n.getNodeName().toLowerCase();
                if (name.equals("string")
                        && n.getAttributes().getNamedItem("builtin").getNodeValue().toLowerCase().equals("elementtype")) {
                    atom.symbol = n.getChildNodes().item(0).getNodeValue();
                }
                if (name.equals("integer")
                        && n.getAttributes().getNamedItem("builtin").getNodeValue().toLowerCase().equals("hydrogencount")) {
                    atom.hCount = Integer.parseInt(n.getChildNodes().item(0).getNodeValue());
                }
                if (name.equals("float")
                        && n.getAttributes().getNamedItem("title").getNodeValue().toLowerCase().equals("assignedcarbonshift")) {
                    // legacy: new Float(String)
                    atom.shift13C = Float.valueOf(n.getChildNodes().item(0).getNodeValue());
                }
            }
            return atom;
        }

        private void readSpectrum(Node node) {
            NMRSpectrum nmrspect = readSpectrumInfo(node);
            nmrspect.name = node.getAttributes().getNamedItem("id").getNodeValue();
            for (Node n : children(node)) {
                if (n.getNodeType() == Node.ELEMENT_NODE && n.getNodeName().toLowerCase().equals("signal")) {
                    nmrspect.addSignal(readNMRSignal(n, nmrspect.nucleus));
                }
            }
            // legacy: specType.equals(...) for each slot; a missing <type> throws NPE, an unknown one is dropped
            String type = nmrspect.specType;
            if (type == null) throw new NullPointerException("spectrum without <type>");
            SpectrumSlot slot = SpectrumSlot.bySpecType(type);
            if (slot != null) sd.setSpectrum(slot, nmrspect);
        }

        /**
         * Legacy loop, including its reassignment of the loop variable: at the first {@code <spectruminfo>}
         * child (index f) legacy sets {@code node = newNode}, so the outer loop continues over that
         * element's children from index f + 1. A second {@code <spectruminfo>} of the spectrum is
         * therefore never read (only one nested inside the first would be).
         */
        private NMRSpectrum readSpectrumInfo(Node node) {
            String type = null, solvent = null;
            float frequency = 0;
            String[] nuclei = new String[4];
            int axisinfoCounter = 0;
            for (int f = 0; f < node.getChildNodes().getLength(); f++) {
                Node n = node.getChildNodes().item(f);
                if (n.getNodeType() != Node.ELEMENT_NODE || !n.getNodeName().toLowerCase().equals("spectruminfo")) continue;
                node = n;
                for (int g = 0; g < node.getChildNodes().getLength(); g++) {
                    Node c = node.getChildNodes().item(g);
                    if (c.getNodeType() != Node.ELEMENT_NODE) continue;
                    String name = c.getNodeName().toLowerCase();
                    Node text = c.getChildNodes().item(0);
                    switch (name) {
                        case "dimension" -> {
                            // parsed and discarded by legacy (dim comes from the number of axes)
                            if (text != null) Integer.parseInt(text.getNodeValue());
                        }
                        case "type" -> {
                            if (text != null) type = text.getNodeValue();
                        }
                        case "frequency" -> {
                            if (text != null) frequency = Float.parseFloat(text.getNodeValue());
                        }
                        case "solvent" -> {
                            if (text != null) solvent = text.getNodeValue();
                        }
                        case "standard" -> {
                            // read but never stored by legacy
                        }
                        case "axisinfo" -> {
                            readAxisInfo(c, nuclei);
                            axisinfoCounter++;
                        }
                        default -> {
                        }
                    }
                }
            }
            String[] finalNuclei = new String[axisinfoCounter];
            System.arraycopy(nuclei, 0, finalNuclei, 0, finalNuclei.length);
            NMRSpectrum nmrspect = new NMRSpectrum(finalNuclei, null);
            nmrspect.setSolvent(solvent);
            nmrspect.specType = type;
            nmrspect.setSpectrometerFrequency(frequency);
            return nmrspect;
        }

        private void readAxisInfo(Node node, String[] nuclei) {
            String nucleus = null;
            int nucleiIndex = roleIndex(node.getAttributes().getNamedItem("role").getNodeValue());
            for (Node n : children(node)) {
                if (n.getNodeType() != Node.ELEMENT_NODE) continue;
                String name = n.getNodeName().toLowerCase();
                // property and unit are read (NPE if empty) and discarded, as legacy
                if (name.equals("nucleus")) nucleus = n.getChildNodes().item(0).getNodeValue();
                if (name.equals("property")) n.getChildNodes().item(0).getNodeValue();
                if (name.equals("unit")) n.getChildNodes().item(0).getNodeValue();
            }
            nuclei[nucleiIndex] = nucleus;
        }

        private NMRSignal readNMRSignal(Node node, String[] nucleus) {
            // legacy: new NMRSignal(nucleus) shares the spectrum's nucleus array
            NMRSignal sig = new NMRSignal(nucleus);
            for (Node n : children(node)) {
                if (n.getNodeType() != Node.ELEMENT_NODE) continue;
                String name = n.getNodeName().toLowerCase();
                Node text = n.getChildNodes().item(0);
                switch (name) {
                    case "location" -> {
                        int idx = roleIndex(n.getAttributes().getNamedItem("role").getNodeValue());
                        if (text != null) sig.setShift(Float.parseFloat(text.getNodeValue()), idx);
                    }
                    case "intensity" -> {
                        if (text != null) sig.intensity = Float.parseFloat(text.getNodeValue());
                    }
                    case "normalizefactor" -> {
                        if (text != null) sig.normalizeFactor = Float.parseFloat(text.getNodeValue());
                    }
                    case "phase" -> {
                        if (text != null) sig.phase = Integer.parseInt(text.getNodeValue());
                    }
                    default -> {
                    }
                }
            }
            return sig;
        }

        private void readAnnealingSchedule(Node node) {
            AnnealingSchedule s = new AnnealingSchedule();
            for (Node n : children(node)) {
                if (n.getNodeType() != Node.ELEMENT_NODE) continue;
                String name = n.getNodeName().toLowerCase();
                switch (name) {
                    case "initialacceptanceprobability" -> s.initialAcceptanceProbability = Double.parseDouble(text(n));
                    case "maxuphillsteps" -> s.maxUphillSteps = Long.parseLong(text(n));
                    case "plateausteps" -> s.plateauSteps = Long.parseLong(text(n));
                    case "asymptoticcoolingfactor" -> s.asymptoticCoolingFactor = Double.parseDouble(text(n));
                    default -> {
                        // noOfSteps, coolingCharacteristic, linearCoolingStepsize, useStructure, converge: ignored
                    }
                }
            }
            sd.annealingSchedules.add(s);
        }

        /** Port addition (DV-08): written by the DEFAULT writer only. */
        private void readDescription(Node node) {
            sd.setDescription(node.getTextContent());
        }

        /**
         * Port addition (DV-08): written by the DEFAULT writer only. The 2001 reader ignores the element,
         * so an empty or unparsable value (e.g. "136,2" from another tool) is skipped and the mass stays
         * as it is: such a file opens as it does in legacy.
         */
        private void readMolecularMass(Node node) {
            String value = node.getTextContent();
            if (value == null) return;
            try {
                sd.setMolecularMass(Float.parseFloat(value.trim()));
            } catch (NumberFormatException e) {
                // ignored, as the 2001 reader ignores the whole element
            }
        }

        /** Legacy {@code getChildNodes().item(0).getNodeValue()} (NPE for an empty element). */
        private static String text(Node n) {
            return n.getChildNodes().item(0).getNodeValue();
        }
    }

    /** Legacy role mapping: f1..f5 -&gt; 0..4, anything else -1 (an index error follows, as legacy). */
    private static int roleIndex(String role) {
        int idx = -1;
        String r = role.toLowerCase();
        if (r.equals("f1")) idx = 0;
        if (r.equals("f2")) idx = 1;
        if (r.equals("f3")) idx = 2;
        if (r.equals("f4")) idx = 3;
        if (r.equals("f5")) idx = 4;
        return idx;
    }

    /**
     * Legacy {@code getDocStart}: depth-first, the first node with at least one child whose trimmed,
     * lower-cased name equals {@code startTag} (a childless {@code <senecadataset/>} is not found).
     */
    static Node getDocStart(Node node, String startTag) {
        NodeList kids = node.getChildNodes();
        if (kids.getLength() > 0 && node.getNodeName().trim().toLowerCase().equals(startTag.trim().toLowerCase())) {
            return node;
        }
        for (int f = 0; f < kids.getLength(); f++) {
            Node found = getDocStart(kids.item(f), startTag);
            if (found != null) return found;
        }
        return null;
    }

    private static List<Node> children(Node node) {
        NodeList l = node.getChildNodes();
        List<Node> r = new ArrayList<>(l.getLength());
        for (int i = 0; i < l.getLength(); i++) r.add(l.item(i));
        return r;
    }
}
