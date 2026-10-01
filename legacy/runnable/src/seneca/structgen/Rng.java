/* Rng.java
 *
 * PORT NOTE (2026): New class, not part of the 2001 code base.
 * Determinism hook for the structure generator. All random draws of the
 * generator (StochasticGenerator, SingleRandomStructureGenerator) go through
 * Rng.random(), which replaces the former Math.random() calls one-to-one.
 *
 * Unseeded, Rng behaves like Math.random(): it is backed by one unseeded
 * java.util.Random (which is exactly what Math.random() uses internally),
 * returning uniformly distributed doubles in [0.0, 1.0).
 * After Rng.seed(s), the sequence of draws is reproducible.
 */

package seneca.structgen;

import java.util.Random;

public final class Rng
{
	private static Random random = new Random();

	private Rng()
	{
	}

	/** Re-seeds the single shared generator. Subsequent draws are reproducible. */
	public static synchronized void seed(long seed)
	{
		random = new Random(seed);
	}

	/** Drop-in replacement for Math.random(): a double in [0.0, 1.0). */
	public static synchronized double random()
	{
		return random.nextDouble();
	}
}
