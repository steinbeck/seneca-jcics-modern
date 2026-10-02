package io.github.steinbeck.seneca.app.dataset;

import java.awt.BorderLayout;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import javax.swing.table.AbstractTableModel;

import io.github.steinbeck.seneca.app.ComponentNames;
import io.github.steinbeck.seneca.app.ui.OptionPanes;
import io.github.steinbeck.seneca.chem.MolecularFormula;
import io.github.steinbeck.seneca.model.SenecaDataset;

/**
 * Tab "General Settings" (F-40), port of {@code SenecaDatasetFrame.GeneralTableModel}: a two-column
 * table "Parameter" / "Setting" (header above the table, no scroll pane, as 2001) with the rows
 * Filename, Description, Molecular Formula and Molecular Mass. Column 2 is editable:
 *
 * <ul>
 *   <li>Filename: {@code sd.setName} (the window title and the Datasets menu follow the dataset's change
 *       event).</li>
 *   <li>Description: {@code sd.setDescription}.</li>
 *   <li>Molecular Formula: {@code sd.setMolecularFormula}, which regenerates the atoms (H 0, no shifts);
 *       every tab refreshes on the dataset's change event. Port (DV-85): re-entering the current formula
 *       changes nothing (2001: every committed edit, even an unchanged one, wiped all H counts and shift
 *       assignments), and an empty formula is refused with a message (2001: NullPointerException after
 *       the formula string had already been stored).</li>
 *   <li>Molecular Mass: {@code sd.setMolecularMass(Float)}; shown is the stored mass or, while it is 0,
 *       the mass computed from the formula ({@link MolecularFormula#mass}). Port: the stored mass is shown
 *       as the float it is (2001 widened it to double: "136.1251983642578"); a value that is not a number
 *       is ignored (2001: exception on the event thread).</li>
 * </ul>
 * Description and mass are not written to SML by the 2001 writer (DEFAULT writes them, DV-08).
 */
public final class GeneralSettingsPanel extends JPanel {

    private static final Logger LOG = Logger.getLogger(GeneralSettingsPanel.class.getName());

    private final DatasetContext ctx;
    private final SenecaDataset sd;
    private final Model model = new Model();
    private final JTable table;

    @SuppressWarnings("this-escape")    // Swing: listeners and children in the constructor
    public GeneralSettingsPanel(DatasetContext ctx) {
        super(new BorderLayout());
        this.ctx = ctx;
        this.sd = ctx.dataset();
        table = new JTable(model);
        table.setName(ComponentNames.GENERAL_TABLE);
        add(table.getTableHeader(), BorderLayout.NORTH);
        add(table, BorderLayout.CENTER);
        Runnable refresh = model::fireTableDataChanged;
        sd.addChangeListener(refresh);
        ctx.frame().addInternalFrameListener(new InternalFrameAdapter() {
            @Override
            public void internalFrameClosed(InternalFrameEvent e) {
                sd.removeChangeListener(refresh);
            }
        });
    }

    /** The table (tests). */
    public JTable table() {
        return table;
    }

    private void refuse(String message) {
        ctx.app().ui.dialogs().show(this, "General Settings", OptionPanes.message(message, JOptionPane.ERROR_MESSAGE));
    }

    private final class Model extends AbstractTableModel {
        private final String[] names = {"Parameter", "Setting"};
        private final String[] rows = {"Filename", "Description", "Molecular Formula", "Molecular Mass"};

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public int getRowCount() {
            return 4;
        }

        @Override
        public String getColumnName(int column) {
            return names[column];
        }

        @Override
        public Object getValueAt(int row, int column) {
            if (column == 0) return rows[row];
            return switch (row) {
                case 0 -> sd.getName();
                case 1 -> sd.getDescription();
                case 2 -> sd.getMolecularFormula();
                default -> massText();
            };
        }

        private String massText() {
            String mf = sd.getMolecularFormula();
            if (mf != null && !mf.isEmpty() && sd.getMolecularMass() == 0) return String.valueOf(MolecularFormula.mass(mf));
            return String.valueOf(sd.getMolecularMass());
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 1;
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            if (column != 1) return;
            String s = value == null ? "" : value.toString();
            switch (row) {
                case 0 -> sd.setName(s);
                case 1 -> sd.setDescription(s);
                case 2 -> setFormula(s.trim());
                default -> setMass(s);
            }
            fireTableDataChanged();
        }

        private void setFormula(String mf) {
            if (mf.equals(sd.getMolecularFormula())) return;
            if (mf.isEmpty()) {
                refuse("The molecular formula must not be empty.");
                return;
            }
            sd.setMolecularFormula(mf);
        }

        private void setMass(String s) {
            try {
                sd.setMolecularMass(Float.parseFloat(s.trim()));
            } catch (NumberFormatException e) {
                LOG.log(Level.FINE, "Molecular Mass: not a number: " + s, e);
            }
        }
    }
}
