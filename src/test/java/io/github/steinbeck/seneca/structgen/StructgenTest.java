package io.github.steinbeck.seneca.structgen;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.chem.AtomSets;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.chem.StandardElements;
import io.github.steinbeck.seneca.golden.GoldenFixtures;
import io.github.steinbeck.seneca.judges.ScoreSummary;
import io.github.steinbeck.seneca.model.AnnealingSchedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests of the generator's switch points and of {@link StructureGeneratorResult} (the golden
 * tests cover the search itself). Inputs come from the golden echo ({@link GoldenFixtures}).
 */
@Tag("C")
class StructgenTest {

    static RunInput pinene(String profile) {
        return GoldenFixtures.runInput("pinene", profile);
    }

    static RunInput withSchedules(RunInput in, List<AnnealingSchedule> s) {
        return new RunInput(in.atoms(), new ArrayList<>(in.judges()), s, in.datasetName());
    }

    /** pinene paper seed 1 ends at the max after 21,101 iterations with 22 results (golden run). */
    static RunResult pinenePaperSeed1(LegacyCompat compat) {
        return new StochasticGenerator(pinene("paper"), RunOptions.golden(1).withCompat(compat)).run();
    }

    @Test
    void emptyScheduleListStrictEndsLikeLegacyDefaultRejects() {
        RunInput in = withSchedules(pinene("paper"), List.of());
        RunResult r = new StochasticGenerator(in, RunOptions.golden(1)).run();
        assertInstanceOf(ArrayIndexOutOfBoundsException.class, r.exception());
        assertEquals("0 >= 0", r.exception().getMessage());
        assertEquals("exception_in_run", r.goldenStatus());
        assertEquals(0, r.iterations());
        assertNotNull(r.crashCandidate(), "the start structure is the candidate in the attic");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new StochasticGenerator(withSchedules(pinene("paper"), List.of()), RunOptions.application()));
        assertTrue(e.getMessage().contains("schedule"), e.getMessage());
    }

    @Test
    void twoDJudgeWithoutAssignment() throws Exception {
        // pinene has no H,H-COSY signals: the recipe unticks HHCOSYJudge; ticking it again is DV-06
        java.util.function.Supplier<io.github.steinbeck.seneca.model.SenecaDataset> sd = () -> {
            try {
                var d = io.github.steinbeck.seneca.config.GuiRecipe.prepare(
                        io.github.steinbeck.seneca.golden.GoldenFiles.sml("pinene"), "pinene", io.github.steinbeck.seneca.config.Profile.GUI).sd;
                var hh = d.judges.stream().filter(j -> j instanceof io.github.steinbeck.seneca.judges.HHCOSYJudge).findFirst().orElseThrow();
                assertNull(((io.github.steinbeck.seneca.judges.TwoDSpectrumJudge) hh).assignment);
                hh.setEnabled(true);
                return d;
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        };
        assertThrows(NullPointerException.class, () -> RunInput.fromDataset(sd.get(), LegacyCompat.STRICT));
        RunInput in = RunInput.fromDataset(sd.get(), LegacyCompat.DEFAULT);
        RunResult r = new StochasticGenerator(in, RunOptions.application().withIterationCap(1000)).run();
        assertNull(r.exception());
        assertEquals(2400, r.maxScore(), "the 2D judge without signals adds nothing to the max score");
    }

    private static HeavyAtom atom(String symbol, int number, int h) {
        HeavyAtom a = new HeavyAtom(symbol, number);
        a.hCount = h;
        a.maxBondOrder = StandardElements.getMaxBondOrder(symbol);
        a.maxBondOrderSum = StandardElements.getMaxBondOrderSum(symbol);
        return a;
    }

    @Test
    void validateRejectsInputsOnWhichLegacyHangs() {
        List<AnnealingSchedule> s = List.of(new AnnealingSchedule());
        // three atoms: the Faulon move never finds four distinct atoms
        RunInput three = new RunInput(new HeavyAtom[]{atom("C", 0, 3), atom("C", 1, 2), atom("C", 2, 3)}, List.of(), s, "propane");
        assertNotNull(StochasticGenerator.validate(three));
        // odd sum of free valences (eupatorio without the OH proton): no saturated structure exists
        RunInput odd = new RunInput(new HeavyAtom[]{atom("C", 0, 3), atom("C", 1, 2), atom("C", 2, 2), atom("O", 3, 0)}, List.of(), s, "odd");
        assertTrue(StochasticGenerator.validate(odd).contains("odd"), StochasticGenerator.validate(odd));
        // an atom without free valence cannot be connected
        RunInput methane = new RunInput(new HeavyAtom[]{atom("C", 0, 3), atom("C", 1, 2), atom("C", 2, 3), atom("C", 3, 4)}, List.of(), s, "x");
        assertNotNull(StochasticGenerator.validate(methane));
        // butane is fine, and so are all golden datasets
        RunInput butane = new RunInput(new HeavyAtom[]{atom("C", 0, 3), atom("C", 1, 2), atom("C", 2, 2), atom("C", 3, 3)}, List.of(), s, "butane");
        assertNull(StochasticGenerator.validate(butane));
        for (String key : io.github.steinbeck.seneca.golden.GoldenFiles.keys())
            assertNull(StochasticGenerator.validate(GoldenFixtures.runInput(key, "gui")), key);
        assertThrows(IllegalArgumentException.class, () -> new StochasticGenerator(odd, RunOptions.application()));
    }

    /**
     * DV-65: a start structure without a connected Faulon neighbour (the free valences have exactly one
     * connected realisation). Legacy draws for ever inside initAnnealing. STRICT reports the hang as the
     * run's exception; DEFAULT ends with the start structure as the only result. Neither draws a random
     * number beyond the start generator.
     */
    @Test
    void onlyOneStructurePossible() {
        List<AnnealingSchedule> s = List.of(new AnnealingSchedule());
        HeavyAtom[] isobutane = {atom("C", 0, 1), atom("C", 1, 3), atom("C", 2, 3), atom("C", 3, 3)};
        HeavyAtom[] butane = {atom("C", 0, 3), atom("C", 1, 2), atom("C", 2, 2), atom("C", 3, 3)};
        assertNull(StochasticGenerator.validate(new RunInput(isobutane, List.of(), s, "isobutane")));
        int[][] star = {{0, 1, 1, 1}, {1, 0, 0, 0}, {1, 0, 0, 0}, {1, 0, 0, 0}};
        assertFalse(StochasticGenerator.hasConnectedNeighbour(star));
        int[][] chain = {{0, 1, 0, 0}, {1, 0, 1, 0}, {0, 1, 0, 1}, {0, 0, 1, 0}};
        assertTrue(StochasticGenerator.hasConnectedNeighbour(chain));
        assertFalse(StochasticGenerator.hasConnectedNeighbour(new int[][] {{0, 1, 0}, {1, 0, 1}, {0, 1, 0}}), "3 atoms: no move");

        RunResult strict = new StochasticGenerator(new RunInput(isobutane, List.of(), s, "isobutane"), RunOptions.golden(1)).run();
        assertInstanceOf(IllegalStateException.class, strict.exception());
        assertEquals("exception_in_run", strict.goldenStatus());
        assertEquals(0, strict.iterations());
        RunResult strict3 = new StochasticGenerator(new RunInput(new HeavyAtom[] {atom("C", 0, 3), atom("C", 1, 2), atom("C", 2, 3)},
                List.of(), s, "propane"), RunOptions.golden(1)).run();
        assertInstanceOf(IllegalStateException.class, strict3.exception(), "fewer than 4 atoms: mutate never returns");

        RunResult def = new StochasticGenerator(new RunInput(isobutane, List.of(), s, "isobutane"),
                RunOptions.golden(1).withCompat(LegacyCompat.DEFAULT)).run();
        assertNull(def.exception());
        assertEquals(0, def.iterations());
        assertEquals(1, def.result().size());
        assertNotNull(def.bestStructure());
        assertEquals(strict.rngDraws(), def.rngDraws(), "no draw beyond the start generator");
        assertTrue(java.util.Arrays.deepEquals(star, def.currentStructure()) || def.currentStructure()[0][1] + def.currentStructure()[0][2]
                + def.currentStructure()[0][3] == 3, "the star around the CH");

        // a molecule with a connected neighbour is unaffected
        RunResult b = new StochasticGenerator(new RunInput(butane, List.of(), s, "butane"),
                RunOptions.golden(1).withCompat(LegacyCompat.DEFAULT).withIterationCap(500)).run();
        assertNull(b.exception());
        assertTrue(b.iterations() > 0);
    }

    /** One RunInput started twice: each run works on its own deep copy (legacy: one serialized copy per server). */
    @Test
    void oneInputCanBeRunTwice() {
        RunInput shared = pinene("paper");
        RunResult a = new StochasticGenerator(shared, RunOptions.golden(1)).run();
        RunResult b = new StochasticGenerator(shared, RunOptions.golden(1)).run();
        assertEquals(a.bestStructure(), b.bestStructure());
        assertEquals(a.rngDraws(), b.rngDraws());
        assertEquals(a.iterations(), b.iterations());
    }

    @Test
    void iterationCapStopsLikeTheHarness() {
        RunResult r = new StochasticGenerator(pinene("gui"), RunOptions.golden(2).withIterationCap(1000)).run();
        assertTrue(r.capped());
        assertFalse(r.stoppedByUser());
        assertEquals("iteration_cap", r.goldenStatus());
        assertEquals(1001, r.iterations(), "the step in progress at the cap completes");
    }

    @Test
    void observerStopAndStatus() {
        AtomicInteger news = new AtomicInteger();
        StepObserver obs = new StepObserver() {
            @Override
            public void afterStep(StepContext ctx) {
                if (ctx.iteration() == 700) ctx.requestStop();
            }

            @Override
            public void onNewBest(String mdl, ScoreSummary summary) {
                assertTrue(mdl.startsWith("Score: " + summary.score + "\n"));
                news.incrementAndGet();
            }
        };
        StochasticGenerator g = new StochasticGenerator(pinene("paper"), RunOptions.golden(1).withObserver(obs));
        RunStatus before = g.status();
        assertEquals(RunStatus.State.IDLE, before.state());
        assertNull(before.bestStructure());
        assertEquals("C10H16", before.molecularFormula());
        RunResult r = g.run();
        assertEquals(700, r.iterations());
        assertTrue(r.stoppedByUser());
        assertEquals(r.result().size(), Math.min(news.get(), StructureGeneratorResult.DEFAULT_CAPACITY));
        RunStatus after = g.status();
        assertEquals(RunStatus.State.STOPPED, after.state());
        assertEquals(r.bestStructure(), after.bestStructure());
        assertEquals(r.maxScore(), after.bestEvaluation().maxScore, "status sets maxScore on its copy");
        assertEquals(-1, g.resultCopy().scoreSummaries.get(0).maxScore, "the stored summary is not modified");
        assertEquals(1, g.annealingLogSince(0).size(), "one annealing log entry per 500 iterations");
        assertEquals(0f, g.annealingLogSince(0).get(0).iteration(), "lagged x value as legacy");
        assertThrows(IllegalStateException.class, g::run, "a generator runs once");
    }

    @Test
    void unseededRunsWork() {
        RunResult r = new StochasticGenerator(pinene("paper"), RunOptions.application().withIterationCap(2000)).run();
        assertNull(r.exception());
        assertTrue(r.iterations() > 0 || r.result().size() == 0);
    }

    @Test
    void resultListSortMergeCopy() {
        StructureGeneratorResult r = pinenePaperSeed1(LegacyCompat.STRICT).result();
        int n = r.size();
        assertEquals(22, n);
        StructureGeneratorResult copy = r.copy();
        copy.scoreSummaries.get(0).description = "changed";
        assertFalse(r.scoreSummaries.get(0).description.equals("changed"), "deep copy");
        // stored newest first: ascending scores from the end -> sort makes it descending, stable
        StructureGeneratorResult s = r.copy();
        s.sort();
        for (int i = 0; i + 1 < s.size(); i++) assertTrue(s.scoreSummaries.get(i).score >= s.scoreSummaries.get(i + 1).score);
        // merge: capacity grows, the other's entries end up reversed in front
        StructureGeneratorResult m = new StructureGeneratorResult();
        m.merge(r.copy());
        assertEquals(30 + n, m.structures.capacity());
        assertEquals(n, m.size());
        assertEquals(r.structures.get(n - 1), m.structures.get(0));
        assertEquals(r.structures.get(0), m.structures.get(n - 1));
    }

    @Test
    void removeIsomorphismStrictAndDefault() {
        StructureGeneratorResult r = pinenePaperSeed1(LegacyCompat.STRICT).result();
        int n = r.size();
        // STRICT: the two lowest are dropped from the structures, not from the summaries (D5)
        StructureGeneratorResult strict = r.copy();
        strict.removeIsomorphism(LegacyCompat.STRICT);
        assertEquals(n - 2, strict.structures.size());
        assertEquals(n, strict.scoreSummaries.size());
        // DEFAULT: nothing truncated; notInStock already kept duplicates out, so nothing is removed
        StructureGeneratorResult def = r.copy();
        def.removeIsomorphism(LegacyCompat.DEFAULT);
        assertEquals(n, def.size());
        assertEquals(n, def.scoreSummaries.size());
        // duplicates (same structure, same score) are removed in both modes - as far as the legacy Morgan
        // heuristic sees them: it is not reflexive (two atoms with equal Morgan numbers but different element
        // or H count make a structure "differ" from itself), so such structures keep their twin
        int selfIsomorphic = 0;
        for (int i = 0; i < n; i++) {
            HeavyAtom[] a = io.github.steinbeck.seneca.chem.LegacyMdlReader.read(r.structures.get(i));
            if (io.github.steinbeck.seneca.chem.IsomorphismTester.isIsomorph(a, a)) selfIsomorphic++;
        }
        StructureGeneratorResult dup = r.copy();
        dup.merge(r.copy());
        dup.removeIsomorphism(LegacyCompat.DEFAULT);
        assertEquals(2 * n - selfIsomorphic, dup.size());
        assertTrue(selfIsomorphic > 0);
        // small lists: legacy throws (1, 2) or loops for ever (0)
        assertThrows(IllegalStateException.class, () -> new StructureGeneratorResult().removeIsomorphism(LegacyCompat.STRICT));
        for (int k = 1; k <= 2; k++) {
            StructureGeneratorResult small = new StructureGeneratorResult();
            for (int i = 0; i < k; i++) small.push(r.structures.get(i), r.scoreSummaries.get(i).copy());
            StructureGeneratorResult small2 = small.copy();
            // legacy truncates before it throws
            assertThrows(ArrayIndexOutOfBoundsException.class, () -> small.removeIsomorphism(LegacyCompat.STRICT));
            small2.removeIsomorphism(LegacyCompat.DEFAULT);
            assertEquals(k, small2.size());
        }
        new StructureGeneratorResult().removeIsomorphism(LegacyCompat.DEFAULT);
    }

    @Test
    void startStructureGeneratorAliasesTheAttic() {
        RunInput in = pinene("gui");
        io.github.steinbeck.seneca.judges.Attic attic = new io.github.steinbeck.seneca.judges.Attic();
        HeavyAtom[] atoms = in.atoms();
        HeavyAtom[] start = SingleRandomStructureGenerator.generate(atoms, attic, RunRandom.seeded(1));
        assertTrue(start != atoms, "the input atoms are cloned");
        for (HeavyAtom a : atoms) assertEquals(0, a.degree, "input not modified");
        assertTrue(attic.atomSet == start);
        assertNotNull(attic.apsp);
        for (HeavyAtom a : start) assertTrue(a.isSaturated());
        int[][] bm = AtomSets.createConnectionTable(start);
        assertEquals(bm.length, attic.apsp.length);
        // a stop request is honoured between restarts of an unsaturable input
        HeavyAtom[] odd = {atom("C", 0, 3), atom("C", 1, 2), atom("C", 2, 2), atom("O", 3, 0)};
        assertNull(SingleRandomStructureGenerator.generate(odd, attic, RunRandom.seeded(1), () -> true));
    }
}
