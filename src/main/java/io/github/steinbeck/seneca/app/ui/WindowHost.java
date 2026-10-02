package io.github.steinbeck.seneca.app.ui;

import java.awt.Dimension;

import javax.swing.JComponent;

/**
 * Opens top-level windows. The legacy client opened two kinds of separate {@code JFrame}s: the
 * "Structure Display" ({@code StrucDispFrame}, F-30/F-67/F-90) and the "Judge Configuration Report"
 * (F-81). In the port the window <em>content</em> is a plain {@link JComponent} built by the feature
 * code; this host wraps it in a frame ({@link FrameWindowHost}) or, in tests, just records it.
 */
public interface WindowHost {

    /**
     * Shows {@code content} in a new top-level window.
     *
     * @param title window title ("Structure Display", "Judge Configuration Report")
     * @param size  initial content size, or null for the preferred size
     * @return a handle to close the window
     */
    Handle show(String title, JComponent content, Dimension size);

    /** An open window. */
    interface Handle {
        /** Closes (disposes) the window; the legacy "Close" buttons call this. */
        void close();

        /** Changes the title. */
        void setTitle(String title);
    }
}
