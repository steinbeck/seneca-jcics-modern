package io.github.steinbeck.seneca.app.log;

import java.io.PrintStream;

import io.github.steinbeck.seneca.structgen.EngineLog;

/**
 * The {@link EngineLog} of every run started by the application (one shared, thread-safe instance),
 * reproducing the 2001 log4j configuration ({@code seneca/client/logging.properties}; architecture.md
 * section 8; categories are additive):
 *
 * <ul>
 *   <li>INFO messages (category {@code ...StochasticGenerator.log}): the {@link Logbook} (appender A4,
 *       {@code %m}, verbatim) and {@code seneca.log} (A2, {@code %m%n}), see {@link LogFiles#info}.</li>
 *   <li>Result blocks ({@code ...StochasticGenerator.results}): {@code seneca.results.sdf} (A3) and
 *       {@code seneca.log} (A2), see {@link LogFiles#result}.</li>
 *   <li>The FATAL message "Error in StoicGent.run()" also reached the root appender A1 (stdout,
 *       threshold ERROR, {@code %p [%t] %c{2} (%M:%L) - %m%n}): the same line is printed to
 *       {@link #stdout} ({@code FATAL [<thread>] StochasticGenerator.log (run:384) - Error in StoicGent.run()}).</li>
 * </ul>
 * The Logbook gets whole lines (DV-90): the engine writes a progress line in two messages
 * ({@code "Iteration: n "}, then {@code "Current kT = ...\n"}); with several runs at once the halves of
 * different runs interleaved ("Iteration: 17000 Iteration: 18000 Current kT = ..."). Each run thread's
 * messages are therefore collected until one ends with a newline and then appended together. A single
 * run's Logbook text is unchanged; {@code seneca.log} gets every message at once, as in 2001 (log4j wrote
 * each message as its own line there, so nothing interleaved within a line).
 */
public final class AppEngineLog implements EngineLog {

    /** The engine's message for a run that ended with an exception (legacy FATAL). */
    public static final String FATAL_MESSAGE = "Error in StoicGent.run()";

    private final Logbook logbook;
    private final LogFiles files;
    private volatile PrintStream stdout = System.out;
    /** Per run thread: the start of a Logbook line not yet ended by "\n". */
    private final ThreadLocal<StringBuilder> partial = ThreadLocal.withInitial(StringBuilder::new);

    /** A partial line longer than this is appended anyway (never happens with the engine's messages). */
    static final int MAX_PARTIAL = 64 * 1024;

    public AppEngineLog(Logbook logbook, LogFiles files) {
        this.logbook = logbook;
        this.files = files;
    }

    /** Where the A1 (console) line goes; {@code System.out} by default (tests capture it). */
    public void setStdout(PrintStream out) {
        this.stdout = out;
    }

    @Override
    public void info(String message) {
        StringBuilder line = partial.get();
        if (message.startsWith(FATAL_MESSAGE)) {
            // its own style in the Logbook: the line begun before goes first
            if (line.length() > 0) logbook.append(line.toString());
            line.setLength(0);
            logbook.append(message);
        } else {
            line.append(message);
            if (message.endsWith("\n") || line.length() > MAX_PARTIAL) {
                logbook.append(line.toString());
                line.setLength(0);
            }
        }
        files.info(message);
        if (message.startsWith(FATAL_MESSAGE))
            stdout.println("FATAL [" + Thread.currentThread().getName() + "] StochasticGenerator.log (run:384) - " + FATAL_MESSAGE);
    }

    @Override
    public void result(String sdfBlock) {
        files.result(sdfBlock);
    }
}
