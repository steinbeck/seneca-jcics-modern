# SENECA 2001 (JCICS publication version) – Architecture

Authoritative description of the legacy code base in `legacy/original/src` (with the 2001 build
`legacy/dist-2001/seneca-client.jar` as tie-breaker). Later port phases use this document together with
`feature-inventory.md` as the 1:1 reference. Line numbers refer to `legacy/original/src/...`.

Reference: Steinbeck, C. *J. Chem. Inf. Comput. Sci.* 2001, 41(6), 1500–1507.

---

## 0. Findings that later phases must know first

1. **The source tree is incomplete; `seneca/structgen/SingleRandomStructureGenerator.java` is missing.**
   The class exists only in the 2001 jar (`legacy/dist-2001/seneca-client.jar`). A trial compile of
   `legacy/original/src` against `legacy/original/jar/*.jar` fails with exactly 5 errors:
   `StochasticGenerator.java:209,210` (missing class), `StochasticGenerator.java:416,421` and
   `SGServerPanel.java:541` (missing field `StructureGeneratorStatus.al`). The jar's
   `StructureGeneratorStatus` has an extra field `public AnnealingLog al = null;`. Spot checks of
   `StochasticGenerator.run()`, `StructureGeneratorResult.removeIsomorphism()`, `TwoDSpectrumJudge`,
   `HybridizationJudge`, `HeteroBondsJudge`, `FeatureJudge`, and `SymmetryJudge` (decompiled with CFR 0.152)
   match the source exactly. Treat the source and the two jar-only items as the reference.
   The repository's reference decompilation, made with Vineflower 1.11.1, is in `legacy/decompiled/`
   (`seneca-client-2001`, `compchem`, and `jmdraw`); a rebuilt `SingleRandomStructureGenerator.java`
   (bytecode-identical to the 2001 class) is in `legacy/runnable/src/seneca/structgen/`.
2. **There are two places with randomness, not one.** Besides `Math.random()` in
   `StochasticGenerator` (lines 255, 450–453, and 475), the jar-only `SingleRandomStructureGenerator`
   draws `Math.random()` to build the random start structure (section 4.2). No other code in the
   engine path (compchem and the judges) uses randomness (`compchem.jar` decompiled: no `Math.random`/`Random`).
3. **Scoring and duplicate detection depend on `compchem.jar` (binary-only, pre-CDK)** semantics that
   differ from CDK: the "isomorphism" test is a Morgan-number heuristic that ignores bond orders,
   the HOSE code is compchem's own 1-sphere format looked up in a 651-entry Bremser table, the
   valence table is compchem's own, and the ring finder (FeatureJudge) is not a minimum cycle basis
   and can throw on ring-rich candidates, which ends the run. A 1:1 port must reproduce these semantics
   (section 6; details in `compchem-semantics.md`).
4. **Several judges are effectively inert in the legacy program** because `init()` re-creates their
   configuration arrays on the generator side (HybridizationJudge, HeteroBondsJudge), and some
   maxScore computations are inconsistent (SymmetryJudge, FeatureJudge). Details in section 5.
   The port plan decides whether the port reproduces or fixes these defects (see section 9).
5. The legacy client starts and loads an SML file on JDK 22 with the 2001 jar and the bundled jars:
   `java -Dseneca.home=. -cp ../dist-2001/seneca-client.jar:jar/*.jar seneca.client.Seneca sml/eurabidiol.sml`
   (run from `legacy/original`). It creates `~/seneca.log`, `~/seneca.results.sdf`, and
   `~/seneca.annealing.log` and truncates them on every start.

---

## 1. Package overview

| Package | Files | Role | Port |
|---|---|---|---|
| `seneca.client` | 34 `.java` + `Seneca.properties`, `logging.properties`, `images/` | Swing GUI (MDI desktop), dataset editing, judge configuration, run control, and result display | KEEP (except RMI parts) |
| `seneca.core` | 9 | Data model (`SenecaDataset`, `NMRSpectrum`, and `NMRSignal`), SpecML reader/writer, and CML writer | KEEP |
| `seneca.structgen` | 8 (+1 jar-only) | Simulated annealing engine, annealing schedule and log, and containers for result and status | KEEP |
| `seneca.judges` | 14 | Scoring functions ("judges") | KEEP |
| `seneca.engine` | `StructureGeneratorServer`, `benchmarker`, `logging.server.properties` | RMI server wrapping one `StochasticGenerator` | DROP (replace with an in-process run controller) |
| `seneca.compute` | `Compute`, `Task` | RMI remote interfaces | DROP |
| `seneca.gatekeeper` | 10 | Central RMI registry of compute servers (`www.nmrshiftdb.org/GateKeeper`); checkpoint files | DROP |
| `jnt.scimark2` | 11 | SciMark 2 benchmark that `benchmarker` uses to measure server speed | DROP |
| `META-INF/*.mf`, `java.policy.txt`, `server.policy` | | Manifests for client/server/gatekeeper jars; RMI security policies | DROP |

Files named `.#Name.java.x.y` (e.g. `judges/.#FeatureJudge.java.1.4`, `client/.#SGServerPanel.java.1.6`)
are CVS merge backups of older revisions. They are not compiled (their names do not end in `.java`) and are not
part of the reference.

---

## 2. Classes and responsibilities

### 2.1 `seneca.client`

| Class | Responsibility |
|---|---|
| `Seneca` (package-private, `main`) | Main `JFrame` with `JDesktopPane`; menus/toolbar built from `Seneca.properties`; status bar (3 labels); actions New/Open/Save/Import LUCY/Exit/LnF/Current Structures/About; dataset switching. Configures log4j from `seneca/client/logging.properties`, takes the TextPane appender `A4` for the Logbook, and opens `args[0]` as SML. |
| `SenecaDatasetCollection` | `Vector` of open datasets and the current index; fires `ChangeEvent`s; removes a dataset when it signals `destroy`. |
| `SenecaDatasetFrame` | `JInternalFrame` per dataset with 8 tabs: General Settings, NMR Spectra (6 sub-tabs), Atom Properties, Annealing Schedule, Judges (7 sub-tabs), Serverlist, Structure Generation, and Logbook. Close button / close box → save prompt (writes CML, not SML). |
| `NMRSpectrumTablePanel` | Editable signal table for one `NMRSpectrum`; Add/Delete signal; WinNMR peak-pick paste. |
| `AtomPropertyPanel` | Heavy-atom table (symbol, H count, and assigned 13C shift) and carbon/DEPT shift table; "Autoassignment" (`CarbonShiftAssigner`); H-count balance message (red/green). |
| `AnnealingScheduleConfigurator` | Edits the dataset's list of `AnnealingSchedule`s. |
| `JudgeConfigurator` (abstract) | Common judge panel: "Autoconfigure Judge", "Report Judgeconfig", and the "Activate this Judge?" checkbox; listens to judge changes. |
| `TwoDSpectrumJudgeConfigurator` (abstract), `HMBCJudgeConfigurator`, `HHCOSYJudgeConfigurator` | Shows the 2D assignment table, autoconfigures via `HMBCAssigner` / `HHCOSYAssigner`, and has a report window. |
| `HOSECodeJudgeConfigurator`, `SymmetryJudgeConfigurator`, `FeatureJudgeConfigurator`, `HybridizationJudgeConfigurator`, `HeteroBondsJudgeConfigurator` | Per-judge configuration panels (section 5). |
| `SpectrumAssigner` (base), `CarbonShiftAssigner`, `HMBCAssigner`, `HHCOSYAssigner` | Automatic assignment of 1D shifts/H counts to atoms and of 2D correlations to atom pairs. |
| `SGServerPanel` | "Structure Generation" tab: table of generator servers, start/stop/reset, 2 s polling timer, best-structure view, evaluation HTML, JFreeChart annealing plot, "Summarize Result", and "Log Annealing Data". Contains the inner classes `ChartPanel` and `AnnealingDataSource`. **Mixed: RMI plumbing (DROP) + run monitoring UI (KEEP).** |
| `ComputeServerConfigurator` | "Serverlist" tab: shows `sd.serverList` and queries the GateKeeper HTML status. **DROP.** |
| `StrucDispFrame` | Separate `JFrame` "Structure Display": grid of structure drawings; Load MDL, Load LUCY Result, Save MDL, and Close. |
| `JMDrawPanel` | `JPanel` that renders a compchem `Atom[]` with jmdraw `RenderingEngine` (scaled into 80 % of the panel and centered). |
| `LUCYFileHandler` | Imports a LUCY `.col` dataset into a `SenecaDataset`. |
| `NodeTableFrame` | Dialog to edit the symbol, max bond order, max bond sum, and HCount of each atom. Unreachable from the GUI (its action is not registered). |
| `ConnTablePanel` | Read-only connection matrix table. Unused. |
| `AnnealingViewPanel`, `StructureGeneratorCollection` | Empty stubs. Unused. |
| `FastResolver` | Thread that resolves a host and looks up an RMI `StoIcGentServer`. Unused. **DROP.** |
| `Parameters`, `SenecaParameters` | Persist last-used directories in `~/seneca.props` (`filelocation.lucy` and `filelocation.seneca`, plus the unused `filelocation.logfile` and `filelocation.resultfile`). |
| `ResourceManager` | Resource bundle helper. Unused. |
| `ErrorMessages` | `stderr` messages prefixed "Seneca: ". |
| `ExtensionFileFilter` | Sun demo file filter by extension. |

### 2.2 `seneca.core`

| Class | Responsibility |
|---|---|
| `SenecaDataset` | One elucidation project: name, description, molecular formula, mass, 7 spectra (`carbon1D`, `dept90`, `dept135`, `hetcor`, `hetcorlr`, `hhcosy`, and `noesy`), `setOfNodes` (heavy atoms, `compchem.Atom[]`), `annealingSchedules` (Vector), `judges` (Vector), `serverList`, and `sessions` (unused). `init()` creates the 7 judges in a fixed order (section 5). `getJudge(name)` returns the instance from `judges` or, if absent, a new unregistered instance. |
| `NMRSpectrum extends Vector` | n-dimensional spectrum: `nucleus[]`, `dim`, `specType`, frequency, solvent, standard, and signals; `pickClosestSignal` and `pickSignals`. |
| `NMRSignal` | `shift[]` (float), `nucleus[]`, `intensity`, `normalizeFactor`, and `phase` (0 NONE, 1 POSITIVE, 2 NEGATIVE). |
| `CNMRSignal`, `Carbon1D` | Unused legacy subclasses. |
| `SpecMLReader` | DOM reader for `.sml` (section 7 of the inventory). Calls `sd.init()` after parsing. |
| `SpecMLGenerator` | Writes `.sml`. It does not write judges (the code is commented out). |
| `CMLGenerator` | Writes a CML-like document (used only when saving on dataset close). |
| `SpecMLConstants` | DOM node type names. |

### 2.3 `seneca.structgen`

| Class | Responsibility |
|---|---|
| `StochasticGenerator extends Thread` | The simulated annealing engine (section 4). Name "StoIcGent", version string "StoIcGenT V 0.6 beta, (c) 1998, 1999, 2000, Christoph Steinbeck". |
| `SingleRandomStructureGenerator` (jar only) | Builds one random, saturated, connected structure as the starting point. |
| `StructureGenerator` (abstract) | Base of `SingleRandomStructureGenerator`; `structureConnected()` via the all-pairs shortest-path (APSP) matrix. |
| `AnnealingSchedule` | `initialAcceptanceProbability=0.8`, `maxUphillSteps=50`, `plateauSteps=500`, `asymptoticCoolingFactor=0.95`; `toString()` = `"A-F:0.95-IAP:0.8-PS:500-UHS: 50"`. |
| `AnnealingScheduleInterface` | Unused interface. |
| `AnnealingLog` | Growable `Float[series][item][3]` table; default `iterationAxisStepsize = 500`. |
| `Attic` | Shared scratch data for judges: `atomSet` (the current candidate, bonds synchronized), `normalized`, `apsp` (Floyd all-pairs shortest-path matrix, set once by `SingleRandomStructureGenerator.generate()` for the start structure and never updated: the per-step recomputation in `StochasticGenerator` is commented out), and `setOfAllRings` (unused). |
| `StructureGeneratorResult` | Two `LIFOQueue(30)`s: `structures` (MDL molfile strings) and `scoreSummaries`; `sort()`, `removeIsomorphism()`, and `merge()`. |
| `StructureGeneratorStatus` | Snapshot for polling: `datasetName`, `molecularFormula`, `iteration`, `bestStructure` (MDL string), `status`, `bestEvaluation` (`ScoreSummary`), and `al` (jar only). Status strings: `Idle`, `Running`, `Holding Results`, and `Stopped by User`. |

### 2.4 `seneca.judges`

`Judge` (abstract base), `JudgeResult` (`score`, `maxScore`, `propertyValue`, and `scoreDescription`),
`ScoreSummary` (`score`, `maxScore=-1`, and `description`; `toString()` = description + `"Overall Score:" + score [+ "/" + maxScore if > 0]`),
`JudgeListener`, `TwoDRule`, `TwoDSpectrumJudge`, `HMBCJudge`, `HHCOSYJudge`, `HOSECodeJudge`,
`SymmetryJudge`, `FeatureJudge`, `HybridizationJudge`, `HeteroBondsJudge`, and `WienerIndexJudge`
(not instantiated anywhere; if it were, it would read the stale `attic.apsp` of the start structure;
see 5.7).

### 2.5 RMI-only classes (all DROP)

`seneca.engine.StructureGeneratorServer` (plus rmic `_Stub`/`_Skel` in the jar), `seneca.engine.benchmarker`,
`seneca.compute.Compute`, `seneca.compute.Task`, all of `seneca.gatekeeper`
(`GateKeeper`, `GateKeeperInterface`, `ServerInfo`, `Session`, and 6 exception classes), `jnt.scimark2.*`,
`client.ComputeServerConfigurator`, `client.FastResolver`, and the RMI parts of `client.SGServerPanel`
(`ScanNetAction` and `Compute` casts). `ServerInfo` is also referenced by `SenecaDataset.serverList` and
by the SpecML `<serverinfo>` reader and writer; the port may parse and ignore `<serverinfo>`.

---

## 3. Data flow (legacy)

```
Seneca (JFrame)
 └─ SenecaDatasetFrame (per dataset)
     ├─ General / NMR Spectra / Atom Properties  → edit SenecaDataset (formula, spectra, setOfNodes H counts + 13C shifts)
     ├─ Annealing Schedule                       → sd.annealingSchedules
     ├─ Judges (7 configurators)                 → configure the Judge objects in sd.judges, set enabled
     └─ Structure Generation (SGServerPanel)
          "Start local server"  → new StructureGeneratorServer("local"+i)   (in-process RMI object)
          "Update server list"  → Naming.lookup("//www.nmrshiftdb.org/GateKeeper").getHosts(8)
                                   → Naming.lookup("//host/StoIcGentServer")   (remote)
          "Start as selected"   → for each selected server:
                 for enabled judges: judge.calcMaxScore()            (client side, mutates sd.judges)
                 sgi.setMolecule(serClone(new Molecule(sd.setOfNodes)))
                 sgi.setJudges(serClone(sd.judges))                  (ALL judges, enabled or not)
                 sgi.setDatasetName(sd.name)
                 sgi.setSASchedules(serClone(clones of sd.annealingSchedules))
                 sgi.executeTask()
                     └─ StructureGeneratorServer.executeTask():
                          stoicGent = new StochasticGenerator(true)
                          setMolecule / setJudges / setDatasetName / setSASchedules
                          stoicGent.execute() → Thread.start() → run()  (section 4)
          Timer 2000 ms (round robin, one server per tick, index currentBest):
                 sgs[i] = server.getTaskStatus()   → StochasticGenerator.getStatus()
                 draw sgs[i].bestStructure (MDL → compchem Atom[] → JMDraw), evaluation HTML,
                 append sgs[i].al to the chart data (series i)
          "Stop Selected"  → server.endTask()   → stoicGent.end()  (pleaseStop = true)
          "Reset selected" → chart data cleared; server.resetTask() → stoicGent = null
                 (no end(): the generator thread keeps running, logging and appending to
                  ~/seneca.results.sdf; the slot just loses its reference to it)
          "Clear Server List" → drop all server references (generator threads keep running)
          "Summarize Result" → for all servers: getTaskResult() → StochasticGenerator.getResult()
                 merge (pop all), removeIsomorphism(), sort(), show in StrucDispFrame
          "Log Annealing Data" → write chart data table to log4j/stdout
```

Further run-control quirks:

- "Start as selected" on a slot that is already running calls `executeTask()` again, which creates a
  second `StochasticGenerator`. The first one keeps running, unreachable from the GUI.
- "Clear Server List" sets the list size to 0 but keeps the `sgs` status array. The next "Start local
  server" calls `updateSgs()`, which copies the old array into a new one of the new (shorter) size.
  If the cleared list had 2 or more slots, the copy throws `ArrayIndexOutOfBoundsException`. The catch
  prints the stack trace; by then the slot has been added, but the 2 s poll timer is not
  restarted, so the table stops updating.

`serClone` = Java serialization round trip (deep copy). Each run therefore owns private copies of
molecule, judges, and schedules. For remote servers RMI serializes every call argument and return
value; for local servers (`StartLocalAction`) the `StructureGeneratorServer` is a plain in-process
object and calls are direct method calls.

**Consequence for `getTaskResult()` on local servers:** `StochasticGenerator.getResult()` returns
`structureGeneratorResult.clone()`, a shallow `Object.clone()`. `ShowCurrentAction` then `pop()`s all
entries and appends `"<p>Result from host i</p>"` to each `ScoreSummary.description`. On a local
server these calls drain the generator's live result queue (and its duplicate-check stock) and mutate its
summaries; with RMI (serialized copy) they do not. The port should implement the RMI semantics
(return a deep copy).

### 3.1 What the in-process replacement must provide

Replace `StructureGeneratorServer`, `Compute`, and `GateKeeper` with an in-process run controller that
offers the same observable behaviour as "local servers":

- **Runs list** (one entry per generator, equivalent of table rows): No., ID (legacy: `"local" + index`),
  Host (legacy: local host name), Dataset, MF, Status, Iter.; multi-row selection.
- **Create run slot** ("Start local server"): adds an idle slot (status `idle`/`none` until started).
- **Start selected**: per selected slot, deep-copy `setOfNodes` (as `Molecule`), all judges (after
  `calcMaxScore()` on the enabled ones in the dataset), dataset name, and schedules; then start a
  background thread that runs the annealing (section 4). Several runs may execute concurrently and
  independently (the README workflow starts four).
- **Stop selected**: set the stop flag (`end()`); the run finishes as described in 4.6.
- **Reset selected**: clear plot data; discard the generator of the selected slots. Unlike legacy,
  stop the run first (legacy leaves the thread running; see above).
- **Start on a running slot**: stop the old run first (legacy orphans it).
- **Clear list**: remove all slots and stop their runs (legacy leaves them running and can break the
  next "Start local server"; see above).
- **Progress polling every 2 s** (round robin over slots; one slot per tick): `StructureGeneratorStatus`
  with iteration, status string, best structure (MDL string), best `ScoreSummary` including `maxScore`,
  and the new annealing log entries since the previous poll.
- **Annealing log/plot**: one series per run (`"host " + (i+1)`) plus, in score mode, a horizontal
  `"max. Score"` series; x = iteration, y = best score or kT (a mouse click toggles between them).
- **Results**: per run, up to 30 best structures (MDL and `ScoreSummary`), returned as a deep copy;
  merged view with duplicate removal and sorting ("Summarize Result").
- **Logbook**: INFO messages of the engine log (sections 4.5 and 4.10) shown in the Logbook tab and written to
  `~/seneca.log`; every new best structure appended to `~/seneca.results.sdf`.
- **Pause does not exist in the legacy program.** It has only Stop and Reset. Pause and resume would be
  an addition, not a 1:1 requirement.
- Thread safety: legacy polls `getStatus()` from the Swing timer while the generator thread mutates
  state without synchronization; the port must publish snapshots safely (e.g. copy under a lock).

---

## 4. The annealing algorithm (`StochasticGenerator`)

Notation: `bondMatrix` is an `int[n][n]` symmetric bond-order matrix over the heavy atoms
(`n = setOfNodes.length`; hydrogens are implicit via fixed `HCount`). Bond order 0 means no bond.

### 4.1 Setup (`setMolecule`, `run()` lines 175–223)

1. `setMolecule(m)`: `atomSet`, `attic.atomSet`, and `attic.normalized` = independent `cloneAtomSet` copies of `m.getAtoms()`.
2. `run()`: `maxScore = getMaxScore()` = Σ `judge.getMaxScore()` over enabled judges; -1 if any
   enabled judge has `hasMaxScore == false` (no GUI judge does). `reheatFactor = 0.3` (a local variable
   that lives for the whole run and is not reset per schedule). Thread priority MIN.
3. Start structure: `SingleRandomStructureGenerator` with `setAtomSet(atomSet)` (clones again) and
   `attic = this.attic`; `atomSet = generate()` (4.2). `generate()` also sets `attic.atomSet` to
   its own atom array, so from now on `attic.atomSet` and `atomSet` are the same array object.
4. `bondMatrix = createConnectionTable(atomSet)`; `synchronizeConnectionTable` (copy upper → lower).
5. `lastScore = bestScore = ScoreSummary(0, "")`; `iteration = 0`.
6. `initJudges()`: for each enabled judge: `setAttic(this.attic)`, `init()`.

### 4.2 Random start structure (`SingleRandomStructureGenerator.generate()`, jar only)

```
for each atom: reset()                      // degree = 0, bondOrderSum = 0
repeat (attempt):
    iteration++
    repeat (sweep):
        formed = false
        for n2 in 0..n-1:
            if atom[n2] not saturated:                       // bondCount + HCount != maxBondOrderSum
                start = (int)(Math.random() * n)             // DRAW A
                n3 = first j in start..n-1, then 0..start-1 with:
                     atom[j] unsaturated, j != n2, not bonded to atom[n2];  else -1
                if n3 != -1:
                    m  = min(atom[n2].getCurrentMaxBondOrder(), atom[n3].getCurrentMaxBondOrder())
                         // getCurrentMaxBondOrder = min(maxBondOrder, maxBondOrderSum - HCount - bondOrderSum)
                    bo = max(1, (int)(Math.random() * m))     // DRAW B  → never equals m unless m == 1
                    atom[n2].formBond(atom[n3], bo); atom[n3].formBond(atom[n2], bo)
                    formed = true
    until not formed
    attic.atomSet = atomSet; bondMatrix = createConnectionTable; attic.apsp = Floyd APSP
    if no unsaturated atom and structureConnected (all APSP entries ≤ n): return atomSet
    reset all atoms
until forever (the loop flag is never set; returns only on success)
```

### 4.3 Move: `mutate(int[][] bm)` (lines 438–490; Faulon, JCICS 1996, 36, 731)

```
do:
    do:
        x1 = (int)(Math.random()*n); x2 = …; y1 = …; y2 = …   // 4 DRAWS, in this order
        Thread.yield()
    while not all four distinct
    a11 = bm[x1][y1]; a12 = bm[x1][y2]; a22 = bm[x2][y2]; a21 = bm[x2][y1]
    min = max(0, a11-a22, a11+a12-3, a11+a21-3)
    max = min(3, a11+a12, a11+a21, a11-a22+3)
    b11 = a11
    if (max - min > 0 || a11 != min):
        do: b11 = (int)(Math.random() * (max-min+1)) + min    // DRAW (repeated)
        while (a11 == b11)
    Thread.yield()
while not (b11 != a11 && min <= b11 <= max)
bm[x1][y1] = bm[y1][x1] = b11
bm[x1][y2] = bm[y2][x1] = a11 + a12 - b11
bm[x2][y2] = bm[y2][x2] = a22 - a11 + b11
bm[x2][y1] = bm[y1][x2] = a11 + a21 - b11
```

Properties: the move preserves every row sum (bond-order sum per atom), so valences and H counts stay fixed;
bond orders stay in 0..3 (the move ignores the element's `maxBondOrder`). The move does not guarantee
connectivity; the caller checks it afterwards. When `max - min + 1 ≤ 0`, the inner draw yields `b11 = min` and
the outer loop rejects it; the draw is still consumed.

### 4.4 Temperature initialisation: `initAnnealing()` (lines 536–590), once per schedule

```
currentSchedule = schedules[currentScheduleNo++]
last = 0 (local ScoreSummary); changes = 0 (long); f = 0
do:
    bm = clone(bondMatrix)                        // always a neighbour of the CURRENT structure
    mutate(bm)                                     // draws
    synchronizeBonds(attic.atomSet, bm)
    if PathUtils.isConnected(attic.atomSet):
        recent = getScore(bm)                      // judges evaluate attic.atomSet
        changes += |last.score - recent.score|     // first term = |0 - score| (full score!)
        last = recent; f++
while f < 500                                      // 500 CONNECTED samples; disconnected ones are not counted
plateauSteps        = currentSchedule.plateauSteps
maxUphillSteps      = currentSchedule.maxUphillSteps
convergenceCounter  = 0
convergenceStopCount= plateauSteps * 3            // fixed for the schedule (not updated on reheat)
changes             = changes / 500               // long integer division
start_kT            = -changes / ln(initialAcceptanceProbability)
current_kT          = start_kT
coolingRate         = asymptoticCoolingFactor
```

`initAnnealing` does not change `bondMatrix`; it leaves `attic.atomSet` with the bonds of the last
sample (the next main-loop step re-synchronizes it). If `changes == 0`, `start_kT = 0.0`
(then `deltaE/kT` is `+Inf` for worse moves, which are never accepted, and `NaN` for equal moves, which
the `deltaE <= 0` branch accepts). `plateauStepCounter` and `uphill` are not reset here.

### 4.5 Main loop (lines 224–375)

```
do (per schedule, while currentScheduleNo < schedules.size()):
    initAnnealing()
    current_kT = start_kT; annealingStepCounter = 0
    do:
        bm = clone(bondMatrix); mutate(bm)                                   // draws
        synchronizeBonds(attic.atomSet, bm)
        if isConnected(attic.atomSet):
            recent = getScore(bm)
            deltaE = lastScore.score - recent.score                         // long; > 0 means worse
            rnd = Math.random()                                             // ALWAYS drawn for connected candidates
            exp = (double)deltaE / current_kT
            if deltaE <= 0 || rnd < Math.exp(-exp):                         // Metropolis
                if deltaE <= 0: uphill++                                    // NB: counts NON-worse accepted moves
                lastScore = recent; bondMatrix = bm
                if deltaE < 0: convergenceCounter = 0                       // strict improvement only
                if recent.score >= bestScore.score:
                    latest = cloneAtomSet(atomSet); synchronizeBonds(latest, bm)
                    if notInStock(latest):                                  // 4.7
                        mdl = MDL molfile of latest, title "Score: " + recent.score
                        log results: mdl + "\n$$$$\n"                       // → ~/seneca.results.sdf
                        log INFO "New best Structure: \n" + bestScore + "\n" // prints the PREVIOUS best
                        bestScore = recent; bestStructure = mdl
                        result.structures.push(mdl); result.scoreSummaries.push(recent)
            convergenceCounter++; annealingStepCounter++; iteration++
            cool()
            if iteration % 500 == 0 (alStepsize): al.addEntry((float)current_kT, (float)bestScore.score)
            if iteration % 1000 == 0: log INFO "Iteration: <it> " and "Current kT = <kT>\n"
            goOn = true
            if convergenceCounter >= convergenceStopCount:                  // reheat / termination
                if bestScore.score < maxScore && reheatFactor < 1:
                    current_kT += (start_kT - current_kT) * reheatFactor
                    convergenceCounter = 0; annealingStepCounter = 0
                    plateauSteps   = (long)(plateauSteps * 1.5)
                    maxUphillSteps = (long)(maxUphillSteps * 1.5)
                    reheatFactor  += 0.1
                    log INFO converged / best / max / "Reheating by " + reheatFactor*100 + "%" / new limits
                else if reheatFactor >= 1:
                    log INFO "did not converge … Aborting."; goOn = false
                else if bestScore.score >= maxScore:
                    log INFO "Maximum score reached. Regular end of calculation."; goOn = false
        Thread.yield()
    while goOn && !pleaseStop
    goOn = false
while currentScheduleNo < schedules.size()
log INFO stop time, "Overall runtime: <s> seconds.", "Simulated annealing run finished."; running = false
```

`cool()` (lines 595–605), called once per connected candidate:

```
if plateauStepCounter > plateauSteps || uphill > maxUphillSteps:
    current_kT *= coolingRate; plateauStepCounter = 0; uphill = 0
plateauStepCounter++
```

Exact consequences that a 1:1 port must keep:

- Only connected candidates count as iterations, consume the acceptance draw, cool, log, or
  advance convergence. Disconnected candidates consume only `mutate()` draws.
- `goOn` is `false` at the start of every schedule. If the first candidate of a schedule is
  disconnected, the inner loop exits after that single attempt, and the schedule ends.
- Reheat factors used, in double arithmetic: 0.3, 0.4, 0.5, 0.6, 0.7, 0.7999999999999999,
  0.8999999999999999, 0.9999999999999999. That allows at most 8 reheats per run (shared by all schedules);
  after that every convergence event ends the current schedule.
- If `maxScore == -1`, the first convergence event ends the schedule (third branch).
- A new schedule continues from the current `bondMatrix` (no restart); `bestScore`, the result
  queue, `lastScore`, `reheatFactor`, `plateauStepCounter`, and `uphill` carry over.
- `run()` catches any exception raised during the search (everything after `getMaxScore()`), logs it
  as FATAL "Error in StoicGent.run()", and prints the stack trace; `running` stays `true`. Typical cause: an empty schedule list (`elementAt(0)`).

### 4.6 Stop

`end()` sets `pleaseStop = true`. The inner loop exits; the outer loop still runs every remaining
schedule: `initAnnealing()` (500 connected samples) plus one loop body each, then finishes.
`pleaseStop` is reset only at the start of `run()`.

### 4.7 Duplicate check `notInStock(atomSet)`

`IsomorphismTester(latest)` against each MDL string in `result.structures` (≤ 30, re-parsed with
`ChemFileUtils.readMDLMolFile`, H counts re-derived by `saturate()`). compchem "isomorphism":

1. Morgan vector `m = computeMorganMatrix(atoms)`: start `m0[i] = degree(i)`; repeat `n` times
   `m'[i] = Σ m[j]` over neighbours `j` (own value not included); Java `int` arithmetic (overflow
   wraps for larger molecules). The recurrence ignores bond orders and elements.
2. Equal sorted Morgan vectors required.
3. For each base atom `i` and each compare atom `j` with `m_base[i] == m_cmp[j]`: symbols and `HCount`
   must match; every base atom needs at least one Morgan match.

The test can therefore report as duplicates structures that have the same skeleton and matching H
counts but different bond orders. Port this check verbatim.

### 4.8 Every `Math.random()` draw, in order (per run)

1. Start structure (4.2): for each unsaturated atom visited in a sweep, draw A (partner start index); if a
   partner is found, draw B (bond order). Sweeps and attempts repeat until success.
2. For each schedule, `initAnnealing()`: `mutate()` draws for each sample until it has 500 connected samples.
3. Main loop: per candidate, `mutate()` draws (4 per atom-quadruple attempt, plus `b11` draws), then,
   only if the candidate is connected, one acceptance draw `rnd`.

`Math.random()` is a single JVM-wide generator. With several concurrent local runs, the draws
interleave between threads, so legacy runs are not reproducible. Recommendation: inject one seedable
`java.util.Random` per run and call its `nextDouble()` exactly where the legacy code calls
`Math.random()`, preserving the draw order. Trace-equivalence tests against the port can then use a
seeded variant of the legacy code (a patched copy outside `legacy/original`).

### 4.9 Scoring call `getScore(bm)` (lines 627–663)

For each judge in `judges` order, if enabled: `ser = judge.evaluate(bm)`; `score += ser.score`;
`description += ser.scoreDescription + "\n"`. After the sum: `if score < 0: score = 0`.
Returns `ScoreSummary(score, description)`. All judges read the candidate from `attic.atomSet`
(bonds synchronized from `bm` before the call), not from `bm`.

### 4.10 What is recorded

| Record | Content | Where |
|---|---|---|
| `structureGeneratorResult` | `LIFOQueue(30)` of MDL strings and `ScoreSummary`s; newest at index 0; oldest dropped beyond 30 | `getResult()` |
| `bestStructure`, `bestScore` | last accepted new-best (score ≥ previous best and not in stock) | `getStatus()` |
| `AnnealingLog al` | every 500 connected iterations: `(x = itemCount*500, kT, bestScore)`; stored as `{x, score, kT}`; x is lagged (the first entry, at iteration 500, has x = 0) | `getStatus()` returns only entries added since the last call |
| `iteration` | number of connected candidates evaluated | status |
| log | see 4.5; INFO level for `seneca.structgen.StochasticGenerator.log` | Logbook pane, `~/seneca.log` |
| results log | each new best: MDL + `$$$$` | `~/seneca.results.sdf` |

`getStatus()` details: `molecularFormula` from `MFAnalyser(atomSet)`; `bestStructure =
new String(this.bestStructure)` throws NPE until the first best exists (the legacy client swallows it);
`bestScore.maxScore = maxScore` is written into the live best summary; status string: `Idle` by
default, `Stopped by User` if `pleaseStop`, `Running` if `!pleaseStop && goOn`, and `Holding Results` if
`!pleaseStop && !goOn`. When copying log entries it calls `addEntry(Float it, Float temperature,
Float score)` with `(e[0], e[1], e[2])`, i.e. the client-side log stores `{x, kT, score}` (indices 1
and 2 swapped relative to the generator's log). The chart and "Log Annealing Data" read the
client-side layout.

---

## 5. Judges

Common: `Judge.enabled` defaults to `false`; `JudgeConfigurator`'s "Autoconfigure Judge" sets it `true`
before configuring. `sd.init()` creates the judges in this order (also the evaluation order
and the order of the descriptions): **HHCOSYJudge, HMBCJudge, HeteroBondsJudge, SymmetryJudge,
HybridizationJudge, HOSECodeJudge, FeatureJudge**. The client calls `calcMaxScore()` on the
enabled judges right before the run; the generator calls `init()` on the enabled judges
after the start structure exists (with `attic.atomSet` set).

**Total score** = max(0, Σ enabled judge scores) (long). **Total max score** = Σ `getMaxScore()` of
enabled judges, or -1 if one has no max. A run ends regularly when the best score reaches the total
max at a convergence check (4.5).

### 5.1 TwoDSpectrumJudge (base of HMBCJudge, HHCOSYJudge)

- Config: `boolean[n][n][n] assignment` (`[from][to][to]` = correlation between atoms `from` and
  `to`; `[from][to][alt]` = alternative partner), `int[7] scores` (index = path length − 1).
- `init()`: append a `TwoDRule(f, h)` to `rules` for every true `assignment[f][g][h]` (each
  alternative becomes its own rule; `rules` is not cleared, so `init()` must run once per fresh
  copy); `cutoff = 1 + highest index i with scores[i] > 0`.
- `calcMaxScore()`: `numberOf2DSignals` = number of true entries; `maxScore = numberOf2DSignals ×
  max(scores)`. NPE if `assignment == null`.
- `evaluate()`: if `assignment == null` → score 0, `"<p>No signals available for <name></p>"`.
  For each rule: `p = PathUtils.getPathLength(attic.atomSet, a1, a2, cutoff)` (BFS bond distance;
  -1 if > cutoff or not reachable; also -1 for `a1 == a2`); if `p > 0`: `score += scores[p-1]`.
  `satisfiedSignals` is incremented for every rule (brace bug).
  Description: `"<p>" + satisfied + "/" + numberOf2DSignals + " Signals satisfied in " + name + ". Score " + score + "/" + maxScore + "</p>"`.
- **HMBCJudge**: name "HMBCJudge", `scores = {100, 100, 5, 0, 0, 0, 0}` → cutoff 3. Atoms are
  carbons: path 1 (2J_CH) = 100, path 2 (3J_CH) = 100, path 3 (4J_CH) = 5.
- **HHCOSYJudge**: name "HHCOSYJudge", `scores = {100, 0, …}` → cutoff 1 (vicinal: carbons bonded).
- Max = number of rules × 100.

### 5.2 HOSECodeJudge

- Config: `double[] carbonShifts` (autoconfigure: the assigned 13C shifts of all atoms that have one,
  in atom order, compacted). Field `score = 100` (shadows `Judge.score`; `setScore()` is a no-op).
- `init()`: nothing. `calcMaxScore()`: `carbonShifts.length × 100` (NPE if not configured).
- `evaluate()`: for each atom `f` with symbol `"C"`: `code = HOSECodeGenerator.getHOSECode(attic.atomSet,
  atom_f, 1)` (compchem 1-sphere code, e.g. `CC(//)`, `=OCC(//)`, no H count, no ring flag);
  `shift` and `confidenceLimit` from `BremserOneSphereHOSECodePredictor` (651 codes; an unknown code
  throws an exception, and the carbon then contributes nothing and is not counted);
  `deviation = |shift − carbonShifts[f]|` (indexed by atom index f, which is correct only when
  carbons come first in the atom list, as they do for atoms generated from a formula; otherwise the same
  catch swallows the `ArrayIndexOutOfBounds`); `+100` if `deviation < confidenceLimit`.
- Description: `"<p>Score: " + score + "/" + maxScore + ", Carbon shift medium deveation: " + (sumDeviation/carbonCount) + "</p>"`.
- compchem HOSE code construction (1 sphere): neighbours of the centre sorted by descending
  `elementRank + bondRank` (ranks C 9000, O 8900, N 8800, S 8700, P 8600, Si 8500, B 8400, F 8300,
  Cl 8200, Br 8100, I 7900; bond rank double 200000, triple 300000, aromatic 100000), bond prefixes
  `""`, `"="` (2), `"%"` (3), `"*"` (4); symbols Si→`Q`, Cl→`X`, Br→`Y`; suffix `"(//)"`.

### 5.3 SymmetryJudge

- Config: list of symmetry classes (`boolean[n]` each). Autoconfigure: for each atom `f`, the class
  `{f} ∪ {g > f : shift(g) == shift(f)}` (exact float equality of assigned 13C shifts) if non-trivial;
  a group of k equal shifts yields k−1 nested classes. `score = 500` (base field).
  Quirk: `shift1` is updated only when atom `f` has a 13C shift. For an atom without a shift (e.g. a
  heteroatom), it keeps the previous atom's value, so that atom is put in a class with every later atom
  that has the previous atom's shift. Legacy-compatible by default; fix candidate.
- Configurator table quirk: column `Symb.` shows `sd.setOfNodes[row].symbol`, where `row` is the
  class index, not an atom of the class. Display only.
- `init()`: copy the class vector into an array. `calcMaxScore()`: `Σ faculty(count)/2` (no ×500; `int`
  factorial overflows for > 12 atoms).
- `evaluate()`: Morgan vector of `attic.atomSet` (4.7); per class, per pair `g < h` both in class:
  `+500` if Morgan values equal. Description `"" + score/500 + " symmetry classes are completly filled."`
- Effect: maxScore is in different units from the score (1 vs 500 per pair for a 2-member class).

### 5.4 FeatureJudge

- Config: `Hashtable ringSizeScores` (size → score; score 0 is not stored), `hetHetPenalty = -5`,
  and `hetHetPenaltyEnabled = false`. Autoconfigure: ring 3 → −500, ring 4 → −300, and het-het penalty on.
- `init()`: `ringsizeRestrictionEnabled = ringSizeScores.size() > 0`.
  `calcMaxScore()` (runs on the client, where `init()` was never called): `maxScore = 0` unless the
  flag is already set; then Σ ring scores. In practice 0.
- `evaluate()`: if ring restriction: for each ring of the compchem SSSR (smallest set of smallest rings, `SetOfRingsFinder.findSSSR`):
  add `ringSizeScores[size]` (computed twice, same value). `findSSSR` throws on some ring-rich
  candidates; the exception ends the run (D14, `compchem-semantics.md` 6.3). If het-het enabled: for each non-C atom,
  for each non-C neighbour: `+hetHetPenalty`; result `/ 2` (int division). Description
  `"<p>FeatureJudge->" + ["RingScore: x. "] + ["HetHetScore: y. "]` (unclosed `<p>`).
- `getRingSizeScore()` uses `Hashtable.contains(value)` (bug; unused by scoring).

### 5.5 HybridizationJudge

- Config: `int[n][3] scores` (SP3=0, SP2=1, SP=2), set from the table as `value × multiplicator(100)`
  (values 0.0–1.0 from a combo box). The configurator calls `init()` once to allocate the array.
- `calcMaxScore()`: Σ over atoms of max(scores[f][*]).
- `evaluate()` for carbons, with `degree` = number of heavy neighbours, `b` = Σ bond orders:
  degree 1: b=1 SP3, 2 SP2, 3 SP; degree 2: b=2 SP3, 3 SP2, 4 SP; degree 3: b=3 SP3, 4 SP2;
  degree 4: SP3. Description uses the unrelated base field `score` (always 0):
  `"<p>0 from a possible <max> points for hybridization state were given.</p>"`.
- **Legacy defect:** `init()` (called by `initJudges()` on the generator side) re-allocates `scores`
  as zeros, so the judge always scores 0 during a run, but its maxScore still counts toward the
  total; the total max is therefore unreachable, and the run ends only via the reheat limit.

### 5.6 HeteroBondsJudge

- Config: `boolean[n] isHeteroBonded` (table checkbox per atom), `score = 100`.
- `calcMaxScore()` writes a private `maxScore` field that shadows `Judge.maxScore`;
  `getMaxScore()` returns the base field (0).
- `evaluate()`: per carbon flagged hetero-bonded: `+100` if any neighbour is not `"C"`.
  Description `"<p>k of n carbons assigned to be heterobonded are indeed hetero-bonded in the structure.</p>"`.
- **Legacy defect:** `init()` on the generator side re-allocates `isHeteroBonded` as all `false`,
  so the judge always scores 0 (and contributes 0 to the max).

### 5.7 WienerIndexJudge

It is not created by `sd.init()`, has no configurator, and is unreachable. If it were enabled, it would not throw: it
would sum `attic.apsp`, which holds only the distances of the start structure (set once in 4.2 and
never updated), so its score would stay constant during a run. DROP (document only).

### 5.8 Assigners (judge autoconfiguration)

- `CarbonShiftAssigner` (Atom Properties → Autoassignment): `normalizeFactor = 1/intensity` for every
  13C signal; while `round(Σ intensity×normalizeFactor) < #C`: double the `normalizeFactor` of the
  signal with the largest raw intensity (always the same signal). If `#C == round(Σ)`: walk atoms
  in order; for each carbon take the next 13C signal and assign it to `round(intensity×normFactor)`
  atoms via `for g: f += g` (skips atoms for multiplicities ≥ 3; legacy defect); for each assigned atom
  pick the closest DEPT-90/DEPT-135 signals within 0.05 ppm and apply four independent `if`s, in this
  order, each adding to the running H sum `HCounter`:
  (1) DEPT135 positive and no DEPT90 → HCount 3, `HCounter += 3`;
  (2) DEPT135 negative → HCount 2, `HCounter += 2` (warning on stdout if a DEPT90 signal is also present);
  (3) DEPT90 positive → HCount 1, `HCounter += 1`;
  (4) neither signal → HCount 0.
  A carbon with a negative DEPT135 and a positive DEPT90 signal matches (2) and (3): it ends with
  HCount 1, but `HCounter` grows by 3. Last step: if `HCounter` equals the formula's H count, all
  non-C atoms get H=0. The double count can make this test pass or fail wrongly.
- `HMBCAssigner`: requires every carbon to have an assigned shift. For each CH long-range signal:
  `from` = carbon(s) whose assigned shift is closest to the signal's 13C shift (within 0.05);
  `to` = carbon(s) for the 13C shift of the closest 1JCH signal by 1H shift (within 0.01);
  `assignment[from][to][to] = true`, alternatives `[from][to][toAlt]`. `pickClosestCarbons` adds
  the closest atom again instead of the other equal-shift atoms (defect), so alternatives collapse to
  `to`. No exception handling: a missing 1JCH partner throws.
- `HHCOSYAssigner`: same scheme for H,H-COSY signals (both protons mapped via 1JCH to carbons);
  `from` from proton 2, `to` from proton 1; alternative index uses `j` instead of `k` (defect);
  exceptions → `false` (judge disabled).
- Neither 2D assigner clears an existing matrix: `assignment` is allocated only if it is `null` or
  empty. Pressing Autoconfigure again (e.g. after editing spectra) adds the new correlations to the
  old ones, and `calcMaxScore` counts all of them. A failed HH-COSY autoconfigure disables the judge
  but leaves the previous matrix (and any entries set before the failure) in place.

---

## 6. compchem semantics used by the engine (binary-only `jar/compchem.jar`)

| compchem API | Used by | Semantics to preserve |
|---|---|---|
| `Atom` | everywhere | fields `symbol`, `number`, `HCount` (default 0), `degree`, `nodeTable[10]`, `bondTable[10]`, `maxBondOrder`, `maxBondOrderSum`, physical properties (`"13C chemical shift"` → `Float`); identity equality; `getBondCount()` = Σ bond orders (4 → 1.5, >4 → 1), truncated to int |
| `StandardElements` | dataset, MDL reader | (maxBondOrder, valence): H 1/1, C 3/4, Si 3/4, N 3/3, P 3/3, O 2/2, S 2/2, F/Cl/Br/I 1/1, X 3/10; unknown → -1 |
| `MFAnalyser(String)` | formula parsing | atoms in formula order (`C15H28O2` → 15 C, 28 H, 2 O); `getSetOfHeavyNodes()` drops H and keeps the order; `getAtomCount("H")` |
| `MFAnalyser(Atom[])` | formula display | rebuilds the formula string: elements in order of first appearance; H (Σ HCount>0) inserted after the first element |
| `DataStructureTools` | engine | `createConnectionTable`, `synchronizeConnectionTable` (upper→lower), `cloneInt2D`, `cloneAtomSet` (physical properties not copied), `synchronizeBonds` (rebuild neighbour lists from matrix, i<j order), `computeMorganMatrix`, `faculty`, `getMass(MF)` (Σ major isotope exact masses incl. H) |
| `PathUtils.isConnected` | engine | BFS from atom 0 |
| `PathUtils.getPathLength(atoms, a, b, cutoff)` | TwoD judges | BFS sphere count from a to b; -1 if not found within cutoff; -1 if a == b |
| `IsomorphismTester` | `notInStock`, `removeIsomorphism` | Morgan heuristic (4.7) |
| `SetOfRingsFinder.findSSSR` | FeatureJudge, jmdraw | Legacy variant of Figueras' algorithm, not a minimum cycle basis: it can return more rings than the cycle rank, and its ring sizes depend on adjacency order. Ring sizes differ from CDK `Cycles.sssr` in 3.0 % of random isomers (29 of 24,277 differ in the 3-/4-ring counts that FeatureJudge scores). It throws `ArrayIndexOutOfBoundsException` on some ring-rich candidates, which ends the run (13 of 30 golden `gui` runs). Re-implement verbatim, including the failure modes (`compchem-semantics.md` section 6). Do not substitute CDK. |
| `HOSECodeGenerator.getHOSECode(atoms, atom, 1)` | HOSECodeJudge | compchem code format (5.2); CDK's `HOSECodeGenerator` produces a different format |
| `BremserOneSphereHOSECodePredictor` | HOSECodeJudge | 651-entry table (648 distinct keys: 3 keys are put twice, and the later `put` wins; see `compchem-semantics.md` section 5) `code → (shift, confidenceLimit, multiplicity, max, min, counts)` from Bremser, Magn. Reson. Chem. 1985, 23, 271; `predict` and `getConfidenceLimit` throw for unknown codes. Port the table verbatim. |
| `ChemFileUtils.writeMDLMol` | results | V2000 molfile: title line, 2 empty lines, counts line `aaabbb  0  0  0  0  0  0  0  0  1 V2000`, all coordinates 0.0, bonds i<j with order 1–3, `M  END` |
| `ChemFileUtils.readMDLMolFile` | results, Load MDL | reads counts, atoms, bonds (stereo codes mapped), and `M  CHG`; sets `HCount` via `saturate()` |
| `ChemFileUtils.readLUCYStructure` | Load LUCY Result | LUCY result structure file (atoms with symbol and H count, bond list with orders) |
| `LIFOQueue(size)` | results | `push` inserts at 0 and truncates to `size`; `pop` removes index 0 |
| `Molecule` | RMI transfer, jmdraw | wrapper around `Atom[]` |

---

## 7. Result post-processing (`StructureGeneratorResult`)

- `sort()`: bubble sort by score descending; swaps adjacent entries only when the next is strictly
  greater (stable for ties); structures and summaries move together.
- `removeIsomorphism()`: `sort()`; then the first loop parses `structures[size-1]` and calls
  `structures.setSize(s - 1)` with `s = size - 1`, i.e. drops the two lowest-scored structures
  (summaries are not truncated). With 0 structures, the loop never terminates (UI freeze); with 1 or 2,
  it throws. Then, for each `i`, it removes every later `j` with equal score that is Morgan-isomorphic
  to `i` (structure and summary). It prints progress to stdout.
- `ShowCurrentAction` merges all runs (`StructureGeneratorResult(nRuns × 30)`; pop/push reverses the
  order), calls `removeIsomorphism()` and then `sort()`, and shows each structure in `StrucDispFrame`
  titled `"Score <score>"` with tooltip `"<html><b>Score <s>: </b>" + description + "</html>"`.

---

## 8. Logging and files (log4j 1.0.4 `Category` API)

Client config `seneca/client/logging.properties`:

| Category | Appender | Target |
|---|---|---|
| root (DEBUG) | A1 `FileAppender` → `System.out`, threshold ERROR, `%p [%t] %c{2} (%M:%L) - %m%n` | console |
| `seneca.structgen` (INFO) | A2 → `${user.home}/seneca.log`, `Append=false`, `%m%n` | file |
| `seneca.structgen.StochasticGenerator.results` (INFO) | A3 → `${user.home}/seneca.results.sdf`, `Append=false`, `%m` | SD file of new best structures |
| `seneca.structgen.StochasticGenerator.log` (INFO) | A4 `org.apache.log4j.gui.TextPaneAppender`, `%m`, option Fancy=false | Logbook tab |
| `seneca.structgen.StochasticGenerator.annealing` (INFO) | A5 → `${user.home}/seneca.annealing.log`, `Append=false`, `%m` | "Log Annealing Data" output (category `...annealing.log` inherits A5) |

Categories are additive: engine log lines also reach `~/seneca.log`. Creating a local server loads
`seneca/engine/logging.server.properties` (the same configuration without A4) into the same JVM; loading it
re-creates A2, A3, and A5 and truncates the three files again. The INFO level suppresses all `debug(...)`
engine messages.

---

## 9. Legacy defects and port decisions (summary)

| # | Defect | Effect in 2001 | Suggested handling |
|---|---|---|---|
| D1 | HybridizationJudge/HeteroBondsJudge `init()` wipes configuration | judges score 0; Hybridization max unreachable | decision: legacy-compatible mode for validation, fixed mode as option |
| D2 | SymmetryJudge max = Σ n!/2 (no ×500) | max far below achievable | as D1 |
| D3 | FeatureJudge max computed before `init()` | max 0 (only penalties anyway) | keep (harmless) |
| D4 | `TwoDSpectrumJudge` "satisfied" counts all rules | description text only | keep text or fix (cosmetic) |
| D5 | `removeIsomorphism` drops 2 lowest, hangs on 0, and throws on 1–2 | Summarize unusable for < 3 results | fix crash/hang; decide on the truncation |
| D6 | Shallow `getResult()` clone and `pop()` on local servers | Summarize drains the local run | use deep copy (RMI semantics) |
| D7 | `getStatus()` called 3× per poll, each drains new log entries | plot loses points | deliver all entries |
| D8 | SpecML writer emits `<molecule>`, reader only reads `<cml:molecule>` | saved atom H counts / shift assignments are ignored on reload (7 of 10 example files) | decision (see inventory) |
| D9 | Judges and their configuration are never saved | configuration lost on save/load | keep (1:1) or extend (optional) |
| D10 | "New Dataset" does not call `sd.init()` and has no schedule | judges not registered, run throws | call `init()`, add default schedule (fix) |
| D11 | Close-with-save writes CML (CMLGenerator), not SML | file cannot be reopened | decision |
| D12 | CarbonShiftAssigner `f += g`, assigner alternative-picking bugs | wrong assignment for multiplicities ≥ 3 / symmetric carbons | legacy-compatible by default |
| D13 | `Math.random()` shared across concurrent runs | non-reproducible | per-run `Random` with identical draw order |
| D14 | compchem ring finder throws (`breakBond` on a degree-0 atom) on ring-rich candidates; can hang if all atoms have degree ≥ 4 | FeatureJudge runs end silently (13 of 30 golden `gui` runs) | decision: emulate (matches golden data) or fix; see `compchem-semantics.md` 6.3 |
| D15 | Reset does not stop the run; Start on a running slot orphans it; Clear followed by Start local can throw (section 3) | orphaned threads, frozen table | stop runs on Reset/restart/Clear (fix) |
| D16 | 2D assigners accumulate into an existing matrix; CarbonShiftAssigner double-counts H for CH2+DEPT90 | config depends on Autoconfigure history | legacy-compatible by default |
