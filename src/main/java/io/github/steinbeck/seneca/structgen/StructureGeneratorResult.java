package io.github.steinbeck.seneca.structgen;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.chem.IsomorphismTester;
import io.github.steinbeck.seneca.chem.LegacyMdlReader;
import io.github.steinbeck.seneca.chem.LifoQueue;
import io.github.steinbeck.seneca.judges.ScoreSummary;

/**
 * The best structures of a run: two parallel LIFO queues (capacity 30) of MDL strings and
 * {@link ScoreSummary}s, newest first. Port of
 * {@code legacy/original/src/seneca/structgen/StructureGeneratorResult.java}; architecture.md section 7.
 *
 * <ul>
 *   <li>{@link #sort()}: bubble sort by score descending, swapping only on strictly greater (stable).</li>
 *   <li>{@link #removeIsomorphism}: switch point (D5). STRICT: legacy exactly - sort; the first loop
 *       drops the <b>two</b> lowest-scored structures (summaries are not truncated); with 0 structures
 *       the legacy loop never terminates (the port detects it and throws {@link IllegalStateException}),
 *       with 1-2 it throws; then for each i, every later j with equal score that is Morgan-"isomorphic"
 *       ({@code chem.IsomorphismTester} on {@code LegacyMdlReader}-parsed strings) is removed.
 *       DEFAULT: no truncation, no hang/throw for small lists; the same duplicate removal.</li>
 *   <li>{@link #merge}: legacy {@code merge} (capacity grows by the other's size; pushes in the other's
 *       order, so the merged order is reversed - "Summarize Result").</li>
 *   <li>{@link #copy()}: deep copy (the port always hands out copies: RMI semantics, D6).</li>
 * </ul>
 */
public final class StructureGeneratorResult {

    public static final int DEFAULT_CAPACITY = 30;

    public final LifoQueue<String> structures;
    public final LifoQueue<ScoreSummary> scoreSummaries;

    public StructureGeneratorResult() {
        this(DEFAULT_CAPACITY);
    }

    public StructureGeneratorResult(int capacity) {
        structures = new LifoQueue<>(capacity);
        scoreSummaries = new LifoQueue<>(capacity);
    }

    private StructureGeneratorResult(LifoQueue<String> s, LifoQueue<ScoreSummary> ss) {
        structures = s;
        scoreSummaries = ss;
    }

    public int size() {
        return structures.size();
    }

    /** Legacy {@code push} of a new best: both queues. */
    public void push(String mdl, ScoreSummary summary) {
        structures.push(mdl);
        scoreSummaries.push(summary);
    }

    /** Legacy {@code sort()}: bubble sort by score, descending; moves an entry up only on strictly greater. */
    public void sort() {
        boolean somethingsChanged;
        do {
            somethingsChanged = false;
            for (int f = 0; f < structures.size() - 1; f++) {
                if (scoreSummaries.get(f).score < scoreSummaries.get(f + 1).score) {
                    String o1 = structures.removeAt(f + 1);
                    ScoreSummary o2 = scoreSummaries.removeAt(f + 1);
                    structures.insert(f, o1);
                    scoreSummaries.insert(f, o2);
                    somethingsChanged = true;
                }
            }
        } while (somethingsChanged);
    }

    /**
     * Legacy {@code removeIsomorphism()} (switch point D5, see class doc). STRICT throws where legacy
     * throws (1 or 2 structures: {@link ArrayIndexOutOfBoundsException}) and reports the legacy endless
     * loop on an empty list as {@link IllegalStateException}.
     */
    public void removeIsomorphism(LegacyCompat compat) {
        sort();
        if (compat.strict()) {
            int s = structures.size();
            if (s == 0) {
                throw new IllegalStateException("StructureGeneratorResult.removeIsomorphism: the legacy code loops for ever on an empty result list");
            }
            LegacyMdlReader.read(structures.get(s - 1));     // legacy reads the last entry (never fails on our strings)
            s = s - 1;
            if (s - 1 < 0) throw new ArrayIndexOutOfBoundsException("Index -1 out of bounds for length " + structures.legacyArrayLength());
            structures.truncate(s - 1);                     // drops the two lowest-scored structures, summaries kept
            int i = 0;
            do {
                if (i >= structures.size()) throw new ArrayIndexOutOfBoundsException(i + " >= " + structures.size());
                HeavyAtom[] n1 = LegacyMdlReader.read(structures.get(i));
                int j = i + 1;
                do {
                    if (i != j && scoreSummaries.get(i).score == scoreSummaries.get(j).score) {
                        boolean isIsomorphic = false;
                        if (j < structures.size()) {         // legacy: elementAt(j) throws, caught -> false
                            isIsomorphic = IsomorphismTester.isIsomorph(n1, LegacyMdlReader.read(structures.get(j)));
                        }
                        if (isIsomorphic) {
                            structures.removeAt(j);
                            scoreSummaries.removeAt(j);
                        } else {
                            j++;
                        }
                    } else {
                        j++;
                    }
                } while (structures.size() > 0 && j < structures.size());
                i++;
            } while (structures.size() > 0 && i < structures.size());
            return;
        }
        for (int i = 0; i < structures.size(); i++) {
            HeavyAtom[] n1 = LegacyMdlReader.read(structures.get(i));
            int j = i + 1;
            while (j < structures.size()) {
                if (scoreSummaries.get(i).score == scoreSummaries.get(j).score
                        && IsomorphismTester.isIsomorph(n1, LegacyMdlReader.read(structures.get(j)))) {
                    structures.removeAt(j);
                    scoreSummaries.removeAt(j);
                } else {
                    j++;
                }
            }
        }
    }

    /**
     * Legacy {@code merge}: the capacity grows by {@code other.size()}, then the other's entries are pushed
     * in its order (so they end up reversed in front of this result's entries). The entries are shared, not
     * copied (merge copies if they must stay independent).
     */
    public void merge(StructureGeneratorResult other) {
        int n = other.size();
        structures.setCapacity(structures.capacity() + n);
        scoreSummaries.setCapacity(scoreSummaries.capacity() + n);
        for (int f = 0; f < n; f++) {
            structures.push(other.structures.get(f));
            scoreSummaries.push(other.scoreSummaries.get(f));
        }
    }

    /** Deep copy (strings are immutable; summaries copied). */
    public StructureGeneratorResult copy() {
        return new StructureGeneratorResult(structures.copy(s -> s), scoreSummaries.copy(ScoreSummary::copy));
    }
}
