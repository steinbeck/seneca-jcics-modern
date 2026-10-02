package io.github.steinbeck.seneca.app.spectra;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.border.BevelBorder;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import javax.swing.table.AbstractTableModel;

import io.github.steinbeck.seneca.app.ComponentNames;
import io.github.steinbeck.seneca.app.dataset.DatasetContext;
import io.github.steinbeck.seneca.app.ui.OptionPanes;
import io.github.steinbeck.seneca.io.WinNmrPeakPicking;
import io.github.steinbeck.seneca.model.NMRSignal;
import io.github.steinbeck.seneca.model.NMRSpectrum;
import io.github.steinbeck.seneca.model.SenecaDataset;
import io.github.steinbeck.seneca.model.SpectrumSlot;

/**
 * One sub-tab of "NMR Spectra" (F-41), port of {@code NMRSpectrumTablePanel}: the label
 * {@code "<n> signals in <specType>."} (2001's constructor wrote "Signals", but registering the change
 * listener called it at once, so users only ever saw lower case), the signal
 * table and, below it, "Add Signal", "Delete Signal" and the combo "Import peak picking".
 *
 * <ul>
 *   <li>Table: {@code No.} (1-based), one column per axis titled with its nucleus ({@code 13C},
 *       {@code 1H}; in the stored axis order), {@code Intensity}, {@code Phase} (combo NONE / POSITIVE /
 *       NEGATIVE). Shifts and intensity are edited as floats (the table refuses other text).</li>
 *   <li>"Add Signal": appends an empty signal (shifts 0, intensity 0, phase NONE).</li>
 *   <li>"Delete Signal": removes the selected row (port: nothing without a selection; 2001 threw).</li>
 *   <li>"Paste WinNMR pp from clipboard": {@link WinNmrPeakPicking#paste} with the clipboard text
 *       ({@code UiServices.clipboard()}); the combo returns to "Import peak picking". Text that cannot be
 *       parsed is reported (the signals read before the error stay, as 2001; DV-83). The GIFA import (a
 *       no-op in 2001) is dropped.</li>
 *   <li>DEFAULT (DV-85): a spectrum read empty from SML has no axes; before its first signal is added or
 *       pasted it gets the slot's axes ({@link NMRSpectrum#ensureAxes}).</li>
 * </ul>
 */
public final class SpectrumTablePanel extends JPanel {

    private static final Logger LOG = Logger.getLogger(SpectrumTablePanel.class.getName());

    /** Combo items (2001 order without the dropped GIFA import). */
    public static final String IMPORT_TITLE = "Import peak picking", PASTE_WINNMR = "Paste WinNMR pp from clipboard";

    private final DatasetContext ctx;
    private final SpectrumSlot slot;
    private final NMRSpectrum spectrum;
    private final SignalModel model = new SignalModel();
    private final JTable table;
    private final JLabel northLabel;
    private final JButton addButton = new JButton("Add Signal");
    private final JButton deleteButton = new JButton("Delete Signal");
    private final JComboBox<String> ppSelector = new JComboBox<>(new String[] {IMPORT_TITLE, PASTE_WINNMR});
    private int dimShown = -1;

    @SuppressWarnings("this-escape")    // Swing: listeners and children in the constructor
    public SpectrumTablePanel(DatasetContext ctx, SpectrumSlot slot) {
        super(new BorderLayout());
        this.ctx = ctx;
        this.slot = slot;
        SenecaDataset sd = ctx.dataset();
        this.spectrum = sd.spectrum(slot);
        table = new JTable(model);
        table.setName(ComponentNames.SPECTRUM_TABLE + slot.key);
        addButton.setName(ComponentNames.SPECTRUM_ADD + slot.key);
        deleteButton.setName(ComponentNames.SPECTRUM_DELETE + slot.key);
        ppSelector.setName(ComponentNames.SPECTRUM_IMPORT + slot.key);
        table.setRowSelectionAllowed(true);
        installPhaseEditor();
        setPreferredSize(new Dimension(500, 300));

        JScrollPane scrollpane = new JScrollPane(table);
        scrollpane.setColumnHeaderView(table.getTableHeader());     // headers also before addNotify (offscreen renderings)
        scrollpane.setBorder(new BevelBorder(BevelBorder.LOWERED));
        scrollpane.setPreferredSize(new Dimension(400, 400));
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(scrollpane, BorderLayout.CENTER);

        addButton.addActionListener(e -> addSignal());
        deleteButton.addActionListener(e -> deleteSignal());
        ppSelector.addActionListener(e -> {
            if (PASTE_WINNMR.equals(ppSelector.getSelectedItem())) pasteWinNmr();
            if (ppSelector.getSelectedIndex() != 0) ppSelector.setSelectedIndex(0);
        });
        JPanel southPanel = new JPanel(new FlowLayout());
        southPanel.add(addButton);
        southPanel.add(deleteButton);
        southPanel.add(ppSelector);

        // lower case from the start, as 2001 showed it (its addChangeListener ran stateChanged at once)
        northLabel = new JLabel(spectrum.size() + " signals in " + spectrum.specType + ".");
        northLabel.setName(ComponentNames.SPECTRUM_LABEL + slot.key);
        add(northLabel, BorderLayout.NORTH);
        add(southPanel, BorderLayout.SOUTH);
        add(centerPanel, BorderLayout.CENTER);

        Runnable changed = this::changed;
        spectrum.addChangeListener(changed);
        sd.addChangeListener(changed);
        ctx.frame().addInternalFrameListener(new InternalFrameAdapter() {
            @Override
            public void internalFrameClosed(InternalFrameEvent e) {
                spectrum.removeChangeListener(changed);
                sd.removeChangeListener(changed);
            }
        });
    }

    public SpectrumSlot slot() {
        return slot;
    }

    public JTable table() {
        return table;
    }

    public JLabel label() {
        return northLabel;
    }

    /** 2001 {@code stateChanged}: the label and the table. */
    private void changed() {
        northLabel.setText(spectrum.size() + " signals in " + spectrum.specType + ".");
        if (spectrum.dim != dimShown) {
            if (table.isEditing()) table.getCellEditor().cancelCellEditing();
            model.fireTableStructureChanged();
            installPhaseEditor();
        } else {
            model.fireTableDataChanged();
        }
    }

    private void installPhaseEditor() {
        dimShown = spectrum.dim;
        JComboBox<String> phaseSelector = new JComboBox<>(NMRSignal.PHASENAMES.toArray(new String[0]));
        table.getColumnModel().getColumn(spectrum.dim + 2).setCellEditor(new DefaultCellEditor(phaseSelector));
    }

    private void ensureAxes() {
        if (!ctx.app().compat.strict()) spectrum.ensureAxes(slot.defaultNuclei());
    }

    /** "Add Signal". */
    private void addSignal() {
        if (table.isEditing()) table.getCellEditor().stopCellEditing();
        ensureAxes();
        spectrum.newSignal();
    }

    /** "Delete Signal": the selected row. */
    private void deleteSignal() {
        if (table.isEditing()) table.getCellEditor().cancelCellEditing();
        int row = table.getSelectedRow();
        if (row < 0 || row >= spectrum.size()) return;
        spectrum.removeSignal(table.convertRowIndexToModel(row));
    }

    /** "Paste WinNMR pp from clipboard". */
    private void pasteWinNmr() {
        String text = ctx.app().ui.clipboard().getText();
        if (text == null || text.isBlank()) {
            message("The clipboard holds no text.");
            return;
        }
        ensureAxes();
        try {
            WinNmrPeakPicking.paste(text, spectrum, ctx.app().compat);
        } catch (RuntimeException e) {
            LOG.log(Level.INFO, "WinNMR peak picking not readable", e);
            message("The clipboard text is not a complete WinNMR peak picking list.\n"
                    + "The signals read before the error were added.\n" + e);
        }
        changed();
    }

    private void message(String text) {
        ctx.app().ui.dialogs().show(this, PASTE_WINNMR, OptionPanes.message(text, JOptionPane.WARNING_MESSAGE));
    }

    private final class SignalModel extends AbstractTableModel {
        @Override
        public int getColumnCount() {
            return spectrum.dim + 3;
        }

        @Override
        public int getRowCount() {
            return spectrum.size();
        }

        @Override
        public String getColumnName(int column) {
            if (column == 0) return "No.";
            if (column <= spectrum.dim) return spectrum.nucleus[column - 1];
            if (column == spectrum.dim + 1) return "Intensity";
            return "Phase";
        }

        @Override
        public Class<?> getColumnClass(int column) {
            if (column == 0) return Integer.class;
            if (column <= spectrum.dim + 1) return Float.class;
            return String.class;
        }

        @Override
        public Object getValueAt(int row, int column) {
            if (row >= spectrum.size()) return null;
            NMRSignal s = spectrum.getSignal(row);
            if (column == 0) return row + 1;
            if (column <= spectrum.dim) return column - 1 < s.shift.length ? s.shift[column - 1] : null;
            if (column == spectrum.dim + 1) return s.intensity;
            return s.phase >= 0 && s.phase < NMRSignal.PHASENAMES.size() ? NMRSignal.PHASENAMES.get(s.phase) : "-";
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column > 0;
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            if (row >= spectrum.size() || value == null) return;
            NMRSignal s = spectrum.getSignal(row);
            if (column >= 1 && column <= spectrum.dim) {
                if (column - 1 < s.shift.length) s.shift[column - 1] = ((Number) value).floatValue();
            } else if (column == spectrum.dim + 1) {
                s.intensity = ((Number) value).floatValue();
            } else if (column == spectrum.dim + 2) {
                int phase = NMRSignal.PHASENAMES.indexOf(value.toString());
                if (phase >= 0) s.phase = phase;
            }
            fireTableRowsUpdated(row, row);
        }
    }
}
