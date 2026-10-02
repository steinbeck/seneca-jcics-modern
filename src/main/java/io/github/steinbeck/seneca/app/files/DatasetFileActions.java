package io.github.steinbeck.seneca.app.files;

import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JOptionPane;

import io.github.steinbeck.seneca.app.AppContext;
import io.github.steinbeck.seneca.app.dataset.DatasetFrame;
import io.github.steinbeck.seneca.app.ui.OptionPanes;
import io.github.steinbeck.seneca.io.LucyDatasetImporter;
import io.github.steinbeck.seneca.io.SpecMLReader;
import io.github.steinbeck.seneca.io.SpecMLWriter;
import io.github.steinbeck.seneca.model.SenecaDataset;

/**
 * File operations on datasets (port of {@code Seneca.openFile}, {@code SaveAllAction},
 * {@code ImportLUCYAction} and {@code SenecaDatasetFrame.tryExit}): Open SENECA Dataset (F-21, F-02),
 * Save SENECA Dataset (F-22), Import LUCY dataset (F-24), close with the save prompt (F-48). Called by the
 * main window's actions ({@code AppActions}), the dataset windows and the macOS open-file handler; all on
 * the EDT. Every file goes through {@code UiServices.files()}, every message through
 * {@code UiServices.dialogs()}; the last directories are kept in {@code AppPreferences}.
 *
 * <ul>
 *   <li>Open: chooser at the last SML directory, filter {@code *.sml} "SENECA Dataset"; a file that cannot
 *       be read is reported (2001: stderr only; DV-44, DV-83).</li>
 *   <li>Save: the <b>current</b> dataset, {@code SpecMLWriter.write(sd, DEFAULT)} as UTF-8 (DV-08: the H
 *       counts and shift assignments survive a save and reload); {@code .sml} is appended to a name without
 *       extension and the native dialog asks before overwriting (DV-72); write errors are reported.</li>
 *   <li>Import LUCY: chooser at the last LUCY directory, filter {@code *.col} "Lucy datasets";
 *       {@code LucyDatasetImporter.read(file, DEFAULT, log)}; a parse error (the partial dataset is opened,
 *       as 2001) and an unreadable file are reported.</li>
 *   <li>Close: "Close &lt;name&gt;?" / "You are about to close this dataset.\nDo you want to save your
 *       changes?" Yes / No / Cancel. Yes saves as SML exactly like Save and closes after a successful save
 *       (cancelling the file dialog keeps the window; 2001 wrote unreadable CML, D11, DV-71); No closes;
 *       Cancel keeps the window.</li>
 * </ul>
 */
public final class DatasetFileActions {

    private static final Logger LOG = Logger.getLogger(DatasetFileActions.class.getName());

    public static final String OPEN_TITLE = "Open SENECA Dataset";
    public static final String SAVE_TITLE = "Save SENECA Dataset";
    public static final String LUCY_TITLE = "Import LUCY dataset";

    private final AppContext app;

    public DatasetFileActions(AppContext app) {
        this.app = app;
    }

    /** "File &gt; Open &gt; SENECA Dataset": chooser at the last SML directory, filter {@code *.sml}. */
    public void open(Component parent) {
        File f = app.ui.files().open(parent, OPEN_TITLE, app.preferences.senecaLocation().toFile(), "sml", "SENECA Dataset");
        if (f == null) return;
        if (f.getParentFile() != null) app.preferences.setSenecaLocation(f.getParentFile().toPath());
        openFile(f, parent);
    }

    /**
     * Reads {@code file} as SML and opens it (it becomes the current dataset). On failure: null, after the
     * error was logged and shown (DV-44, DV-83).
     */
    public SenecaDataset openFile(File file, Component parent) {
        try {
            SenecaDataset sd = SpecMLReader.read(file.toPath());
            app.addDataset(sd);
            return sd;
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "There was a fatal problem while trying to import SenecaDataset " + file.getName(), e);
            error(parent, OPEN_TITLE, "There was a fatal problem while trying to import SenecaDataset " + file.getName()
                    + "\n" + reason(e));
            return null;
        }
    }

    /** "File &gt; Save &gt; SENECA Dataset" of the current dataset (F-22). */
    public void save(Component parent) {
        SenecaDataset sd = app.datasets.current();
        if (sd == null) {
            error(parent, SAVE_TITLE, app.resources.text("statusNoDatasetAvailable"));
            return;
        }
        save(sd, parent);
    }

    /**
     * Asks for a file and writes {@code sd} to it as SML (DEFAULT). Returns true if the dataset was written,
     * false if the user cancelled or writing failed (reported).
     */
    public boolean save(SenecaDataset sd, Component parent) {
        String suggested = sd.getName() == null || sd.getName().isEmpty() ? "untitled.sml"
                : sd.getName().toLowerCase().endsWith(".sml") ? sd.getName() : sd.getName() + ".sml";
        File f = app.ui.files().save(parent, SAVE_TITLE, app.preferences.senecaLocation().toFile(), suggested, "sml", "SENECA Dataset");
        if (f == null) return false;
        File chosen = f;
        f = withExtension(f, "sml");
        if (f.getParentFile() != null) app.preferences.setSenecaLocation(f.getParentFile().toPath());
        // ".sml" appended here: the dialog's overwrite prompt only saw the name without it (DV-72)
        if (!f.equals(chosen) && !OptionPanes.mayReplace(app.ui.dialogs(), parent, SAVE_TITLE, f)) return false;
        try {
            Files.writeString(f.toPath(), SpecMLWriter.write(sd, app.compat), StandardCharsets.UTF_8);
            return true;
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Could not save " + f, e);
            error(parent, SAVE_TITLE, "Could not save the dataset to " + f + "\n" + reason(e));
            return false;
        }
    }

    /** DV-72: {@code name} gets {@code .ext} if it has no extension (an existing file of that name is replaced only after asking). */
    static File withExtension(File f, String ext) {
        String n = f.getName();
        int dot = n.lastIndexOf('.');
        if (dot > 0 && dot < n.length() - 1) return f;
        return new File(f.getParentFile(), (dot == n.length() - 1 ? n : n + ".") + ext);
    }

    /** "File &gt; Import &gt; LUCY dataset" (F-24). Returns the imported dataset, or null. */
    public SenecaDataset importLucy(Component parent) {
        File f = app.ui.files().open(parent, LUCY_TITLE, app.preferences.lucyLocation().toFile(), "col", "Lucy datasets");
        if (f == null) return null;
        if (f.getParentFile() != null) app.preferences.setLucyLocation(f.getParentFile().toPath());
        List<String> log = new ArrayList<>();
        SenecaDataset sd;
        try {
            sd = LucyDatasetImporter.read(f.toPath(), app.compat, log::add);
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Error while trying to read LUCY file " + f, e);
            error(parent, LUCY_TITLE, "Error while trying to read LUCY file " + f.getName() + "\n" + reason(e));
            return null;
        }
        app.addDataset(sd);
        if (!log.isEmpty()) {
            LOG.warning(String.join("\n", log));
            app.ui.dialogs().show(parent, LUCY_TITLE, OptionPanes.message(f.getName() + ": " + String.join("\n", log).trim()
                    + "\nThe dataset was read only up to the error.", JOptionPane.WARNING_MESSAGE));
        }
        return sd;
    }

    /**
     * Close button / close box of a dataset window (F-48): "Close &lt;name&gt;?" / "You are about to close
     * this dataset.\nDo you want to save your changes?" Yes saves as SML and closes (DV-71), No closes,
     * Cancel (and a cancelled save) keeps the window.
     */
    public void close(DatasetFrame frame) {
        SenecaDataset sd = frame.dataset();
        int answer = app.ui.dialogs().showOption(frame, app.resources.format("CloseTitle", sd.getName()), closePane(app));
        if (answer == JOptionPane.NO_OPTION) frame.close();
        else if (answer == JOptionPane.YES_OPTION && save(sd, frame)) frame.close();
    }

    /** The close prompt (F-48). */
    public static JOptionPane closePane(AppContext app) {
        return OptionPanes.confirm(app.resources.text("CloseMsg"), JOptionPane.YES_NO_CANCEL_OPTION);
    }

    private static String reason(Throwable e) {
        Throwable c = e.getCause() != null ? e.getCause() : e;
        return c.getMessage() != null ? c.getMessage() : c.toString();
    }

    private void error(Component parent, String title, String message) {
        app.ui.dialogs().show(parent, title, OptionPanes.message(message, JOptionPane.ERROR_MESSAGE));
    }
}
