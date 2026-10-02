package io.github.steinbeck.seneca.structgen;

import java.util.ArrayList;
import java.util.List;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.chem.AtomSets;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.chem.IsomorphismTester;
import io.github.steinbeck.seneca.chem.LegacyMdlReader;
import io.github.steinbeck.seneca.chem.LegacyMdlWriter;
import io.github.steinbeck.seneca.chem.MolecularFormula;
import io.github.steinbeck.seneca.chem.PathUtils;
import io.github.steinbeck.seneca.judges.Attic;
import io.github.steinbeck.seneca.judges.Judge;
import io.github.steinbeck.seneca.judges.JudgeSet;
import io.github.steinbeck.seneca.judges.ScoreSummary;
import io.github.steinbeck.seneca.judges.SymmetryJudge;
import io.github.steinbeck.seneca.judges.TwoDRule;
import io.github.steinbeck.seneca.judges.TwoDSpectrumJudge;
import io.github.steinbeck.seneca.model.AnnealingSchedule;

/**
 * The simulated annealing engine ("StoIcGenT"). Port of
 * {@code legacy/original/src/seneca/structgen/StochasticGenerator.java} (+ {@code Rng} sites of
 * {@code legacy/runnable/src}); exact algorithm in docs/legacy/architecture.md section 4 - setup 4.1,
 * start structure 4.2, {@code mutate} 4.3, {@code initAnnealing} 4.4, main loop and {@code cool} 4.5,
 * stop 4.6, {@code notInStock} 4.7, draw order 4.8, {@code getScore} 4.9 ({@code judges.JudgeSet}).
 *
 * <p>Not a {@code Thread} any more: {@link #run()} is synchronous; {@link RunController} supplies the
 * thread. Everything that determines the search is reproduced exactly, in particular:
 * <ul>
 *   <li>one {@link RunRandom} per run, consumed in the legacy order (4 draws per atom-quadruple attempt,
 *       b11 draws, the Metropolis draw for every connected candidate);</li>
 *   <li>{@code long} scores/deltaE; {@code changes} as {@code long} with {@code (long)(changes/500)};
 *       {@code start_kT = -changes / Math.log(p0)} (so {@code changes == 0} gives -0.0);
 *       {@code exp = (double) deltaE / current_kT}; acceptance {@code deltaE <= 0 || rnd < Math.exp(-exp)};
 *       {@code Math} (not {@code StrictMath}) as legacy;</li>
 *   <li>{@code uphill} counts non-worse accepted moves; {@code convergenceCounter} reset only on strict
 *       improvement; best update on {@code >=} and {@code notInStock} (Morgan heuristic against the
 *       re-read stored MDL strings); results stored as legacy MDL text titled {@code "Score: " + score};</li>
 *   <li>reheat factor 0.3 + 0.1 per reheat in double arithmetic (max 8 reheats per run), plateau/uphill
 *       x1.5 truncated to long; {@code plateauStepCounter}/{@code uphill} not reset per schedule;</li>
 *   <li>{@code goOn} false at the start of each schedule; stop semantics of 4.6 (remaining schedules
 *       still run {@code initAnnealing} + one loop body);</li>
 *   <li>annealing log entry every 500 iterations after {@code cool()}; INFO log every 1000.</li>
 * </ul>
 * Switch points ({@code RunOptions.compat}, DEVIATIONS.md): Q1 (first candidate of a schedule
 * disconnected: STRICT ends the schedule; DEFAULT does the same unless it is the last schedule and no
 * result has been stored, where it keeps sampling until a connected candidate is found - no other change), empty schedule list (STRICT: exception_in_run as legacy {@code elementAt(0)};
 * DEFAULT: {@link IllegalArgumentException} from the constructor), inputs on which legacy hangs (fewer
 * than 4 atoms: the Faulon move never finds four distinct atoms; free valences that cannot be
 * saturated or not reachable for the start generator: it restarts for ever) are rejected by the
 * constructor in DEFAULT ({@link #validate}); a stop requested before {@link #run()} starts is lost in
 * STRICT (legacy {@code run()} clears the flag) and honoured in DEFAULT (DV-62); between restarts of the
 * start generator a stop is honoured only where legacy would restart for ever (DV-60), in DEFAULT also
 * after {@link #START_STOP_GRACE} restarts, and DEFAULT starts from a constructed structure after
 * {@link #START_RESTART_LIMIT} restarts (DV-64); a start structure without a connected Faulon neighbour,
 * on which legacy draws for ever, is reported as the run's exception in STRICT and is the only result in
 * DEFAULT (DV-65).
 * In both modes an exception during the run ends it like the legacy outer {@code catch (Exception)}:
 * no rethrow, {@link RunResult#exception()} set.
 *
 * <p>Thread safety: all fields are confined to the run thread, except the stop flag and the iteration
 * counter (volatile), and the best structure/summary and result list, which are written under
 * {@code lock} and copied under it by {@link #status()} and {@link #resultCopy()} (design.md 8).
 */
public final class StochasticGenerator {

    public static final String VERSION = "StoIcGenT V 0.6 beta, (c) 1998, 1999, 2000, Christoph Steinbeck";

    /** Number of calibration samples of {@code initAnnealing()}. */
    static final int CALIBRATION_SAMPLES = 500;

    /**
     * DEFAULT only (DV-64): after this many restarts of the start generator without a saturated,
     * connected structure, the run takes a constructed start structure
     * ({@link StartStructureFeasibility#witness}) instead of restarting further. The 2001 program keeps
     * restarting; for molecules with many monovalent atoms (perfluoroalkanes: C8F18 has no start
     * structure after 1,000,000 restarts, about 12 s) that is an endless run. The golden inputs need at
     * most 23 restarts (200 seeds x 4 profiles x 11 datasets).
     */
    public static final long START_RESTART_LIMIT = 1_000_000;

    /**
     * DEFAULT only (DV-64): from this many restarts of the start generator on, a stop request is honoured
     * between two restarts for every input (legacy ignores it until the start structure exists). Below
     * it, a stop is ignored as in legacy unless the input can never succeed (DV-60).
     */
    public static final long START_STOP_GRACE = 10_000;

    private final RunInput input;
    private final RunOptions options;
    private final LegacyCompat compat;
    private final RunRandom random;
    private final JudgeSet judges;
    private final List<AnnealingSchedule> schedules;
    private final StepObserver observer;
    private final EngineLog log;
    private final String datasetName;
    private final String molecularFormula;

    private final Object lock = new Object();
    private final Attic attic = new Attic();
    private final AnnealingLog al = new AnnealingLog();
    private final StructureGeneratorResult structureGeneratorResult = new StructureGeneratorResult();
    private final Context context = new Context();

    private HeavyAtom[] atomSet;
    private int[][] bondMatrix;
    private int[][] candidate;
    private ScoreSummary lastScore;
    private ScoreSummary recentScore;
    private ScoreSummary bestScore;
    private String bestStructure;
    private long maxScore = -1;

    private volatile long iteration = 0;
    private volatile boolean goOn = false;
    private volatile boolean pleaseStop = false;
    private volatile boolean started = false;
    private volatile boolean finished = false;
    private boolean capped = false;
    private volatile boolean stopRequestedExternally = false;
    private StartStructureFeasibility.Verdict startVerdict;

    private double start_kT = 0;
    private double current_kT = 0;
    private double coolingRate = 0;
    private long plateauSteps = 0;
    private long maxUphillSteps = 50;
    private long convergenceStopCount = 0;
    private long convergenceCounter = 0;
    /** Written as in legacy, never read (kept only to mirror the legacy field). */
    @SuppressWarnings("unused")
    private long annealingStepCounter;
    private int plateauStepCounter = 0;
    private int uphill = 0;
    private int currentScheduleNo = 0;

    /**
     * Legacy {@code new StochasticGenerator(true)} + {@code setMolecule} (two independent clones of the
     * atoms: {@code atomSet} and {@code attic.atomSet}) + {@code setJudges} + {@code setDatasetName} +
     * {@code setSASchedules}. The generator works on a private deep copy of {@code input}
     * ({@link RunInput#copy()}), so one input can be started several times.
     *
     * @throws IllegalArgumentException in DEFAULT mode for inputs on which the legacy run fails before or
     *                                  instead of producing anything (no schedule, fewer than 4 atoms,
     *                                  free valences that cannot be saturated), see {@link #validate}
     */
    public StochasticGenerator(RunInput input, RunOptions options) {
        this.options = options;
        this.compat = options.compat();
        if (!compat.strict()) {
            String problem = validate(input);
            if (problem != null) throw new IllegalArgumentException(problem);
        }
        // a private deep copy (legacy: every server received its own serialized copy): one RunInput
        // started twice must not let two runs share judge objects (their attics would point at each
        // other's candidates). Judge.copy has the legacy serClone semantics, so nothing else changes.
        this.input = input.copy();
        input = this.input;
        this.random = options.seed() == null ? RunRandom.unseeded() : RunRandom.seeded(options.seed());
        this.judges = new JudgeSet(new ArrayList<>(input.judges()));
        this.schedules = new ArrayList<>(input.schedules());
        this.observer = options.observer();
        this.log = options.log();
        this.datasetName = input.datasetName();
        this.atomSet = AtomSets.cloneAtomSet(input.atoms());
        this.attic.atomSet = AtomSets.cloneAtomSet(input.atoms());
        this.attic.normalized = AtomSets.cloneAtomSet(input.atoms());
        this.attic.compat = compat;
        String mf;
        try {
            mf = MolecularFormula.formulaOf(atomSet);
        } catch (RuntimeException e) {
            mf = "not available";
        }
        this.molecularFormula = mf;
    }

    /**
     * Checks an input for the conditions on which the legacy engine fails without a result: an empty
     * schedule list (exception), fewer than 4 heavy atoms ({@code mutate} loops for ever), and atoms
     * from which the start generator can never build a saturated connected structure (it restarts for
     * ever). The last condition is first checked with cheap necessary conditions on the free valences
     * ({@code maxBondOrderSum - hCount}; they give a specific message: negative, zero in a molecule of 2
     * or more atoms, more than {@code maxBondOrder} bonds to every other atom can carry, odd sum, fewer
     * than a spanning tree needs, one atom with more than all others together) and then exactly with
     * {@link StartStructureFeasibility} (e.g. acetone: the start generator never forms the C=O double
     * bond). An input whose feasibility cannot be decided within the search budget is accepted (it can
     * still be stopped, DEVIATIONS.md DV-60). Also rejected: no atoms at all, and an enabled judge whose
     * configuration refers to atoms the molecule does not have ({@link #staleJudgeConfiguration}, DV-66;
     * legacy: every evaluation throws, the run ends without a result).
     *
     * @return null if the input is acceptable, else the reason
     */
    public static String validate(RunInput input) {
        if (input.schedules().isEmpty()) return "no annealing schedule";
        HeavyAtom[] atoms = input.atoms();
        if (atoms == null || atoms.length == 0) return NO_ATOMS;
        int n = atoms.length;
        if (n < 4) return "a structure generation needs at least 4 heavy atoms, got " + n;
        String stale = staleJudgeConfiguration(input.judges(), n);
        if (stale != null) return stale;
        long sum = 0, max = 0;
        for (HeavyAtom a : atoms) {
            int free = a.maxBondOrderSum - a.hCount;
            if (free < 0) return "atom " + a.number + " (" + a.symbol + ") has more hydrogens (" + a.hCount + ") than its valence " + a.maxBondOrderSum;
            if (free == 0) return "atom " + a.number + " (" + a.symbol + ") has no free valence; the structure cannot be connected";
            if (free > (long) a.maxBondOrder * (n - 1))
                return "atom " + a.number + " (" + a.symbol + ") has more free valence (" + free + ") than bonds to the other atoms can take";
            sum += free;
            max = Math.max(max, free);
        }
        if (sum % 2 != 0) return "the sum of free valences (" + sum + ") is odd: check the hydrogen counts";
        if (sum < 2L * (n - 1)) return "too few free valences (" + sum + ") to connect " + n + " atoms";
        if (max > sum - max) return "one atom has more free valence than all others together";
        if (StartStructureFeasibility.of(atoms) == StartStructureFeasibility.Verdict.IMPOSSIBLE)
            return "no saturated, connected start structure can be built from these atoms and hydrogen counts "
                    + "(the start structure generator forms single bonds, and double bonds only between C, Si, N or P "
                    + "atoms with at least 3 free valences, e.g. no C=O): check the hydrogen counts";
        return null;
    }

    /** Reason given by {@link #validate} and {@code RunInput.fromDataset} (DEFAULT) for a dataset without atoms. */
    public static final String NO_ATOMS = "the dataset has no atoms: enter a molecular formula";

    /**
     * DV-66: an enabled judge whose configuration refers to atoms the molecule does not have (the
     * molecular formula was changed after the judge was configured; {@code SenecaDataset.setMolecularFormula}
     * keeps the judges). Exactly the configurations on which every evaluation of that judge throws
     * {@link ArrayIndexOutOfBoundsException}, so that the legacy run ends at its first scored structure
     * without a result: a 2D rule (existing, or one {@code init()} would add from {@code assignment}) with
     * an atom index {@code >= n}, and a symmetry class with two or more members of which the last is
     * {@code >= n} (a lone out-of-range member is never looked up). Stale HOSE shift arrays and
     * Hybridization/HeteroBonds tables do not fail (the judge catches the index error, or
     * {@code init()} reallocates the table on the generator side) and are not rejected.
     *
     * @return null if no enabled judge is stale, else the reason
     */
    static String staleJudgeConfiguration(List<Judge> judges, int n) {
        for (Judge j : judges) {
            if (!j.getEnabled()) continue;
            if (j instanceof TwoDSpectrumJudge t) {
                int worst = -1;
                for (TwoDRule r : t.rules) worst = Math.max(worst, Math.max(r.atom1, r.atom2));
                boolean[][][] a = t.assignment;
                if (a != null) {
                    // init() walks f, g, h < a.length; a ragged matrix fails there in both modes, so the
                    // check only has to be safe on it, not exact
                    for (int f = 0; f < a.length; f++) {
                        if (a[f] == null) continue;
                        for (int g = 0; g < a[f].length; g++) {
                            if (a[f][g] == null) continue;
                            for (int h = 0; h < a[f][g].length; h++)
                                if (a[f][g][h] && (f >= n || h >= n)) worst = Math.max(worst, Math.max(f, h));
                        }
                    }
                }
                if (worst >= n) return staleMessage(j, worst, n);
            } else if (j instanceof SymmetryJudge s) {
                for (int c = 0; c < s.getSymmetryClassCount(); c++) {
                    boolean[] members = s.getSymmetryClass(c);
                    int count = 0, last = -1;
                    for (int g = 0; g < members.length; g++) {
                        if (members[g]) {
                            count++;
                            last = g;
                        }
                    }
                    if (count >= 2 && last >= n) return staleMessage(j, last, n);
                }
            }
        }
        return null;
    }

    private static String staleMessage(Judge j, int atom, int n) {
        return "the configuration of the " + j.name + " refers to atom " + (atom + 1) + " but the molecule has "
                + n + " heavy atoms (was the molecular formula changed after the judge was configured?): "
                + "configure the judge again";
    }

    /** Runs to completion on the calling thread; never throws for engine failures (see class doc). */
    public RunResult run() {
        synchronized (lock) {
            if (started) throw new IllegalStateException("a StochasticGenerator runs once");
            started = true;
        }
        if (compat.strict()) {
            // legacy run() sets pleaseStop = false on entry: a stop requested before the run started is lost
            pleaseStop = false;
            stopRequestedExternally = false;
        }
        Throwable exception = null;
        log.info("Start of StoicGent\n");
        maxScore = judges.maxScore();
        long starttime = System.currentTimeMillis();
        log.info("Start time: " + new java.util.Date(starttime) + "\n");
        double reheatFactor = 0.3;
        try {
            int[][] bm;
            lastScore = null;
            recentScore = null;
            bestScore = null;
            long deltaE;
            double rnd;
            double exp;
            iteration = 0;
            HeavyAtom[] start = startStructure();
            if (start == null) {
                // stopped by the user while the start generator restarted on an input it can never
                // finish, or (DEFAULT) for a long time (legacy: endless loop; port only, DV-60, DV-64)
                log.info("Stopped before a start structure was found.\n");
                return finish(null);
            }
            atomSet = start;
            bondMatrix = AtomSets.createConnectionTable(atomSet);
            AtomSets.synchronizeConnectionTable(bondMatrix);
            if (observer != null) observer.onStartStructure(bondMatrix);
            lastScore = new ScoreSummary(0, "");
            synchronized (lock) {
                bestScore = new ScoreSummary(0, "");
            }
            judges.initJudges(attic);
            if (!compat.strict() && !hasConnectedNeighbour(bondMatrix)) {
                // DV-65: no Faulon move leads from the start structure to another connected structure
                // (legacy initAnnealing draws for ever, see there); it is the only possible structure
                onlyStructure();
                return finish(null);
            }
            do {
                initAnnealing();
                log.info("start_kT = " + start_kT);
                current_kT = start_kT;
                annealingStepCounter = 0;
                boolean stepInSchedule = false;
                do {
                    bm = AtomSets.cloneInt2D(bondMatrix);
                    mutate(bm);
                    AtomSets.synchronizeBonds(attic.atomSet, bm);
                    boolean isConnected = PathUtils.isConnected(attic.atomSet);
                    if (isConnected) {
                        stepInSchedule = true;
                        candidate = bm;
                        recentScore = getScore(bm);
                        deltaE = lastScore.score - recentScore.score;
                        if (observer != null) observer.beforeAcceptance(context);
                        rnd = random.nextDouble();
                        exp = ((double) deltaE / (double) current_kT);
                        if (deltaE <= 0 || rnd < Math.exp(-exp)) {
                            if (deltaE <= 0) uphill++;
                            lastScore = recentScore;
                            bondMatrix = bm;
                            if (deltaE < 0) convergenceCounter = 0;
                            if (recentScore.score >= bestScore.score) {
                                HeavyAtom[] latestResult = AtomSets.cloneAtomSet(atomSet);
                                AtomSets.synchronizeBonds(latestResult, bm);
                                if (notInStock(latestResult)) storeBest(latestResult);
                            }
                        }
                        convergenceCounter++;
                        annealingStepCounter++;
                        iteration = iteration + 1;
                        cool();
                        long it = iteration;
                        if (it % AnnealingLog.ITERATION_AXIS_STEPSIZE == 0) {
                            al.addEntry((float) current_kT, (float) bestScore.score);
                        }
                        if (it % 1000 == 0) {
                            log.info("Iteration: " + it + " ");
                            log.info("Current kT = " + current_kT + "\n");
                        }
                        goOn = true;
                        if (convergenceCounter >= convergenceStopCount) {
                            if (bestScore.score < maxScore && reheatFactor < 1) {
                                current_kT = current_kT + ((start_kT - current_kT) * reheatFactor);
                                convergenceCounter = 0;
                                annealingStepCounter = 0;
                                plateauSteps = (long) ((double) plateauSteps * 1.5);
                                maxUphillSteps = (long) ((double) maxUphillSteps * 1.5);
                                if (reheatFactor < 1) reheatFactor += 0.1;
                                log.info("Calculation converged.\n");
                                log.info("Best score achieved in this run: " + bestScore.score + "\n");
                                log.info("Maximum possible score: " + maxScore + "\n");
                                log.info("Maximum score not reached: Reheating by " + (reheatFactor * 100) + "%\n");
                                log.info("Increasing plateauSteps to : " + plateauSteps + "\n");
                                log.info("Increasing maximum uphill moves to : " + maxUphillSteps + "\n");
                            } else if (reheatFactor >= 1) {
                                log.info("Calculation did not converge despite successive parameter modifications.\n");
                                log.info("Best score achieved in this run: " + bestScore.score + "\n");
                                log.info("Maximum possible score: " + maxScore + "\n");
                                log.info("Maximum score not reached: Aborting.\n");
                                goOn = false;
                            } else if (bestScore.score >= maxScore) {
                                log.info("Calculation converged.\n");
                                log.info("Best score achieved in this run: " + bestScore.score + "\n");
                                log.info("Maximum possible score: " + maxScore + "\n");
                                log.info("Maximum score reached. Regular end of calculation.\n");
                                goOn = false;
                            }
                        }
                        if (observer != null) observer.afterStep(context);
                    }
                }
                // Q1 (switch point, DV-04): legacy leaves the loop when the first candidate of a schedule
                // is disconnected (goOn is still false). On a non-final schedule, or once a result is
                // stored, that only skips the schedule and the run ends normally, so both modes do it.
                // Only where the run would end without any result (last schedule, nothing stored) does
                // DEFAULT keep sampling until a connected candidate is found.
                while ((goOn || (!stepInSchedule && q1KeepsSampling())) && !pleaseStop);
                goOn = false;
            } while (currentScheduleNo < schedules.size());
            long endtime = System.currentTimeMillis();
            log.info("Stop: " + new java.util.Date(endtime) + "\n");
            log.info("Overall runtime: " + ((endtime - starttime) / 1000) + " seconds.\n");
            log.info("Simulated annealing run finished.\n");
        } catch (Exception e) {
            log.info("Error in StoicGent.run()\n");
            exception = e;
        }
        return finish(exception);
    }

    /**
     * DEFAULT fix of quirk Q1 (DV-04): true only if leaving the current schedule now would end the run
     * without any result, i.e. it is the last schedule and no structure has been stored. Everywhere else
     * DEFAULT leaves the schedule exactly as legacy does.
     */
    private boolean q1KeepsSampling() {
        if (compat.strict() || currentScheduleNo < schedules.size()) return false;
        synchronized (lock) {
            return bestStructure == null;
        }
    }

    private RunResult finish(Throwable exception) {
        int[][] crash = null;
        if (exception != null && attic.atomSet != null) {
            crash = AtomSets.createConnectionTable(attic.atomSet);
            AtomSets.synchronizeConnectionTable(crash);
        }
        ScoreSummary best;
        String bestMdl;
        StructureGeneratorResult results;
        synchronized (lock) {
            best = bestScore == null ? new ScoreSummary(0, "") : bestScore.copy();
            bestMdl = bestStructure;
            results = structureGeneratorResult.copy();
        }
        RunResult r = new RunResult(iteration, random.draws(), maxScore, start_kT, current_kT, plateauSteps,
                maxUphillSteps, best, bestMdl, results, exception, crash,
                bondMatrix == null ? null : AtomSets.cloneInt2D(bondMatrix), capped, stopRequestedExternally);
        finished = true;
        goOn = false;
        if (observer != null) observer.onFinished(r);
        return r;
    }

    /**
     * DEFAULT (DV-65): the start structure is the only saturated connected structure these atoms allow
     * (e.g. isobutane, CCl4, neopentane, tert-butanol): score it, store it as the only result and end
     * the run without annealing. No random number is drawn.
     */
    private void onlyStructure() {
        AtomSets.synchronizeBonds(attic.atomSet, bondMatrix);
        candidate = bondMatrix;
        recentScore = getScore(bondMatrix);
        lastScore = recentScore;
        HeavyAtom[] latestResult = AtomSets.cloneAtomSet(atomSet);
        AtomSets.synchronizeBonds(latestResult, bondMatrix);
        storeBest(latestResult);
        log.info("The start structure is the only structure that can be built from these atoms "
                + "(no Faulon move leads to another connected structure).\n");
        log.info("Best score achieved in this run: " + bestScore.score + "\n");
        log.info("Maximum possible score: " + maxScore + "\n");
        log.info("Simulated annealing run finished.\n");
    }

    /**
     * True if some Faulon move (any four distinct atoms, any admissible {@code b11 != a11}, exactly the
     * moves {@link #mutate(int[][], RunRandom)} can draw) turns {@code bm} into a connected structure.
     * Draws no random number. Moves are reversible, so every structure reached through connected moves
     * from a structure with this property has it too: checking the start structure once suffices.
     */
    static boolean hasConnectedNeighbour(int[][] bm) {
        int n = bm.length;
        int[][] c = AtomSets.cloneInt2D(bm);
        for (int x1 = 0; x1 < n; x1++) {
            for (int x2 = 0; x2 < n; x2++) {
                if (x2 == x1) continue;
                for (int y1 = 0; y1 < n; y1++) {
                    if (y1 == x1 || y1 == x2) continue;
                    for (int y2 = 0; y2 < n; y2++) {
                        if (y2 == x1 || y2 == x2 || y2 == y1) continue;
                        int a11 = bm[x1][y1], a12 = bm[x1][y2], a22 = bm[x2][y2], a21 = bm[x2][y1];
                        int min = Math.max(Math.max(0, a11 - a22), Math.max(a11 + a12 - 3, a11 + a21 - 3));
                        int max = Math.min(Math.min(3, a11 + a12), Math.min(a11 + a21, a11 - a22 + 3));
                        for (int b11 = min; b11 <= max; b11++) {
                            if (b11 == a11) continue;
                            c[x1][y1] = c[y1][x1] = b11;
                            c[x1][y2] = c[y2][x1] = a11 + a12 - b11;
                            c[x2][y2] = c[y2][x2] = a22 - a11 + b11;
                            c[x2][y1] = c[y1][x2] = a11 + a21 - b11;
                            boolean connected = connected(c);
                            c[x1][y1] = c[y1][x1] = a11;
                            c[x1][y2] = c[y2][x1] = a12;
                            c[x2][y2] = c[y2][x2] = a22;
                            c[x2][y1] = c[y1][x2] = a21;
                            if (connected) return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /** Connectivity of a bond matrix (breadth-first search). */
    private static boolean connected(int[][] bm) {
        int n = bm.length;
        if (n == 0) return true;
        boolean[] seen = new boolean[n];
        int[] queue = new int[n];
        int head = 0, tail = 0, count = 1;
        seen[0] = true;
        queue[tail++] = 0;
        while (head < tail) {
            int v = queue[head++];
            for (int u = 0; u < n; u++) {
                if (bm[v][u] != 0 && !seen[u]) {
                    seen[u] = true;
                    count++;
                    queue[tail++] = u;
                }
            }
        }
        return count == n;
    }

    /**
     * The start structure (legacy {@code SingleRandomStructureGenerator}). STRICT: the legacy generator
     * with the DV-60 stop check. DEFAULT (DV-64): the same generator, but a stop request is honoured
     * between restarts once {@link #START_STOP_GRACE} restarts have failed, and after
     * {@link #START_RESTART_LIMIT} failed restarts a constructed structure is used instead
     * ({@link StartStructureFeasibility#witness}; draws no random number). Runs whose start structure
     * is found within the limit, and stopped runs within the grace, are identical to legacy.
     *
     * @return the start structure, or null if the run was stopped before one was found
     */
    private HeavyAtom[] startStructure() {
        if (compat.strict()) return SingleRandomStructureGenerator.generate(atomSet, attic, random, this::stopStartGeneration);
        long[] restarts = {0};
        boolean[] limit = {false};
        HeavyAtom[] start = SingleRandomStructureGenerator.generate(atomSet, attic, random, () -> {
            restarts[0]++;
            if (stopStartGeneration() || (pleaseStop && restarts[0] >= START_STOP_GRACE)) return true;
            if (restarts[0] >= START_RESTART_LIMIT) limit[0] = true;
            return limit[0];
        });
        if (start != null || !limit[0]) return start;
        int[][] witness = StartStructureFeasibility.witness(input.atoms());
        if (witness != null) {
            log.info("No start structure after " + restarts[0] + " restarts of the random start structure generator; "
                    + "starting from a constructed structure.\n");
            return SingleRandomStructureGenerator.fromBondMatrix(atomSet, attic, witness);
        }
        // feasibility undecided within the search budget: keep restarting, a stop ends it
        return SingleRandomStructureGenerator.generate(atomSet, attic, random, () -> pleaseStop);
    }

    /** The accepted candidate is a new best not in stock: legacy MDL text, logs, result queues. */
    private void storeBest(HeavyAtom[] latestResult) {
        String structure = LegacyMdlWriter.write("Score: " + recentScore.score, latestResult);
        String previousBest = "New best Structure: \n" + bestScore + "\n";   // legacy logs the previous best
        synchronized (lock) {
            bestScore = recentScore;
            bestStructure = structure;
            structureGeneratorResult.push(structure, recentScore);
        }
        log.result(structure + "\n$$$$\n");
        log.info(previousBest);
        if (observer != null) observer.onNewBest(structure, recentScore.copy());
    }

    /**
     * Initializes the annealing procedure each time a new schedule is taken from the list (legacy
     * {@code initAnnealing}): 500 connected calibration samples around the current structure.
     */
    private void initAnnealing() {
        if (currentScheduleNo >= schedules.size()) {
            // legacy Vector.elementAt (D10: empty schedule list)
            throw new ArrayIndexOutOfBoundsException(currentScheduleNo + " >= " + schedules.size());
        }
        int schedule = currentScheduleNo;
        AnnealingSchedule currentSchedule = schedules.get(currentScheduleNo);
        currentScheduleNo++;
        if (schedule == 0 && compat.strict() && !hasConnectedNeighbour(bondMatrix)) {
            // DV-65: no Faulon move leads from the start structure to another connected structure, so the
            // loop below never collects a sample (mutate itself never returns if there is no valid move at
            // all, e.g. fewer than 4 atoms or a star such as isobutane) and legacy draws for ever,
            // unstoppably. STRICT reports the hang as the run's exception (as DV-03); DEFAULT never gets
            // here (run() ends with the start structure as the only result). Later schedules start from a
            // structure reached by a connected move, so they always have a connected neighbour.
            throw new IllegalStateException("StochasticGenerator.initAnnealing: no Faulon move leads from the start "
                    + "structure to another connected structure; the legacy engine loops for ever here");
        }
        ScoreSummary last = new ScoreSummary(0, "");
        long changes = 0;
        int f = 0;
        do {
            int[][] bm = AtomSets.cloneInt2D(bondMatrix);
            mutate(bm);
            AtomSets.synchronizeBonds(attic.atomSet, bm);
            if (PathUtils.isConnected(attic.atomSet)) {
                ScoreSummary recent = getScore(bm);
                changes += Math.abs(last.score - recent.score);
                last = recent;
                f++;
                if (observer != null) observer.onInitEvaluation(schedule, f, recent.score, bm, random.draws());
            }
        } while (f < CALIBRATION_SAMPLES);
        plateauSteps = currentSchedule.plateauSteps;
        maxUphillSteps = currentSchedule.maxUphillSteps;
        convergenceCounter = 0;
        convergenceStopCount = plateauSteps * 3;
        changes = (long) (changes / CALIBRATION_SAMPLES);
        start_kT = -changes / Math.log(currentSchedule.initialAcceptanceProbability);
        current_kT = start_kT;
        coolingRate = currentSchedule.asymptoticCoolingFactor;
        if (observer != null)
            observer.onScheduleInitialized(schedule, start_kT, plateauSteps, maxUphillSteps, convergenceStopCount, coolingRate);
    }

    /** Legacy {@code cool()}. */
    private void cool() {
        if (plateauStepCounter > plateauSteps || uphill > maxUphillSteps) {
            current_kT *= coolingRate;
            plateauStepCounter = 0;
            uphill = 0;
        }
        plateauStepCounter++;
    }

    /** Legacy {@code notInStock}: Morgan heuristic against every stored (re-read) structure. */
    private boolean notInStock(HeavyAtom[] latest) {
        IsomorphismTester isoTest = new IsomorphismTester(latest);
        for (int f = 0; f < structureGeneratorResult.structures.size(); f++) {
            if (isoTest.isIsomorph(LegacyMdlReader.read(structureGeneratorResult.structures.get(f)))) return false;
        }
        return true;
    }

    /** Legacy {@code getScore(bm)}; the candidate is already synchronized into {@code attic.atomSet}. */
    private ScoreSummary getScore(int[][] bm) {
        return judges.score(bm).toScoreSummary();
    }

    /**
     * Stop check of the start generator between two restarts (DV-60). Legacy ignores a stop request until
     * the start structure exists, so the request is honoured only where legacy would restart for ever:
     * STRICT only for inputs proven impossible, DEFAULT also for inputs the feasibility search could not
     * decide (DEFAULT has already rejected the impossible ones). For every input on which the start
     * generator can succeed the run continues exactly as legacy (initAnnealing + one loop body per
     * remaining schedule, then the stop). Run thread only; the verdict is computed on first need.
     */
    private boolean stopStartGeneration() {
        if (!pleaseStop) return false;
        if (startVerdict == null) startVerdict = StartStructureFeasibility.of(input.atoms());
        return compat.strict() ? startVerdict == StartStructureFeasibility.Verdict.IMPOSSIBLE
                : startVerdict != StartStructureFeasibility.Verdict.POSSIBLE;
    }

    /** Legacy {@code end()}: request stop (any thread). */
    public void end() {
        stopRequestedExternally = true;
        pleaseStop = true;
    }

    /** Thread-safe progress snapshot (any thread). */
    public RunStatus status() {
        String best;
        ScoreSummary eval;
        synchronized (lock) {
            best = bestStructure;
            eval = bestStructure == null ? null : bestScore.copy();
        }
        if (eval != null) eval.maxScore = maxScore;
        RunStatus.State state;
        if (!started) state = RunStatus.State.IDLE;
        else if (pleaseStop) state = RunStatus.State.STOPPED;
        else if (goOn) state = RunStatus.State.RUNNING;
        else state = finished ? RunStatus.State.HOLDING : RunStatus.State.RUNNING;
        return new RunStatus(datasetName, molecularFormula, iteration, best, eval, state, al.size());
    }

    /** True once {@link #run()} has returned. */
    public boolean isFinished() {
        return finished;
    }

    /** Thread-safe deep copy of the result list (legacy {@code getResult()} with RMI semantics, D6). */
    public StructureGeneratorResult resultCopy() {
        synchronized (lock) {
            return structureGeneratorResult.copy();
        }
    }

    /** Annealing log entries {@code [from, size)} (any thread). */
    public List<AnnealingLog.Entry> annealingLogSince(int from) {
        return al.since(from);
    }

    /**
     * Legacy {@code mutate(int[][] bm)} (Faulon move, architecture.md 4.3) on this run's random source.
     * Applies the iteration-cap hook first ({@link RunOptions#iterationCap()}): the first call after
     * {@code iteration} reached the cap calls {@code end()} (golden harness {@code CappedGenerator}).
     */
    public void mutate(int[][] bm) {
        long cap = options.iterationCap();
        if (cap > 0 && !capped && iteration >= cap) {
            capped = true;
            pleaseStop = true;
        }
        mutate(bm, random);
    }

    /**
     * The pure Faulon move of {@link #mutate(int[][])} on an explicit random source (no cap hook): the
     * same draws in the same order. Used by the golden scores random walk ({@code GoldenMaster.randomWalk}).
     */
    public static void mutate(int[][] bm, RunRandom random) {
        int x1, x2, y1, y2;
        int a11, a12, a22, a21, b11;
        int min, max;
        int nrOfAtoms = bm.length;
        do {
            do {
                x1 = (int) (random.nextDouble() * nrOfAtoms);
                x2 = (int) (random.nextDouble() * nrOfAtoms);
                y1 = (int) (random.nextDouble() * nrOfAtoms);
                y2 = (int) (random.nextDouble() * nrOfAtoms);
            } while (!(x1 != x2 && x1 != y1 && x1 != y2 && x2 != y1 && x2 != y2 && y1 != y2));
            a11 = bm[x1][y1];
            a12 = bm[x1][y2];
            a22 = bm[x2][y2];
            a21 = bm[x2][y1];
            // legacy max(cmax) / min(cmin) with cmax = {0, a11 - a22, a11 + a12 - 3, a11 + a21 - 3},
            // cmin = {3, a11 + a12, a11 + a21, a11 - a22 + 3}
            min = Math.max(Math.max(0, a11 - a22), Math.max(a11 + a12 - 3, a11 + a21 - 3));
            max = Math.min(Math.min(3, a11 + a12), Math.min(a11 + a21, a11 - a22 + 3));
            b11 = a11;
            if (max - min > 0 || a11 != min) {
                do {
                    b11 = ((int) (random.nextDouble() * (max - min + 1))) + min;
                } while (a11 == b11);
            }
        } while (!(b11 != a11 && (b11 >= min && b11 <= max)));
        bm[x1][y1] = b11;
        bm[x1][y2] = a11 + a12 - b11;
        bm[x2][y2] = a22 - a11 + b11;
        bm[x2][y1] = a11 + a21 - b11;
        bm[y1][x1] = b11;
        bm[y2][x1] = a11 + a12 - b11;
        bm[y2][x2] = a22 - a11 + b11;
        bm[y1][x2] = a11 + a21 - b11;
    }

    /** This run's random source (draw counter for tests and golden records). */
    public RunRandom random() {
        return random;
    }

    public RunInput input() {
        return input;
    }

    public RunOptions options() {
        return options;
    }

    /** The generator state as the observers see it (generator thread only). */
    private final class Context implements StepContext {
        @Override
        public long iteration() {
            return iteration;
        }

        @Override
        public double currentKT() {
            return current_kT;
        }

        @Override
        public long candidateScore() {
            return recentScore.score;
        }

        @Override
        public int[][] candidate() {
            return candidate;
        }

        @Override
        public int[][] current() {
            return bondMatrix;
        }

        @Override
        public boolean accepted() {
            return bondMatrix == candidate;
        }

        @Override
        public long lastScore() {
            return lastScore.score;
        }

        @Override
        public long bestScore() {
            return bestScore.score;
        }

        @Override
        public int resultCount() {
            return structureGeneratorResult.size();
        }

        @Override
        public long draws() {
            return random.draws();
        }

        @Override
        public void requestStop() {
            end();
        }

        @Override
        public StructureGeneratorResult resultCopy() {
            return StochasticGenerator.this.resultCopy();
        }
    }
}
