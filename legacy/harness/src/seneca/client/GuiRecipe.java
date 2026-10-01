package seneca.client;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import compchem.Atom;
import seneca.core.SenecaDataset;
import seneca.core.SpecMLReader;
import seneca.judges.Judge;
import seneca.judges.TwoDSpectrumJudge;
import seneca.structgen.AnnealingSchedule;

/**
 * Reproduces, headless and in-process, what a SENECA 2001 GUI user does with a dataset
 * before pressing "Start as selected". Lives in package seneca.client because the judge
 * configurators (whose constructors and autoconfigure() methods are reused verbatim) have
 * package-private constructors. Requires -Djava.awt.headless=true (the configurators are
 * lightweight Swing panels; no window is created).
 *
 * The recipe (documented in golden/FORMAT.md, section "GUI recipe"):
 *  1. File -> Open: SpecMLReader(file).getSenecaDataset()      (calls sd.init(): 7 disabled judges)
 *  2. The dataset window opens: the 7 judge configurators are constructed in the order of
 *     SenecaDatasetFrame.getJudgeTabbedPane() (Hybridization/HeteroBonds constructors call init()).
 *  3. Atom Properties -> "AutoAssign" (CarbonShiftAssigner) iff no atom has a 13C shift after loading
 *     (i.e. the file had no <cml:molecule> block with assigned shifts).
 *  4. Atom Properties -> manual H-count edits, only for the datasets listed in H_OVERRIDES.
 *     For the dataset variant "<key>@hblock" ALL H counts are instead taken from the file's own
 *     <molecule> block (written by the 2001 GUI, ignored by SpecMLReader); see golden/FORMAT.md.
 *  5. Judges -> "Autoconfigure Judge" (JudgeConfigurator.AutoAssignAction: setEnabled(true); autoconfigure())
 *     on HHCOSYJudge, HMBCJudge, HOSECodeJudge, FeatureJudge ("gui" profile), on all 7 judges
 *     ("all" profile), on HHCOSYJudge, HMBCJudge, HOSECodeJudge only ("paper" profile: no
 *     FeatureJudge, whose ring finder crashes long runs), or ("manual" profile) on HHCOSYJudge,
 *     HMBCJudge, HOSECodeJudge, SymmetryJudge plus hand edits of the HybridizationJudge and
 *     HeteroBondsJudge tables through the configurators' own table models (MANUAL_RULE).
 *     A 2D judge left enabled with assignment == null is disabled (in the GUI, "Start" would
 *     throw an NPE in calcMaxScore, so the user must untick it).
 *  6. Annealing Schedule -> "New ->" (new AnnealingSchedule()) iff the file had no schedule.
 */
public final class GuiRecipe {

    public static final String PROFILE_GUI = "gui";
    public static final String PROFILE_ALL = "all";
    public static final String PROFILE_PAPER = "paper";
    public static final String PROFILE_MANUAL = "manual";

    /** Dataset-key suffix: take every atom's H count from the file's own <molecule> block. */
    public static final String VARIANT_HBLOCK = "@hblock";

    /**
     * The hand edits of the "manual" profile (a harness choice that exercises the two table-driven
     * judges, not a chemical assignment):
     *  HybridizationJudge table: for every C with a 13C shift, cell "sp2" := "1.0" if shift >= 100,
     *    else cell "sp3" := "1.0" (combo-box value; the judge stores 1.0 * multiplicator = 100).
     *  HeteroBondsJudge table: "hetero-attached?" := true for every C with 50 <= shift < 100.
     */
    public static final String MANUAL_RULE = "HybridizationJudge: C with shift>=100 -> sp2 '1.0', other C with shift -> sp3 '1.0'; "
            + "HeteroBondsJudge: C with 50<=shift<100 -> hetero-attached";

    /** File base name of a dataset key ("polycarpol@hblock" -> "polycarpol"). */
    public static String baseKey(String key) {
        int i = key.indexOf('@');
        return i < 0 ? key : key.substring(0, i);
    }

    /** Manual H-count edits a 2001 user had to make in the Atom Properties table. {datasetKey, atomIndex, HCount, reason} */
    public static final Object[][] H_OVERRIDES = {
        {"eurabidiol", 15, 1, "AutoAssign leaves O H=0 (C-H sum 26 != formula 28); OH counts taken from the file's own <molecule> block (saved by the 2001 GUI, ignored by SpecMLReader)"},
        {"eurabidiol", 16, 1, "see atom 15"},
        {"polycarpol", 30, 1, "AutoAssign leaves O H=0 (C-H sum 46 != formula 48); OH counts taken from the file's own <molecule> block (saved by the 2001 GUI, ignored by SpecMLReader). "
                + "The same block also differs from AutoAssign at 3 carbons (atom 13: CH2 vs C; atoms 16, 18: CH vs CH2); those block values are NOT used here, because they "
                + "contradict the literature structure (lanosta-7,9(11),24-triene-3b,15a-diol needs 4 quaternary sp3 C, as AutoAssign gives). Variant polycarpol@hblock uses the whole block"},
        {"polycarpol", 31, 1, "see atom 30"},
        {"eupatorio", 13, 1, "AutoAssign gives C-H sum 13, formula C13H14O3 needs 14; the file holds no H counts. Harness decision: the missing H goes to the first O (atom 13). Without it the heavy-atom valence sum is odd and no saturated structure exists"},
    };

    public static final class Prepared {
        public SenecaDataset sd;
        public String profile;
        public boolean moleculeRead;      // shifts present right after loading
        public boolean autoAssigned;
        public boolean scheduleAdded;
        public List<String> steps = new ArrayList<String>();
        public List<String> hOverrides = new ArrayList<String>();
    }

    private GuiRecipe() {}

    public static Prepared prepare(File file, String key, String profile) {
        Prepared p = new Prepared();
        p.profile = profile;
        SenecaDataset sd = new SpecMLReader(file).getSenecaDataset();
        if (sd == null) throw new IllegalStateException("SpecMLReader returned null for " + file);
        if (sd.setOfNodes == null) throw new IllegalStateException("no atoms (no <formula> and no <cml:molecule>) in " + file);
        p.sd = sd;
        p.steps.add("open: SpecMLReader.getSenecaDataset()");

        // 2. dataset window: judge configurators, constructed in SenecaDatasetFrame order
        HHCOSYJudgeConfigurator cosyC = new HHCOSYJudgeConfigurator(sd);
        HMBCJudgeConfigurator hmbcC = new HMBCJudgeConfigurator(sd);
        HOSECodeJudgeConfigurator hoseC = new HOSECodeJudgeConfigurator(sd);
        SymmetryJudgeConfigurator symC = new SymmetryJudgeConfigurator(sd);
        FeatureJudgeConfigurator featC = new FeatureJudgeConfigurator(sd);
        HybridizationJudgeConfigurator hyC = new HybridizationJudgeConfigurator(sd);
        HeteroBondsJudgeConfigurator hbC = new HeteroBondsJudgeConfigurator(sd);
        p.steps.add("window: 7 judge configurators constructed");

        // 3. AutoAssign
        boolean anyShift = false;
        for (int f = 0; f < sd.setOfNodes.length; f++)
            if (sd.setOfNodes[f].getPhysicalProperty(Atom.PHYS_PROP_13C_SHIFT) != null) anyShift = true;
        p.moleculeRead = anyShift;
        if (!anyShift) {
            new CarbonShiftAssigner(sd).assign();
            p.autoAssigned = true;
            p.steps.add("atom properties: AutoAssign (CarbonShiftAssigner)");
        }

        // 4. manual H edits (AtomPropertyPanel.setValueAt column 2: accepts 0..4)
        if (key.endsWith(VARIANT_HBLOCK)) applyHBlock(file, sd, p);
        else for (int i = 0; i < H_OVERRIDES.length; i++) {
            if (!H_OVERRIDES[i][0].equals(key)) continue;
            int atom = ((Integer) H_OVERRIDES[i][1]).intValue();
            int h = ((Integer) H_OVERRIDES[i][2]).intValue();
            if (!sd.setOfNodes[atom].symbol.equals("O"))
                throw new IllegalStateException("H override for non-O atom " + atom + " in " + key);
            sd.setOfNodes[atom].HCount = h;
            p.hOverrides.add("atom " + atom + " (" + sd.setOfNodes[atom].symbol + ") HCount=" + h + ": " + H_OVERRIDES[i][3]);
            p.steps.add("atom properties: set HCount of atom " + atom + " to " + h);
        }

        // 5. judges -> Autoconfigure Judge
        List<JudgeConfigurator> press = new ArrayList<JudgeConfigurator>();
        press.add(cosyC);
        press.add(hmbcC);
        press.add(hoseC);
        if (PROFILE_ALL.equals(profile) || PROFILE_MANUAL.equals(profile)) press.add(symC);
        if (PROFILE_GUI.equals(profile) || PROFILE_ALL.equals(profile)) press.add(featC);
        if (PROFILE_ALL.equals(profile)) { press.add(hyC); press.add(hbC); }
        for (JudgeConfigurator c : press) {
            String name = c.judge.name;
            try {
                c.new AutoAssignAction().actionPerformed(null);
                p.steps.add("judges: Autoconfigure " + name + " -> enabled=" + c.judge.getEnabled());
            } catch (RuntimeException e) {
                // In the GUI the exception ends up on the event thread; the judge stays enabled.
                p.steps.add("judges: Autoconfigure " + name + " threw " + e.getClass().getName()
                        + " -> enabled=" + c.judge.getEnabled());
            }
            if (c.judge instanceof TwoDSpectrumJudge && c.judge.getEnabled()
                    && ((TwoDSpectrumJudge) c.judge).assignment == null) {
                c.judge.setEnabled(false);
                p.steps.add("judges: untick " + name + " (assignment == null; Start would NPE in calcMaxScore)");
            }
        }

        // 5b. "manual": tick "Activate this Judge?" and edit the tables (MANUAL_RULE)
        if (PROFILE_MANUAL.equals(profile)) {
            hyC.judge.setEnabled(true);
            p.steps.add("judges: tick 'Activate this Judge?' on HybridizationJudge");
            HybridizationJudgeConfigurator.HybridizationTableModel hm = hyC.new HybridizationTableModel();
            HeteroBondsJudgeConfigurator.HeteroBondsTableModel bm = hbC.new HeteroBondsTableModel();
            List<String> hy = new ArrayList<String>(), hb = new ArrayList<String>();
            for (int f = 0; f < sd.setOfNodes.length; f++) {
                Object o = sd.setOfNodes[f].getPhysicalProperty(Atom.PHYS_PROP_13C_SHIFT);
                if (o == null || !sd.setOfNodes[f].symbol.equals("C")) continue;
                float sh = ((Float) o).floatValue();
                int col = sh >= 100f ? 4 : 3;
                hm.setValueAt("1.0", f, col);
                hy.add(f + (col == 4 ? ":sp2" : ":sp3"));
            }
            p.steps.add("judges: HybridizationJudge table, value '1.0' in " + hy);
            hbC.judge.setEnabled(true);
            p.steps.add("judges: tick 'Activate this Judge?' on HeteroBondsJudge");
            for (int f = 0; f < sd.setOfNodes.length; f++) {
                Object o = sd.setOfNodes[f].getPhysicalProperty(Atom.PHYS_PROP_13C_SHIFT);
                if (o == null || !sd.setOfNodes[f].symbol.equals("C")) continue;
                float sh = ((Float) o).floatValue();
                if (sh >= 50f && sh < 100f) {
                    bm.setValueAt(Boolean.TRUE, f, 3);
                    hb.add(String.valueOf(f));
                }
            }
            p.steps.add("judges: HeteroBondsJudge table, hetero-attached ticked for atoms " + hb);
        }

        // 6. annealing schedule
        if (sd.annealingSchedules.size() == 0) {
            sd.annealingSchedules.addElement(new AnnealingSchedule());
            p.scheduleAdded = true;
            p.steps.add("annealing schedule: New -> (defaults)");
        }
        return p;
    }

    /**
     * Variant "@hblock": every atom's HCount from the file's <molecule> block (the block the 2001
     * GUI saved; SpecMLReader ignores it). The block's atoms must be in dataset order with the same
     * elements and the same assigned shifts as the configured dataset; anything else throws.
     */
    static void applyHBlock(File file, SenecaDataset sd, Prepared p) {
        String s;
        try { s = new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1); }
        catch (java.io.IOException e) { throw new IllegalStateException(e); }
        Matcher m = Pattern.compile("<molecule[^>]*>(.*?)</molecule>", Pattern.DOTALL).matcher(s);
        if (!m.find()) throw new IllegalStateException("no <molecule> block in " + file);
        Matcher a = Pattern.compile("<atom id=\"a(\\d+)\">(.*?)</atom>", Pattern.DOTALL).matcher(m.group(1));
        Pattern el = Pattern.compile("elementType\">(\\w+)<"), hc = Pattern.compile("hydrogenCount\">(\\d+)<"),
                sh = Pattern.compile("assignedCarbonShift\">([-0-9.]+)<");
        int n = 0;
        List<String> changed = new ArrayList<String>();
        while (a.find()) {
            int idx = Integer.parseInt(a.group(1));
            if (idx != n || idx >= sd.setOfNodes.length) throw new IllegalStateException("block atom order " + idx + " at " + n);
            Atom at = sd.setOfNodes[idx];
            Matcher me = el.matcher(a.group(2)), mh = hc.matcher(a.group(2)), ms = sh.matcher(a.group(2));
            if (!me.find() || !mh.find()) throw new IllegalStateException("block atom " + idx + " incomplete");
            if (!me.group(1).equals(at.symbol)) throw new IllegalStateException("block element differs at " + idx);
            Object cur = at.getPhysicalProperty(Atom.PHYS_PROP_13C_SHIFT);
            if (ms.find() != (cur != null) || (cur != null && Float.parseFloat(ms.group(1)) != ((Float) cur).floatValue()))
                throw new IllegalStateException("block shift differs from the configured shift at atom " + idx);
            int h = Integer.parseInt(mh.group(1));
            if (h != at.HCount) changed.add(idx + ":" + at.HCount + "->" + h);
            at.HCount = h;
            n++;
        }
        if (n != sd.setOfNodes.length) throw new IllegalStateException("block has " + n + " atoms, dataset " + sd.setOfNodes.length);
        p.hOverrides.add("all " + n + " H counts from the file's <molecule> block; changed " + changed);
        p.steps.add("atom properties: set every HCount as in the file's <molecule> block (changed " + changed + ")");
    }

    /** Names of the enabled judges, in sd.judges order. */
    public static List<String> enabledJudges(SenecaDataset sd) {
        List<String> l = new ArrayList<String>();
        for (int g = 0; g < sd.judges.size(); g++) {
            Judge j = (Judge) sd.judges.elementAt(g);
            if (j.getEnabled()) l.add(j.name);
        }
        return l;
    }
}
