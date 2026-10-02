package io.github.steinbeck.seneca.app;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JOptionPane;

import io.github.steinbeck.seneca.app.ui.Dialogs;
import io.github.steinbeck.seneca.app.ui.FileDialogs;
import io.github.steinbeck.seneca.app.ui.TextClipboard;
import io.github.steinbeck.seneca.app.ui.UiServices;
import io.github.steinbeck.seneca.app.ui.WindowHost;

/**
 * Scripted {@link UiServices} for headless GUI tests: file dialogs and option panes answer from queues
 * filled by the test, windows and panes are recorded (so they can be rendered), the clipboard is a field.
 * Nothing ever blocks. An unscripted file dialog answers "cancel"; an unscripted option pane answers
 * {@link JOptionPane#CLOSED_OPTION}.
 */
public final class ScriptedUi implements FileDialogs, Dialogs, WindowHost, TextClipboard {

    /** A recorded option pane. */
    public record Shown(String title, JOptionPane pane) {
        /** The pane's message as text. */
        public String message() {
            return String.valueOf(pane.getMessage());
        }
    }

    /** A recorded window. */
    public static final class Window implements WindowHost.Handle {
        public String title;
        public final JComponent content;
        public final Dimension size;
        public boolean closed;

        Window(String title, JComponent content, Dimension size) {
            this.title = title;
            this.content = content;
            this.size = size;
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public void setTitle(String t) {
            title = t;
        }
    }

    /** A recorded file dialog request. */
    public record FileRequest(String kind, String title, File directory, String extension) {
    }

    private final Deque<List<File>> fileAnswers = new ArrayDeque<>();
    private final Deque<Object> dialogAnswers = new ArrayDeque<>();
    public final List<FileRequest> fileRequests = new ArrayList<>();
    public final List<Shown> shown = new ArrayList<>();
    public final List<Window> windows = new ArrayList<>();
    public String clipboardText;
    public Transferable clipboardContents;

    public UiServices services() {
        return new UiServices(this, this, this, this);
    }

    /** The next file dialog answers {@code files} (none = cancel). */
    public ScriptedUi answerFiles(File... files) {
        fileAnswers.add(List.of(files));
        return this;
    }

    /** The next option pane answers {@code value} (e.g. {@link JOptionPane#YES_OPTION}). */
    public ScriptedUi answerDialog(Object value) {
        dialogAnswers.add(value);
        return this;
    }

    public Shown lastShown() {
        return shown.isEmpty() ? null : shown.get(shown.size() - 1);
    }

    public Window lastWindow() {
        return windows.isEmpty() ? null : windows.get(windows.size() - 1);
    }

    // FileDialogs

    private List<File> nextFiles(String kind, String title, File dir, String ext) {
        fileRequests.add(new FileRequest(kind, title, dir, ext));
        return fileAnswers.isEmpty() ? List.of() : fileAnswers.poll();
    }

    @Override
    public File open(Component parent, String title, File directory, String extension, String description) {
        List<File> l = nextFiles("open", title, directory, extension);
        return l.isEmpty() ? null : l.get(0);
    }

    @Override
    public List<File> openMultiple(Component parent, String title, File directory, String extension, String description) {
        return nextFiles("openMultiple", title, directory, extension);
    }

    @Override
    public File save(Component parent, String title, File directory, String suggestedName, String extension, String description) {
        List<File> l = nextFiles("save", title, directory, extension);
        return l.isEmpty() ? null : l.get(0);
    }

    // Dialogs

    @Override
    public Object show(Component parent, String title, JOptionPane pane) {
        shown.add(new Shown(title, pane));
        Object v = dialogAnswers.isEmpty() ? Integer.valueOf(JOptionPane.CLOSED_OPTION) : dialogAnswers.poll();
        pane.setValue(v);
        return v;
    }

    // WindowHost

    @Override
    public Handle show(String title, JComponent content, Dimension size) {
        Window w = new Window(title, content, size);
        windows.add(w);
        return w;
    }

    // TextClipboard

    @Override
    public String getText() {
        if (clipboardText != null) return clipboardText;
        try {
            if (clipboardContents != null && clipboardContents.isDataFlavorSupported(DataFlavor.stringFlavor))
                return (String) clipboardContents.getTransferData(DataFlavor.stringFlavor);
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    @Override
    public void setText(String text) {
        clipboardText = text;
        clipboardContents = null;
    }

    @Override
    public void setContents(Transferable contents) {
        clipboardContents = contents;
        clipboardText = null;
    }
}
