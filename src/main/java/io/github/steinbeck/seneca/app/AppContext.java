package io.github.steinbeck.seneca.app;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ExecutionException;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.app.dataset.DatasetFrame;
import io.github.steinbeck.seneca.app.log.AppEngineLog;
import io.github.steinbeck.seneca.app.log.LogFiles;
import io.github.steinbeck.seneca.app.log.Logbook;
import io.github.steinbeck.seneca.app.ui.UiServices;
import io.github.steinbeck.seneca.model.SenecaDataset;
import io.github.steinbeck.seneca.structgen.EngineLog;
import io.github.steinbeck.seneca.structgen.RunController;
import io.github.steinbeck.seneca.structgen.RunOptions;
import io.github.steinbeck.seneca.structgen.RunSlot;

/**
 * Application-wide state (docs/port/gui-design.md, section "State"): one instance per running
 * application (and per GUI test). Everything else hangs off a dataset window ({@code DatasetContext}).
 *
 * <ul>
 *   <li>{@link #datasets}: the open datasets and the current one (F-33).</li>
 *   <li>{@link #ui}: dialogs, file dialogs, windows, clipboard (scripted in tests).</li>
 *   <li>{@link #logbook}, {@link #logFiles}, {@link #engineLog}: the shared Logbook and log files (F-47, 7.7).</li>
 *   <li>{@link #runOptions(RunSlot)}: the options of every run started from the GUI ({@code DEFAULT}
 *       mode, the shared engine log, the hidden {@link TestHooks}).</li>
 *   <li>{@link #control}: one background thread for run-control calls that may block
 *       ({@code RunController.start/reset/clear} join a stopping run for up to 30 s); never the EDT.</li>
 * </ul>
 * EDT only, except {@link #runOptions}, {@link #control} and the thread-safe members.
 */
public final class AppContext {

    /** The application always fixes legacy failures (design.md section 5). */
    public final LegacyCompat compat = LegacyCompat.DEFAULT;

    public final Resources resources;
    public final UiServices ui;
    public final AppPaths paths;
    public final AppPreferences preferences;
    public final TestHooks hooks;
    /** The bundled example datasets (File &gt; Open &gt; Example Dataset). */
    public final Examples examples;
    public final DatasetCollection datasets = new DatasetCollection();
    /** Whether any run of any dataset window is going on (macOS: no App Nap meanwhile, {@link MacActivity}). */
    public final RunActivity runActivity = new RunActivity();
    public final Logbook logbook = new Logbook();
    public final LogFiles logFiles;
    public final EngineLog engineLog;

    private final ExecutorService control = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "seneca-control");
        t.setDaemon(true);
        return t;
    });
    private final List<DatasetFrame> frames = new ArrayList<>();
    private MainView mainView;
    private Runnable exitHook = () -> System.exit(0);

    public AppContext(Resources resources, UiServices ui, AppPaths paths, TestHooks hooks, LogFiles logFiles) {
        this.resources = resources;
        this.ui = ui;
        this.paths = paths;
        this.hooks = hooks;
        this.preferences = new AppPreferences(paths.preferencesFile());
        this.examples = Examples.fromSystem();
        preferences.setDefaultSenecaLocation(examples.dir());
        this.logFiles = logFiles;
        this.engineLog = new AppEngineLog(logbook, logFiles);
    }

    /** The application's context: log files truncated and opened at {@code paths} (F-06). */
    public static AppContext create(Resources resources, UiServices ui, AppPaths paths, TestHooks hooks) {
        return new AppContext(resources, ui, paths, hooks, LogFiles.open(paths));
    }

    /** Options for a run in {@code slot}: DEFAULT, shared engine log, hidden test hooks (any thread). */
    public RunOptions runOptions(RunSlot slot) {
        return hooks.apply(RunOptions.application().withLog(engineLog), slot);
    }

    /** Runs {@code task} on the run-control thread (in submission order). */
    public void control(Runnable task) {
        control.execute(task);
    }

    /** Waits until every run-control task submitted so far has completed (tests; never on the EDT). */
    public void awaitControl(long timeoutMillis) throws InterruptedException, TimeoutException {
        try {
            control.submit(() -> { }).get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Called by {@link MainView}. */
    void setMainView(MainView v) {
        this.mainView = v;
    }

    public MainView mainView() {
        return mainView;
    }

    /** Adds {@code sd} to the open datasets (it becomes current) and opens its dataset window. */
    public DatasetFrame addDataset(SenecaDataset sd) {
        datasets.add(sd);
        return mainView.openDatasetWindow(sd);
    }

    /** Called by {@link DatasetFrame} when it opens and closes. */
    public void registerFrame(DatasetFrame f) {
        frames.add(f);
    }

    public void unregisterFrame(DatasetFrame f) {
        frames.remove(f);
    }

    /** The open dataset windows, in opening order. */
    public List<DatasetFrame> datasetFrames() {
        return List.copyOf(frames);
    }

    /** The window of {@code sd}, or null. */
    public DatasetFrame frameOf(SenecaDataset sd) {
        for (DatasetFrame f : frames) if (f.dataset() == sd) return f;
        return null;
    }

    /** Replaces {@code System.exit(0)} (tests). */
    public void setExitHook(Runnable hook) {
        this.exitHook = hook;
    }

    /**
     * Prepares the exit: asks every run to stop (does not wait: run threads are daemons and every log
     * write is flushed) and closes the log files.
     */
    public void shutdown() {
        for (DatasetFrame f : frames) {
            RunController rc = f.runs();
            for (RunSlot s : rc.slots()) rc.stop(s);
        }
        control.shutdownNow();
        logFiles.close();
    }

    /** {@link #shutdown()}, then the exit hook (the application: {@code System.exit(0)}). */
    public void exit() {
        shutdown();
        exitHook.run();
    }
}
