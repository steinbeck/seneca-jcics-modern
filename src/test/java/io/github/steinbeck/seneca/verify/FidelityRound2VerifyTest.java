package io.github.steinbeck.seneca.verify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.chem.AtomSets;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.chem.PathUtils;
import io.github.steinbeck.seneca.chem.StandardElements;
import io.github.steinbeck.seneca.judges.Attic;
import io.github.steinbeck.seneca.judges.FeatureJudge;
import io.github.steinbeck.seneca.judges.Judge;
import io.github.steinbeck.seneca.model.AnnealingSchedule;
import io.github.steinbeck.seneca.structgen.EngineLog;
import io.github.steinbeck.seneca.structgen.RunInput;
import io.github.steinbeck.seneca.structgen.RunOptions;
import io.github.steinbeck.seneca.structgen.RunRandom;
import io.github.steinbeck.seneca.structgen.SingleRandomStructureGenerator;
import io.github.steinbeck.seneca.structgen.StartStructureFeasibility;
import io.github.steinbeck.seneca.structgen.StochasticGenerator;

/**
 * Verifier "fidelity", round 2 (port only, no legacy classes needed).
 *
 * <p>Hang that DEFAULT does not fix: when the start structure has no Faulon neighbour that is connected,
 * the 2001 engine never returns - {@code mutate} loops for ever if no move exists at all (isobutane,
 * neopentane, tert-butanol, CCl4: every atom but one has one free valence, so the bond-order sums have
 * exactly one realisation, the star), and {@code initAnnealing} would loop for ever collecting its 500
 * connected calibration samples if every move disconnected the molecule. Neither loop checks the stop
 * flag, so the run cannot be stopped either.
 * {@link StochasticGenerator#validate} accepts these inputs (start structure POSSIBLE). Fixed by DV-65:
 * DEFAULT ends the run with the start structure as the only result (STRICT reports the hang as the run's
 * exception).
 *
 * <p>The run is executed in a child JVM (so a hang cannot leave a spinning thread in the test JVM).
 */
@Tag("verify")
class FidelityRound2VerifyTest {

    /** symbols and H counts of the test molecules. */
    static Object[] molecule(String name) {
        return switch (name) {
            case "isobutane" -> new Object[] {new String[] {"C", "C", "C", "C"}, new int[] {1, 3, 3, 3}};
            case "neopentane" -> new Object[] {new String[] {"C", "C", "C", "C", "C"}, new int[] {0, 3, 3, 3, 3}};
            case "tetrachloromethane" -> new Object[] {new String[] {"C", "Cl", "Cl", "Cl", "Cl"}, new int[] {0, 0, 0, 0, 0}};
            case "n-butane" -> new Object[] {new String[] {"C", "C", "C", "C"}, new int[] {3, 2, 2, 3}};
            case "tert-butanol" -> new Object[] {new String[] {"C", "C", "C", "C", "O"}, new int[] {0, 3, 3, 3, 1}};
            default -> throw new IllegalArgumentException(name);
        };
    }

    static HeavyAtom[] atoms(String name) {
        Object[] m = molecule(name);
        String[] sym = (String[]) m[0];
        int[] h = (int[]) m[1];
        HeavyAtom[] a = new HeavyAtom[sym.length];
        for (int i = 0; i < a.length; i++) {
            a[i] = new HeavyAtom(sym[i], i);
            a[i].hCount = h[i];
            a[i].maxBondOrder = StandardElements.getMaxBondOrder(sym[i]);
            a[i].maxBondOrderSum = StandardElements.getMaxBondOrderSum(sym[i]);
        }
        return a;
    }

    static RunInput input(String name) {
        List<Judge> judges = new ArrayList<>();
        FeatureJudge f = new FeatureJudge();
        f.setHetHetPenaltyEnabled(true);
        f.setEnabled(true);
        judges.add(f);
        for (Judge j : judges) if (j.getEnabled()) j.calcMaxScore();
        return new RunInput(atoms(name), judges, List.of(new AnnealingSchedule()), name);
    }

    /** Counts (move, outcome) of every Faulon move from {@code bm}: [valid moves, connected results]. */
    static int[] neighbours(int[][] bm, HeavyAtom[] atoms) {
        int n = bm.length;
        int moves = 0, connected = 0;
        for (int x1 = 0; x1 < n; x1++)
            for (int x2 = 0; x2 < n; x2++)
                for (int y1 = 0; y1 < n; y1++)
                    for (int y2 = 0; y2 < n; y2++) {
                        if (!(x1 != x2 && x1 != y1 && x1 != y2 && x2 != y1 && x2 != y2 && y1 != y2)) continue;
                        int a11 = bm[x1][y1], a12 = bm[x1][y2], a22 = bm[x2][y2], a21 = bm[x2][y1];
                        int min = Math.max(Math.max(0, a11 - a22), Math.max(a11 + a12 - 3, a11 + a21 - 3));
                        int max = Math.min(Math.min(3, a11 + a12), Math.min(a11 + a21, a11 - a22 + 3));
                        for (int b11 = min; b11 <= max; b11++) {
                            if (b11 == a11) continue;
                            moves++;
                            int[][] c = AtomSets.cloneInt2D(bm);
                            c[x1][y1] = c[y1][x1] = b11;
                            c[x1][y2] = c[y2][x1] = a11 + a12 - b11;
                            c[x2][y2] = c[y2][x2] = a22 - a11 + b11;
                            c[x2][y1] = c[y1][x2] = a11 + a21 - b11;
                            HeavyAtom[] cl = AtomSets.cloneAtomSet(atoms);
                            AtomSets.synchronizeBonds(cl, c);
                            if (PathUtils.isConnected(cl)) connected++;
                        }
                    }
        return new int[] {moves, connected};
    }

    @ParameterizedTest
    @ValueSource(strings = {"isobutane", "neopentane", "tert-butanol", "tetrachloromethane"})
    void defaultMustNotHangWhenTheStartStructureHasNoConnectedNeighbour(String name) throws Exception {
        HeavyAtom[] atoms = atoms(name);
        // the input passes the DEFAULT validation (start structure possible)
        assertNull(StochasticGenerator.validate(input(name)), "validate");
        assertEquals(StartStructureFeasibility.Verdict.POSSIBLE, StartStructureFeasibility.of(atoms));
        // deterministic proof that the engine can never leave mutate / initAnnealing from the start structure
        HeavyAtom[] start = SingleRandomStructureGenerator.generate(atoms, new Attic(), RunRandom.seeded(1));
        int[][] bm = AtomSets.createConnectionTable(start);
        AtomSets.synchronizeConnectionTable(bm);
        int[] nb = neighbours(bm, start);
        System.out.println("[verify] " + name + ": Faulon moves from the start structure = " + nb[0] + ", connected = " + nb[1]);
        assertEquals(0, nb[1], "precondition: no connected neighbour");

        // the run itself, in a child JVM: DEFAULT, Stop requested after 1 s; it must end (or be rejected)
        String out = runChild(name, 12);
        System.out.println("[verify] " + name + " child: " + out);
        assertTrue(out.startsWith("FINISHED") || out.startsWith("REJECTED"),
                "DEFAULT run on " + name + " neither rejected nor stoppable: " + out);
    }

    /** Control: a molecule with a connected neighbour terminates normally in the same child set-up. */
    @ParameterizedTest
    @ValueSource(strings = {"n-butane"})
    void controlTerminates(String name) throws Exception {
        String out = runChild(name, 60);
        assertTrue(out.startsWith("FINISHED"), out);
    }

    static String runChild(String name, int seconds) throws Exception {
        String java = ProcessHandle.current().info().command().orElse("java");
        ProcessBuilder pb = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                Child.class.getName(), name);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        StringBuilder sb = new StringBuilder();
        Thread reader = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String l;
                while ((l = r.readLine()) != null) synchronized (sb) {
                    sb.append(l).append('\n');
                }
            } catch (Exception ignored) {
                // child killed
            }
        });
        reader.setDaemon(true);
        reader.start();
        if (!p.waitFor(seconds + 30L, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            fail("child JVM did not exit");
        }
        reader.join(5000);
        synchronized (sb) {
            String s = sb.toString().trim();
            int i = s.lastIndexOf('\n');
            return i < 0 ? s : s.substring(i + 1);
        }
    }

    /** Child JVM: starts a DEFAULT run, requests Stop after 1 s, waits 8 s more, reports and exits. */
    public static final class Child {
        public static void main(String[] args) throws Exception {
            String name = args[0];
            StochasticGenerator g;
            try {
                g = new StochasticGenerator(input(name), new RunOptions(1L, LegacyCompat.DEFAULT, 0, null, EngineLog.NONE));
            } catch (IllegalArgumentException e) {
                System.out.println("REJECTED " + e.getMessage());
                System.exit(0);
                return;
            }
            final StochasticGenerator gen = g;
            Thread t = new Thread(gen::run, "run");
            t.setDaemon(true);
            t.start();
            t.join(1000);
            gen.end();
            t.join(8000);
            if (t.isAlive()) {
                System.out.println("HUNG iterations=" + gen.status().iteration() + " draws=" + gen.random().draws()
                        + " (stop requested 8 s ago)");
            } else {
                System.out.println("FINISHED iterations=" + gen.status().iteration());
            }
            System.exit(0);
        }
    }
}
