package io.github.steinbeck.seneca.verify;

import static io.github.steinbeck.seneca.app.GuiTestSupport.click;
import static io.github.steinbeck.seneca.app.GuiTestSupport.flushEdt;
import static io.github.steinbeck.seneca.app.GuiTestSupport.menuItem;
import static io.github.steinbeck.seneca.app.GuiTestSupport.onEdt;
import static io.github.steinbeck.seneca.app.GuiTestSupport.shot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.image.BufferedImage;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;

import javax.vecmath.Point2d;

import org.junit.jupiter.api.Test;
import org.openscience.cdk.graph.ConnectivityChecker;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IBond;
import org.openscience.cdk.io.MDLV2000Reader;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmiFlavor;
import org.openscience.cdk.smiles.SmilesGenerator;

import io.github.steinbeck.seneca.app.ComponentNames;
import io.github.steinbeck.seneca.app.GoldenRunAssert;
import io.github.steinbeck.seneca.app.GuiTestSupport;
import io.github.steinbeck.seneca.app.GuiTestSupport.TestApp;
import io.github.steinbeck.seneca.app.ScriptedUi;
import io.github.steinbeck.seneca.app.TestHooks;
import io.github.steinbeck.seneca.app.dataset.DatasetFrame;
import io.github.steinbeck.seneca.app.edit.RecipeThroughPanelsTest;
import io.github.steinbeck.seneca.app.run.StructureGenerationPanel;
import io.github.steinbeck.seneca.app.structure.Depictions;
import io.github.steinbeck.seneca.app.structure.StructureDisplayPanel;
import io.github.steinbeck.seneca.config.GuiRecipe;
import io.github.steinbeck.seneca.config.Profile;
import io.github.steinbeck.seneca.golden.RunGolden;
import io.github.steinbeck.seneca.model.SenecaDataset;

/**
 * Visual review (verifier "visual"): renders the application at its real default size (main window
 * content {@link io.github.steinbeck.seneca.app.MainView#DEFAULT_SIZE}; 2001: 800x600) and the run UI during and after a run, the Structure Display with result structures,
 * and large depictions of golden best structures, to {@code <build>/gui-shots/visual-*.png}. The
 * depiction test also checks the drawn constitution against the connection table read independently by
 * CDK's MDL reader (canonical SMILES) and reports layout overlaps and bond crossings.
 */
class VisualReviewVerifyTest {

    static final int W = io.github.steinbeck.seneca.app.MainView.DEFAULT_SIZE.width, H = io.github.steinbeck.seneca.app.MainView.DEFAULT_SIZE.height;

    static SenecaDataset openViaToolbar(TestApp t, String key) {
        t.ui.answerFiles(GuiTestSupport.sml(key).toFile());
        click(GuiTestSupport.find(t.view, ComponentNames.TOOLBAR + ".openAll", JButton.class));
        flushEdt();
        return onEdt(t.app.datasets::current);
    }

    static StructureGenerationPanel runPanel(DatasetFrame f) {
        return onEdt(() -> {
            for (Component c : GuiTestSupport.all(f)) if (c instanceof StructureGenerationPanel p) return p;
            return null;
        });
    }

    @Test
    void everyTabAtDefaultWindowSize() throws Exception {
        try (TestApp t = GuiTestSupport.newApp("visual-tabs", new TestHooks(1L, RunGolden.ITERATION_CAP))) {
            shot(t.view, W, H, "visual-00-main-empty");
            SenecaDataset sd = openViaToolbar(t, "pinene");
            DatasetFrame f = onEdt(() -> t.app.frameOf(sd));
            JTabbedPane tabs = f.tabs();
            for (int i = 0; i < 7; i++) {
                int k = i;
                onEdt(() -> tabs.setSelectedIndex(k));
                shot(t.view, W, H, "visual-01-read-tab" + (i + 1));
            }
            SenecaDataset recipe = GuiRecipe.prepare(GuiTestSupport.sml("pinene"), "pinene", Profile.GUI, t.app.compat).sd;
            RecipeThroughPanelsTest.configure(f, "pinene", Profile.GUI, recipe);
            for (int i = 0; i < 5; i++) {
                int k = i;
                onEdt(() -> tabs.setSelectedIndex(k));
                shot(t.view, W, H, "visual-02-configured-tab" + (i + 1));
            }
            JTabbedPane spectra = GuiTestSupport.find(f, ComponentNames.SPECTRA_TABS, JTabbedPane.class);
            onEdt(() -> tabs.setSelectedIndex(1));
            for (int i = 0; i < onEdt(spectra::getTabCount); i++) {
                int k = i;
                onEdt(() -> spectra.setSelectedIndex(k));
                shot(t.view, W, H, "visual-03-spectra-" + (i + 1));
            }
            JTabbedPane judges = GuiTestSupport.find(f, ComponentNames.JUDGE_TABS, JTabbedPane.class);
            onEdt(() -> tabs.setSelectedIndex(4));
            for (int i = 0; i < onEdt(judges::getTabCount); i++) {
                int k = i;
                onEdt(() -> judges.setSelectedIndex(k));
                shot(t.view, W, H, "visual-04-judges-" + (i + 1));
            }

            // run tab: 3 slots, rows 1 and 3, finished
            onEdt(() -> f.showTab("Structure Generation"));
            StructureGenerationPanel p = runPanel(f);
            onEdt(() -> p.timer().stop());
            shot(t.view, W, H, "visual-05-run-empty");
            AbstractButton add = GuiTestSupport.find(f, ComponentNames.RUN_ADD_SLOT, AbstractButton.class);
            for (int i = 0; i < 3; i++) click(add);
            t.app.awaitControl(60_000);
            flushEdt();
            JTable table = p.table();
            onEdt(() -> {
                table.clearSelection();
                table.addRowSelectionInterval(0, 0);
                table.addRowSelectionInterval(2, 2);
            });
            shot(t.view, W, H, "visual-06-run-slots-selected");
            click(GuiTestSupport.find(f, ComponentNames.RUN_START, AbstractButton.class));
            t.app.awaitControl(60_000);
            assertTrue(GuiTestSupport.waitFor(() -> f.runs().slots().get(0).lastResult() != null
                    && f.runs().slots().get(2).lastResult() != null, 300_000));
            flushEdt();
            for (int i = 0; i < 3; i++) onEdt(p::poll);
            onEdt(p::poll);
            shot(t.view, W, H, "visual-07-run-finished-default");
            shot(t.view, 1280, 800, "visual-07-run-finished-1280x800");
            onEdt(() -> p.chart().toggleDisplay());
            shot(t.view, W, H, "visual-08-run-finished-kT");
            onEdt(() -> p.chart().toggleDisplay());
            onEdt(() -> f.showTab("Logbook"));
            shot(t.view, W, H, "visual-09-logbook");
            onEdt(() -> f.showTab("Structure Generation"));
            click(GuiTestSupport.find(f, ComponentNames.RUN_SUMMARIZE, AbstractButton.class));
            ScriptedUi.Window w = t.ui.lastWindow();
            shot(w.content, "visual-10-summarize-default");

            // dialogs
            click(menuItem(t.menuBar, "Help", "About Seneca"));
            shot(t.ui.lastShown().pane(), "visual-11-about");
            t.ui.answerDialog(JOptionPane.NO_OPTION);
            click(menuItem(t.menuBar, "File", "Exit"));
            shot(t.ui.lastShown().pane(), "visual-12-exit");
            // second dataset: two internal frames
            openViaToolbar(t, "eurabidiol");
            shot(t.view, W, H, "visual-13-two-datasets");
        }
    }

    /** A long run (polycarpol "paper", 3 slots): the tab during the run and after it. */
    @org.junit.jupiter.api.Tag("slow")
    @Test
    void runTabDuringAndAfterALongRun() throws Exception {
        try (TestApp t = GuiTestSupport.newApp("visual-run", new TestHooks(1L, RunGolden.ITERATION_CAP))) {
            SenecaDataset sd = openViaToolbar(t, "polycarpol");
            DatasetFrame f = onEdt(() -> t.app.frameOf(sd));
            SenecaDataset recipe = GuiRecipe.prepare(GuiTestSupport.sml("polycarpol"), "polycarpol", Profile.PAPER, t.app.compat).sd;
            RecipeThroughPanelsTest.configure(f, "polycarpol", Profile.PAPER, recipe);
            onEdt(() -> f.showTab("Structure Generation"));
            StructureGenerationPanel p = runPanel(f);    // the 2-second timer keeps running, as in the app
            AbstractButton add = GuiTestSupport.find(f, ComponentNames.RUN_ADD_SLOT, AbstractButton.class);
            for (int i = 0; i < 3; i++) click(add);
            t.app.awaitControl(60_000);
            flushEdt();
            onEdt(() -> p.table().setRowSelectionInterval(0, 2));
            click(GuiTestSupport.find(f, ComponentNames.RUN_START, AbstractButton.class));
            long start = System.currentTimeMillis();
            for (int k = 1; k <= 4; k++) {
                long until = start + k * 3000L;
                GuiTestSupport.waitFor(() -> System.currentTimeMillis() >= until, 10_000);
                shot(t.view, W, H, "visual-20-during-" + k);
            }
            shot(p.chart(), 500, 350, "visual-21-chart-during");
            assertTrue(GuiTestSupport.waitFor(() -> {
                for (var s : f.runs().slots()) if (s.lastResult() == null && s.failure() == null) return false;
                return true;
            }, 900_000));
            GuiTestSupport.waitFor(() -> false, 4500);      // two more timer ticks
            flushEdt();
            shot(t.view, W, H, "visual-22-after");
            shot(t.view, 1280, 800, "visual-22-after-1280x800");
            shot(p.chart(), 500, 350, "visual-23-chart-after");
            onEdt(() -> p.chart().toggleDisplay());
            shot(p.chart(), 500, 350, "visual-23-chart-after-kT");
            onEdt(() -> p.chart().toggleDisplay());
            click(GuiTestSupport.find(f, ComponentNames.RUN_SUMMARIZE, AbstractButton.class));
            ScriptedUi.Window w = t.ui.lastWindow();
            assertTrue(w.title.startsWith("Structure Display - "), w.title);   // dataset name in the title (DV-88)
            StructureDisplayPanel display = (StructureDisplayPanel) w.content;
            shot(display, "visual-24-summarize-default");
            List<StructureDisplayPanel.Tile> tiles = onEdt(display::tileList);
            shot(display.tiles(), 600, Math.min(5, (tiles.size() + 1) / 2) * 200, "visual-25-summarize-tiles");
            // report: each tile's constitution vs its MDL
            StringBuilder rep = new StringBuilder();
            for (int i = 0; i < Math.min(10, tiles.size()); i++) {
                StructureDisplayPanel.Tile tile = tiles.get(i);
                Depictions.Structure s = tile.structure();
                String mdl = s.mdl("x");
                rep.append("tile ").append(i).append(' ').append(tile.title()).append(": ")
                        .append(compare(mdl, s.molecule())).append('\n');
                BufferedImage big = Depictions.render(s, 900, 600, 1.0);
                ImageIO.write(big, "png", GuiTestSupport.shotsDir().resolve("visual-26-tile" + i + "-big.png").toFile());
            }
            Files.writeString(GuiTestSupport.shotsDir().resolve("visual-26-tiles.txt"), rep);
        }
    }

    /** Golden best structures (several datasets, heteroatoms): drawn constitution = connection table. */
    @Test
    void depictionsOfGoldenBestStructures() throws Exception {
        String[][] runs = {{"pinene", "gui", "1"}, {"pinene", "gui", "2"}, {"eurabidiol", "gui", "1"}, {"monochaetin", "paper", "1"},
                {"monochaetin", "gui", "2"}, {"narelin", "paper", "1"}, {"Hyperaspine", "paper", "2"}, {"polycarpol", "paper", "2"},
                {"triterpen", "paper", "1"}, {"bn-ye1", "paper", "1"}, {"eupatorio", "paper", "1"}, {"preconen", "paper", "1"}};
        Files.createDirectories(GuiTestSupport.shotsDir());
        StringBuilder rep = new StringBuilder();
        int mismatches = 0;
        for (String[] r : runs) {
            RunGolden g = GoldenRunAssert.golden(r[0], r[1], Long.parseLong(r[2]));
            String mdl = g.record.str("bestMDL");
            Depictions.Structure s = Depictions.fromMdl(mdl);
            assertTrue(s.drawable(), r[0]);
            String name = r[0] + "-" + r[1] + "-" + r[2];
            ImageIO.write(Depictions.render(s, 300, 200, 1.0), "png", GuiTestSupport.shotsDir().resolve("visual-30-" + name + "-tile.png").toFile());
            ImageIO.write(Depictions.render(s, 900, 600, 1.0), "png", GuiTestSupport.shotsDir().resolve("visual-30-" + name + "-big.png").toFile());
            String c = compare(mdl, s.molecule());
            if (c.contains("MISMATCH")) mismatches++;
            rep.append(name).append(": ").append(c).append('\n');
        }
        Files.writeString(GuiTestSupport.shotsDir().resolve("visual-30-depictions.txt"), rep);
        System.out.println(rep);
        assertEquals(0, mismatches, rep.toString());
    }

    /**
     * Diagnostic for the fix suggestion: does laying out the same constitution in another atom order
     * (canonical SMILES order, or shuffled orders) avoid the crossings of the current layout?
     */
    @Test
    void alternativeAtomOrdersAvoidCrossings() throws Exception {
        String[][] runs = {{"pinene", "gui", "1"}, {"pinene", "gui", "2"}, {"pinene", "paper", "1"}, {"monochaetin", "paper", "1"},
                {"polycarpol", "paper", "1"}, {"polycarpol", "paper", "2"}, {"polycarpol", "paper", "3"}, {"triterpen", "paper", "1"},
                {"narelin", "paper", "1"}};
        Files.createDirectories(GuiTestSupport.shotsDir());
        StringBuilder rep = new StringBuilder();
        SmilesGenerator sg = new SmilesGenerator(SmiFlavor.Unique);
        org.openscience.cdk.smiles.SmilesParser sp = new org.openscience.cdk.smiles.SmilesParser(SilentChemObjectBuilder.getInstance());
        for (String[] r : runs) {
            RunGolden g = GoldenRunAssert.golden(r[0], r[1], Long.parseLong(r[2]));
            List<String> mdls = new ArrayList<>();
            mdls.add(g.record.str("bestMDL"));
            for (String mdl : mdls) {
                Depictions.Structure s = Depictions.fromMdl(mdl);
                String cur = layoutProblems(s.molecule());
                IAtomContainer canon = sp.parseSmiles(sg.create(s.molecule()));
                new org.openscience.cdk.layout.StructureDiagramGenerator().generateCoordinates(canon);
                String can = layoutProblems(canon);
                int best = Integer.MAX_VALUE;
                java.util.Random rnd = new java.util.Random(42);
                for (int k = 0; k < 20; k++) {
                    IAtomContainer m = s.molecule().clone();
                    List<IAtom> atoms = new ArrayList<>();
                    for (IAtom a : m.atoms()) atoms.add(a);
                    java.util.Collections.shuffle(atoms, rnd);
                    m.setAtoms(atoms.toArray(new IAtom[0]));
                    new org.openscience.cdk.layout.StructureDiagramGenerator().generateCoordinates(m);
                    best = Math.min(best, crossingsPlusClose(m));
                }
                rep.append(r[0]).append('-').append(r[1]).append('-').append(r[2]).append("\n  current:   ").append(cur)
                        .append("\n  canonical: ").append(can).append("\n  best of 20 shuffled orders: crossings+close=").append(best).append('\n');
                if (r[0].equals("pinene") && r[2].equals("1") && r[1].equals("gui")) {
                    Depictions.Structure c = Depictions.fromAtoms(io.github.steinbeck.seneca.chem.LegacyMdlReader.read(mdl));
                    ImageIO.write(new org.openscience.cdk.depict.DepictionGenerator().withSize(300, 200).withFillToFit().depict(canon).toImg(), "png",
                            GuiTestSupport.shotsDir().resolve("visual-31-pinene-canonical-order.png").toFile());
                    assertNotNull(c);
                }
            }
        }
        Files.createDirectories(GuiTestSupport.shotsDir());
        Files.writeString(GuiTestSupport.shotsDir().resolve("visual-31-alternative-layouts.txt"), rep);
        System.out.println(rep);
    }

    static int crossingsPlusClose(IAtomContainer m) {
        String s = layoutProblems(m);
        java.util.regex.Matcher x = java.util.regex.Pattern.compile("crossings=(\\d+) closeNonBonded\\(<0.5L\\)=(\\d+)").matcher(s);
        return x.find() ? Integer.parseInt(x.group(1)) + Integer.parseInt(x.group(2)) : Integer.MAX_VALUE;
    }

    /** A large dataset (polycarpol, 32 heavy atoms, "all" recipe): the wide tables at the default size. */
    @Test
    void largeDatasetTablesAtDefaultSize() throws Exception {
        try (TestApp t = GuiTestSupport.newApp("visual-large", TestHooks.none())) {
            SenecaDataset sd = openViaToolbar(t, "polycarpol");
            DatasetFrame f = onEdt(() -> t.app.frameOf(sd));
            SenecaDataset recipe = GuiRecipe.prepare(GuiTestSupport.sml("polycarpol"), "polycarpol", Profile.ALL, t.app.compat).sd;
            RecipeThroughPanelsTest.configure(f, "polycarpol", Profile.ALL, recipe);
            JTabbedPane tabs = f.tabs();
            onEdt(() -> tabs.setSelectedIndex(2));
            shot(t.view, W, H, "visual-40-large-atoms");
            JTabbedPane spectra = GuiTestSupport.find(f, ComponentNames.SPECTRA_TABS, JTabbedPane.class);
            onEdt(() -> tabs.setSelectedIndex(1));
            for (int i : new int[] {0, 4, 5}) {
                onEdt(() -> spectra.setSelectedIndex(i));
                shot(t.view, W, H, "visual-41-large-spectra-" + (i + 1));
            }
            JTabbedPane judges = GuiTestSupport.find(f, ComponentNames.JUDGE_TABS, JTabbedPane.class);
            onEdt(() -> tabs.setSelectedIndex(4));
            for (int i = 0; i < onEdt(judges::getTabCount); i++) {
                int k = i;
                onEdt(() -> judges.setSelectedIndex(k));
                shot(t.view, W, H, "visual-42-large-judges-" + (i + 1));
            }
            // the run table paints a selected row in the selection colour on screen (printAll suppresses it)
            onEdt(() -> f.showTab("Structure Generation"));
            StructureGenerationPanel p = runPanel(f);
            onEdt(() -> p.timer().stop());
            click(GuiTestSupport.find(f, ComponentNames.RUN_ADD_SLOT, AbstractButton.class));
            click(GuiTestSupport.find(f, ComponentNames.RUN_ADD_SLOT, AbstractButton.class));
            t.app.awaitControl(60_000);
            flushEdt();
            String colours = onEdt(() -> {
                JTable tb = p.table();
                tb.setRowSelectionInterval(0, 0);
                java.awt.Component sel = tb.prepareRenderer(tb.getCellRenderer(0, 1), 0, 1);
                java.awt.Color a = sel.getBackground();
                java.awt.Component un = tb.prepareRenderer(tb.getCellRenderer(1, 1), 1, 1);
                return a + " / " + un.getBackground();
            });
            System.out.println("run table selected / unselected background: " + colours);
            Files.writeString(GuiTestSupport.shotsDir().resolve("visual-43-selection-colours.txt"), colours);
        }
    }

    // ------------------------------------------------------------------ chemistry checks

    /** Stats of the MDL read by CDK vs the depicted molecule; overlaps and crossings of the layout. */
    static String compare(String mdl, IAtomContainer drawn) throws Exception {
        IAtomContainer ref;
        try (MDLV2000Reader rd = new MDLV2000Reader(new StringReader(mdl))) {
            ref = rd.read(SilentChemObjectBuilder.getInstance().newAtomContainer());
        }
        String a = stats(ref), b = stats(drawn);
        SmilesGenerator sg = new SmilesGenerator(SmiFlavor.Unique);
        String sa = sg.create(ref), sb = sg.create(drawn);
        StringBuilder out = new StringBuilder();
        out.append(a.equals(b) && sa.equals(sb) ? "OK " : "MISMATCH ");
        out.append("ct[").append(a).append("] drawn[").append(b).append("] smiles ").append(sa);
        if (!sa.equals(sb)) out.append(" vs ").append(sb);
        out.append(" | ").append(layoutProblems(drawn));
        return out.toString();
    }

    static String stats(IAtomContainer m) {
        int dbl = 0, tpl = 0;
        for (IBond b : m.bonds()) {
            if (b.getOrder() == IBond.Order.DOUBLE) dbl++;
            if (b.getOrder() == IBond.Order.TRIPLE) tpl++;
        }
        Map<String, Integer> hetero = new TreeMap<>();
        int heteroH = 0;
        for (IAtom at : m.atoms()) {
            if (!at.getSymbol().equals("C")) {
                hetero.merge(at.getSymbol(), 1, Integer::sum);
                heteroH += at.getImplicitHydrogenCount() == null ? 0 : at.getImplicitHydrogenCount();
            }
        }
        int comps = ConnectivityChecker.partitionIntoMolecules(m).getAtomContainerCount();
        int rings = m.getBondCount() - m.getAtomCount() + comps;
        return "atoms=" + m.getAtomCount() + " bonds=" + m.getBondCount() + " dbl=" + dbl + " tpl=" + tpl + " hetero=" + hetero
                + " heteroH=" + heteroH + " rings=" + rings + " fragments=" + comps;
    }

    static String layoutProblems(IAtomContainer m) {
        if (m == null || m.getAtomCount() == 0) return "no layout";
        List<Double> lens = new ArrayList<>();
        for (IBond b : m.bonds()) lens.add(b.getBegin().getPoint2d().distance(b.getEnd().getPoint2d()));
        lens.sort(Double::compare);
        double L = lens.isEmpty() ? 1.5 : lens.get(lens.size() / 2);
        int close = 0;
        double minD = Double.MAX_VALUE;
        for (int i = 0; i < m.getAtomCount(); i++)
            for (int j = i + 1; j < m.getAtomCount(); j++) {
                if (m.getBond(m.getAtom(i), m.getAtom(j)) != null) continue;
                double d = m.getAtom(i).getPoint2d().distance(m.getAtom(j).getPoint2d());
                minD = Math.min(minD, d);
                if (d < 0.5 * L) close++;
            }
        int cross = 0;
        List<IBond> bs = new ArrayList<>();
        for (IBond b : m.bonds()) bs.add(b);
        for (int i = 0; i < bs.size(); i++)
            for (int j = i + 1; j < bs.size(); j++) {
                IBond x = bs.get(i), y = bs.get(j);
                if (x.contains(y.getBegin()) || x.contains(y.getEnd())) continue;
                if (intersects(x.getBegin().getPoint2d(), x.getEnd().getPoint2d(), y.getBegin().getPoint2d(), y.getEnd().getPoint2d())) cross++;
            }
        double minBond = lens.isEmpty() ? 0 : lens.get(0), maxBond = lens.isEmpty() ? 0 : lens.get(lens.size() - 1);
        return String.format("layout: crossings=%d closeNonBonded(<0.5L)=%d minNonBonded=%.2fL bondLen %.2f..%.2fL", cross, close,
                minD / L, minBond / L, maxBond / L);
    }

    static boolean intersects(Point2d p1, Point2d p2, Point2d p3, Point2d p4) {
        double d1 = cross(p3, p4, p1), d2 = cross(p3, p4, p2), d3 = cross(p1, p2, p3), d4 = cross(p1, p2, p4);
        return ((d1 > 1e-9 && d2 < -1e-9) || (d1 < -1e-9 && d2 > 1e-9)) && ((d3 > 1e-9 && d4 < -1e-9) || (d3 < -1e-9 && d4 > 1e-9));
    }

    static double cross(Point2d a, Point2d b, Point2d c) {
        return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
    }
}
