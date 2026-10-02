package io.github.steinbeck.seneca.app.judges;

import java.awt.BorderLayout;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.border.Border;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import javax.swing.table.AbstractTableModel;

import io.github.steinbeck.seneca.app.ComponentNames;
import io.github.steinbeck.seneca.app.dataset.DatasetContext;
import io.github.steinbeck.seneca.app.ui.OptionPanes;
import io.github.steinbeck.seneca.config.JudgeConfiguration;
import io.github.steinbeck.seneca.judges.Judge;
import io.github.steinbeck.seneca.judges.TwoDSpectrumJudge;
import io.github.steinbeck.seneca.model.SenecaDataset;

/**
 * One sub-tab of "Judges" (F-44), port of {@code JudgeConfigurator}: a judge-specific centre panel
 * (subclasses) and the common bottom row
 *
 * <ul>
 *   <li>"Autoconfigure Judge" (tooltip "Autoconfigures &lt;judge name&gt;"):
 *       {@link JudgeConfiguration#autoconfigure} (enables the judge, then the judge-specific
 *       configuration; a 2D assignment that fails disables it again, DEFAULT DV-46);</li>
 *   <li>"Report Judgeconfig" (tooltip "Opens a window with judge configuration in ASCII output"): the
 *       window "Judge Configuration Report" ({@link JudgeReportPanel}) with
 *       {@link JudgeConfiguration#report} for the 2D judges; nothing for the others (2001 printed "dummy"
 *       on stdout);</li>
 *   <li>check box "Activate this Judge?": {@link JudgeConfiguration#setActivated}; it follows every
 *       change of the judge's enabled flag (the panel is the judge's {@code JudgeListener}, as 2001).</li>
 * </ul>
 * The judge is {@code sd.getJudge(<class>)} when the window opens (as 2001). The panels never call
 * {@code JudgeConfiguration.datasetWindowOpened}: the dataset window did that once before constructing them.
 * Subclasses build their centre panel and pass it to {@link #setCenter}; {@link #refresh()} updates it
 * after a judge or dataset change.
 */
public abstract class JudgeConfiguratorPanel extends JPanel {

    private static final Logger LOG = Logger.getLogger(JudgeConfiguratorPanel.class.getName());

    protected final DatasetContext ctx;
    protected final SenecaDataset sd;
    protected final Judge judge;
    protected final JCheckBox enabled;
    private final JButton autoconfigureButton = new JButton("Autoconfigure Judge");
    private final JButton reportButton = new JButton("Report Judgeconfig");

    @SuppressWarnings("this-escape")    // Swing: listeners and children in the constructor
    protected JudgeConfiguratorPanel(DatasetContext ctx, Class<? extends Judge> type) {
        super(new BorderLayout());
        this.ctx = ctx;
        this.sd = ctx.dataset();
        this.judge = sd.getJudge(type);
        String key = type.getSimpleName();

        autoconfigureButton.setName(ComponentNames.JUDGE_AUTOCONFIGURE + key);
        autoconfigureButton.setToolTipText("Autoconfigures " + judge.name);
        autoconfigureButton.addActionListener(e -> autoconfigure());
        reportButton.setName(ComponentNames.JUDGE_REPORT + key);
        reportButton.setToolTipText("Opens a window with judge configuration in ASCII output");
        reportButton.addActionListener(e -> report());
        enabled = new JCheckBox("Activate this Judge?", judge.getEnabled());
        enabled.setName(ComponentNames.JUDGE_ACTIVATE + key);
        enabled.addActionListener(e -> JudgeConfiguration.setActivated(judge, enabled.isSelected()));

        Box southBox = new Box(BoxLayout.X_AXIS);
        southBox.add(autoconfigureButton);
        southBox.add(reportButton);
        southBox.add(Box.createHorizontalGlue());
        southBox.add(enabled);
        add(southBox, BorderLayout.SOUTH);

        judge.setJudgeListener(this::judgeDataChanged);
        Runnable datasetChanged = this::refresh;
        sd.addChangeListener(datasetChanged);
        ctx.frame().addInternalFrameListener(new InternalFrameAdapter() {
            @Override
            public void internalFrameClosed(InternalFrameEvent e) {
                sd.removeChangeListener(datasetChanged);
                judge.setJudgeListener(null);
            }
        });
    }

    /** The judge this panel configures. */
    public Judge judge() {
        return judge;
    }

    /** Sets the judge-specific centre panel (called once by the subclass constructor). */
    protected final void setCenter(JComponent center) {
        add(center, BorderLayout.CENTER);
    }

    /** Etched border titled with the judge name (2001 {@code getTitledBorder()}). */
    protected Border titledBorder() {
        return BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), judge.name);
    }

    /** Updates the centre panel from the judge and the dataset (2001 {@code reactOnJudgeDataChange}). */
    protected void refresh() {
    }

    /** {@code JudgeListener}: the check box follows the judge's enabled flag. */
    private void judgeDataChanged() {
        if (enabled.isSelected() != judge.getEnabled()) enabled.setSelected(judge.getEnabled());
        refresh();
    }

    /**
     * "Autoconfigure Judge". A 2D judge whose assignment fails stays inactive; 2001 printed only
     * "Assignment failed." to stdout, the port prints the same and also says so in a message (DV-83).
     */
    private void autoconfigure() {
        try {
            JudgeConfiguration.autoconfigure(sd, judge, ctx.app().compat);
            if (judge instanceof TwoDSpectrumJudge && !judge.getEnabled()) {
                System.out.println("Assignment failed.");
                ctx.app().ui.dialogs().show(this, "Autoconfigure Judge", OptionPanes.message(assignmentFailed(judge.name),
                        JOptionPane.WARNING_MESSAGE));
            }
        } catch (RuntimeException e) {
            // DEFAULT: cannot happen (the assigners fail without exceptions); reported if it does (DV-83)
            LOG.log(Level.WARNING, "Autoconfigure " + judge.name + " failed", e);
            ctx.app().ui.dialogs().show(this, "Autoconfigure Judge",
                    OptionPanes.message("Autoconfigure " + judge.name + " failed:\n" + e, JOptionPane.ERROR_MESSAGE));
        }
        judgeDataChanged();
    }

    /** The message of a failed 2D assignment. */
    static String assignmentFailed(String judgeName) {
        return "Assignment failed: " + judgeName + " could not be configured and stays inactive.\n"
                + "Every carbon needs an assigned 13C shift (Atom Properties: Autoassignment), and the\n"
                + "spectrum's correlations must fit the assigned shifts.";
    }

    /** "Report Judgeconfig". */
    private void report() {
        String text = JudgeConfiguration.report(judge);
        if (text == null) return;
        ctx.app().ui.windows().show(JudgeReportPanel.TITLE, new JudgeReportPanel(text), null);
    }

    /**
     * Refreshes a table after a change: a new structure (columns) if the column count changed, else new
     * data. Returns true if the structure was rebuilt (column editors must then be installed again).
     */
    protected static boolean refreshTable(JTable table, AbstractTableModel model) {
        if (table.isEditing()) table.getCellEditor().cancelCellEditing();
        if (table.getColumnCount() != model.getColumnCount()) {
            model.fireTableStructureChanged();
            return true;
        }
        model.fireTableDataChanged();
        return false;
    }
}
