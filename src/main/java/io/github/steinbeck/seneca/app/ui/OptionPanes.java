package io.github.steinbeck.seneca.app.ui;

import javax.swing.Icon;
import javax.swing.JOptionPane;

/**
 * Factories of the option panes the application shows (legacy {@code JOptionPane.showConfirmDialog} /
 * {@code showMessageDialog} calls), so that showing ({@link Dialogs}) and rendering in tests use the same
 * component. Texts come from the callers (resource bundle).
 */
public final class OptionPanes {

    private OptionPanes() {
    }

    /** {@code showConfirmDialog(parent, msg, title, optionType)}: question icon, Yes/No(/Cancel). */
    public static JOptionPane confirm(Object message, int optionType) {
        return new JOptionPane(message, JOptionPane.QUESTION_MESSAGE, optionType);
    }

    /** {@code showMessageDialog(parent, msg, title, messageType)}. */
    public static JOptionPane message(Object message, int messageType) {
        return new JOptionPane(message, messageType, JOptionPane.DEFAULT_OPTION);
    }

    /**
     * Asks before replacing {@code file} when the application itself appended the extension after the
     * file dialog had closed (the dialog's own overwrite prompt only saw the name without it, DV-72).
     * Returns true if the file may be written: it does not exist, or the user answered Yes.
     */
    public static boolean mayReplace(Dialogs dialogs, java.awt.Component parent, String title, java.io.File file) {
        if (!file.exists()) return true;
        JOptionPane pane = new JOptionPane("\"" + file.getName() + "\" already exists.\nDo you want to replace it?",
                JOptionPane.WARNING_MESSAGE, JOptionPane.YES_NO_OPTION);
        return dialogs.showOption(parent, title, pane) == JOptionPane.YES_OPTION;
    }

    /** Message pane with a custom icon (About box with the SENECA logo). */
    public static JOptionPane message(Object message, int messageType, Icon icon) {
        return new JOptionPane(message, messageType, JOptionPane.DEFAULT_OPTION, icon);
    }
}
