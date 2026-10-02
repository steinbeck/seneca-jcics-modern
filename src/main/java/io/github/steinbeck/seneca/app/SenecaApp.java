package io.github.steinbeck.seneca.app;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.MissingResourceException;

import javax.swing.SwingUtilities;

import io.github.steinbeck.seneca.app.ui.UiServices;

/**
 * Entry point of the SENECA application (F-01, F-02, F-04, F-05; docs/port/gui-design.md).
 *
 * <pre>
 *   java -cp ... io.github.steinbeck.seneca.app.SenecaApp [options] [file.sml]
 *   Seneca.app/Contents/MacOS/Seneca [options] [file.sml]
 *   open -n Seneca.app --args [options] [file.sml]
 * </pre>
 * Sets the macOS properties (screen menu bar, application name, system appearance) before AWT starts,
 * loads the resources (missing: "Seneca.properties not found", exit 1), truncates the three log files,
 * selects the system look and feel, builds the main window on the EDT and opens the file argument if given
 * (failure: "Seneca: Could not opoen the file referenced by the command line parameters" on stderr, as
 * in 2001).
 *
 * <p>Options (additions of the port; each sets the system property named in brackets, so they also work
 * where JVM options cannot be passed, e.g. {@code open --args} on the packaged application):
 * <ul>
 *   <li>{@code --logdir=<dir>}: directory of the three log files instead of the home directory
 *       ({@value AppPaths#LOGDIR_PROPERTY});</li>
 *   <li>{@code --examples=<dir>}: directory of the example datasets ({@value Examples#PROPERTY});</li>
 *   <li>{@code --seed=<n>}, {@code --iteration-cap=<n>}: the hidden test hooks ({@link TestHooks});</li>
 *   <li>{@code --smoke[=<report file>]}: unattended self-test of the installed application ({@link Smoke}),
 *       then exit with 0 (passed) or 1.</li>
 * </ul>
 * Finder's {@code -psn_...} argument is ignored.
 */
public final class SenecaApp {

    private SenecaApp() {
    }

    /** Parsed command line. */
    record Arguments(String file, boolean smoke, Path smokeReport, List<String> unknown) {
    }

    public static void main(String[] args) {
        Arguments a = parse(args);
        for (String u : a.unknown()) System.err.println("Seneca: unknown option " + u + " (ignored)");
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("apple.awt.application.name", "Seneca");
        System.setProperty("apple.awt.application.appearance", "system");
        Resources resources;
        try {
            resources = Resources.load();
        } catch (MissingResourceException e) {
            System.err.println("Seneca.properties not found");
            System.exit(1);
            return;
        }
        if (a.smoke() && System.getProperty(TestHooks.SEED) == null) {
            // the self-test reproduces a golden run (Smoke): seed 1, the golden harness's cap
            System.setProperty(TestHooks.SEED, "1");
            if (System.getProperty(TestHooks.ITERATION_CAP) == null) System.setProperty(TestHooks.ITERATION_CAP, "5000000");
        }
        AppPaths paths = AppPaths.fromSystem();
        TestHooks hooks = TestHooks.fromSystemProperties();
        if (a.smoke()) {
            Smoke smoke = new Smoke(a.smokeReport(), a.file());
            smoke.installHandlers();
            SwingUtilities.invokeLater(() -> {
                try {
                    MainFrame f = start(resources, paths, hooks, null);
                    smoke.startAsync(f, code -> {
                        f.view().app().shutdown();
                        System.exit(code);
                    });
                } catch (RuntimeException | Error e) {
                    smoke.failStartup(e);
                    System.exit(1);
                }
            });
            return;
        }
        SwingUtilities.invokeLater(() -> start(resources, paths, hooks, a.file()));
    }

    /**
     * Options to system properties (see the class comment); returns the file argument (the first
     * argument that is not an option) and the smoke settings.
     */
    static Arguments parse(String[] args) {
        String file = null;
        boolean smoke = false;
        Path report = null;
        List<String> unknown = new ArrayList<>();
        for (String s : args) {
            if (s == null || s.isEmpty() || s.startsWith("-psn_")) continue;
            if (s.equals("--smoke")) {
                smoke = true;
            } else if (s.startsWith("--smoke=")) {
                smoke = true;
                report = Path.of(s.substring("--smoke=".length()));
            } else if (s.startsWith("--logdir=")) {
                System.setProperty(AppPaths.LOGDIR_PROPERTY, s.substring("--logdir=".length()));
            } else if (s.startsWith("--examples=")) {
                System.setProperty(Examples.PROPERTY, s.substring("--examples=".length()));
            } else if (s.startsWith("--seed=")) {
                System.setProperty(TestHooks.SEED, s.substring("--seed=".length()));
            } else if (s.startsWith("--iteration-cap=")) {
                System.setProperty(TestHooks.ITERATION_CAP, s.substring("--iteration-cap=".length()));
            } else if (s.startsWith("--")) {
                unknown.add(s);
            } else if (file == null) {
                file = s;
            }
        }
        return new Arguments(file, smoke, report, unknown);
    }

    /** Builds and shows the application (EDT); opens {@code file} if not null. */
    static MainFrame start(Resources resources, AppPaths paths, TestHooks hooks, String file) {
        LookAndFeels.applySystem();
        AppContext app = AppContext.create(resources, UiServices.swing(), paths, hooks);
        MainFrame frame = new MainFrame(app);
        MacIntegration.install(app, frame.view().actions(), MainMenuBar.create(app, frame.view().actions()));
        frame.setVisible(true);
        if (file != null) {
            if (frame.view().actions().files().openFile(new File(file), null) == null)
                System.err.println("Seneca: " + resources.text("OpenArgumentFailed"));
        }
        return frame;
    }
}
