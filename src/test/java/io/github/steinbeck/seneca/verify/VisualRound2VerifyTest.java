package io.github.steinbeck.seneca.verify;

import static io.github.steinbeck.seneca.app.GuiTestSupport.click;
import static io.github.steinbeck.seneca.app.GuiTestSupport.flushEdt;
import static io.github.steinbeck.seneca.app.GuiTestSupport.onEdt;
import static io.github.steinbeck.seneca.app.GuiTestSupport.shot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Rectangle;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JTextPane;
import javax.swing.JViewport;
import javax.vecmath.Point2d;

import org.junit.jupiter.api.Test;
import org.openscience.cdk.depict.DepictionGenerator;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IBond;
import org.openscience.cdk.io.MDLV2000Reader;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmiFlavor;
import org.openscience.cdk.smiles.SmilesGenerator;
import org.openscience.cdk.tools.manipulator.MolecularFormulaManipulator;

import io.github.steinbeck.seneca.app.ComponentNames;
import io.github.steinbeck.seneca.app.GuiTestSupport;
import io.github.steinbeck.seneca.app.GuiTestSupport.TestApp;
import io.github.steinbeck.seneca.app.ScriptedUi;
import io.github.steinbeck.seneca.app.TestHooks;
import io.github.steinbeck.seneca.app.dataset.DatasetFrame;
import io.github.steinbeck.seneca.app.edit.RecipeThroughPanelsTest;
import io.github.steinbeck.seneca.app.run.StructureGenerationPanel;
import io.github.steinbeck.seneca.app.structure.Depictions;
import io.github.steinbeck.seneca.app.structure.StructureDisplayPanel;
import io.github.steinbeck.seneca.chem.Layout2D;
import io.github.steinbeck.seneca.config.GuiRecipe;
import io.github.steinbeck.seneca.config.Profile;
import io.github.steinbeck.seneca.golden.GoldenFiles;
import io.github.steinbeck.seneca.golden.JsonValue;
import io.github.steinbeck.seneca.golden.RunGolden;
import io.github.steinbeck.seneca.model.SenecaDataset;

/**
 * Visual review, round 2 (verifier "visual"): geometry audit of the structure drawings over every golden
 * best structure (atoms drawn on top of each other or on a bond they do not belong to make the drawing
 * read as a different constitution), plus run-UI renderings not covered by {@link VisualReviewVerifyTest}
 * (Logbook end visible, best-structure window, smaller main window). Renderings and reports go to
 * {@code <shotsDir>/visual2-*}.
 */
class VisualRound2VerifyTest {

    static final int W = io.github.steinbeck.seneca.app.MainView.DEFAULT_SIZE.width, H = io.github.steinbeck.seneca.app.MainView.DEFAULT_SIZE.height;

    /** Every golden best structure: constitution of the drawing = connection table; misleading geometry reported. */
    @Test
    void depictionGeometryOfAllGoldenBestStructures() throws Exception {
        Path runs = GoldenFiles.golden().resolve("runs");
        List<Path> files;
        try (Stream<Path> s = Files.list(runs)) {
            files = s.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
        Files.createDirectories(GuiTestSupport.shotsDir());
        StringBuilder rep = new StringBuilder();
        int n = 0, constitutionMismatch = 0, coincident = 0, onBond = 0, withCrossings = 0;
        List<String> bad = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Path f : files) {
            JsonValue.JObject r = JsonValue.parse(Files.readString(f).trim()).asObject();
            if (!r.has("bestMDL") || r.isNull("bestMDL")) continue;
            String mdl = r.str("bestMDL");
            String name = f.getFileName().toString().replace(".json", "");
            Depictions.Structure s = Depictions.fromMdl(mdl);
            assertTrue(s.drawable(), name);
            IAtomContainer m = s.molecule();
            IAtomContainer ref;
            try (MDLV2000Reader rd = new MDLV2000Reader(new StringReader(mdl))) {
                ref = rd.read(SilentChemObjectBuilder.getInstance().newAtomContainer());
            }
            SmilesGenerator sg = new SmilesGenerator(SmiFlavor.Unique);
            String sa = sg.create(ref), sb = sg.create(m);
            String fa = MolecularFormulaManipulator.getString(MolecularFormulaManipulator.getMolecularFormula(ref));
            String fb = MolecularFormulaManipulator.getString(MolecularFormulaManipulator.getMolecularFormula(m));
            boolean same = sa.equals(sb) && fa.equals(fb);
            if (!seen.add(sa)) continue;              // same structure in several runs
            n++;
            if (!same) constitutionMismatch++;
            List<String> issues = geometry(m);
            int cross = Layout2D.crossings(m);
            if (cross > 0) withCrossings++;
            boolean hasCoincident = issues.stream().anyMatch(x -> x.startsWith("COINCIDENT"));
            boolean hasOnBond = issues.stream().anyMatch(x -> x.startsWith("ON-BOND"));
            if (hasCoincident) coincident++;
            if (hasOnBond) onBond++;
            rep.append(name).append(": ").append(same ? "constitution OK " : "CONSTITUTION MISMATCH ").append(fb).append(' ').append(sb)
                    .append(" | atoms=").append(m.getAtomCount()).append(" crossings=").append(cross)
                    .append(String.format(" penalty=%.1f", Layout2D.penalty(m)));
            for (String i : issues) rep.append("\n    ").append(i);
            rep.append('\n');
            if (hasCoincident || hasOnBond) {
                bad.add(name);
                ImageIO.write(Depictions.render(s, 900, 600, 1.0), "png", GuiTestSupport.shotsDir().resolve("visual2-depict-" + name + ".png").toFile());
                ImageIO.write(new DepictionGenerator().withSize(900, 600).withFillToFit().withAtomNumbers().depict(m).toImg(), "png",
                        GuiTestSupport.shotsDir().resolve("visual2-depict-" + name + "-numbered.png").toFile());
            }
        }
        String summary = String.format("%d distinct golden best structures: constitution mismatches=%d, with coincident atoms=%d, "
                + "with atoms on a foreign bond=%d, with bond crossings=%d%nmisleading: %s%n", n, constitutionMismatch, coincident, onBond,
                withCrossings, bad);
        Files.writeString(GuiTestSupport.shotsDir().resolve("visual2-depictions.txt"), summary + rep);
        System.out.println(summary + rep);
        assertEquals(0, constitutionMismatch, summary);
    }

    /** Atoms drawn on top of each other (< 0.2 L) or on a bond they do not belong to (< 0.12 L, inside the bond). */
    static List<String> geometry(IAtomContainer m) {
        List<String> out = new ArrayList<>();
        List<Double> lens = new ArrayList<>();
        for (IBond b : m.bonds()) lens.add(b.getBegin().getPoint2d().distance(b.getEnd().getPoint2d()));
        lens.sort(Double::compare);
        double L = lens.get(lens.size() / 2);
        for (int i = 0; i < m.getAtomCount(); i++)
            for (int j = i + 1; j < m.getAtomCount(); j++) {
                IAtom a = m.getAtom(i), b = m.getAtom(j);
                if (m.getBond(a, b) != null) continue;
                double d = a.getPoint2d().distance(b.getPoint2d()) / L;
                if (d < 0.2) out.add(String.format("COINCIDENT %s%d(deg %d) and %s%d(deg %d) at %.2f L", a.getSymbol(), i + 1,
                        m.getConnectedBondsCount(a), b.getSymbol(), j + 1, m.getConnectedBondsCount(b), d));
            }
        for (int i = 0; i < m.getAtomCount(); i++) {
            IAtom q = m.getAtom(i);
            for (IBond b : m.bonds()) {
                if (b.contains(q)) continue;
                Point2d p = q.getPoint2d(), a = b.getBegin().getPoint2d(), c = b.getEnd().getPoint2d();
                double dx = c.x - a.x, dy = c.y - a.y, len2 = dx * dx + dy * dy;
                double t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / len2;
                if (t <= 0.08 || t >= 0.92) continue;
                double fx = a.x + t * dx - p.x, fy = a.y + t * dy - p.y;
                double d = Math.sqrt(fx * fx + fy * fy) / L;
                if (d < 0.12) out.add(String.format("ON-BOND %s%d(deg %d) lies on bond %s%d-%s%d (%.2f L off)", q.getSymbol(), i + 1,
                        m.getConnectedBondsCount(q), b.getBegin().getSymbol(), m.indexOf(b.getBegin()) + 1, b.getEnd().getSymbol(),
                        m.indexOf(b.getEnd()) + 1, d));
            }
        }
        double max = lens.get(lens.size() - 1) / L;
        if (max > 2.5) out.add(String.format("STRETCHED longest bond %.2f L", max));
        return out;
    }

    /**
     * Diagnostic for the fix: for the non-cage structures whose drawing has coincident atoms or atoms on a
     * foreign bond, do plain CDK layouts in other atom orders (40 shuffles) exist without such overlaps?
     */
    @Test
    void overlapFreeLayoutsExist() throws Exception {
        String[] names = {"monochaetin.gui.seed1", "monochaetin.gui.seed2", "narelin.gui.seed1", "narelin.gui.seed2", "narelin.gui.seed3",
                "narelin.paper.seed1", "narelin.paper.seed3", "polycarpol.gui.seed2", "polycarpol.gui.seed3", "triterpen.gui.seed1",
                "triterpen.gui.seed2", "triterpen.gui.seed3", "triterpen.paper.seed2", "triterpen.paper.seed3"};
        StringBuilder rep = new StringBuilder();
        int fixable = 0;
        for (String name : names) {
            JsonValue.JObject r = JsonValue.parse(Files.readString(GoldenFiles.golden().resolve("runs").resolve(name + ".json")).trim()).asObject();
            Depictions.Structure s = Depictions.fromMdl(r.str("bestMDL"));
            java.util.Random rnd = new java.util.Random(7);
            int clean = 0, minCross = Integer.MAX_VALUE;
            for (int k = 0; k < 40; k++) {
                IAtomContainer m = s.molecule().clone();
                List<IAtom> atoms = new ArrayList<>();
                for (IAtom a : m.atoms()) atoms.add(a);
                java.util.Collections.shuffle(atoms, rnd);
                m.setAtoms(atoms.toArray(new IAtom[0]));
                try {
                    new org.openscience.cdk.layout.StructureDiagramGenerator().generateCoordinates(m);
                } catch (Exception e) {
                    continue;
                }
                List<String> g = geometry(m);
                if (g.stream().noneMatch(x -> x.startsWith("COINCIDENT") || x.startsWith("ON-BOND"))) {
                    clean++;
                    minCross = Math.min(minCross, Layout2D.crossings(m));
                }
            }
            if (clean > 0) fixable++;
            rep.append(name).append(": overlap-free CDK layouts ").append(clean).append("/40")
                    .append(clean > 0 ? ", fewest crossings among them " + minCross : "").append('\n');
        }
        rep.append(fixable).append('/').append(names.length).append(" have an overlap-free layout among 40 atom orders\n");
        Files.writeString(GuiTestSupport.shotsDir().resolve("visual2-overlap-free.txt"), rep);
        System.out.println(rep);
    }

    /** Run UI after a pinene run: Logbook scrolled to its end, best-structure window, smaller main window. */
    @Test
    void runUiDetails() throws Exception {
        try (TestApp t = GuiTestSupport.newApp("visual2-run", new TestHooks(1L, RunGolden.ITERATION_CAP))) {
            SenecaDataset sd = VisualReviewVerifyTest.openViaToolbar(t, "pinene");
            DatasetFrame f = onEdt(() -> t.app.frameOf(sd));
            SenecaDataset recipe = GuiRecipe.prepare(GuiTestSupport.sml("pinene"), "pinene", Profile.GUI, t.app.compat).sd;
            RecipeThroughPanelsTest.configure(f, "pinene", Profile.GUI, recipe);
            onEdt(() -> f.showTab("Structure Generation"));
            StructureGenerationPanel p = VisualReviewVerifyTest.runPanel(f);
            onEdt(() -> p.timer().stop());
            click(GuiTestSupport.find(f, ComponentNames.RUN_ADD_SLOT, AbstractButton.class));
            t.app.awaitControl(60_000);
            flushEdt();
            onEdt(() -> p.table().setRowSelectionInterval(0, 0));
            click(GuiTestSupport.find(f, ComponentNames.RUN_START, AbstractButton.class));
            t.app.awaitControl(60_000);
            assertTrue(GuiTestSupport.waitFor(() -> f.runs().slots().get(0).lastResult() != null, 300_000));
            flushEdt();
            onEdt(p::poll);
            flushEdt();
            // a smaller main window (e.g. 1024x640 usable on a small screen)
            shot(t.view, 1024, 640, "visual2-run-1024x640");
            // Logbook: is the end of the text visible after the run?
            onEdt(() -> f.showTab("Logbook"));
            flushEdt();
            shot(t.view, W, H, "visual2-logbook");
            flushEdt();
            String logEnd = onEdt(() -> {
                JTextPane tp = GuiTestSupport.find(f, ComponentNames.LOGBOOK, JTextPane.class);
                JViewport vp = (JViewport) tp.getParent();
                Rectangle view = vp.getViewRect();
                try {
                    Rectangle last = tp.modelToView2D(Math.max(0, tp.getDocument().getLength() - 2)).getBounds();
                    return "viewRect=" + view + " lastCharRect=" + last + " textHeight=" + tp.getHeight()
                            + " lastLineFullyVisible=" + (last.y + last.height <= view.y + view.height);
                } catch (javax.swing.text.BadLocationException e) {
                    return e.toString();
                }
            });
            Files.writeString(GuiTestSupport.shotsDir().resolve("visual2-logbook.txt"), logEnd);
            System.out.println("logbook: " + logEnd);
            // best structure in its own window (double click)
            onEdt(() -> f.showTab("Structure Generation"));
            StructureDisplayPanel big = onEdt(p::showBestInWindow);
            ScriptedUi.Window w = t.ui.lastWindow();
            System.out.println("best-structure window: title=" + w.title + " size=" + w.size);
            shot(big, w.size != null ? w.size.width : 0, w.size != null ? w.size.height : 0, "visual2-best-window");
            // all pinene summary tiles
            click(GuiTestSupport.find(f, ComponentNames.RUN_SUMMARIZE, AbstractButton.class));
            StructureDisplayPanel sum = (StructureDisplayPanel) t.ui.lastWindow().content;
            List<StructureDisplayPanel.Tile> tiles = onEdt(sum::tileList);
            shot(sum.tiles(), 600, ((tiles.size() + 1) / 2) * 200, "visual2-pinene-summary-tiles");
        }
    }
}
