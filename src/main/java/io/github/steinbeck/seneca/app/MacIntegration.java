package io.github.steinbeck.seneca.app;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.awt.Taskbar;
import java.io.File;
import java.util.logging.Logger;

import javax.swing.JMenuBar;
import javax.swing.SwingUtilities;

/**
 * macOS integration through {@link java.awt.Desktop} (no Apple-specific API): the application menu's
 * "About Seneca", "Settings..." (the Look&amp;Feel choice, {@link AppActions#showPreferences}) and
 * "Quit Seneca" (with the legacy exit confirmation), files opened from the Finder
 * ("Open With", double click on an associated {@code .sml} file, F-02), the menu bar shown when no window
 * is focused, and the Dock icon (the 2001 logo; the packaged {@code Seneca.app} keeps its bundle icon);
 * while a structure generation run is going on, App Nap is suspended ({@link MacActivity}, driven by
 * {@link AppContext#runActivity}). Each part is installed only where the platform supports it; nothing
 * happens headless. The system properties that must be set before AWT starts are set by
 * {@link SenecaApp#main}.
 */
public final class MacIntegration {

    private static final Logger LOG = Logger.getLogger(MacIntegration.class.getName());

    private MacIntegration() {
    }

    /**
     * @param defaultMenuBar a second menu bar instance (a menu bar can belong to one frame only) for
     *                       {@link Desktop#setDefaultMenuBar}, or null
     */
    public static void install(AppContext app, AppActions actions, JMenuBar defaultMenuBar) {
        if (GraphicsEnvironment.isHeadless()) return;
        // -Dseneca.allowAppNap=true: diagnostic only (launch test's negative control)
        if (MacActivity.supported() && !Boolean.getBoolean("seneca.allowAppNap")) app.runActivity.setSink(MacActivity::set);
        if (!Desktop.isDesktopSupported()) return;
        Desktop d = Desktop.getDesktop();
        if (d.isSupported(Desktop.Action.APP_ABOUT)) d.setAboutHandler(e -> actions.showAbout());
        if (d.isSupported(Desktop.Action.APP_PREFERENCES)) d.setPreferencesHandler(e -> actions.showPreferences());
        if (d.isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
            d.setQuitHandler((e, response) -> {
                if (actions.confirmExit()) {
                    app.shutdown();
                    response.performQuit();
                } else {
                    response.cancelQuit();
                }
            });
        }
        if (d.isSupported(Desktop.Action.APP_OPEN_FILE)) {
            d.setOpenFileHandler(e -> SwingUtilities.invokeLater(() -> {
                for (File f : e.getFiles()) actions.files().openFile(f, app.mainView());
            }));
        }
        if (defaultMenuBar != null && d.isSupported(Desktop.Action.APP_MENU_BAR)) d.setDefaultMenuBar(defaultMenuBar);
        // the packaged application (jpackage sets jpackage.app-path) keeps its bundle icon in the Dock
        if (System.getProperty("jpackage.app-path") != null) return;
        try {
            if (Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE)
                    && app.resources.logo() != null)
                Taskbar.getTaskbar().setIconImage(app.resources.logo().getImage());
        } catch (UnsupportedOperationException | SecurityException ex) {
            LOG.fine("no Dock icon: " + ex);
        }
    }
}
