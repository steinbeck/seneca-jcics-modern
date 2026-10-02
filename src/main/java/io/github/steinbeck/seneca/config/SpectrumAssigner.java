package io.github.steinbeck.seneca.config;

import io.github.steinbeck.seneca.model.SenecaDataset;

/**
 * Base of the automatic assigners. Port of {@code legacy/original/src/seneca/client/SpectrumAssigner.java}
 * (no Swing in the legacy class either; moved into the core, policy point 5).
 */
public abstract class SpectrumAssigner {

    protected SenecaDataset sd;

    protected SpectrumAssigner(SenecaDataset sd) {
        this.sd = sd;
    }

    public void setSenecaDataset(SenecaDataset sd) {
        this.sd = sd;
    }

    /** @return false if the assignment failed (2D assigners: the judge is then disabled) */
    public abstract boolean assign();

    /**
     * Deep copy of a 2D judge's assignment matrix (null stays null), on which the 2D assigners build a
     * new assignment in DEFAULT mode so that a failure leaves the judge unchanged (DV-46).
     */
    protected static boolean[][][] copyAssignment(boolean[][][] a) {
        if (a == null) return null;
        boolean[][][] c = new boolean[a.length][][];
        for (int i = 0; i < a.length; i++) {
            if (a[i] == null) continue;
            c[i] = new boolean[a[i].length][];
            for (int j = 0; j < a[i].length; j++) c[i][j] = a[i][j] == null ? null : a[i][j].clone();
        }
        return c;
    }
}
