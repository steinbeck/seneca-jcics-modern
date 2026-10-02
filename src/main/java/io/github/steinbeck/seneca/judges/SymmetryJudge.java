package io.github.steinbeck.seneca.judges;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

import io.github.steinbeck.seneca.chem.AtomSets;

/**
 * Symmetry classes judge. Port of {@code legacy/original/src/seneca/judges/SymmetryJudge.java};
 * architecture.md 5.3.
 *
 * <ul>
 *   <li>Base field {@code score = 500}; {@link #addSymmetryClass} calls {@link #init()}.</li>
 *   <li>{@link #calcMaxScore()}: sum over classes of {@code AtomSets.faculty(count) / 2} (int, wraps
 *       for count &gt;= 13; NOT multiplied by 500 - kept, quirk D2). Uses the array built by init().</li>
 *   <li>{@link #evaluate}: Morgan numbers of {@code attic.atomSet}; +score per pair g &lt; h of a class
 *       with equal Morgan value; description {@code "" + scoreSum/score + " symmetry classes are completly filled."}.</li>
 * </ul>
 */
public class SymmetryJudge extends Judge {

    /** One class: membership flags per atom and the member count. */
    public static final class SymmetryClass {
        public final boolean[] symmetricAtoms;
        public final int symmetricAtomsCount;

        public SymmetryClass(boolean[] symmetricAtoms) {
            this.symmetricAtoms = symmetricAtoms;
            int c = 0;
            for (boolean b : symmetricAtoms) if (b) c++;
            this.symmetricAtomsCount = c;
        }
    }

    protected List<SymmetryClass> symmetryClassesVector = new ArrayList<>();
    protected SymmetryClass[] symmetryClasses;

    public SymmetryJudge() {
        super("SymmetryJudge");
        score = 500;
        hasMaxScore = true;
    }

    public int getSymmetryClassCount() {
        return symmetryClassesVector.size();
    }

    /** Adds a class and calls {@link #init()} (legacy). */
    public void addSymmetryClass(boolean[] symmetricAtoms) {
        symmetryClassesVector.add(new SymmetryClass(symmetricAtoms));
        init();
    }

    public void clearSymmetryClasses() {
        symmetryClassesVector.clear();
    }

    /** @return the flags of class n, or null if n is out of range */
    public boolean[] getSymmetryClass(int n) {
        return n < symmetryClassesVector.size() ? symmetryClassesVector.get(n).symmetricAtoms : null;
    }

    /** Legacy {@code getClassMember(classNumber, memberNumber)} (1-based member; GUI table only). */
    public int getClassMember(int classNumber, int memberNumber) {
        if (symmetryClasses.length > classNumber) {
            int counter = 0;
            for (int f = 0; f < symmetryClasses[classNumber].symmetricAtoms.length; f++) {
                if (symmetryClasses[classNumber].symmetricAtoms[f]) counter++;
                if (counter == memberNumber) return f;
            }
        }
        return -1;
    }

    public void setScore(int s) {
        score = s;
    }

    @Override
    public void init() {
        symmetryClasses = new SymmetryClass[symmetryClassesVector.size()];
        for (int f = 0; f < symmetryClassesVector.size(); f++) symmetryClasses[f] = symmetryClassesVector.get(f);
    }

    @Override
    public void calcMaxScore() {
        maxScore = 0;
        for (int f = 0; f < symmetryClassesVector.size(); f++) {
            maxScore += AtomSets.faculty(symmetryClasses[f].symmetricAtomsCount) / 2;
        }
    }

    @Override
    public JudgeResult evaluate(int[][] bm) {
        int sum = 0;
        int[] mm = AtomSets.computeMorganMatrix(attic.atomSet);
        for (int f = 0; f < symmetryClasses.length; f++) {
            boolean[] members = symmetryClasses[f].symmetricAtoms;
            for (int g = 0; g < members.length; g++) {
                if (g < members.length - 1 && members[g]) {
                    for (int h = g + 1; h < members.length; h++) {
                        if (members[h] && mm[g] == mm[h]) sum += score;
                    }
                }
            }
        }
        String message = "" + sum / score + " symmetry classes are completly filled.";
        return new JudgeResult(maxScore, sum, 0, message);
    }

    @Override
    public Judge copy() {
        SymmetryJudge c = new SymmetryJudge();
        copyBaseFieldsTo(c);
        // deep copy that keeps the aliasing between the class list and the init() array (serialization semantics)
        IdentityHashMap<SymmetryClass, SymmetryClass> copies = new IdentityHashMap<>();
        c.symmetryClassesVector = new ArrayList<>();
        for (SymmetryClass sc : symmetryClassesVector) c.symmetryClassesVector.add(copies.computeIfAbsent(sc, SymmetryJudge::copyOf));
        if (symmetryClasses != null) {
            c.symmetryClasses = new SymmetryClass[symmetryClasses.length];
            for (int f = 0; f < symmetryClasses.length; f++) {
                c.symmetryClasses[f] = symmetryClasses[f] == null ? null : copies.computeIfAbsent(symmetryClasses[f], SymmetryJudge::copyOf);
            }
        }
        return c;
    }

    private static SymmetryClass copyOf(SymmetryClass sc) {
        return new SymmetryClass(sc.symmetricAtoms.clone());
    }
}
