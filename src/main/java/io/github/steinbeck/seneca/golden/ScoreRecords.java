package io.github.steinbeck.seneca.golden;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.chem.AtomSets;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.chem.PathUtils;
import io.github.steinbeck.seneca.config.GuiRecipe;
import io.github.steinbeck.seneca.config.Profile;
import io.github.steinbeck.seneca.judges.Attic;
import io.github.steinbeck.seneca.judges.Judge;
import io.github.steinbeck.seneca.judges.JudgeSet;
import io.github.steinbeck.seneca.structgen.RunInput;
import io.github.steinbeck.seneca.structgen.RunRandom;
import io.github.steinbeck.seneca.structgen.SingleRandomStructureGenerator;
import io.github.steinbeck.seneca.structgen.StochasticGenerator;

/**
 * Emits golden/scores/&lt;key&gt;.scores.jsonl (golden/FORMAT.md (b)) and the per-judge breakdown
 * object used in (b), (d) {@code bestJudges} and (e) {@code judges}:
 * {@code {"total":..,"raw":..,"judges":[{"name","score","max","resultMax","prop","desc"}...]}}
 * ({@code GoldenMaster.scoredJson}).
 *
 * <p>{@link #scores} needs structure construction (C: seeded start structure via
 * {@code SingleRandomStructureGenerator} + {@code 20*(k-1)} connected {@code StochasticGenerator.mutate}
 * moves on the same random stream, {@code GoldenMaster.scores/startStructure/randomWalk}), and the
 * scorer of {@code GoldenMaster.Scorer}: {@code init()} once on an attic holding an <b>unbonded</b> clone
 * of the atoms, then per structure {@code synchronizeBonds} + evaluate.
 */
public final class ScoreRecords {

    private ScoreRecords() {
    }

    /** {@code GoldenMaster.scoredJson}. */
    public static Json breakdown(JudgeSet.Breakdown b) {
        Json arr = Json.arr();
        for (int i = 0; i < b.results.size(); i++) {
            var r = b.results.get(i);
            var j = b.judges.get(i);
            arr.add(Json.obj().put("name", j.name).put("score", r.score).put("max", j.getMaxScore())
                    .put("resultMax", r.maxScore).put("prop", r.propertyValue).put("desc", r.scoreDescription));
        }
        return Json.obj().put("total", b.total).put("raw", b.raw).put("judges", arr);
    }

    /** {@code GoldenMaster.N_STRUCTURES}. */
    public static final int N_STRUCTURES = 50;
    /** {@code GoldenMaster.MOVES_PER_K}. */
    public static final int MOVES_PER_K = 20;

    /**
     * The harness's independent scorer ({@code GoldenMaster.Scorer}): the given judges (used as they are,
     * so pass a private copy), {@code setAttic} + {@code init()} once on the enabled ones with an attic
     * holding an <b>unbonded</b> clone of the atoms; then per structure {@code synchronizeBonds} + evaluate.
     */
    public static final class Scorer {
        public final Attic attic = new Attic();
        public final JudgeSet judges;

        /** With the compatibility mode of the run being re-scored (set before {@code init()}). */
        public Scorer(HeavyAtom[] atoms, List<Judge> judges, LegacyCompat compat) {
            this.judges = new JudgeSet(judges);
            attic.atomSet = AtomSets.cloneAtomSet(atoms);
            attic.normalized = AtomSets.cloneAtomSet(atoms);
            attic.compat = compat;
            this.judges.initJudges(attic);
        }

        public JudgeSet.Breakdown score(int[][] bm) {
            return judges.scoreStructure(attic, bm);
        }
    }

    /**
     * All lines of the scores file (header + 50 structures) for prepared inputs of the three profiles
     * ({@code GoldenMaster.scores}). The inputs are not modified (the scorers work on copies). Structure k:
     * seeded {@code RunRandom(k)}, start structure from the gui input's atoms ({@code startStructure}),
     * {@code startDraws}, then {@code 20*(k-1)} connected Faulon moves on the same stream ({@code randomWalk}).
     */
    public static List<String> scores(String key, RunInput gui, RunInput all, RunInput manual) {
        List<String> lines = new ArrayList<>();
        RunInput g = gui.copy(), a = all.copy(), m = manual.copy();
        Scorer sg = new Scorer(g.atoms(), new ArrayList<>(g.judges()), LegacyCompat.STRICT);
        Scorer sa = new Scorer(a.atoms(), new ArrayList<>(a.judges()), LegacyCompat.STRICT);
        Scorer sm = new Scorer(m.atoms(), new ArrayList<>(m.judges()), LegacyCompat.STRICT);
        lines.add(Json.obj().put("t", "header").put("key", key)
                .put("structures", N_STRUCTURES).put("movesPerK", MOVES_PER_K)
                .put("guiEnabled", Json.strings(sg.judges.enabledNames()))
                .put("guiMaxScore", sg.judges.maxScore())
                .put("allEnabled", Json.strings(sa.judges.enabledNames()))
                .put("allMaxScore", sa.judges.maxScore())
                .put("manualEnabled", Json.strings(sm.judges.enabledNames()))
                .put("manualMaxScore", sm.judges.maxScore()).toString());
        HeavyAtom[] molecule = gui.atoms();
        for (int k = 1; k <= N_STRUCTURES; k++) {
            int moves = MOVES_PER_K * (k - 1);
            RunRandom rng = RunRandom.seeded(k);
            int[][] bm = startStructure(molecule, rng);
            long startDraws = rng.draws();
            bm = randomWalk(molecule, bm, moves, rng);
            lines.add(Json.obj().put("t", "structure").put("k", k).put("seed", (long) k).put("moves", moves)
                    .put("startDraws", startDraws).put("hash", Hashes.hash(bm)).put("ct", Hashes.ct(bm))
                    .put("gui", breakdown(sg.score(bm))).put("all", breakdown(sa.score(bm)))
                    .put("manual", breakdown(sm.score(bm))).toString());
        }
        return lines;
    }

    /** {@code GoldenMaster.startStructure}: {@code setMolecule} clone, {@code generate()} with a fresh attic, matrix. */
    static int[][] startStructure(HeavyAtom[] molecule, RunRandom rng) {
        HeavyAtom[] start = SingleRandomStructureGenerator.generate(AtomSets.cloneAtomSet(molecule), new Attic(), rng);
        int[][] bm = AtomSets.createConnectionTable(start);
        AtomSets.synchronizeConnectionTable(bm);
        return bm;
    }

    /** {@code GoldenMaster.randomWalk}: {@code moves} connected mutations with the generator's own move. */
    static int[][] randomWalk(HeavyAtom[] molecule, int[][] bm, int moves, RunRandom rng) {
        HeavyAtom[] tmp = AtomSets.cloneAtomSet(molecule);
        int done = 0;
        while (done < moves) {
            int[][] b2 = AtomSets.cloneInt2D(bm);
            StochasticGenerator.mutate(b2, rng);
            AtomSets.synchronizeBonds(tmp, b2);
            if (PathUtils.isConnected(tmp)) {
                bm = b2;
                done++;
            }
        }
        return bm;
    }

    /**
     * Same, preparing the inputs with {@code GuiRecipe.prepare} + {@code RunInput.fromDataset} (STRICT) for
     * the profiles gui, all and manual (CLI {@code seneca scores}).
     */
    public static List<String> scores(Path smlFile, String key) throws IOException {
        RunInput g = RunInput.fromDataset(GuiRecipe.prepare(smlFile, key, Profile.GUI).sd, LegacyCompat.STRICT);
        RunInput a = RunInput.fromDataset(GuiRecipe.prepare(smlFile, key, Profile.ALL).sd, LegacyCompat.STRICT);
        RunInput m = RunInput.fromDataset(GuiRecipe.prepare(smlFile, key, Profile.MANUAL).sd, LegacyCompat.STRICT);
        return scores(key, g, a, m);
    }
}
