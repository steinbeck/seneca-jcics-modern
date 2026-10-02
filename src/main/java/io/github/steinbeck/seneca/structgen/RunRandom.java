package io.github.steinbeck.seneca.structgen;

import java.util.Random;

/**
 * The random source of one run: a private {@link java.util.Random} (seeded or unseeded) replacing the
 * JVM-wide {@code Math.random()} of the legacy code (policy point 4, D13), with a draw counter.
 *
 * <p>Every legacy {@code Math.random()} call site (6 in {@code StochasticGenerator}, 2 in
 * {@code SingleRandomStructureGenerator}) becomes exactly one {@link #nextDouble()} call, in the same
 * order. For a seed {@code s} the sequence equals {@code new Random(s).nextDouble()}, which is what the
 * golden recording used ({@code Rng.seed(s)}); {@link #draws()} equals the golden
 * {@code draws}/{@code rngDraws}/{@code startDraws} counters. No other code may draw from it.
 *
 * <p>Not thread-safe by design: a run's random source is confined to the run's thread.
 */
public final class RunRandom {

    private final Random random;
    private final Long seed;
    private long draws = 0;

    private RunRandom(Random random, Long seed) {
        this.random = random;
        this.seed = seed;
    }

    /** Reproducible stream: {@code new Random(seed)}. */
    public static RunRandom seeded(long seed) {
        return new RunRandom(new Random(seed), seed);
    }

    /** Unseeded stream (the application's default; same distribution as {@code Math.random()}). */
    public static RunRandom unseeded() {
        return new RunRandom(new Random(), null);
    }

    /** The legacy {@code Math.random()} replacement: one draw in [0, 1). */
    public double nextDouble() {
        draws++;
        return random.nextDouble();
    }

    /** Number of {@link #nextDouble()} calls so far. */
    public long draws() {
        return draws;
    }

    /** @return the seed, or null if unseeded */
    public Long seed() {
        return seed;
    }
}
