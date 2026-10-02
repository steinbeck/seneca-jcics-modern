package io.github.steinbeck.seneca.app;

import io.github.steinbeck.seneca.structgen.RunOptions;
import io.github.steinbeck.seneca.structgen.RunSlot;

/**
 * Hidden test hooks of the application (docs/port/gui-design.md, "Testing without a human"): they make a
 * run started from the GUI reproducible, so that it can be compared with a golden run. Not shown anywhere
 * in the GUI; set as system properties ({@code -Dseneca.seed=1}, or in {@code JAVA_OPTIONS} of the app).
 *
 * <ul>
 *   <li>{@value #SEED}: base seed. The run in slot {@code "local" + k} uses seed {@code base + k}, so
 *       "Start local server" three times, select all, "Start as selected" with {@code seneca.seed=1}
 *       reproduces the golden seeds 1, 2 and 3. Unset: unseeded runs (the application default).</li>
 *   <li>{@value #ITERATION_CAP}: the golden harness's deterministic iteration cap (the golden runs used
 *       5,000,000). Unset or 0: no cap.</li>
 * </ul>
 * The compatibility mode is not a hook: the application always runs {@code LegacyCompat.DEFAULT}.
 *
 * @param seed         base seed, null = unseeded
 * @param iterationCap 0 = none
 */
public record TestHooks(Long seed, long iterationCap) {

    public static final String SEED = "seneca.seed";
    public static final String ITERATION_CAP = "seneca.iterationCap";

    /** No hooks (unseeded, no cap). */
    public static TestHooks none() {
        return new TestHooks(null, 0);
    }

    /** From the system properties {@value #SEED} and {@value #ITERATION_CAP}. */
    public static TestHooks fromSystemProperties() {
        String s = System.getProperty(SEED);
        String c = System.getProperty(ITERATION_CAP);
        return new TestHooks(s == null || s.isBlank() ? null : Long.valueOf(s.trim()),
                c == null || c.isBlank() ? 0 : Long.parseLong(c.trim()));
    }

    /** {@code base} with the seed of {@code slot} and the cap applied. */
    public RunOptions apply(RunOptions base, RunSlot slot) {
        RunOptions o = base;
        if (seed != null) o = o.withSeed(seed + slotNumber(slot));
        if (iterationCap > 0) o = o.withIterationCap(iterationCap);
        return o;
    }

    /** k of slot id {@code "local" + k} (0 for any other id). */
    static long slotNumber(RunSlot slot) {
        String id = slot.id();
        if (!id.startsWith("local")) return 0;
        try {
            return Long.parseLong(id.substring("local".length()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
