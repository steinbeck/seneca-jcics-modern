package io.github.steinbeck.seneca.app.log;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.JViewport;
import javax.swing.text.DefaultCaret;

import io.github.steinbeck.seneca.app.ComponentNames;

/**
 * "Logbook" tab of a dataset window (F-47): a white, read-only, scrollable text pane on the shared
 * {@link Logbook} document.
 *
 * <p>The newest text stays in view (DV-92), like a terminal: while the view is at the bottom of the
 * text (initially, and whenever the user scrolls back down), every change of the text's or the
 * window's size (text appended or trimmed, window resized, tab shown) keeps it at the bottom, so that a
 * run finished while another tab was showing ends in view. Once the user scrolls up, the view stays
 * where it is. The caret does not move the view ({@link DefaultCaret#NEVER_UPDATE}). 2001 opened the
 * tab at the top of the text.
 *
 * <p>Design: docs/port/gui-design.md.
 */
public final class LogbookPanel extends JPanel {

    private final JViewport viewport;
    private boolean atBottom = true;
    private int lastViewHeight = -1, lastExtentHeight = -1;

    public LogbookPanel(Logbook logbook) {
        super(new BorderLayout());
        JTextPane pane = new JTextPane(logbook.document());
        pane.setEditable(false);
        pane.setBackground(Color.white);
        pane.setName(ComponentNames.LOGBOOK);
        ((DefaultCaret) pane.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        JScrollPane scroll = new JScrollPane(pane);
        viewport = scroll.getViewport();
        viewport.addChangeListener(e -> viewportChanged());
        add(scroll, BorderLayout.CENTER);
    }

    /** True while the view follows the end of the text. */
    public boolean following() {
        return atBottom;
    }

    private void viewportChanged() {
        Dimension view = viewport.getViewSize(), extent = viewport.getExtentSize();
        int bottom = Math.max(0, view.height - extent.height);
        Point pos = viewport.getViewPosition();
        if (view.height != lastViewHeight || extent.height != lastExtentHeight) {
            lastViewHeight = view.height;
            lastExtentHeight = extent.height;
            if (atBottom && pos.y != bottom) viewport.setViewPosition(new Point(pos.x, bottom));   // fires again
        } else {
            atBottom = pos.y >= bottom - 2;              // the user scrolled
        }
    }
}
