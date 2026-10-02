package io.github.steinbeck.seneca.app.ui;

import java.awt.Component;
import java.io.File;
import java.util.List;

/**
 * Open and save dialogs. On macOS the native dialog ({@code java.awt.FileDialog}), elsewhere
 * {@code JFileChooser} ({@link SwingFileDialogs}); tests script the answers. All methods are called on the
 * EDT and return null / an empty list when the user cancels.
 */
public interface FileDialogs {

    /**
     * One file to open.
     *
     * @param parent      owner (any component of the owning window, may be null)
     * @param title       dialog title
     * @param directory   start directory (may be null)
     * @param extension   file extension without dot that the filter accepts ("sml", "col"), null = all files
     * @param description filter description ("SENECA Dataset", "Lucy datasets"), used by {@code JFileChooser}
     */
    File open(Component parent, String title, File directory, String extension, String description);

    /** Several files to open (Structure Display "Load MDL", "Load LUCY Result": multi-selection). */
    List<File> openMultiple(Component parent, String title, File directory, String extension, String description);

    /** A file to save to (the native dialog asks before overwriting; the caller writes the file). */
    File save(Component parent, String title, File directory, String suggestedName, String extension, String description);
}
