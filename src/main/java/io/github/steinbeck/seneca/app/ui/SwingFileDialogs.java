package io.github.steinbeck.seneca.app.ui;

import java.awt.Component;
import java.awt.Dialog;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.Window;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * {@link FileDialogs} for the application: the native macOS dialog ({@code java.awt.FileDialog}, which
 * also asks before overwriting) on macOS, {@code JFileChooser} with an extension filter elsewhere (the
 * legacy client used {@code JFileChooser} with {@code ExtensionFileFilter} everywhere and an AWT
 * {@code FileDialog} for "Save MDL").
 */
public final class SwingFileDialogs implements FileDialogs {

    static final boolean MAC = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac");

    @Override
    public File open(Component parent, String title, File directory, String extension, String description) {
        List<File> l = show(parent, title, directory, null, extension, description, FileDialog.LOAD, false);
        return l.isEmpty() ? null : l.get(0);
    }

    @Override
    public List<File> openMultiple(Component parent, String title, File directory, String extension, String description) {
        return show(parent, title, directory, null, extension, description, FileDialog.LOAD, true);
    }

    @Override
    public File save(Component parent, String title, File directory, String suggestedName, String extension, String description) {
        List<File> l = show(parent, title, directory, suggestedName, extension, description, FileDialog.SAVE, false);
        return l.isEmpty() ? null : l.get(0);
    }

    private static List<File> show(Component parent, String title, File directory, String name, String extension,
                                   String description, int mode, boolean multiple) {
        if (MAC) {
            Window w = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
            FileDialog fd = w instanceof Dialog d ? new FileDialog(d, title, mode)
                    : new FileDialog(w instanceof Frame f ? f : null, title, mode);
            if (directory != null) fd.setDirectory(directory.getAbsolutePath());
            if (name != null) fd.setFile(name);
            if (extension != null && mode == FileDialog.LOAD)
                fd.setFilenameFilter((dir, n) -> n.toLowerCase(Locale.ROOT).endsWith("." + extension));
            fd.setMultipleMode(multiple);
            fd.setVisible(true);
            File[] files = fd.getFiles();
            return files == null ? List.of() : Arrays.asList(files);
        }
        JFileChooser fc = new JFileChooser(directory);
        fc.setDialogTitle(title);
        if (extension != null) fc.setFileFilter(new FileNameExtensionFilter(description, extension));
        fc.setMultiSelectionEnabled(multiple);
        if (name != null) fc.setSelectedFile(new File(directory, name));
        int r = mode == FileDialog.SAVE ? fc.showSaveDialog(parent) : fc.showOpenDialog(parent);
        if (r != JFileChooser.APPROVE_OPTION) return List.of();
        if (multiple) return new ArrayList<>(Arrays.asList(fc.getSelectedFiles()));
        return List.of(fc.getSelectedFile());
    }
}
