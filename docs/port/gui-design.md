# SENECA port – GUI design

The Swing application on top of the ported core (docs/port/design.md). This document is the contract
for the two GUI implementers G1 (dataset editing) and G2 (runs and results), section 13. Behaviour is
specified elsewhere and not repeated here:

| Document | Content |
|---|---|
| `docs/legacy/feature-inventory.md` | every user-visible function F-xx with labels, defaults and file formats: **the GUI specification** |
| `docs/legacy/architecture.md` | legacy data flow (section 3), logging (section 8), defects D1–D16 (section 9) |
| `legacy/original/src/seneca/client/` | the 34 legacy client sources (ISO-8859-1: read with `iconv -f ISO-8859-1`) |
| `docs/port/design.md` | core packages, run API (section 7), threading of runs (section 8), `LegacyCompat` (section 5) |
| `docs/port/DEVIATIONS.md` | deviations of the core; GUI deviations are in section 12 here (DV-70 onwards) |

## 1. Principles

1. **Every KEEP and KEEP-ADAPT item of the feature inventory exists** (mapping in section 4). DROP items
   (RMI, GateKeeper, server lists, disabled or no-op menu entries, unreachable classes) do not appear.
2. **Recognisable, not re-designed.** Swing, as in 2001. Window structure, tab order, table columns,
   button labels, menu labels and message texts are the 2001 ones; where a label must change (run slots
   instead of servers) the change is listed in section 12.
3. **The GUI calls the core; it computes nothing that decides a result.** Autoassignment,
   autoconfigure, table edits of judges, schedule editing, H balance, file formats, runs, summarize: all
   in the core (`config`, `io`, `structgen`, `chem`). If the GUI needs something the core lacks, add it
   minimally to the core **with a unit test** and report it; never duplicate logic in Swing code.
4. **The application runs `LegacyCompat.DEFAULT`** (`AppContext.compat`) everywhere a core API takes a
   mode (`RunInput.fromDataset`, `JudgeConfiguration.autoconfigure`, `ScheduleConfiguration`,
   `AtomProperties.autoassign`, `WinNmrPeakPicking.paste`, `LucyDatasetImporter.read`,
   `SpecMLWriter.write`, `RunController.summarize`).
5. **Testable without a human** (section 10): nothing in `app` creates a dialog, file chooser or frame or
   touches the clipboard except through `app.ui.UiServices`; every control the scripted tests drive has a
   `ComponentNames` name; tests run headless.
6. **macOS first**, nothing macOS-only: `java.awt.Desktop`/`Taskbar` integration where supported,
   system look and feel, native file dialog; everything degrades to plain Swing elsewhere.

## 2. Decisions

| Topic | Decision | Why |
|---|---|---|
| Look and feel (F-05) | system look and feel at start (Aqua on macOS: `LookAndFeels.systemClassName()`); Edit ▸ Preferences ▸ Look&Feel lists the installed ones | the 2001 list had 3 non-existent L&Fs; Aqua is what a Mac user expects |
| Window structure (F-10, F-13) | main `JFrame` "Seneca" (toolbar, `JDesktopPane`, status bar; 2001: 800×600, now 1120×780 reduced to the screen, DV-88); one `JInternalFrame` per dataset, opened **maximized** (fills the desktop as in 2001; the title-bar button un-maximizes it into a free internal window); "Structure Display" and "Judge Configuration Report" are separate top-level frames, as in 2001 | keeps the 2001 layout; a top-level frame per dataset would need a menu bar per frame on macOS |
| Menu bar | screen menu bar on macOS (`apple.laf.useScreenMenuBar`); same menus elsewhere; `Desktop.setDefaultMenuBar` shows it while a separate window has focus | macOS convention |
| About / Quit | application menu (via `Desktop`) **and** Help ▸ About Seneca / File ▸ Exit; both quit paths show the 2001 confirmation | identical menu content plus macOS convention |
| Accelerators | ⌘N New Dataset, ⌘O Open, ⌘S Save (Ctrl elsewhere) | addition, harmless |
| File dialogs | `java.awt.FileDialog` (native, multi-select, own overwrite prompt) on macOS, `JFileChooser` with an extension filter elsewhere (`app.ui.SwingFileDialogs`) | native dialog on the Mac |
| Close dataset → "Yes" (F-48, D11) | saves **SML** (`SpecMLWriter`, DEFAULT), exactly like File ▸ Save, then closes; "No" closes; "Cancel" keeps it | the 2001 CML output is not well-formed and has no reader (data loss) |
| Save (F-22) | appends `.sml` if the chosen name has no extension; native dialog asks before overwriting | 2001 wrote the name verbatim with no prompt |
| New Dataset (F-20, D10) | `SenecaDataset.newDataset(name)`: name `"untitled" + new Date().hashCode()`, `init()` (7 disabled judges), one default schedule | 2001 dataset could not be run |
| Judge configuration in SML (D9) | not persisted (unchanged); Autoconfigure is deterministic from the data | changing the format is out of scope; revisit on request |
| Current dataset (F-33) | adding a dataset makes it current (2001); additionally **activating a dataset window makes it current**, and Datasets ▸ switch raises the window | in 2001 "Save" could silently save a dataset other than the visible one |
| Run slots (F-60..F-66) | section 6 | RMI dropped |
| Logging | `java.util.logging` for the application's own messages (no dependency); the three legacy log files written by `app.log.LogFiles` reproducing the log4j layouts (section 9) | log4j 1.x is EOL |
| Preferences (7.8) | properties file "Seneca Properties", keys `filelocation.seneca`, `filelocation.lucy`; `~/Library/Application Support/io.github.steinbeck.seneca/seneca.props` on macOS (DV-78), `~/seneca.props` elsewhere; default directory = home (2001: "/") | macOS location; "/" is useless |
| Log file location (F-06, 7.7) | home directory, names unchanged, truncated at start; overridable with `-Dseneca.logdir=<dir>` | 1:1; the override serves tests and users who want them elsewhere |
| Structure drawing (F-85) | CDK `StructureDiagramGenerator` (cdk-sdg) + `DepictionGenerator` (cdk-depict), section 8; **no editor** (jmdraw was display-only in SENECA) | inventory 9 |
| Annealing chart (F-70) | JFreeChart 1.5.6 (`org.jfree.chart`) | inventory 9 |
| Resources (F-04) | `io/github/steinbeck/seneca/app/Seneca.properties` (2001 texts) + `images/{new,open,save,strucDisp,seneca}.gif` | 1:1 |

## 3. Packages and classes

All GUI code is in `io.github.steinbeck.seneca.app` (the only package allowed to use AWT/Swing,
`PortHygieneTest`; GUI rules: `app.AppHygieneTest`). Owner: **A** = GUI architect (done), **G1**, **G2**
(section 13). "skeleton" = exists as a working minimal or placeholder version that the owner completes.

| Class | Owner | Responsibility |
|---|---|---|
| `app.SenecaApp` | A | `main`: macOS system properties, resources (F-04), log files, L&F, main window on the EDT, `args[0]` (F-02) |
| `app.AppContext` | A | application state (section 7), run options, run-control thread, exit |
| `app.AppPaths`, `app.AppPreferences` | A | log-file and preference locations; preferences file (7.8) |
| `app.Resources` | A | resource bundle, icons, multi-line legacy messages, version |
| `app.DatasetCollection` | A | open datasets + current one (port of `SenecaDatasetCollection`) |
| `app.MainFrame`, `app.MainView`, `app.StatusBar`, `app.MainMenuBar`, `app.AppActions` | A | main window shell / content / status bar / menus / actions (F-10..F-34) |
| `app.LookAndFeels`, `app.MacIntegration` | A | F-05, F-29; `Desktop` handlers, Dock icon |
| `app.TestHooks`, `app.ComponentNames` | A | hidden seed/cap hook; names of driven controls (section 10) |
| `app.Examples`, `app.Smoke` | integration | bundled example datasets (DV-87); `--smoke` self-test of the installed application (section 15) |
| `app.ui.*` (`UiServices`, `FileDialogs`, `SwingFileDialogs`, `Dialogs`, `SwingDialogs`, `OptionPanes`, `WindowHost`, `FrameWindowHost`, `TextClipboard`, `SystemTextClipboard`, `EdtRunListener`, `PlaceholderPanel`) | A | OS/user-blocking services, EDT bridge |
| `app.dataset.DatasetFrame`, `app.dataset.DatasetContext` | A | dataset window: tabs, Close button, close box, run controller |
| `app.dataset.GeneralSettingsPanel` | G1 | F-40 |
| `app.spectra.*` (`SpectraPanel`, new: `SpectrumTablePanel`, ...) | G1 | F-41, 7.6 |
| `app.dataset.AtomPropertiesPanel` | G1 | F-42 |
| `app.dataset.AnnealingSchedulePanel` | G1 | F-43 |
| `app.judges.*` (`JudgesPanel`, new: `JudgeConfiguratorPanel` + 7 subclasses, `JudgeReportPanel`) | G1 | F-44, F-50..F-56, F-81 |
| `app.files.DatasetFileActions` (+ new files in `app.files`) | G1 (skeleton: open works) | F-21, F-22, F-24, F-48, F-02 errors |
| `app.run.*` (`StructureGenerationPanel`, new: `RunTableModel`, `AnnealingChartPanel`, `RunPoller`, ...) | G2 | F-46, F-60..F-70, F-86..F-88 |
| `app.structure.*` (`StructureDisplayPanel`, new: `StructureView`, `Depictions`, ...) | G2 (skeleton: layout, Close) | F-30, F-67 display, F-85, F-90..F-94, copy |
| `app.log.*` (`Logbook`, `LogbookPanel`, `LogFiles`, `AppEngineLog`) | G2 (skeleton: working) | F-47, F-06, 7.7, F-68 output |

Resources: `src/main/resources/io/github/steinbeck/seneca/app/` (A; G1/G2 may add keys and images).

## 4. Mapping

### 4.1 Legacy client classes (`legacy/original/src/seneca/client`, 34 files)

| Legacy class | Port | Owner |
|---|---|---|
| `Seneca` (main frame, menus, toolbar, status bar, actions, L&F, log4j setup) | `SenecaApp`, `MainFrame`, `MainView`, `MainMenuBar`, `AppActions`, `StatusBar`, `LookAndFeels`, `MacIntegration`, `app.log.LogFiles` | A (G2: logs) |
| `Seneca.properties` | `app/Seneca.properties` | A |
| `logging.properties` | `app.log.LogFiles`, `AppEngineLog`, `Logbook` (section 9) | G2 |
| `SenecaDatasetCollection` | `DatasetCollection` | A |
| `SenecaDatasetFrame` (frame, tabs, Close, close prompt, `GeneralTableModel`) | `DatasetFrame` (frame, tabs, Close) + `GeneralSettingsPanel` (table) + `DatasetFileActions.close` (prompt, save) | A, G1 |
| `NMRSpectrumTablePanel` | `app.spectra.SpectrumTablePanel` (inside `SpectraPanel`); WinNMR parsing: core `io.WinNmrPeakPicking` | G1 |
| `AtomPropertyPanel` | `AtomPropertiesPanel`; logic: core `config.AtomProperties`, `config.CarbonShiftAssigner` | G1 |
| `AnnealingScheduleConfigurator` | `AnnealingSchedulePanel`; logic: core `config.ScheduleConfiguration` | G1 |
| `JudgeConfigurator` (common bottom row) | `app.judges.JudgeConfiguratorPanel`; logic: `config.JudgeConfiguration` | G1 |
| `TwoDSpectrumJudgeConfigurator`, `HHCOSYJudgeConfigurator`, `HMBCJudgeConfigurator` | `TwoDJudgePanel` (+ the two judges); report window `JudgeReportPanel` via `WindowHost` | G1 |
| `HOSECodeJudgeConfigurator` | `HoseCodeJudgePanel` | G1 |
| `SymmetryJudgeConfigurator` | `SymmetryJudgePanel` | G1 |
| `FeatureJudgeConfigurator` | `FeatureJudgePanel` | G1 |
| `HybridizationJudgeConfigurator` | `HybridizationJudgePanel` (edits via `JudgeConfiguration.setHybridizationCell`) | G1 |
| `HeteroBondsJudgeConfigurator` | `HeteroBondsJudgePanel` (`setHeteroBondsCell`) | G1 |
| `CarbonShiftAssigner`, `HMBCAssigner`, `HHCOSYAssigner`, `SpectrumAssigner` | core `config.*` (done) | – |
| `LUCYFileHandler` | core `io.LucyDatasetImporter` (done); dialog in `DatasetFileActions.importLucy` | G1 |
| `SGServerPanel` (run table, buttons, timer, chart, best structure, evaluation, summarize, log annealing data) | `app.run.StructureGenerationPanel` + helpers; run control: core `structgen.RunController` | G2 |
| `JMDrawPanel` | `app.structure.StructureView` (CDK depiction, section 8) | G2 |
| `StrucDispFrame` | `app.structure.StructureDisplayPanel` in a `WindowHost` window | G2 |
| `ExtensionFileFilter` | `SwingFileDialogs` (extension filter / `FileNameExtensionFilter`) | A |
| `Parameters`, `SenecaParameters` | `AppPreferences` | A |
| `ErrorMessages` | `java.util.logging` + `Dialogs` (user-visible errors) | all |
| `ComputeServerConfigurator` (Serverlist tab), `FastResolver` | DROP (F-45) | – |
| `NodeTableFrame`, `ConnTablePanel`, `AnnealingViewPanel`, `StructureGeneratorCollection`, `ResourceManager` | DROP (unreachable) | – |

### 4.2 Feature inventory, KEEP and KEEP-ADAPT items

| F-ID | Item | Class(es) | Owner |
|---|---|---|---|
| F-01 | launch, `Seneca.app` | `SenecaApp`; packaging `scripts/build-app.sh` (section 15) | A, integration |
| F-02 | `args[0]`, Finder "Open With" | `SenecaApp.start`, `MacIntegration` (`APP_OPEN_FILE`) → `DatasetFileActions.openFile` | A, G1 (error report) |
| F-04 | resource bundle | `Resources`; missing → "Seneca.properties not found", exit 1 | A |
| F-05, F-29 | look and feel | `LookAndFeels`, Edit ▸ Preferences ▸ Look&Feel | A |
| F-06, 7.7 | log files, truncated at start | `app.log.LogFiles` (opened by `AppContext.create`) | G2 |
| F-10 | main frame "Seneca", 800×600 (now `MainView.DEFAULT_SIZE`, DV-88), centred, close box → F-23 | `MainFrame`, `MainView` | A |
| F-11 | toolbar new/openAll/saveAll, icons only | `MainView` | A |
| F-12 | status bar (3 labels) | `StatusBar`, `MainView` | A |
| F-13 | dataset frames | `MainView.openDatasetWindow`, `DatasetFrame` | A |
| F-20 | New Dataset (D10 fixed) | `AppActions.newDataset`, core `SenecaDataset.newDataset` | A |
| F-21 | Open SENECA Dataset | `DatasetFileActions.open/openFile` | G1 |
| F-22 | Save SENECA Dataset (current dataset) | `DatasetFileActions.save` | G1 |
| F-23 | Exit + confirmation; macOS Quit | `AppActions.exit/confirmExit`, `MacIntegration` | A |
| F-24 | Import LUCY dataset | `DatasetFileActions.importLucy` | G1 |
| F-30 | View ▸ Current Structures (empty display, 600×400) | `StructureDisplayPanel.open` | A (action), G2 (window) |
| F-33 | Datasets ▸ switch | `MainMenuBar`, `AppActions.switchTo`, `DatasetCollection` | A |
| F-34 | About | `AppActions.showAbout`, `MacIntegration` | A |
| F-40 | General Settings | `GeneralSettingsPanel` | G1 |
| F-41, 7.6 | NMR spectra tables, Add/Delete Signal, WinNMR paste | `app.spectra.*`, core `io.WinNmrPeakPicking`, `UiServices.clipboard()` | G1 |
| F-42 | Atom Properties, Autoassignment, H balance | `AtomPropertiesPanel`, core `config.AtomProperties` | G1 |
| F-43 | Annealing Schedule | `AnnealingSchedulePanel`, core `config.ScheduleConfiguration` | G1 |
| F-44, F-50..F-56 | Judges tab, 7 configurators | `app.judges.*`, core `config.JudgeConfiguration` | G1 |
| F-81 | Judge Configuration Report window | `app.judges.JudgeReportPanel` via `WindowHost` | G1 |
| F-46 | Structure Generation tab | `app.run.StructureGenerationPanel` | G2 |
| F-47 | Logbook | `app.log.Logbook`, `LogbookPanel` | G2 |
| F-48, 7.2 | close dataset with save prompt (D11: SML) | `DatasetFrame.requestClose/close`, `DatasetFileActions.close` | A, G1 |
| F-60 | run table | `app.run.RunTableModel` | G2 |
| F-61 | add run slot ("Start local server") | `RunController.addSlot` | G2 |
| F-63 | Clear slot list (stops runs) | `RunController.clear` on the control thread | G2 |
| F-64 | Start as selected | `RunInput.fromDataset(sd, DEFAULT)` + `RunController.start(slot, in, app.runOptions(slot))` | G2 |
| F-65 | Stop Selected | `RunController.stop` | G2 |
| F-66 | Reset selected (stops the run, clears chart data) | `RunController.reset` | G2 |
| F-67 | Summarize Result | `RunController.summarize(DEFAULT)` → `StructureDisplayPanel` | G2 |
| F-68 | Log Annealing Data | chart data table → stdout, `LogFiles.annealing` | G2 |
| F-69 | 2 s round-robin polling, best structure, evaluation HTML | `javax.swing.Timer(2000)` in `app.run` | G2 |
| F-70, F-88 | annealing chart, click toggles score/kT | `app.run.AnnealingChartPanel` (JFreeChart) | G2 |
| F-85..F-87 | structure drawing, best structure panel, evaluation HTML | `app.structure.StructureView`, `app.run` | G2 |
| F-90..F-94 | Structure Display: tiles, Load MDL, Load LUCY Result, Save MDL, Close | `app.structure.StructureDisplayPanel` | G2 |
| 7.1 | SML read/write | core `io.SpecMLReader/Writer` | G1 |
| 7.3 | LUCY import | core `io.LucyDatasetImporter` | G1 |
| 7.4 | MDL read (Load MDL) and write (Save MDL, results) | core `chem.LegacyMdlReader/Writer` | G2 |
| 7.5 | LUCY result structure | core `chem.LucyStructureReader` | G2 |
| 7.8 | preferences | `AppPreferences` | A (G1 uses) |
| 8 | icons new, open, save, strucDisp; logo | `app/images/*.gif` | A |

## 5. Main window (architect, done)

Menu bar (`MainMenuBar`; DROP items omitted, so the Calculate menu disappears):

```
File      New Dataset | Open ▸ SENECA Dataset | Save ▸ SENECA Dataset | Import ▸ LUCY dataset | ─── | Exit
Edit      Preferences ▸ Look&Feel ▸ <installed look and feels>
View      Current Structures
Datasets  switch ▸ <names of the open datasets>
Help      About Seneca
```

Toolbar: new, openAll, saveAll (icons only; tooltips "Initialize a new dataset", "Open a file",
"Save to a file"). Status bar: "No dataset available" at start, then name and formula of the current
dataset; "No dataset loaded" after the last one was closed. Action command keys are the 2001 command
names (`new`, `openAll`, `saveAll`, `LUCYimp`, `exit`, `curSol`, `about`, `switchDS`).

Dataset window (`DatasetFrame`): title = dataset name (follows renames), tabs **General Settings, NMR
Spectra, Atom Properties, Annealing Schedule, Judges, Structure Generation, Logbook** (tooltips as 2001,
"LogBook" for Logbook), "Close" button. Constructing it calls `JudgeConfiguration.datasetWindowOpened(sd)`
**once** (the 2001 configurator constructors' side effect); panels must not call it again. It owns the
window's `RunController`. `close()` stops and clears the runs (control thread), removes the dataset and
disposes the window. Tab panels are constructed with a `DatasetContext(app, dataset, frame, runs)`;
keep these constructor signatures.

## 6. Run slots instead of the server list (G2)

The "Structure Generation" tab keeps the 2001 layout (legacy rendering:
`legacy-dataset-7-Structure_Generation.png`, section 10.5): run table and chart "Annealing Data" on top,
best-structure drawing and "Current Structure Evaluation" below, three titled button groups at the
bottom. The empty north text field (never written in 2001) is dropped.

| 2001 | Port |
|---|---|
| group "Server Admin": "Start local server", "Update server list", "Clear Server List" | group **"Run Slots"**: **"Add run slot"** (tooltip "Start local server in SENECA 2001"), **"Clear Slot List"**; "Update server list" dropped (RMI) |
| group "Selections": "Start as selected", "Stop Selected", "Reset selected" | unchanged |
| group "Result": "Summarize Result", "Log Annealing Data" | unchanged |
| table `No.`, `ID`, `Host`, `Dataset`, `MF`, `Status `, `Iter.` (yellow cells; idle row: Dataset/MF "none", Status "idle", Iter "none") | unchanged; ID `local<k>` (`RunSlot.id()`), Host "local" (`RunSlot.host()`), Status `RunStatus.State.label` |
| chart series "host <i>", "max. Score"; Summarize descriptions "Result from host <i>" | unchanged (the description text is produced by the core) |

Semantics (all fixed in the core, DV-21..DV-25, DV-47): one `RunController` per dataset window; a slot
runs at most one run (Start on a running slot stops and joins it first); Reset and Clear stop runs;
Summarize uses `RunController.summarize(LegacyCompat.DEFAULT)` (deep copies, never drains a run).
Start: `RunInput in = RunInput.fromDataset(ctx.dataset(), app.compat)` **on the EDT** (it reads and
mutates the dataset's judges, quirk Q6), then for every selected slot
`app.control(() -> runs.start(slot, in, app.runOptions(slot)))` (the generator copies its input). An
`IllegalArgumentException` (DEFAULT input validation: no atoms, no schedule, judge configured for
another formula, impossible formula) is shown with `Dialogs` (message = exception message); nothing
starts. Polling (F-69): a `javax.swing.Timer(2000)` refreshes the table every tick and, round robin, one
slot's best structure, evaluation HTML (`<html>\n<b>Score: s[/max]</b>` + description + `</html>\n`,
title "Evaluation of no. k", drawing title "Best structure of no.k" / "No structure available") and its
new annealing-log entries (`slot.annealingLogSince(lastIndex)`, one index per slot; nothing is drained).
Listener events (`RunListener` via `EdtRunListener`) may refresh the table and status earlier.

## 7. State: where it lives

| State | Lives in | Thread |
|---|---|---|
| open datasets, current dataset | `AppContext.datasets` (`DatasetCollection`) | EDT |
| dataset content (atoms, spectra, schedules, judges) | the `SenecaDataset` itself; the tabs edit it in place (as 2001) and call `sd.fireChange()` after edits that other views show (name, formula) | EDT |
| per-window table/editor state (`ScheduleConfiguration`, selections) | the panel | EDT |
| runs of a dataset window | `DatasetFrame.runs()` (`RunController`), slots, their results and annealing logs (thread-safe core objects) | any |
| chart series data, poll index, last log index per slot | `app.run` panel | EDT |
| Logbook text | `AppContext.logbook` (one `StyledDocument` shared by every window's Logbook tab) | append: any; document: EDT |
| log files | `AppContext.logFiles` | any (synchronized) |
| preferences (last directories) | `AppContext.preferences` | EDT |
| hidden test hooks | `AppContext.hooks` (`TestHooks`) | immutable |
| open top-level windows | `WindowHost` handles held by their panels | EDT |

No static mutable state in `app` (one `AppContext` per application and per test).

## 8. Structure display: jmdraw → CDK (G2)

jmdraw was used **only for display**: `JMDrawPanel` in the Structure Generation tab (best structure) and
in the Structure Display tiles; `JMDraw.draw()` computed 2D coordinates (ring templates), the
`RenderingEngine` drew them scaled to 80 % of the panel, centred, white background, implicit H on
labelled atoms. **No structure was ever edited** anywhere in SENECA, so no editor is needed.

Pipeline (`app.structure.Depictions` / `StructureView`, a `JComponent`):

1. MDL text (`RunStatus.bestStructure`, result list entries, Load MDL files) →
   `chem.LegacyMdlReader.read` → `HeavyAtom[]` (dataset atom order, H counts); LUCY result files →
   `chem.LucyStructureReader.read`.
2. `AtomSets.createConnectionTable(atoms)` + `synchronizeConnectionTable` → bond matrix →
   `chem.CdkBridge.toAtomContainer(atoms, bm)`.
3. Layout: `chem.CdkBridge.layout2D(HeavyAtom[])` (core, tested): CDK `StructureDiagramGenerator`
   (cdk-sdg), improved by `chem.Layout2D` where its layout has crossing bonds, crowded atoms or atoms
   drawn on top of each other or on a foreign bond (DV-88; the last two are removed in every golden best
   structure);
   the 2D coordinates are copied into `HeavyAtom.x/y`, so that Save MDL writes them with
   `LegacyMdlWriter.write(title, atoms)` (byte-exact legacy format; 2001: "coordinates from the drawing
   layout"). Summarize Result lays its tiles out in parallel.
4. Render: `DepictionGenerator` (cdk-depict) → `Depiction.draw(Graphics2D, bounds)` or
   `toImg()`, fitted to 80 % of the component, centred, white background; default CDK style (heteroatom
   H shown, carbons unlabelled). Cache the depiction per structure; never lay out on every paint.
5. Errors (unparsable MDL, layout failure) show the tile's title and an empty white area, logged.

Save MDL (F-93): `FileDialogs.save` (title "Save MDL MOL Files"), trailing `.mol` stripped, one file
`<dir>/<name><index>.mol` per tile (index 0-based, title line = file name). **Additions (G2):** "Save SDF"
(all tiles into one SD file, `LegacyMdlWriter` blocks + `$$$$`) and copy of a tile (context menu or
⌘C on a selected tile: MDL text and image on the clipboard via `TextClipboard.setContents`).

## 9. Logging (G2)

`AppEngineLog` (one instance, `AppContext.engineLog`, set on every run by `AppContext.runOptions`)
reproduces the 2001 log4j configuration (architecture.md 8; categories are additive):

| Target | Content | Layout |
|---|---|---|
| Logbook (all windows) | every engine INFO message | `%m` (verbatim) |
| `seneca.log` | every engine INFO message, every result block, every "Log Annealing Data" message | `%m%n` (message + "\n") |
| `seneca.results.sdf` | every new best structure (`mdl + "\n$$$$\n"`) | `%m` |
| `seneca.annealing.log` | "Log Annealing Data" table | `%m` |
| stdout | "Log Annealing Data" table (as 2001); FATAL "Error in StoicGent.run()" as `%p [%t] %c{2} (%M:%L) - %m%n`-like line | – |

All three files are truncated when the application starts. Application diagnostics (unreadable
preferences, I/O problems, listener failures) go to `java.util.logging`; anything the user must know
is shown with `Dialogs`. **Verified (G2)** against a 2001 run: `scripts/legacy-run-shots.sh` drives the
2001 `SGServerPanel` in-process (client log4j configuration, A4 with Fancy=false as `Seneca` sets it,
`user.home` = scratch) for pinene "gui" seed 1, polls once, presses "Log Annealing Data". Its Logbook text,
`seneca.log`, `seneca.results.sdf`, `seneca.annealing.log` and stdout are stored in
`src/test/resources/io/github/steinbeck/seneca/app/runs/legacy-pinene-gui-seed1/`;
`app.runs.RunPanelTest.outputsEqualThoseOfThe2001Client` drives the same run through the port's tab and
finds all five **identical** (apart from start/stop time and runtime). The 2001 Logbook style is kept: 14 pt,
INFO grey (TextPaneAppender attributes), white pane. The core's `FileEngineLog` (newline only when missing)
is not used by the app. With several runs at once the Logbook gets whole lines per run, and it keeps only
the newest ~1 MB (DV-90, `app.LogbookTest`); the files are unchanged.

## 10. Testing without a human

### 10.1 Rules

- Tests run **headless** (surefire `-Djava.awt.headless=${seneca.headless}`, default true). No
  `java.awt.Robot`, no OS accessibility, no visible window, ever.
- Components are built on the EDT (`GuiTestSupport.onEdt`), actions triggered with
  `AbstractButton.doClick()` / `Action.actionPerformed` / model setters, then `flushEdt()`.
- Everything that would block on a user is scripted through `ScriptedUi` (test implementation of
  `UiServices`): file dialogs answer queued files, option panes answer queued options and are recorded
  (title, pane), windows are recorded (title, content), the clipboard is a field.
- Log files and preferences go to a scratch directory (`AppPaths.under`), and `user.home` points to a
  scratch directory while a test app is open (`GuiTestSupport.newApp`); real `~/seneca.*` are never
  touched.
- Exit is a hook (`AppContext.setExitHook`); `System.exit` only in `SenecaApp` and `AppContext`
  (`AppHygieneTest`).

### 10.2 Harness (`src/test/java/io/github/steinbeck/seneca/app`)

| Class | Purpose |
|---|---|
| `GuiTestSupport` | `onEdt`, `flushEdt`, `waitFor`; `render`/`shot` (offscreen PNG to `<build>/gui-shots`, surefire property `seneca.guishots`), `shotMenu`; `find`/`require` by `ComponentNames` (`require` **aborts** the test as skipped while a control is not implemented), `click`, `menuItem(bar, "File", "Open", "SENECA Dataset")`, `labels`; `newApp(name, hooks)` → `TestApp` (context, `MainView`, menu bar, `ScriptedUi`, scratch dir, exit counter) |
| `ScriptedUi` | scripted `FileDialogs`, `Dialogs`, `WindowHost`, `TextClipboard` |
| `GuiSmokeTest` | menus, toolbar, status bar; New/Open; both dataset windows, every tab and sub-tab rendered; Datasets ▸ switch; rename; Close (Cancel/No); About, Exit (No/Yes), Structure Display window, Look&Feel switch; all rendered |
| `GuiEndToEndTest` | (1) **scripted end-to-end**, one test per run: toolbar "Open a file" (scripted dialog) → the golden recipe by hand in the window (`app.edit.RecipeThroughPanelsTest.configure`: Autoassignment, typed H counts, Autoconfigure in the profile's order, untick, New schedule) → dataset equal to `GuiRecipe.prepare(..., DEFAULT)` record by record → "Add run slot" *s* times, select row *s*, "Start as selected" (seed *s* via the hook) → result equal to `golden/runs/<key>.<profile>.seed<s>.json` (status, iterations, random draws, kT, best score and structure, every result) and shown by the GUI (table row, best-structure drawing, evaluation, Summarize tiles, Logbook). Runs: pinene gui/paper seed 1, eurabidiol gui/paper seed 1, polycarpol paper seed 2 (`slow`). (2) the GUI run path without the editing panels (recipe-prepared dataset, the window's `RunController`, `AppContext.runOptions`, `EdtRunListener`, Logbook and log files) |
| `GoldenRunAssert` | comparison of a `RunResult` with a golden run record (status, termination, enabled judges, counters, kT, best, every result's score/hash/description) |
| `AppUnitTest` | `TestHooks`, `DatasetCollection`, `EdtRunListener` ordering/coalescing, `LogFiles` layouts, preferences, resources |
| `AppHygieneTest` | no legacy imports, RMI or random sources in `app`; no `System.exit`, dialogs, frames, file choosers or system clipboard outside `app.ui` |

### 10.3 Hidden test hooks (`TestHooks`)

`-Dseneca.seed=<base>`: the run in slot `local<k>` uses seed `base + k` (three slots with
`seneca.seed=1` reproduce golden seeds 1, 2, 3). `-Dseneca.iterationCap=<n>`: the golden harness's cap
(golden runs: 5,000,000). Both are applied only by `AppContext.runOptions(slot)`; G2 must start every
run with those options. Also settable for the real app (`JAVA_OPTS` of `scripts/run-app.sh`).

### 10.4 Component names (`ComponentNames`)

Every control the scripted tests drive carries a fixed `setName`. The list in `ComponentNames` is part
of this contract; per-judge names append the judge class simple name, per-spectrum names the
`SpectrumSlot` key. G1/G2 set them all (add names there if needed, never rename).

### 10.5 Renderings and the legacy comparison

- `mvn -o -Dbuild.dir=<dir> test -Dtest='Gui*'` → `<dir>/gui-shots/*.png` (Metal: headless Aqua paints
  without native bezels and with translucent title bars, so headless renderings use the cross-platform
  look and feel; `-Dseneca.test.laf=<class>` overrides).
- `... -Dseneca.headless=false -Dseneca.guishots=<dir>/gui-shots-aqua` → the same renderings in Aqua:
  every component is hosted in a hidden, never shown frame (peer without a window on screen;
  `apple.awt.UIElement=true` keeps the JVM out of the Dock).
- `scripts/legacy-gui-shots.sh [outDir] [file.sml]` → the 2001 dataset window, every tab and sub-tab, as
  `legacy-dataset-*.png` (legacy classes, Metal, hidden frame, `user.home` = scratch). Compare these with
  the port's renderings when implementing a panel; put both side by side in the review.
- `scripts/legacy-run-shots.sh [outDir] [file.sml] [seed] [slots]` (G2) → a seeded 2001 run in the
  "Structure Generation" tab: `legacy-run-*.png` (tab after the run, chart in score and kT mode),
  `legacy-structure-display.png` (the "Summarize Result" tiles drawn by jmdraw), `legacy-logbook.png`, and the
  Logbook text, stdout of "Log Annealing Data", the Summarize tiles and the three log files as text. The
  port's counterparts are the `runs-*` renderings of `app.runs`.
- `scripts/run-app.sh [file.sml]` starts the real application (`BUILD_DIR`, `JAVA_OPTS`); for a quick
  look use `JAVA_OPTS="-Dseneca.logdir=<scratch> -Duser.home=<scratch>"` and kill it afterwards.

## 11. Threading

- **EDT**: all Swing, all dataset edits, `RunInput.fromDataset`, opening/saving files (small; the 2001
  client did the same), Summarize (`RunController.summarize` is fast) and the depiction of its results
  (lay out lazily per tile if slow).
- **Run threads** (one per run, created by the core): never touch Swing. They reach the GUI only through
  (a) `RunListener` registered as `new EdtRunListener(target)` — callbacks are queued and delivered on
  the EDT in order, status coalesced per slot; and (b) `AppEngineLog` (Logbook appends are queued to the
  EDT; file writes are synchronized).
- **Control thread** (`AppContext.control`, one daemon thread `seneca-control`): `RunController.start`,
  `reset`, `clear` (they stop and join a run for up to 30 s). Results go back with `invokeLater`; tests
  wait with `AppContext.awaitControl`. `stop` may be called on the EDT (does not wait).
- **Polling**: `javax.swing.Timer` (EDT) reads thread-safe snapshots (`RunSlot.status()`,
  `annealingLogSince`, `result()`).
- **Exit**: `AppContext.shutdown()` asks every run to stop (no join: run threads are daemons, every log
  write is flushed), closes the log files; then the exit hook (`System.exit(0)`).

## 12. GUI deviations from 2001 (DV-70 onwards; DEVIATIONS.md points here)

None of these changes a score, a search or a golden record.

| ID | 2001 | Port | Why |
|---|---|---|---|
| DV-70 | "New Dataset": no judges, no schedule (D10) | `SenecaDataset.newDataset`: `init()` + default schedule | the dataset could not be run |
| DV-71 | close → "Yes" writes CML (D11) | writes SML (as File ▸ Save), then closes | CML output not well-formed, no reader |
| DV-72 | Save writes the name verbatim, no overwrite prompt | `.sml` appended if no extension; the native dialog asks before replacing the name as typed; when `.sml` was appended afterwards and that file exists, the application asks ("… already exists. Do you want to replace it?", also for `.sdf` in Structure Display ▸ Save SDF) | data loss |
| DV-73 | the current dataset changes only on open/new and via Datasets ▸ switch (which does not raise the window); collection `remove` notifies before removing; switch by name picks the first of equal names | activating a window makes its dataset current; switch raises the window; switch by index; `remove` notifies after removing | Save could target an invisible dataset |
| DV-74 | Edit menu items Copy/Cut/Paste, Compound Properties, Spectra, Connectivity Matrix, Node Table, Annealing Properties, Score Evaluators; View ▸ Status of Generator Threads; Calculate ▸ Start; File ▸ Export, Print, unlabeled Open item (all disabled or no-ops) | omitted (Calculate menu gone) | DROP items of the inventory |
| DV-75 | Look&Feel: Windows, Motif, Java (missing classes), Metal | installed look and feels; system L&F at start | F-05, F-29 |
| DV-76 | toolbar openAll/saveAll without tooltip (key mismatch) | "Open a file", "Save to a file" (texts from the 2001 properties); ⌘N/⌘O/⌘S | usability |
| DV-77 | About: version 0.8b, MPI e-mail and web address | original credit lines, citation, port version line | addresses obsolete |
| DV-78 | preferences `~/seneca.props`, default directory "/" | `~/Library/Application Support/io.github.steinbeck.seneca/seneca.props` (macOS; the folder is named after the bundle id because another SENECA program on the same Mac uses `Application Support/Seneca`, whose saved directories steered this application's dialogs), default home; a remembered directory that no longer exists is ignored | macOS location |
| DV-79 | dataset frames always fill the desktop (BorderLayout) | open maximized; can be un-maximized | KEEP-ADAPT F-13 |
| DV-80 | one Logbook text pane, moved into the newest dataset frame | every Logbook tab views the same document | the pane vanished from older frames |
| DV-81 | closing a dataset leaves its runs running | runs are stopped (as Reset/Clear, DV-25) | orphaned threads |
| DV-82 | "Server Admin" group, "Start local server", "Update server list", "Clear Server List"; north text field | "Run Slots", "Add run slot", "Clear Slot List"; no update button, no north field. Slot ids as 2001: `local0`, `local1`, ...; after "Clear Slot List" the numbering restarts at `local0` (`RunController.clear`), so the seed hook again gives row k the seed base + k − 1 | RMI dropped |
| DV-83 | errors only on stdout/stderr (unreadable file, failed start, failed autoconfigure; a 2D judge whose assignment fails prints "Assignment failed." and stays inactive) | also a message dialog (failed 2D assignment: the same stdout line plus a warning "Assignment failed: ... stays inactive" with what it needs) | the user sees nothing otherwise |
| DV-84 | Structure Display: no SDF export, no copy | "Save SDF", copy of a tile | requested additions |

| DV-85 | dataset editing tabs (G1): (a) "Add Signal" / "Paste WinNMR pp" on a spectrum read empty from SML (dim 0, no axes) give signals without shifts; (b) committing the "Molecular Formula" cell, even unchanged, regenerates the atoms (all H counts and shift assignments lost); an empty formula throws after storing ""; (c) "Delete Signal" without a selection, a non-numeric "Assigned 13C Shift" or "Molecular Mass" throw on the event thread; (d) "Carbon Shifts" table and the shift/numbering columns of the judge tables are editable but discard the edit; (e) Symmetry table `Symb.` shows the symbol of the atom whose index is the class number; (f) stored molecular mass shown widened to double ("136.1251983642578"); (g) report window without scroll bars, editable; (h) the H balance (with its side effect, undefined H → 0 when balanced) runs only when the Atom Properties table paints | (a) the spectrum first gets the slot's axes (`NMRSpectrum.ensureAxes`, DEFAULT); (b) an unchanged formula changes nothing, an empty one is refused with a message; (c) ignored (no selection / not a number); (d) read-only; (e) symbol of the class's first member (the `Shift` column already used it); (f) shown as float; (g) scroll pane, read-only; (h) also when the window opens, after Autoassignment and on dataset changes | data loss / crash / misleading display; none changes a recipe result (`app.edit.RecipeThroughPanelsTest`: all 10 datasets × gui/paper/manual/all equal `GuiRecipe` and the echo file) |

| DV-86 | run and results UI (G2): (a) every "Start local server" re-configured log4j (`logging.server.properties`) and so truncated the three log files again; (b) Host column = the machine's host name (`InetAddress.getLocalHost()`); (c) the timer refreshed one table row per 2-s tick; (d) chart: series allocated once for the slots existing at the first data, a later slot's data were lost (exception in the timer); after a click the kT values appeared only at the next poll; the frame title changed from "Annealing Data" to "Annealing data" at the first poll; without data one series "no data" with an invisible point at (1, 1); (e) "Log Annealing Data" without data: NullPointerException on the event thread; (f) "Save MDL" wrote jmdraw's layout coordinates; a file that could not be read or drawn aborted "Load MDL"/"Load LUCY Result"; (g) Structure Display 600 px wide including the scroll bar (one tile per row once the bar appears); (h) the best-structure drawing and every tile drawn by jmdraw | (a) truncated once, at application start (runs of other slots keep their log); (b) "local"; (c) every row each tick, plus run-listener updates in between; cells read-only; (d) series grow with the slots; the click redraws at once; the title stays "Annealing Data"; "no data" is an empty series; "max. Score" is drawn as one horizontal segment over the iterations shown; (e) nothing is written; (f) CDK 2D layout coordinates (`chem.CdkBridge.layout2D`, bond length 1.5), the molfile format is the legacy one; such a file gets a tile with its name and an empty area (logged); (g) wide enough for two tiles plus the scroll bar; (h) CDK layout (improved by `chem.Layout2D`, DV-88) and depiction (section 8): 80 % of the area (at most 3× the standard bond length), black on white, carbons unlabelled except where CDK's standard style labels them (the middle carbon of cumulated double bonds, C=C=C, C=C=O), other atoms with their H, no stereo marks | bugs / RMI / jmdraw replaced; nothing changes a run, a score or a file format (`app.runs.RunPanelTest`: GUI runs = golden runs; Logbook and the three files = a 2001 run) |

| DV-87 | examples only in the distribution's `sml/` folder; the Open dialog starts at "/" (2001) or the last directory; the application takes one argument (a file) | File ▸ Open ▸ **Example Dataset** ▸ the ten bundled datasets (`app.Examples`, directory `-Dseneca.examples`, in `Seneca.app`: `Contents/app/examples`); the first Open dialog starts there; options `--logdir=`, `--examples=`, `--seed=`, `--iteration-cap=`, `--smoke[=report]` (`SenecaApp`, section 15); the packaged application keeps its bundle icon in the Dock instead of the 2001 logo | requested additions; the options make the packaged app testable without JVM options |

| DV-88 | layout and display (found by the visual review): (a) main window 800x600: with the macOS look and feel the dataset window's tab row and the Judges sub-tabs did not fit ("Structure Generation" and "Logbook" behind an overflow arrow); (b) run tab: seven equal table columns (dataset, formula, status, iteration count cut off), best structure about 370x115 px, fixed boxes; (c) judge matrices (one column per atom) squeezed into the window, headers "..." from about 25 atoms on; (d) chart: series colours of the chart library by position ("max. Score" pure yellow on white with three slots), a maximum score of 0 gave y labels like "4E-9"; (e) every Structure Display window titled "Structure Display"; (f) jmdraw layouts (CDK's plain layout drew e.g. the gem-dimethyl bonds of the pinene result's norbornene bridge across its C=C bond) | (a) `MainView.DEFAULT_SIZE` 1120x780, reduced to the usable screen; `app.DefaultLayoutTest` checks that every tab lies inside its tab pane; (b) column widths by content, cell tooltips; table/chart and drawing/evaluation in split panes (movable dividers); a double click on the best structure opens it in a Structure Display window; (c) `ui.MatrixTable`: columns as wide as their header, horizontal scroll bar, header tooltips; (d) fixed dark palette (red and blue first, as 2001), "max. Score" dashed black, y axis spans at least 1; (e) "Structure Display - <dataset name>" for Summarize Result and the double click (View ▸ Current Structures keeps "Structure Display"); (f) `chem.Layout2D`: CDK's layout scored by bond crossings, crowded atoms, atoms on bonds and substituents drawn inside rings; if not clean, 8 more atom orders and a rotation of substituents about their acyclic bonds, the best layout wins (deterministic; a clean CDK layout is kept exactly; `chem.Layout2DTest`). Overlaps, drawings that read as a different constitution (non-bonded atoms closer than 0.3 bond lengths, an atom within 0.2 bond lengths of the inner part of a bond it does not belong to), cost more than any crossings they save; where they remain, a short deterministic relaxation pushes the atoms apart (bond lengths kept, every atom held near its place) and the result is kept if it scores better. Plain CDK had such overlaps in 34 of the 56 golden best structures (e.g. two ring atoms of monochaetin and triterpen drawn on one spot); after `Layout2D` none has two non-bonded atoms closer than 0.2 bond lengths or an atom within 0.12 bond lengths of a foreign bond (`Layout2DTest.goldenBestStructuresHaveNoOverlaps`, the cages included; at most ~0.1 s per structure). Cage-like random results (Hyperaspine, bn-ye1 with 17–26 rings) still have many crossing bonds: no 2D layout of them is clean | usability; nothing changes a run, a score or a file format |
| DV-89 | – (2001 predates App Nap) | while a run is going on, the application asks macOS not to throttle it (`NSProcessInfo beginActivityWithOptions: NSActivityUserInitiatedAllowingIdleSystemSleep` via the Foreign Function API, `app.MacActivity`, driven by `app.RunActivity` over all windows' run controllers; JVM option `--enable-native-access=ALL-UNNAMED`); ended when the last run ends | measured: a run in a background app took about 4× as long (narelin, 400,000 iterations: 93 s instead of 41 s); `LSAppNapIsDisabled` in Info.plist does not help |
| DV-90 | Logbook: every engine message appended as it comes, the whole log kept; with several runs at once the two halves of a progress line ("Iteration: n ", "Current kT = ...\n") of different runs interleave | each run's messages are collected up to the newline and appended as whole lines (`AppEngineLog`; a single run's text is unchanged); consecutive messages inserted together; the Logbook keeps the newest ~1 MB (oldest whole lines dropped at 1,000,000 characters down to 750,000); `seneca.log` keeps everything as before | the port's engine writes the log many times faster (27 MB in 200 s for one long run): the Swing thread spent most of its time laying out the growing text |
| DV-91 | – | macOS application menu "Settings…" opens the Look&Feel choice of Edit ▸ Preferences ▸ Look&Feel as a dialog (`AppActions.showPreferences`) | the entry was dead otherwise |
| DV-92 | (a) the Logbook tab opened at the top of the text, and new text did not scroll it; (b) Structure Display: tiles of 300x200 at the top left, the rest of the window grey only as far as the rows reach (2001: one tile per window too, e.g. the best structure) | (a) the Logbook follows the end of the text like a terminal: while the view is at the bottom (initially, or scrolled back down), growing text, a resized window or the tab being shown keep it at the bottom; scrolled up, the view stays (`app.log.LogbookPanel`, `app.LogbookTest`); (b) a window with a single structure (double click on the best structure, one Load MDL file) draws it as large as the window; the tile panel always fills the window (`app.runs.StructureDisplayTest`) | the end of a run is what the user looks for; the double-click window was meant to enlarge the drawing |

Add further GUI deviations here (next free: DV-92) with the same columns.

## 13. Work split

Both implementers: read this document, `feature-inventory.md` sections 2–7 and the legacy sources of
their classes; render the legacy windows (`scripts/legacy-gui-shots.sh`) and compare. Use a private build
dir (`mvn -o -Dbuild.dir=$PWD/target-<name> test`). Edit only your files; shared files (`pom.xml`,
`ComponentNames`, `Seneca.properties`, this document, `GuiTestSupport`, `ScriptedUi`, `AppContext`,
`DatasetFrame`, `MainView`, `AppActions`) only minimally, and report every such change. Core additions:
minimal, with a unit test, reported. No commits.

### G1 – dataset editing UI

Files (own): `app/dataset/GeneralSettingsPanel.java`, `app/dataset/AtomPropertiesPanel.java`,
`app/dataset/AnnealingSchedulePanel.java`, `app/spectra/**`, `app/judges/**`, `app/files/**`; tests
`src/test/java/io/github/steinbeck/seneca/app/edit/**` (new package for G1 tests).

1. General Settings (F-40): table "Parameter"/"Setting", rows Filename, Description, Molecular Formula,
   Molecular Mass (mass: stored, else `MolecularFormula.mass(formula)`); edits call the dataset setters
   (formula regenerates the atoms: refresh every tab; `sd.fireChange()`).
2. NMR Spectra (F-41): one `SpectrumTablePanel` per sub-tab (`SpectrumSlot` carbon1D, dept90, dept135,
   hetcor, hetcorlr, hhcosy): label `"<n> signals in <specType>."` (2001's constructor wrote "Signals", but its change listener ran at once on registration, so users always saw lower case), table (No., one column per axis
   titled with the nucleus, Intensity, Phase combo NONE/POSITIVE/NEGATIVE), "Add Signal", "Delete Signal",
   combo "Import peak picking" with "Paste WinNMR pp from clipboard" (`WinNmrPeakPicking.paste(text,
   spectrum, DEFAULT)`, text from `app.ui.clipboard()`); GIFA dropped.
3. Atom Properties (F-42): both tables, "Autoassignment" (`AtomProperties.autoassign(sd, DEFAULT)`; on
   `false` a message), H balance label (red/dark green) — call `AtomProperties.balance(sd)` wherever the
   2001 table rendered or was edited (its side effect sets undefined H counts to 0 when balanced).
4. Annealing Schedule (F-43): bind to `new ScheduleConfiguration(sd, DEFAULT)`; list renders
   `A-F:<cool>-IAP:<p0>-PS:<plateau>-UHS: <uphill>`; `NumberFormatException` of Update → message.
5. Judges (F-44, F-50..F-56, F-81): `JudgeConfiguratorPanel` (judge = `sd.getJudge(Class)` at
   construction, titled border with the judge name, bottom row "Autoconfigure Judge" (tooltip
   "Autoconfigures <name>"), "Report Judgeconfig", checkbox "Activate this Judge?" reflecting programmatic
   changes) and the 7 centre panels as in the inventory, all edits through `JudgeConfiguration`; the report
   window "Judge Configuration Report" via `WindowHost` with `JudgeConfiguration.report(judge)` (non-2D
   judges: nothing, as 2001). Autoconfigure exceptions (STRICT only) cannot occur in DEFAULT; a `false`
   result unticks the checkbox (model already updated by the core).
6. Files (F-21, F-22, F-24, F-48, F-02): complete `DatasetFileActions`: open with error dialog (DV-44,
   DV-83), save (current dataset, `SpecMLWriter.write(sd, DEFAULT)`, UTF-8, `.sml` appended, DV-72),
   import LUCY (`LucyDatasetImporter.read(path, DEFAULT, log)`, chooser at the LUCY directory, filter
   `*.col` "Lucy datasets"), close prompt Yes = save then `frame.close()` (DV-71); remember directories in
   `AppPreferences`.
7. Component names from `ComponentNames` on every control; tests (scripted, headless) for each panel
   incl. the README tutorial (eurabidiol: Autoassignment leaves 2 H unassigned, type 1 into O-16 and O-17
   → green) and renderings `shot(...)` of every panel; compare with the legacy renderings.

Done when: `GuiEndToEndTest.pineneGuiRecipeThroughTheGuiReproducesGoldenSeed1` passes up to the
Structure Generation step (the dataset equals the recipe's), every G1 item of section 4.2 has a test.

### G2 – run and results UI

Files (own): `app/run/**`, `app/structure/**`, `app/log/**`; tests
`src/test/java/io/github/steinbeck/seneca/app/runs/**` (new package for G2 tests); core additions in
`chem.CdkBridge` (layout) with tests.

1. Structure Generation tab (section 6): `RunTableModel`, button groups, Start/Stop/Reset/Clear through
   the control thread, input validation messages, 2 s `Timer` polling round robin (F-69), listener via
   `EdtRunListener`.
2. Annealing chart (F-70): JFreeChart 1.5 `XYSeriesCollection`, one series "host <i>" per slot, "max.
   Score" horizontal line in score mode, tiny empty title, axes "Iteration"/"Score" ("kT" when toggled),
   mouse click toggles score/kT; Reset clears all chart data (2001).
3. Best structure and evaluation (F-86, F-87): `StructureView` (section 8), `JEditorPane` HTML read-only.
4. Summarize Result (F-67): `RunController.summarize(DEFAULT)` → `StructureDisplayPanel` window
   ("Structure Display"), one tile per structure titled "Score <s>", tooltip
   `<html><b>Score s: </b>` + description + `</html>`.
5. Log Annealing Data (F-68): table `Iteration; Server 1; ...` then `<x>; <score1>; ...` lines (read the
   2001 source for the exact text) to stdout and `LogFiles.annealing`.
6. Structure Display (F-90..F-94, DV-84): tiles 300×200 in a flow layout, "Load MDL" / "Load LUCY Result"
   (`FileDialogs.openMultiple`), "Save MDL", "Save SDF", copy, "Close".
7. Logbook and log files (section 9): verify against a legacy run; adjust `LogFiles`/`AppEngineLog`.
8. Component names, tests (scripted, headless): runs with the seed hook against golden runs (pinene gui
   seeds 1–3 in three slots), stop/reset/clear, summarize, chart toggling, Save MDL byte format,
   renderings of every view.

Done when: `GuiEndToEndTest.pineneGuiRecipeThroughTheGuiReproducesGoldenSeed1` passes completely (with
G1), every G2 item of section 4.2 has a test, the fast suite and `-Pslow,oracle` are green.

## 14. Status of the skeleton (architect)

Implemented and tested: main window, menus, toolbar, status bar, dataset collection and Datasets menu,
dataset window with all tabs (placeholders for G1/G2), close prompt (No/Cancel), New Dataset, Open SENECA
Dataset, About, Exit, Look&Feel switching, macOS integration (About/Quit/open-file/Dock/default menu
bar), `UiServices` with native and scripted implementations, `EdtRunListener`, test hooks, Logbook and
log files (working first versions, owned by G2), preferences, resources; test harness, smoke test,
renderings, legacy rendering tool, launcher script. Pending: everything marked G1/G2 above (placeholders
say "pending: G1"/"pending: G2").

**G1 (dataset editing), done.** All G1 items of 4.2 are implemented: General Settings, NMR Spectra
(`app.spectra.SpectrumTablePanel`), Atom Properties, Annealing Schedule, Judges (`app.judges.*`: one
`JudgeConfiguratorPanel` subclass per centre panel, `JudgeReportPanel`), and `app.files.DatasetFileActions`
(open with error dialog, save as SML, LUCY import, close with Yes = save). Tests in `app.edit`:
`RecipeThroughPanelsTest` drives the golden recipe through the panels for all 10 datasets × gui/paper/manual/all
(plus polycarpol@hblock) and compares every configured record with `GuiRecipe` (DEFAULT) and with the echo
file (STRICT); one test class per panel, `EmptyDatasetTest`, renderings `edit-*` and `recipe-*`. Core
additions: `NMRSpectrum.ensureAxes` (DV-85), `EchoRecords.configuredRecords` (test comparison),
`HMBCAssigner` DEFAULT on a dataset without atoms (DV-46).

**G2 (runs and results), done.** All G2 items of 4.2 are implemented. `app.run`: `StructureGenerationPanel`
(layout, button groups, Start/Stop/Reset/Clear through the control thread, input messages, 2-s round-robin
polling, `EdtRunListener`), `RunTableModel`, `AnnealingData` (chart data, "Log Annealing Data" table),
`AnnealingChartPanel` (JFreeChart, click toggles score/kT). `app.structure`: `Depictions` (MDL / LUCY →
`CdkBridge.layout2D` → CDK depiction), `StructureView`, `StructureDisplayPanel` (tiles, Load MDL, Load LUCY
Result, Save MDL, Save SDF, Copy, Close), `StructureTransferable`. `app.log`: Logbook style of 2001,
FATAL console line in `AppEngineLog`. Tests in `app.runs`: `RunPanelTest` (seeds 1 and 3 in rows 1 and 3
against golden / the core's DEFAULT run; table, best structure, evaluation, chart in both modes, Summarize,
Log Annealing Data; outputs identical to a 2001 run), `RunControlTest` (stop, reset, restart on a running slot,
clear, window close; messages for inputs that cannot run), `StructureDisplayTest` (depiction fit, load,
Save MDL byte format, Save SDF, copy), `AnnealingDataTest`, `RunPanelSlowTest` (`slow`: seeds 1–3 in three
slots, golden seeds 1 and 2); renderings `runs-*`. Core addition: `chem.CdkBridge.layout2D` (`chem.CdkLayoutTest`).
Component names added: `STRUCTURE_SAVE_SDF`, `STRUCTURE_COPY`, `STRUCTURE_TILES`, `STRUCTURE_TILE`.
Deviations: DV-86.

**Integration, done.** `GuiEndToEndTest` drives five golden runs end to end through the GUI (open, recipe
by hand, seeded run, results) and finds them identical to golden/runs: pinene and eurabidiol with "gui" and
"paper" (seed 1), polycarpol with "paper" (seed 2, `slow`; polycarpol "gui" has no complete golden run, the
2001 ring finder crashed in every seed). Additions: Example Dataset menu, command-line options and the
self-test (DV-87), packaging (section 15). Fixes found on the way: `cdk-standard` is a runtime dependency
of cdk-ctab/-smiles/-sdg/-depict and was demoted to test scope by the pom (the packaged application could not
draw a structure; tests and `scripts/run-app.sh` hid it because the latter put the test classpath on the
application's classpath); the test JVMs run with `user.home` = the build directory, so the 2001 code of the
oracle tests no longer writes the user's real `~/seneca.*`.

**Fixes after the verification round, done.** App Nap suspended during runs (DV-89, `app.RunActivityTest`,
launch test step 4); larger default window, run tab and judge matrices readable at the default size,
chart colours, Structure Display titles (DV-88, `app.DefaultLayoutTest`); 2D layouts scored and improved
(`chem.Layout2D`, `chem.Layout2DTest`); Logbook whole lines and bounded (DV-90, `app.LogbookTest`); slot
ids restart after "Clear Slot List" as in 2001 (DV-82, `structgen.RunControllerTest`); failed 2D
autoconfigure reported (DV-83); preferences in a folder of their own (DV-78); macOS "Settings…" (DV-91);
menu items without the toolbar tooltips (as 2001); SML DEFAULT writer keeps a missing solvent missing
(DEVIATIONS.md DV-08); renderings paint instead of print, so table selections show (`GuiTestSupport.render`).

**Fixes after the second verification round, done.** Structure drawings without overlapping atoms
(DV-88 f, `chem.Layout2DTest.goldenBestStructuresHaveNoOverlaps`, `verify.VisualRound2VerifyTest`); the
spectrum label reads "n signals in …" from the start, as users saw it in 2001; Save asks before replacing
an existing file when it appended `.sml` (or `.sdf`) itself (DV-72); the Logbook follows the newest text and a
single structure fills its Structure Display window (DV-92); `scripts/build-app.sh` registers only the
default `dist/` bundle with Launch Services (or with `REGISTER=1`).

## 15. Packaging and launch test

`scripts/build-app.sh` builds `dist/Seneca.app` (only `dist/README.md` is versioned):

1. `mvn package` (tests skipped) and `dependency:copy-dependencies` (runtime scope): the application jar and
   its 27 runtime jars, unshaded, side by side in the app's `Contents/app` (the launcher's class path);
2. the ten example datasets (`legacy/original/sml/*.sml`) into `Contents/app/examples`
   (`-Dseneca.examples=$APPDIR/examples`);
3. the icon: `scripts/gui/MakeIcon.java` draws alpha-pinene with CDK (cdk-sdg layout, cdk-depict
   rendering, white on a blue rounded square, with a 13C stick spectrum) at each size of a macOS icon set;
   `iconutil` makes `Seneca.icns`;
4. `jlink`: the modules `jdeps` reports (java.base, java.desktop, java.sql, plus java.logging and
   jdk.localedata with en/de), stripped, compressed;
5. `jpackage --type app-image`: name Seneca, version from pom.xml without `-SNAPSHOT`, bundle id
   `io.github.steinbeck.seneca`, main class `io.github.steinbeck.seneca.app.SenecaApp`, JVM options
   `-Xss16m` (deep legacy recursion, as in the tests), `--enable-native-access=ALL-UNNAMED` (App Nap,
   DV-89) and the examples directory;
6. `Info.plist` (plutil): document type "SENECA Dataset" for `.sml` (role Editor, owner) and the exported
   type `io.github.steinbeck.seneca.sml` (conforms to public.xml), `NSHighResolutionCapable`;
7. ad-hoc signature `codesign --force --deep -s -`, verified; `lsregister -f` registers the bundle (only a build into the default `dist/`, or with `REGISTER=1`; other `DIST` directories print the command instead).

`scripts/launch-test.sh` tests the built application unattended (its windows appear for a few seconds; the
log files go to a scratch directory through `--logdir`): (1) the launcher binary with `--smoke=<report>`,
(2) the same through Launch Services (`open -n ... --args --smoke=...`), (3) a normal start with an example
dataset as argument, still running after 10 s with nothing on stderr, then killed. The self-test
(`app.Smoke`) opens the example pinene, paints every tab, presses the buttons of the golden "gui" recipe,
starts one slot (seed 1, cap 5,000,000 unless `--seed` is given), waits, polls, summarizes, and reports the
runtime, look and feel, run and every uncaught exception or WARNING log record; it passes iff there is none.
The launch test also requires the golden numbers of pinene.gui.seed1 (21248 iterations, 2400/2400) and the
bundled runtime. A run that does not end within 5 minutes is stopped by the self-test ("Stop Selected") and
reported, not failed (narelin "gui" keeps reheating in DEFAULT). On macOS the self-test also reports whether
App Nap was suspended during the run (a problem if not) and restored afterwards. Step (4) of the launch
test measures the speed in the background: the self-test on narelin capped at 400,000 iterations, started
with `open -g` (never activated), next to the same run on the command line (`cli generate`, bundled
runtime); the app must reach 60 % of the command line's iterations per second (measured: 9,694 vs 9,925;
with App Nap allowed through the diagnostic property `-Dseneca.allowAppNap=true`: 4,286). `SPEED_TEST=0`
skips it. `build-app.sh` and `run-app.sh` run Maven offline unless `MVN_OFFLINE=` is set.
