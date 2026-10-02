package io.github.steinbeck.seneca.app;

import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Date;
import java.util.Locale;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;
import javax.swing.UIManager;

import io.github.steinbeck.seneca.app.files.DatasetFileActions;
import io.github.steinbeck.seneca.app.structure.StructureDisplayPanel;
import io.github.steinbeck.seneca.app.ui.OptionPanes;
import io.github.steinbeck.seneca.model.SenecaDataset;

/**
 * The actions of the main window's menu bar and toolbar (F-11, F-20..F-34). Menu and toolbar share the
 * same {@link Action} objects (legacy: one {@code Action} per command, registered with both). The action
 * command keys are the legacy command names ("new", "openAll", "saveAll", "LUCYimp", "exit", "curSol",
 * "about"). File actions delegate to {@link DatasetFileActions} (G1), "Current Structures" opens a
 * {@link StructureDisplayPanel} (G2).
 */
public final class AppActions {

    private final AppContext app;
    private final DatasetFileActions files;
    private Component parent;

    /** File &gt; New Dataset; toolbar "new" (F-20, fix D10). */
    public final Action newDataset;
    /** File &gt; Open &gt; SENECA Dataset; toolbar "openAll" (F-21). */
    public final Action openDataset;
    /** File &gt; Save &gt; SENECA Dataset; toolbar "saveAll" (F-22). */
    public final Action saveDataset;
    /** File &gt; Import &gt; LUCY dataset (F-24). */
    public final Action importLucy;
    /** File &gt; Exit; macOS Quit; main window close box (F-23). */
    public final Action exit;
    /** View &gt; Current Structures (F-30). */
    public final Action currentStructures;
    /** Help &gt; About Seneca; macOS About (F-34). */
    public final Action about;

    public AppActions(AppContext app) {
        this.app = app;
        this.files = new DatasetFileActions(app);
        Resources r = app.resources;
        newDataset = action("new", r.text("newLabel"), r.icon("newImage"), r.text("newTooltip"), KeyEvent.VK_N, e -> newDataset());
        openDataset = action("openAll", r.text("openAllLabel"), r.icon("openAllImage"), r.text("openTooltip"), KeyEvent.VK_O,
                e -> files.open(parent));
        saveDataset = action("saveAll", r.text("saveAllLabel"), r.icon("saveAllImage"), r.text("saveTooltip"), KeyEvent.VK_S,
                e -> files.save(parent));
        importLucy = action("LUCYimp", r.text("LUCYimpLabel"), null, null, 0, e -> files.importLucy(parent));
        exit = action("exit", r.text("exitLabel"), null, null, 0, e -> {
            if (confirmExit()) app.exit();
        });
        currentStructures = action("curSol", r.text("curSolLabel"), r.icon("curSolImage"), r.text("curSolTooltip"), 0,
                e -> StructureDisplayPanel.open(app, "Structure Display"));
        about = action("about", r.text("aboutLabel"), null, null, 0, e -> showAbout());
    }

    /** The component dialogs are attached to (the main window's content). */
    void setParent(Component parent) {
        this.parent = parent;
    }

    public DatasetFileActions files() {
        return files;
    }

    /** "New Dataset": {@code "untitled" + new Date().hashCode()}, judges and a default schedule (D10 fix). */
    public SenecaDataset newDataset() {
        SenecaDataset sd = SenecaDataset.newDataset("untitled" + new Date().hashCode());
        app.addDataset(sd);
        return sd;
    }

    /** Exit confirmation (F-23): "Exit Seneca" / "Do you really want to exit Seneca?", Yes/No. */
    public boolean confirmExit() {
        return app.ui.dialogs().showOption(parent, app.resources.text("ExitTitle"), exitPane(app.resources)) == JOptionPane.YES_OPTION;
    }

    public static JOptionPane exitPane(Resources r) {
        return OptionPanes.confirm(r.lines("Exit"), JOptionPane.YES_NO_OPTION);
    }

    /** About box (F-34). */
    public void showAbout() {
        app.ui.dialogs().show(parent, app.resources.text("AboutTitle"), aboutPane(app.resources));
    }

    public static JOptionPane aboutPane(Resources r) {
        return OptionPanes.message(r.lines("About", Resources.version()), JOptionPane.INFORMATION_MESSAGE, r.logo());
    }

    /** Edit &gt; Preferences &gt; Look&amp;Feel &gt; {@code info.getName()} (F-29). */
    public Action lookAndFeel(UIManager.LookAndFeelInfo info) {
        return action(info.getClassName(), info.getName(), null, null, 0,
                e -> LookAndFeels.apply(info.getClassName(), app.mainView()));
    }

    /**
     * macOS application menu "Settings..." (addition, DV-91): the choice of Edit &gt; Preferences &gt;
     * Look&amp;Feel as a dialog ("Preferences", a list of the installed look and feels, OK/Cancel); OK
     * applies the selected one. Returns the class name applied, or null.
     */
    public String showPreferences() {
        java.util.List<UIManager.LookAndFeelInfo> all = LookAndFeels.installed();
        String[] names = all.stream().map(UIManager.LookAndFeelInfo::getName).toArray(String[]::new);
        javax.swing.JComboBox<String> box = new javax.swing.JComboBox<>(names);
        box.setName(ComponentNames.PREFERENCES_LOOK_AND_FEEL);
        String current = UIManager.getLookAndFeel() == null ? null : UIManager.getLookAndFeel().getClass().getName();
        for (int i = 0; i < all.size(); i++) if (all.get(i).getClassName().equals(current)) box.setSelectedIndex(i);
        JOptionPane pane = new JOptionPane(new Object[] {"Look&Feel:", box}, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
        if (app.ui.dialogs().showOption(parent, "Preferences", pane) != JOptionPane.OK_OPTION || box.getSelectedIndex() < 0) return null;
        String cls = all.get(box.getSelectedIndex()).getClassName();
        LookAndFeels.apply(cls, app.mainView());
        return cls;
    }

    /** File &gt; Open &gt; Example Dataset &gt; {@code name}: opens the bundled example {@code file} (DV-87). */
    public Action openExample(java.nio.file.Path file) {
        return action("openExample", Examples.label(file), null, null, 0, e -> files.openFile(file.toFile(), parent));
    }

    /** Datasets &gt; switch &gt; {@code name} (F-33): makes dataset {@code index} current and raises its window. */
    public Action switchTo(int index, String name) {
        return action("switchDS", name, null, null, 0, e -> {
            app.datasets.setCurrent(index);
            if (index < app.datasets.size()) {
                var f = app.frameOf(app.datasets.get(index));
                if (f != null) f.raise();
            }
        });
    }

    private static Action action(String command, String label, Icon icon, String tooltip, int key, Consumer<ActionEvent> body) {
        AbstractAction a = new AbstractAction(label, icon) {
            @Override
            public void actionPerformed(ActionEvent e) {
                body.accept(e);
            }
        };
        a.putValue(Action.ACTION_COMMAND_KEY, command);
        if (tooltip != null) a.putValue(Action.SHORT_DESCRIPTION, tooltip);
        if (key != 0) a.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(key, menuShortcutMask()));
        return a;
    }

    /** Command on macOS, Ctrl elsewhere (also headless, where the toolkit cannot be asked). */
    static int menuShortcutMask() {
        if (!GraphicsEnvironment.isHeadless()) return Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac") ? InputEvent.META_DOWN_MASK
                : InputEvent.CTRL_DOWN_MASK;
    }
}
