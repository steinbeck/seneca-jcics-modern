package io.github.steinbeck.seneca.structgen;

/**
 * Sink for the engine's log output (replaces the log4j categories of architecture.md section 8).
 *
 * <ul>
 *   <li>{@link #info}: the INFO messages of {@code seneca.structgen.StochasticGenerator.log}, verbatim
 *       text including the trailing {@code "\n"} where legacy has one ("Start of StoicGent\n",
 *       "start_kT = ...", "Iteration: n ", "Current kT = ...\n", "New best Structure: \n" + previous best
 *       + "\n", convergence/reheat/abort messages, stop time, runtime, "Simulated annealing run finished.\n",
 *       FATAL "Error in StoicGent.run()"). Logbook tab and {@code ~/seneca.log}.</li>
 *   <li>{@link #result}: each new best structure, {@code mdl + "\n$$$$\n"} ({@code ~/seneca.results.sdf}).</li>
 * </ul>
 * Called on the generator thread; implementations must be thread-safe if shared between runs.
 * Debug-level messages of the legacy code are dropped.
 */
public interface EngineLog {

    void info(String message);

    void result(String sdfBlock);

    /** Discards everything. */
    EngineLog NONE = new EngineLog() {
        @Override
        public void info(String message) {
        }

        @Override
        public void result(String sdfBlock) {
        }
    };
}
