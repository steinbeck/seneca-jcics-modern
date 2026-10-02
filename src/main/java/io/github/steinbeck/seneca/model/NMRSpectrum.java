package io.github.steinbeck.seneca.model;

import java.util.ArrayList;
import java.util.List;

/**
 * An n-dimensional NMR spectrum: an ordered list of {@link NMRSignal}s plus metadata. Port of
 * {@code legacy/original/src/seneca/core/NMRSpectrum.java} (legacy {@code extends Vector}; here the
 * signal list is {@link #signals}). Swing change events are replaced by plain listeners.
 *
 * <p>Signal order is significant (assigners walk signals in order). The shift lists
 * ({@code shiftList}, {@code updateShiftLists}) are never read by SENECA and are omitted.
 */
public final class NMRSpectrum {

    public static final String NUC_PROTON = "1H", NUC_CARBON = "13C", NUC_NITROGEN = "15N", NUC_PHOSPHORUS = "31P";

    /** Internal name ("carbon1d", "dept90", ... for the dataset slots). */
    public String name = "";
    /** Experiment type text ({@code SpectrumSlot.specType} for the dataset slots). */
    public String specType = "";
    public int dim;
    public String[] nucleus;
    public float spectrometerFrequency;
    public String solvent = "";
    public String standard = "";
    public final List<NMRSignal> signals = new ArrayList<>();
    private final transient List<Runnable> listeners = new ArrayList<>();

    /** Legacy package-private {@code NMRSpectrum(String[] nucleus, String name)}. */
    public NMRSpectrum(String[] nucleus, String name) {
        this.dim = nucleus.length;
        this.nucleus = nucleus;
        this.name = name;
    }

    public int size() {
        return signals.size();
    }

    /**
     * Legacy {@code getSignal(int)} = {@code Vector.elementAt}: an index outside the list throws
     * {@link ArrayIndexOutOfBoundsException} (not {@code IndexOutOfBoundsException}), as legacy callers
     * (assigners, GUI recipe step texts) observe it.
     */
    public NMRSignal getSignal(int i) {
        if (i >= signals.size()) throw new ArrayIndexOutOfBoundsException(i + " >= " + signals.size());
        if (i < 0) throw new ArrayIndexOutOfBoundsException("Index " + i + " out of bounds for length " + signals.size());
        return signals.get(i);
    }

    public void addSignal(NMRSignal s) {
        signals.add(s);
        fireChange();
    }

    /** Legacy {@code newSignal()}: appends {@code new NMRSignal(nucleus)}. */
    public void newSignal() {
        signals.add(new NMRSignal(nucleus));
        fireChange();
    }

    /**
     * Legacy {@code pickClosestSignal(shift, nucleus, precision)}: the signal with the smallest
     * {@code |shift[dim] - shift|} (first on ties, float arithmetic) if that difference is
     * {@code < precision}, else null. Axis = first axis with that nucleus.
     */
    public NMRSignal pickClosestSignal(float shift, String nuc, float precision) {
        int axis = axisOf(nuc);
        int thisPosition = -1;
        float diff = Float.MAX_VALUE;
        for (int f = 0; f < signals.size(); f++) {
            // legacy: shift[dim] with dim == -1 if the nucleus is absent -> ArrayIndexOutOfBoundsException
            if (diff > Math.abs(signals.get(f).shift[axis] - shift)) {
                diff = Math.abs(signals.get(f).shift[axis] - shift);
                thisPosition = f;
            }
        }
        if (diff < precision) return getSignal(thisPosition);
        return null;
    }

    /** Legacy {@code pickSignals(shift, nucleus, precision)}: all signals with {@code precision > |diff|}, in order. */
    public List<NMRSignal> pickSignals(float shift, String nuc, float precision) {
        int axis = axisOf(nuc);
        List<NMRSignal> picked = new ArrayList<>();
        for (NMRSignal s : signals) {
            if (precision > Math.abs(s.shift[axis] - shift)) picked.add(s);
        }
        return picked;
    }

    /** First axis whose nucleus equals {@code nuc}, or -1 (legacy {@code dim = -1}). */
    private int axisOf(String nuc) {
        for (int f = 0; f < nucleus.length; f++) {
            if (nucleus[f].equals(nuc)) return f;
        }
        return -1;
    }

    public float getSpectrometerFrequency() {
        return spectrometerFrequency;
    }

    public void setSpectrometerFrequency(float frequency) {
        this.spectrometerFrequency = frequency;
    }

    public String getSolvent() {
        return solvent;
    }

    public void setSolvent(String solvent) {
        this.solvent = solvent;
    }

    public String getStandard() {
        return standard;
    }

    public void setStandard(String standard) {
        this.standard = standard;
    }

    /** "Delete Signal" in the spectrum table: removes row {@code i} (Vector semantics for bad indices). */
    public void removeSignal(int i) {
        getSignal(i);
        signals.remove(i);
        fireChange();
    }

    /**
     * Gives a spectrum without axes the axes {@code nuclei} (application, DEFAULT; gui-design.md DV-85).
     * The SpecML reader leaves a spectrum without signals at dim 0 / nucleus [] (no {@code axisinfo} is
     * written for it), so "Add Signal" and "Paste WinNMR pp" in the 2001 GUI produced signals without
     * shifts there. The spectrum tables call this, with the slot's default nuclei, before they add the
     * first signal. Changes nothing (returns false) if the spectrum has axes or signals.
     */
    public boolean ensureAxes(String[] nuclei) {
        if (dim != 0 || !signals.isEmpty() || nuclei == null || nuclei.length == 0) return false;
        this.nucleus = nuclei.clone();
        this.dim = nuclei.length;
        fireChange();
        return true;
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

    /** Deep copy (signals copied; listeners not). */
    public NMRSpectrum copy() {
        NMRSpectrum s = new NMRSpectrum(nucleus == null ? null : nucleus.clone(), name);
        s.dim = dim;
        s.specType = specType;
        s.spectrometerFrequency = spectrometerFrequency;
        s.solvent = solvent;
        s.standard = standard;
        for (NMRSignal g : signals) s.signals.add(g.copy());
        return s;
    }
}
