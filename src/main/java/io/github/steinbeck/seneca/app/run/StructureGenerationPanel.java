package io.github.steinbeck.seneca.app.run;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.text.html.HTMLEditorKit;

import io.github.steinbeck.seneca.app.AppContext;
import io.github.steinbeck.seneca.app.ComponentNames;
import io.github.steinbeck.seneca.app.dataset.DatasetContext;
import io.github.steinbeck.seneca.app.structure.Depictions;
import io.github.steinbeck.seneca.app.structure.StructureDisplayPanel;
import io.github.steinbeck.seneca.app.structure.StructureView;
import io.github.steinbeck.seneca.app.ui.EdtRunListener;
import io.github.steinbeck.seneca.app.ui.OptionPanes;
import io.github.steinbeck.seneca.judges.ScoreSummary;
import io.github.steinbeck.seneca.structgen.AnnealingLog;
import io.github.steinbeck.seneca.structgen.RunController;
import io.github.steinbeck.seneca.structgen.RunInput;
import io.github.steinbeck.seneca.structgen.RunListener;
import io.github.steinbeck.seneca.structgen.RunResult;
import io.github.steinbeck.seneca.structgen.RunSlot;
import io.github.steinbeck.seneca.structgen.RunStatus;
import io.github.steinbeck.seneca.structgen.StructureGeneratorResult;

/**
 * Tab "Structure Generation" of a dataset window (port of {@code SGServerPanel}; feature-inventory F-46,
 * F-60..F-70, F-85..F-88; docs/port/gui-design.md section 6). Layout as 2001: run table and the chart
 * "Annealing Data" on top, the best structure and "Current Structure Evaluation" below, and three titled
 * button groups at the bottom:
 *
 * <ul>
 *   <li>"Run Slots" (2001 "Server Admin", DV-82): "Add run slot" (2001 "Start local server": a new row
 *       {@code local<k>}, {@code RunController.addSlot}), "Clear Slot List" (stops every run and removes
 *       every slot; the chart keeps its data, as 2001);</li>
 *   <li>"Selections": "Start as selected" ({@code RunInput.fromDataset(dataset, DEFAULT)} on the EDT,
 *       then {@code RunController.start} for every selected row on the run-control thread, with
 *       {@code AppContext.runOptions(slot)}; a rejected input is reported with a message and nothing
 *       starts), "Stop Selected" ({@code RunController.stop}), "Reset selected" (drops all chart data,
 *       then {@code RunController.reset} of the selected rows: the run is stopped and discarded);</li>
 *   <li>"Result": "Summarize Result" ({@code RunController.summarize(DEFAULT)} into a new "Structure
 *       Display" window), "Log Annealing Data" (the chart data as table to stdout and
 *       {@code seneca.annealing.log}).</li>
 * </ul>
 * Polling (F-69): a {@code javax.swing.Timer} every 2000 ms ({@link #poll()}) refreshes every row of the
 * table and, round robin, one slot ("no. k" = row k): its best structure ("Best structure of no.k"), the
 * evaluation HTML ("Evaluation of no. k": {@code <b>Score: s[/max]</b>} + judge descriptions), the max
 * score line, and its annealing-log entries since the last poll of that slot (nothing is drained). A
 * slot without a best structure leaves the drawing and the evaluation as they are (legacy: its status
 * could not be fetched). Run events ({@link EdtRunListener}) update the table rows in between.
 *
 * <p>Threading: everything here runs on the EDT; calls that may wait for a stopping run
 * ({@code start}, {@code reset}, {@code clear}) go to {@code AppContext.control}, which reports back
 * with {@code invokeLater}. Closing the dataset window stops the timer and removes the listener.
 */
public final class StructureGenerationPanel extends JPanel {

    private static final Logger LOG = Logger.getLogger(StructureGenerationPanel.class.getName());

    /** Polling period (legacy {@code tableUpdateTimer}). */
    public static final int POLL_MILLIS = 2000;

    private final AppContext app;
    private final DatasetContext ctx;
    private final RunController runs;
    private final RunTableModel model;
    private final JTable table;
    private final AnnealingData data = new AnnealingData();
    private final AnnealingChartPanel chart;
    private final StructureView bestView = new StructureView();
    private final JEditorPane reportPane = new JEditorPane();
    private final JScrollPane reportScroll;
    private final Border etched = BorderFactory.createEtchedBorder();
    private final Timer timer;
    private final RunListener listener;

    /** Next row to poll (legacy {@code currentBest}). */
    private int current;
    /** Per slot: annealing-log entries already in the chart. */
    private final Map<RunSlot, Integer> logIndex = new HashMap<>();
    /** Slots whose start or reset is on the control thread: their (old) log is not read meanwhile. */
    private final Set<RunSlot> pending = new HashSet<>();
    /** MDL of the best structure shown (no new layout if it did not change). */
    private String shownMdl;
    private PrintStream stdout = System.out;

    @SuppressWarnings("this-escape")    // Swing: children and listeners are set up in the constructor
    public StructureGenerationPanel(DatasetContext ctx) {
        super(new BorderLayout());
        this.ctx = ctx;
        this.app = ctx.app();
        this.runs = ctx.runs();

        // run table (F-60): yellow cells, multi-row selection; column widths by content (DV-88)
        model = new RunTableModel(runs);
        table = new JTable(model);
        table.setName(ComponentNames.RUN_TABLE);
        table.setRowSelectionAllowed(true);
        table.setColumnSelectionAllowed(false);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        DefaultTableCellRenderer yellow = new DefaultTableCellRenderer() {
            @Override
            public java.awt.Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus,
                    int row, int column) {
                java.awt.Component c = super.getTableCellRendererComponent(t, value, selected, focus, row, column);
                setToolTipText(value == null ? null : value.toString());     // a cut-off cell shows in full
                return c;
            }
        };
        yellow.setBackground(Color.yellow);
        for (int c = 0; c < table.getColumnCount(); c++) {
            table.getColumnModel().getColumn(c).setCellRenderer(yellow);
            table.getColumnModel().getColumn(c).setPreferredWidth(RunTableModel.WIDTHS[c]);
        }
        table.getTableHeader().setReorderingAllowed(false);
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setColumnHeaderView(table.getTableHeader());   // also without a native peer (renderings)
        tableScroll.setBorder(new BevelBorder(BevelBorder.LOWERED));
        tableScroll.setPreferredSize(new Dimension(RunTableModel.totalWidth() + 4, 200));
        tableScroll.setMinimumSize(new Dimension(150, 80));

        // chart (F-70)
        chart = new AnnealingChartPanel(data);
        chart.setPreferredSize(new Dimension(400, 200));
        chart.setMinimumSize(new Dimension(150, 80));

        // best structure (F-86; a double click opens it in a "Structure Display" window) and evaluation (F-87)
        bestView.setName(ComponentNames.RUN_BEST_STRUCTURE);
        bestView.setPreferredSize(new Dimension(300, 300));
        bestView.setMinimumSize(new Dimension(100, 80));
        bestView.setBorder(BorderFactory.createTitledBorder(etched, "No structure available"));
        bestView.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) showBestInWindow();
            }
        });
        reportPane.setName(ComponentNames.RUN_EVALUATION);
        reportPane.setEditable(false);
        reportPane.setEditorKit(new HTMLEditorKit());
        reportScroll = new JScrollPane(reportPane);
        reportScroll.setPreferredSize(new Dimension(300, 300));
        reportScroll.setMinimumSize(new Dimension(100, 80));
        reportScroll.setBorder(BorderFactory.createTitledBorder(etched, "Current Structure Evaluation"));
        reportScroll.setBackground(Color.white);

        // 2001: two fixed rows of two boxes; here split panes (the user can move the dividers), the
        // extra space goes half to each side and slightly more to the drawing row (DV-88)
        JSplitPane top = split(JSplitPane.HORIZONTAL_SPLIT, tableScroll, chart, 0.5);
        JSplitPane middle = split(JSplitPane.HORIZONTAL_SPLIT, bestView, reportScroll, 0.5);
        JSplitPane center = split(JSplitPane.VERTICAL_SPLIT, top, middle, 0.4);
        add(center, BorderLayout.CENTER);

        // button groups (2001: GridLayout(3, 1) each; titled borders of the look and feel)
        JPanel slotsGroup = group("Run Slots");
        JButton add = button(slotsGroup, "Add run slot", ComponentNames.RUN_ADD_SLOT, this::addSlot);
        add.setToolTipText("Start local server in SENECA 2001");
        button(slotsGroup, "Clear Slot List", ComponentNames.RUN_CLEAR, this::clearSlots);
        JPanel selGroup = group("Selections");
        button(selGroup, "Start as selected", ComponentNames.RUN_START, this::startSelected);
        button(selGroup, "Stop Selected", ComponentNames.RUN_STOP, this::stopSelected);
        button(selGroup, "Reset selected", ComponentNames.RUN_RESET, this::resetSelected);
        JPanel resultGroup = group("Result");
        button(resultGroup, "Summarize Result", ComponentNames.RUN_SUMMARIZE, this::summarize);
        button(resultGroup, "Log Annealing Data", ComponentNames.RUN_LOG_ANNEALING, this::logAnnealingData);
        Box south = new Box(BoxLayout.X_AXIS);
        south.add(slotsGroup);
        south.add(selGroup);
        south.add(resultGroup);
        add(south, BorderLayout.SOUTH);

        // run events (run threads -> EDT) and polling
        listener = new EdtRunListener(new RunListener() {
            @Override
            public void onStatus(RunSlot slot, RunStatus status) {
                model.setStatus(slot, status);
            }

            @Override
            public void onFinished(RunSlot slot, RunResult result) {
                model.setStatus(slot, slot.status());
            }
        });
        runs.addListener(listener);
        timer = new Timer(POLL_MILLIS, e -> poll());
        timer.start();
        ctx.frame().addInternalFrameListener(new InternalFrameAdapter() {
            @Override
            public void internalFrameClosed(InternalFrameEvent e) {
                dispose();
            }
        });
    }

    private static JSplitPane split(int orientation, java.awt.Component a, java.awt.Component b, double weight) {
        JSplitPane p = new JSplitPane(orientation, true, a, b);
        p.setResizeWeight(weight);
        p.setBorder(null);
        p.setDividerSize(6);
        return p;
    }

    private static JPanel group(String title) {
        JPanel p = new JPanel(new GridLayout(3, 1));
        p.setBorder(BorderFactory.createTitledBorder(title));
        return p;
    }

    private static JButton button(JPanel group, String label, String name, Runnable action) {
        JButton b = new JButton(label);
        b.setName(name);
        b.addActionListener(e -> action.run());
        group.add(b);
        return b;
    }

    /** Stops polling and listening (the dataset window was closed). */
    public void dispose() {
        timer.stop();
        runs.removeListener(listener);
    }

    // ------------------------------------------------------------------ accessors (tests)

    public RunTableModel model() {
        return model;
    }

    public JTable table() {
        return table;
    }

    public AnnealingChartPanel chart() {
        return chart;
    }

    public StructureView bestStructureView() {
        return bestView;
    }

    /** Title of the best-structure border ("No structure available", "Best structure of no.k"). */
    public String bestStructureTitle() {
        return ((javax.swing.border.TitledBorder) bestView.getBorder()).getTitle();
    }

    /** Title of the evaluation border ("Current Structure Evaluation", "Evaluation of no. k"). */
    public String evaluationTitle() {
        return ((javax.swing.border.TitledBorder) reportScroll.getBorder()).getTitle();
    }

    public JEditorPane evaluationPane() {
        return reportPane;
    }

    /** The polling timer (tests stop it and call {@link #poll()} themselves). */
    public Timer timer() {
        return timer;
    }

    /** Where "Log Annealing Data" prints its table (stdout; tests capture it). */
    public void setStdout(PrintStream out) {
        this.stdout = out;
    }

    // ------------------------------------------------------------------ actions

    private List<RunSlot> selectedSlots() {
        List<RunSlot> l = new ArrayList<>();
        for (int r : table.getSelectedRows()) if (r < model.getRowCount()) l.add(model.slot(r));
        return l;
    }

    /** "Add run slot" (2001 "Start local server", F-61). */
    public void addSlot() {
        runs.addSlot();
        model.refresh();
    }

    /** "Clear Slot List" (F-63): stops all runs and removes all slots (control thread). */
    public void clearSlots() {
        app.control(() -> {
            runs.clear();
            SwingUtilities.invokeLater(() -> {
                logIndex.clear();
                pending.clear();
                current = 0;
                model.refresh();
            });
        });
    }

    /** "Start as selected" (F-64). */
    public void startSelected() {
        List<RunSlot> sel = selectedSlots();
        if (sel.isEmpty()) return;
        RunInput in;
        try {
            in = RunInput.fromDataset(ctx.dataset(), app.compat);   // EDT: reads and mutates the judges (Q6)
        } catch (IllegalArgumentException e) {
            startFailed(e.getMessage());
            return;
        }
        if (in.maxScore() == 0) {                                   // DV-93: nothing to score, the search would run blind
            startFailed(RunInput.NO_SCORE);
            return;
        }
        pending.addAll(sel);
        app.control(() -> {
            List<RunSlot> started = new ArrayList<>();
            String failure = null;
            for (RunSlot s : sel) {
                if (!runs.slots().contains(s)) continue;          // removed by "Clear Slot List" meanwhile
                try {
                    runs.start(s, in, app.runOptions(s));
                    started.add(s);
                } catch (IllegalArgumentException e) {
                    failure = e.getMessage();                       // same input for every slot: none starts
                    break;
                } catch (RuntimeException e) {
                    LOG.log(Level.WARNING, "start of " + s + " failed", e);
                    failure = String.valueOf(e);
                    break;
                }
            }
            String f = failure;
            SwingUtilities.invokeLater(() -> {
                for (RunSlot s : started) logIndex.put(s, 0);
                pending.removeAll(sel);
                model.refresh();
                if (f != null) startFailed(f);
            });
        });
    }

    private void startFailed(String message) {
        app.ui.dialogs().show(this, "Start as selected", OptionPanes.message(message, JOptionPane.ERROR_MESSAGE));
    }

    /** "Stop Selected" (F-65): asks the selected runs to stop (does not wait). */
    public void stopSelected() {
        for (RunSlot s : selectedSlots()) runs.stop(s);
    }

    /** "Reset selected" (F-66): drops all chart data, stops and discards the selected runs. */
    public void resetSelected() {
        data.clear();
        chart.refresh();
        List<RunSlot> sel = selectedSlots();
        if (sel.isEmpty()) return;
        pending.addAll(sel);
        app.control(() -> {
            for (RunSlot s : sel) if (runs.slots().contains(s)) runs.reset(s);
            SwingUtilities.invokeLater(() -> {
                for (RunSlot s : sel) logIndex.put(s, 0);
                pending.removeAll(sel);
                model.refresh();
            });
        });
    }

    /** "Summarize Result" (F-67): the merged result in a new "Structure Display" window. */
    public StructureDisplayPanel summarize() {
        StructureGeneratorResult merged = runs.summarize(app.compat);
        return StructureDisplayPanel.showResult(app, merged, ctx.dataset().getName());
    }

    /**
     * Double click on the best structure (addition, DV-88): the structure shown in a new "Structure
     * Display" window (large drawing, Save MDL/SDF, Copy); nothing if no structure is shown.
     */
    public StructureDisplayPanel showBestInWindow() {
        if (shownMdl == null) return null;
        StructureDisplayPanel p = StructureDisplayPanel.open(app, StructureDisplayPanel.title(ctx.dataset().getName()));
        p.addStructure(Depictions.fromMdl(shownMdl), bestStructureTitle(), reportPane.getText());
        return p;
    }

    /** "Log Annealing Data" (F-68): the chart data as table to stdout and {@code seneca.annealing.log}. */
    public void logAnnealingData() {
        for (String line : data.logLines()) {
            stdout.println(line);
            app.logFiles.annealing(line + "\n");
        }
    }

    // ------------------------------------------------------------------ polling (F-69)

    /** One tick of the 2-second timer: every row, then one slot round robin. */
    public void poll() {
        model.refresh();
        List<RunSlot> slots = model.slots();
        if (slots.isEmpty()) return;
        if (current >= slots.size()) current = 0;
        int k = current;
        current = (current + 1) % slots.size();
        pollSlot(slots.get(k), k, slots.size());
    }

    private void pollSlot(RunSlot slot, int k, int rows) {
        if (!slot.hasRun()) return;
        RunStatus st = slot.status();
        ScoreSummary eval = st.bestEvaluation();
        if (eval != null) data.setMaxScore(eval.maxScore);
        if (st.bestStructure() != null && eval != null) showBest(st.bestStructure(), eval, k);
        if (!pending.contains(slot)) {
            int from = logIndex.getOrDefault(slot, 0);
            List<AnnealingLog.Entry> entries = slot.annealingLogSince(from);
            logIndex.put(slot, from + entries.size());
            if (!entries.isEmpty() || !data.hasData()) data.add(k, rows, entries);
        }
        chart.refresh();
    }

    private void showBest(String mdl, ScoreSummary eval, int k) {
        if (!mdl.equals(shownMdl)) {
            bestView.setStructure(Depictions.fromMdl(mdl));
            shownMdl = mdl;
        }
        bestView.setBorder(BorderFactory.createTitledBorder(etched, "Best structure of no." + (k + 1)));
        StringBuilder report = new StringBuilder("<html>\n");
        report.append("<b>Score: ").append(eval.score);
        if (eval.maxScore > 0) report.append("/").append(eval.maxScore);
        report.append("</b>");
        report.append(eval.description).append("</html>\n");
        reportScroll.setBorder(BorderFactory.createTitledBorder(etched, "Evaluation of no. " + (k + 1)));
        reportPane.setText(report.toString());
        reportPane.setCaretPosition(0);
    }
}
