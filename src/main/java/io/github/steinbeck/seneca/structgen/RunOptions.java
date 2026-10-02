package io.github.steinbeck.seneca.structgen;

import io.github.steinbeck.seneca.LegacyCompat;

/**
 * Per-run options that do not exist in the legacy program (seed, compatibility mode, test hooks).
 *
 *
 * @param seed          null = unseeded {@link RunRandom} (application default), else {@code new Random(seed)}
 * @param compat        legacy failure handling (golden tests: STRICT; application: DEFAULT)
 * @param iterationCap  0 = none. Otherwise the golden harness's deterministic cap: the first
 *                      {@code mutate()} call after {@code iteration >= iterationCap} calls {@code end()}
 *                      (then the step in progress completes, golden/FORMAT.md (d))
 * @param observer      optional step observer (golden trajectories, diagnostics); null = none
 * @param log           engine log sink (Logbook, seneca.log, seneca.results.sdf); never null
 */
public record RunOptions(Long seed, LegacyCompat compat, long iterationCap, StepObserver observer, EngineLog log) {

    public RunOptions {
        if (compat == null) throw new IllegalArgumentException("compat");
        if (log == null) log = EngineLog.NONE;
    }

    /** Application defaults: unseeded, DEFAULT mode, no cap, no observer, no log. */
    public static RunOptions application() {
        return new RunOptions(null, LegacyCompat.DEFAULT, 0, null, EngineLog.NONE);
    }

    /** Golden-master settings: seeded, STRICT. */
    public static RunOptions golden(long seed) {
        return new RunOptions(seed, LegacyCompat.STRICT, 0, null, EngineLog.NONE);
    }

    public RunOptions withSeed(Long s) {
        return new RunOptions(s, compat, iterationCap, observer, log);
    }

    public RunOptions withCompat(LegacyCompat c) {
        return new RunOptions(seed, c, iterationCap, observer, log);
    }

    public RunOptions withIterationCap(long cap) {
        return new RunOptions(seed, compat, cap, observer, log);
    }

    public RunOptions withObserver(StepObserver o) {
        return new RunOptions(seed, compat, iterationCap, o, log);
    }

    public RunOptions withLog(EngineLog l) {
        return new RunOptions(seed, compat, iterationCap, observer, l);
    }
}
