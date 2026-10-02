package io.github.steinbeck.seneca.app.dataset;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.beans.PropertyVetoException;

import javax.swing.JButton;
import javax.swing.JInternalFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;

import io.github.steinbeck.seneca.app.AppContext;
import io.github.steinbeck.seneca.app.ComponentNames;
import io.github.steinbeck.seneca.app.judges.JudgesPanel;
import io.github.steinbeck.seneca.app.log.LogbookPanel;
import io.github.steinbeck.seneca.app.run.StructureGenerationPanel;
import io.github.steinbeck.seneca.app.spectra.SpectraPanel;
import io.github.steinbeck.seneca.config.JudgeConfiguration;
import io.github.steinbeck.seneca.model.SenecaDataset;
import io.github.steinbeck.seneca.structgen.RunController;

/**
 * The window of one dataset (port of {@code SenecaDatasetFrame}, feature-inventory 3): an internal frame
 * titled with the dataset name (resizable, closable, maximizable, iconifiable; preferred 500x300) with
 * the tabs, in this order, and a "Close" button at the bottom:
 *
 * <ol>
 *   <li>General Settings ({@link GeneralSettingsPanel}, F-40, G1)</li>
 *   <li>NMR Spectra ({@link SpectraPanel}, F-41, G1)</li>
 *   <li>Atom Properties ({@link AtomPropertiesPanel}, F-42, G1)</li>
 *   <li>Annealing Schedule ({@link AnnealingSchedulePanel}, F-43, G1)</li>
 *   <li>Judges ({@link JudgesPanel}, F-44, G1)</li>
 *   <li>Structure Generation ({@link StructureGenerationPanel}, F-46, G2; the dropped "Serverlist" tab
 *       stood before it)</li>
 *   <li>Logbook ({@link LogbookPanel}, F-47, G2)</li>
 * </ol>
 * Opening the window has the legacy side effect of the judge configurators' constructors
 * ({@link JudgeConfiguration#datasetWindowOpened}, called exactly once here; the judge panels must not
 * call it again). Close button and close box ask through {@code DatasetFileActions.close} (F-48, G1),
 * which calls {@link #close()}. Activating the window makes its dataset the current one.
 */
public final class DatasetFrame extends JInternalFrame {

    private final AppContext app;
    private final SenecaDataset sd;
    private final RunController runs = new RunController();
    private final JTabbedPane tabs = new JTabbedPane();
    private final Runnable titleUpdater;
    private boolean closed;

    public DatasetFrame(AppContext app, SenecaDataset sd) {
        super(sd.getName(), true, true, true, true);
        this.app = app;
        this.sd = sd;
        setName(ComponentNames.DATASET_FRAME);
        app.registerFrame(this);
        app.runActivity.watch(runs);
        JudgeConfiguration.datasetWindowOpened(sd);
        DatasetContext ctx = new DatasetContext(app, sd, this, runs);

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addInternalFrameListener(new InternalFrameAdapter() {
            @Override
            public void internalFrameClosing(InternalFrameEvent e) {
                requestClose();
            }

            @Override
            public void internalFrameActivated(InternalFrameEvent e) {
                app.datasets.setCurrent(sd);
            }
        });
        getContentPane().setLayout(new BorderLayout());
        setPreferredSize(new Dimension(500, 300));

        tabs.setName(ComponentNames.DATASET_TABS);
        tab("General Settings", new GeneralSettingsPanel(ctx), "General Settings");
        tab("NMR Spectra", new SpectraPanel(ctx), "NMR Spectra");
        tab("Atom Properties", new AtomPropertiesPanel(ctx), "Atom Properties");
        tab("Annealing Schedule", new AnnealingSchedulePanel(ctx), "Annealing Schedule");
        tab("Judges", new JudgesPanel(ctx), "Judges");
        tab("Structure Generation", new StructureGenerationPanel(ctx), "Structure Generation");
        tab("Logbook", new LogbookPanel(app.logbook), "LogBook");
        getContentPane().add(tabs, BorderLayout.CENTER);

        JPanel south = new JPanel();
        JButton close = new JButton("Close");
        close.setName(ComponentNames.DATASET_CLOSE);
        close.addActionListener(e -> requestClose());
        south.add(close);
        getContentPane().add(south, BorderLayout.SOUTH);

        titleUpdater = () -> SwingUtilities.invokeLater(() -> {
            if (sd.getName() != null && !sd.getName().equals(getTitle())) setTitle(sd.getName());
        });
        sd.addChangeListener(titleUpdater);
    }

    private void tab(String title, java.awt.Component c, String tip) {
        tabs.addTab(title, null, c, tip);
    }

    public SenecaDataset dataset() {
        return sd;
    }

    /** The runs of this window ("Structure Generation"). */
    public RunController runs() {
        return runs;
    }

    public JTabbedPane tabs() {
        return tabs;
    }

    /** Selects the tab with this title (tests, "Datasets" menu). */
    public void showTab(String title) {
        int i = tabs.indexOfTab(title);
        if (i >= 0) tabs.setSelectedIndex(i);
    }

    /** Close button / close box: the save prompt of F-48 ({@code DatasetFileActions.close}). */
    public void requestClose() {
        if (closed) return;
        app.mainView().actions().files().close(this);
    }

    /**
     * Closes without asking: stops and clears this window's runs (legacy left them running) on the
     * run-control thread, removes the dataset from the open datasets and disposes the window.
     */
    public void close() {
        if (closed) return;
        closed = true;
        sd.removeChangeListener(titleUpdater);
        app.control(() -> {
            runs.clear();
            app.runActivity.unwatch(runs);
        });
        app.unregisterFrame(this);
        app.datasets.remove(sd);
        dispose();
    }

    public boolean isClosedByUser() {
        return closed;
    }

    /** Brings the window to the front and selects it. */
    public void raise() {
        try {
            if (isIcon()) setIcon(false);
            setSelected(true);
        } catch (PropertyVetoException e) {
            // never vetoed
        }
        toFront();
    }
}
