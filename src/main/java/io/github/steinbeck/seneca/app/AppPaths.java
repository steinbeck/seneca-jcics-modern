package io.github.steinbeck.seneca.app;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Where the application writes (docs/port/gui-design.md, section "State").
 *
 * <ul>
 *   <li>Log files: {@code seneca.log}, {@code seneca.results.sdf}, {@code seneca.annealing.log} in
 *       {@link #logDir()}: the user's home directory as in 2001 (feature-inventory F-06, 7.7), or the
 *       directory given by the system property {@value #LOGDIR_PROPERTY}.</li>
 *   <li>Preferences (legacy {@code ~/seneca.props}, 7.8): {@code ~/Library/Application Support/io.github.steinbeck.seneca/seneca.props}
 *       on macOS (the folder is named after the bundle id: the plain "Seneca" folder is used by another
 *       SENECA program on the same Mac, whose saved directories must not steer this application's file
 *       dialogs, DV-78), {@code ~/seneca.props} elsewhere.</li>
 * </ul>
 * Tests use {@link #under(Path)} so that nothing is written to the real home directory.
 */
public record AppPaths(Path logDir, Path preferencesFile) {

    /** System property: directory of the three log files instead of the home directory. */
    public static final String LOGDIR_PROPERTY = "seneca.logdir";

    /** macOS folder below {@code ~/Library/Application Support} (the bundle identifier). */
    public static final String MAC_SUPPORT_FOLDER = "io.github.steinbeck.seneca";

    public static AppPaths fromSystem() {
        Path home = Path.of(System.getProperty("user.home"));
        String logDir = System.getProperty(LOGDIR_PROPERTY);
        boolean mac = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac");
        Path prefs = mac ? home.resolve("Library").resolve("Application Support").resolve(MAC_SUPPORT_FOLDER).resolve("seneca.props")
                : home.resolve("seneca.props");
        return new AppPaths(logDir == null ? home : Path.of(logDir), prefs);
    }

    /** Everything below {@code dir} (tests: a scratch directory). */
    public static AppPaths under(Path dir) {
        return new AppPaths(dir, dir.resolve("seneca.props"));
    }

    /** {@code seneca.log}: engine INFO messages (the Logbook text). */
    public Path logFile() {
        return logDir.resolve("seneca.log");
    }

    /** {@code seneca.results.sdf}: every new best structure of every run. */
    public Path resultsFile() {
        return logDir.resolve("seneca.results.sdf");
    }

    /** {@code seneca.annealing.log}: the "Log Annealing Data" table. */
    public Path annealingLogFile() {
        return logDir.resolve("seneca.annealing.log");
    }
}
