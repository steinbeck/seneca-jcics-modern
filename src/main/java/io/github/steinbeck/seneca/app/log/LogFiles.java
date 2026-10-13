package io.github.steinbeck.seneca.app.log;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.logging.Logger;

import io.github.steinbeck.seneca.app.AppPaths;

/**
 * The three log files of the 2001 client (feature-inventory F-06, 7.7; architecture.md section 8),
 * reproducing its log4j configuration ({@code seneca/client/logging.properties}):
 *
 * <ul>
 *   <li>All three are truncated when the application starts ({@code Append=false}).</li>
 *   <li>{@code seneca.log} (A2 on category {@code seneca.structgen}, layout {@code %m%n}): every engine
 *       INFO message followed by "\n"; because log4j categories are additive, also every result block and
 *       every "Log Annealing Data" line (their categories are children of {@code seneca.structgen}).</li>
 *   <li>{@code seneca.results.sdf} (A3, {@code %m}): every new best structure, verbatim
 *       ({@code mdl + "\n$$$$\n"}).</li>
 *   <li>{@code seneca.annealing.log} (A5, {@code %m}): the "Log Annealing Data" output, verbatim.</li>
 * </ul>
 * Thread-safe; every write is flushed. An I/O error does not reach the caller: it is logged once and that
 * file is no longer written (log4j swallowed errors, too). After {@link #close()} writes are ignored.
 *
 * <p>Verified against a legacy run with {@code -Duser.home=<scratch>}.
 */
public final class LogFiles implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(LogFiles.class.getName());

    private BufferedWriter log, results, annealing;

    private LogFiles(BufferedWriter log, BufferedWriter results, BufferedWriter annealing) {
        this.log = log;
        this.results = results;
        this.annealing = annealing;
    }

    /** Truncates and opens the three files of {@code paths}; a file that cannot be opened is skipped. */
    public static LogFiles open(AppPaths paths) {
        return new LogFiles(open(paths.logFile()), open(paths.resultsFile()), open(paths.annealingLogFile()));
    }

    /** Writes nothing. */
    public static LogFiles disabled() {
        return new LogFiles(null, null, null);
    }

    private static BufferedWriter open(Path p) {
        try {
            if (p.getParent() != null) Files.createDirectories(p.getParent());
            return Files.newBufferedWriter(p, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException e) {
            LOG.warning("Seneca: cannot open log file " + p + ": " + e);
            return null;
        }
    }

    /** An engine INFO message: {@code seneca.log} ({@code %m%n}). */
    public synchronized void info(String message) {
        log = write(log, message + "\n");
    }

    /** A result SD block: {@code seneca.results.sdf} verbatim, and {@code seneca.log} ({@code %m%n}). */
    public synchronized void result(String sdfBlock) {
        results = write(results, sdfBlock);
        log = write(log, sdfBlock + "\n");
    }

    /** One "Log Annealing Data" message: {@code seneca.annealing.log} verbatim, and {@code seneca.log} ({@code %m%n}). */
    public synchronized void annealing(String message) {
        annealing = write(annealing, message);
        log = write(log, message + "\n");
    }

    private static BufferedWriter write(BufferedWriter w, String s) {
        if (w == null) return null;
        try {
            w.write(s);
            w.flush();
            return w;
        } catch (IOException e) {
            LOG.warning("Seneca: log file write failed, file disabled: " + e);
            try {
                w.close();
            } catch (IOException ignored) {
                // already failing
            }
            return null;
        }
    }

    @Override
    public synchronized void close() {
        for (BufferedWriter w : new BufferedWriter[] {log, results, annealing}) {
            if (w == null) continue;
            try {
                w.close();
            } catch (IOException e) {
                LOG.warning("Seneca: closing a log file failed: " + e);
            }
        }
        log = results = annealing = null;
    }
}
