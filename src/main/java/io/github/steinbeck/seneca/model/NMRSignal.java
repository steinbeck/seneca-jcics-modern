package io.github.steinbeck.seneca.model;

import java.util.List;

/**
 * One NMR signal. Port of {@code legacy/original/src/seneca/core/NMRSignal.java}.
 * Shifts, intensity and normalize factor are Java {@code float} (golden: binary32).
 */
public final class NMRSignal {
    public static final int PHASE_NONE = 0, PHASE_POSITIVE = 1, PHASE_NEGATIVE = 2;
    public static final List<String> PHASENAMES = List.of("NONE", "POSITIVE", "NEGATIVE");

    public int dim;
    /** One shift per axis, in {@link #nucleus} order. */
    public float[] shift;
    public String[] nucleus;
    public float intensity;
    public float normalizeFactor = 1;
    public int phase;

    /** Legacy {@code NMRSignal(String[])}: all shifts 0, intensity 0, phase NONE. */
    public NMRSignal(String[] nucleus) {
        this.dim = nucleus.length;
        this.shift = new float[dim];
        this.nucleus = nucleus;
        this.intensity = 0;
        this.normalizeFactor = 1;
        this.phase = PHASE_NONE;
    }

    public NMRSignal(String[] nucleus, float[] shift, float intensity, int phase) {
        this.dim = nucleus.length;
        this.shift = shift;
        this.nucleus = nucleus;
        this.intensity = intensity;
        this.normalizeFactor = 1;
        this.phase = phase;
    }

    /** Legacy {@code getShift(String)}: the shift on the first axis of that nucleus, else {@code Float.MAX_VALUE}. */
    public float getShift(String nuc) {
        for (int f = 0; f < nucleus.length; f++) if (nucleus[f].equals(nuc)) return shift[f];
        return Float.MAX_VALUE;
    }

    public float getShift(int axis) {
        return shift[axis];
    }

    /** Legacy {@code setShift(float, String)}: first axis of that nucleus. */
    public void setShift(float value, String nuc) {
        for (int f = 0; f < nucleus.length; f++) {
            if (nucleus[f].equals(nuc)) {
                shift[f] = value;
                break;
            }
        }
    }

    public void setShift(float value, int axis) {
        shift[axis] = value;
    }

    public NMRSignal copy() {
        NMRSignal s = new NMRSignal(nucleus.clone(), shift.clone(), intensity, phase);
        s.dim = dim;
        s.normalizeFactor = normalizeFactor;
        return s;
    }
}
