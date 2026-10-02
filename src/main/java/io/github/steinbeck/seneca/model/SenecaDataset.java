package io.github.steinbeck.seneca.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.chem.MolecularFormula;
import io.github.steinbeck.seneca.chem.StandardElements;
import io.github.steinbeck.seneca.judges.FeatureJudge;
import io.github.steinbeck.seneca.judges.HHCOSYJudge;
import io.github.steinbeck.seneca.judges.HMBCJudge;
import io.github.steinbeck.seneca.judges.HOSECodeJudge;
import io.github.steinbeck.seneca.judges.HeteroBondsJudge;
import io.github.steinbeck.seneca.judges.HybridizationJudge;
import io.github.steinbeck.seneca.judges.Judge;
import io.github.steinbeck.seneca.judges.SymmetryJudge;

/**
 * One structure elucidation project. Port of {@code legacy/original/src/seneca/core/SenecaDataset.java}
 * (ISO-8859-1 source: read it with {@code iconv -f ISO-8859-1}).
 *
 * <ul>
 *   <li>{@link #setOfNodes}: heavy atoms in dataset order; their {@code hCount} and {@code shift13C}
 *       carry the Atom Properties table.</li>
 *   <li>Seven spectrum slots ({@link SpectrumSlot}); {@link #spectrum}/{@link #setSpectrum}.</li>
 *   <li>{@link #init()}: {@code initializeNodes()} then the 7 judges in the fixed order (= evaluation
 *       order): HHCOSY, HMBC, HeteroBonds, Symmetry, Hybridization, HOSECode, Feature.</li>
 *   <li>Description defaults to "none"; description and molecular mass are not written to SML.</li>
 *   <li>{@code serverList}/{@code sessions} (RMI) are dropped; SpecML {@code <serverinfo>} is parsed and ignored.</li>
 * </ul>
 * Swing change events are replaced by plain listeners ({@link #addChangeListener}).
 */
public final class SenecaDataset {

    private String name;
    private String description = "none";
    private String molecularFormula;
    private float molecularMass = 0f;
    private final Map<SpectrumSlot, NMRSpectrum> spectra = new EnumMap<>(SpectrumSlot.class);
    public final List<AnnealingSchedule> annealingSchedules = new ArrayList<>();
    public HeavyAtom[] setOfNodes;
    public final List<Judge> judges = new ArrayList<>();
    private final transient List<Runnable> listeners = new ArrayList<>();

    /** Legacy constructor: the 7 empty spectra with their default names, types and nuclei. */
    public SenecaDataset() {
        for (SpectrumSlot s : SpectrumSlot.values()) {
            NMRSpectrum sp = new NMRSpectrum(s.defaultNuclei(), s.defaultName);
            sp.specType = s.specType;
            spectra.put(s, sp);
        }
    }

    public SenecaDataset(String name) {
        this();
        this.name = name;
    }

    /**
     * "File &gt; New Dataset" of the application (feature-inventory F-20 with the D10 fix,
     * docs/port/gui-design.md): legacy only called {@code new SenecaDataset(name)}, so the new dataset had
     * no judges and no annealing schedule and "Start" failed. The port also calls {@link #init()} (the 7
     * disabled judges, as after reading a file) and adds one default {@link AnnealingSchedule}
     * (as "New -&gt;" in the schedule editor). No formula, no atoms.
     */
    public static SenecaDataset newDataset(String name) {
        SenecaDataset sd = new SenecaDataset(name);
        sd.init();
        sd.annealingSchedules.add(new AnnealingSchedule());
        return sd;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        fireChange();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        fireChange();
    }

    public String getMolecularFormula() {
        return molecularFormula;
    }

    /**
     * Legacy {@code setMolecularFormula(String)}: stores the string and <b>regenerates</b>
     * {@link #setOfNodes} from it (heavy atoms in formula order, H 0, no shifts), then initializeNodes.
     */
    public void setMolecularFormula(String mf) {
        this.molecularFormula = mf;
        this.setOfNodes = MolecularFormula.heavyAtoms(mf);
        initializeNodes();
        fireChange();
    }

    public float getMolecularMass() {
        return molecularMass;
    }

    public void setMolecularMass(float mass) {
        this.molecularMass = mass;
        fireChange();
    }

    public HeavyAtom[] getSetOfNodes() {
        return setOfNodes;
    }

    /**
     * Legacy {@code setSetOfNodes(Atom[])}: replaces the atoms, initializeNodes, and if no formula is set
     * yet derives one with the dataset's own {@code analyseSetOfNodes()} (which, unlike
     * {@code MFAnalyser}, also adds negative H counts).
     */
    public void setSetOfNodes(HeavyAtom[] atoms) {
        this.setOfNodes = atoms;
        initializeNodes();
        if (this.molecularFormula == null) this.molecularFormula = analyseSetOfNodes();
        fireChange();
    }

    /**
     * Legacy {@code setMolecularFormula(Atom[])}: takes the atoms and derives the formula with
     * {@code MFAnalyser(atoms).analyseSetOfNodes()} (positive H counts only), then initializeNodes.
     */
    public void setMolecularFormula(HeavyAtom[] atoms) {
        this.setOfNodes = atoms;
        this.molecularFormula = MolecularFormula.formulaOf(setOfNodes);
        initializeNodes();
        fireChange();
    }

    /**
     * Legacy private {@code analyseSetOfNodes()}: element symbols in order of first appearance with
     * their counts (count 1 omitted), total H inserted after the first element. Unlike
     * {@code MFAnalyser.analyseSetOfNodes()} the H total is the plain sum of all H counts, so undefined
     * (-1) counts are subtracted; H is written only if that sum is positive.
     */
    private String analyseSetOfNodes() {
        String mf = "";
        String[] symbols = new String[setOfNodes.length];
        int[] elementCount = new int[setOfNodes.length];
        int hCount = 0;
        int numberOfElements = 0;
        for (HeavyAtom atom : setOfNodes) {
            String symbol = atom.symbol;
            hCount += atom.hCount;
            boolean done = false;
            for (int g = 0; g < numberOfElements; g++) {
                if (symbols[g].equals(symbol)) {
                    elementCount[g]++;
                    done = true;
                    break;
                }
            }
            if (!done) {
                symbols[numberOfElements] = symbol;
                elementCount[numberOfElements]++;
                numberOfElements++;
            }
        }
        for (int g = 0; g < numberOfElements; g++) {
            mf += symbols[g];
            if (elementCount[g] > 1) mf += Integer.toString(elementCount[g]);
            if (g == 0 && hCount > 0) {
                mf += "H";
                if (hCount > 1) mf += Integer.toString(hCount);
            }
        }
        return mf;
    }

    public NMRSpectrum spectrum(SpectrumSlot slot) {
        return spectra.get(slot);
    }

    /** Replace a slot (the SpecML reader does this for every {@code <spectrum>} element). */
    public void setSpectrum(SpectrumSlot slot, NMRSpectrum spectrum) {
        spectra.put(slot, spectrum);
        fireChange();
    }

    /**
     * Legacy {@code init()}: {@code initializeNodes()}, then appends fresh (disabled) judges in the fixed
     * order. Like legacy, it does not clear {@link #judges} first.
     */
    public boolean init() {
        initializeNodes();
        judges.add(new HHCOSYJudge());
        judges.add(new HMBCJudge());
        judges.add(new HeteroBondsJudge());
        judges.add(new SymmetryJudge());
        judges.add(new HybridizationJudge());
        judges.add(new HOSECodeJudge());
        judges.add(new FeatureJudge());
        return true;
    }

    /**
     * Legacy {@code getJudge(String)}: the instance in {@link #judges} whose class has that simple name;
     * if absent, a <b>new unregistered</b> instance (legacy {@code Class.forName(...).newInstance()}).
     */
    public Judge getJudge(String simpleClassName) {
        for (Judge j : judges) if (j.getClass().getSimpleName().equals(simpleClassName)) return j;
        return switch (simpleClassName) {
            case "HHCOSYJudge" -> new HHCOSYJudge();
            case "HMBCJudge" -> new HMBCJudge();
            case "HeteroBondsJudge" -> new HeteroBondsJudge();
            case "SymmetryJudge" -> new SymmetryJudge();
            case "HybridizationJudge" -> new HybridizationJudge();
            case "HOSECodeJudge" -> new HOSECodeJudge();
            case "FeatureJudge" -> new FeatureJudge();
            default -> null;
        };
    }

    /** Typed variant of {@link #getJudge(String)}. */
    public <T extends Judge> T getJudge(Class<T> type) {
        return type.cast(getJudge(type.getSimpleName()));
    }

    /** Legacy private {@code initializeNodes()}: {@code number = index}, valence data from StandardElements. */
    public void initializeNodes() {
        if (setOfNodes == null) return;
        for (int f = 0; f < setOfNodes.length; f++) {
            setOfNodes[f].number = f;
            setOfNodes[f].maxBondOrder = StandardElements.getMaxBondOrder(setOfNodes[f].symbol);
            setOfNodes[f].maxBondOrderSum = StandardElements.getMaxBondOrderSum(setOfNodes[f].symbol);
        }
    }

    public void addChangeListener(Runnable l) {
        listeners.add(l);
    }

    public void removeChangeListener(Runnable l) {
        listeners.remove(l);
    }

    public void fireChange() {
        for (Runnable l : new ArrayList<>(listeners)) l.run();
    }
}
