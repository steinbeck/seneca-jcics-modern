package io.github.steinbeck.seneca.verify;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.chem.AtomSets;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.chem.StandardElements;
import io.github.steinbeck.seneca.config.GuiRecipe;
import io.github.steinbeck.seneca.config.Profile;
import io.github.steinbeck.seneca.golden.EchoRecords;
import io.github.steinbeck.seneca.golden.GoldenFiles;
import io.github.steinbeck.seneca.golden.GoldenFixtures;
import io.github.steinbeck.seneca.golden.Hashes;
import io.github.steinbeck.seneca.judges.Attic;
import io.github.steinbeck.seneca.judges.Judge;
import io.github.steinbeck.seneca.model.AnnealingSchedule;
import io.github.steinbeck.seneca.model.SpectrumSlot;
import io.github.steinbeck.seneca.structgen.RunInput;
import io.github.steinbeck.seneca.structgen.RunOptions;
import io.github.steinbeck.seneca.structgen.RunRandom;
import io.github.steinbeck.seneca.structgen.RunResult;
import io.github.steinbeck.seneca.structgen.SingleRandomStructureGenerator;
import io.github.steinbeck.seneca.structgen.StepContext;
import io.github.steinbeck.seneca.structgen.StepObserver;
import io.github.steinbeck.seneca.structgen.StochasticGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Policy verifier, round 3. Policy point 2: a DEFAULT fix must not change the random stream or any
 * outcome of a run in which the legacy code does not fail.
 */
class PolicyRound3VerifyTest {

    /** Main-loop steps per schedule, and a digest of every step. */
    private static final class Steps implements StepObserver {
        final List<Integer> perSchedule = new ArrayList<>();
        final List<String> trace = new ArrayList<>();

        @Override
        public void onScheduleInitialized(int schedule, double startKT, long p, long u, long cs, double cr) {
            perSchedule.add(0);
            trace.add("schedule " + schedule + " " + startKT);
        }

        @Override
        public void beforeAcceptance(StepContext ctx) {
            trace.add(ctx.iteration() + "|" + ctx.draws() + "|" + Hashes.hash(ctx.candidate()) + "|" + ctx.candidateScore());
        }

        @Override
        public void afterStep(StepContext ctx) {
            perSchedule.set(perSchedule.size() - 1, perSchedule.get(perSchedule.size() - 1) + 1);
        }
    }

    private static String outcome(RunResult r) {
        StringBuilder sb = new StringBuilder();
        sb.append(r.goldenStatus()).append(" it=").append(r.iterations()).append(" draws=").append(r.rngDraws())
                .append(" best=").append(r.bestScore().score).append(" results=").append(r.result().size());
        for (int i = 0; i < r.result().size(); i++) sb.append(' ').append(Integer.toHexString(r.result().structures.get(i).hashCode()));
        return sb.toString();
    }

    /**
     * Quirk Q1 (DV-04) on a schedule that is <b>not</b> the last one. Legacy then simply goes on with the
     * next schedule ({@code while (currentScheduleNo < schedules.size())}); the run finishes normally
     * with results. This is not a crash, hang or data loss, so DEFAULT must leave the schedule exactly as
     * legacy does; the DEFAULT fix of DV-04 applies only where the empty schedule would leave the run
     * without any result (the last schedule, nothing stored yet).
     */
    @Test
    void q1OnANonFinalScheduleIsNotALegacyFailureSoDefaultMustEqualStrict() throws Exception {
        List<AnnealingSchedule> three = List.of(new AnnealingSchedule(0.8, 5, 20, 0.5),
                new AnnealingSchedule(0.5, 10, 30, 0.7), new AnnealingSchedule(0.9, 3, 10, 0.3));
        int examined = 0;
        List<String> differing = new ArrayList<>();
        for (String key : GoldenFiles.keys()) {
            RunInput base = GoldenFixtures.runInput(key, "paper");
            for (long seed = 201; seed <= 206; seed++) {
                Steps ss = new Steps(), sd = new Steps();
                RunResult s = new StochasticGenerator(with(base, three),
                        RunOptions.golden(seed).withIterationCap(50_000).withObserver(ss)).run();
                boolean legacyCompletedNormally = s.exception() == null && s.goldenStatus().equals("finished");
                int emptyNonFinal = -1;
                for (int i = 0; i < ss.perSchedule.size() - 1; i++) if (ss.perSchedule.get(i) == 0) emptyNonFinal = i;
                if (!legacyCompletedNormally || emptyNonFinal < 0) continue;
                examined++;
                RunResult d = new StochasticGenerator(with(base, three),
                        RunOptions.golden(seed).withCompat(LegacyCompat.DEFAULT).withIterationCap(50_000).withObserver(sd)).run();
                if (!ss.trace.equals(sd.trace) || !outcome(s).equals(outcome(d)))
                    differing.add(key + "/paper/seed" + seed + " (STRICT steps per schedule " + ss.perSchedule
                            + ", DEFAULT " + sd.perSchedule + "): STRICT " + outcome(s).substring(0, Math.min(90, outcome(s).length()))
                            + " | DEFAULT " + outcome(d).substring(0, Math.min(90, outcome(d).length())));
            }
        }
        System.out.println("[verify-r3] runs that legacy finishes normally with an empty non-final schedule: " + examined
                + ", DEFAULT differs in " + differing.size());
        differing.forEach(x -> System.out.println("[verify-r3]   " + x));
        assertTrue(examined > 0, "no run with an empty non-final schedule found; widen the seed range");
        assertEquals(List.of(), differing, "DEFAULT changes runs that legacy completes normally (Q1 on a non-final schedule)");
    }

    /**
     * Policy point 2 for the configuration layer (DV-42 Autoassignment, DV-46 HMBC/H,H-COSY Autoconfigure):
     * the GUI recipe gives the same dataset, the same judge configuration and the same generator input in
     * DEFAULT as in STRICT for every bundled dataset and profile (the fix report claimed this was checked
     * by hand; nothing in the test suite asserted it).
     */
    @Test
    void guiRecipeGivesTheSameDatasetInBothModes() throws Exception {
        Method atoms = method("atomsRecord"), spectrum = method("spectrumRecord"), schedules = method("schedulesRecord"),
                judges = method("judgesRecord"), max = method("generatorMaxScore");
        int compared = 0;
        List<String> mismatches = new ArrayList<>();
        for (String key : GoldenFiles.keys()) {
            Path sml = GoldenFiles.sml(key);
            for (Profile profile : EchoRecords.ECHO_PROFILES) {
                List<String> rec = new ArrayList<>();
                for (LegacyCompat c : LegacyCompat.values()) {
                    GuiRecipe.Prepared p = GuiRecipe.prepare(sml, key, profile, c);
                    RunInput go = RunInput.fromDataset(p.sd, c);
                    StringBuilder sb = new StringBuilder();
                    sb.append(p.steps).append(p.hOverrides).append(p.autoAssigned).append(p.scheduleAdded).append('\n');
                    sb.append(atoms.invoke(null, "x", p.sd.setOfNodes)).append('\n');
                    for (SpectrumSlot slot : SpectrumSlot.values())
                        sb.append(spectrum.invoke(null, "x", slot, p.sd.spectrum(slot))).append('\n');
                    sb.append(schedules.invoke(null, "x", p.sd.annealingSchedules)).append('\n');
                    sb.append(judges.invoke(null, "x", go.judges(), true)).append('\n');
                    sb.append(GuiRecipe.enabledJudges(p.sd)).append(max.invoke(null, go.judges()));
                    rec.add(sb.toString());
                }
                compared++;
                if (!rec.get(0).equals(rec.get(1))) mismatches.add(key + "/" + profile.key);
            }
        }
        assertEquals(44, compared);
        assertEquals(List.of(), mismatches, "GUI recipe differs between STRICT and DEFAULT");
    }

    /**
     * DV-65: DEFAULT ends a run with the start structure as the only result when
     * {@code hasConnectedNeighbour} is false. A false "no neighbour" would change a run that legacy
     * completes. Cross-check against the real {@code mutate} by sampling, on random small molecules
     * (independent of the enumeration in the production code).
     */
    @Test
    void hasConnectedNeighbourAgreesWithSampledMutate() throws Exception {
        Method hcn = StochasticGenerator.class.getDeclaredMethod("hasConnectedNeighbour", int[][].class);
        hcn.setAccessible(true);
        java.util.Random pick = new java.util.Random(31);
        String[] elements = {"C", "C", "C", "N", "O", "Cl"};
        int checked = 0, negatives = 0;
        List<String> mismatches = new ArrayList<>();
        while (checked < 400) {
            int n = 4 + pick.nextInt(4);
            HeavyAtom[] a = new HeavyAtom[n];
            for (int i = 0; i < n; i++) {
                String s = elements[pick.nextInt(elements.length)];
                a[i] = new HeavyAtom(s, i);
                a[i].maxBondOrder = StandardElements.getMaxBondOrder(s);
                a[i].maxBondOrderSum = StandardElements.getMaxBondOrderSum(s);
                a[i].hCount = pick.nextInt(a[i].maxBondOrderSum);   // at least one free valence
            }
            int[] restarts = {0};
            HeavyAtom[] start = SingleRandomStructureGenerator.generate(a, new Attic(), RunRandom.seeded(checked),
                    () -> ++restarts[0] > 2000);
            if (start == null) continue;
            int[][] bm = AtomSets.createConnectionTable(start);
            boolean claimed = (Boolean) hcn.invoke(null, (Object) bm);
            if (!claimed) negatives++;
            boolean anyValid = anyValidMove(bm);
            boolean sampled = false;
            if (anyValid) {   // mutate never returns without a valid move
                RunRandom r = RunRandom.seeded(1000 + checked);
                for (int k = 0; k < 60_000 && !sampled; k++) {
                    int[][] c = AtomSets.cloneInt2D(bm);
                    StochasticGenerator.mutate(c, r);
                    sampled = connected(c);
                }
            }
            if (claimed != sampled) mismatches.add("n=" + n + " claimed=" + claimed + " sampled=" + sampled);
            checked++;
        }
        System.out.println("[verify-r3] hasConnectedNeighbour: " + checked + " structures, " + negatives + " without a connected neighbour");
        assertEquals(List.of(), mismatches);
    }

    // ------------------------------------------------------------------ helpers

    private static RunInput with(RunInput in, List<AnnealingSchedule> schedules) {
        RunInput c = in.copy();
        List<AnnealingSchedule> s = new ArrayList<>();
        for (AnnealingSchedule a : schedules) s.add(a.copy());
        return new RunInput(c.atoms(), new ArrayList<Judge>(c.judges()), s, c.datasetName());
    }

    private static Method method(String name) {
        for (Method m : EchoRecords.class.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                m.setAccessible(true);
                return m;
            }
        }
        throw new AssertionError("EchoRecords." + name + " not found");
    }

    /** A Faulon move exists iff some 4 distinct atoms and b11 != a11 keep all four new orders in [0, 3]. */
    private static boolean anyValidMove(int[][] bm) {
        int n = bm.length;
        for (int x1 = 0; x1 < n; x1++)
            for (int x2 = 0; x2 < n; x2++)
                for (int y1 = 0; y1 < n; y1++)
                    for (int y2 = 0; y2 < n; y2++) {
                        if (x1 == x2 || x1 == y1 || x1 == y2 || x2 == y1 || x2 == y2 || y1 == y2) continue;
                        int a11 = bm[x1][y1], a12 = bm[x1][y2], a22 = bm[x2][y2], a21 = bm[x2][y1];
                        for (int b = 0; b <= 3; b++) {
                            if (b == a11) continue;
                            int o2 = a11 + a12 - b, o3 = a22 - a11 + b, o4 = a11 + a21 - b;
                            if (o2 >= 0 && o2 <= 3 && o3 >= 0 && o3 <= 3 && o4 >= 0 && o4 <= 3) return true;
                        }
                    }
        return false;
    }

    private static boolean connected(int[][] bm) {
        int n = bm.length;
        boolean[] seen = new boolean[n];
        java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
        q.add(0);
        seen[0] = true;
        int count = 1;
        while (!q.isEmpty()) {
            int v = q.poll();
            for (int u = 0; u < n; u++) if (bm[v][u] > 0 && !seen[u]) {
                seen[u] = true;
                count++;
                q.add(u);
            }
        }
        return count == n;
    }
}
