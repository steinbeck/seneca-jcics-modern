package io.github.steinbeck.seneca.app;

/**
 * {@code Component.setName} values of the controls the scripted GUI tests drive
 * (docs/port/gui-design.md, "Testing without a human"). Every implementer sets these names on the
 * corresponding component; the tests find components only by these names (never by position or text),
 * so labels may stay exactly as in 2001. Per-judge names append the judge's class simple name
 * (e.g. {@code JUDGE_AUTOCONFIGURE + "HMBCJudge"}); per-spectrum names append the
 * {@code SpectrumSlot} key (e.g. {@code SPECTRUM_TABLE + "carbon1D"}).
 */
public final class ComponentNames {

    private ComponentNames() {
    }

    // main window (architect)
    public static final String MAIN_VIEW = "main.view";
    public static final String TOOLBAR = "main.toolbar";
    public static final String DESKTOP = "main.desktop";
    public static final String STATUS_BAR = "main.status";

    // dataset window (architect: frame, tabs, close button)
    public static final String DATASET_FRAME = "dataset.frame";
    public static final String DATASET_TABS = "dataset.tabs";
    public static final String DATASET_CLOSE = "dataset.close";

    // G1: General Settings, spectra, atoms, schedule, judges
    public static final String GENERAL_TABLE = "general.table";
    public static final String SPECTRA_TABS = "spectra.tabs";
    public static final String SPECTRUM_TABLE = "spectrum.table.";
    public static final String SPECTRUM_ADD = "spectrum.add.";
    public static final String SPECTRUM_DELETE = "spectrum.delete.";
    public static final String SPECTRUM_IMPORT = "spectrum.import.";
    public static final String SPECTRUM_LABEL = "spectrum.label.";
    public static final String ATOM_TABLE = "atoms.table";
    public static final String CARBON_SHIFT_TABLE = "atoms.carbonShifts";
    public static final String AUTOASSIGN = "atoms.autoassign";
    public static final String H_BALANCE = "atoms.balance";
    public static final String SCHEDULE_LIST = "schedule.list";
    public static final String SCHEDULE_ACCEPTANCE = "schedule.acceptance";
    public static final String SCHEDULE_PLATEAU = "schedule.plateau";
    public static final String SCHEDULE_COOLING = "schedule.cooling";
    public static final String SCHEDULE_UPHILL = "schedule.uphill";
    public static final String SCHEDULE_UPDATE = "schedule.update";
    public static final String SCHEDULE_REMOVE = "schedule.remove";
    public static final String SCHEDULE_NEW = "schedule.new";
    public static final String JUDGE_TABS = "judges.tabs";
    public static final String JUDGE_AUTOCONFIGURE = "judge.autoconfigure.";
    public static final String JUDGE_REPORT = "judge.report.";
    public static final String JUDGE_ACTIVATE = "judge.activate.";
    public static final String JUDGE_TABLE = "judge.table.";
    public static final String FEATURE_HETHET = "judge.feature.hethet";
    public static final String JUDGE_REPORT_TEXT = "judge.reportText";

    // G2: Structure Generation, Logbook, Structure Display
    public static final String RUN_TABLE = "run.table";
    public static final String RUN_ADD_SLOT = "run.addSlot";
    public static final String RUN_CLEAR = "run.clear";
    public static final String RUN_START = "run.start";
    public static final String RUN_STOP = "run.stop";
    public static final String RUN_RESET = "run.reset";
    public static final String RUN_SUMMARIZE = "run.summarize";
    public static final String RUN_LOG_ANNEALING = "run.logAnnealing";
    public static final String RUN_CHART = "run.chart";
    public static final String RUN_BEST_STRUCTURE = "run.bestStructure";
    public static final String RUN_EVALUATION = "run.evaluation";
    public static final String LOGBOOK = "logbook.text";
    public static final String STRUCTURE_DISPLAY = "structures.display";
    public static final String STRUCTURE_LOAD_MDL = "structures.loadMdl";
    public static final String STRUCTURE_LOAD_LUCY = "structures.loadLucy";
    public static final String STRUCTURE_SAVE_MDL = "structures.saveMdl";
    public static final String STRUCTURE_CLOSE = "structures.close";
    // G2 additions: Structure Display "Save SDF" and "Copy" (DV-84), the tiles and their container
    public static final String STRUCTURE_SAVE_SDF = "structures.saveSdf";
    public static final String STRUCTURE_COPY = "structures.copy";
    public static final String STRUCTURE_TILES = "structures.tiles";
    public static final String STRUCTURE_TILE = "structures.tile";

    /** The look-and-feel list of the macOS "Settings..." dialog ({@code AppActions.showPreferences}). */
    public static final String PREFERENCES_LOOK_AND_FEEL = "preferences.lookAndFeel";
}
