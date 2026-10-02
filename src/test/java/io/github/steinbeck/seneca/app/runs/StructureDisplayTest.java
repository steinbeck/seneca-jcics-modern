package io.github.steinbeck.seneca.app.runs;

import static io.github.steinbeck.seneca.app.GuiTestSupport.click;
import static io.github.steinbeck.seneca.app.GuiTestSupport.find;
import static io.github.steinbeck.seneca.app.GuiTestSupport.menuItem;
import static io.github.steinbeck.seneca.app.GuiTestSupport.onEdt;
import static io.github.steinbeck.seneca.app.GuiTestSupport.shot;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.datatransfer.DataFlavor;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.swing.AbstractButton;

import org.junit.jupiter.api.Test;

import io.github.steinbeck.seneca.app.ComponentNames;
import io.github.steinbeck.seneca.app.GoldenRunAssert;
import io.github.steinbeck.seneca.app.GuiTestSupport;
import io.github.steinbeck.seneca.app.GuiTestSupport.TestApp;
import io.github.steinbeck.seneca.app.ScriptedUi;
import io.github.steinbeck.seneca.app.TestHooks;
import io.github.steinbeck.seneca.app.structure.Depictions;
import io.github.steinbeck.seneca.app.structure.StructureDisplayPanel;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.chem.LegacyMdlReader;
import io.github.steinbeck.seneca.chem.LegacyMdlWriter;

/**
 * The "Structure Display" window (feature-inventory F-30, F-85, F-90..F-94, DV-84): CDK drawings, Load
 * MDL, Load LUCY Result, Save MDL in the legacy molfile format, Save SDF, Copy, Close; all file dialogs
 * and the clipboard scripted.
 */
class StructureDisplayTest {

    static final String LUCY_CYCLOHEXANOL = "cyclohexanol\n7 7\n1 C 1 1 1\n2 C 1 1 2\n3 C 1 1 2\n4 C 1 1 2\n5 C 1 1 2\n"
            + "6 C 1 1 2\n7 O 1 1 1\n1 2 1\n2 3 1\n3 4 1\n4 5 1\n5 6 1\n6 1 1\n1 7 1\n";

    static String bestMdl() {
        return GoldenRunAssert.golden("pinene", "gui", 1).record.str("bestMDL");
    }

    /** Fraction of non-white pixels. */
    static double ink(BufferedImage img) {
        long n = 0;
        for (int y = 0; y < img.getHeight(); y++)
            for (int x = 0; x < img.getWidth(); x++) if ((img.getRGB(x, y) & 0xffffff) != 0xffffff) n++;
        return (double) n / (img.getWidth() * (double) img.getHeight());
    }

    /** Bounding box of the non-white pixels: {minX, minY, maxX, maxY}. */
    static int[] box(BufferedImage img) {
        int[] b = {img.getWidth(), img.getHeight(), -1, -1};
        for (int y = 0; y < img.getHeight(); y++)
            for (int x = 0; x < img.getWidth(); x++)
                if ((img.getRGB(x, y) & 0xffffff) != 0xffffff) {
                    b[0] = Math.min(b[0], x);
                    b[1] = Math.min(b[1], y);
                    b[2] = Math.max(b[2], x);
                    b[3] = Math.max(b[3], y);
                }
        return b;
    }

    @Test
    void depictionFillsEightyPercentCentredAndLabelsHeteroatoms() {
        Depictions.Structure pinene = Depictions.fromMdl(bestMdl());
        assertTrue(pinene.drawable());
        BufferedImage img = onEdt(() -> Depictions.render(pinene, 300, 200, 1.0));
        assertEquals(300, img.getWidth());
        int[] b = box(img);
        int w = b[2] - b[0], h = b[3] - b[1];
        // fitted to 80 % in one dimension (within the depiction's margin), centred
        assertTrue(w <= 0.8 * 300 + 2 && h <= 0.8 * 200 + 2, w + "x" + h);
        assertTrue(w >= 0.7 * 300 || h >= 0.7 * 200, w + "x" + h);
        assertEquals(150, (b[0] + b[2]) / 2.0, 12);
        assertEquals(100, (b[1] + b[3]) / 2.0, 12);
        // Retina: twice the pixels, the same drawing
        BufferedImage hi = onEdt(() -> Depictions.render(pinene, 300, 200, 2.0));
        assertEquals(600, hi.getWidth());
        assertEquals(400, hi.getHeight());

        // ethanol: the O is labelled with its hydrogen ("OH"), carbons are not: more ink than the bare bonds
        Depictions.Structure ethanol = Depictions.fromAtoms(io.github.steinbeck.seneca.chem.LegacyMdlReader.read(
                "ethanol\n\n\n  3  2  0  0  0  0  0  0  0  0  1 V2000\n"
                        + "    0.0000    0.0000    0.0000 C   0  0  0  0  0  0  0  0  0  0  0  0\n"
                        + "    0.0000    0.0000    0.0000 C   0  0  0  0  0  0  0  0  0  0  0  0\n"
                        + "    0.0000    0.0000    0.0000 O   0  0  0  0  0  0  0  0  0  0  0  0\n"
                        + "  1  2  1  0  0  0  0\n  2  3  1  0  0  0  0\nM  END\n"));
        assertEquals(Integer.valueOf(1), ethanol.molecule().getAtom(2).getImplicitHydrogenCount());
        assertTrue(ink(onEdt(() -> Depictions.render(ethanol, 300, 200, 1.0))) > 0.002);

        // unreadable input: an empty white area
        Depictions.Structure bad = Depictions.fromMdl("garbage");
        assertFalse(bad.drawable());
        assertEquals(0.0, ink(onEdt(() -> Depictions.render(bad, 300, 200, 1.0))));
    }

    @Test
    void loadSaveCopyAndClose() throws Exception {
        try (TestApp t = GuiTestSupport.newApp("structures", TestHooks.none())) {
            Path dir = t.dir;
            Path mol = dir.resolve("pinene-best.mol");
            Files.writeString(mol, bestMdl(), StandardCharsets.ISO_8859_1);
            Path lucy = dir.resolve("cyclohexanol.lucy");
            Files.writeString(lucy, LUCY_CYCLOHEXANOL, StandardCharsets.ISO_8859_1);

            click(menuItem(t.menuBar, "View", "Current Structures"));
            ScriptedUi.Window w = t.ui.lastWindow();
            assertEquals("Structure Display", w.title);
            StructureDisplayPanel p = (StructureDisplayPanel) w.content;

            // Load MDL (multi-selection): one tile per file, titled with the file name
            t.ui.answerFiles(mol.toFile(), mol.toFile());
            click(find(p, ComponentNames.STRUCTURE_LOAD_MDL, AbstractButton.class));
            assertEquals("openMultiple", t.ui.fileRequests.get(t.ui.fileRequests.size() - 1).kind());
            // Load LUCY Result
            t.ui.answerFiles(lucy.toFile());
            click(find(p, ComponentNames.STRUCTURE_LOAD_LUCY, AbstractButton.class));
            List<StructureDisplayPanel.Tile> tiles = onEdt(p::tileList);
            assertEquals(List.of("pinene-best.mol", "pinene-best.mol", "cyclohexanol.lucy"), tiles.stream().map(StructureDisplayPanel.Tile::title).toList());
            for (StructureDisplayPanel.Tile tile : tiles) assertTrue(tile.structure().drawable(), tile.title());
            assertEquals(7, tiles.get(2).structure().atoms().length);
            shot(p, 620, 700, "runs-structure-display-loaded");

            // Save MDL: "x.mol" -> x0.mol, x1.mol, x2.mol, title line = file name, legacy format, layout coordinates
            t.ui.answerFiles(dir.resolve("x.mol").toFile());
            List<Path> written = onEdt(p::saveMdl);
            assertEquals(List.of(dir.resolve("x0.mol"), dir.resolve("x1.mol"), dir.resolve("x2.mol")), written);
            assertEquals("Save MDL MOL Files", t.ui.fileRequests.get(t.ui.fileRequests.size() - 1).title());
            for (int g = 0; g < 3; g++) {
                String text = Files.readString(dir.resolve("x" + g + ".mol"), StandardCharsets.ISO_8859_1);
                HeavyAtom[] atoms = tiles.get(g).structure().atoms();
                assertEquals(LegacyMdlWriter.write("x" + g + ".mol", atoms), text, "byte format of x" + g);
                assertTrue(text.startsWith("x" + g + ".mol\n\n\n"));
                assertFalse(text.contains("    0.0000    0.0000    0.0000 C"), "coordinates of the drawing, not zeros");
            }
            HeavyAtom[] back = LegacyMdlReader.read(Files.readString(dir.resolve("x0.mol")));
            assertArrayEquals(GoldenRunAssert.ct(bestMdl()), GoldenRunAssert.ct(LegacyMdlWriter.write("t", back)));
            assertEquals("x", StructureDisplayPanel.baseName("x.mol"));
            assertEquals("x", StructureDisplayPanel.baseName("x.molfile"));
            assertEquals("x.sdf", StructureDisplayPanel.baseName("x.sdf"));
            assertEquals("mol", StructureDisplayPanel.baseName("mol"));
            // cancelled dialog: nothing written
            assertEquals(List.of(), onEdt(p::saveMdl));

            // Save SDF: every tile, title line = tile title, "$$$$" after each
            t.ui.answerFiles(dir.resolve("all").toFile());
            Path sdf = onEdt(p::saveSdf);
            assertEquals(dir.resolve("all.sdf"), sdf);
            String sd = Files.readString(sdf);
            assertEquals(3, sd.split("\\$\\$\\$\\$\n", -1).length - 1);
            assertTrue(sd.startsWith("pinene-best.mol\n\n\n"));
            assertTrue(sd.contains("M  END\n$$$$\ncyclohexanol.lucy\n"));

            // Copy: nothing selected -> disabled; select a tile -> MDL text and picture on the clipboard
            AbstractButton copy = find(p, ComponentNames.STRUCTURE_COPY, AbstractButton.class);
            assertFalse(onEdt(copy::isEnabled));
            onEdt(() -> p.select(tiles.get(2)));
            assertTrue(onEdt(copy::isEnabled));
            assertTrue(onEdt(() -> tiles.get(2).isSelected()));
            click(copy);
            assertNotNull(t.ui.clipboardContents);
            String text = (String) t.ui.clipboardContents.getTransferData(DataFlavor.stringFlavor);
            assertEquals(LegacyMdlWriter.write("cyclohexanol.lucy", tiles.get(2).structure().atoms()), text);
            BufferedImage img = (BufferedImage) t.ui.clipboardContents.getTransferData(DataFlavor.imageFlavor);
            assertTrue(ink(img) > 0.001);
            // the shortcut does the same
            t.ui.setText(null);
            onEdt(() -> p.getActionMap().get("copy").actionPerformed(null));
            assertNotNull(t.ui.clipboardContents);
            shot(p, 600, 500, "runs-structure-display-selected");

            // Close
            assertFalse(w.closed);
            click(find(p, ComponentNames.STRUCTURE_CLOSE, AbstractButton.class));
            assertTrue(w.closed);

            // an unreadable MDL file still gets its tile (title, empty area)
            click(menuItem(t.menuBar, "View", "Current Structures"));
            StructureDisplayPanel p2 = (StructureDisplayPanel) t.ui.lastWindow().content;
            Path bad = dir.resolve("bad.mol");
            Files.writeString(bad, "not a molfile");
            t.ui.answerFiles(bad.toFile());
            click(find(p2, ComponentNames.STRUCTURE_LOAD_MDL, AbstractButton.class));
            assertEquals(1, onEdt(p2::tileList).size());
            assertFalse(onEdt(() -> p2.tileList().get(0).structure().drawable()));
            assertNull(t.ui.lastShown());
        }
    }

    /**
     * A single tile (the best structure opened by a double click) fills the window, so its drawing is
     * larger than the 300x200 tile; with several tiles they keep 300x200; the tile panel always fills
     * the viewport (no white band below the tiles).
     */
    @Test
    void singleTileFillsTheWindowAndThePanelFillsTheViewport() throws Exception {
        try (TestApp t = GuiTestSupport.newApp("structures-fill", TestHooks.none())) {
            StructureDisplayPanel p = onEdt(() -> StructureDisplayPanel.open(t.app, "Structure Display"));
            StructureDisplayPanel.Tile only = onEdt(() -> p.addStructure(Depictions.fromMdl(bestMdl()), "Score 2400", null));
            assertTrue(Files.exists(shot(p, 640, 500, "runs-structure-display-single")));
            java.awt.Dimension vp = onEdt(() -> p.tiles().getParent().getSize());
            assertEquals(vp, onEdt(() -> only.getSize()), "the one tile fills the viewport");
            assertEquals(vp, onEdt(() -> p.tiles().getSize()), "the tile panel fills the viewport");
            assertTrue(vp.width > 2 * StructureDisplayPanel.TILE.width - 20 && vp.height > StructureDisplayPanel.TILE.height + 100, vp.toString());

            StructureDisplayPanel.Tile second = onEdt(() -> p.addStructure(Depictions.fromMdl(bestMdl()), "Score 2400", null));
            assertNotNull(second);
            shot(p, 640, 500, "runs-structure-display-two");
            assertEquals(StructureDisplayPanel.TILE, onEdt(() -> only.getSize()), "several tiles: 300x200 each");
            assertEquals(Integer.valueOf(vp.height), onEdt(() -> Integer.valueOf(p.tiles().getHeight())), "one row of tiles: the panel still fills the viewport");
        }
    }
}
