package io.github.steinbeck.seneca.judges;

/**
 * Base class of all judges (scoring functions). Port of
 * {@code legacy/original/src/seneca/judges/Judge.java}; semantics in
 * {@code docs/legacy/architecture.md} section 5.
 *
 * <p>Field names, defaults and the field-hiding quirks of the subclasses are kept on purpose
 * ({@code HOSECodeJudge.score}, {@code HeteroBondsJudge.score}/{@code maxScore} hide the base fields;
 * the golden echo records both, as {@code judgeScoreField} and {@code privateMaxScore}).
 *
 * <p>Life cycle (identical to legacy):
 * <ol>
 *   <li>configured on the dataset's instance (config package: autoconfigure, table edits);</li>
 *   <li>"Start": {@link #calcMaxScore()} on every <em>enabled</em> judge of the dataset, then a deep
 *       {@link #copy()} of <em>all</em> judges is handed to the generator (legacy: serialization);</li>
 *   <li>generator: {@link #setAttic} + {@link #init()} on every enabled judge, once, after the start
 *       structure exists ({@code initJudges()});</li>
 *   <li>{@link #evaluate(int[][])} per connected candidate, reading the candidate from {@code attic.atomSet}.</li>
 * </ol>
 */
public abstract class Judge {

    /** Judge name as in legacy ("HMBCJudge", ...); appears in descriptions and golden records. */
    public String name = "nop";
    private boolean enabled = false;
    protected boolean hasMaxScore = false;
    protected boolean ringSetRequired = false;
    protected Attic attic;
    /** Legacy package-private {@code int score, maxScore, scoreSum; int multiplicator}. */
    protected int score, maxScore, scoreSum;
    protected int multiplicator;
    /** Legacy transient listener; never copied. */
    protected transient JudgeListener judgeListener;

    protected Judge(String name) {
        this.name = name;
        this.attic = new Attic();
    }

    /** Scores the candidate in {@code attic.atomSet}; {@code connectionTable} is the same structure as matrix. */
    public abstract JudgeResult evaluate(int[][] connectionTable);

    /** Generator-side initialisation (called once per run on enabled judges, after {@link #setAttic}). */
    public abstract void init();

    /** Client-side max score computation, called on enabled judges right before the deep copy. */
    public abstract void calcMaxScore();

    /**
     * Deep copy with the semantics of the legacy serialization round trip ({@code serClone}): every
     * configuration field copied (arrays/collections deep), {@link #judgeListener} not copied, a fresh
     * {@link Attic} (the generator replaces it in {@code initJudges}). Must not share mutable state with
     * the original.
     */
    public abstract Judge copy();

    public int getMultiplicator() {
        return multiplicator;
    }

    public void setMultiplicator(int multiplicator) {
        this.multiplicator = multiplicator;
    }

    public boolean getEnabled() {
        return enabled;
    }

    /** Sets the flag and notifies the listener (legacy {@code fireChanged()}). */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        fireChanged();
    }

    public void setJudgeListener(JudgeListener listener) {
        this.judgeListener = listener;
    }

    public void fireChanged() {
        if (judgeListener != null) judgeListener.judgeDataChanged();
    }

    public Attic getAttic() {
        return attic;
    }

    public void setAttic(Attic attic) {
        this.attic = attic;
    }

    public boolean isRingSetRequired() {
        return ringSetRequired;
    }

    public void setRingSetRequired(boolean ringSetRequired) {
        this.ringSetRequired = ringSetRequired;
    }

    public boolean getHasMaxScore() {
        return hasMaxScore;
    }

    public void setHasMaxScore(boolean hasMaxScore) {
        this.hasMaxScore = hasMaxScore;
    }

    /** The <b>base</b> field {@code Judge.maxScore} (HeteroBondsJudge: always 0, quirk Q4). */
    public int getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(int maxScore) {
        this.maxScore = maxScore;
    }

    /** The base field {@code Judge.score} (golden echo {@code judgeScoreField}). */
    public final int getJudgeScoreField() {
        return score;
    }

    /** Copies the base-class fields into {@code target} (helper for {@link #copy()} implementations). */
    protected final void copyBaseFieldsTo(Judge target) {
        target.name = name;
        target.enabled = enabled;
        target.hasMaxScore = hasMaxScore;
        target.ringSetRequired = ringSetRequired;
        target.score = score;
        target.maxScore = maxScore;
        target.scoreSum = scoreSum;
        target.multiplicator = multiplicator;
        target.attic = new Attic();
    }
}
