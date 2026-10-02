package io.github.steinbeck.seneca.model;

/**
 * One asymptotic-cooling schedule. Port of
 * {@code legacy/original/src/seneca/structgen/AnnealingSchedule.java} (moved to the model package: the
 * dataset owns its schedules). Defaults 0.8 / 50 / 500 / 0.95. Schedules run one after another
 * within a run (architecture.md 4.5).
 */
public final class AnnealingSchedule {
    /** Initial acceptance probability p0; {@code start_kT = -changes / Math.log(p0)}. */
    public double initialAcceptanceProbability = 0.8;
    public long maxUphillSteps = 50;
    public long plateauSteps = 500;
    public double asymptoticCoolingFactor = 0.95;

    public AnnealingSchedule() {
    }

    public AnnealingSchedule(double initialAcceptanceProbability, long maxUphillSteps, long plateauSteps,
                             double asymptoticCoolingFactor) {
        this.initialAcceptanceProbability = initialAcceptanceProbability;
        this.maxUphillSteps = maxUphillSteps;
        this.plateauSteps = plateauSteps;
        this.asymptoticCoolingFactor = asymptoticCoolingFactor;
    }

    public AnnealingSchedule copy() {
        return new AnnealingSchedule(initialAcceptanceProbability, maxUphillSteps, plateauSteps, asymptoticCoolingFactor);
    }

    /** Legacy list rendering, e.g. {@code "A-F:0.95-IAP:0.8-PS:500-UHS: 50"}. */
    @Override
    public String toString() {
        return "A-F:" + asymptoticCoolingFactor + "-IAP:" + initialAcceptanceProbability + "-PS:" + plateauSteps
                + "-UHS: " + maxUphillSteps;
    }
}
