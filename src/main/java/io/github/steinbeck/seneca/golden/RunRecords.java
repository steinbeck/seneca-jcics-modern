package io.github.steinbeck.seneca.golden;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.chem.AtomSets;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.config.GuiRecipe;
import io.github.steinbeck.seneca.config.Profile;
import io.github.steinbeck.seneca.judges.JudgeSet;
import io.github.steinbeck.seneca.judges.ScoreSummary;
import io.github.steinbeck.seneca.structgen.RunInput;
import io.github.steinbeck.seneca.structgen.RunOptions;
import io.github.steinbeck.seneca.structgen.RunResult;
import io.github.steinbeck.seneca.structgen.StochasticGenerator;
import io.github.steinbeck.seneca.structgen.StructureGeneratorResult;

/**
 * Emits golden/runs/&lt;key&gt;.&lt;profile&gt;.seed&lt;s&gt;.json (golden/FORMAT.md (d)): one {@code run}
 * record as {@code GoldenMaster.full} writes it - status/termination ({@code RunResult.goldenStatus()}),
 * counters, kT values, best structure (parsed back from the best MDL with {@code chem.LegacyMdlReader}),
 * {@code bestJudges} (independent re-score with the scorer of an {@code input.copy()} taken before
 * the run), {@code bestMDL}, {@code crash} (candidate, current hash, exception class/message; {@code at}
 * is "&lt;class&gt;.&lt;method&gt;" of the port's top frame (no line number) and differs from the legacy
 * {@code compchem.SetOfRingsFinder.breakBond:302}), {@code reference} ({@link ReferenceCheck}),
 * {@code results}. Every result is re-scored and must equal its stored score, as in the harness.
 */
public final class RunRecords {

    private RunRecords() {
    }

    /**
     * The run record for a prepared input (golden tests build it from the echo records). The input is
     * consumed (the generator owns its judges).
     *
     * @param references   legacy/harness/references.tsv
     * @param iterationCap 5,000,000 for the golden runs
     */
    public static String run(String key, Profile profile, RunInput input, long seed, long iterationCap,
                             Path references, LegacyCompat compat) throws IOException {
        return outcome(key, profile, input, seed, iterationCap, references, compat).line();
    }

    /** The run record and the run's result (the CLI also needs status and iterations for timing.jsonl). */
    public record Outcome(String line, RunResult result) {
    }

    /** Same as {@link #run(String, Profile, RunInput, long, long, Path, LegacyCompat)}, also returning the result. */
    public static Outcome outcome(String key, Profile profile, RunInput input, long seed, long iterationCap,
                                  Path references, LegacyCompat compat) throws IOException {
        HeavyAtom[] molecule = AtomSets.cloneAtomSet(input.atoms());
        RunInput scorerInput = input.copy();
        JudgeSet enabled = new JudgeSet(new ArrayList<>(input.judges()));
        StochasticGenerator sg = new StochasticGenerator(input, new RunOptions(seed, compat, iterationCap, null, null));
        RunResult r = sg.run();
        return new Outcome(record(key, profile, seed, iterationCap, references, compat, molecule, scorerInput, enabled, r), r);
    }

    /** The record of a finished run ({@code GoldenMaster.full} after the join). */
    static String record(String key, Profile profile, long seed, long iterationCap, Path references, LegacyCompat compat,
                         HeavyAtom[] molecule, RunInput scorerInput, JudgeSet enabled, RunResult r) throws IOException {
        StructureGeneratorResult res = r.result();
        String status = r.goldenStatus();
        ReferenceCheck refCheck = ReferenceCheck.of(references, key, molecule);
        ScoreRecords.Scorer scorer = RunSupport.scorer(scorerInput, compat);
        Json results = Json.arr();
        Json resultsEqualRef = Json.arr();
        for (int i = 0; i < res.size(); i++) {
            int[][] bm = RunSupport.ctFromMdl(res.structures.get(i));
            ScoreSummary ss = res.scoreSummaries.get(i);
            JudgeSet.Breakdown re = scorer.score(bm);
            if (re.total != ss.score) throw new IllegalStateException("rescore mismatch result " + i + ": " + re.total + " != " + ss.score);
            results.add(Json.obj().put("i", i).put("score", ss.score).put("hash", Hashes.hash(bm)).put("ct", Hashes.ct(bm))
                    .put("description", ss.description));
            if (refCheck.mappable() && refCheck.equalConstitution(bm)) resultsEqualRef.add(i);
        }
        Json rec = Json.obj().put("t", "run").put("key", key).put("profile", profile.key).put("seed", seed).put("status", status)
                .put("iterationCap", iterationCap)
                .put("termination", r.goldenTermination())
                .put("enabled", Json.strings(enabled.enabledNames()))
                .put("iterations", r.iterations()).put("rngDraws", r.rngDraws())
                .put("maxScore", r.maxScore()).put("start_kT", r.startKT()).put("final_kT", r.finalKT())
                .put("plateauSteps", r.plateauSteps()).put("maxUphillSteps", r.maxUphillSteps());
        Boolean bestEq = null;
        if (res.size() > 0) {
            int[][] best = RunSupport.ctFromMdl(r.bestStructure());
            JudgeSet.Breakdown bs = scorer.score(best);
            rec.put("bestScore", r.bestScore().score).put("bestHash", Hashes.hash(best)).put("bestCt", Hashes.ct(best))
                    .put("bestJudges", ScoreRecords.breakdown(bs)).put("bestMDL", r.bestStructure());
            if (refCheck.defined()) bestEq = refCheck.mappable() && refCheck.equalConstitution(best);
        } else {
            rec.putNull("bestScore");
        }
        if (r.exception() != null) rec.put("crash", RunSupport.crashJson(r, scorer));
        Json refJ = Json.obj();
        if (!refCheck.defined()) refJ.putNull("name");
        else refJ.put("name", refCheck.ref().name()).put("status", refCheck.ref().status()).put("mappable", refCheck.mappable());
        if (bestEq == null) refJ.putNull("bestEqualsReference");
        else refJ.put("bestEqualsReference", bestEq.booleanValue());
        refJ.put("resultsEqualReference", refCheck.defined() ? resultsEqualRef : null);
        rec.put("reference", refJ).put("nResults", res.size()).put("results", results);
        return rec.toString();
    }

    /** Same, preparing the input with {@code GuiRecipe.prepare} + {@code RunInput.fromDataset} (CLI). */
    public static String run(Path smlFile, String key, Profile profile, long seed, long iterationCap,
                             Path references, LegacyCompat compat) throws IOException {
        return outcome(smlFile, key, profile, seed, iterationCap, references, compat).line();
    }

    /** Same, also returning the result. */
    public static Outcome outcome(Path smlFile, String key, Profile profile, long seed, long iterationCap,
                                  Path references, LegacyCompat compat) throws IOException {
        RunInput in = RunInput.fromDataset(GuiRecipe.prepare(smlFile, key, profile).sd, compat);
        return outcome(key, profile, in, seed, iterationCap, references, compat);
    }
}
