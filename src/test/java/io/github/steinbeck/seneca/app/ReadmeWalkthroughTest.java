package io.github.steinbeck.seneca.app;

import static io.github.steinbeck.seneca.app.GuiTestSupport.click;
import static io.github.steinbeck.seneca.app.GuiTestSupport.flushEdt;
import static io.github.steinbeck.seneca.app.GuiTestSupport.menuItem;
import static io.github.steinbeck.seneca.app.GuiTestSupport.onEdt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.AbstractButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.RepaintManager;
import javax.swing.text.JTextComponent;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.app.GuiTestSupport.TestApp;
import io.github.steinbeck.seneca.app.dataset.AtomPropertiesPanel;
import io.github.steinbeck.seneca.app.dataset.DatasetFrame;
import io.github.steinbeck.seneca.app.run.StructureGenerationPanel;
import io.github.steinbeck.seneca.app.structure.Depictions;
import io.github.steinbeck.seneca.app.structure.StructureDisplayPanel;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.config.GuiRecipe;
import io.github.steinbeck.seneca.config.Profile;
import io.github.steinbeck.seneca.golden.EchoRecords;
import io.github.steinbeck.seneca.golden.GoldenFiles;
import io.github.steinbeck.seneca.golden.JsonDiff;
import io.github.steinbeck.seneca.golden.ReferenceCheck;
import io.github.steinbeck.seneca.model.AnnealingSchedule;
import io.github.steinbeck.seneca.model.SenecaDataset;
import io.github.steinbeck.seneca.structgen.RunResult;
import io.github.steinbeck.seneca.structgen.RunSlot;

/**
 * The step-by-step guide of README.md ("Run polycarpol"), done through the GUI exactly as the README
 * describes it, with the run seeded as {@code open Seneca.app --args --seed=<n>} seeds it (the first run
 * slot runs with seed n):
 *
 * <ol>
 *   <li>File &gt; Open &gt; Example Dataset &gt; polycarpol (no file dialog): window "AMAZ.COL", C30H48O2,
 *       32 rows in Atom Properties (30 C, then 2 O).</li>
 *   <li>Atom Properties &gt; "Autoassignment": the 30 carbons get their 13C shifts and H counts from the
 *       DEPT spectra; the line next to the button turns from "... 48 - ... 0" to the red
 *       {@value #BALANCE_AFTER_AUTOASSIGNMENT}.</li>
 *   <li>The two hydroxyl hydrogens by hand: column "Hydrogens", rows 31 and 32 (Symbol O), type 1 each;
 *       the line turns green, {@value #BALANCE_DONE}.</li>
 *   <li>Judges: in the sub-tabs "H,H-COSY", "C,H-long-range Corr." and "Carbon Shifts" (variant: also
 *       "Features") press "Autoconfigure Judge"; "Activate this Judge?" is then ticked. No message appears.</li>
 *   <li>Annealing Schedule: nothing to do, the file brings one schedule (0.8 / 10000 / 0.95 / 1000).</li>
 *   <li>Structure Generation: "Add run slot", click the new row, "Start as selected". The row shows
 *       "Running", then "Holding Results" and the iterations in column "Iter."; the chart "Annealing Data"
 *       climbs to the dashed "max. Score" line, "Best structure of no.1" shows the last structure with the
 *       best score and "Evaluation of no. 1" its "Score: 8000/8000".</li>
 *   <li>"Summarize Result": window "Structure Display - AMAZ.COL", tiles sorted by score; several different
 *       constitutions have the maximum score 8000, polycarpol (lanosta-7,9(11),24-triene-3&beta;,15&alpha;-diol)
 *       is one of them.</li>
 * </ol>
 * The dataset configured this way equals the golden recipe ({@code GuiRecipe.prepare}, profile "paper" for
 * the three judges, "gui" with Features) record by record.
 *
 * <p>README screenshots: {@code -Dseneca.readmeImages=<dir>} writes them at twice the size (Retina) to
 * {@code <dir>} (docs/images); with {@code -Dseneca.headless=false} in the macOS look (hidden frames, nothing
 * is shown). Without the property they go to {@code <build>/gui-shots/readme}.
 */
class ReadmeWalkthroughTest {

    /** The judge sub-tabs of the README (title, judge class): the golden "paper" profile (golden/FORMAT.md, recipe step 5). */
    static final String[][] THREE_JUDGES = {
        {"H,H-COSY", "HHCOSYJudge"},
        {"C,H-long-range Corr.", "HMBCJudge"},
        {"Carbon Shifts", "HOSECodeJudge"},
    };
    /** The optional variant of the README: the same three, then "Features" (penalizes 3- and 4-membered rings). */
    static final String[][] WITH_FEATURES = {
        {"H,H-COSY", "HHCOSYJudge"},
        {"C,H-long-range Corr.", "HMBCJudge"},
        {"Carbon Shifts", "HOSECodeJudge"},
        {"Features", "FeatureJudge"},
    };
    static final long MAX_SCORE = 8000;

    static final String BALANCE_BEFORE = "HCount in molecular formula: 48 - Sum of HCounts in this table: 0";
    static final String BALANCE_AFTER_AUTOASSIGNMENT = "HCount in molecular formula: 48 - Sum of HCounts in this table: 46";
    static final String BALANCE_DONE = "HCount in molecular formula: 48 - Sum of HCounts in this table: 48";

    /** Pixel scale of the README images (Retina). */
    static final int SCALE = 2;

    /**
     * One documented run.
     *
     * @param seed           the app's {@code --seed=}: its first run slot runs with this seed
     * @param judges         sub-tabs in which "Autoconfigure Judge" is pressed (title, judge class)
     * @param profile        the golden recipe profile with exactly these judges
     * @param iterations     iterations of the run (column "Iter.")
     * @param polycarpolTile 1-based position of polycarpol among the "Summarize Result" tiles
     * @param tilesAtMax     tiles with the maximum score (all different constitutions)
     * @param endsOnPolycarpol whether the run's best structure ("Best structure of no.1") is polycarpol
     * @param images         README images to write: step key ("atoms", "hydrogens", "judges", "finished",
     *                       "summarize", "structure") to file name without ".png"; absent keys are not written
     */
    record Plan(long seed, String[][] judges, Profile profile, long iterations, int polycarpolTile, int tilesAtMax,
                boolean endsOnPolycarpol, Map<String, String> images) {
    }

    /** The README's walkthrough: three judges, {@code --seed=6} (about 380,000 iterations). */
    @Test
    void polycarpolStepByStepAsTheReadmeDescribesIt() throws Exception {
        walkthrough(new Plan(6, THREE_JUDGES, Profile.PAPER, 377_824, 2, 6, false, Map.of(
                "atoms", "02-atom-properties-autoassignment",
                "hydrogens", "03-atom-properties-hydroxyl-hydrogens",
                "judges", "04-judges-autoconfigure",
                "finished", "01-structure-generation-finished",
                "summarize", "05-summarize-result",
                "structure", "06-polycarpol-structure")));
    }

    /**
     * The README's variant with "Features" as fourth judge, {@code --seed=3}: this run ends on polycarpol itself
     * (about 980,000 iterations; the judge makes every iteration five to six times slower, hence slow).
     */
    @Tag("slow")
    @Test
    void polycarpolWithTheFeaturesJudgeEndsOnPolycarpolWithSeed3() throws Exception {
        walkthrough(new Plan(3, WITH_FEATURES, Profile.GUI, 982_812, 6, 6, true, Map.of(
                "finished", "07-features-run-ends-on-polycarpol")));
    }

    static void walkthrough(Plan p) throws Exception {
        try (TestApp t = GuiTestSupport.newApp("readme-walkthrough", new TestHooks(p.seed(), 0))) {
            int w = MainView.DEFAULT_SIZE.width, h = MainView.DEFAULT_SIZE.height;

            // 1. File > Open > Example Dataset > polycarpol
            AbstractButton example = menuItem(t.menuBar, "File", "Open", "Example Dataset", "polycarpol");
            assertNotNull(example, "File > Open > Example Dataset > polycarpol");
            click(example);
            flushEdt();
            assertEquals(0, t.ui.fileRequests.size(), "an example opens without a file dialog");
            SenecaDataset sd = onEdt(t.app.datasets::current);
            assertNotNull(sd, "no dataset opened");
            DatasetFrame f = onEdt(() -> t.app.frameOf(sd));
            assertNotNull(f, "no dataset window");
            assertEquals("AMAZ.COL", onEdt(f::getTitle));
            assertEquals("C30H48O2", sd.getMolecularFormula());
            assertEquals("C30H48O2", onEdt(() -> t.view.statusBar().status(2)));

            // 2. Atom Properties > Autoassignment
            onEdt(() -> f.showTab("Atom Properties"));
            AtomPropertiesPanel atomsPanel = onEdt(() -> (AtomPropertiesPanel) f.tabs().getSelectedComponent());
            JTable atoms = atomsPanel.atomsTable();
            JLabel balance = GuiTestSupport.find(f, ComponentNames.H_BALANCE, JLabel.class);
            assertNotNull(balance, "H balance line");
            assertEquals(32, onEdt(atoms::getRowCount));
            assertEquals(BALANCE_BEFORE, onEdt(balance::getText));
            click(GuiTestSupport.find(f, ComponentNames.AUTOASSIGN, AbstractButton.class));
            flushEdt();
            assertTrue(t.ui.shown.isEmpty(), "Autoassignment showed a message");
            assertEquals(BALANCE_AFTER_AUTOASSIGNMENT, onEdt(balance::getText));
            assertEquals(AtomPropertiesPanel.UNBALANCED, onEdt(balance::getForeground), "red");
            for (int r = 0; r < 32; r++) {
                int row = r;
                String symbol = onEdt(() -> String.valueOf(atoms.getValueAt(row, 1)));
                String shift = onEdt(() -> String.valueOf(atoms.getValueAt(row, 3)));
                assertEquals(r < 30 ? "C" : "O", symbol, "row " + (r + 1));
                assertEquals(r < 30, !"-".equals(shift), "row " + (r + 1) + ": a carbon has a shift, an oxygen none");
            }
            assertEquals("146.0", onEdt(() -> String.valueOf(atoms.getValueAt(0, 3))), "row 1: the highest shift");
            assertEquals("0", onEdt(() -> String.valueOf(atoms.getValueAt(30, 2))), "row 31: O without H");
            assertEquals("0", onEdt(() -> String.valueOf(atoms.getValueAt(31, 2))), "row 32: O without H");
            onEdt(() -> {
                atoms.scrollRectToVisible(atoms.getCellRect(0, 0, true));
                atoms.clearSelection();
            });
            readmeShot(p, "atoms", t.view, w, h, null);

            // 3. Hydrogens: 1 in rows 31 and 32 (the two O)
            assertTrue(edit(atoms, 30, 2, "1"), "row 31");
            assertTrue(edit(atoms, 31, 2, "1"), "row 32");
            flushEdt();
            assertEquals(BALANCE_DONE, onEdt(balance::getText));
            assertEquals(AtomPropertiesPanel.BALANCED, onEdt(balance::getForeground), "green");
            assertEquals(1, sd.getSetOfNodes()[30].hCount);
            assertEquals(1, sd.getSetOfNodes()[31].hCount);
            onEdt(() -> {
                atoms.setRowSelectionInterval(30, 31);
                atoms.setColumnSelectionInterval(0, 3);
            });
            // the rendering lays the panel out first; scroll to the bottom rows inside it
            readmeShot(p, "hydrogens", t.view, w, h, () -> atoms.scrollRectToVisible(atoms.getCellRect(31, 0, true)));
            onEdt(atoms::clearSelection);

            // 4. Judges: Autoconfigure Judge in each of the plan's sub-tabs
            onEdt(() -> f.showTab("Judges"));
            JTabbedPane judgeTabs = GuiTestSupport.find(f, ComponentNames.JUDGE_TABS, JTabbedPane.class);
            assertNotNull(judgeTabs, "Judges sub-tabs");
            for (String[] j : p.judges()) {
                int index = onEdt(() -> judgeTabs.indexOfTab(j[0]));
                assertTrue(index >= 0, "sub-tab " + j[0]);
                onEdt(() -> judgeTabs.setSelectedIndex(index));
                JCheckBox activate = GuiTestSupport.find(f, ComponentNames.JUDGE_ACTIVATE + j[1], JCheckBox.class);
                assertFalse(onEdt(activate::isSelected), j[0] + ": inactive before Autoconfigure");
                click(GuiTestSupport.find(f, ComponentNames.JUDGE_AUTOCONFIGURE + j[1], AbstractButton.class));
                flushEdt();
                assertTrue(onEdt(activate::isSelected), j[0] + ": \"Activate this Judge?\" ticked by Autoconfigure");
                assertTrue(sd.getJudge(j[1]).getEnabled(), j[1]);
            }
            assertTrue(t.ui.shown.isEmpty(), "Autoconfigure showed a message: " + (t.ui.shown.isEmpty() ? "" : t.ui.lastShown().message()));
            onEdt(() -> judgeTabs.setSelectedIndex(judgeTabs.indexOfTab("C,H-long-range Corr.")));
            readmeShot(p, "judges", t.view, w, h, null);

            // 5. Annealing Schedule: the file's schedule, nothing to change
            assertEquals(1, sd.annealingSchedules.size());
            AnnealingSchedule s = sd.annealingSchedules.get(0);
            assertEquals(0.8, s.initialAcceptanceProbability);
            assertEquals(10000, s.plateauSteps);
            assertEquals(0.95, s.asymptoticCoolingFactor);
            assertEquals(1000, s.maxUphillSteps);

            // the steps above are the golden recipe of the plan's profile
            SenecaDataset recipe = GuiRecipe.prepare(GoldenFiles.sml("polycarpol"), "polycarpol", p.profile(), t.app.compat).sd;
            List<String> want = EchoRecords.configuredRecords(recipe, p.profile(), LegacyCompat.DEFAULT);
            List<String> got = onEdt(() -> EchoRecords.configuredRecords(sd, p.profile(), LegacyCompat.DEFAULT));
            assertEquals(want.size(), got.size());
            for (int i = 0; i < want.size(); i++)
                JsonDiff.assertSameRecord("polycarpol/" + p.profile().key + " configured record " + i, want.get(i), got.get(i), Set.of());
            ReferenceCheck ref = ReferenceCheck.of(GoldenFiles.referencesTsv(), "polycarpol", sd.getSetOfNodes());
            assertTrue(ref.mappable(), "the configured atoms fit the literature structure");

            // 6. Structure Generation: Add run slot, select the row, Start as selected
            onEdt(() -> f.showTab("Structure Generation"));
            StructureGenerationPanel panel = onEdt(() -> (StructureGenerationPanel) f.tabs().getSelectedComponent());
            onEdt(() -> panel.timer().stop());         // the test polls itself, deterministically
            click(GuiTestSupport.find(f, ComponentNames.RUN_ADD_SLOT, AbstractButton.class));
            t.app.awaitControl(60_000);
            flushEdt();
            JTable table = panel.table();
            assertEquals(1, onEdt(table::getRowCount));
            assertEquals("idle", onEdt(() -> String.valueOf(table.getValueAt(0, 5))));
            onEdt(() -> table.setRowSelectionInterval(0, 0));
            long t0 = System.nanoTime();
            click(GuiTestSupport.find(f, ComponentNames.RUN_START, AbstractButton.class));
            t.app.awaitControl(60_000);
            assertTrue(t.ui.shown.isEmpty(), "Start as selected showed a message");
            RunSlot slot = f.runs().slots().get(0);
            assertEquals(Long.valueOf(p.seed()), t.app.runOptions(slot).seed());
            assertTrue(GuiTestSupport.waitFor(() -> slot.lastResult() != null || slot.failure() != null, 3_600_000), "run did not end");
            long wallMs = (System.nanoTime() - t0) / 1_000_000;
            assertNull(slot.failure());
            flushEdt();
            onEdt(panel::poll);

            RunResult r = slot.lastResult();
            assertEquals("max_score_reached", r.goldenTermination());
            assertEquals(p.iterations(), r.iterations());
            assertEquals(MAX_SCORE, r.maxScore());
            assertEquals(MAX_SCORE, r.bestScore().score);
            assertEquals(p.endsOnPolycarpol(), ref.equalConstitution(ct(r.bestStructure())), "best structure = polycarpol");
            assertEquals("Holding Results", onEdt(() -> String.valueOf(table.getValueAt(0, 5))));
            assertEquals(String.valueOf(p.iterations()), onEdt(() -> String.valueOf(table.getValueAt(0, 6))));
            assertEquals("Best structure of no.1", onEdt(panel::bestStructureTitle));
            assertTrue(onEdt(() -> panel.bestStructureView().structure().drawable()), "best structure drawn");
            String eval = onEdt(() -> panel.evaluationPane().getText());
            assertTrue(eval.contains("Score: " + MAX_SCORE + "/" + MAX_SCORE), eval);
            assertTrue(onEdt(() -> panel.chart().data().hasData()), "annealing chart has data");
            assertTrue(onEdt(t.app.logbook::text).contains("Maximum score reached. Regular end of calculation."));
            System.out.println("ReadmeWalkthroughTest: polycarpol " + p.profile().key + " seed " + p.seed() + ", " + r.iterations()
                    + " iterations in " + wallMs + " ms (" + Math.round(r.iterations() * 1000.0 / Math.max(1, wallMs))
                    + " it/s), best " + r.bestScore().score + "/" + r.maxScore());
            onEdt(table::clearSelection);
            readmeShot(p, "finished", t.view, w, h, null);

            // 7. Summarize Result
            click(GuiTestSupport.find(f, ComponentNames.RUN_SUMMARIZE, AbstractButton.class));
            ScriptedUi.Window win = t.ui.lastWindow();
            assertNotNull(win, "Structure Display window");
            assertEquals("Structure Display - AMAZ.COL", win.title);
            StructureDisplayPanel display = (StructureDisplayPanel) win.content;
            List<StructureDisplayPanel.Tile> tiles = onEdt(display::tileList);
            List<int[][]> atMax = new ArrayList<>();
            int polycarpol = -1;
            for (int i = 0; i < tiles.size(); i++) {
                StructureDisplayPanel.Tile tile = tiles.get(i);
                int[][] bm = ct(onEdt(() -> tile.structure().mdl("x")));
                if (polycarpol < 0 && ref.equalConstitution(bm)) polycarpol = i + 1;
                if (tile.title().equals("Score " + MAX_SCORE)) atMax.add(bm);
            }
            assertEquals("Score " + MAX_SCORE, tiles.get(0).title());
            assertEquals(p.polycarpolTile(), polycarpol, "polycarpol's tile");
            assertEquals("Score " + MAX_SCORE, tiles.get(polycarpol - 1).title(), "polycarpol has the maximum score");
            assertEquals(p.tilesAtMax(), atMax.size(), "tiles with the maximum score");
            for (int a = 0; a < atMax.size(); a++)
                for (int b = a + 1; b < atMax.size(); b++)
                    assertFalse(sameConstitution(sd.getSetOfNodes(), atMax.get(a), atMax.get(b)), "tiles " + (a + 1) + " and " + (b + 1));
            StructureDisplayPanel.Tile hit = tiles.get(polycarpol - 1);
            onEdt(() -> display.select(hit));
            readmeShot(p, "summarize", display, 628, 661, null);     // three rows of tiles

            // the literature structure as SENECA found it, large
            if (p.images().containsKey("structure"))
                write(onEdt(() -> Depictions.render(hit.structure(), 640, 420, SCALE)), p.images().get("structure"));
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Edits a table cell as a user does (editor, text, commit); false if the editor refused the value. */
    static boolean edit(JTable table, int row, int column, String value) {
        return onEdt(() -> {
            if (!table.editCellAt(row, column)) throw new AssertionError("cell " + row + "/" + column + " is not editable");
            Component c = table.getEditorComponent();
            if (!(c instanceof JTextComponent tc)) throw new AssertionError("unexpected editor " + c);
            tc.setText(value);
            boolean ok = table.getCellEditor().stopCellEditing();
            if (!ok) table.getCellEditor().cancelCellEditing();
            return ok;
        });
    }

    static int[][] ct(String mdl) {
        return GoldenRunAssert.ct(mdl);
    }

    static boolean sameConstitution(HeavyAtom[] atoms, int[][] a, int[][] b) {
        String[] el = new String[atoms.length];
        int[] h = new int[atoms.length];
        for (int i = 0; i < atoms.length; i++) {
            el[i] = atoms[i].symbol;
            h[i] = atoms[i].hCount;
        }
        return ReferenceCheck.sameConstitution(el, h, a, el, h, b);
    }

    /** Where the README images go: {@code seneca.readmeImages}, else {@code <gui-shots>/readme}. */
    static Path imagesDir() {
        String p = System.getProperty("seneca.readmeImages");
        return p != null && !p.isBlank() ? Path.of(p) : GuiTestSupport.shotsDir().resolve("readme");
    }

    /** {@link #readmeShot(Component, int, int, String, Runnable)} if the plan wants image {@code key}. */
    static void readmeShot(Plan p, String key, Component c, int width, int height, Runnable afterLayout) {
        String name = p.images().get(key);
        if (name != null) readmeShot(c, width, height, name, afterLayout);
    }

    /**
     * Renders {@code c} at {@code width x height} logical pixels and {@link #SCALE} times the pixels (as
     * {@code GuiTestSupport.render}, with the native look and feel hosted in a hidden frame), then writes
     * {@code <imagesDir>/<name>.png}. {@code afterLayout} runs on the EDT after the layout, before painting.
     */
    static void readmeShot(Component c, int width, int height, String name, Runnable afterLayout) {
        BufferedImage img = onEdt(() -> {
            c.setSize(width, height);
            GuiTestSupport.layoutTree(c);
            GuiTestSupport.layoutTree(c);
            JFrame host = null;
            if (!GraphicsEnvironment.isHeadless() && c.getParent() == null && !(c instanceof java.awt.Window)) {
                host = new JFrame();
                host.setUndecorated(true);
                host.getContentPane().setLayout(null);
                host.getContentPane().add(c);
                host.addNotify();
                host.setSize(width, height);
                c.setBounds(0, 0, width, height);
                host.validate();
            }
            try {
                GuiTestSupport.layoutTree(c);
                if (afterLayout != null) {
                    afterLayout.run();
                    GuiTestSupport.layoutTree(c);
                }
                BufferedImage out = new BufferedImage(width * SCALE, height * SCALE, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = out.createGraphics();
                g.setColor(c.getBackground() != null ? c.getBackground() : Color.white);
                g.fillRect(0, 0, out.getWidth(), out.getHeight());
                g.scale(SCALE, SCALE);
                JComponent jc = (JComponent) c;
                RepaintManager rm = RepaintManager.currentManager(jc);
                boolean db = rm.isDoubleBufferingEnabled();
                rm.setDoubleBufferingEnabled(false);
                try {
                    jc.paint(g);
                } finally {
                    rm.setDoubleBufferingEnabled(db);
                }
                g.dispose();
                return out;
            } finally {
                if (host != null) {
                    host.getContentPane().remove(c);
                    host.dispose();
                }
            }
        });
        write(img, name);
    }

    /** PNG with the strongest compression. */
    static void write(BufferedImage img, String name) {
        try {
            Files.createDirectories(imagesDir());
            Path p = imagesDir().resolve(name + ".png");
            ImageWriter writer = ImageIO.getImageWritersByFormatName("png").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(0.0f);
            }
            Files.deleteIfExists(p);
            try (ImageOutputStream out = ImageIO.createImageOutputStream(p.toFile())) {
                writer.setOutput(out);
                writer.write(null, new IIOImage(img, null, null), param);
            } finally {
                writer.dispose();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
