package io.github.steinbeck.seneca.app.ui;

import java.awt.BorderLayout;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/**
 * Stand-in for a tab or window whose implementation is still pending (GUI skeleton; the owner and the
 * feature-inventory items are listed in docs/port/gui-design.md, section "Work split"). Shows one centred
 * line, e.g. "Atom Properties (F-42) - pending: G1".
 */
public class PlaceholderPanel extends JPanel {

    @SuppressWarnings("this-escape")    // Swing: adding children in the constructor
    public PlaceholderPanel(String text) {
        super(new BorderLayout());
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setEnabled(false);
        add(l, BorderLayout.CENTER);
    }
}
