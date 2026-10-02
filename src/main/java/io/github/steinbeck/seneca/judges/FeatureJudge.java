package io.github.steinbeck.seneca.judges;

import java.util.Hashtable;
import java.util.Map;
import java.util.Vector;

import io.github.steinbeck.seneca.chem.AtomSets;
import io.github.steinbeck.seneca.chem.CdkBridge;
import io.github.steinbeck.seneca.chem.Ring;
import io.github.steinbeck.seneca.chem.RingFinder;

/**
 * Ring-size and hetero-hetero bond penalties. Port of
 * {@code legacy/original/src/seneca/judges/FeatureJudge.java}; architecture.md 5.4.
 *
 * <ul>
 *   <li>{@link #addRingSizeScore}: score 0 is not stored.</li>
 *   <li>{@link #init()}: {@code ringsizeRestrictionEnabled = ringSizeScores.size() > 0}.
 *       {@link #calcMaxScore()} runs before init() in practice, so max = 0 (quirk Q3, keep).</li>
 *   <li>{@link #evaluate}: if ring restriction: {@code judgeRingScore()} is called <b>twice</b> (the first
 *       result discarded; keep the double call - it matters for the failure modes' timing only in that
 *       the first call already throws), summing {@code ringSizeScores[size]} over the rings of
 *       {@link RingFinder#findSSSR}. If het-het enabled: for each non-C atom and each non-C neighbour
 *       {@code += hetHetPenalty}, result {@code / 2}. Description {@code "<p>FeatureJudge->" +
 *       ["RingScore: x. "] + ["HetHetScore: y. "]}; {@code JudgeResult(0, scoreSum, 0, message)}.</li>
 *   <li><b>Switch point</b> ({@code attic.compat}): STRICT lets ring-finder exceptions propagate (the run
 *       ends, exception_in_run, as in 2001). DEFAULT catches every {@link RuntimeException} of the legacy
 *       finder (failure modes 1-2: {@code ArrayIndexOutOfBoundsException}; mode 3:
 *       {@link RingFinder.HangDetected}) and scores the ring sizes of {@code CdkBridge.sssrRingSizes} for
 *       that structure instead (DEVIATIONS.md DV-01..03).</li>
 *   <li>{@link #getRingSizeScore} keeps the legacy {@code Hashtable.contains(value)} bug (unused by scoring).</li>
 * </ul>
 */
public class FeatureJudge extends Judge {

    /** ring size -&gt; score (legacy {@code Hashtable}; iteration order is irrelevant for scoring). */
    public Map<Integer, Integer> ringSizeScores = new Hashtable<>();
    protected int hetHetPenalty = -5;
    protected boolean hetHetPenaltyEnabled = false;
    protected boolean ringsizeRestrictionEnabled = false;
    protected RingFinder sorf = new RingFinder();

    public FeatureJudge() {
        super("FeatureJudge");
        setRingSetRequired(true);
        setHasMaxScore(true);
    }

    public void addRingSizeScore(int size, int score) {
        if (score != 0) ringSizeScores.put(size, score);
        fireChanged();
    }

    /** Legacy bug kept: tests {@code contains(size)} on the values. */
    public int getRingSizeScore(int size) {
        // legacy Hashtable.contains(Object) tests the VALUES; get() then may return null (NPE on unboxing)
        if (ringSizeScores.containsValue(size)) {
            return ringSizeScores.get(size);
        }
        return 0;
    }

    public int getHetHetPenalty() {
        return hetHetPenalty;
    }

    public void setHetHetPenalty(int hetHetPenalty) {
        fireChanged();
        this.hetHetPenalty = hetHetPenalty;
    }

    public boolean isHetHetPenaltyEnabled() {
        return hetHetPenaltyEnabled;
    }

    public void setHetHetPenaltyEnabled(boolean enabled) {
        this.hetHetPenaltyEnabled = enabled;
        fireChanged();
    }

    public boolean isRingsizeRestrictionEnabled() {
        return ringsizeRestrictionEnabled;
    }

    @Override
    public void init() {
        if (ringSizeScores.size() > 0) ringsizeRestrictionEnabled = true;
    }

    @Override
    public void calcMaxScore() {
        maxScore = 0;
        if (ringsizeRestrictionEnabled) {
            for (int v : ringSizeScores.values()) maxScore += v;
        }
        // the hetHetPenalty only lowers the score; its maximum contribution is 0
    }

    @Override
    public JudgeResult evaluate(int[][] bm) {
        scoreSum = 0;
        int ringScore;
        int hetScore;
        String message = "<p>FeatureJudge->";
        if (ringsizeRestrictionEnabled) {
            ringScore = judgeRingScore();
            ringScore = judgeRingScore();
            scoreSum += ringScore;
            message += "RingScore: " + ringScore + ". ";
        }
        if (isHetHetPenaltyEnabled()) {
            hetScore = judgeHetHetScore();
            scoreSum += hetScore;
            message += "HetHetScore: " + hetScore + ". ";
        }
        return new JudgeResult(0, scoreSum, 0, message);
    }

    /**
     * Sum of {@code ringSizeScores[size]} over the rings of the legacy ring finder. Switch point
     * (DEVIATIONS.md DV-01..03): if the finder fails (any {@link RuntimeException}: failure modes 1-3),
     * STRICT rethrows (the run ends as in 2001); DEFAULT scores the ring sizes of CDK {@code Cycles.sssr}
     * of the same structure instead. Structures on which the legacy finder succeeds are scored identically
     * in both modes.
     */
    private int judgeRingScore() {
        int ringscore = 0;
        int[] sizes;
        try {
            Vector<Ring> sssr = sorf.findSSSR(attic.atomSet);
            sizes = new int[sssr.size()];
            for (int f = 0; f < sizes.length; f++) sizes[f] = sssr.elementAt(f).size();
        } catch (RuntimeException e) {
            if (attic.compat == null || attic.compat.strict()) throw e;
            sizes = CdkBridge.sssrRingSizes(attic.atomSet, AtomSets.createConnectionTable(attic.atomSet));
        }
        for (int ringsize : sizes) {
            Integer s = ringSizeScores.get(ringsize);
            if (s != null) ringscore += s;
        }
        return ringscore;
    }

    private int judgeHetHetScore() {
        int score = 0;
        for (int f = 0; f < attic.atomSet.length; f++) {
            var atom = attic.atomSet[f];
            if (!atom.symbol.equals("C")) {
                for (int g = 0; g < atom.degree; g++) {
                    if (!atom.nodeTable[g].symbol.equals("C")) score += hetHetPenalty;
                }
            }
        }
        return score / 2;
    }

    @Override
    public Judge copy() {
        FeatureJudge c = new FeatureJudge();
        copyBaseFieldsTo(c);
        c.ringSizeScores = new Hashtable<>(ringSizeScores);
        c.hetHetPenalty = hetHetPenalty;
        c.hetHetPenaltyEnabled = hetHetPenaltyEnabled;
        c.ringsizeRestrictionEnabled = ringsizeRestrictionEnabled;
        return c;
    }
}
