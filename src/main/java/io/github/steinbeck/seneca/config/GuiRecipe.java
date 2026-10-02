package io.github.steinbeck.seneca.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.io.SpecMLReader;
import io.github.steinbeck.seneca.judges.FeatureJudge;
import io.github.steinbeck.seneca.judges.HHCOSYJudge;
import io.github.steinbeck.seneca.judges.HMBCJudge;
import io.github.steinbeck.seneca.judges.HOSECodeJudge;
import io.github.steinbeck.seneca.judges.HeteroBondsJudge;
import io.github.steinbeck.seneca.judges.HybridizationJudge;
import io.github.steinbeck.seneca.judges.Judge;
import io.github.steinbeck.seneca.judges.SymmetryJudge;
import io.github.steinbeck.seneca.judges.TwoDSpectrumJudge;
import io.github.steinbeck.seneca.model.AnnealingSchedule;
import io.github.steinbeck.seneca.model.SenecaDataset;

/**
 * The golden "GUI recipe": what a 2001 user does with a dataset before "Start as selected", headless.
 * Port of {@code legacy/harness/src/seneca/client/GuiRecipe.java} (harness code, the specification of
 * golden/FORMAT.md "The GUI recipe"), built on {@link io.github.steinbeck.seneca.io.SpecMLReader},
 * {@link JudgeConfiguration} and {@link CarbonShiftAssigner}.
 *
 * <p>Steps: open; dataset window (configurators constructed); AutoAssign iff no atom has a 13C shift;
 * H-count overrides ({@link #H_OVERRIDES}) or, for a key ending in {@link #VARIANT_HBLOCK}, all H counts
 * from the file's {@code <molecule>} block; Autoconfigure per {@link Profile}; untick a 2D judge left
 * enabled with {@code assignment == null}; MANUAL table edits ({@link #MANUAL_RULE}); add a default
 * schedule iff none. The {@code steps}/{@code hOverrides} strings must equal the harness's (golden echo
 * {@code recipe} records).
 */
public final class GuiRecipe {

    /** Dataset-key suffix: take every atom's H count from the file's own {@code <molecule>} block. */
    public static final String VARIANT_HBLOCK = "@hblock";

    public static final String MANUAL_RULE = "HybridizationJudge: C with shift>=100 -> sp2 '1.0', other C with shift -> sp3 '1.0'; "
            + "HeteroBondsJudge: C with 50<=shift<100 -> hetero-attached";

    /** Manual H-count edits a 2001 user had to make: {datasetKey, atomIndex, HCount, reason} (verbatim from the harness). */
    public static final List<HOverride> H_OVERRIDES = List.of(
        new HOverride("eurabidiol", 15, 1, "AutoAssign leaves O H=0 (C-H sum 26 != formula 28); OH counts taken from the file's own <molecule> block (saved by the 2001 GUI, ignored by SpecMLReader)"),
        new HOverride("eurabidiol", 16, 1, "see atom 15"),
        new HOverride("polycarpol", 30, 1, "AutoAssign leaves O H=0 (C-H sum 46 != formula 48); OH counts taken from the file's own <molecule> block (saved by the 2001 GUI, ignored by SpecMLReader). "
                + "The same block also differs from AutoAssign at 3 carbons (atom 13: CH2 vs C; atoms 16, 18: CH vs CH2); those block values are NOT used here, because they "
                + "contradict the literature structure (lanosta-7,9(11),24-triene-3b,15a-diol needs 4 quaternary sp3 C, as AutoAssign gives). Variant polycarpol@hblock uses the whole block"),
        new HOverride("polycarpol", 31, 1, "see atom 30"),
        new HOverride("eupatorio", 13, 1, "AutoAssign gives C-H sum 13, formula C13H14O3 needs 14; the file holds no H counts. Harness decision: the missing H goes to the first O (atom 13). Without it the heavy-atom valence sum is odd and no saturated structure exists"));

    /** One manual H-count edit. */
    public record HOverride(String key, int atom, int hCount, String reason) {
    }

    /** Result of {@link #prepare}. */
    public static final class Prepared {
        public SenecaDataset sd;
        public Profile profile;
        /** shifts present right after loading */
        public boolean moleculeRead;
        public boolean autoAssigned;
        public boolean scheduleAdded;
        public final List<String> steps = new ArrayList<>();
        public final List<String> hOverrides = new ArrayList<>();
    }

    private GuiRecipe() {
    }

    /** File base name of a dataset key ("polycarpol@hblock" -&gt; "polycarpol"). */
    public static String baseKey(String key) {
        int i = key.indexOf('@');
        return i < 0 ? key : key.substring(0, i);
    }

    /** Runs the recipe on {@code smlFile} for dataset key {@code key} and {@code profile} (STRICT, golden). */
    public static Prepared prepare(Path smlFile, String key, Profile profile) throws IOException {
        return prepare(smlFile, key, profile, LegacyCompat.STRICT);
    }

    /**
     * Same, with the compatibility mode of the Autoassignment ({@link CarbonShiftAssigner}, DV-42) and of
     * "Autoconfigure Judge" ({@link JudgeConfiguration#autoconfigure}, DV-46). The golden recorders use
     * STRICT; {@code seneca generate} passes its {@code --compat}. On the bundled datasets both modes
     * give the same dataset.
     */
    public static Prepared prepare(Path smlFile, String key, Profile profile, LegacyCompat compat) throws IOException {
        Prepared p = new Prepared();
        p.profile = profile;
        // 1. File -> Open
        SenecaDataset sd = SpecMLReader.read(smlFile);
        if (sd.setOfNodes == null) throw new IllegalStateException("no atoms (no <formula> and no <cml:molecule>) in " + smlFile);
        p.sd = sd;
        p.steps.add("open: SpecMLReader.getSenecaDataset()");

        // 2. dataset window: the configurators bind their judges (sd.getJudge) and run their constructors
        HHCOSYJudge cosy = sd.getJudge(HHCOSYJudge.class);
        HMBCJudge hmbc = sd.getJudge(HMBCJudge.class);
        HOSECodeJudge hose = sd.getJudge(HOSECodeJudge.class);
        SymmetryJudge sym = sd.getJudge(SymmetryJudge.class);
        FeatureJudge feat = sd.getJudge(FeatureJudge.class);
        HybridizationJudge hy = sd.getJudge(HybridizationJudge.class);
        HeteroBondsJudge hb = sd.getJudge(HeteroBondsJudge.class);
        JudgeConfiguration.datasetWindowOpened(sd);
        p.steps.add("window: 7 judge configurators constructed");

        // 3. AutoAssign iff no atom has a 13C shift after loading
        boolean anyShift = false;
        for (HeavyAtom a : sd.setOfNodes) if (a.shift13C != null) anyShift = true;
        p.moleculeRead = anyShift;
        if (!anyShift) {
            new CarbonShiftAssigner(sd, compat).assign();
            p.autoAssigned = true;
            p.steps.add("atom properties: AutoAssign (CarbonShiftAssigner)");
        }

        // 4. manual H edits (AtomPropertyPanel.setValueAt column 2: accepts 0..4)
        if (key.endsWith(VARIANT_HBLOCK)) {
            applyHBlock(smlFile, sd, p);
        } else {
            for (HOverride o : H_OVERRIDES) {
                if (!o.key().equals(key)) continue;
                int atom = o.atom();
                int h = o.hCount();
                if (!sd.setOfNodes[atom].symbol.equals("O"))
                    throw new IllegalStateException("H override for non-O atom " + atom + " in " + key);
                sd.setOfNodes[atom].hCount = h;
                p.hOverrides.add("atom " + atom + " (" + sd.setOfNodes[atom].symbol + ") HCount=" + h + ": " + o.reason());
                p.steps.add("atom properties: set HCount of atom " + atom + " to " + h);
            }
        }

        // 5. Judges -> "Autoconfigure Judge"
        List<Judge> press = new ArrayList<>();
        press.add(cosy);
        press.add(hmbc);
        press.add(hose);
        if (profile == Profile.ALL || profile == Profile.MANUAL) press.add(sym);
        if (profile == Profile.GUI || profile == Profile.ALL) press.add(feat);
        if (profile == Profile.ALL) {
            press.add(hy);
            press.add(hb);
        }
        for (Judge j : press) {
            String name = j.name;
            try {
                JudgeConfiguration.autoconfigure(sd, j, compat);
                p.steps.add("judges: Autoconfigure " + name + " -> enabled=" + j.getEnabled());
            } catch (RuntimeException e) {
                // in the GUI the exception ends up on the event thread; the judge stays enabled
                p.steps.add("judges: Autoconfigure " + name + " threw " + e.getClass().getName() + " -> enabled=" + j.getEnabled());
            }
            if (j instanceof TwoDSpectrumJudge t && j.getEnabled() && t.assignment == null) {
                j.setEnabled(false);
                p.steps.add("judges: untick " + name + " (assignment == null; Start would NPE in calcMaxScore)");
            }
        }

        // 5b. "manual": tick "Activate this Judge?" and edit the tables (MANUAL_RULE)
        if (profile == Profile.MANUAL) {
            JudgeConfiguration.setActivated(hy, true);
            p.steps.add("judges: tick 'Activate this Judge?' on HybridizationJudge");
            List<String> hyCells = new ArrayList<>(), hbCells = new ArrayList<>();
            for (int f = 0; f < sd.setOfNodes.length; f++) {
                Float o = sd.setOfNodes[f].shift13C;
                if (o == null || !sd.setOfNodes[f].symbol.equals("C")) continue;
                float sh = o;
                int col = sh >= 100f ? JudgeConfiguration.HYB_COLUMN_SP2 : JudgeConfiguration.HYB_COLUMN_SP3;
                JudgeConfiguration.setHybridizationCell(sd, f, col, "1.0", compat);
                hyCells.add(f + (col == JudgeConfiguration.HYB_COLUMN_SP2 ? ":sp2" : ":sp3"));
            }
            p.steps.add("judges: HybridizationJudge table, value '1.0' in " + hyCells);
            JudgeConfiguration.setActivated(hb, true);
            p.steps.add("judges: tick 'Activate this Judge?' on HeteroBondsJudge");
            for (int f = 0; f < sd.setOfNodes.length; f++) {
                Float o = sd.setOfNodes[f].shift13C;
                if (o == null || !sd.setOfNodes[f].symbol.equals("C")) continue;
                float sh = o;
                if (sh >= 50f && sh < 100f) {
                    JudgeConfiguration.setHeteroBondsCell(sd, f, JudgeConfiguration.HETERO_COLUMN, true, compat);
                    hbCells.add(String.valueOf(f));
                }
            }
            p.steps.add("judges: HeteroBondsJudge table, hetero-attached ticked for atoms " + hbCells);
        }

        // 6. annealing schedule
        if (sd.annealingSchedules.isEmpty()) {
            sd.annealingSchedules.add(new AnnealingSchedule());
            p.scheduleAdded = true;
            p.steps.add("annealing schedule: New -> (defaults)");
        }
        return p;
    }

    /**
     * Variant "@hblock": every atom's H count from the file's {@code <molecule>} block (the block the
     * 2001 GUI saved; the reader ignores it). The block's atoms must be in dataset order with the same
     * elements and the same assigned shifts as the configured dataset; anything else throws
     * {@link IllegalStateException} (verbatim from the harness).
     */
    static void applyHBlock(Path file, SenecaDataset sd, Prepared p) throws IOException {
        String s = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
        Matcher m = Pattern.compile("<molecule[^>]*>(.*?)</molecule>", Pattern.DOTALL).matcher(s);
        if (!m.find()) throw new IllegalStateException("no <molecule> block in " + file);
        Matcher a = Pattern.compile("<atom id=\"a(\\d+)\">(.*?)</atom>", Pattern.DOTALL).matcher(m.group(1));
        Pattern el = Pattern.compile("elementType\">(\\w+)<"), hc = Pattern.compile("hydrogenCount\">(\\d+)<"),
                sh = Pattern.compile("assignedCarbonShift\">([-0-9.]+)<");
        int n = 0;
        List<String> changed = new ArrayList<>();
        while (a.find()) {
            int idx = Integer.parseInt(a.group(1));
            if (idx != n || idx >= sd.setOfNodes.length) throw new IllegalStateException("block atom order " + idx + " at " + n);
            HeavyAtom at = sd.setOfNodes[idx];
            Matcher me = el.matcher(a.group(2)), mh = hc.matcher(a.group(2)), ms = sh.matcher(a.group(2));
            if (!me.find() || !mh.find()) throw new IllegalStateException("block atom " + idx + " incomplete");
            if (!me.group(1).equals(at.symbol)) throw new IllegalStateException("block element differs at " + idx);
            Float cur = at.shift13C;
            if (ms.find() != (cur != null) || (cur != null && Float.parseFloat(ms.group(1)) != cur.floatValue()))
                throw new IllegalStateException("block shift differs from the configured shift at atom " + idx);
            int h = Integer.parseInt(mh.group(1));
            if (h != at.hCount) changed.add(idx + ":" + at.hCount + "->" + h);
            at.hCount = h;
            n++;
        }
        if (n != sd.setOfNodes.length) throw new IllegalStateException("block has " + n + " atoms, dataset " + sd.setOfNodes.length);
        p.hOverrides.add("all " + n + " H counts from the file's <molecule> block; changed " + changed);
        p.steps.add("atom properties: set every HCount as in the file's <molecule> block (changed " + changed + ")");
    }

    /** Names of the enabled judges, in {@code sd.judges} order. */
    public static List<String> enabledJudges(SenecaDataset sd) {
        List<String> l = new ArrayList<>();
        for (var j : sd.judges) if (j.getEnabled()) l.add(j.name);
        return l;
    }
}
