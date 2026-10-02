package io.github.steinbeck.seneca.structgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.chem.AtomSets;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.judges.HOSECodeJudge;
import io.github.steinbeck.seneca.judges.HeteroBondsJudge;
import io.github.steinbeck.seneca.judges.HybridizationJudge;
import io.github.steinbeck.seneca.judges.Judge;
import io.github.steinbeck.seneca.judges.JudgeSet;
import io.github.steinbeck.seneca.judges.TwoDSpectrumJudge;
import io.github.steinbeck.seneca.model.AnnealingSchedule;
import io.github.steinbeck.seneca.model.SenecaDataset;

/**
 * What "Start as selected" hands to a generator: private deep copies of the atoms, <b>all</b> judges
 * (with their enabled flags and max scores) and the schedules. Port of {@code SGServerPanel.goAction}
 * ({@code legacy/original/src/seneca/client/SGServerPanel.java} ~line 700; legacy/runnable/HOW-TO-DRIVE.md
 * section 1; quirk Q6: {@code calcMaxScore()} runs on the dataset's own judges before the copy).
 */
public final class RunInput {

    private final HeavyAtom[] atoms;
    private final List<Judge> judges;
    private final List<AnnealingSchedule> schedules;
    private final String datasetName;

    public RunInput(HeavyAtom[] atoms, List<Judge> judges, List<AnnealingSchedule> schedules, String datasetName) {
        this.atoms = atoms;
        this.judges = judges;
        this.schedules = schedules;
        this.datasetName = datasetName;
    }

    /**
     * {@code goAction}: {@code calcMaxScore()} on every enabled judge of the dataset (mutates the
     * dataset's judges, as legacy), then deep copies.
     *
     * <p>Switch point (DV-06, DV-63): an enabled judge that was never configured makes legacy
     * {@code calcMaxScore()} throw a NullPointerException ("Start" fails): a 2D judge with
     * {@code assignment == null}, a HOSECodeJudge without carbon shifts (activated without
     * Autoconfigure), a Hybridization- or HeteroBondsJudge whose table was never allocated (no dataset
     * window). STRICT propagates it; DEFAULT gives such a judge max score 0 instead of calling
     * {@code calcMaxScore()}. In the run it then scores 0 exactly as legacy would score the same
     * judge configured with nothing (2D: no rules, "No signals available"; HOSE: every carbon fails
     * inside the judge's {@code try}; Hybridization/HeteroBonds: {@code init()} allocates empty tables).
     *
     * <p>A dataset without atoms (New Dataset before a formula was entered): STRICT throws
     * {@link NullPointerException} as before; DEFAULT throws {@link IllegalArgumentException} with
     * {@link StochasticGenerator#NO_ATOMS} (DV-66). Judges configured for another atom count are rejected
     * by the generator in DEFAULT ({@link StochasticGenerator#validate}).
     *
     * @throws IllegalArgumentException DEFAULT only, if the dataset has no atoms
     */
    public static RunInput fromDataset(SenecaDataset sd, LegacyCompat compat) {
        HeavyAtom[] nodes = sd.getSetOfNodes();
        // legacy goAction fails inside its try (new Molecule(null)) and starts nothing; STRICT keeps the
        // NullPointerException (from cloneAtomSet below), DEFAULT says why (DV-66)
        if (!compat.strict() && (nodes == null || nodes.length == 0))
            throw new IllegalArgumentException(StochasticGenerator.NO_ATOMS);
        for (Judge j : sd.judges) {
            if (!j.getEnabled()) continue;
            if (!compat.strict() && unconfigured(j)) {
                j.setMaxScore(0);
                continue;
            }
            j.calcMaxScore();
        }
        List<AnnealingSchedule> sched = new ArrayList<>();
        for (AnnealingSchedule s : sd.annealingSchedules) sched.add(s.copy());
        return new RunInput(AtomSets.cloneAtomSet(nodes), JudgeSet.deepCopy(sd.judges), sched, sd.getName());
    }

    /** A judge whose {@code calcMaxScore()} throws because its configuration was never created. */
    static boolean unconfigured(Judge j) {
        return switch (j) {
            case TwoDSpectrumJudge t -> t.assignment == null;
            case HOSECodeJudge h -> h.getCarbonShifts() == null;
            case HybridizationJudge h -> h.scores == null;
            case HeteroBondsJudge h -> h.isHeteroBonded == null;
            default -> false;
        };
    }

    /** Atoms in dataset order (the generator clones them again, as {@code setMolecule} does). */
    public HeavyAtom[] atoms() {
        return atoms;
    }

    /** All judges in dataset order (the generator owns them; use {@link #copy()} for a second run). */
    public List<Judge> judges() {
        return Collections.unmodifiableList(judges);
    }

    public List<AnnealingSchedule> schedules() {
        return Collections.unmodifiableList(schedules);
    }

    public String datasetName() {
        return datasetName;
    }

    /** Independent deep copy (e.g. one input for several runs, or the golden independent scorer). */
    public RunInput copy() {
        List<AnnealingSchedule> sched = new ArrayList<>();
        for (AnnealingSchedule s : schedules) sched.add(s.copy());
        return new RunInput(AtomSets.cloneAtomSet(atoms), JudgeSet.deepCopy(judges), sched, datasetName);
    }
}
