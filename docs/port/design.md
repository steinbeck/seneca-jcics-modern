# SENECA port – design

Port of SENECA 2001 (Steinbeck, *J. Chem. Inf. Comput. Sci.* 2001, 41, 1500) to JDK 25 and CDK 2.13,
final product a macOS `Seneca.app`. This document is the contract for the implementers of work
packages A, B and C (section 11). The specification of *behaviour* is not repeated here; it is:

| Document | Content |
|---|---|
| `docs/legacy/architecture.md` | algorithm (section 4), judges (5), compchem usage (6), results (7), defects D1–D16 (9) |
| `docs/legacy/feature-inventory.md` | every user-visible function F-xx, file formats (7) |
| `docs/legacy/compchem-semantics.md` | exact compchem semantics and the CDK verdict per algorithm |
| `golden/FORMAT.md` | the golden master: GUI recipe, record formats, quirks, findings |
| `legacy/runnable/HOW-TO-DRIVE.md`, `PATCHES.md` | how a run is driven; the four patches of the runnable copy |
| `legacy/decompiled/` | compchem, jmdraw, 2001 jar (Vineflower) – the reference for every compchem port |
| `docs/port/DEVIATIONS.md` | every deviation from legacy behaviour (what, why, mode, golden records) |

## 1. Policy (binding, summarised)

1. Everything that determines scores or the search is reproduced **exactly**, including legacy quirks
   (Hybridization/HeteroBonds judges scoring 0 after `init()`, SymmetryJudge max-score scale,
   Morgan "isomorphism" ignoring bond orders, kT = -0.0, number and order of random draws, `Math.*` as
   used by legacy). compchem algorithms that CDK does not reproduce identically are re-implemented
   verbatim from `legacy/decompiled`. CDK only where provably identical, for I/O and depiction.
2. Crashes, hangs and data loss are fixed **only behind `LegacyCompat`**: `STRICT` reproduces the legacy
   failure, `DEFAULT` (the app) fixes it, without changing the random stream or any outcome of runs in
   which the legacy code does not fail (section 5). A hang may be detected and reported in `STRICT` as
   the run's exception (ring finder, `initAnnealing` without a connected move, empty `removeIsomorphism`).
   **Exception (decided):** failures in mechanics that the port replaces anyway — RMI result transport
   and polling, the run-control plumbing of the server panel, log4j, file-format error reporting and
   reader hardening (security), static reader state — are fixed in both modes without a switch. These
   pieces have no legacy-equivalent API that a STRICT mode could reproduce, none of them takes part in
   scoring or the search, and none affects a golden record. They are exactly the entries of the
   "Replaced mechanics" table of DEVIATIONS.md (DV-20 to DV-31, DV-43 to DV-45, DV-51, DV-52, DV-61).
   **Exception (decided) for practically endless runs:** DV-64 changes, in DEFAULT only, runs whose
   start-structure generator has failed 1,000,000 times in a row (about 12 s). Legacy can in principle
   still succeed there, so this is not a provable hang; it is treated as a practical one: the limit is
   more than 40,000 times what any golden input needs (at most 23 restarts), and the measured counts
   grow steeply with the number of monovalent atoms (C4F10 ~8·10^3, C6F14 ~1·10^5, C8F18 none in 10^6).
   Runs that find their start structure within the limit are unchanged (DEVIATIONS.md DV-64).
3. Every deviation is listed in `docs/port/DEVIATIONS.md`.
4. RMI, GateKeeper, scimark dropped; runs in-process, each with its own `java.util.Random`.
5. GUI-only logic that the core needs (autoconfigure, assigners, the golden GUI recipe) lives in the core
   as plain Java; the later GUI only calls it.

## 2. Package layout

Base package `io.github.steinbeck.seneca` (legacy packages are `seneca`, `compchem`, `jmdraw`; no clash).

| Package | Content | Owner |
|---|---|---|
| (root) | `LegacyCompat` | architect |
| `.chem` | legacy-exact compchem re-implementations (`HeavyAtom`, `AtomSets`, `PathUtils`, `FloydApsp`, `IsomorphismTester`, `RingFinder`/`Ring`, `HoseCodeGenerator`, `BremserPredictor`, `MolecularFormula`, `StandardElements`, `LegacyMdlWriter`/`Reader`, `LucyStructureReader`, `LifoQueue`) and the CDK boundary (`CdkBridge`) | A |
| `.judges` | `Judge` and the 7 judges, `JudgeResult`, `ScoreSummary`, `TwoDRule`, `JudgeListener`, `Attic`, `JudgeSet` (= `getScore`/`getMaxScore`/`initJudges`) | A |
| `.model` | `SenecaDataset`, `NMRSpectrum`, `NMRSignal`, `AnnealingSchedule`, `SpectrumSlot` | B |
| `.io` | SpecML read/write, CML write, LUCY dataset import, WinNMR peak-picking paste | B |
| `.config` | `SpectrumAssigner` (base of the assigners), `CarbonShiftAssigner`, `HMBCAssigner`, `HHCOSYAssigner`, `JudgeConfiguration` (configurator logic), `ScheduleConfiguration` (schedule editor logic), `AtomProperties` (atom table: H column, balance message), `GuiRecipe`, `Profile` | B |
| `.structgen` | `StochasticGenerator`, `SingleRandomStructureGenerator`, `AnnealingLog`, `StructureGeneratorResult`, run API (`RunInput`, `RunOptions`, `RunRandom`, `RunResult`, `RunStatus`, `RunController`, `RunSlot`, `RunListener`, `StepObserver`, `StepContext`, `EngineLog`, `FileEngineLog`), `StartStructureFeasibility` (DV-60) | C |
| `.golden` | golden-format emitters shared by CLI and tests: `Json`, `Hashes` (architect), `EchoRecords` (B), `ScoreRecords` (A), `TrajectoryRecords`, `RunRecords`, `RunSupport`, `ReferenceCheck`, `ReferenceRecords` (C) | mixed |
| `.cli` | `Main` | C |
| `.app` | Swing GUI (only package allowed to use `java.awt`/`javax.swing`); design and work split: docs/port/gui-design.md | GUI |

Allowed dependencies (acyclic): `chem` ← `judges` ← `model` ← {`io`, `config`} ; `structgen` → `model`,
`judges`, `chem` ; `golden` → all of the above ; `cli` → all ; `app` → all.
`PortHygieneTest` enforces: no legacy imports (`compchem`, `seneca`, `jmdraw`, log4j, `jnt`), no
`Math.random()` and no other random source (`java.util.Random`, `ThreadLocalRandom`, ...) outside
`RunRandom` and the golden reference recorder's fixed-seed labelling search, no `java.rmi`, no AWT/Swing
(imported or fully qualified) in main code outside `.app`. Public constant tables are immutable
(`List.of`, records).

Legacy class names are kept where they appear in golden records or user-visible text (judge classes:
golden echo records contain `getClass().getSimpleName()`, e.g. `"HMBCJudge"`; `SenecaDataset`,
`NMRSpectrum`, `StochasticGenerator`, …).

## 3. Molecule representation (decision)

**Decision: a small legacy-style model – `chem.HeavyAtom[]` in dataset order plus, in the generator, a
symmetric `int[n][n]` bond matrix – with conversion to CDK `IAtomContainer` only at the boundary
(`chem.CdkBridge`: depiction, SMILES, CLI reference check, the DEFAULT ring fallback, oracle tests).**

`HeavyAtom` is a field-for-field port of `compchem.Atom` as SENECA uses it: `symbol`, `number`
(= array index), `hCount` (implicit H from the dataset, -1 = undefined), `nodeTable[10]` (neighbour
references) and `bondTable[10]`, `degree`, `maxBondOrder`, `maxBondOrderSum`, `bondOrderSum`, coordinates,
`shift13C` (the "13C chemical shift" physical property), identity equality, `copy()` = `Atom.clone()`.

Why not CDK `IAtomContainer` with atoms in dataset order:

1. **Neighbour order is part of the semantics.** `findSSSR` (incl. `restoreBond` appending and thereby
   permuting adjacency lists, BFS `findSRing`, `checkEdges` picking edges by index of the normalised
   ring), HOSE tie handling and Morgan numbers are defined over compchem's per-atom arrays as rebuilt by
   `synchronizeBonds` (ascending index). CDK's neighbour order follows bond insertion order and its
   containers re-index on removal; reproducing the legacy permutations on top of CDK would mean
   re-implementing them anyway, with an extra layer that can silently reorder.
2. **Failure modes are data-structure facts.** The ring-finder crash that ended 13 of 30 golden `gui`
   runs is `nodeTable[-1]` on an array of length 10 (`ArrayIndexOutOfBoundsException: Index -1 out of
   bounds for length 10`). With the same structure, STRICT reproduction falls out of a verbatim port;
   on CDK it would have to be simulated.
3. **Verbatim transcription.** Every compchem method can be transcribed statement by statement from
   `legacy/decompiled/compchem` and checked against `compchem.jar` on identical inputs (oracle
   profile). Already verified for `AtomSets.synchronizeBonds`, `computeMorganMatrix`,
   `PathUtils.isConnected`, `getPathLength` (1,100 differential tests, 0 mismatches).
4. **Valence and H.** SENECA's valence model is `StandardElements` (12 entries, `X` wildcard, no
   hypervalence); CDK atom typing/valence models differ and must not leak into scoring.
5. **Speed and simplicity.** The generator rebuilds adjacency for every candidate (~12,000 per second
   in the legacy program); flat arrays avoid per-step object churn and CDK's per-call canonical
   labelling (CDK HOSE).

The bond matrix is the generator state exactly as in legacy (`bondMatrix`, `mutate(int[][])`);
`AtomSets.synchronizeBonds(attic.atomSet, bm)` produces the atom view the judges read.

## 4. Legacy-exact details that cut across packages

- **Random numbers:** `structgen.RunRandom` (one per run, seeded or not, counts draws). The 8 legacy
  `Math.random()` sites become `random.nextDouble()` in the same order (architecture.md 4.8). Nothing
  else may draw from it; observers never draw. Golden draw counters equal `RunRandom.draws()`.
- **Arithmetic:** `long` scores and `deltaE`, `long changes` with `(long)(changes / 500)`,
  `start_kT = -changes / Math.log(p0)` (gives -0.0 for `changes == 0`), `exp = (double) deltaE /
  current_kT`, `Math.exp`, `Math.log` (not `StrictMath`, as legacy; the golden master was recorded on
  JDK 25/Apple silicon with `Math`), int Morgan numbers (wrap), int `faculty`, reheat factor `+= 0.1`
  in double, `(long)(x * 1.5)`. Float shifts stay `float` (`NMRSignal`, `HeavyAtom.shift13C`); the
  HOSECodeJudge works on `(double) floatShift`.
- **Text that is golden data:** judge descriptions, `ScoreSummary.description` (each judge's text + `"\n"`),
  the legacy MDL text (`LegacyMdlWriter`, titles `"Score: " + score`), `Double.toString`/`Float.toString`
  formatting (identical on the same JDK).
- **Copies:** legacy hands the generator Java-serialization copies (`serClone`). The port uses
  `Judge.copy()`, `AtomSets.cloneAtomSet`, `AnnealingSchedule.copy()` with the same semantics
  (listeners not copied, all configuration deep). `RunInput.fromDataset` = `goAction` (calcMaxScore on
  the dataset's enabled judges first, quirk Q6).

## 5. LegacyCompat (switch points)

`LegacyCompat.STRICT` reproduces the legacy failure, `DEFAULT` fixes it. Golden tests run STRICT;
`DefaultModeGoldenTest` checks DEFAULT against golden (identical where legacy does not fail, identical
up to the failure point otherwise). The mode travels in `RunOptions.compat` and `Attic.compat`.

| Switch point | STRICT | DEFAULT | Where |
|---|---|---|---|
| Ring finder degree-2 `breakBond` on degree-0 atom (D14, mode 1) | `ArrayIndexOutOfBoundsException` ends the run (`exception_in_run`) | FeatureJudge scores the ring sizes of CDK `Cycles.sssr` for that structure | `chem.RingFinder`, `judges.FeatureJudge`, `chem.CdkBridge` |
| Ring finder degree-3 empty candidates (mode 2) | same exception class/message as legacy | same fallback | same |
| Ring finder hang (all remaining degrees ≥ 4, mode 3) | detected, `RingFinder.HangDetected` ends the run like an exception (legacy hung) | same fallback | same |
| HOSE code generator NPE for a neighbour without a 2001 major isotope (wildcard `X`, DV-50) | NPE ends the run | the carbon is treated like an unknown code | `judges.HOSECodeJudge` |
| Q1: first candidate of a schedule disconnected (DV-04) | schedule ends immediately | the same, except on the last schedule while no result is stored (where the run would end without a result): keep sampling until the first connected candidate | `structgen.StochasticGenerator` |
| Empty schedule list (D10) | run ends with `exception_in_run` | rejected before start (`IllegalArgumentException` from the `StochasticGenerator` constructor) | `structgen.StochasticGenerator` |
| Judge enabled but never configured: 2D judge with `assignment == null` (DV-06), HOSECodeJudge without Autoconfigure, Hybridization/HeteroBonds table never allocated (DV-63) | NPE in `calcMaxScore` ("Start" fails) | max 0 instead of `calcMaxScore`; scores 0 as an empty configuration (2D: no rules) | `structgen.RunInput.fromDataset`, `judges.TwoDSpectrumJudge.init` |
| Inputs on which the engine never ends: < 4 atoms, atoms from which the start generator can never build a saturated connected structure (DV-60) | legacy; a stop request between start-structure restarts is honoured only for inputs proven impossible | rejected by the `StochasticGenerator` constructor (`IllegalArgumentException`); stop honoured between restarts only if the feasibility search was undecided | `structgen.StochasticGenerator.validate`, `StartStructureFeasibility`, `SingleRandomStructureGenerator` |
| Start generator practically never succeeds although it could (many monovalent atoms: C8F18 none in 1,000,000 restarts), stop ignored there (DV-64) | legacy (restarts; stop ignored for possible inputs) | stop honoured between restarts from 10,000 restarts on; after 1,000,000 restarts the run starts from a constructed structure (`StartStructureFeasibility.witness`) | `structgen.StochasticGenerator.startStructure`, `SingleRandomStructureGenerator.fromBondMatrix` |
| No Faulon move leads from the start structure to another connected structure (only one structure possible: isobutane, CCl4, neopentane, tert-butanol; also < 4 atoms): `initAnnealing` draws for ever, unstoppably (DV-65) | detected before the first calibration sample, `IllegalStateException` ends the run (`exception_in_run`; legacy hung) | the start structure is scored and stored as the only result; the run ends without annealing | `structgen.StochasticGenerator.hasConnectedNeighbour`, `initAnnealing`, `run` |
| Judge configuration left over from another atom count (formula changed after configuring; DV-66): a 2D rule or a symmetry pair with an atom beyond the molecule | every evaluation throws, the run ends without a result | rejected before start (`IllegalArgumentException` naming the judge) | `structgen.StochasticGenerator.validate` |
| Dataset without atoms at "Start" (DV-66) | `NullPointerException` from `RunInput.fromDataset` (legacy: nothing starts) | `IllegalArgumentException` with a message | `structgen.RunInput.fromDataset` |
| Hybridization/HeteroBonds table edit on a table that was never allocated or is shorter than the row (DV-66) | NPE/AIOOBE, edit lost | the table is enlarged first | `config.JudgeConfiguration.setHybridizationCell/setHeteroBondsCell` |
| Schedule editor "Remove"/"Update" (DV-67) | legacy selection shuffle (fields can show a removed schedule; "Update" then copies it into the next one), field-by-field update, AIOOBE on an empty list | the schedule that took the removed one's place is selected and shown; update all-or-nothing; no-op without a selection | `config.ScheduleConfiguration` |
| Stop requested before `run()` starts (DV-62) | lost (legacy clears the flag on entry) | honoured (as a stop right after the start) | `structgen.StochasticGenerator.run` |
| `removeIsomorphism` (D5): drops 2 lowest, throws on 1–2, hangs on 0 | legacy (hang detected → `IllegalStateException`) | no truncation, no failure | `structgen.StructureGeneratorResult` |
| "Summarize Result" with a slot that never ran (DV-47) | collection stops at that slot (legacy NPE inside the one `try`) | the slot is skipped | `structgen.RunController.summarize` |
| SpecML writer `<molecule>` that the reader ignores (D8, data loss) | legacy text | `<cml:molecule>` block the reader reads, escaping, description/mass (DV-08) | `io.SpecMLWriter` |
| WinNMR paste: 2D shift array shared; paste into a dim-0 spectrum throws (DV-40, DV-41) | legacy | own arrays; all signals appended | `io.WinNmrPeakPicking` |
| Autoassignment hang (zero intensity), NPE (no positive intensity), walk past signals/atoms (DV-42) | NPE/AIOOBE as legacy; hang detected -> `IllegalStateException` | rejected before any change (`false`); walk stops | `config.CarbonShiftAssigner` |
| HMBC Autoconfigure: long-range proton without 1JCH partner, empty "to" list (DV-46) | NPE/AIOOBE, partial assignment, judge stays enabled | `false`, nothing changed, judge unticked | `config.HMBCAssigner`, `config.JudgeConfiguration.autoconfigure` |
| H,H-COSY Autoconfigure failing on a signal (DV-46) | `false`, judge unticked, correlations set before the failure stay (re-ticking runs with a partial matrix) | `false`, nothing changed, judge unticked | `config.HHCOSYAssigner` |
| LUCY import with K > 0 known bonds (data loss) | lines not consumed (misparse) | consumed | `io.LucyDatasetImporter` |

Not switched (fixed in both modes under the exception in section 1 point 2, no observable effect on runs; DEVIATIONS.md): deep-copied results
(D6), undrained annealing log (D7), status poll without side effects, run control stops orphaned runs
(D15), no static state in the SML reader (Q7), `Thread.yield`/priority dropped, log4j replaced.
Every core API that has a switch point takes the mode explicitly (no overload silently defaults to
STRICT; the one exception is `GuiRecipe.prepare(file, key, profile)`, which is the golden recipe); the golden recipe and recorders
pass `STRICT`, the app and `seneca generate` pass `DEFAULT` (`GuiRecipe.prepare(..., compat)`). `Attic.compat`
defaults to STRICT but is always set by the generator and the golden scorers.
Not switched and **kept** in both modes (score-determining): D1, D2, D3, D4, D12, D16, Q3, Q4, Q5.

## 6. Data model and I/O (B)

`model.SenecaDataset` mirrors the legacy fields: name, description ("none"), formula, mass (float),
seven spectra (`SpectrumSlot`: carbon1D, dept90, dept135, hetcor, hetcorlr, hhcosy, noesy), schedules,
`setOfNodes` (`HeavyAtom[]`), judges (`init()` appends the 7 judges in the fixed order HHCOSY, HMBC,
HeteroBonds, Symmetry, Hybridization, HOSECode, Feature). Swing `ChangeListener`s become `Runnable`
listeners. `io.SpecMLReader` must reproduce golden echo stage `as_read` (including: only `<cml:molecule>`
is read; empty spectra become dim 0). The GUI recipe (`config.GuiRecipe`) must reproduce the echo stages
`configured_*` and the `recipe` records' step strings verbatim.

## 7. Run API for the GUI (C)

No Swing in `structgen`. The GUI (later) uses:

```java
RunController rc = new RunController();               // one per application or per dataset window
RunSlot slot = rc.addSlot();                          // "Start local server" -> id "local0"
RunInput in = RunInput.fromDataset(dataset, LegacyCompat.DEFAULT);   // goAction: calcMaxScore + deep copies
rc.start(slot, in, RunOptions.application().withLog(logbook));       // own thread; validates, then stops an active run
rc.start(other, in, RunOptions.application());                        // the same input again: each run copies it
rc.addListener(listener);                              // onStatus / onLog / onNewBest / onFinished (run thread!)
RunStatus s = slot.status();                           // or poll every 2 s like the legacy timer
List<AnnealingLog.Entry> e = slot.annealingLogSince(lastIndex);      // chart data, nothing drained
StructureGeneratorResult r = slot.result();            // deep copy
rc.stop(slot); rc.reset(slot); rc.clear();
StructureGeneratorResult merged = rc.summarize(LegacyCompat.DEFAULT); // "Summarize Result"
```

- `StochasticGenerator` is not a `Thread`; `run()` is synchronous and returns a `RunResult` (never
  throws for engine failures; `exception()` holds what legacy's outer `catch` swallowed).
- `RunResult.goldenStatus()/goldenTermination()` derive the golden `status`/`termination` exactly like
  the harness (`exception_in_run` > `iteration_cap` > `no_result(Q1)` > `finished`;
  `max_score_reached` iff `bestScore >= maxScore`).
- `RunStatus` = legacy `StructureGeneratorStatus` without its bugs: never throws (Q1), does not write
  `maxScore` into the live best summary, does not drain the annealing log; `state` gives the legacy
  status strings ("Idle", "Running", "Holding Results", "Stopped by User").
- `StepObserver` (golden trajectories, diagnostics): `onStartStructure`, `onInitEvaluation`,
  `onScheduleInitialized`, `beforeAcceptance` (candidate scored, Metropolis draw not yet made),
  `onNewBest` (a new best was stored), `afterStep`, `onFinished`. The only allowed influence is `StepContext.requestStop()` (= Stop button;
  this is how the 5000-step trajectories end, like the harness's `TraceProbe`).
- `RunOptions.iterationCap`: the harness's deterministic cap (the first `mutate()` after
  `iteration >= cap` calls `end()`).
- `EngineLog`: the INFO texts of the legacy log (Logbook, `seneca.log`) and the result SD blocks
  (`seneca.results.sdf`). File/GUI sinks are supplied by the caller.

## 8. Threading

- One platform thread per run (`"seneca-" + slotId`, daemon), started by `RunController`. Several runs
  execute concurrently and independently: own `RunRandom`, own judge copies, own `Attic`; **no static
  mutable state anywhere in the engine path** (legacy `synchronizeBonds` was `static synchronized`; the
  port needs no lock because nothing is shared).
- Generator state is confined to its thread. Published to other threads: a `volatile` stop flag;
  a `volatile` iteration counter; the best structure, best summary copy and result list, updated under
  the generator's lock only when a new best is stored (rare) and copied under the same lock by
  `resultCopy()`/`status()`; the `AnnealingLog` (synchronized append-only list).
- Listener callbacks run on the generator thread and must not block; the GUI marshals to the EDT.
- `reset`/`clear`/restart: request stop, then join with a timeout (`RunController.JOIN_MILLIS`). In
  DEFAULT every phase of a run reacts to a stop in bounded time: a main-loop step is short, a ring-finder
  hang is detected (DV-03), a start structure without a connected move ends the run at once (DV-65),
  `initAnnealing` collects its 500 samples with probability 1 once a connected move exists, and the
  start generator honours a stop from 10,000 restarts on (DV-64). In STRICT the legacy start generator
  ignores a stop for inputs it can build (it may restart for a very long time, e.g. C8F18); the join
  then times out and the thread keeps running until it finds a start structure.
- `start` constructs (and so validates) the new generator before it stops the slot's running run, so a
  rejected input leaves the running run alone. The generator deep-copies its `RunInput`.
- Golden tests run generators synchronously on the test thread. Tests may run concurrently (no shared
  state); C may enable JUnit parallel execution for the slow run tests.

## 9. Golden test infrastructure (src/test/java/io/github/steinbeck/seneca/golden)

| Class | Purpose |
|---|---|
| `GoldenFiles` | locations (surefire system properties, or walk up to `golden/FORMAT.md`), key and run-id listing, gzip-aware line reading |
| `JsonValue` | parser that keeps key order and **number lexemes** (signed zero, float vs double printing); `toJson()` reproduces every golden line byte for byte |
| `JsonDiff` | exact comparison of a port record with a golden record (key order, number lexemes, strings), first difference with JSON path, ignore paths (only `$.crash.at`, see below) |
| `EchoGolden`, `ScoresGolden`, `TrajectoryGolden`, `RunGolden`, `ReferenceGolden` | readers for each golden file type |
| `GoldenFixtures` | builds `HeavyAtom[]`, judges (configured through the port's judge API, `calcMaxScore` checked) and schedules **from the echo records**, so A's and C's tests do not depend on B; `Scorer` = harness's independent scorer |
| `Port` | calls into the port (checked exceptions become `AssertionError`); during the implementation phase it also turned "not implemented yet" stubs into skipped tests - the stubs are gone |
| `GoldenReadersTest` | self-test (passes now): layout, round trip of every line, every `hash` = SHA-1 of its `ct`, signed zero, float fields |

Test classes (tags: work package `A`/`B`/`C`, and `slow`):

| Test | Checks | Package |
|---|---|---|
| `DatasetEchoGoldenTest` | whole echo files via `golden.EchoRecords`; enabled judges + generator max per profile via `GuiRecipe` + `RunInput` | B |
| `ScoresGoldenTest.judgeScores` | 550 structures × 3 profiles, per-judge breakdown exact | A |
| `ScoresGoldenTest.structureConstruction` | seeded start structures, random walk, `startDraws` | C |
| `ScoresGoldenTest.endToEnd` | whole scores files via `ScoreRecords.scores(sml, key)` | A+B+C |
| `ReferencesGoldenTest.bestLabellingScores` | literature structures' best labellings scored exactly | A |
| `ReferencesGoldenTest.mappability` | `golden.ReferenceCheck` | C |
| `TrajectoryGoldenTest` | 88 trajectories, every record bit-exact (STRICT) | C (needs A) |
| `RunsGoldenTest.fastRuns` / `slowRuns` (`slow`) | 39 / 33 complete runs, whole run record (STRICT) | C (needs A) |
| `DefaultModeGoldenTest` | DEFAULT = golden where legacy does not fail; = golden up to the failure point and continuing past it otherwise | C (needs A) |
| `PortHygieneTest` | no legacy imports, no `Math.random()`, no Swing in main | all |
| `oracle/CompchemOracleTest` (`-Poracle`) | port vs original `compchem.jar` on the golden structures and the 13 crash candidates | A |
| `oracle/RandomIsomerOracleTest`, `JudgeOracleTest`, `MdlOracleTest`, `TablesOracleTest` (`-Poracle`) | HOSE codes, Bremser, ring finder, Morgan, paths, MDL I/O, isomorphism heuristic and all 7 judges against compchem.jar / the 2001 judge classes on random isomer walks and every golden result structure | A |
| `oracle/DatasetIoOracleTest` (`-Poracle`) | SpecML reader/writer, CML writer, assigners, LUCY import, WinNMR paste against the 2001 classes | B |
| `verify/*OracleTest` (`-Poracle`, tag `verify`) | whole seeded runs of the legacy engine vs the port on inputs the golden data does not cover (multi-schedule, more elements, degenerate schedules, stop before run), Summarize with idle slots, SpecML edge cases | verification |
| `verify/RecipePathGoldenVerifyTest`, `verify/PolicyVerifyTest` | golden trajectories/runs/references through the full SML → recipe → engine path; policy checks (DEFAULT accepts every golden input, app path reproduces a golden run, fixed policy gaps) | verification |
| `structgen/StartStructureFeasibilityTest` | the start-structure feasibility decision (DV-60) against itself and the real generator on random molecules | C |

Commands (use a private build directory when several builds share the working tree):

```
export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home
mvn -q -Dbuild.dir=$PWD/target-<name> test                 # default: everything except the 33 slow runs and the oracle
mvn -q -Dbuild.dir=$PWD/target-<name> test -Dgroups=A      # one work package
mvn -q -Dbuild.dir=$PWD/target-<name> -Pslow test          # including the 33 long complete runs
mvn -q -Dbuild.dir=$PWD/target-<name> -Poracle test        # + differential tests vs compchem.jar (needs legacy/runnable/build.sh once)
```

End state (reached): 0 skipped, 0 failed; the `NotImplementedYet` stubs and their skip mechanism are removed.

`crash.at` (the top stack frame of a crash record) is the one golden field the tests do not compare:
legacy records name the compchem frame (`compchem.SetOfRingsFinder.breakBond:302`), the port names its
own as `class.method` without a line number (`io.github.steinbeck.seneca.chem.RingFinder.breakBond`, stable
across edits of the source). This is accepted (DEVIATIONS.md DV-30);
the port does not forge the legacy frame string. Everything else in the 14 crash records (13 `gui` runs
and the triterpen.gui.seed3 trajectory) is identical, including the exception class and message.

## 10. CLI (`io.github.steinbeck.seneca.cli.Main`, C)

Replays the golden GUI recipe for a dataset + profile and emits the harness's JSON shapes through the
same `golden.*` emitters the tests use, so a complete port recording can be compared with
`legacy/harness/compare.sh golden <dir>` (byte comparison; `timing.jsonl`, `PROVENANCE.json`,
`MANIFEST.jsonl` excepted, the latter being produced by `summarize.py` from the records).

```
seneca echo   <file.sml> <key> [-o out]                                   golden (a)
seneca scores <file.sml> <key> [-o out]                                   golden (b)
seneca traj   <file.sml> <key> <profile> <seed> [--steps 5000] [--compat strict|default] [-o out]   (c)
seneca run    <file.sml> <key> <profile> <seed> [--cap 5000000] [--references tsv] [--compat ...] [-o out]  (d)
seneca ref    <file.sml> <key> [--references tsv] [-o out]                golden (e), labelling search as the harness
seneca record <smlDir> <outDir> [--stages echo,scores,ref,traj,run] [--datasets k1,k2] [--jobs n]
              [--references tsv] [--cap n] [--steps n] [--compat ...] [--manifest summarize.py]   whole golden layout
seneca generate <file.sml> [--key k] [--profile gui] [--seed s] [--compat default] [--cap n] [--verbose] [-o results.sdf]
```

`record` uses the job list of `legacy/harness/run.sh` and runs the jobs on parallel threads in one JVM.
Verified: a complete `record` is byte-identical to golden/ except `runs/timing.jsonl`, `PROVENANCE.json`
and `crash.at` (DEVIATIONS.md, last section). `scripts/compare-golden.sh [golden] <dir>` is the one-command
check: it compares like `compare.sh`, skips `PROVENANCE.json`, compares differing files again with
`crash.at` removed, and exits 0 iff everything else is identical.

`key` is a golden dataset key (`polycarpol@hblock` reads `polycarpol.sml`); golden commands default to
STRICT. Exit codes 0 ok, 1 a `record` job failed, 2 usage, 4 I/O. Packaging: `mvn package` builds a jar with
`Main` as main class (dependencies on the classpath). The macOS application `dist/Seneca.app` (jlink runtime,
jpackage app image, main class `app.SenecaApp`) is built by `scripts/build-app.sh`; docs/port/gui-design.md
section 15.

## 11. Work split

Shared files (`pom.xml`, `LegacyCompat`, `golden/Json`, `golden/Hashes`, all of
`src/test/java/.../golden` infrastructure, `PortHygieneTest`, this document): owned by the architect;
make only minimal changes and report them. Each package edits only its own files. Commits were made
centrally, after review. Implementations replaced the skeleton's `NotImplementedYet` stubs (class removed
once all were implemented); signatures may be extended, not silently changed – report signature changes. Javadoc of every skeleton names the legacy
source to port.

### A – chem + judges (critical path: B and C need parts of it early)

Order:
1. `chem.MolecularFormula` (`heavyAtoms`, `atomCount`, `formulaOf`; `mass` with the 2001 isotope table)
   – B's SML reader needs it first.
2. All judges' `init()`, `calcMaxScore()`, `copy()` (+ `SymmetryJudge.getClassMember`,
   `FeatureJudge.getRingSizeScore`) – `GoldenFixtures` and B's echo need them.
3. `evaluate()` of all judges with `chem.HoseCodeGenerator`, `chem.BremserPredictor` (651 puts, later put
   wins), `chem.RingFinder`/`Ring` (verbatim incl. failure modes 1–3), `IsomorphismTester`.
4. `chem.LegacyMdlWriter` (byte-exact), `chem.LegacyMdlReader` – C's duplicate check needs both.
5. `chem.CdkBridge` (`toAtomContainer`, `sssrRingSizes`) and the DEFAULT fallback in `FeatureJudge`.
6. `chem.LucyStructureReader`; `golden.ScoreRecords.scores` (after C's structure construction).

Also: review/own the architect-written `HeavyAtom`, `StandardElements`, `AtomSets`, `PathUtils`,
`FloydApsp`, `LifoQueue`, `JudgeSet`, `ScoreRecords.breakdown`; extend `src/test-oracle`
(`CompchemOracleTest`: HOSE codes, Bremser vs both compchem and CDK, ring finder incl. the 13 crash
candidates with identical exception class and message, IsomorphismTester, MDL writer and reader,
MFAnalyser); unit tests for the DEFAULT fallback and the hang detection.
Done when: `ScoresGoldenTest.judgeScores` (33), `ReferencesGoldenTest.bestLabellingScores` (24) pass and
`-Poracle` has 0 failures/0 skips; DEVIATIONS entries for the ring finder finalised.

### B – dataset, SML/CML/LUCY I/O, config, assigners, GUI recipe

1. `model.NMRSpectrum.pickClosestSignal/pickSignals`, `model.SenecaDataset.setSetOfNodes` (+ the
   dataset's own `analyseSetOfNodes` incl. negative H counts).
2. `io.SpecMLReader` (echo stage `as_read` exact), `io.SpecMLWriter` (STRICT byte-identical to
   `SpecMLGenerator`; DEFAULT per D8 decision), `io.CMLWriter`, `io.LucyDatasetImporter` (no `.col`
   sample exists in the repository: write synthetic tests from feature-inventory 7.3),
   `io.WinNmrPeakPicking`.
3. `config.CarbonShiftAssigner`, `HMBCAssigner`, `HHCOSYAssigner` (all legacy defects kept),
   `config.JudgeConfiguration` (configurator constructor side effects, autoconfigure of all 7 judges,
   Hybridization/HeteroBonds table edits, report), `config.GuiRecipe.prepare` (incl. `@hblock`, step
   strings verbatim from `legacy/harness/src/seneca/client/GuiRecipe.java`).
4. `golden.EchoRecords.echo`.
Later (GUI phase, may start if time permits, in `config`): Atom Properties table semantics (H 0–4,
balance message, negative H → 0), schedule list editing with the D-fix, "New Dataset" with `init()` and a
default schedule (D10), preferences.
Done when: `DatasetEchoGoldenTest` (11 + 44) passes; SML round-trip and LUCY/WinNMR unit tests;
DEVIATIONS entries for D8, LUCY, Q7 finalised.

### C – structgen + CLI

1. `SingleRandomStructureGenerator.generate`, `StochasticGenerator.mutate` (static and instance) –
   `ScoresGoldenTest.structureConstruction` needs nothing from A.
2. `StochasticGenerator.run` (architecture.md 4.1–4.10, all switch points of section 5), `StepContext`
   implementation, `status`, `resultCopy`, `annealingLogSince`, `end`; `StructureGeneratorResult`
   `sort`/`removeIsomorphism`/`merge`.
3. `golden.TrajectoryRecords` (mirror `TraceProbe` exactly), `golden.RunRecords`, `golden.ReferenceCheck`.
4. `RunController`, `RunSlot` (threads, listeners, summarize), an `EngineLog` file implementation.
5. `cli.Main`: `record` (whole golden layout, parallel jobs) and `generate`.
Done when: `ScoresGoldenTest.structureConstruction`, `TrajectoryGoldenTest` (88), `RunsGoldenTest`
(39 + 33 slow), `DefaultModeGoldenTest`, `ReferencesGoldenTest.mappability` pass; with B done,
`seneca record` + `compare.sh` against golden/ is byte-identical for datasets/, scores/, trajectories/
(after gzip -n), runs/ (except `crash.at`); RunController unit tests (concurrent runs reproducible with
seeds, stop/reset/clear).

## 12. Mapping: legacy class → port

### 12.1 `seneca.core`, `seneca.judges`, `seneca.structgen`

| Legacy | Port | Notes |
|---|---|---|
| `core.SenecaDataset` | `model.SenecaDataset` | `serverList`, `sessions` dropped |
| `core.NMRSpectrum` | `model.NMRSpectrum` | not a `Vector`; `signals` list; shift lists dropped |
| `core.NMRSignal` | `model.NMRSignal` | |
| `core.CNMRSignal`, `core.Carbon1D` | – | unused, DROP |
| `core.SpecMLReader` | `io.SpecMLReader` | no static state |
| `core.SpecMLGenerator` | `io.SpecMLWriter` | |
| `core.CMLGenerator` | `io.CMLWriter` | |
| `core.SpecMLConstants` | inlined in `io` | |
| `judges.Judge` | `judges.Judge` | + `copy()`, `getJudgeScoreField()` |
| `judges.JudgeResult`, `ScoreSummary`, `JudgeListener`, `TwoDRule` | same names in `judges` | |
| `judges.TwoDSpectrumJudge`, `HMBCJudge`, `HHCOSYJudge` | same names | |
| `judges.HOSECodeJudge`, `SymmetryJudge`, `FeatureJudge`, `HybridizationJudge`, `HeteroBondsJudge` | same names | field hiding kept |
| `judges.WienerIndexJudge` | – | dead code, DROP |
| `structgen.StochasticGenerator` | `structgen.StochasticGenerator` | not a Thread; `getScore`/`getMaxScore`/`initJudges` in `judges.JudgeSet` |
| `structgen.SingleRandomStructureGenerator` (2001 jar only) | `structgen.SingleRandomStructureGenerator` | |
| `structgen.StructureGenerator` (abstract) | folded into `SingleRandomStructureGenerator` | `setAtomSet` clone, `structureConnected` |
| `structgen.AnnealingSchedule` | `model.AnnealingSchedule` | |
| `structgen.AnnealingScheduleInterface` | – | unused, DROP |
| `structgen.AnnealingLog` | `structgen.AnnealingLog` | single series, record entries |
| `structgen.Attic` | `judges.Attic` | + `compat`; `setOfAllRings` dropped |
| `structgen.StructureGeneratorResult` | `structgen.StructureGeneratorResult` | |
| `structgen.StructureGeneratorStatus` | `structgen.RunStatus` | |
| `structgen.Rng` (runnable patch) | `structgen.RunRandom` | per run |
| `engine.StructureGeneratorServer`, `compute.Compute`, `compute.Task` | `structgen.RunController`, `RunSlot` | RMI DROP |
| `engine.benchmarker`, `gatekeeper.*`, `jnt.scimark2.*` | – | DROP |

### 12.2 compchem / jmdraw

| Legacy | Port | Verdict (compchem-semantics.md) |
|---|---|---|
| `Atom` | `chem.HeavyAtom` | own model (section 3) |
| `Molecule` | `HeavyAtom[]` | thin wrapper, dropped |
| `StandardElements`, `Element` | `chem.StandardElements` | own table |
| `DataStructureTools` (`cloneAtomSet`, `createConnectionTable`, `synchronizeConnectionTable`, `cloneInt2D`, `synchronizeBonds`, `computeMorganMatrix`, `faculty`) | `chem.AtomSets` | verbatim (done, oracle-verified in part) |
| `DataStructureTools.getMass`, `MFAnalyser`, `StandardIsotopes`, `Isotope` | `chem.MolecularFormula` | own (CDK differs) |
| `DataStructureTools.removeIsomorphism` | `structgen.StructureGeneratorResult.removeIsomorphism` / GUI | |
| `PathUtils` | `chem.PathUtils` | verbatim (done) |
| `ShortestPathFinder` | `chem.FloydApsp` | verbatim (done) |
| `IsomorphismTester` | `chem.IsomorphismTester` | own (CDK differs) |
| `SetOfRingsFinder`, `setOfRings`, `Ring`, `Path`, `Bond`, `Edge`, `Queue` | `chem.RingFinder`, `chem.Ring` | verbatim incl. failures (CDK differs) |
| `HOSECodeGenerator` | `chem.HoseCodeGenerator` | own; CDK identical → oracle only |
| `BremserOneSphereHOSECodePredictor` | `chem.BremserPredictor` | table verbatim; CDK oracle |
| `ChemFileUtils.writeMDLMol` | `chem.LegacyMdlWriter` | byte-exact |
| `ChemFileUtils.readMDLMolFile` | `chem.LegacyMdlReader` | |
| `ChemFileUtils.readLUCYStructure` | `chem.LucyStructureReader` | |
| `LIFOQueue` | `chem.LifoQueue` | done |
| `SMILESParser`, `RingSet`, `Sorter`, `DPoint`, `exceptions.*` | – | unused by SENECA, DROP |
| `jmdraw.JMDraw`, `RenderingEngine`, `RenderingAttributes` | CDK `StructureDiagramGenerator` + `DepictionGenerator` via `chem.CdkBridge` | GUI phase |

### 12.3 `seneca.client` (GUI; only the non-Swing logic moves now)

| Legacy | Port |
|---|---|
| `CarbonShiftAssigner`, `HMBCAssigner`, `HHCOSYAssigner`, `SpectrumAssigner` | `config.*` (same names) |
| `JudgeConfigurator`, `TwoDSpectrumJudgeConfigurator`, `HMBCJudgeConfigurator`, `HHCOSYJudgeConfigurator`, `HOSECodeJudgeConfigurator`, `SymmetryJudgeConfigurator`, `FeatureJudgeConfigurator`, `HybridizationJudgeConfigurator`, `HeteroBondsJudgeConfigurator` | logic: `config.JudgeConfiguration`; panels: `app` (GUI phase) |
| `LUCYFileHandler` | `io.LucyDatasetImporter` |
| `NMRSpectrumTablePanel` (WinNMR paste) | `io.WinNmrPeakPicking`; table: `app` |
| `SGServerPanel` | run control: `structgen.RunController`; table/chart/evaluation view: `app` |
| `StrucDispFrame`, `JMDrawPanel` | `app` + `chem.LegacyMdlReader/Writer`, `LucyStructureReader`, `CdkBridge` |
| `AnnealingScheduleConfigurator` | logic (selection, field texts, legacy parsing, New/Update/Remove): `config.ScheduleConfiguration`; panel: `app` (GUI phase) |
| `Seneca`, `SenecaDatasetFrame`, `SenecaDatasetCollection`, `AtomPropertyPanel`, `ErrorMessages`, `ExtensionFileFilter`, `Parameters`, `SenecaParameters` | `app` (GUI phase) |
| `ComputeServerConfigurator`, `FastResolver`, `NodeTableFrame`, `ConnTablePanel`, `AnnealingViewPanel`, `StructureGeneratorCollection`, `ResourceManager` | DROP |
| harness `GuiRecipe` | `config.GuiRecipe`, `config.Profile` |
| harness `GoldenMaster`, `Json`, `RefStructures` | `golden.*`, `cli.Main` |

### 12.4 Feature inventory (KEEP / KEEP-ADAPT items)

| F-ID | Item | Core support now | GUI phase |
|---|---|---|---|
| F-01, F-02, F-04, F-05, F-10–F-13 | app launch, open file argument, resources, L&F, main window, toolbar, status bar, dataset windows | `io.SpecMLReader`, `MolecularFormula.formulaOf` (status bar) | `app`, `Seneca.app` |
| F-06, 7.7 | logging: `seneca.log`, `seneca.results.sdf`, `seneca.annealing.log` | `structgen.EngineLog` (+ C's file sink) | sink locations |
| F-20 | New Dataset (fix D10) | `SenecaDataset` + `init()` + default `AnnealingSchedule` | action |
| F-21, F-22, 7.1 | Open / Save SML | `io.SpecMLReader`, `io.SpecMLWriter` | dialogs |
| F-24, 7.3 | Import LUCY | `io.LucyDatasetImporter` | dialog |
| F-23, F-29, F-30, F-33, F-34 | Exit, L&F, Current Structures, Datasets menu, About | – | `app` |
| F-40 | General Settings | `SenecaDataset` setters (formula regenerates atoms), `MolecularFormula.mass` | table |
| F-41, 7.6 | NMR spectra tables, WinNMR paste | `model.NMRSpectrum`, `io.WinNmrPeakPicking` | tables |
| F-42 | Atom Properties, Autoassignment, H balance | `config.CarbonShiftAssigner`; balance helper (B, later) | table |
| F-43 | Annealing schedules | `model.AnnealingSchedule`, `config.ScheduleConfiguration` (Remove fixed in DEFAULT, DV-67) | list, fields |
| F-44, F-50–F-56, F-81 | Judges tab, autoconfigure, activate, tables, report | `config.JudgeConfiguration` | panels |
| F-46, F-60–F-70 | Structure generation: run table, start/stop/reset/clear, polling, summarize, log annealing data, chart | `structgen.RunController/RunSlot/RunStatus/AnnealingLog/StructureGeneratorResult` | table, chart (JFreeChart 1.5) |
| F-47 | Logbook | `structgen.EngineLog`, `RunListener.onLog` | text pane |
| F-48, 7.2 | Close dataset with save (D11) | `io.CMLWriter`, `io.SpecMLWriter` | decision |
| F-85–F-88 | structure drawing, best structure, evaluation HTML, chart | `chem.CdkBridge`, `ScoreSummary` | CDK depiction |
| F-90–F-94, 7.4, 7.5 | Structure Display: Load MDL, Load LUCY result, Save MDL | `chem.LegacyMdlReader`, `LucyStructureReader`, `LegacyMdlWriter` | window |
| 7.8 | preferences | – | `java.util.prefs` |
