package io.github.steinbeck.seneca.app;

import static io.github.steinbeck.seneca.app.GuiTestSupport.click;
import static io.github.steinbeck.seneca.app.GuiTestSupport.find;
import static io.github.steinbeck.seneca.app.GuiTestSupport.flushEdt;
import static io.github.steinbeck.seneca.app.GuiTestSupport.labels;
import static io.github.steinbeck.seneca.app.GuiTestSupport.menuItem;
import static io.github.steinbeck.seneca.app.GuiTestSupport.onEdt;
import static io.github.steinbeck.seneca.app.GuiTestSupport.shot;
import static io.github.steinbeck.seneca.app.GuiTestSupport.shotMenu;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;
import javax.swing.UIManager;

import org.junit.jupiter.api.Test;

import io.github.steinbeck.seneca.app.GuiTestSupport.TestApp;
import io.github.steinbeck.seneca.app.dataset.DatasetFrame;
import io.github.steinbeck.seneca.app.files.DatasetFileActions;
import io.github.steinbeck.seneca.app.structure.StructureDisplayPanel;
import io.github.steinbeck.seneca.model.SenecaDataset;

/**
 * Opens every window and dialog of the application headless, drives the main window's actions with
 * {@code doClick()} and renders everything to {@code <build>/gui-shots/*.png} for review
 * (docs/port/gui-design.md, "Testing without a human"). Checks the menu structure, toolbar, status bar
 * and the dataset window's tabs against feature-inventory sections 2 and 3.
 */
class GuiSmokeTest {

    @Test
    void mainWindowMenusToolbarAndStatusBar() throws Exception {
        try (TestApp t = GuiTestSupport.newApp("smoke-main", TestHooks.none())) {
            // menu bar (feature-inventory 2.1 without the DROP items)
            List<String> menus = onEdt(() -> {
                List<String> l = new ArrayList<>();
                for (int i = 0; i < t.menuBar.getMenuCount(); i++) l.add(t.menuBar.getMenu(i).getText());
                return l;
            });
            assertEquals(List.of("File", "Edit", "View", "Datasets", "Help"), menus);
            assertEquals(List.of("New Dataset", "Open", "Save", "Import", "---", "Exit"), labels(t.menuBar.getMenu(0)));
            assertEquals(List.of("SENECA Dataset", "Example Dataset"), labels((JMenu) menuItem(t.menuBar, "File", "Open")));
            // DV-87: the ten example datasets (surefire: seneca.examples = legacy/original/sml)
            JMenu examples = (JMenu) menuItem(t.menuBar, "File", "Open", "Example Dataset");
            assertEquals(List.of("bn-ye1", "eupatorio", "eurabidiol", "Hyperaspine", "monochaetin", "narelin", "pinene",
                    "polycarpol", "preconen", "triterpen"), labels(examples));
            assertTrue(onEdt(examples::isEnabled));
            assertEquals(GuiTestSupport.sml("pinene").getParent().toAbsolutePath().normalize(),
                    onEdt(() -> t.app.preferences.senecaLocation()), "Open dialog starts at the examples");
            assertEquals(List.of("SENECA Dataset"), labels((JMenu) menuItem(t.menuBar, "File", "Save")));
            assertEquals(List.of("LUCY dataset"), labels((JMenu) menuItem(t.menuBar, "File", "Import")));
            JMenu lnf = (JMenu) menuItem(t.menuBar, "Edit", "Preferences", "Look&Feel");
            assertNotNull(lnf);
            List<String> installed = new ArrayList<>();
            for (UIManager.LookAndFeelInfo i : UIManager.getInstalledLookAndFeels()) installed.add(i.getName());
            assertEquals(installed, labels(lnf));
            assertEquals(List.of("Current Structures"), labels(t.menuBar.getMenu(2)));
            assertEquals(List.of("switch"), labels(t.menuBar.getMenu(3)));
            assertEquals(List.of("About Seneca"), labels(t.menuBar.getMenu(4)));

            // toolbar: new, openAll, saveAll; icons only
            List<String> tools = onEdt(() -> {
                List<String> l = new ArrayList<>();
                for (var c : t.view.toolbar().getComponents())
                    if (c instanceof JButton b) l.add(b.getName() + "|" + b.getToolTipText() + "|" + (b.getIcon() != null) + "|" + (b.getText() == null ? "" : b.getText()));
                return l;
            });
            assertEquals(List.of("main.toolbar.new|Initialize a new dataset|true|", "main.toolbar.openAll|Open a file|true|",
                    "main.toolbar.saveAll|Save to a file|true|"), tools);

            // status bar at start
            assertEquals("No dataset available", onEdt(() -> t.view.statusBar().status(1)));

            shot(t.view, 800, 600, "main-empty");
            shot(t.menuBar, 800, 0, "menubar");
            for (int i = 0; i < t.menuBar.getMenuCount(); i++) shotMenu(t.menuBar.getMenu(i), "menu-" + t.menuBar.getMenu(i).getText());
            shotMenu(lnf, "menu-Edit-LookAndFeel");
            shotMenu(examples, "menu-File-Open-Example");

            // File > Open > Example Dataset > eurabidiol opens it without a file dialog
            click(menuItem(t.menuBar, "File", "Open", "Example Dataset", "eurabidiol"));
            flushEdt();
            assertEquals(0, t.ui.fileRequests.size());
            SenecaDataset sd = onEdt(t.app.datasets::current);
            assertNotNull(sd);
            assertEquals("C15H28O2", sd.getMolecularFormula());
        }
    }

    @Test
    void datasetWindowsTabsAndDatasetsMenu() throws Exception {
        try (TestApp t = GuiTestSupport.newApp("smoke-dataset", TestHooks.none())) {
            // File > New Dataset (toolbar "new")
            click(find(t.view, "main.toolbar.new", JButton.class));
            SenecaDataset untitled = onEdt(t.app.datasets::current);
            assertTrue(untitled.getName().startsWith("untitled"));
            assertEquals(7, untitled.judges.size());
            assertEquals(1, untitled.annealingSchedules.size());

            // File > Open > SENECA Dataset with a scripted file dialog
            t.ui.answerFiles(GuiTestSupport.sml("pinene").toFile());
            click(menuItem(t.menuBar, "File", "Open", "SENECA Dataset"));
            flushEdt();
            assertEquals("open", t.ui.fileRequests.get(0).kind());
            assertEquals("sml", t.ui.fileRequests.get(0).extension());
            SenecaDataset pinene = onEdt(t.app.datasets::current);
            assertEquals("PINEN.COL", pinene.getName());
            assertEquals("PINEN.COL", onEdt(() -> t.view.statusBar().status(1)));
            assertEquals("C10H16", onEdt(() -> t.view.statusBar().status(2)));
            assertEquals(pinene.getSetOfNodes().length, 10);

            // two dataset windows, the newest on top, both maximized
            List<DatasetFrame> frames = onEdt(t.view::datasetFrames);
            assertEquals(2, frames.size());
            DatasetFrame f = onEdt(() -> t.app.frameOf(pinene));
            assertSame(f, frames.get(0), "the newest window is on top");
            assertEquals("PINEN.COL", onEdt(f::getTitle));
            assertTrue(onEdt(f::isMaximum));
            assertTrue(onEdt(f::isClosable) && onEdt(f::isIconifiable) && onEdt(f::isMaximizable));   // isResizable() is false while maximized

            // tabs in legacy order (Serverlist dropped)
            JTabbedPane tabs = f.tabs();
            List<String> titles = onEdt(() -> {
                List<String> l = new ArrayList<>();
                for (int i = 0; i < tabs.getTabCount(); i++) l.add(tabs.getTitleAt(i));
                return l;
            });
            assertEquals(List.of("General Settings", "NMR Spectra", "Atom Properties", "Annealing Schedule", "Judges",
                    "Structure Generation", "Logbook"), titles);
            for (int i = 0; i < titles.size(); i++) {
                int k = i;
                onEdt(() -> tabs.setSelectedIndex(k));
                shot(t.view, 1000, 700, "dataset-" + (i + 1) + "-" + titles.get(i).replace(' ', '_'));
            }
            JTabbedPane spectra = find(f, ComponentNames.SPECTRA_TABS, JTabbedPane.class);
            assertEquals(6, onEdt(spectra::getTabCount));
            JTabbedPane judges = find(f, ComponentNames.JUDGE_TABS, JTabbedPane.class);
            assertEquals(7, onEdt(judges::getTabCount));
            onEdt(() -> tabs.setSelectedIndex(1));
            for (int i = 0; i < 6; i++) {
                int k = i;
                onEdt(() -> spectra.setSelectedIndex(k));
                shot(t.view, 1000, 700, "dataset-spectra-" + (i + 1));
            }
            onEdt(() -> tabs.setSelectedIndex(4));
            for (int i = 0; i < 7; i++) {
                int k = i;
                onEdt(() -> judges.setSelectedIndex(k));
                shot(t.view, 1000, 700, "dataset-judges-" + (i + 1));
            }

            // Datasets > switch: both names; switching makes the dataset current and raises its window
            assertEquals(List.of(untitled.getName(), "PINEN.COL"), labels((JMenu) menuItem(t.menuBar, "Datasets", "switch")));
            click(menuItem(t.menuBar, "Datasets", "switch", untitled.getName()));
            assertSame(untitled, onEdt(t.app.datasets::current));
            assertEquals(untitled.getName(), onEdt(() -> t.view.statusBar().status(1)));
            assertSame(untitled, onEdt(() -> t.view.datasetFrames().get(0).dataset()));

            // a name change (General Settings) retitles the window and the menu
            onEdt(() -> untitled.setName("renamed"));
            flushEdt();
            assertEquals("renamed", onEdt(() -> t.app.frameOf(untitled).getTitle()));
            assertEquals(List.of("renamed", "PINEN.COL"), labels((JMenu) menuItem(t.menuBar, "Datasets", "switch")));

            // Close: Cancel keeps the window, No closes without saving (F-48)
            DatasetFrame pf = onEdt(() -> t.app.frameOf(pinene));
            t.ui.answerDialog(JOptionPane.CANCEL_OPTION);
            click(find(pf, ComponentNames.DATASET_CLOSE, JButton.class));
            assertEquals("Close PINEN.COL?", t.ui.lastShown().title());
            assertEquals("You are about to close this dataset.\nDo you want to save your changes?", t.ui.lastShown().message());
            shot(t.ui.lastShown().pane(), "dialog-close-dataset");
            assertEquals(2, onEdt(t.app.datasets::size));
            t.ui.answerDialog(JOptionPane.NO_OPTION);
            click(find(pf, ComponentNames.DATASET_CLOSE, JButton.class));
            assertEquals(1, onEdt(t.app.datasets::size));
            assertTrue(pf.isClosedByUser());
            assertEquals(1, onEdt(t.view::datasetFrames).size());
            assertNull(onEdt(() -> t.app.frameOf(pinene)));

            t.ui.answerDialog(JOptionPane.NO_OPTION);
            onEdt(() -> t.app.frameOf(untitled).requestClose());
            assertEquals(0, onEdt(t.app.datasets::size));
            assertEquals("No dataset loaded", onEdt(() -> t.view.statusBar().status(1)));
            assertEquals(List.of(), labels((JMenu) menuItem(t.menuBar, "Datasets", "switch")));
        }
    }

    @Test
    void dialogsAndSeparateWindows() throws Exception {
        try (TestApp t = GuiTestSupport.newApp("smoke-dialogs", TestHooks.none())) {
            // Help > About Seneca
            click(menuItem(t.menuBar, "Help", "About Seneca"));
            assertEquals("About Seneca", t.ui.lastShown().title());
            assertTrue(t.ui.lastShown().message().startsWith("SENECA 0.8b\n(c) 1998, 1999, 2000 by Dr. Christoph Steinbeck\n"
                    + "Max-Planck-Institute of Chemical Ecology, Jena\n"), t.ui.lastShown().message());
            shot(t.ui.lastShown().pane(), "dialog-about");

            // File > Exit: No keeps running, Yes exits (exit hook instead of System.exit)
            t.ui.answerDialog(JOptionPane.NO_OPTION);
            click(menuItem(t.menuBar, "File", "Exit"));
            assertEquals("Exit Seneca", t.ui.lastShown().title());
            assertEquals("Do you really want to exit Seneca?\n", t.ui.lastShown().message());
            assertEquals(0, t.exits);
            shot(t.ui.lastShown().pane(), "dialog-exit");

            // View > Current Structures: an empty Structure Display window
            click(menuItem(t.menuBar, "View", "Current Structures"));
            ScriptedUi.Window w = t.ui.lastWindow();
            assertEquals("Structure Display", w.title);
            assertTrue(w.content instanceof StructureDisplayPanel);
            shot(w.content, "window-structure-display-empty");
            click(find(w.content, ComponentNames.STRUCTURE_CLOSE, JButton.class));
            assertTrue(w.closed);

            // Look&Feel: switch to Nimbus and back, the main view still renders
            JMenuItem nimbus = menuItem(t.menuBar, "Edit", "Preferences", "Look&Feel", "Nimbus");
            assertNotNull(nimbus);
            click(nimbus);
            assertEquals("Nimbus", UIManager.getLookAndFeel().getName());
            shot(t.view, 800, 600, "main-empty-nimbus");
            onEdt(() -> {
                GuiTestSupport.applyTestLookAndFeel();
                LookAndFeels.apply(javax.swing.UIManager.getLookAndFeel().getClass().getName(), t.view, t.menuBar);
            });

            t.ui.answerDialog(JOptionPane.YES_OPTION);
            click(menuItem(t.menuBar, "File", "Exit"));
            assertEquals(1, t.exits);
        }
    }

    @Test
    void shotsAreWrittenAndNotBlank() throws Exception {
        try (TestApp t = GuiTestSupport.newApp("smoke-shots", TestHooks.none())) {
            Path p = shot(t.view, 800, 600, "main-check");
            BufferedImage img = ImageIO.read(p.toFile());
            assertEquals(800, img.getWidth());
            assertEquals(600, img.getHeight());
            int first = img.getRGB(0, 0);
            boolean varied = false;
            for (int y = 0; y < img.getHeight() && !varied; y += 7)
                for (int x = 0; x < img.getWidth() && !varied; x += 7) varied = img.getRGB(x, y) != first;
            assertTrue(varied, "rendering is a single colour");
            assertTrue(Files.size(p) > 1000);
            // nothing outside the scratch directory: the log files were created there
            assertTrue(Files.exists(t.dir.resolve("seneca.log")));
            assertTrue(Files.exists(t.dir.resolve("seneca.results.sdf")));
            assertTrue(Files.exists(t.dir.resolve("seneca.annealing.log")));
            assertFalse(DatasetFileActions.closePane(t.app).getMessage().toString().isEmpty());
        }
    }
}
