package io.github.steinbeck.seneca.app.edit;

import static io.github.steinbeck.seneca.app.GuiTestSupport.click;
import static io.github.steinbeck.seneca.app.GuiTestSupport.find;
import static io.github.steinbeck.seneca.app.GuiTestSupport.flushEdt;
import static io.github.steinbeck.seneca.app.GuiTestSupport.menuItem;
import static io.github.steinbeck.seneca.app.GuiTestSupport.onEdt;
import static io.github.steinbeck.seneca.app.GuiTestSupport.shot;
import static io.github.steinbeck.seneca.app.edit.EditSupport.named;
import static io.github.steinbeck.seneca.app.edit.EditSupport.press;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JOptionPane;

import org.junit.jupiter.api.Test;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.app.AppPreferences;
import io.github.steinbeck.seneca.app.ComponentNames;
import io.github.steinbeck.seneca.app.GuiTestSupport;
import io.github.steinbeck.seneca.app.GuiTestSupport.TestApp;
import io.github.steinbeck.seneca.app.ScriptedUi;
import io.github.steinbeck.seneca.app.TestHooks;
import io.github.steinbeck.seneca.app.dataset.DatasetFrame;
import io.github.steinbeck.seneca.app.files.DatasetFileActions;
import io.github.steinbeck.seneca.io.LucyDatasetImporter;
import io.github.steinbeck.seneca.io.SpecMLReader;
import io.github.steinbeck.seneca.io.SpecMLWriter;
import io.github.steinbeck.seneca.model.SenecaDataset;

/** File &gt; Open / Save / Import LUCY and the close prompt (F-21, F-22, F-24, F-48, F-02). */
class DatasetFileActionsTest {

    /** The synthetic LUCY file of the core's importer test (C4H10O). */
    static String col(boolean complete) {
        String s = String.join("\n", "LUCY 1.0", "C4H10O", "5", "3", "C 4", "H 10", "O 1", "4", "x", "y",
                "1 3", "2 2", "3 1", "4 0", "5 1", "0", "C", "C", "C", "C", "O",
                "10.0 0.9 0", "32.0 1.45 1.50", "69.0 3.7 0", "23.0 0 0", "3", "1 3", "1 4", "2 3", "1", "1 2") + "\n";
        return complete ? s : s.substring(0, s.indexOf("3\n1 3"));
    }

    @Test
    void openReportsUnreadableFiles() throws Exception {
        try (TestApp t = GuiTestSupport.newApp("edit-files-open", TestHooks.none())) {
            Path bad = t.dir.resolve("broken.sml");
            Files.writeString(bad, "<senecadataset><formula>C2H6");
            t.ui.answerFiles(bad.toFile());
            click(menuItem(t.menuBar, "File", "Open", "SENECA Dataset"));
            flushEdt();
            assertEquals(0, onEdt(t.app.datasets::size));
            ScriptedUi.Shown s = t.ui.lastShown();
            assertEquals(DatasetFileActions.OPEN_TITLE, s.title());
            assertTrue(s.message().startsWith("There was a fatal problem while trying to import SenecaDataset broken.sml"), s.message());
            assertEquals(JOptionPane.ERROR_MESSAGE, s.pane().getMessageType());
            shot(s.pane(), "edit-dialog-open-failed");
            assertEquals(t.dir, t.app.preferences.senecaLocation(), "directory remembered");

            // command line / Finder "Open With" path
            assertNull(onEdt(() -> t.actions().files().openFile(t.dir.resolve("missing.sml").toFile(), null)));
            assertEquals(2, t.ui.shown.size());
        }
    }

    @Test
    void saveWritesTheCurrentDatasetAsSmlWithAssignments() throws Exception {
        try (TestApp t = GuiTestSupport.newApp("edit-files-save", TestHooks.none())) {
            // nothing open: message
            click(menuItem(t.menuBar, "File", "Save", "SENECA Dataset"));
            assertEquals("No dataset available", t.ui.lastShown().message());

            DatasetFrame pinene = EditSupport.open(t, "pinene");
            press(pinene, ComponentNames.AUTOASSIGN);
            DatasetFrame eura = EditSupport.open(t, "eurabidiol");
            assertSame(eura.dataset(), onEdt(t.app.datasets::current));

            // the current dataset (Datasets > switch) is saved; ".sml" appended (DV-72)
            click(menuItem(t.menuBar, "Datasets", "switch", "PINEN.COL"));
            Path out = Files.createDirectories(t.dir.resolve("saved"));
            t.ui.answerFiles(out.resolve("pinene-assigned").toFile());
            click(find(t.view, ComponentNames.TOOLBAR + ".saveAll", JButton.class));
            ScriptedUi.FileRequest req = t.ui.fileRequests.get(t.ui.fileRequests.size() - 1);
            assertEquals("save", req.kind());
            assertEquals(DatasetFileActions.SAVE_TITLE, req.title());
            assertEquals("sml", req.extension());
            Path file = out.resolve("pinene-assigned.sml");
            assertTrue(Files.exists(file));
            String text = Files.readString(file, StandardCharsets.UTF_8);
            assertEquals(SpecMLWriter.write(pinene.dataset(), LegacyCompat.DEFAULT), text);
            assertEquals(out, t.app.preferences.senecaLocation());

            // reading it back keeps the H counts and shifts (DEFAULT writer, DV-08)
            SenecaDataset back = SpecMLReader.read(file);
            for (int i = 0; i < back.getSetOfNodes().length; i++) {
                assertEquals(pinene.dataset().getSetOfNodes()[i].hCount, back.getSetOfNodes()[i].hCount);
                assertEquals(pinene.dataset().getSetOfNodes()[i].shift13C, back.getSetOfNodes()[i].shift13C);
            }

            // ".sml" appended to the name of an existing file: asked first (the dialog only checked
            // "pinene-assigned"); No (or closing the prompt) writes nothing, Yes replaces it
            Files.writeString(file, "keep me");
            int shownBefore = t.ui.shown.size();
            t.ui.answerFiles(out.resolve("pinene-assigned").toFile()).answerDialog(JOptionPane.NO_OPTION);
            click(menuItem(t.menuBar, "File", "Save", "SENECA Dataset"));
            assertEquals(shownBefore + 1, t.ui.shown.size());
            assertEquals(DatasetFileActions.SAVE_TITLE, t.ui.lastShown().title());
            assertEquals("\"pinene-assigned.sml\" already exists.\nDo you want to replace it?", t.ui.lastShown().message());
            assertEquals("keep me", Files.readString(file));
            t.ui.answerFiles(out.resolve("pinene-assigned").toFile()).answerDialog(JOptionPane.YES_OPTION);
            click(menuItem(t.menuBar, "File", "Save", "SENECA Dataset"));
            assertEquals(SpecMLWriter.write(pinene.dataset(), LegacyCompat.DEFAULT), Files.readString(file, StandardCharsets.UTF_8));
            // the full name of an existing file: no second prompt (the file dialog has asked already)
            shownBefore = t.ui.shown.size();
            t.ui.answerFiles(file.toFile());
            click(menuItem(t.menuBar, "File", "Save", "SENECA Dataset"));
            assertEquals(shownBefore, t.ui.shown.size());

            // a name with an extension is kept; cancel writes nothing
            t.ui.answerFiles(out.resolve("copy.xml").toFile());
            click(menuItem(t.menuBar, "File", "Save", "SENECA Dataset"));
            assertTrue(Files.exists(out.resolve("copy.xml")));
            long before;
            try (var s = Files.list(out)) {
                before = s.count();
            }
            click(menuItem(t.menuBar, "File", "Save", "SENECA Dataset"));
            try (var s = Files.list(out)) {
                assertEquals(before, s.count());
            }

            // a write error is reported
            t.ui.answerFiles(out.resolve("no-such-dir").resolve("x.sml").toFile());
            click(menuItem(t.menuBar, "File", "Save", "SENECA Dataset"));
            assertEquals(DatasetFileActions.SAVE_TITLE, t.ui.lastShown().title());
            assertTrue(t.ui.lastShown().message().startsWith("Could not save the dataset"));
        }
    }

    @Test
    void importLucyDataset() throws Exception {
        try (TestApp t = GuiTestSupport.newApp("edit-files-lucy", TestHooks.none())) {
            Path lucyDir = Files.createDirectories(t.dir.resolve("lucy"));
            Path good = lucyDir.resolve("butanol.col");
            Files.writeString(good, col(true), StandardCharsets.ISO_8859_1);
            t.ui.answerFiles(good.toFile());
            click(menuItem(t.menuBar, "File", "Import", "LUCY dataset"));
            flushEdt();
            ScriptedUi.FileRequest req = t.ui.fileRequests.get(0);
            assertEquals("open", req.kind());
            assertEquals(DatasetFileActions.LUCY_TITLE, req.title());
            assertEquals("col", req.extension());
            assertEquals(t.dir.toFile(), req.directory(), "LUCY directory: home (scratch) by default");
            SenecaDataset sd = onEdt(t.app.datasets::current);
            assertNotNull(sd);
            assertEquals("butanol.col", sd.getName());
            assertEquals("C4H10O", sd.getMolecularFormula());
            SenecaDataset ref = LucyDatasetImporter.read(good, LegacyCompat.DEFAULT);
            assertEquals(ref.getSetOfNodes().length, sd.getSetOfNodes().length);
            assertEquals(7, sd.judges.size());
            assertNotNull(onEdt(() -> t.app.frameOf(sd)));
            assertEquals(0, t.ui.shown.size());
            assertEquals(lucyDir, t.app.preferences.lucyLocation());
            Path props = t.app.paths.preferencesFile();
            assertTrue(Files.readString(props).contains(AppPreferences.LUCY_LOCATION));
            shot(t.view, 1000, 700, "edit-files-lucy-imported");

            // a file with a parse error: the partial dataset opens, the error is reported
            Path broken = lucyDir.resolve("broken.col");
            Files.writeString(broken, col(false), StandardCharsets.ISO_8859_1);
            t.ui.answerFiles(broken.toFile());
            click(menuItem(t.menuBar, "File", "Import", "LUCY dataset"));
            assertEquals(lucyDir.toFile(), t.ui.fileRequests.get(1).directory());
            assertEquals(2, onEdt(t.app.datasets::size));
            assertEquals(DatasetFileActions.LUCY_TITLE, t.ui.lastShown().title());
            assertTrue(t.ui.lastShown().message().startsWith("broken.col: Error while trying to read LUCY file"), t.ui.lastShown().message());

            // a file that cannot be read: nothing opens
            t.ui.answerFiles(lucyDir.resolve("missing.col").toFile());
            click(menuItem(t.menuBar, "File", "Import", "LUCY dataset"));
            assertEquals(2, onEdt(t.app.datasets::size));
            assertTrue(t.ui.lastShown().message().startsWith("Error while trying to read LUCY file missing.col"));
        }
    }

    @Test
    void closeWithYesSavesSmlThenCloses() throws Exception {
        try (TestApp t = GuiTestSupport.newApp("edit-files-close", TestHooks.none())) {
            DatasetFrame f = EditSupport.open(t, "pinene");
            press(f, ComponentNames.AUTOASSIGN);

            // Yes, then the save dialog is cancelled: the window stays
            t.ui.answerDialog(JOptionPane.YES_OPTION);
            press(f, ComponentNames.DATASET_CLOSE);
            assertEquals("Close PINEN.COL?", t.ui.lastShown().title());
            assertFalse(f.isClosedByUser());
            assertEquals(1, onEdt(t.app.datasets::size));
            assertEquals("save", t.ui.fileRequests.get(t.ui.fileRequests.size() - 1).kind());

            // Yes + a file: saved as SML (DV-71), then closed
            File target = t.dir.resolve("closing").toFile();
            t.ui.answerDialog(JOptionPane.YES_OPTION).answerFiles(target);
            press(f, ComponentNames.DATASET_CLOSE);
            Path saved = t.dir.resolve("closing.sml");
            assertTrue(Files.exists(saved));
            assertTrue(f.isClosedByUser());
            assertEquals(0, onEdt(t.app.datasets::size));
            SenecaDataset back = SpecMLReader.read(saved);
            assertEquals(f.dataset().getSetOfNodes()[0].shift13C, back.getSetOfNodes()[0].shift13C);
            assertEquals(List.of(), onEdt(t.app::datasetFrames));
        }
    }
}
