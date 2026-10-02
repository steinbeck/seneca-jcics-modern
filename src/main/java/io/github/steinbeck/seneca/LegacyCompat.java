package io.github.steinbeck.seneca;

/**
 * Compatibility switch for legacy failures (crashes, hangs, data loss) of the 2001 program.
 *
 * <p>Port policy (docs/port/design.md, section 5): everything that determines scores or the search is
 * reproduced exactly in <b>both</b> modes. The switch only selects what happens where the legacy code
 * <em>fails</em>:
 * <ul>
 *   <li>{@link #STRICT}: reproduce the legacy failure exactly (e.g. the compchem ring finder's
 *       {@code ArrayIndexOutOfBoundsException} ends the run as "exception_in_run"; a run whose first
 *       main-loop candidate is disconnected ends immediately). All golden tests run in this mode.</li>
 *   <li>{@link #DEFAULT}: the failure is fixed (used by the application). A fix must not change the
 *       random stream or any outcome of a run in which the legacy code does not fail.</li>
 * </ul>
 * Every switch point is listed in docs/port/DEVIATIONS.md.
 */
public enum LegacyCompat {
    /** Reproduce legacy failures bit for bit (golden-master mode). */
    STRICT,
    /** Fix legacy failures; identical to STRICT wherever the legacy code does not fail. */
    DEFAULT;

    /** @return true if legacy failures must be reproduced */
    public boolean strict() {
        return this == STRICT;
    }
}
