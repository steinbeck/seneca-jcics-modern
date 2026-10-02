package io.github.steinbeck.seneca.app;

import java.util.List;

import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.UIManager;

/**
 * The menu bar (feature-inventory 2.1), on macOS the screen menu bar. Structure and labels are the 2001
 * ones without the DROP items (disabled/no-op entries: Edit &gt; Copy/Cut/Paste and the data-editing
 * shortcuts, View &gt; Status of Generator Threads, the Calculate menu, File &gt; Export, the unlabeled
 * Print and Open items):
 *
 * <pre>
 * File      New Dataset | Open &gt; SENECA Dataset, Example Dataset &gt; (bundled examples) | Save &gt; SENECA Dataset
 *           | Import &gt; LUCY dataset | --- | Exit
 * Edit      Preferences &gt; Look&amp;Feel &gt; (installed look and feels)
 * View      Current Structures
 * Datasets  switch &gt; (names of the open datasets)
 * Help      About Seneca
 * </pre>
 * "Example Dataset" is an addition (DV-87): the ten datasets of the 2001 distribution ({@link Examples});
 * disabled if none are installed.
 */
public final class MainMenuBar {

    private MainMenuBar() {
    }

    public static JMenuBar create(AppContext app, AppActions actions) {
        Resources r = app.resources;
        JMenuBar mb = new JMenuBar();

        JMenu file = new JMenu(r.text("fileLabel"));
        file.add(item(actions.newDataset));
        JMenu open = new JMenu(r.text("openLabel"));
        open.add(item(actions.openDataset));
        JMenu examples = new JMenu(r.text("examplesLabel"));
        for (java.nio.file.Path f : app.examples.files()) examples.add(item(actions.openExample(f)));
        examples.setEnabled(examples.getItemCount() > 0);
        open.add(examples);
        file.add(open);
        JMenu save = new JMenu(r.text("saveLabel"));
        save.add(item(actions.saveDataset));
        file.add(save);
        JMenu imp = new JMenu(r.text("importLabel"));
        imp.add(item(actions.importLucy));
        file.add(imp);
        file.addSeparator();
        file.add(item(actions.exit));
        mb.add(file);

        JMenu edit = new JMenu(r.text("editLabel"));
        JMenu prefs = new JMenu(r.text("prefsLabel"));
        JMenu lnf = new JMenu(r.text("looknfeelLabel"));
        for (UIManager.LookAndFeelInfo info : LookAndFeels.installed()) lnf.add(item(actions.lookAndFeel(info)));
        prefs.add(lnf);
        edit.add(prefs);
        mb.add(edit);

        JMenu view = new JMenu(r.text("viewLabel"));
        view.add(item(actions.currentStructures));
        mb.add(view);

        JMenu datasets = new JMenu(r.text("datasetsLabel"));
        JMenu sw = new JMenu(r.text("switchLabel"));
        datasets.add(sw);
        mb.add(datasets);
        app.datasets.addChangeListener(() -> {
            sw.removeAll();
            List<String> names = app.datasets.names();
            for (int f = 0; f < names.size(); f++) sw.add(item(actions.switchTo(f, names.get(f))));
        });

        JMenu help = new JMenu(r.text("helpLabel"));
        help.add(item(actions.about));
        mb.add(help);
        return mb;
    }

    private static JMenuItem item(Action a) {
        // the actions' short descriptions are the toolbar tooltips; 2001 menu items had none
        return new JMenuItem(a) {
            @Override
            protected void configurePropertiesFromAction(Action action) {
                super.configurePropertiesFromAction(action);
                setToolTipText(null);
            }

            @Override
            protected void actionPropertyChanged(Action action, String propertyName) {
                super.actionPropertyChanged(action, propertyName);
                if (Action.SHORT_DESCRIPTION.equals(propertyName)) setToolTipText(null);
            }
        };
    }
}
