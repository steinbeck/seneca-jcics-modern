package io.github.steinbeck.seneca.app;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JCheckBox;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JRootPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import io.github.steinbeck.seneca.app.dataset.DatasetFrame;
import io.github.steinbeck.seneca.app.run.StructureGenerationPanel;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.judges.Judge;
import io.github.steinbeck.seneca.judges.TwoDSpectrumJudge;
import io.github.steinbeck.seneca.model.SenecaDataset;
import io.github.steinbeck.seneca.structgen.RunResult;
import io.github.steinbeck.seneca.structgen.RunSlot;

/**
 * Unattended self-test of an installed application ({@code --smoke[=<report>]}, {@link SenecaApp}): the
 * launch test of the packaged {@code Seneca.app} (scripts/build-app.sh, scripts/launch-test.sh). It runs in
 * the real application, with its window on the screen, and presses the buttons a user presses
 * ({@code doClick()} on the named controls, as the GUI tests do):
 *
 * <ol>
 *   <li>open the file argument, else the example "pinene" ({@link Examples}), through the same code as
 *       File &gt; Open;</li>
 *   <li>show and paint every tab of the dataset window (PNG files next to the report);</li>
 *   <li>the golden "gui" recipe: Autoassignment iff no shifts were read, "Autoconfigure Judge" for
 *       H,H-COSY, HMBC, Carbon Shifts (HOSE code) and Features, untick a 2D judge left without assignment,
 *       a default schedule iff none;</li>
 *   <li>Structure Generation: "Add run slot", select it, "Start as selected"; wait for the end (at most 5
 *       minutes, then "Stop Selected": a run that does not converge is reported, not failed); one poll
 *       (best structure, evaluation, chart); "Summarize Result";</li>
 *   <li>macOS: App Nap was suspended while the run went on and is restored afterwards ({@link MacActivity});</li>
 *   <li>report: Java runtime, look and feel, examples, dataset, run (iterations, seconds, iterations per
 *       second, best score), every
 *       uncaught exception and every log record of level WARNING or above.</li>
 * </ol>
 * Passed (exit code 0) iff every step finished and nothing was thrown or logged at WARNING or above.
 * Without {@code --seed} the run is seeded with 1 and capped at 5,000,000 iterations (set by
 * {@link SenecaApp}), so for pinene the report must show the golden run pinene.gui.seed1: 21248 iterations,
 * best score 2400. Nothing here changes the application's behaviour without {@code --smoke}.
 */
final class Smoke {

    static final long STEP_SECONDS = 60;
    static final long RUN_SECONDS = 300;

    private final Path report;
    private final String file;
    private final List<String> lines = Collections.synchronizedList(new ArrayList<>());
    private final List<String> problems = Collections.synchronizedList(new ArrayList<>());

    Smoke(Path report, String file) {
        this.report = report == null ? null : report.toAbsolutePath();
        this.file = file;
    }

    /** Before AWT starts: collect uncaught exceptions (any thread, the EDT included) and WARNING+ log records. */
    void installHandlers() {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> problems.add("uncaught exception in " + t.getName() + ": " + stack(e)));
        Logger root = Logger.getLogger("");
        root.addHandler(new Handler() {
            @Override
            public void publish(LogRecord r) {
                if (r.getLevel().intValue() < Level.WARNING.intValue()) return;
                String msg = r.getLevel() + " " + r.getLoggerName() + ": " + r.getMessage();
                if (r.getThrown() != null) msg += "\n" + stack(r.getThrown());
                problems.add(msg);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
    }

    /** The main window could not be built. */
    void failStartup(Throwable e) {
        problems.add("startup failed: " + stack(e));
        finish();
    }

    /** Runs the self-test on its own thread; {@code exit} receives 0 (passed) or 1. */
    void startAsync(MainFrame frame, IntConsumer exit) {
        Thread t = new Thread(() -> {
            int code;
            try {
                run(frame);
            } catch (Throwable e) {
                problems.add("smoke test aborted: " + stack(e));
            }
            code = finish();
            exit.accept(code);
        }, "seneca-smoke");
        t.setDaemon(true);
        t.start();
    }

    private void run(MainFrame frame) throws Exception {
        long t0 = System.currentTimeMillis();
        AppContext app = frame.view().app();
        line("java.version", System.getProperty("java.version"));
        line("java.home", System.getProperty("java.home"));
        line("jpackage.app-path", System.getProperty("jpackage.app-path"));
        line("app.version", Resources.version());
        line("lookAndFeel", edt(() -> UIManager.getLookAndFeel().getClass().getName()));
        line("logdir", app.paths.logDir());
        line("examples.dir", app.examples.dir());
        line("examples", app.examples.files().size());
        line("hooks", app.hooks);

        // 1. open
        File f = file != null ? new File(file) : null;
        if (f == null) {
            Path p = app.examples.file("pinene");
            if (p == null && !app.examples.files().isEmpty()) p = app.examples.files().get(0);
            if (p == null) throw new IllegalStateException("no file argument and no example datasets");
            f = p.toFile();
        }
        File open = f;
        line("open", open);
        SenecaDataset sd = edt(() -> app.mainView().actions().files().openFile(open, app.mainView()));
        if (sd == null) throw new IllegalStateException("could not open " + open);
        DatasetFrame df = edt(() -> app.frameOf(sd));
        line("dataset", sd.getName() + " " + sd.getMolecularFormula() + ", " + sd.getSetOfNodes().length + " heavy atoms");
        line("menu.exampleDatasets", edt(() -> exampleMenuItems(frame, app.resources)));

        // 2. every tab painted
        List<String> tabs = edt(() -> {
            List<String> l = new ArrayList<>();
            JTabbedPane tp = df.tabs();
            for (int i = 0; i < tp.getTabCount(); i++) l.add(tp.getTitleAt(i));
            return l;
        });
        for (String tab : tabs) {
            edt(() -> {
                df.showTab(tab);
                return null;
            });
            paint(frame, "tab-" + tab);
        }
        line("tabs", tabs);

        // 3. the "gui" recipe through the buttons
        edt(() -> {
            df.showTab("Atom Properties");
            boolean anyShift = false;
            for (HeavyAtom a : sd.getSetOfNodes()) anyShift |= a.shift13C != null;
            if (!anyShift) button(df, ComponentNames.AUTOASSIGN).doClick();
            df.showTab("Judges");
            for (String judge : List.of("HHCOSYJudge", "HMBCJudge", "HOSECodeJudge", "FeatureJudge")) {
                button(df, ComponentNames.JUDGE_AUTOCONFIGURE + judge).doClick();
                Judge j = sd.getJudge(judge);
                if (j instanceof TwoDSpectrumJudge two && j.getEnabled() && two.assignment == null)
                    named(df, ComponentNames.JUDGE_ACTIVATE + judge, JCheckBox.class).doClick();
            }
            df.showTab("Annealing Schedule");
            if (sd.annealingSchedules.isEmpty()) button(df, ComponentNames.SCHEDULE_NEW).doClick();
            return null;
        });
        List<String> enabled = edt(() -> {
            List<String> l = new ArrayList<>();
            for (Judge j : sd.judges) if (j.getEnabled()) l.add(j.name);
            return l;
        });
        line("judges.enabled", enabled);

        // 4. Structure Generation: one slot, start, wait, poll, summarize
        edt(() -> {
            df.showTab("Structure Generation");
            button(df, ComponentNames.RUN_ADD_SLOT).doClick();
            return null;
        });
        app.awaitControl(STEP_SECONDS * 1000);
        edt(() -> {
            JTable table = named(df, ComponentNames.RUN_TABLE, JTable.class);
            table.setRowSelectionInterval(0, 0);
            button(df, ComponentNames.RUN_START).doClick();
            return null;
        });
        app.awaitControl(STEP_SECONDS * 1000);
        RunSlot slot = df.runs().slots().get(0);
        line("run.options", app.runOptions(slot));
        long runStart = System.currentTimeMillis();
        long end = runStart + RUN_SECONDS * 1000;
        boolean napSuspended = false;
        while (slot.lastResult() == null && slot.failure() == null && System.currentTimeMillis() < end) {
            Thread.sleep(100);
            if (slot.isRunning() && app.runActivity.active() && MacActivity.held()) napSuspended = true;
        }
        boolean stopped = false;
        if (slot.lastResult() == null && slot.failure() == null) {
            // a dataset that does not converge (e.g. narelin "gui" keeps reheating in DEFAULT): the
            // application works, the self-test just stops waiting ("Stop Selected") and reports it
            stopped = true;
            edt(() -> {
                button(df, ComponentNames.RUN_STOP).doClick();
                return null;
            });
            long stopEnd = System.currentTimeMillis() + STEP_SECONDS * 1000;
            while (slot.lastResult() == null && slot.failure() == null && System.currentTimeMillis() < stopEnd) Thread.sleep(100);
        }
        double runSeconds = (System.currentTimeMillis() - runStart) / 1000.0;
        if (slot.failure() != null) throw new IllegalStateException("run failed", slot.failure());
        RunResult r = slot.lastResult();
        if (r == null) throw new IllegalStateException("run did not end within " + RUN_SECONDS + " s and did not stop");
        if (stopped) line("run.stoppedBySmokeTestAfterSeconds", RUN_SECONDS);
        line("run.status", r.goldenStatus());
        line("run.iterations", r.iterations());
        line("run.seconds", runSeconds);
        line("run.iterationsPerSecond", Math.round(r.iterations() / Math.max(0.001, runSeconds)));
        // macOS: App Nap must be suspended while the run goes on and restored afterwards (MacActivity)
        if (MacActivity.supported()) {
            Thread.sleep(200);                     // the final status reaches RunActivity on the run thread
            line("run.appNapSuspended", napSuspended);
            line("appNapSuspendedAfterRun", MacActivity.held());
            if (!napSuspended && r.iterations() > 0 && runSeconds > 1) problems.add("App Nap was not suspended during the run");
            if (MacActivity.held()) problems.add("App Nap still suspended after the run");
        }
        line("run.bestScore", r.bestScore() == null ? "none" : r.bestScore().score + "/" + r.maxScore());
        line("run.results", r.result().size());
        String shown = edt(() -> {
            StructureGenerationPanel p = find(df, StructureGenerationPanel.class);
            if (p == null) throw new IllegalStateException("no Structure Generation panel");
            p.poll();
            return p.bestStructureTitle() + ", drawn=" + p.bestStructureView().structure().drawable();
        });
        line("run.display", shown);
        paint(frame, "run-finished");
        edt(() -> {
            button(df, ComponentNames.RUN_SUMMARIZE).doClick();
            return null;
        });
        line("logfile.bytes", Files.size(app.paths.logFile()));
        line("results.sdf.bytes", Files.size(app.paths.resultsFile()));
        line("seconds", (System.currentTimeMillis() - t0) / 1000.0);
    }

    private int finish() {
        boolean ok = problems.isEmpty();
        StringBuilder sb = new StringBuilder("SENECA smoke test\n");
        synchronized (lines) {
            for (String l : lines) sb.append(l).append('\n');
        }
        synchronized (problems) {
            sb.append("problems=").append(problems.size()).append('\n');
            for (String p : problems) sb.append("PROBLEM ").append(p).append('\n');
        }
        sb.append("status=").append(ok ? "PASSED" : "FAILED").append('\n');
        System.out.print(sb);
        System.out.flush();
        if (report != null) {
            try {
                if (report.getParent() != null) Files.createDirectories(report.getParent());
                Files.writeString(report, sb.toString(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                System.err.println("Seneca: cannot write the smoke report " + report + ": " + e);
            }
        }
        return ok ? 0 : 1;
    }

    private void line(String key, Object value) {
        lines.add(key + "=" + value);
    }

    /** The root pane of {@code frame} painted into an image; written as PNG next to the report. */
    private void paint(MainFrame frame, String name) throws Exception {
        BufferedImage img = edt(() -> {
            JRootPane root = frame.getRootPane();
            int w = Math.max(1, root.getWidth()), h = Math.max(1, root.getHeight());
            BufferedImage i = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = i.createGraphics();
            try {
                root.paint(g);
            } finally {
                g.dispose();
            }
            return i;
        });
        if (report != null) {
            String png = "smoke-" + name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-") + ".png";
            Path out = report.resolveSibling(png);
            Files.createDirectories(out.getParent());
            ImageIO.write(img, "png", out.toFile());
        }
    }

    /** Labels under File &gt; Open &gt; Example Dataset of the main window's menu bar. */
    private static List<String> exampleMenuItems(MainFrame frame, Resources r) {
        List<String> l = new ArrayList<>();
        JMenuBar bar = frame.getJMenuBar();
        if (bar == null) return l;
        JMenu fileMenu = bar.getMenu(0);
        for (int i = 0; i < fileMenu.getItemCount(); i++) {
            if (!(fileMenu.getItem(i) instanceof JMenu open) || !open.getText().equals(r.text("openLabel"))) continue;
            for (int k = 0; k < open.getItemCount(); k++) {
                if (!(open.getItem(k) instanceof JMenu ex) || !ex.getText().equals(r.text("examplesLabel"))) continue;
                for (int m = 0; m < ex.getItemCount(); m++) l.add(ex.getItem(m).getText());
            }
        }
        return l;
    }

    /** Calls {@code c} on the EDT, waiting at most {@link #STEP_SECONDS} (a modal dialog would block it). */
    private static <T> T edt(Callable<T> c) throws Exception {
        FutureTask<T> task = new FutureTask<>(c);
        SwingUtilities.invokeLater(task);
        return task.get(STEP_SECONDS, TimeUnit.SECONDS);
    }

    private static AbstractButton button(Component root, String name) {
        return named(root, name, AbstractButton.class);
    }

    private static <T extends Component> T named(Component root, String name, Class<T> type) {
        T c = find(root, name, type);
        if (c == null) throw new IllegalStateException("no control named " + name);
        return c;
    }

    private static <T extends Component> T find(Component root, String name, Class<T> type) {
        if (type.isInstance(root) && name.equals(root.getName())) return type.cast(root);
        if (root instanceof Container k)
            for (Component ch : k.getComponents()) {
                T t = find(ch, name, type);
                if (t != null) return t;
            }
        return null;
    }

    private static <T extends Component> T find(Component root, Class<T> type) {
        if (type.isInstance(root)) return type.cast(root);
        if (root instanceof Container k)
            for (Component ch : k.getComponents()) {
                T t = find(ch, type);
                if (t != null) return t;
            }
        return null;
    }

    private static String stack(Throwable e) {
        StringWriter w = new StringWriter();
        e.printStackTrace(new PrintWriter(w));
        return w.toString().trim();
    }
}
