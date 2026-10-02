package io.github.steinbeck.seneca.app;

import java.awt.Dimension;
import java.awt.GridLayout;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.BevelBorder;

/**
 * Status bar of the main window (F-12): three lowered-bevel, centred labels in a 640x30 panel; (1) the
 * current dataset's name or "No dataset available" (start) / "No dataset loaded" (after the last one was
 * closed), (2) its molecular formula, (3) unused.
 */
public final class StatusBar extends JPanel {

    private final JLabel[] labels = new JLabel[3];

    public StatusBar() {
        super(new GridLayout(1, 3));
        setName(ComponentNames.STATUS_BAR);
        setPreferredSize(new Dimension(640, 30));
        for (int f = 0; f < 3; f++) {
            JLabel l = new JLabel();
            l.setPreferredSize(new Dimension(100, 100));
            l.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
            l.setHorizontalAlignment(SwingConstants.CENTER);
            labels[f] = l;
            add(l);
        }
    }

    /** Legacy {@code setStatus(text, i)}, i = 1..3. */
    public void setStatus(String text, int i) {
        labels[i - 1].setText(text);
    }

    public String status(int i) {
        return labels[i - 1].getText();
    }
}
