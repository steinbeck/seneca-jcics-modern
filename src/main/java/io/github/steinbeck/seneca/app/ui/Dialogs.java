package io.github.steinbeck.seneca.app.ui;

import java.awt.Component;

import javax.swing.JOptionPane;

/**
 * Shows modal option panes. Every message box and confirmation of the application is a
 * {@link JOptionPane} built by {@link OptionPanes} (so the GUI tests can render it without showing it)
 * and shown through this interface. The application uses {@link SwingDialogs}; tests answer with a script.
 */
public interface Dialogs {

    /**
     * Shows {@code pane} modally and returns {@link JOptionPane#getValue()} after it was closed (an
     * {@code Integer} such as {@link JOptionPane#YES_OPTION} for option panes, null or
     * {@link JOptionPane#UNINITIALIZED_VALUE} if the window was closed).
     */
    Object show(Component parent, String title, JOptionPane pane);

    /** {@link #show} as an int option ({@link JOptionPane#CLOSED_OPTION} if none). */
    default int showOption(Component parent, String title, JOptionPane pane) {
        Object v = show(parent, title, pane);
        return v instanceof Integer i ? i : JOptionPane.CLOSED_OPTION;
    }
}
