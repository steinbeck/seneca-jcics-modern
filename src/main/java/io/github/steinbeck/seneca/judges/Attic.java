package io.github.steinbeck.seneca.judges;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.chem.HeavyAtom;

/**
 * Shared scratch data of one run: the candidate structure the judges evaluate.
 *
 * <p>Reference: {@code legacy/original/src/seneca/structgen/Attic.java} (moved to the judges package
 * so that {@code judges} does not depend on {@code structgen}). Semantics:
 * <ul>
 *   <li>{@link #atomSet}: the candidate; the generator calls {@code AtomSets.synchronizeBonds(attic.atomSet, bm)}
 *       before every {@code getScore(bm)}. All judges read the candidate from here, not from {@code bm}.
 *       After the start structure is built, {@code attic.atomSet} and the generator's {@code atomSet}
 *       are the same array (architecture.md 4.1 step 3).</li>
 *   <li>{@link #apsp}: Floyd matrix of the <em>start structure</em> only (never updated; only the dead
 *       {@code WienerIndexJudge} would read it).</li>
 *   <li>{@link #normalized}: set by {@code setMolecule}, never read.</li>
 *   <li>{@link #compat}: port addition, the run's {@link LegacyCompat} mode, read by judges that have a
 *       switch point ({@code FeatureJudge}).</li>
 * </ul>
 * The legacy field {@code setOfAllRings} is unused and omitted.
 */
public final class Attic {
    public HeavyAtom[] atomSet;
    public HeavyAtom[] normalized;
    public int[][] apsp;
    public LegacyCompat compat = LegacyCompat.STRICT;
}
