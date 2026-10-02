package io.github.steinbeck.seneca.app;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.LayoutManager;
import java.beans.PropertyVetoException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JDesktopPane;
import javax.swing.JInternalFrame;
import javax.swing.JPanel;
import javax.swing.JToolBar;

import io.github.steinbeck.seneca.app.dataset.DatasetFrame;
import io.github.steinbeck.seneca.model.SenecaDataset;

/**
 * Content of the main window (F-10..F-13): toolbar (north), desktop with the dataset windows (centre),
 * status bar (south); preferred size {@link #DEFAULT_SIZE} (2001: 800x600, see DV-88). A plain component,
 * so that tests can build and render it without a {@code JFrame} ({@link MainFrame} is the thin window
 * shell).
 *
 * <p>Dataset windows are {@link JInternalFrame}s on a {@link JDesktopPane}, as in 2001. Legacy added each
 * one to the desktop's BorderLayout centre, so it filled the desktop and the last opened was on top. Here
 * every dataset window opens maximized and stays fitted to the desktop while maximized; un-maximizing it
 * (its title bar button) makes it a free internal window (KEEP-ADAPT F-13).
 */
public final class MainView extends JPanel {

    /**
     * Preferred content size of the main window. 2001 used 800x600; with the macOS look and feel the
     * dataset window's seven tabs (and the Judges tab's seven sub-tabs) then no longer fit in one row
     * and "Structure Generation" and "Logbook" hid behind an overflow arrow, and the run tab's table,
     * chart and best structure were cramped. {@link MainFrame} shrinks the window to the screen if needed
     * (gui-design.md DV-88).
     */
    public static final Dimension DEFAULT_SIZE = new Dimension(1120, 780);

    private final AppContext app;
    private final AppActions actions;
    private final JToolBar toolbar = new JToolBar();
    private final JDesktopPane desktop = new JDesktopPane();
    private final StatusBar statusBar = new StatusBar();

    public MainView(AppContext app, AppActions actions) {
        super(new BorderLayout());
        this.app = app;
        this.actions = actions;
        setName(ComponentNames.MAIN_VIEW);
        setPreferredSize(new Dimension(DEFAULT_SIZE));
        app.setMainView(this);
        actions.setParent(this);

        toolbar.setName(ComponentNames.TOOLBAR);
        toolbar.add(toolButton(actions.newDataset));
        toolbar.add(toolButton(actions.openDataset));
        toolbar.add(toolButton(actions.saveDataset));
        toolbar.add(Box.createHorizontalGlue());
        add(toolbar, BorderLayout.NORTH);

        desktop.setName(ComponentNames.DESKTOP);
        desktop.setLayout(new MaximizedFramesLayout());
        desktop.putClientProperty("JDesktopPane.dragMode", "outline");
        add(desktop, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);

        statusBar.setStatus(app.resources.text("statusNoDatasetAvailable"), 1);
        boolean[] first = {true};
        app.datasets.addChangeListener(() -> {
            if (first[0]) {          // the immediate call of addChangeListener: keep "No dataset available"
                first[0] = false;
                return;
            }
            SenecaDataset sd = app.datasets.current();
            if (sd != null) {
                statusBar.setStatus(sd.getName(), 1);
                statusBar.setStatus(sd.getMolecularFormula(), 2);
            } else {
                statusBar.setStatus(app.resources.text("statusNoDatasetLoaded"), 1);
                statusBar.setStatus("", 2);
            }
        });
    }

    /** Toolbar button: icon only, tooltip, no focus (legacy {@code createToolbarButton}). */
    private static JButton toolButton(javax.swing.Action a) {
        JButton b = new JButton(a);
        b.setHideActionText(true);
        b.setFocusable(false);
        b.setName(ComponentNames.TOOLBAR + "." + a.getValue(javax.swing.Action.ACTION_COMMAND_KEY));
        return b;
    }

    /** Opens the dataset window of {@code sd} on the desktop, maximized and selected. */
    public DatasetFrame openDatasetWindow(SenecaDataset sd) {
        DatasetFrame f = new DatasetFrame(app, sd);
        desktop.add(f);
        f.setVisible(true);
        try {
            f.setMaximum(true);
            f.setSelected(true);
        } catch (PropertyVetoException e) {
            // never vetoed
        }
        f.toFront();
        desktop.revalidate();
        return f;
    }

    public JDesktopPane desktop() {
        return desktop;
    }

    public StatusBar statusBar() {
        return statusBar;
    }

    public JToolBar toolbar() {
        return toolbar;
    }

    public AppActions actions() {
        return actions;
    }

    /** The application this window belongs to. */
    public AppContext app() {
        return app;
    }

    /** The dataset windows on the desktop, front to back. */
    public List<DatasetFrame> datasetFrames() {
        List<DatasetFrame> l = new ArrayList<>();
        for (JInternalFrame f : desktop.getAllFrames()) if (f instanceof DatasetFrame d) l.add(d);
        return l;
    }

    /** Lays out maximized internal frames to fill the desktop; leaves all other children alone. */
    private static final class MaximizedFramesLayout implements LayoutManager {
        @Override
        public void addLayoutComponent(String name, Component comp) {
        }

        @Override
        public void removeLayoutComponent(Component comp) {
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            return new Dimension(0, 0);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return new Dimension(0, 0);
        }

        @Override
        public void layoutContainer(Container parent) {
            for (Component c : parent.getComponents()) {
                if (c instanceof JInternalFrame f && f.isMaximum() && !f.isIcon()) c.setBounds(0, 0, parent.getWidth(), parent.getHeight());
            }
        }
    }
}
