# SENECA 2001 – Feature Inventory (1:1 checklist for the port)

Every user-visible function of the legacy client (`seneca.client.Seneca`), with its exact behaviour
and defaults, marked **KEEP** (must exist in the port), **KEEP-ADAPT** (keep the function, adapt the
mechanics), **DROP** (RMI/distributed only, or never reachable) and, where the legacy behaviour is
buggy, a pointer to the defect list in `architecture.md` §9 (D1–D13). Engine and judge semantics are
specified in `architecture.md` §4–§6; this file covers the GUI, files and resources.

IDs (F-xx) are for cross-referencing in later phase plans.

---

## 1. Startup and command line

| ID | Item | Legacy behaviour | Mark |
|---|---|---|---|
| F-01 | Launch | `java -Dseneca.home=. -jar seneca-client.jar` (`bin/seneca.sh`, `bin/Seneca.bat`); main class `seneca.client.Seneca` | KEEP-ADAPT (macOS `Seneca.app`) |
| F-02 | `args[0]` | If present, opened as SML dataset at startup (`openFile`); on failure stderr "Could not opoen the file referenced by the command line parameters" | KEEP (also macOS "Open With"/file association) |
| F-03 | `-Dseneca.home` | Read into `Seneca.senecaHome`, otherwise unused | DROP |
| F-04 | Resource bundle | `seneca/client/Seneca.properties` missing → stderr "Seneca.properties not found", `System.exit(1)` | KEEP-ADAPT (bundled resource) |
| F-05 | Initial look&feel | Cross-platform (Metal) | KEEP-ADAPT (Metal or macOS Aqua; decide in UI phase) |
| F-06 | Logging setup | log4j from `seneca/client/logging.properties`; truncates `~/seneca.log`, `~/seneca.results.sdf`, `~/seneca.annealing.log` at startup | KEEP-ADAPT (see §8) |
| F-07 | Java version check | Prints a warning if `java.version < "1.1.2"` | DROP |

## 2. Main window

| ID | Item | Legacy behaviour | Mark |
|---|---|---|---|
| F-10 | Frame | Title "Seneca"; content = `JDesktopPane` (BorderLayout), preferred 800×600, centered on screen; close box → exit confirmation (F-23) | KEEP |
| F-11 | Toolbar | Buttons (icons only) `new` (images/new.gif, tooltip "Initialize a new dataset"), `openAll` (images/open.gif), `saveAll` (images/save.gif) | KEEP |
| F-12 | Status bar | 3 lowered-bevel labels, 640×30: (1) current dataset name or "No dataset available"/"No dataset loaded", (2) its molecular formula, (3) unused | KEEP |
| F-13 | Dataset frames | Each open dataset is a `JInternalFrame` added to the desktop's CENTER (fills the desktop; the last opened is on top) | KEEP-ADAPT (one dataset view per window/tab) |

### 2.1 Menus (built from `Seneca.properties`; "disabled" = no action registered for the item)

| ID | Menu path | Label | State / action | Mark |
|---|---|---|---|---|
| F-20 | File ▸ New Dataset | "New Dataset" (icon new.gif) | creates `SenecaDataset("untitled" + new Date().hashCode())` and opens its frame. Does **not** call `sd.init()` → no judges registered, no schedule (D10) | KEEP (fix D10) |
| F-21 | File ▸ Open ▸ SENECA Dataset | "SENECA Dataset" (open.gif) | `JFileChooser` at `filelocation.seneca` (default "/"), filter `*.sml` "SENECA Dataset"; stores parent dir in `~/seneca.props`; reads SML (§7.1), opens frame | KEEP |
| F-21a | File ▸ Open ▸ (item `openNodeProps`) | no label | disabled | DROP |
| F-22 | File ▸ Save ▸ SENECA Dataset | "SENECA Dataset" (save.gif) | Save dialog, filter `*.sml`; writes the **current** dataset (Datasets menu selection) as SML (§7.1) to the chosen file verbatim (no extension added, no overwrite prompt); IOException → stdout | KEEP |
| F-24 | File ▸ Import ▸ LUCY dataset | "LUCY dataset" | chooser at `filelocation.lucy`, filter `*.col` "Lucy datasets"; LUCY import (§7.3); opens frame | KEEP |
| F-25 | File ▸ Export | empty submenu | – | DROP |
| F-26 | File ▸ (print) | item without label, enabled, does nothing | – | DROP |
| F-23 | File ▸ Exit | "Exit" | confirm dialog title "Exit Seneca", text "Do you really want to exit Seneca?" Yes/No → `System.exit(0)` | KEEP (macOS Quit) |
| F-27 | Edit ▸ Copy / Cut / Paste | disabled (action names mismatch) | – | DROP |
| F-28 | Edit ▸ Compound Properties, Spectra ▸ {1D NMR Data, CH Corr, CH long range, HH COSY, NOESY}, Connectivity Matrix, Node Table, Annealing Properties, Score Evaluators ▸ Bond Orders | all disabled | – | DROP (functions live in the dataset frame tabs) |
| F-29 | Edit ▸ Preferences ▸ Look&Feel ▸ Windows / Motif / Java / Metal | enabled; class names for Windows, Motif, Java are wrong → stderr "Could not locate requested look&feel"; Metal works (`updateComponentTreeUI` + `pack`) | KEEP-ADAPT (offer the L&Fs that exist on macOS) |
| F-30 | View ▸ Current Structures | "Current Structures" (strucDisp.gif) | opens an empty Structure Display window 600×400 (§6) | KEEP |
| F-31 | View ▸ Status of Generator Threads | disabled | – | DROP |
| F-32 | Calculate ▸ Start | disabled | – | DROP (start is in the Structure Generation tab) |
| F-33 | Datasets ▸ switch ▸ <dataset names> | rebuilt on every collection change; selecting a name makes it the current dataset (status bar, target of Save). Does not raise its frame | KEEP |
| F-34 | Help ▸ About Seneca | `JOptionPane` "About Seneca": "SENECA 0.8b / (c) 1998, 1999, 2000 by Dr. Christoph Steinbeck / Max-Planck-Institute of Chemical Ecology, Jena / E-Mail: steinbeck@ice.mpg.de / Please check http://seneca.ice.mpg.de/~stein / for more!" | KEEP-ADAPT (update version/credits text, keep original credit) |

## 3. Dataset frame (`SenecaDatasetFrame`)

Internal frame titled with the dataset name; resizable, closable, maximizable, iconifiable;
preferred 500×300; "Close" button at the bottom. Tabs in this order:

| ID | Tab | Mark |
|---|---|---|
| F-40 | General Settings | KEEP |
| F-41 | NMR Spectra | KEEP |
| F-42 | Atom Properties | KEEP |
| F-43 | Annealing Schedule | KEEP |
| F-44 | Judges | KEEP |
| F-45 | Serverlist | DROP |
| F-46 | Structure Generation | KEEP-ADAPT |
| F-47 | Logbook | KEEP |

| ID | Item | Legacy behaviour | Mark |
|---|---|---|---|
| F-48 | Close dataset | Close button or close box → `YES_NO_CANCEL` dialog "Close <name>?" / "You are about to close this dataset.\nDo you want to save your changes?". **Yes** → `JFileChooser` save → writes **CML** via `CMLGenerator` (§7.2), then removes the dataset; **No** → removes without saving; **Cancel** → nothing | KEEP-ADAPT (D11: decide whether "Yes" saves SML) |

### 3.1 General Settings (F-40)

Two-column table "Parameter" / "Setting", column 2 editable:

| Row | Shows | On edit |
|---|---|---|
| Filename | `sd.getName()` | `sd.setName()`, frame title updated |
| Description | `sd.getDescription()` (default "none") | `setDescription` |
| Molecular Formula | `sd.getMolecularFormula()` | `setMolecularFormula(MF)`: **regenerates `setOfNodes`** from the formula (heavy atoms in formula order, HCount 0, no shifts) |
| Molecular Mass | stored mass, or if 0: `DataStructureTools.getMass(MF)` (Σ major-isotope exact masses incl. H) | `setMolecularMass(Float)` |

Description and Molecular Mass are **not saved**: the SML writer omits both (7.1), so they are lost
on save and reload.

### 3.2 NMR Spectra (F-41)

Sub-tabs: **1D Carbon** (`carbon1D`), **DEPT-90**, **DEPT-135**, **H,C-HetCor** (`hetcor`, 1JCH),
**H,C-lr-HetCor** (`hetcorlr`, HMBC), **H,H-COSY** (`hhcosy`). NOESY is stored/saved but has no tab
(KEEP data, no UI needed).

Each sub-tab (`NMRSpectrumTablePanel`):

| Element | Behaviour |
|---|---|
| North label | `"<n> Signals in <specType>."` (after changes: lower-case "signals") |
| Table | Columns: `No.` (1-based), one column per axis titled with the nucleus (`13C`, `1H`), `Intensity`, `Phase` (combo NONE / POSITIVE / NEGATIVE). All cells editable (shift/intensity as Float). For 2D spectra the axis order is as stored (1JCH/HMBC files: f1 = 1H, f2 = 13C; signals created by the GUI use the spectrum's nucleus order) |
| Add Signal | appends an empty signal (all shifts 0, intensity 0, phase NONE) |
| Delete Signal | removes the selected row |
| Combo "Import peak picking" | "Paste WinNMR pp from clipboard" (§7.6); "Import GIFA pp from file" (no-op, code commented out → DROP); resets to item 0 |

### 3.3 Atom Properties (F-42)

| Element | Behaviour |
|---|---|
| Table "Atom Properties" | rows = heavy atoms; columns `No.` (1-based), `Symbol`, `Hydrogens` (editable, accepts 0–4, shows "undefined" for −1), `Assigned 13C Shift` (editable Float, `"-"` if none) |
| Table "Carbon Shifts" | rows = 13C signals; columns `[ppm]`, `Int.` (intensity × normalizeFactor), `DEPT-90`, `Int.`, `DEPT-135`, `Int.` (row k shows the k-th DEPT signal, not aligned by shift; "-" beyond the list) |
| Button "Autoassignment" | `CarbonShiftAssigner.assign()` (architecture §5.8; D12) |
| Message label | `"HCount in molecular formula: X - Sum of HCounts in this table: Y"`; red if X ≠ Y; dark green if equal (then all negative HCounts are set to 0) |

README tutorial (Eurabidiol): Autoassignment leaves 2 H unassigned (red); user types 1 into the
Hydrogens column of O-16 and O-17 → green. The port must support this workflow.

### 3.4 Annealing Schedule (F-43)

| Element | Behaviour / default |
|---|---|
| Tab "Asymptotic Cooling" | fields `Initial Accept. Prob.:`, `Plateau Steps:`, `Cooling Factor:`, `max. Uphill Steps:` (shown as double, e.g. "50.0") |
| List "Schedules" | `sd.annealingSchedules`, entries rendered `A-F:<cool>-IAP:<p0>-PS:<plateau>-UHS: <uphill>`; selecting fills the fields; last entry selected initially |
| `-> Update ->` | parse fields (Double; plateau/uphill truncated to long) into the selected schedule |
| `Remove <-` | removes the selected schedule from the `Vector`. The `JList` reads the `Vector` directly (so the repaint shows the shorter list) but gets no removal event, so the selection index stays numerically the same. Then, if that index is > 0, the code calls `setSelectedIndex(index − 1)` twice: the selection moves back **two** places, the fields reload from that schedule, and they are written back into it unchanged (no-op). From index 1 the second call asks for −1, which `DefaultListSelectionModel` ignores on JDK 25, so the selection ends at 0 and nothing throws. (A selection model that accepted −1 would make `elementAt(−1)` throw.) From index 0 the selection stays at 0, which now denotes the next schedule, but the fields still show the removed one, so a following Update overwrites the next schedule with them. Removing the only schedule leaves selection 0 on an empty list, and Update then throws `ArrayIndexOutOfBoundsException`. Behaviour checked with a `JList(Vector)` on JDK 25. Port: fix (select the neighbour, reload the fields). |
| `New ->` | appends `AnnealingSchedule()` with defaults **p0 = 0.8, plateauSteps = 500, asymptoticCoolingFactor = 0.95, maxUphillSteps = 50** and selects it |
| Semantics | schedules run one after another in list order within one run (architecture §4.5); at least one schedule is required (otherwise the run fails, D10) |

### 3.5 Judges (F-44)

Sub-tabs (label → judge): **H,H-COSY** → HHCOSYJudge; **C,H-long-range Corr.** → HMBCJudge;
**Carbon Shifts** → HOSECodeJudge; **Symmetric Atoms Classes** → SymmetryJudge; **Features** →
FeatureJudge; **Hybridization** → HybridizationJudge; **Heterobond Attachments** → HeteroBondsJudge.

Common bottom row (all sub-tabs):

| Element | Behaviour |
|---|---|
| "Autoconfigure Judge" (tooltip "Autoconfigures <JudgeName>") | `judge.setEnabled(true)`, then judge-specific autoconfigure |
| "Report Judgeconfig" (tooltip "Opens a window with judge configuration in ASCII output") | 2D judges: `JFrame` "Judge Configuration Report" with `toString()` ("Configuration of TwoDSpectrumJudge <name>:", "Listing relation of nuclei by number...", lines `f-h` (0-based), "End of listing"); other judges: prints "dummy" to stdout (no window) |
| Checkbox "Activate this Judge?" | `judge.setEnabled(selected)`; reflects programmatic changes |

Judge-specific center panels and defaults (scoring in architecture §5):

| ID | Judge | Center panel | Autoconfigure | Defaults |
|---|---|---|---|---|
| F-50 | HHCOSY | table titled with judge name: rows = assigned correlations (`[i][j][j]`), columns `No.` (atom i+1) and `1..n` booleans (`assignment[i][j][col-1]`); read-only | `HHCOSYAssigner` from H,H-COSY + 1JCH spectra; requires all carbons to have shifts; failure → judge disabled, "Assignment failed." on stdout, previous matrix kept. Never clears an existing matrix: a second Autoconfigure adds to the old correlations (architecture §5.8) | scores {100}, cutoff 1 |
| F-51 | HMBC | same table | `HMBCAssigner` from CH long-range + 1JCH spectra (same preconditions; uncaught exception if no 1JCH partner). Accumulates like F-50 | scores {100,100,5}, cutoff 3 |
| F-52 | Carbon Shifts (HOSE) | read-only HTML text: "HOSECodeJudge uses about 600 1-sphere HOSE codes … You should not use this Judge in conjunction with HybridizationJudge and HeteroBondsJudge …" | copies assigned 13C shifts (atoms with a shift, in atom order) into `carbonShifts` | 100 per carbon |
| F-53 | Symmetry | table: rows = classes; columns `No.`, `Symb.`, `Shift`, then one boolean column per atom titled `<symbol><index+1>`; read-only (edits ignored). Quirk: `Symb.` shows the symbol of atom number `row` (the class index), not of a class member | clears classes; builds classes of atoms with identical assigned shift (architecture §5.3). Quirk: an atom without a shift inherits the previous atom's shift and can land in a class with later atoms of that shift | 500 per matching pair |
| F-54 | Features | table "Ringsize Scores" (`Ring Size`, `Score`, read-only); checkbox "Hetero-Hetero Atom Bonds forbidden" (toggles het-het penalty) | ring 3 → −500, ring 4 → −300, het-het penalty on | hetHetPenalty −5 (no UI to change). With ring scores set, the legacy ring finder can throw and silently end a run (architecture D14) |
| F-55 | Hybridization | table `No.` (0-based), `Symb.`, `Shift`, `sp3`, `sp2`, `sp`; sp columns edited via combo 1.0, 0.9 … 0.0 (stored ×100) | none ("dummy") | all 0; D1 |
| F-56 | Heterobond Attachments | table `No.` (0-based), `Symb.`, `Shift`, `hetero-attached?` (checkbox) | none ("dummy") | all false; score 100; D1 |

Judge configuration is **not** persisted in SML (D9). After loading, all judges are disabled and
unconfigured.

### 3.6 Serverlist (F-45) – DROP

Table `No.`, `Running on Host`, `Response`, `Speed` over `sd.serverList` (from SML `<serverinfo>`),
HTML pane "Current Server List", buttons "Clear List", "Get Serverlist" (RMI GateKeeper
`www.nmrshiftdb.org`). Port: may read and ignore `<serverinfo>`; no UI.

### 3.7 Structure Generation (F-46)

Layout: north = empty text field (never written; DROP); center-top = run table + chart
"Annealing Data"; center-bottom = best-structure drawing + "Current Structure Evaluation" HTML pane;
south = three titled groups.

| ID | Element | Legacy behaviour | Mark |
|---|---|---|---|
| F-60 | Run table | columns `No.`, `ID`, `Host`, `Dataset`, `MF`, `Status `, `Iter.`; yellow cells; multi-row selection; before start: Dataset/MF "none", Status "idle", Iter "none" | KEEP-ADAPT (Host column may show "local") |
| F-61 | Server Admin ▸ "Start local server" | adds an in-process generator slot with ID `local<k>`. After "Clear Server List" of 2+ slots it throws in `updateSgs()` (stack trace on stdout), the slot is added anyway and the poll timer stays stopped (architecture §3) | KEEP-ADAPT ("add run"; fix) |
| F-62 | Server Admin ▸ "Update server list" | RMI scan via GateKeeper | DROP |
| F-63 | Server Admin ▸ "Clear Server List" | removes all slots (running threads are not stopped); keeps the old status array, which breaks the next F-61 | KEEP (should also stop runs) |
| F-64 | Selections ▸ "Start as selected" | for each selected slot: `calcMaxScore()` on enabled judges, deep copies, start run (architecture §3). On a slot that is already running it creates a second generator; the first keeps running, orphaned | KEEP (stop the old run first) |
| F-65 | Selections ▸ "Stop Selected" | `end()` on selected runs | KEEP |
| F-66 | Selections ▸ "Reset selected" | clears all chart data; drops the reference to the generator of selected slots (`stoicGent = null`, no `end()`): the thread keeps running, logging and appending to `~/seneca.results.sdf` | KEEP (stop the run) |
| F-67 | Result ▸ "Summarize Result" | merge results of all runs, `removeIsomorphism()`, `sort()`, open Structure Display with one drawing per structure (title `Score <s>`, tooltip HTML evaluation incl. `Result from host <i>`) (D5, D6) | KEEP |
| F-68 | Result ▸ "Log Annealing Data" | writes table `Iteration; Server 1; Server 2; …` then rows `<x>; <score1>; <score2>; ` to stdout and `~/seneca.annealing.log` (one per line) | KEEP |
| F-69 | Polling | every 2000 ms one slot (round robin): status → table; best structure drawn ("Best structure of no.<k>", or "No structure available"); HTML "Evaluation of no. <k>": `<b>Score: <s>[/<max>]</b>` + judge descriptions | KEEP |
| F-70 | Annealing chart | JFreeChart XY line chart, tiny empty title; x axis "Iteration", y axis "Score"; one series per run "host <i>" plus "max. Score" (horizontal line at the last polled max score, score mode only); **mouse click toggles** y between best score and kT (axis label "kT") | KEEP |

### 3.8 Logbook (F-47)

Scrollable text pane (white) receiving engine INFO messages (`%m`, no timestamps): start time,
`start_kT = …`, `Iteration: <n> ` + `Current kT = …` every 1000 iterations, `New best Structure: …`,
convergence/reheat/abort messages, stop time, runtime, "Simulated annealing run finished.". One
shared pane for the whole application (shown in the most recently created dataset frame). KEEP.

## 4. Dialogs and windows outside the dataset frame

| ID | Item | Mark |
|---|---|---|
| F-23 | Exit confirmation | KEEP |
| F-34 | About | KEEP-ADAPT |
| F-48 | Close-dataset save prompt | KEEP-ADAPT |
| F-80 | `NodeTableFrame` "Atom Properties" dialog (No., Symb., Max.Bond, Max.Bond.Sum, HCount) | DROP (unreachable) |
| F-81 | `JFrame` "Judge Configuration Report" | KEEP |

## 5. Result display

| ID | Item | Legacy behaviour | Mark |
|---|---|---|---|
| F-85 | Structure drawing | jmdraw: 2D layout computed by `JMDraw.draw()` (sets atom x/y; ring and fused-ring templates), rendered by `RenderingEngine` scaled to 80 % of the panel and centered, white background, implicit H shown on labelled atoms. Display only, **no editing** anywhere in SENECA | KEEP-ADAPT (CDK layout + depiction) |
| F-86 | Best structure panel | 200×300 in the Structure Generation tab | KEEP |
| F-87 | Evaluation HTML | `JEditorPane` with HTML kit, read-only | KEEP |
| F-88 | Annealing chart | see F-70 | KEEP |

## 6. Structure Display window (`StrucDispFrame`, F-30 / F-67)

| ID | Element | Legacy behaviour | Mark |
|---|---|---|---|
| F-90 | Window | title "Structure Display", scroll pane 600×400; flow layout of 300×200 tiles, each with titled etched border (`Hit no. <k>`, `Score <s>`, or file name) | KEEP |
| F-91 | Load MDL | multi-select `JFileChooser`; each file read with compchem MDL reader (§7.4), one tile per file titled with the file name | KEEP |
| F-92 | Load LUCY Result | multi-select; each file read by `readLUCYStructure` (§7.5) | KEEP |
| F-93 | Save MDL | AWT `FileDialog` "Save MDL MOL Files"; trailing `.mol` stripped from the entered name; writes every tile as `<dir>/<name><index>.mol` (index 0-based, title line = file name, coordinates from the drawing layout) | KEEP |
| F-94 | Close | disposes the window | KEEP |
| F-95 | "Remove Isomorphism" | button created but never added to the panel | DROP (not visible) |

## 7. File formats

### 7.1 SENECA dataset, SpecML (`.sml`) – read (KEEP) and write (KEEP)

Writer (`SpecMLGenerator.convert`), two-space indent:

```xml
<?xml version="1.0" ?>
<senecadataset title="EURABIDIOL.COL">
  <formula>C15H28O2</formula>
  <molecule xmlns="http://www.xml-cml.org/cml.dtd" title="ConnectionTable">
    <atom id="a0">
      <string builtin="elementType">C</string>
      <integer builtin="hydrogenCount">0</integer>        <!-- only if HCount >= 0 -->
      <float title="assignedCarbonShift">74.3</float>     <!-- only if assigned -->
    </atom>
    …
    <bond id="b2" atomRefs="a0 a1"><string builtin="order">1</string></bond>  <!-- only if atoms have bonds; ids start at b2 -->
  </molecule>
  <spectrum xmlns="http://www.nmrshiftdb.org/" type="NMR Experiment" id="hetcorlr" convention="Seneca">
    <spectruminfo>
      <dimension>2</dimension>
      <type>CH long-range correlation</type>
      <frequency unit="MHz">0.0</frequency>
      <solvent>null</solvent>
      <standard role="calibration"></standard>
      <axisinfo role="f1"><nucleus>1H</nucleus><property>shift</property><unit>ppm</unit></axisinfo>
      <axisinfo role="f2"><nucleus>13C</nucleus><property>shift</property><unit>ppm</unit></axisinfo>
    </spectruminfo>
    <signal id="hetcorlr.p0">
      <location role="f1">1.35</location>
      <location role="f2">74.3</location>
      <intensity type="relative">1.0</intensity>
      <normalizefactor>1.0</normalizefactor>
      <phase>1</phase>
    </signal>
  </spectrum>
  … (7 spectra in order carbon1d, dept90, dept135, hetcor, hetcorlr, hhcosy, noesy; axisinfo only if the spectrum has signals)
  <annealingschedule>
    <initialAcceptanceProbability>0.8</initialAcceptanceProbability>
    <maxUphillSteps>50</maxUphillSteps>
    <plateauSteps>500</plateauSteps>
    <asymptoticCoolingFactor>0.95</asymptoticCoolingFactor>
  </annealingschedule>
  <serverinfo><hostname>…</hostname><responseTime>…</responseTime><speed>…</speed></serverinfo>
</senecadataset>
```

Spectrum `type` strings (identify the slot on reading): `Carbon1D`, `DEPT-90`, `DEPT-135`,
`1JCH correlation`, `CH long-range correlation`, `H,H-COSY`, `NOESY`. Phase codes 0 NONE,
1 POSITIVE, 2 NEGATIVE. Judges are not written (D9). No XML escaping of values.

Reader (`SpecMLReader`, DOM, non-namespace-aware, element names compared lower-case):

- root found by recursive search for element `senecadataset`; `title` → dataset name.
- `formula` text → `setMolecularFormula` (creates heavy atoms from the formula).
- **`cml:molecule` only** → atoms (`id`, `string builtin="elementType"`, `integer builtin="hydrogenCount"`,
  `float title="assignedCarbonShift"`; HCount defaults to −1 when absent); bonds are ignored.
  The writer's `<molecule>` element is **not** read (D8): for such files atoms come from the formula
  (HCount 0, no shifts).
- `spectrum`: axes from `axisinfo role="f1".."f5"`, `type`, `frequency`, `solvent`; signals
  `location role=fN`, `intensity`, `normalizefactor`, `phase`. `standard` is read but dropped.
- `annealingschedule`: the four fields above; unknown legacy children (`noOfSteps`,
  `coolingCharacteristic`, `linearCoolingStepsize`, `useStructure`, `converge`) are ignored, missing
  fields keep defaults.
- `serverinfo` → `sd.serverList` (DROP in port: parse and ignore or keep for round trip).
- `judge` elements are ignored (`readJudge` is commented out). `pinene.sml` is the only example file
  with them: 17 `<judge>` elements covering all 7 judges, including per-atom `judgeScore`
  (`atomref`, `role` SP3/SP2/SP) entries for HybridizationJudge.
  `HybridizationJudgeConfigurator.getJudgeFromXML` exists, but it is never called and reads only the
  SP3 role. Relevant for D9 (whether to persist judge configuration).
- Not written by `SpecMLGenerator.convert`: the dataset description and the molecular mass (only
  title, formula, molecule, spectra, schedules and serverinfo are written; the judge loop is
  commented out). Both are lost on save and reload.
- After parsing: `sd.init()` (creates the 7 disabled judges).

Example datasets in `legacy/original/sml/` (all 1D/DEPT/1JCH/HMBC; H,H-COSY and NOESY empty in
eurabidiol):

| File | Formula | Atoms element | Schedules in file |
|---|---|---|---|
| Hyperaspine.sml | C19H30N2O3 | `molecule` | none |
| bn-ye1.sml | C27H28O6 | `molecule` | p0 0.8, plateau 5000, cool 0.95 (uphill default 50) |
| eupatorio.sml | C13H14O3 | `molecule` | none |
| eurabidiol.sml | C15H28O2 | `molecule` | p0 0.8, plateau 1500, cool 0.95 |
| monochaetin.sml | C18H20O5 | `molecule` | p0 0.8, uphill 600, plateau 6000, cool 0.97 |
| narelin.sml | C20H20N2O4 | `cml:molecule` | p0 0.8, plateau 5000, cool 0.95 |
| pinene.sml | C10H16 | `molecule` | p0 0.8, plateau 500, cool 0.95 |
| polycarpol.sml | C30H48O2 | `molecule` | p0 0.8, uphill 1000, plateau 10000, cool 0.95 |
| preconen.sml | C13H16O3 | `cml:molecule` | p0 0.8, plateau 1000, cool 0.95 |
| triterpen.sml | C32H52O3 | `cml:molecule` | p0 0.8, plateau 500, cool 0.95 |

`sml/pinene.jsx` is a JSX (Java-object-to-XML serialization) dump of a `SenecaDataset`; no code in
the 2001 client reads it. DROP (reference only).

### 7.2 CML document (write only, on "close dataset → Yes") – KEEP-ADAPT (D11)

`CMLGenerator.convert`: `<!DOCTYPE molecule SYSTEM "cml.dtd" []>`, root `<molecule convention="SenecaDocument">`
with `string title="DocumentName"`, `string title="DocumentDescription"` (escaped `&`, `<`),
`formula`, `float title="Mass"`, a ConnectionTable `molecule` (atoms with elementType only, bonds),
and one `molecule title="NMR Experiment" convention="Jena2000NMR"` per spectrum (carbon1D, hetcor,
hetcorlr, hhcosy, noesy; DEPT spectra omitted) with `ExperimentType`, `Frequency`, `Solvent`,
`Standard`, `stringArray` Nucleus/Properties, `list title="Signals"` of `list title="Signal"` with
`floatArray title="Value"`, `float` Intensity and Phase. The output is not well-formed XML
(several `<float …>` / `<stringArray …>` elements are closed with `</string>`). No reader exists.

### 7.3 LUCY dataset import (`.col`) – KEEP

Line-oriented, whitespace-tokenized (`LUCYFileHandler.importLUCYDataset`):

1. version line (ignored)
2. molecular formula → dataset formula, heavy atoms, mass (`MFAnalyser.getMass`)
3. `N` = number of heavy atoms
4. `E` = number of elements, followed by `E` lines (ignored)
5. `C` = number of carbons, followed by `E−1` lines (ignored)
6. `N` lines: token 2 = H count of atom i
7. `K` = number of known bonds; the `K` bond lines are only consumed when debug is on (legacy
   defect: files with `K > 0` are misparsed)
8. `N` lines of element symbols (ignored)
9. `C` lines: `<13C shift> <1H shift a> <1H shift b>` (0 = none)
10. `M` = number of HMBC pairs, `M` lines `i j` (1-based atoms)
11. `L` = number of COSY pairs, `L` lines `i j`

Derived data: 1D carbon signal per carbon (intensity 1, phase NONE); DEPT-90 (CH: +1) and DEPT-135
(CH, CH3: +1 positive; CH2: −1 negative) per carbon by H count; 1JCH signals per non-zero proton
shift; then **all HCounts reset to −1** ("undefined"); each HMBC pair `i<j` → CH long-range signals
(13C of i with each proton of j found in 1JCH within 0.01 ppm, otherwise reversed); each COSY pair
(both directions) → H,H-COSY signals for all proton combinations. Dataset name = file name;
`sd.init()`; no annealing schedule is created. Parse errors → stdout "Error while trying to read
LUCY file", partial dataset returned.

### 7.4 MDL molfile (V2000) – read and write – KEEP

- Read (Load MDL): counts line, atom block (x, y, z, symbol), bond block (order; stereo 1 → codes
  5/6, 6 → 7/8), `M  CHG`; implicit H count by valence (`saturate`).
- Write (Save MDL, result strings, results log): see architecture §6 (`writeMDLMol`): title line,
  two empty lines, `aaabbb  0  0  0  0  0  0  0  0  1 V2000`, atoms with x/y/z (`0.0000` for
  generator results), bonds `i j order stereo  0  0  0`, `M  END`. Result titles are
  `Score: <score>`.
- Port, **writer**: re-implement the legacy `writeMDLMol` byte-exactly. Its output is program output:
  result strings, `~/seneca.results.sdf`, and the input of the duplicate check (`notInStock` reads it
  back). CDK's `MDLV2000Writer` writes a program/timestamp header line and different spacing, so it is
  not a substitute (`compchem-semantics.md` section 10).
- Port, **reader** (Load MDL, user import only): CDK `MDLV2000Reader` is acceptable if heavy-atom and
  implicit-H semantics are kept (harness: H counts identical on SENECA's structures).

### 7.5 LUCY result structure (Load LUCY Result) – KEEP

Line 1 title; line 2 `<nAtoms> <nBonds>`; `nAtoms` lines `<tok> <symbol> <tok> <tok> <HCount>`;
`nBonds` lines `<a1> <a2> <order>` (1-based).

### 7.6 WinNMR peak-picking text from the clipboard – KEEP

- If the text contains `"Peak Picking results:"` at index > 0: 1D import. Text from that marker,
  then from the first `"1"`; repeated groups of 6 tokens: peak no., data point, frequency,
  **ppm**, intensity, **percent intensity** → signal (shift = ppm, intensity = percent, phase
  POSITIVE if > 0, NEGATIVE if < 0, otherwise the previous value).
- Otherwise 2D import: tokens in pairs `<shift f1> <shift f2>`, intensity 1, POSITIVE.

### 7.7 Output logs – KEEP-ADAPT

| File | Content |
|---|---|
| `~/seneca.log` | engine INFO log (same text as Logbook), truncated at startup |
| `~/seneca.results.sdf` | every new best structure of every run: MDL block + `\n$$$$\n` |
| `~/seneca.annealing.log` | "Log Annealing Data" table |
| stdout | ERROR-level messages; many `System.out` debug prints (DROP) |

### 7.8 Preferences `~/seneca.props` – KEEP-ADAPT

`java.util.Properties`, header "Seneca Properties": `filelocation.seneca` (last SML directory,
default "/"), `filelocation.lucy` (last LUCY directory, default "/"), `filelocation.logfile`
("/senecalog") and `filelocation.resultfile` ("/senecaresults") (unused). Missing file → stderr
"Seneca: Can't find properties file. Using defaults.". Port: Java Preferences or a file in
`~/Library/Application Support/Seneca`.

## 8. Resources

| Resource | Content | Mark |
|---|---|---|
| `seneca/client/Seneca.properties` | menu/toolbar definition, labels, icons, tooltips, About/Exit texts (quoted above); `disable=` list (never applied) | KEEP-ADAPT (labels/texts) |
| `seneca/client/logging.properties` | log4j config (architecture §8) | KEEP-ADAPT |
| `seneca/engine/logging.server.properties` | server log config | DROP |
| `seneca/client/images/*.gif` | toolbar/menu icons (new, open, save, cut, copy, paste, props, 1D, chcosy, chcorrlr, hhcosy, noesy, ConnMat (referenced as `connmat.gif`), NodeTab, annealprops, strucDisp, startCalc, …) | KEEP (new, open, save, strucDisp at least) |
| `images/seneca.gif`, `Seneca Vollscan.gif`, `seneca_gross_unbehandelt.gif`, `*.tif`, `MPICElogo*.tif` | logos/artwork, not referenced by code | KEEP-ADAPT (app icon / About) |
| `images/WS_FTP.LOG` | FTP log | DROP |

## 9. Third-party library map

| Legacy jar | What it is | Used for | Modern replacement |
|---|---|---|---|
| `compchem.jar` (75 KB, binary only) | Steinbeck's pre-CDK chemistry library (29 classes) | atom/graph model, formula parsing, valences, MDL I/O, BFS paths, Morgan "isomorphism", SSSR, 1-sphere HOSE codes + Bremser table, LIFO queue | A SENECA-internal port of every compchem function whose exact semantics drive scores or output (`compchem-semantics.md` section 1): graph model with ordered adjacency and valence table, ring perception (`SetOfRingsFinder.findSSSR` + `Ring.sort`, verbatim, including its failure modes), Morgan heuristic (`IsomorphismTester`, `computeMorganMatrix`), 1-sphere HOSE code + Bremser table (651 entries, 648 keys), legacy MDL **writer** (byte-exact), formula string, `getPathLength`, `getBondCount`. **CDK 2.x** only for depiction, optional MDL import (`MDLV2000Reader`), and as a test oracle. Not for rings: `Cycles.sssr` gives different ring sizes. Decompiled reference sources: `legacy/decompiled/compchem` (Vineflower 1.11.1) |
| `jmdraw.jar` (21 KB) | 2D structure layout + AWT renderer for compchem atoms (depends on compchem, vecmath) | structure drawings (display only; no editing) | CDK `StructureDiagramGenerator` + `cdk-depict` `DepictionGenerator` (render to `BufferedImage`/SVG in a Swing panel). No editor needed |
| `jfreechart.jar` (111 KB) | early JFreeChart, package `com.jrefinery.chart` (`XYDataSource`, `Plot.HORIZONTAL_AXIS`, `StandardTitle`) | annealing chart | JFreeChart 1.5.x (`org.jfree.chart`, `XYSeriesCollection`, `ChartPanel`) |
| `log4j.jar`, `log4j-core.jar` | log4j 1.0.4 (`Category` API, `org.apache.log4j.gui.TextPaneAppender`) | engine/results/annealing logs, Logbook pane | `java.util.logging` (no extra dependency) with a custom Swing `Handler` for the Logbook and explicit file writers; alternatively SLF4J + Logback. Do not use log4j 1.x (EOL, CVEs) |
| `aelfred.jar` | Microstar AElfred SAX parser | not referenced directly | JDK JAXP – DROP |
| `sax.jar` | SAX 1 interfaces | `SpecMLReader extends HandlerBase` (unused SAX path) | JDK JAXP – DROP |
| `jaxp.jar` | JAXP 1.0 interfaces | `DocumentBuilderFactory` | JDK `javax.xml.parsers` – DROP |
| `parser.jar` | Sun "Project X" XML parser + DOM (`com.sun.xml.parser.Resolver` imported but unused) | DOM implementation | JDK JAXP – DROP |
| `cml.jar` | early CML/CDOPI classes (`org.openscience.cml`, `org.openscience.cdopi`) | **not referenced** by any source file (CML is written with string concatenation) | DROP (CDK `cdk-libiocml` only if CML reading is ever wanted) |
| `vecmath1.1-1.12.jar` | `javax.vecmath` | only inside jmdraw `RenderingEngine` | DROP (comes transitively with CDK where needed) |
| `junit.jar` | JUnit 3 | no tests in the tree (`build.xml` target `test` refers to missing `seneca.test.SENECATests`) | JUnit 5 for the port's tests |
| JDK RMI (`java.rmi`), `rmic` | distribution | `seneca.engine`, `seneca.compute`, `seneca.gatekeeper`, `SGServerPanel` | DROP (in-process threads, e.g. `ExecutorService`) |
| `jnt.scimark2` (source) | SciMark 2.0 benchmark | server speed | DROP |

## 10. Summary of DROP items

GateKeeper and all of `seneca.gatekeeper`; `StructureGeneratorServer` (RMI export, registry binding,
GateKeeper registration, benchmarking); `Compute`/`Task` interfaces; `jnt.scimark2`; "Update server
list"; Serverlist tab and `ComputeServerConfigurator`; `FastResolver`; RMI policy files and the
server/gatekeeper manifests; disabled menu items (Edit ▸ Copy/Cut/Paste and the data-editing
shortcuts, View ▸ Status of Generator Threads, Calculate ▸ Start, File ▸ Export, unlabeled Print and
Open items); GIFA import (no-op); unreachable classes (`NodeTableFrame`, `ConnTablePanel`,
`AnnealingViewPanel`, `StructureGeneratorCollection`, `ResourceManager`, `CNMRSignal`, `Carbon1D`,
`AnnealingScheduleInterface`, `WienerIndexJudge`); `-Dseneca.home`; JSX dataset dump.
