package io.github.steinbeck.seneca.app;

import java.awt.Component;
import java.awt.Window;
import java.util.List;
import java.util.logging.Logger;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/**
 * Look and feel (F-05, F-29). Start: the system look and feel (Aqua on macOS). "Edit &gt; Preferences &gt;
 * Look&amp;Feel" offers the look and feels installed in this JDK (on macOS: Metal, Nimbus, CDE/Motif,
 * Mac OS X) instead of the 2001 list Windows/Motif/Java/Metal, three of which did not exist.
 */
public final class LookAndFeels {

    private static final Logger LOG = Logger.getLogger(LookAndFeels.class.getName());

    private LookAndFeels() {
    }

    public static List<UIManager.LookAndFeelInfo> installed() {
        return List.of(UIManager.getInstalledLookAndFeels());
    }

    /** Aqua on macOS (also headless, where the JDK reports Metal as the system look and feel), else the system one. */
    public static String systemClassName() {
        if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("mac")) {
            for (UIManager.LookAndFeelInfo i : UIManager.getInstalledLookAndFeels())
                if (i.getClassName().equals(AQUA)) return AQUA;
        }
        return UIManager.getSystemLookAndFeelClassName();
    }

    static final String AQUA = "com.apple.laf.AquaLookAndFeel";

    /** Sets the system look and feel ({@link #systemClassName()}); failures are logged (the cross-platform one stays). */
    public static void applySystem() {
        try {
            UIManager.setLookAndFeel(systemClassName());
        } catch (ReflectiveOperationException | UnsupportedLookAndFeelException e) {
            LOG.warning("Error loading L&F: " + e);
        }
    }

    /**
     * Switches to {@code className} and updates every open window and the given extra roots (components
     * not in a window, e.g. in tests). Legacy message on failure: "Could not locate requested look&amp;feel".
     *
     * @return true on success
     */
    public static boolean apply(String className, Component... extraRoots) {
        try {
            UIManager.setLookAndFeel(className);
        } catch (ReflectiveOperationException | UnsupportedLookAndFeelException e) {
            LOG.warning("Could not locate requested look&feel");
            return false;
        }
        for (Window w : Window.getWindows()) SwingUtilities.updateComponentTreeUI(w);
        for (Component c : extraRoots) if (c != null) SwingUtilities.updateComponentTreeUI(c);
        return true;
    }
}
