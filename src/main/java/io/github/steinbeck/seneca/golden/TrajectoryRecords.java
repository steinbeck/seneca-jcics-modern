package io.github.steinbeck.seneca.golden;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.chem.AtomSets;
import io.github.steinbeck.seneca.config.GuiRecipe;
import io.github.steinbeck.seneca.config.Profile;
import io.github.steinbeck.seneca.judges.JudgeSet;
import io.github.steinbeck.seneca.structgen.RunInput;
import io.github.steinbeck.seneca.structgen.RunOptions;
import io.github.steinbeck.seneca.structgen.RunResult;
import io.github.steinbeck.seneca.structgen.StepContext;
import io.github.steinbeck.seneca.structgen.StepObserver;
import io.github.steinbeck.seneca.structgen.StochasticGenerator;

/**
 * Emits golden/trajectories/&lt;key&gt;.&lt;profile&gt;.seed&lt;s&gt;.jsonl (golden/FORMAT.md (c)):
 * {@code meta}, {@code start}, 500 x {@code init} per schedule, {@code schedule} (emitted at the first
 * main-loop evaluation after initAnnealing, so a Q1 trajectory has none), {@code step} per connected
 * candidate (with {@code ct} every 500 steps), {@code end} (+ {@code crash}).
 *
 * <p>The {@link StepObserver} below mirrors {@code GoldenMaster.TraceProbe} exactly (including its
 * counters: {@code sched} advances only when an {@code init} evaluation follows a main-loop
 * evaluation): at the evaluation of candidate {@code maxSteps + 1} it calls {@code requestStop()} and
 * does not record it ({@code end.iteration} is then {@code maxSteps + 1}); {@code end.reason} is
 * "step_limit", "finished", "no_main_step(Q1)" or "exception_in_run"; {@code checks} counts the steps
 * whose candidate score was re-checked with an independent scorer built from {@code input.copy()} taken
 * <b>before</b> the run (must equal the generator's score; a mismatch throws
 * {@link IllegalStateException} after the trajectory, as the harness does).
 *
 * <p>The probe recorded a step when the next evaluation started; this observer writes it in
 * {@code afterStep}. Nothing that a step record contains changes between the two points, and every
 * pending step was written by the harness before any later record, so the output is the same.
 */
public final class TrajectoryRecords {

    private TrajectoryRecords() {
    }

    /**
     * All lines of the trajectory file (without "\n") for a prepared input (the golden tests build it
     * from the echo records, independent of the GUI recipe). The input is consumed (the generator owns
     * its judges).
     */
    public static List<String> trajectory(String key, Profile profile, RunInput input, long seed, int maxSteps,
                                          LegacyCompat compat) {
        List<String> out = new ArrayList<>();
        JudgeSet meta = new JudgeSet(new ArrayList<>(input.judges()));
        out.add(Json.obj().put("t", "meta").put("key", key).put("profile", profile.key).put("seed", seed).put("maxSteps", maxSteps)
                .put("enabled", Json.strings(meta.enabledNames())).put("maxScore", (long) meta.maxScore()).toString());
        RunInput scorerInput = input.copy();
        Probe probe = new Probe(out, RunSupport.scorer(scorerInput.copy(), compat), maxSteps);
        StochasticGenerator sg = new StochasticGenerator(input, new RunOptions(seed, compat, 0, probe, null));
        RunResult r = sg.run();
        String reason;
        if (r.exception() != null) reason = "exception_in_run";
        else if (probe.done) reason = "step_limit";
        else if (probe.steps == 0) reason = "no_main_step(Q1)";
        else reason = "finished";
        Json end = Json.obj().put("t", "end").put("reason", reason).put("stepsRecorded", probe.recorded)
                .put("iteration", r.iterations()).put("rngDraws", r.rngDraws()).put("checks", probe.checks);
        if (r.exception() != null) end.put("crash", RunSupport.crashJson(r, RunSupport.scorer(scorerInput, compat)));
        out.add(end.toString());
        if (probe.error != null) throw new IllegalStateException(key + "/" + profile.key + "/seed " + seed + ": " + probe.error);
        return out;
    }

    /** Same, preparing the input with {@code GuiRecipe.prepare} + {@code RunInput.fromDataset} (CLI). */
    public static List<String> trajectory(Path smlFile, String key, Profile profile, long seed, int maxSteps,
                                          LegacyCompat compat) throws IOException {
        RunInput in = RunInput.fromDataset(GuiRecipe.prepare(smlFile, key, profile).sd, compat);
        return trajectory(key, profile, in, seed, maxSteps, compat);
    }

    /** {@code GoldenMaster.TraceProbe} as a step observer. */
    private static final class Probe implements StepObserver {
        final List<String> out;
        final ScoreRecords.Scorer scorer;
        final int maxSteps;
        int sched = -1, initCount = 0, steps = 0, recorded = 0;
        long checks = 0;
        boolean inInit = false, startDone = false, done = false;
        String error = null;
        int[][] start;
        // schedule values in effect (legacy fields read by the probe)
        double startKT;
        long plateauSteps, maxUphillSteps, convergenceStopCount;
        double coolingRate;
        // the step being recorded
        int[][] pendingBm = null;
        long pendingN, pendingCand, pendingDraws;
        double pendingKT;

        Probe(List<String> out, ScoreRecords.Scorer scorer, int maxSteps) {
            this.out = out;
            this.scorer = scorer;
            this.maxSteps = maxSteps;
        }

        @Override
        public void onStartStructure(int[][] bondMatrix) {
            start = AtomSets.cloneInt2D(bondMatrix);
        }

        /** The part of {@code TraceProbe.evaluate} common to every evaluation; false if done. */
        private boolean enter() {
            if (done) return false;
            if (!startDone) {
                startDone = true;
                out.add(Json.obj().put("t", "start").put("hash", Hashes.hash(start)).put("ct", Hashes.ct(start)).toString());
            }
            return true;
        }

        @Override
        public void onInitEvaluation(int schedule, int sample, long score, int[][] bm, long draws) {
            if (!enter()) return;
            if (!inInit) {
                inInit = true;
                sched++;
                initCount = 0;
            }
            initCount++;
            long total = scorer.score(bm).total;
            if (total != score && error == null)
                error = "independent init score " + total + " != generator score " + score + " at sample " + sample;
            out.add(Json.obj().put("t", "init").put("sched", sched).put("i", initCount)
                    .put("cand", total).put("draws", draws).put("hash", Hashes.hash(bm)).toString());
        }

        @Override
        public void onScheduleInitialized(int schedule, double startKT, long plateauSteps, long maxUphillSteps,
                                          long convergenceStopCount, double coolingRate) {
            this.startKT = startKT;
            this.plateauSteps = plateauSteps;
            this.maxUphillSteps = maxUphillSteps;
            this.convergenceStopCount = convergenceStopCount;
            this.coolingRate = coolingRate;
        }

        @Override
        public void beforeAcceptance(StepContext ctx) {
            if (!enter()) return;
            if (inInit) {
                inInit = false;
                out.add(Json.obj().put("t", "schedule").put("sched", sched).put("start_kT", startKT)
                        .put("plateauSteps", plateauSteps).put("maxUphillSteps", maxUphillSteps)
                        .put("convergenceStopCount", convergenceStopCount).put("coolingRate", coolingRate).toString());
            }
            if (steps >= maxSteps) {
                ctx.requestStop();
                done = true;
                return;
            }
            steps++;
            pendingBm = ctx.candidate();
            pendingN = ctx.iteration() + 1;
            pendingKT = ctx.currentKT();
            pendingCand = scorer.score(pendingBm).total;
            pendingDraws = ctx.draws();
            if (pendingN != steps && error == null) error = "iteration " + pendingN + " != step " + steps;
        }

        @Override
        public void afterStep(StepContext ctx) {
            if (pendingBm == null) return;
            if (ctx.candidateScore() != pendingCand && error == null)
                error = "independent score " + pendingCand + " != generator score " + ctx.candidateScore() + " at step " + pendingN;
            checks++;
            boolean acc = ctx.current() == pendingBm;
            Json o = Json.obj().put("t", "step").put("n", pendingN).put("kT", pendingKT).put("cand", pendingCand)
                    .put("acc", acc).put("cur", ctx.lastScore()).put("best", ctx.bestScore())
                    .put("hash", Hashes.hash(ctx.current())).put("draws", pendingDraws)
                    .put("nres", ctx.resultCount());
            if (pendingN % 500 == 0) o.put("ct", Hashes.ct(ctx.current()));
            out.add(o.toString());
            recorded++;
            pendingBm = null;
        }
    }
}
