package io.github.steinbeck.seneca.app.run;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.table.AbstractTableModel;

import io.github.steinbeck.seneca.structgen.RunController;
import io.github.steinbeck.seneca.structgen.RunSlot;
import io.github.steinbeck.seneca.structgen.RunStatus;

/**
 * The run table of the "Structure Generation" tab (feature-inventory F-60; legacy
 * {@code SGServerPanel.SGServerTableModel}): one row per run slot of the window's
 * {@link RunController}, columns {@code "No."}, {@code "ID"}, {@code "Host"}, {@code "Dataset"},
 * {@code "MF"}, {@code "Status "} (sic, trailing blank), {@code "Iter."}.
 *
 * <ul>
 *   <li>No.: row + 1; ID: {@code local<k>}; Host: "local" (no RMI).</li>
 *   <li>A slot without a run (new, after "Reset selected"): Dataset and MF "none", Status "idle",
 *       Iter. "none" (legacy: no status object yet).</li>
 *   <li>Otherwise the last known {@link RunStatus}: dataset name, molecular formula, the legacy status
 *       string ("Running", "Holding Results", "Stopped by User"), iteration.</li>
 * </ul>
 * Statuses come from the 2-second polling ({@link #refresh()}, every slot each tick) and from the run
 * listener ({@link #setStatus}). No cell is editable (legacy accepted edits and dropped them). EDT only.
 */
public final class RunTableModel extends AbstractTableModel {

    static final String[] NAMES = {"No.", "ID", "Host", "Dataset", "MF", "Status ", "Iter."};

    /**
     * Preferred column widths (2001: seven equal columns, which cut off dataset, formula, status and
     * iteration count at the default window size, DV-88): narrow No./ID/Host, wide Status and Iter.
     */
    static final int[] WIDTHS = {32, 52, 44, 84, 84, 116, 72};

    /** Sum of {@link #WIDTHS}. */
    static int totalWidth() {
        int w = 0;
        for (int x : WIDTHS) w += x;
        return w;
    }

    private final RunController runs;
    private List<RunSlot> rows = new ArrayList<>();
    private final Map<RunSlot, RunStatus> statuses = new HashMap<>();

    public RunTableModel(RunController runs) {
        this.runs = runs;
        refresh();
    }

    /** The slot shown in {@code row}. */
    public RunSlot slot(int row) {
        return rows.get(row);
    }

    /** The slots, in row order. */
    public List<RunSlot> slots() {
        return List.copyOf(rows);
    }

    /**
     * Re-reads the controller's slots and every slot's status (polling). Keeps the table selection
     * when only values changed or rows were appended.
     */
    public void refresh() {
        List<RunSlot> now = runs.slots();
        for (RunSlot s : now) statuses.put(s, s.hasRun() ? s.status() : null);
        statuses.keySet().retainAll(now);
        List<RunSlot> old = rows;
        rows = new ArrayList<>(now);
        if (old.size() <= now.size() && now.subList(0, old.size()).equals(old)) {
            if (!old.isEmpty()) fireTableRowsUpdated(0, old.size() - 1);
            if (now.size() > old.size()) fireTableRowsInserted(old.size(), now.size() - 1);
        } else {
            fireTableDataChanged();
        }
    }

    /** A status pushed by the run listener (EDT). */
    public void setStatus(RunSlot slot, RunStatus status) {
        int row = rows.indexOf(slot);
        if (row < 0) return;
        statuses.put(slot, slot.hasRun() ? status : null);
        fireTableRowsUpdated(row, row);
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return NAMES.length;
    }

    @Override
    public String getColumnName(int column) {
        return NAMES[column];
    }

    @Override
    public Class<?> getColumnClass(int c) {
        return String.class;
    }

    @Override
    public boolean isCellEditable(int row, int col) {
        return false;
    }

    @Override
    public Object getValueAt(int row, int column) {
        RunSlot slot = rows.get(row);
        RunStatus s = statuses.get(slot);
        return switch (column) {
            case 0 -> String.valueOf(row + 1);
            case 1 -> slot.id();
            case 2 -> slot.host();
            case 3 -> s == null ? "none" : String.valueOf(s.datasetName());
            case 4 -> s == null ? "none" : String.valueOf(s.molecularFormula());
            case 5 -> s == null ? "idle" : s.state().label;
            case 6 -> s == null ? "none" : String.valueOf(s.iteration());
            default -> "-";
        };
    }
}
