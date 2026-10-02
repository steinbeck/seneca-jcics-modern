package io.github.steinbeck.seneca.structgen;

import java.util.function.BooleanSupplier;

import io.github.steinbeck.seneca.chem.AtomSets;
import io.github.steinbeck.seneca.chem.FloydApsp;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.judges.Attic;

/**
 * Random, saturated, connected start structure. Port of the class that exists only in the 2001 jar:
 * {@code legacy/runnable/src/seneca/structgen/SingleRandomStructureGenerator.java} (bytecode-identical
 * reconstruction) and {@code legacy/decompiled/seneca-client-2001/seneca/structgen/}, with base class
 * {@code legacy/original/src/seneca/structgen/StructureGenerator.java} ({@code setAtomSet} clones,
 * {@code structureConnected} via {@link FloydApsp}).
 * Algorithm and draw order: architecture.md 4.2, compchem-semantics.md appendix.
 *
 * <p>Draws: {@code getAnotherUnsaturatedNode}: one {@code nextDouble()} for the start index on every
 * call; one for the bond order {@code max(1, (int)(r * min(currentMaxBondOrder...)))} per bond formed.
 * Restarts (reset all atoms) until saturated and connected; never returns otherwise (an input that
 * cannot be saturated makes the legacy program loop for ever). {@link StartStructureFeasibility} decides
 * which inputs those are; {@link StochasticGenerator} rejects them in DEFAULT mode and passes a stop
 * check that is true only for them (DEVIATIONS.md DV-60). In DEFAULT it also gives up after
 * {@link StochasticGenerator#START_RESTART_LIMIT} restarts and starts from {@link #fromBondMatrix}
 * (DV-64): for molecules with many monovalent atoms the generator practically never succeeds.
 *
 * <p>Side effects kept: {@code attic.atomSet} is set to the generated array (so it aliases the
 * generator's atoms afterwards), {@code attic.apsp} = Floyd matrix of the start structure.
 */
public final class SingleRandomStructureGenerator {

    private SingleRandomStructureGenerator() {
    }

    /**
     * @param atoms  the generator's atoms; not modified (cloned first, as legacy {@code dg.setAtomSet(atomSet)} does)
     * @param attic  the run's attic (legacy: {@code dg.attic = attic})
     * @param random the run's random source
     * @return the bonded atom array (also stored in {@code attic.atomSet})
     */
    public static HeavyAtom[] generate(HeavyAtom[] atoms, Attic attic, RunRandom random) {
        return generate(atoms, attic, random, () -> false);
    }

    /**
     * Same, but gives up (returns null) when {@code stop} is true at a restart. The check draws no
     * random number and does not exist in legacy (the 2001 program could not be stopped here); callers
     * that must behave like legacy pass a check that is false for every input on which the generator
     * can succeed ({@link StochasticGenerator} does).
     */
    public static HeavyAtom[] generate(HeavyAtom[] atoms, Attic attic, RunRandom random, BooleanSupplier stop) {
        HeavyAtom[] atomSet = AtomSets.cloneAtomSet(atoms);      // setAtomSet
        int n = atomSet.length;
        for (HeavyAtom a : atomSet) a.reset();
        while (true) {
            boolean bondFormed;
            do {
                bondFormed = false;
                for (int f = 0; f < n; f++) {
                    if (!atomSet[f].isSaturated()) {
                        int partner = anotherUnsaturatedNode(atomSet, f, random);
                        if (partner != -1) {
                            int maxBondOrder = Math.min(atomSet[f].getCurrentMaxBondOrder(),
                                    atomSet[partner].getCurrentMaxBondOrder());
                            int order = Math.max(1, (int) (random.nextDouble() * maxBondOrder));
                            atomSet[f].formBond(atomSet[partner], order);
                            atomSet[partner].formBond(atomSet[f], order);
                            bondFormed = true;
                        }
                    }
                }
            } while (bondFormed);
            int unsaturated = 0;
            for (HeavyAtom a : atomSet) if (!a.isSaturated()) unsaturated++;
            attic.atomSet = atomSet;
            int[][] bondMatrix = AtomSets.createConnectionTable(attic.atomSet);
            attic.apsp = FloydApsp.compute(bondMatrix);
            if (unsaturated == 0 && structureConnected(attic.apsp)) return atomSet;
            for (HeavyAtom a : atomSet) a.reset();
            if (stop.getAsBoolean()) return null;
        }
    }

    /**
     * Port only (DV-64, DEFAULT): the start structure given by a bond-order matrix
     * ({@link StartStructureFeasibility#witness}) instead of a random one. Same side effects as
     * {@link #generate}: the atoms are cloned, {@code attic.atomSet} is set to the bonded array and
     * {@code attic.apsp} to its Floyd matrix. Bonds are formed in index order. Draws no random number.
     *
     * @throws IllegalStateException if the matrix does not give a saturated, connected structure
     */
    public static HeavyAtom[] fromBondMatrix(HeavyAtom[] atoms, Attic attic, int[][] orders) {
        HeavyAtom[] atomSet = AtomSets.cloneAtomSet(atoms);
        int n = atomSet.length;
        for (HeavyAtom a : atomSet) a.reset();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (orders[i][j] > 0) {
                    atomSet[i].formBond(atomSet[j], orders[i][j]);
                    atomSet[j].formBond(atomSet[i], orders[i][j]);
                }
            }
        }
        attic.atomSet = atomSet;
        attic.apsp = FloydApsp.compute(AtomSets.createConnectionTable(atomSet));
        for (HeavyAtom a : atomSet) {
            if (!a.isSaturated()) throw new IllegalStateException("constructed start structure: atom " + a.number + " is not saturated");
        }
        if (!structureConnected(attic.apsp)) throw new IllegalStateException("constructed start structure is not connected");
        return atomSet;
    }

    /** Legacy {@code getAnotherUnsaturatedNode}: one draw for the start index, then a cyclic scan. */
    private static int anotherUnsaturatedNode(HeavyAtom[] atomSet, int node, RunRandom random) {
        int start = (int) (random.nextDouble() * atomSet.length);
        for (int f = start; f < atomSet.length; f++) {
            if (!atomSet[f].isSaturated() && f != node && !atomSet[f].isBondedTo(atomSet[node])) return f;
        }
        for (int f = 0; f < start; f++) {
            if (!atomSet[f].isSaturated() && f != node && !atomSet[f].isBondedTo(atomSet[node])) return f;
        }
        return -1;
    }

    /** Legacy {@code StructureGenerator.structureConnected}: every pair has a path no longer than n. */
    private static boolean structureConnected(int[][] apsp) {
        int n = apsp.length;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (apsp[i][j] > apsp.length) return false;
            }
        }
        return true;
    }
}
