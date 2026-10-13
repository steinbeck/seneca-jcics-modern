# How a SENECA structure generation run is driven in-process

Audience: whoever writes the headless harness. Everything here was read from the source
and, where marked **verified**, executed on JDK 25 against `legacy/runnable/classes`.
A complete, compiling and verified example is `examples/DriveSketch.java`.

Line numbers refer to `legacy/original/src` (identical in `legacy/runnable/src` except
where PATCHES.md says otherwise).

## 1. The GUI call chain (what the harness must reproduce)

```
Seneca.openFile(File)                                  client/Seneca.java:602
  SpecMLReader(file).getSenecaDataset()                core/SpecMLReader.java:569 (calls sd.init())
  new SenecaDatasetFrame(sd)                           builds all tabs, incl. 7 judge configurators
User: Atom Properties -> "AutoAssign"                  AtomPropertyPanel: new CarbonShiftAssigner(sd).assign()
User: Judges -> <judge> -> "Autoconfigure Judge"       JudgeConfigurator.AutoAssignAction:
                                                         judge.setEnabled(true); autoconfigure();
User: Structure Generation -> "Start local server"     SGServerPanel.StartLocalAction:
                                                         new seneca.engine.StructureGeneratorServer("local"+n)
User: select row -> "Start as selected"               SGServerPanel.goAction (SGServerPanel.java:700)
  for enabled judges in sd.judges: judge.calcMaxScore()
  sgi.setMolecule(serClone(new Molecule(sd.getSetOfNodes())))
  sgi.setJudges(serClone(sd.judges))                   ALL 7 judges, deep copy by serialization
  sgi.setDatasetName(sd.getName())
  sgi.setSASchedules(serClone(clones of sd.annealingSchedules))
  sgi.executeTask()                                    engine/StructureGeneratorServer.java:139
    stoicGent = new StochasticGenerator(true)
    stoicGent.setMolecule(molecule); setJudges(judges); setDatasetName(name); setSASchedules(s)
    stoicGent.execute()                                = Thread.start() -> run()
Timer (2 s): server.getTaskStatus() -> stoicGent.getStatus()   progress, best structure, annealing log
"Summarize Result": server.getTaskResult() -> stoicGent.getResult()
"Stop Selected": server.endTask() -> stoicGent.end()   sets pleaseStop
```

`StructureGeneratorServer` is a thin RMI wrapper: every `Compute` method delegates 1:1 to
the `StochasticGenerator` (it also calls `PropertyConfigurator.configure` with
`seneca/engine/logging.server.properties` in its constructor). The harness calls
`StochasticGenerator` directly; no RMI is needed.

## 2. Minimal in-process run (verified)

```java
SenecaDataset sd = new SpecMLReader(new File("pinene.sml")).getSenecaDataset();
new seneca.client.CarbonShiftAssigner(sd).assign();         // only if shifts/H counts were not read
// ... configure judges, section 4 ...
for (Object o : sd.judges) { Judge j = (Judge) o; if (j.getEnabled()) j.calcMaxScore(); }
Molecule mol     = (Molecule) serClone(new Molecule(sd.getSetOfNodes()));
Vector judges    = (Vector)   serClone(sd.judges);
Vector schedules = (Vector)   serClone(clonesOf(sd.annealingSchedules));

Rng.seed(seed);                                     // seneca.structgen.Rng, before the generator draws
StochasticGenerator sg = new StochasticGenerator(true);
sg.setMolecule(mol); sg.setJudges(judges); sg.setDatasetName(sd.getName()); sg.setSASchedules(schedules);
sg.run();                                           // synchronous, same code as execute()/start()
StructureGeneratorResult r = (StructureGeneratorResult) sg.getResult();
// r.structures.elementAt(i): MDL molfile String ("Score: <n>" in the header line)
// r.scoreSummaries.elementAt(i): ScoreSummary {score, maxScore, description(HTML)}
```

`serClone` = write to `ObjectOutputStream`, read back (SGServerPanel.goAction.serClone).
Classpath: `legacy/runnable/classes` + all of `legacy/original/jar/*.jar`
(the generator needs `compchem.jar`, `log4j.jar`; the assigners live in `seneca.client`
but use no Swing).

Order matters:
1. `calcMaxScore()` on the dataset's judges *before* serialising (maxScore travels in the copy).
2. `Rng.seed()` after all configuration, before `run()`; nothing before `run()` draws.
3. One generator per JVM at a time if results must be reproducible (shared static Rng).

**Verified results** (pinene.sml, AutoAssign, HMBC + HOSECode + Feature judges enabled,
HHCOSY disabled because pinene has no COSY data):

| seed | iterations | best/max | results | wall time |
|------|-----------:|---------:|--------:|----------:|
| 1 | 21 248 | 2400/2400 | 11 | ~1 s |
| 42 | 1 249 196 | 2305/2400 | 13 | ~30 s |
| 2, 4, 5, 6, 8 | 1.2-1.3 M | 2305/2400 | 13-24 | ~30 s |
| 3, 7 | 0 | — | 0 | <0.1 s (see quirk Q1) |

Same seed twice gives byte-identical result lists (seed 1 and seed 42 checked).

## 3. Dataset flow from an .sml file

`SpecMLReader(File)` parses with JAXP DOM, finds the `<senecadataset title=...>` element and
dispatches its children (`handleSDParts`, SpecMLReader.java:162):

| Element | Handling |
|---------|----------|
| `<formula>` | `sd.setMolecularFormula(String)`: `MFAnalyser(mf).getSetOfHeavyNodes()` -> `sd.setOfNodes` (heavy atoms, `HCount` 0, no shifts), then `maxBondOrder`/`maxBondOrderSum` from `StandardElements` |
| `<cml:molecule>` | `readMolecule`: atoms with `elementType`, `hydrogenCount`, `<float title="assignedCarbonShift">` -> replaces `setOfNodes` |
| `<molecule>` (no `cml:` prefix) | **ignored** (name test is exactly `cml:molecule`, line 176) |
| `<spectrum>` | `readSpectrum` -> `sd.carbon1D / dept90 / dept135 / hetcor / hetcorlr / hhcosy / noesy` by `<type>` text (`Carbon1D`, `DEPT-90`, `DEPT-135`, `1JCH correlation`, `CH long-range correlation`, `H,H-COSY`, `NOESY`) |
| `<annealingschedule>` | `AnnealingSchedule` from `initialAcceptanceProbability`, `maxUphillSteps`, `plateauSteps`, `asymptoticCoolingFactor`; other children (`converge`, `coolingCharacteristic`, `linearCoolingStepsize`, `noOfSteps`, `useStructure`) ignored; defaults 0.8 / 50 / 500 / 0.95 |
| `<judge>` | **ignored** (`//readJudge(newNode);`, line 186; `readJudge` would not invoke anything anyway) |
| `<serverinfo>` | `sd.serverList` (RMI, irrelevant) |

`getSenecaDataset()` then calls `sd.init()`, which appends fresh, **disabled** judges in this
order: `HHCOSYJudge, HMBCJudge, HeteroBondsJudge, SymmetryJudge, HybridizationJudge,
HOSECodeJudge, FeatureJudge`. Judge order = evaluation order in `getScore`.
`sd.getJudge("Name")` returns the instance from `sd.judges` (or a new one if absent).

**Verified survey of the bundled datasets** (state after reading, then after
`CarbonShiftAssigner`; COSY/HMBC = number of `true` cells in the assignment, -1 = null):

| file | molecule read | H after AutoAssign (formula) | shifts | schedule | COSY | HMBC |
|------|---------------|------------------------------|-------:|----------|-----:|-----:|
| pinene | no | 16 (16) | 10 | PS 500 | -1 | 14 |
| eurabidiol | no | 26 (28) | 15 | PS 1500 | -1 | 23 |
| monochaetin | no | 20 (20) | 18 | 0.97, PS 6000, UHS 600 | 8 | 24 |
| polycarpol | no | 46 (48) | 30 | PS 10000, UHS 1000 | 14 | 36 |
| narelin | yes | 20 (20) | 20 | PS 5000 | 13 | 23 |
| preconen | yes | 16 (16) | 13 | PS 1000 | 2 | 15 |
| triterpen | yes | 52 (52) | 32 | PS 500 | 18 | 37 |
| bn-ye1 | no | 0 (28), AutoAssign assigns nothing | 0 | PS 5000 | fail | fail |
| eupatorio | no | 13 (14) | 13 | **none** | -1 | -1 |
| Hyperaspine | no | 0 (30) | 19 | **none** | -1 | -1 |

Consequences for the harness:
- For "molecule read = no" files the GUI user had to press AutoAssign; for "yes" files not.
- Where H after AutoAssign < formula H (OH/NH hydrogens), `CarbonShiftAssigner` leaves
  hetero-atom `HCount` at 0 unless the carbon H sum equals the formula H; the GUI user then
  edited `HCount` in the Atom Properties table. The harness needs an explicit per-atom
  H-count override to reproduce such runs.
- No `<annealingschedule>` -> `sd.annealingSchedules` empty -> `run()` dies in
  `initAnnealing()` (`schedules.elementAt(0)`, caught, logs "Error in StoicGent.run()").
  The GUI user added a schedule in the "Annealing Schedule" tab (`new AnnealingSchedule()`
  defaults). The harness must do the same.

## 4. Judge configuration (what each "Autoconfigure" does)

All judges: `hasMaxScore` true except `WienerIndexJudge` (not in `sd.init()`, unused).
`getEnabled()` defaults to false; only enabled judges are `init()`ed and scored.

| Judge | GUI configuration | Harness equivalent |
|-------|-------------------|--------------------|
| HHCOSYJudge | `TwoDSpectrumJudgeConfigurator.autoconfigure`: `new HHCOSYAssigner(sd).assign()`; on `false` -> disabled | same (public class `seneca.client.HHCOSYAssigner`). Fills `boolean[n][n][n] assignment`. Default scores `{100,0,0,0,0,0,0}` (index = path length - 1) |
| HMBCJudge | same with `HMBCAssigner` (hetcorlr + hetcor) | same. Scores `{100,100,5,0,...}` |
| HOSECodeJudge | `setCarbonShifts(double[])` = 13C shifts of all atoms that have one, in atom order | see DriveSketch. Score 100 per carbon within the one-sphere HOSE confidence limit |
| SymmetryJudge | `clearSymmetryClasses()`; for each atom f: all g>f with exactly equal shift form one class `boolean[n]`, `addSymmetryClass(class)` (calls `init()`); score 500 per matching pair | reproduce the loop in `SymmetryJudgeConfigurator.autoconfigure` (client/SymmetryJudgeConfigurator.java:154) |
| FeatureJudge | `addRingSizeScore(3,-500); addRingSizeScore(4,-300); setHetHetPenaltyEnabled(true)` (penalty -5 per het-het bond) | same |
| HybridizationJudge | no autoconfigure; manual table of sp3/sp2/sp scores x100 | `addHybridizationScore(atom+1, SP3/SP2/SP, double)` — but see Q4 |
| HeteroBondsJudge | no autoconfigure; manual checkbox table | `isHeteroBonded[atom] = true` — but see Q4 |

Configurator constructors run when the dataset window opens and already have an effect:
`HybridizationJudgeConfigurator` and `HeteroBondsJudgeConfigurator` set
`judge.getAttic().atomSet = sd.getSetOfNodes()` and call `judge.init()` (allocates
`scores`/`isHeteroBonded`). DriveSketch replicates this.

In the GUI, a 2D judge whose assigner returned `true` but left `assignment == null`
(e.g. HHCOSY on a dataset without COSY signals) stays enabled, and `goAction` then throws
an NPE in `TwoDSpectrumJudge.calcMaxScore()` before anything is started. The harness
should simply not enable such a judge (DriveSketch does this).

## 5. What `StochasticGenerator.run()` does (order of operations and random draws)

1. `maxScore = getMaxScore()` (sum of enabled judges' `getMaxScore()`, -1 if any enabled
   judge lacks a max score).
2. `SingleRandomStructureGenerator.generate()` on the atom set: random saturated, connected
   starting structure. **Draws:** one per `getAnotherUnsaturatedNode` call and one per bond
   formed; restarts until connected and saturated.
3. `initJudges()`: for enabled judges `setAttic(generator attic)` then `init()`.
4. For each schedule: `initAnnealing()` = 500 connected random mutations to measure the mean
   score change; `start_kT = -mean / ln(initialAcceptanceProbability)`.
   **Draws:** `mutate()` = 4 per attempt to pick 4 distinct atoms (repeat until distinct),
   then 1 per attempt for the new bond order (repeat until different), outer repeat until valid.
5. Main loop per step: `mutate`, connectivity test; if connected: score all enabled judges,
   **one draw `rnd` always** (also when the move is downhill), Metropolis accept, update best,
   duplicate check by isomorphism against stored results (`notInStock`), `cool()`,
   convergence/reheat logic (reheat factor 0.3 + 0.1 per reheat, abort at 1.0).
6. Results: every new best-or-equal, non-isomorphic structure is pushed (MDL string +
   `ScoreSummary`) into `structureGeneratorResult` (`LIFOQueue(30)`).
   Also logged via log4j category `seneca.structgen.StochasticGenerator.results`
   (GUI: `~/seneca.results.sdf`).

All randomness of a run goes through `seneca.structgen.Rng` (6 sites in
`StochasticGenerator`, 2 in `SingleRandomStructureGenerator`). No other source of
nondeterminism was found in the generator path: `compchem.jar` and the judges contain no
`Math.random`/`java.util.Random`; the only hash-ordered iterations (`FeatureJudge` sum
over `Hashtable.elements()`, `Atom.toString`) are order-independent or debug output.
Console output of the assigners/configurators contains identity hash codes
(`HMBCJudge: [[[Z@6996db8`), so compare results, not raw stdout.

## 6. Quirks of the original that a 1:1 port must preserve (and tests must expect)

- **Q1 Immediate termination.** `goOn` starts `false` (StochasticGenerator.java:62) and is
  set `true` only inside the "structure is connected" branch (line 326). If the first
  mutation of a schedule yields a disconnected structure, the inner `while (goOn && ...)`
  exits after one step and the schedule ends with no result. Observed for 2 of 8 seeds on
  pinene. Consequence: `getStatus()` then throws NPE (`new String(this.bestStructure)`,
  line 411) — the GUI timer swallows it.
- **Q2 maxScore usually not reached.** pinene converges to 2305/2400 in most seeds; the
  run ends after the reheat factor reaches 1.0 (~30 s on this Mac).
- **Q3 FeatureJudge maxScore is 0.** `ringsizeRestrictionEnabled` is set in `init()`
  (FeatureJudge.java:65), which runs only in the generator, after the client-side
  `calcMaxScore()`. Ring penalties still apply during evaluation. `judgeRingScore()` is
  called twice per evaluation (line 93), harmless.
- **Q4 Manual hybridization/hetero-bond settings are wiped.** `initJudges()` calls `init()`
  with the generator's attic: `HybridizationJudge.init()` reallocates `scores` (line 60),
  `HeteroBondsJudge.init()` reallocates `isHeteroBonded` (line 53). Both judges therefore
  score 0 during generation even when configured. In addition `HeteroBondsJudge` declares a
  private `maxScore` (line 36) that shadows `Judge.maxScore`, so its `getMaxScore()` is 0.
- **Q5 TwoD assigners' alternative assignments are buggy.** `pickClosestCarbons` adds
  `setOfNodes[thisPosition]` instead of `[i]` for equal shifts (HMBCAssigner.java:154,
  HHCOSYAssigner.java:165); HHCOSYAssigner uses `elementAt(j)` instead of `(k)` (line 96).
  `TwoDSpectrumJudge.evaluate` counts every rule as "satisfied" (line 131); the score is
  correct, the message count is not.
- **Q6 The judges handed to the generator are `serClone(sd.judges)`, not the `judges`
  vector of clones that `goAction` builds (lines 737 vs 749).** `calcMaxScore()` therefore
  runs on the dataset's own judges before they are serialised.
- **Q7 SpecMLReader keeps static state** (`docStart`, `startNode`, line 55): a second file
  in the same JVM can resolve to the previous document's root if the new document's first
  child is not the `senecadataset` element. Use one JVM per file or keep files simple.
- **Q8 Thread priority.** `run()` calls `setPriority(Thread.MIN_PRIORITY)` on the current
  thread (also when called directly instead of via `start()`).

## 7. Logging and files

- Client config `seneca/client/logging.properties`: root DEBUG with console appender at
  ERROR; `seneca.structgen` -> `${user.home}/seneca.log`; `...StochasticGenerator.results`
  -> `${user.home}/seneca.results.sdf`; `...annealing` -> `${user.home}/seneca.annealing.log`
  (all `Append=false`, i.e. overwritten per JVM); `...StochasticGenerator.log` -> GUI text pane.
- `StructureGeneratorServer` re-applies `seneca/engine/logging.server.properties` (same
  files, no text pane).
- A headless harness should configure log4j itself (or pass `-Duser.home=<tmp>` to keep
  the home directory clean). Debug strings are built even when not logged (CPU only).

## 8. Monitoring and stopping a threaded run

```java
sg.execute();                                   // Thread.start()
StructureGeneratorStatus s = (StructureGeneratorStatus) sg.getStatus();
// s.iteration, s.bestStructure (MDL), s.bestEvaluation (ScoreSummary, maxScore set),
// s.status ("Idle"/"Running"/"Holding Results"/"Stopped by User"),
// s.al (AnnealingLog with entries added since the previous getStatus call:
//       one entry {iteration, kT, bestScore} per 500 iterations)
sg.end();                                       // pleaseStop = true; loop exits after current step
sg.join();
```
`sg.running` is a public field (true while `run()` is active). `getStatus()` throws NPE
until a first best structure exists (Q1).
