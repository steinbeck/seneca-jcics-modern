package io.github.steinbeck.seneca.config;

/**
 * Judge profiles of the golden GUI recipe (golden/FORMAT.md, "The GUI recipe", step 5).
 *
 * <ul>
 *   <li>{@link #GUI}: Autoconfigure HHCOSY, HMBC, HOSECode, Feature.</li>
 *   <li>{@link #PAPER}: HHCOSY, HMBC, HOSECode (no FeatureJudge).</li>
 *   <li>{@link #ALL}: all 7 (Symmetry included; Hybridization/HeteroBonds get the no-op autoconfigure).</li>
 *   <li>{@link #MANUAL}: HHCOSY, HMBC, HOSECode, Symmetry + hand edits of the Hybridization and
 *       HeteroBonds tables ({@code GuiRecipe.MANUAL_RULE}).</li>
 * </ul>
 * Autoconfigure is pressed in this order: HHCOSY, HMBC, HOSECode, [Symmetry], [Feature],
 * [Hybridization, HeteroBonds] (legacy/harness GuiRecipe.prepare).
 */
public enum Profile {
    GUI("gui"), ALL("all"), PAPER("paper"), MANUAL("manual");

    /** Name used in golden file names and records. */
    public final String key;

    Profile(String key) {
        this.key = key;
    }

    public static Profile byKey(String key) {
        for (Profile p : values()) if (p.key.equals(key)) return p;
        throw new IllegalArgumentException("unknown profile " + key);
    }
}
