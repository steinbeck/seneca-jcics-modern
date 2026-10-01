# Golden master of the legacy SENECA code — format and provenance

Reference data recorded from the 2001 code base (`legacy/runnable/src`, compiled on JDK 25 by
`legacy/harness/build.sh`) with the harness in `legacy/harness/`. A port must reproduce everything
here exactly, except where a section says otherwise. Regenerate with `legacy/harness/run.sh`;
compare two recordings with `legacy/harness/compare.sh <dirA> <dirB>`.

Two kinds of reference are recorded:

- **Self-consistency** (sections (a)-(d)): what the legacy program does, step by step and to the
  end of a run. A port must match it.
- **Chemistry** (section (e)): the literature structure of each compound, scored by the same
  judges. This is the absolute check: it shows where the judges rank the real compound at the
  top and where they do not.

## Layout

| Path | Content | Part |
|------|---------|------|
| `MANIFEST.jsonl` | one line per dataset key: what was recorded, outcome per profile/seed (no times) | all |
| `PROVENANCE.json` | SHA-256 of every input (legacy source and classes, jars, datasets, harness), JDK, parameters | all |
| `datasets/<key>.echo.jsonl` | the dataset as the legacy reader sees it, and after the GUI recipe | (a) |
| `scores/<key>.scores.jsonl` | judge scores of 50 fixed structures, profiles `gui`, `all`, `manual` | (b) |
| `trajectories/<key>.<profile>.seed<s>.jsonl.gz` | first 5000 annealing steps, profiles `gui` and `paper`, seeds 1-3 (pinene, eurabidiol: 1-10) | (c) |
| `runs/<key>.<profile>.seed<s>.json` | complete run, seeds 1-3, profiles `gui`, `paper` (+ `manual` for pinene, preconen, eurabidiol) | (d) |
| `runs/timing.jsonl` | wall-clock times of the complete runs (the only non-deterministic file) | (d) |
| `references/<key>.ref.jsonl` | literature structure, best labelling and per-judge score in every profile | (e) |

`<key>` is the file name of `legacy/original/sml/<key>.sml` without extension, or a **variant key**
`<name>@<variant>` that reads `<name>.sml` and changes one recipe step. The only variant is
`polycarpol@hblock` (all H counts from the file's `<molecule>` block; see the recipe, step 4).
All 10 files load with the legacy reader; none is unreadable (`sml/pinene.jsx` is not an `.sml`
file, see "pinene.jsx").

## Conventions (all files)

- JSON Lines: one JSON object per line, `\n` line ends, keys in a fixed order, ASCII only
  (non-ASCII escaped as `\uXXXX`). Each record has a type field `"t"`.
- Atom indices are 0-based, in **dataset order** (the order of `sd.setOfNodes`: the order of
  `<formula>` heavy atoms as produced by `MFAnalyser.getSetOfHeavyNodes()`, or the order of
  `<atom>` elements in a `<cml:molecule>` block). Hydrogens are implicit (`h` per atom).
- Java `double` values are printed with `Double.toString`. Java `float` values (shifts,
  intensities, normalize factors, frequencies, `molecularMass`) are printed with
  `Float.toString`; parse them as binary32 to compare exactly (e.g. `116.3` is the float
  `116.3f`, not the double `116.3`). Non-finite numbers are written as the strings `"NaN"`,
  `"Infinity"`, `"-Infinity"`.
- **Signed zero.** `-0.0` and `0.0` are different values and are written as such (`kT`,
  `start_kT`, `final_kT`). A JSON reader that folds `-0.0` into `0` loses information; compare
  these fields as strings or with a sign-aware double comparison. See "Annealing at kT = -0.0".
- `ct` (connection table) = list of bonds `[i, j, order]` with `i < j` and `order != 0`, sorted
  row-major (by `i`, then `j`), read from the upper triangle of the bond matrix.
- `hash` = lowercase hex SHA-1 of the ASCII string `"i,j,o;i,j,o;..."` over exactly the bonds of
  `ct` in the same order, joined by `;` (no trailing `;`; the empty structure hashes `""`).
  It identifies a **labelled** connection table, not a constitution: the same compound with
  another assignment of atoms has another hash. A port must keep the dataset atom order.
- Judge order is always the `sd.judges` order created by `SenecaDataset.init()`:
  HHCOSYJudge, HMBCJudge, HeteroBondsJudge, SymmetryJudge, HybridizationJudge, HOSECodeJudge,
  FeatureJudge. Scores are summed in this order; only enabled judges contribute.
  `WienerIndexJudge` exists in the source but is not in this list, so the GUI and the generator
  never use it; it is dead code and not covered here.
- Text fields copied verbatim from the legacy program (`desc`, `description`, `bestMDL`) depend
  on Java's number formatting (`Double.toString`, e.g. the HOSECode judge's mean deviation, which
  is `NaN` for bn-ye1). A port that formats differently must compare them loosely or skip them;
  all numeric fields carry the same information exactly.

## The GUI recipe (how judges were configured)

The legacy code has no batch mode; every run starts from GUI actions. The harness performs them
headless and in-process with the original classes (`seneca.client.GuiRecipe`):

1. File -> Open: `new SpecMLReader(file).getSenecaDataset()`.
2. The dataset window opens: the 7 judge configurators are constructed in
   `SenecaDatasetFrame` tab order (the real `seneca.client.*JudgeConfigurator` classes; the
   Hybridization/HeteroBonds constructors call `judge.init()` on `sd.setOfNodes`).
3. Atom Properties -> AutoAssign (`CarbonShiftAssigner.assign()`), if no atom has a 13C shift
   after loading (all files without a `<cml:molecule>` block: pinene, eurabidiol, monochaetin,
   polycarpol, bn-ye1, eupatorio, Hyperaspine).
4. Manual H-count edits (Atom Properties table), only where AutoAssign leaves the H count short
   of the formula and the data says where the H belongs:
   - eurabidiol atoms 15, 16 (O) -> 1 H each; polycarpol atoms 30, 31 (O) -> 1 H each.
     Source: the file's own `<molecule>` block (written by the 2001 GUI, but ignored by
     `SpecMLReader`, which only reads `<cml:molecule>`).
     For eurabidiol and monochaetin the block agrees with the configured dataset at every atom.
   - **polycarpol conflict.** Its `<molecule>` block also differs from AutoAssign at three
     carbons (the H sum is 48 in both): atom 13 (39.0 ppm) CH2 in the block, C after AutoAssign;
     atoms 16 (36.8) and 18 (36.3) CH in the block, CH2 after AutoAssign. AutoAssign cannot
     produce the block's values from the file's DEPT data (no DEPT signal at 39.0; 36.8 and
     36.3 are negative in DEPT-135), so they are manual edits by a 2001 user. They are **not**
     used for `polycarpol`: the literature structure (lanosta-7,9(11),24-triene-3β,15α-diol)
     has 4 quaternary sp3 carbons, 5 sp3 CH and 7 CH2, exactly as AutoAssign gives, while the
     block has 3 / 7 / 6 and makes the literature structure unreachable for the generator (it
     never changes H counts). The block's configuration is recorded as the variant
     `polycarpol@hblock` (echo, scores, references, complete runs in profile `paper`), so both
     are available.
   - eupatorio atom 13 (first O) -> 1 H. Harness decision: AutoAssign gives 13 H for C13H14O3
     and the file has no H counts; without the 14th H the heavy-atom valence sum is odd and no
     saturated structure exists. Eupatoriochromene has exactly one OH, so its (element, H)
     classes match this configuration (it is mappable in (e)).
   - bn-ye1 (AutoAssign assigns nothing: no shifts, 0 H) and Hyperaspine (19 shifts, 0 H) have
     no information to repair them; they are used as loaded (all H = 0). Their golden data
     therefore exercises the code, not chemistry, and their literature structures cannot be
     placed on them (see (e)).
5. Judges -> "Autoconfigure Judge" = `JudgeConfigurator.AutoAssignAction`
   (`judge.setEnabled(true); configurator.autoconfigure()`), per profile:
   - `gui`: HHCOSYJudge, HMBCJudge, HOSECodeJudge, FeatureJudge. These are the judges whose
     configuration the GUI derives from the data. SymmetryJudge is not pressed (its classes come
     from accidentally equal shifts); HybridizationJudge/HeteroBondsJudge are not pressed (no
     autoconfigure; the HOSECodeJudge help text says not to combine them with HOSECodeJudge).
   - `paper`: HHCOSYJudge, HMBCJudge, HOSECodeJudge — `gui` without FeatureJudge. Reason: with
     FeatureJudge the legacy ring finder crashes many long runs (finding 1), so a complete run
     of the large datasets exists only without it; the paper reports complete runs on them.
     The paper text does not list the judges per run; this profile is the harness's choice.
   - `all`: all 7 judges (Hybridization/HeteroBonds get the inherited no-op `autoconfigure()`).
     Used to record every judge individually.
   - `manual`: HHCOSYJudge, HMBCJudge, HOSECodeJudge, SymmetryJudge (Autoconfigure), plus hand
     edits of the two table-driven judges, made through the configurators' own table models
     (`HybridizationTableModel.setValueAt`, `HeteroBondsTableModel.setValueAt`), after ticking
     "Activate this Judge?":
     - HybridizationJudge: for every C with a 13C shift, combo value `"1.0"` in column sp2 if
       shift >= 100 ppm, else in column sp3 (stored as 1.0 x multiplicator = 100);
     - HeteroBondsJudge: "hetero-attached?" ticked for every C with 50 <= shift < 100 ppm.
     The rule is a harness choice that exercises these code paths, not a chemical assignment.
     It records quirk Q4: `calcMaxScore()` runs on the GUI tables before serialization, so the
     HybridizationJudge's max (100 per shift-bearing carbon) enters the generator's `maxScore`;
     the generator's `initJudges()` then calls `init()`, which reallocates `scores` and
     `isHeteroBonded`, so both judges score 0 on every structure. The max score is therefore
     unreachable and every `manual` run ends by the reheat limit, never by reaching the max.
     HeteroBondsJudge's own max goes into its shadowing private field (`privateMaxScore`) and
     into `JudgeResult.maxScore`, but `getMaxScore()` returns the base field (0), so it does
     not change the generator's max.
   A 2D judge (HHCOSY/HMBC) that ends up enabled with `assignment == null` (dataset without such
   signals) is unticked: in the GUI, "Start" would throw an NPE in `calcMaxScore()`.
6. Annealing Schedule -> "New ->" (`new AnnealingSchedule()`: 0.8 / 50 / 500 / 0.95) if the file
   has no `<annealingschedule>` (eupatorio, Hyperaspine).
7. "Start as selected" (`SGServerPanel.goAction`): `calcMaxScore()` on every enabled judge of
   the dataset, then serialization copies of `new Molecule(sd.setOfNodes)`, of `sd.judges` (all 7,
   with their enabled flags) and of the schedule clones are handed to a fresh
   `StochasticGenerator` (RMI layer bypassed, as in the port).

The resulting enabled judges per dataset are in `MANIFEST.jsonl` (`guiJudges`, `paperJudges`,
`allJudges`, `manualJudges`) and in the `generatorInput` records of the echo files.

### pinene.jsx

`sml/pinene.jsx` is **not** a session of the 2001 program. It serializes `org.openscience.cdk.*`
classes (648 `BremserOneSphereHOSECodePredictor` elements, `AtomContainer`) and a
`seneca.structgen.ConvergenceAnnealingEngine`, none of which exists in `legacy/original/src` or in
the 2001 jar; its judge list has 4 entries (HHCOSY, HMBC, Symmetry, HOSECode), not the 7 of
`SenecaDataset.init()`. It comes from a later, CDK-based SENECA and is no evidence of the 2001
judge set. Its HMBC assignment and H counts agree with the golden pinene configuration.

## Randomness

All random draws go through `seneca.structgen.Rng` (6 sites in `StochasticGenerator`, 2 in
`SingleRandomStructureGenerator`). For a seed `s` the harness calls `Rng.seed(s)` and then
replaces the private `Random` inside `Rng` by an equivalent `java.util.Random(s)` subclass that
only counts `nextDouble()` calls (identical sequence). `draws` / `rngDraws` / `startDraws` fields
are these counts: the port must consume random numbers in exactly the same number and order
(`java.util.Random(seed).nextDouble()`).

## Annealing at kT = -0.0

`initAnnealing()` sets `start_kT = -changes / Math.log(initialAcceptanceProbability)`, where
`changes` is the `long` mean of `|score difference|` over the 500 calibration evaluations. When
all 500 calibration scores are equal (typically all clamped to 0 by `getScore()`), `changes` is 0,
`-changes` is the integer 0, and `0.0 / log(0.8)` (a negative number) is **-0.0**. In the main
loop `exp = (double) deltaE / current_kT`:

- `deltaE > 0` (worse candidate): `exp = -Infinity`, `Math.exp(-exp) = +Infinity`, and
  `rnd < Infinity` is true: **every worse candidate is accepted**;
- `deltaE == 0`: `0 / -0.0` is NaN, but `deltaE <= 0` accepts anyway;
- cooling multiplies -0.0 by the cooling rate and stays -0.0, so the run is a pure random walk.

At the first reheat, `current_kT + (start_kT - current_kT) * reheatFactor` evaluates to
`-0.0 + 0.0 * f = +0.0`. From then on `exp = +Infinity` for worse candidates, `Math.exp(-Infinity)
= 0`, and **every worse candidate is rejected**. A port must reproduce both regimes bit for bit
(IEEE 754 double arithmetic in the same order, `Math.exp`).
Where this happens: all bn-ye1 and Hyperaspine runs in profile `gui`, bn-ye1 also in `paper`, and
eupatorio `gui` seed 1 (see the `start_kT` columns in `MANIFEST.jsonl`).

## (a) `datasets/<key>.echo.jsonl`

Records in this order:

1. `dataset`: `key, file, title` (the `title` attribute), `formula` (as stored), `description`,
   `molecularMass` (float), `nAtoms`, `shiftsRead` (any 13C shift right after loading, i.e. a
   `<cml:molecule>` block was read), `ownStructure` (always the string "none: ..."; no bundled
   file contains bonds — the literature structures are in `references/`).
2. `atoms` stage `as_read`: per atom `i, el, id` (CML atom id; `""` for formula atoms), `h`
   (HCount), `shift` (float or null), `number`, `maxBondOrder`, `maxBondOrderSum`; plus `hSum`.
3. 7 × `spectrum` stage `as_read`, slots `carbon1D, dept90, dept135, hetcor, hetcorlr, hhcosy,
   noesy` (the `SenecaDataset` fields): `name, specType, dim, nucleus[], frequency, solvent,
   standard, nSignals, signals[]` with per signal `shift[]` (floats, one per axis in `nucleus`
   order), `intensity, normalizeFactor, phase` (0 none, 1 positive, 2 negative), `nucleus[]`.
   All files contain all 7 `<spectrum>` elements; an empty one (no `<axisinfo>`, no signals) is
   read as `dim 0`, `nucleus []`, replacing the constructor default of that slot.
4. `schedules` stage `as_read`: `initialAcceptanceProbability, maxUphillSteps, plateauSteps,
   asymptoticCoolingFactor` per `<annealingschedule>`.
5. `judges` stage `as_read`: the 7 judges after `sd.init()` (summary fields only).
6. For each profile `gui`, `all`, `paper`, `manual`:
   - `recipe`: `autoAssign, scheduleAdded, hOverrides[]` (with reason), `steps[]` (what was done,
     including which 2D judges were unticked and which table cells were edited).
   - only for `gui`: `atoms`, 7 × `spectrum` (note: `CarbonShiftAssigner` rewrites
     `carbon1D` `normalizeFactor`s) and `schedules`, stage `configured_gui`.
   - `judges` stage `configured_<profile>`: the judges exactly as handed to the generator
     (serialized copy, after `calcMaxScore()`, before the generator's `init()`):
     common `name, class, enabled, hasMaxScore, maxScore, multiplicator, ringSetRequired,
     judgeScoreField` (the base-class `Judge.score` field); plus
     - 2D judges: `scores[7]` (index = path length - 1), `assignmentSize`, `assignment` = list of
       `[f, g, h]` with `assignment[f][g][h] == true` in loop order (null if none), `rules`
       (always 0 here: rules are built by `init()`), `numberOf2DSignals`, `cutoff`;
     - HOSECodeJudge: `score` (100), `carbonShifts[]` (doubles; compact list of the shifts of the
       atoms that have one, in atom order; `evaluate` indexes it by atom index);
     - SymmetryJudge: `classes` (atom index lists); its `maxScore` is the sum of k!/2 over the
       classes, while `evaluate` gives 500 per matching pair (wrong units, kept as is);
     - FeatureJudge: `ringSizeScores` `[[size, score], ...]` sorted by size, `hetHetPenalty`,
       `hetHetPenaltyEnabled`, `ringsizeRestrictionEnabled` (false before `init()`, hence
       `maxScore` 0 — quirk Q3);
     - HybridizationJudge: `scores[n][3]` (sp3, sp2, sp) as configured (wiped later by `init()`);
     - HeteroBondsJudge: `score`, `isHeteroBonded[]` as configured, `privateMaxScore` (the
       shadowing field; `getMaxScore()` returns the base field — quirk Q4).
   - `generatorInput`: `enabled[]` and `maxScore` = the generator's `getMaxScore()` (sum of
     enabled judges' `getMaxScore()`, -1 if one has no max score).

## (b) `scores/<key>.scores.jsonl`

- `header`: `structures` (50), `movesPerK` (20), enabled judges and generator max score for the
  `gui`, `all` and `manual` profiles.
- 50 × `structure`, `k = 1..50`:
  - Construction: `Rng.seed(k)`; start structure with the same RNG draws and the same bond
    matrix as `StochasticGenerator.run()` builds it (`SingleRandomStructureGenerator` on a clone
    of the molecule's atoms, `createConnectionTable` + `synchronizeConnectionTable`).
    Differences that do not affect the bond matrix: `run()` hands `generate()` the generator's own
    `Attic` (which `generate()` then aliases to the generator's atoms, and in which it computes
    `attic.apsp`); here a fresh `Attic` is used. Verified: the k = 1 start hash equals the seed-1
    trajectory start hash for every dataset. `startDraws` = draws used so far; then
    `moves = 20*(k-1)` **connected** mutations: repeatedly `mutate()` (the generator's own
    method, same RNG stream) on a copy and keep it only if `PathUtils.isConnected`, until `moves`
    mutations were kept. `hash` and `ct` are the final structure.
  - `gui`, `all`, `manual`: scoring like `StochasticGenerator.getScore()` on that profile's
    judges (serialized copy; `setAttic` + `init()` once on the enabled judges, as
    `initJudges()`; per structure `synchronizeBonds(attic.atomSet, bm)` then `evaluate(bm)` per
    enabled judge). One difference to the generator: `init()` is called on an attic holding an
    unbonded clone of the atoms, the generator calls it on the bonded start structure. No
    recorded judge depends on this (Symmetry/Hybridization/HeteroBonds `init()` use only the
    array length; the trajectory cross-check in (c) re-scores every step with this scorer and
    matches the generator); a judge reading `attic.apsp` (WienerIndexJudge) would differ.
    Per profile: `total` (sum, clamped at 0 as `getScore()` does), `raw` (unclamped sum), and per
    enabled judge `name, score, max` (`getMaxScore()`), `resultMax` (`JudgeResult.maxScore`),
    `prop` (`JudgeResult.propertyValue`; for 2D judges the "satisfied" counter, quirk Q5), `desc`
    (`JudgeResult.scoreDescription`, HTML as shown in the GUI).
  - Judges evaluate independently, so a judge's entry is the same in every profile that enables
    it (the harness asserts this). Under `all` and `manual`, Hybridization and HeteroBonds always
    score 0 because `init()` reallocates their tables (quirk Q4); under `manual` their
    `max`/`resultMax` show the configured maxima. A 2D judge without signals is absent
    (unticked, see recipe). SymmetryJudge is non-zero on random structures only when two atoms
    of a class get equal Morgan numbers (eupatorio); its positive case is recorded in (e)
    (precocene II and eupatoriochromene score 500).
- `skipped` (instead of structures): start structure generation did not finish in 60 s. Did not
  occur.

## (c) `trajectories/<key>.<profile>.seed<s>.jsonl.gz` (gzip -n)

Profiles `gui` and `paper`, seeds 1-3 for every dataset, seeds 1-10 for pinene and eurabidiol
(the two small datasets with a real Metropolis test, so that trajectories exercising acceptance
and cooling are not outnumbered by the degenerate ones: kT = -0.0, quirk Q1, all-zero H).

Recorded by observing the unmodified `StochasticGenerator.run()`: a pseudo-judge (`TraceProbe`)
is appended after the serialized judges. It is enabled, has max score 0, returns score 0 and an
empty description, draws no random numbers, and reads the generator's fields from inside
`getScore()`. It cannot change the search (verified: with an unlimited step limit, pinene `gui`
seed 1 ends after the same 21,248 iterations and 1,753,811 draws as the unobserved run). Its only
side effect is one extra trailing `"\n"` in every `ScoreSummary.description` of these runs,
which is not recorded here. Each step's candidate score is also recomputed with the scorer of (b)
and checked against the generator's own `recentScore` (`checks` in the `end` record; any mismatch
aborts).

Records:

- `meta`: `key, profile, seed, maxSteps` (5000), `enabled[]`, `maxScore`.
- `start`: `hash`, `ct` of the random start structure.
- `init` (500 per schedule): the evaluations of `initAnnealing()`: `sched` (0-based), `i`
  (1..500), `cand` (score), `draws` (RNG draws consumed before this evaluation's return, i.e.
  including this mutation), `hash` of the evaluated structure. Disconnected mutations are not
  evaluated and therefore not listed; they show up in `draws`.
- `schedule`: values in effect after `initAnnealing()`: `start_kT`, `plateauSteps`,
  `maxUphillSteps`, `convergenceStopCount` (= 3 x plateauSteps), `coolingRate`.
- `step` (one per **connected** candidate of the main loop, i.e. per `iteration`):
  - `n`: iteration number (1-based; equals the generator's `iteration` after the step);
  - `kT`: `current_kT` used for this step's Metropolis test (before `cool()`);
  - `cand`: candidate score (clamped total);
  - `acc`: accepted (the candidate became the current structure);
  - `cur`, `best`: `lastScore.score` and `bestScore.score` after the step;
  - `hash`: current structure after the step;
  - `draws`: RNG draws consumed up to this candidate, excluding this step's Metropolis draw
    (`rnd` is drawn for every connected candidate, also downhill ones);
  - `nres`: number of stored results (`LIFOQueue(30)`, newest first) after the step;
  - every 500 steps additionally `ct` of the current structure.
  Disconnected candidates in the main loop do not increment `iteration` and are not listed.
- `end`: `reason` = `step_limit` (5000 steps recorded; the probe then calls `end()`, the 5001st
  step completes and the loop exits), `finished` (run ended earlier), `no_main_step(Q1)` (first
  main-loop mutation disconnected, quirk Q1), `exception_in_run` or `timeout`; `stepsRecorded`,
  `iteration`, `rngDraws` (total at the end), `checks`; and `crash` (see (d)) if `run()` died.

## (d) `runs/<key>.<profile>.seed<s>.json`

Unobserved run: `Rng.seed(s)`, `execute()` (thread start, as the server does), join.
Profiles `gui` and `paper` for every dataset; `manual` for pinene, preconen, eurabidiol (the
effect of the unreachable max score on termination); `paper` only for `polycarpol@hblock`.

**Iteration cap.** A run is capped at 5,000,000 iterations, deterministically: the generator is a
subclass whose `mutate()` calls `end()` (the Stop button) at the first call after `iteration`
reached the cap, then delegates to the inherited `mutate()`. The step in progress completes
(so `iterations` is 5,000,001 when that step's candidate was connected), the main loop exits,
and with one schedule `run()` ends. Such a run has status `iteration_cap` and full data: it is
golden data like any other, and the set of capped runs does not depend on machine speed. A
wall-clock safety net (3600 s) exists only to stop a hung harness; reaching it is a harness
failure (status `timeout`, exit code 5), not golden data. It was not reached.

One `run` record:

- `status`: `finished` (regular end), `iteration_cap` (see above), `no_result(Q1)` (quirk Q1),
  `exception_in_run` (`run()` caught an exception, printed it and stopped; `running` stays
  true), `timeout` (harness failure, no data).
- `iterationCap`; `termination`: for `finished` runs `max_score_reached` (bestScore >= maxScore at
  a convergence check) or `reheat_limit` (converged below the max after the reheat factor reached
  1.0); otherwise equal to `status`.
- `enabled[]`, `iterations`, `rngDraws`, `maxScore` (generator), `start_kT`, `final_kT`,
  `plateauSteps`, `maxUphillSteps` (after reheats).
- `bestScore`, `bestHash`, `bestCt` (parsed back from the generator's `bestStructure` MDL text
  with `ChemFileUtils.readMDLMolFile`; atom order = dataset order), `bestJudges` (per-judge
  breakdown as in (b)), `bestMDL` (the MDL string the generator produced, header line
  `Score: <n>`).
- `crash` (only for `exception_in_run`): `iteration`, `candidateHash`/`candidateCt` (the
  structure being scored when it crashed; it is still in the generator's `attic.atomSet`),
  `currentHash`, and `exception`/`message`/`at` obtained by re-scoring the candidate (it
  reproduces the exception).
- `reference`: `name`, `status`, `mappable` of the dataset's reference structure (e), or
  `name: null` if there is none; `bestEqualsReference` (the best structure has the reference's
  constitution: a bijection of atoms preserving element, H count and bond orders; null without
  reference or without result); `resultsEqualReference[]` (indices `i` of such results).
- `nResults`, `results[]`: the result list in `LIFOQueue` order (newest first, at most 30):
  `i, score, hash, ct, description` (`ScoreSummary.description`).

`runs/timing.jsonl` (sorted): `key, profile, seed, status, wallMs, iterations, iterationCap,
wallCapSeconds, java`.

## (e) `references/<key>.ref.jsonl` — literature structures

Input: `legacy/harness/references.tsv` (key, name, Kekulé SMILES, status, source). Seven
compounds are identified (PubChem constitutions): alpha-pinene (pinene), precocene II
(preconen), eupatoriochromene (eupatorio), monochaetin, nareline (narelin), polycarpol
(lanosta-7,9(11),24-triene-3β,15α-diol; AMAZ.COL), hyperaspine (Hyperaspine). eurabidiol,
triterpen (TRITER.COL) and bn-ye1 are not identified and have no reference. Stereochemistry is
irrelevant (SENECA works on constitutions).

Records:

- `reference`: `key, defined` (false: no reference, nothing else follows); `name, status,
  source, smiles, nAtoms, refAtoms[]` (`i, el, h` in SMILES order), `refCt` (bonds in SMILES
  atom order), `kekuleVariants` (number of Kekulé structures: the perfect matchings of the ring
  double bonds; every SENECA structure is one fixed Kekulé form), `mappable` (the reference's
  multiset of (element, H count) equals the configured dataset's), `classDifference` (what
  differs if not mappable).
- one `best` record per profile `gui`, `paper`, `all`, `manual` (only if mappable):
  `enabled[]`, `maxScore` (generator), `total`, `raw`, `atMax` (total >= maxScore), `kekule`
  (index of the Kekulé variant), `map[]` (reference atom -> dataset atom), `hash`, `ct` (the
  labelled structure in dataset order), `judges` (per-judge breakdown as in (b)), `search`.

**Labelling.** The judges score a labelled structure (shifts and 2D correlations belong to
dataset atoms), so the reference is scored with its best labelling: the maximum of the raw score
over all bijections reference atom -> dataset atom that keep element and H count, and over the
Kekulé variants. The search is simulated annealing on swaps within an (element, H) class
(seed 2001, 8 restarts x 4000 steps per Kekulé variant), then steepest ascent over all swaps.
The result is the best labelling found, a lower bound of the true maximum. Re-checked with
32 restarts x 10,000 steps and another seed: identical totals for every dataset and profile.
A labelling whose scoring throws is counted as -infinity (`search.exceptions`; none occurred).

## Structural features (`MANIFEST.jsonl`)

`bestFeatures` (per run) and `reference.features` list chemically notable motifs, computed from
the connection table by `legacy/harness/summarize.py`: `O-O`, `N-O`, `N-N` (heteroatom bonds),
`cumulated` (an atom with two double bonds: allene, ketene, cumulene), `triple`, `ring3`,
`ring4` (a bond whose smallest ring has 3 / 4 members). They are descriptive only. `O-O`,
`cumulated` and `ring3` are implausible in these natural products; `N-O` occurs in nareline itself,
`ring4` in alpha-pinene itself.

## Results of the recording (2026-10-01, JDK 25.0.2, macOS, Apple silicon 4P+4E cores)

All 10 datasets load; nothing was unreadable. Stages (a), (b), (c), (e) are complete for all keys.
`MANIFEST.jsonl` has every number below per key, profile and seed.

### Complete runs and the reference

Columns (d): seeds 1 / 2 / 3; "it" = iterations, then best score. "crash" = the run died with
the ring-finder exception (finding 1); "Q1" = immediate termination; "(reheat limit)" = ended
below the max after the last reheat; "(**cap**)" = stopped at the 5,000,000-iteration cap;
"kT -0.0" = see "Annealing at kT = -0.0"; "**=ref**" = the best structure is the literature
compound; [..] = implausible features of the best structure (see "Structural features").
"reference" = score of the literature compound's best labelling, profiles `gui` / `paper`.

| key | max | reference (gui / paper) | (d) `gui` | (d) `paper` (no FeatureJudge) |
|-----|----:|-----|-----|-----|
| bn-ye1 | 0 | none | 15,000 it, 0, kT -0.0 [cumulated,ring3]; 15,000 it, 0, kT -0.0 [cumulated,ring3]; crash @ 14,008, best 0, kT -0.0 [O-O] | 15,000 it, 0, kT -0.0 [cumulated,ring3]; 15,000 it, 0, kT -0.0 [cumulated,ring3]; 15,000 it, 0, kT -0.0 [ring3] |
| eupatorio | 1300 | 1300 / 1300 | 5,000,001 it, 1300 (**cap**), kT -0.0; 9,716 it, 1300 [cumulated]; 2,117 it, 1300 [cumulated] | 7,883 it, 1300 [cumulated]; 8,690 it, 1300 [O-O,cumulated,ring3]; 7,638 it, 1300 [cumulated] |
| eurabidiol | 3800 | none | 37,779 it, 3800; Q1; 38,898 it, 3800 | 59,648 it, 3800; Q1; 105,223 it, 3800 |
| Hyperaspine | 1900 | not mappable | 18,996 it, 1700 (reheat limit), kT -0.0; 20,087 it, 1800 (reheat limit), kT -0.0; 19,637 it, 1700 (reheat limit), kT -0.0 [triple] | 328,128 it, 1800 (reheat limit) [O-O,ring3]; 329,967 it, 1800 (reheat limit) [cumulated,ring3]; 327,637 it, 1800 (reheat limit) [ring3] |
| monochaetin | 5000 | 5000 / 5000 | crash @ 8,416, best 2625 [O-O]; crash @ 13,589, best 2930 [O-O,cumulated]; crash @ 7,279, best 2915 [O-O] | 389,964 it, 5000 [O-O]; 415,672 it, 5000 [O-O]; 395,171 it, 5000 [O-O] |
| narelin | 5600 | 5295 / 5300 | crash @ 9,159, best 4215 [cumulated]; crash @ 117,241, best 5400; crash @ 78,275, best 5400 | 2,094,988 it, 5400 (reheat limit); 2,143,068 it, 5400 (reheat limit) [ring3]; 1,958,229 it, 5400 (reheat limit) [N-N] |
| pinene | 2400 | 2100 / 2400 | 21,248 it, 2400; 1,291,603 it, 2305 (reheat limit); Q1 | 21,101 it, 2400 **=ref**; 18,089 it, 2400; Q1 |
| polycarpol | 8000 | 8000 / 8000 | crash @ 86,530, best 4635; crash @ 5,627, best 4020; crash @ 15,537, best 4335 | 761,973 it, 8000; 435,377 it, 8000; 3,090,784 it, 8000 |
| polycarpol@hblock | 8000 | not mappable | - | 5,000,001 it, 7810 (**cap**); 5,000,001 it, 7810 (**cap**); 5,000,001 it, 7810 (**cap**) |
| preconen | 3000 | 2900 / 2900 | 2,148,193 it, 2900 (reheat limit) [cumulated]; 2,504,310 it, 2900 (reheat limit) [cumulated]; 2,334,163 it, 2900 (reheat limit) [cumulated] | 1,571,025 it, 2900 (reheat limit) [cumulated]; 1,517,448 it, 2900 (reheat limit) [cumulated]; 1,579,297 it, 2900 (reheat limit) [cumulated] |
| triterpen | 8700 | none | crash @ 26,583, best 7310; crash @ 6,903, best 5215 [O-O]; crash @ 4,654, best 4535 | 1,120,231 it, 7415 (reheat limit); 1,078,824 it, 7405 (reheat limit); 1,166,482 it, 7405 (reheat limit) |

Reference scores in all profiles ("max" = the reference reaches the generator's max score):

| key | reference | own features | `gui` | `paper` | `all` | `manual` |
|-----|-----------|----------|-----|-------|-----|--------|
| eupatorio | eupatoriochromene (6-acetyl-7-hydroxy-2,2-dimethyl-2H-chromene) | - | 1300/1300 max | 1300/1300 max | 1800/1301 max | 1800/2601 |
| Hyperaspine | hyperaspine |  | not mappable: CH0 +17 in dataset, CH1 +7 in reference, CH2 +8 in reference, CH3 +2 in reference, NH0 +1 in dataset, NH1 +1 in reference | | | |
| monochaetin | monochaetin | - | 5000/5000 max | 5000/5000 max | 5000/5000 max | 5000/6800 |
| narelin | nareline | N-O | 5295/5600 | 5300/5600 | 5295/5601 | 5300/7601 |
| pinene | alpha-pinene | ring4 | 2100/2400 | 2400/2400 max | 2100/2400 | 2400/3400 |
| polycarpol | polycarpol (lanosta-7,9(11),24-triene-3beta,15alpha-diol) | - | 8000/8000 max | 8000/8000 max | 8000/8000 max | 8000/11000 |
| polycarpol@hblock | polycarpol (lanosta-7,9(11),24-triene-3beta,15alpha-diol) |  | not mappable: CH0 +1 in reference, CH1 +2 in dataset, CH2 +1 in reference | | | |
| preconen | precocene II (6,7-dimethoxy-2,2-dimethyl-2H-chromene) | - | 2900/3000 | 2900/3000 | 3400/3001 max | 3400/4301 |

Complete runs in profile `manual` (max score unreachable by construction, see recipe step 5):

| key | max | seed 1 | seed 2 | seed 3 |
|-----|----:|-----|-----|-----|
| eurabidiol | 5300 | 2,068,937 it, 3800, reheat_limit | Q1 | 2,157,246 it, 3800, reheat_limit |
| pinene | 3400 | 1,117,348 it, 2400, reheat_limit **=ref** | 1,186,581 it, 2400, reheat_limit | Q1 |
| preconen | 4301 | 1,739,519 it, 3400, reheat_limit [cumulated] | 1,817,524 it, 3400, reheat_limit [cumulated] | 1,780,481 it, 3400, reheat_limit **=ref** |

### Findings a port must know

1. **FeatureJudge crashes long runs.** `compchem.SetOfRingsFinder.findSSSR` throws
   `ArrayIndexOutOfBoundsException: Index -1` in `breakBond(Atom, Atom[])` (an atom is
   reached with degree 0 while several degree-2 atoms are cut in one pass) for some ring-rich
   candidates. `FeatureJudge.judgeRingScore` calls it on every evaluation;
   `StochasticGenerator.run()` catches the exception, prints it and stops (`running` stays true,
   the GUI shows no error). This ended 13 of 30 `gui` runs (all 12 runs of monochaetin,
   narelin, polycarpol and triterpen, plus bn-ye1 seed 3) and one trajectory (triterpen `gui`
   seed 3, after 4,654 steps). Every crashing candidate is recorded (`crash.candidateCt`);
   re-scoring it reproduces the exception. None of the literature structures triggers it
   (`search.exceptions` = 0 in (e)). A port with a correct ring finder will not crash there;
   whether to emulate the crash is a porting decision. The `paper` profile records the same
   datasets without FeatureJudge.
2. **Quirk Q1 confirmed:** pinene seeds 3 and 7 and eurabidiol seeds 2 and 7 stop immediately
   (0 iterations, no result) in every profile (seed 7 exists only as a trajectory).
3. **The judges do not always rank the real compound highest.**
   - narelin: nareline scores 5300 (`paper`), every complete run ends at 5400 on another
     constitution (HOSECode 2000 instead of 1900); the max 5600 is reached by neither.
   - preconen: precocene II scores 2900/3000 (HOSECode 1200/1300). All `gui` and `paper` runs
     end at 2900 on the same cyclic allene, `COC1=C(OC)C2OC(C)(C)C=CC2=C=C1`; precocene II is in
     the result lists of seeds 2 and 3 with the same score. In `manual`, where SymmetryJudge
     rewards the equivalent gem-dimethyls of both (500), seed 3 ends on precocene II.
   - pinene `gui`: FeatureJudge gives alpha-pinene -300 for its 4-membered ring (2100/2400), so
     it cannot win under `gui`; seed 1 ends on a bicyclo[2.2.1] isomer (alpha-pinene is result 4
     of that run, with 2100). Under `paper` alpha-pinene reaches the max: seed 1 ends on it,
     seed 2 ends on another 2400 isomer and has alpha-pinene as result 1.
   - The max score is degenerate where the reference does reach it: polycarpol, monochaetin and
     eupatorio each have several constitutions at the max, and the run keeps the last one
     found (results are stored on `>=`). All three polycarpol `paper` runs contain
     polycarpol (result 4, score 8000) but end on three other 8000 structures; monochaetin
     `paper` seed 2 contains monochaetin (result 1) but ends on a peroxide; the six eupatorio
     runs end at 1300/1300 on six different constitutions, the five regularly finishing ones
     all with cumulated double bonds (eupatoriochromene itself also scores 1300).
4. **Implausible end structures.** Runs ending on structures with O-O bonds, cumulated double
   bonds or three-membered rings: monochaetin (all `paper` runs: peroxides at 5000/5000),
   eupatorio (all finishing runs: allenes/ketenes/cumulenes, seed 2 `paper` also an O-O bond and
   a three-membered ring, at 1300/1300), preconen (cyclic allene, all `gui`/`paper` runs),
   narelin `paper` seed 2 (three-membered ring) and seed 3 (N-N bond), the best structures
   before the crash of monochaetin `gui` (O-O) and triterpen `gui` seed 2 (O-O), and
   bn-ye1/Hyperaspine (chemically meaningless inputs).
   These are what the legacy program produces and therefore golden; they are not port errors.
5. **eurabidiol** has no literature identification. All 6 finishing `gui`/`paper` runs and the
   finishing `manual` runs end on the same constitution at 3800/3800 (bicyclic, a
   5-ring fused to a 7-ring, i.e. a guaiane skeleton with a tertiary OH in the 7-ring and a
   2-hydroxy-2-propyl group); this is a consistency observation, not a reference.
6. **Unreachable max and termination (`manual`).** With HybridizationJudge configured the max
   is unreachable (quirk Q4), so every `manual` run ends by the reheat limit after
   1.1-2.2 M iterations. In `paper` the same seeds end at the max after 18,089-21,101 (pinene)
   and 59,648-105,223 iterations (eurabidiol); preconen ends by the reheat limit in both
   profiles (1.5-1.6 M in `paper`, 1.7-1.8 M in `manual`). For
   pinene the `manual` and `paper` runs are identical up to the first convergence check
   (Hybridization and HeteroBonds score 0, pinene has no symmetry class), so the difference is
   the termination alone.
7. **kT = -0.0 runs:** all bn-ye1 runs, Hyperaspine `gui` and eupatorio `gui` seed 1. bn-ye1 has
   no shifts (max score 0): every run except the crashing `gui` seed 3 ends at the first
   convergence check, after 3 x plateauSteps = 15,000 iterations, with score 0. Hyperaspine `gui` ends by the reheat
   limit after about 19,000 iterations (the first reheat turns kT into +0.0, after which every
   worse candidate is rejected). eupatorio `gui` seed 1 is a pure random walk: every candidate
   is accepted, the convergence counter is reset by every improvement, and the run never
   converges; it is stopped by the iteration cap (best 1300 = max, reached early and kept).
8. **polycarpol@hblock** (all H counts from the file's `<molecule>` block): the max 8000 is
   unchanged, but polycarpol cannot be built (its H classes do not match), and all three
   `paper` runs reach only 7810 and are stopped by the iteration cap.

### polycarpol compared with the paper

Paper (Steinbeck 2001, Table 1): about 350,000 iterations, 12 min on a 600 MHz machine.

- `paper` profile (HHCOSY, HMBC, HOSECode) with the AutoAssign H counts: 761,973 / 435,377 /
  3,090,784 iterations for seeds 1 / 2 / 3, all ending at the maximum score 8000/8000. Every
  run has polycarpol itself in its result list at 8000 (result 4); it was found before the
  final best and is the literature compound, as reported in the paper. Seed 2 is close to the
  paper's iteration count; the spread across seeds is large (factor 7).
- Wall time is not golden data (`runs/timing.jsonl`); single-JVM throughput on this machine is
  about 12,000 iterations/s, roughly 24 times the paper's ~490 iterations/s.
- `gui` profile (with FeatureJudge): all three seeds crash early (86,530 / 5,627 / 15,537
  iterations, best 4635 / 4020 / 4335). The paper's run cannot have used FeatureJudge in this
  form.
- With the `<molecule>` block's H counts (`polycarpol@hblock`) polycarpol is unreachable (see
  finding 8), so the 2001 author's successful run used H counts like AutoAssign's.

### Determinism and provenance

The whole recording was made twice, concurrently, in separate JVMs (`run.sh` into `golden/` and
`GM_NO_BUILD=1 run.sh legacy/harness/work/run2`, 4 parallel jobs each) and compared with
`compare.sh`: all 195 files (everything except `runs/timing.jsonl` and this file, including
`PROVENANCE.json` and the gzip files) are byte-identical. All runs that existed in the previous
recording reproduce their iteration counts and best scores exactly; the only change is that the
former wall-clock-skipped eupatorio `gui` seed 1 is now an `iteration_cap` record with data.
`PROVENANCE.json` holds the SHA-256 of the legacy source tree, the compiled legacy classes
(identical to a build by `legacy/runnable/build.sh`), every jar and dataset, the harness source,
classes and scripts, the JDK and the recording parameters. Check it before trusting a
re-recording: if a hash differs, the inputs differ.

**Observer cross-checks.** The trajectory probe does not change the search: pinene `gui` seed 1
observed without step limit ends after 21,248 iterations and 1,753,811 draws, identical to the
unobserved run; eupatorio `gui` seed 3 (trajectory ends `finished` after 2,117 steps) and
triterpen `gui` seed 3 (trajectory ends with the crash after 4,654 steps) match their unobserved
runs exactly. Every recorded step's candidate score was recomputed independently with the scorer
of (b) and equals the generator's own score (`checks` in each `end` record).

## Regenerating

```
legacy/harness/run.sh                      # all stages into golden/ (~50 min with a concurrent 2nd recording)
legacy/harness/run.sh /some/other/dir      # e.g. a second recording
legacy/harness/compare.sh golden /some/other/dir
STAGES="echo scores ref" DATASETS="pinene" legacy/harness/run.sh /tmp/x   # subset
```

`run.sh` always rebuilds: `build.sh` compiles `legacy/runnable/src` into the private directory
`legacy/harness/legacy-classes` (same flags as `legacy/runnable/build.sh`) and the harness into
`legacy/harness/classes`. It then runs every (dataset, stage, profile, seed) in its own JVM via
`gm.sh` (`-Djava.awt.headless=true`, `-Duser.home=legacy/harness/work/home`, so the legacy code
writes nothing into the real home directory). Console output of the legacy code (it prints
identity hash codes and is therefore not reproducible) goes to `legacy/harness/work/logs/`.
`summarize.py` writes `MANIFEST.jsonl`, `provenance.py` writes `PROVENANCE.json`.
