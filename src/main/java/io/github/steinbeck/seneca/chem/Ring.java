package io.github.steinbeck.seneca.chem;

import java.util.Arrays;
import java.util.Vector;

/**
 * compchem {@code Ring}: a cyclic sequence of (cloned) atoms as found by {@link RingFinder}.
 *
 * <p>Reference: {@code legacy/decompiled/compchem/compchem/Ring.java};
 * {@code docs/legacy/compchem-semantics.md} 6.1. {@link #sort()} (rotate to the smallest
 * {@code number}, continue towards its smaller-numbered ring neighbour; legacy
 * {@code shiftLeft}/{@code shiftRight}/{@code invert}) and {@link #getEdge(int)} are exact, because
 * {@code checkEdges} picks the edge to break by index. {@link #key()} is the {@code ringAlreadyInSet}
 * key ({@code getSortedString}: sorted atom numbers joined with "-").
 *
 * <p>The atoms are kept in a {@link Vector} accessed with {@code elementAt}, like compchem's
 * {@code Ring extends Vector}, so that out-of-range accesses in degenerate cases fail with the same
 * exception class and message as legacy.
 */
public final class Ring {

    /** Ring atoms in cyclic order (clones owned by the ring finder; {@code number} = index). */
    public final Vector<HeavyAtom> atoms = new Vector<>();

    public int size() {
        return atoms.size();
    }

    /** compchem {@code Ring.sort()}: normalise the cyclic order (returns this). */
    public Ring sort() {
        int min = atoms.elementAt(0).number;
        int minIndex = 0;
        int size = atoms.size();
        for (int i = 1; i < size; i++) {
            if (atoms.elementAt(i).number < min) {
                min = atoms.elementAt(i).number;
                minIndex = i;
            }
        }
        if (minIndex == size - 1) {
            shiftLeft(1);
            minIndex--;
        } else if (minIndex == 0) {
            shiftRight(1);
            minIndex++;
        }
        if (atoms.elementAt(minIndex + 1).number < atoms.elementAt(minIndex - 1).number) {
            shiftLeft(minIndex);
        } else {
            shiftRight(atoms.size() - 1 - minIndex);
            invert();
        }
        return this;
    }

    void shiftLeft(int n) {
        for (int i = 0; i < n; i++) {
            HeavyAtom a = atoms.elementAt(0);
            atoms.removeElementAt(0);
            atoms.addElement(a);
        }
    }

    void shiftRight(int n) {
        for (int i = 0; i < n; i++) {
            HeavyAtom a = atoms.elementAt(atoms.size() - 1);
            atoms.removeElementAt(atoms.size() - 1);
            atoms.insertElementAt(a, 0);
        }
    }

    /** compchem {@code invert()}: insert-then-truncate reversal (a correct reversal). */
    void invert() {
        int size = atoms.size();
        for (int i = 0; i < size; i++) atoms.insertElementAt(atoms.elementAt(atoms.size() - 1 - i), i);
        atoms.setSize(size);
    }

    /**
     * compchem {@code Ring.getEdge(i)}: {@code (atoms[i], atoms[i+1])} for {@code i < size-1},
     * else {@code (atoms[0], atoms[size-1])}.
     */
    public HeavyAtom[] getEdge(int i) {
        if (i < atoms.size() - 1) return new HeavyAtom[] {atoms.elementAt(i), atoms.elementAt(i + 1)};
        return new HeavyAtom[] {atoms.elementAt(0), atoms.elementAt(atoms.size() - 1)};
    }

    /** {@code ringAlreadyInSet} comparison key ({@code getSortedString}): sorted atom numbers joined with "-". */
    public String key() {
        int[] numbers = new int[atoms.size()];
        for (int i = 0; i < numbers.length; i++) numbers[i] = atoms.elementAt(i).number;
        Arrays.sort(numbers);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < numbers.length - 1; i++) sb.append(numbers[i]).append('-');
        return sb.append(numbers[numbers.length - 1]).toString();
    }
}
