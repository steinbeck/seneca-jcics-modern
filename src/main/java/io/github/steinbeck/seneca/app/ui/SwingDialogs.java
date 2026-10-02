package io.github.steinbeck.seneca.app.ui;

import java.awt.Component;

import javax.swing.JDialog;
import javax.swing.JOptionPane;

/** {@link Dialogs} for the application: {@code pane.createDialog(parent, title)}, modal. */
public final class SwingDialogs implements Dialogs {

    @Override
    public Object show(Component parent, String title, JOptionPane pane) {
        JDialog d = pane.createDialog(parent, title);
        d.setVisible(true);
        d.dispose();
        return pane.getValue();
    }
}
