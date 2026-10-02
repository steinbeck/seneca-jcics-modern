package io.github.steinbeck.seneca.app.ui;

import java.awt.Dimension;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.WindowConstants;

/** {@link WindowHost} for the application: one {@link JFrame} per window, disposed on close. */
public final class FrameWindowHost implements WindowHost {

    @Override
    public Handle show(String title, JComponent content, Dimension size) {
        JFrame f = new JFrame(title);
        f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        if (size != null) content.setPreferredSize(size);
        f.setContentPane(content);
        f.pack();
        f.setLocationByPlatform(true);
        f.setVisible(true);
        return new Handle() {
            @Override
            public void close() {
                f.dispose();
            }

            @Override
            public void setTitle(String t) {
                f.setTitle(t);
            }
        };
    }
}
