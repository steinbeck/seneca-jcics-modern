package io.github.steinbeck.seneca.app.dataset;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.border.Border;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import javax.swing.table.AbstractTableModel;

import io.github.steinbeck.seneca.app.ComponentNames;
import io.github.steinbeck.seneca.app.ui.OptionPanes;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.config.AtomProperties;
import io.github.steinbeck.seneca.model.NMRSignal;
import io.github.steinbeck.seneca.model.NMRSpectrum;
import io.github.steinbeck.seneca.model.SenecaDataset;
import io.github.steinbeck.seneca.model.SpectrumSlot;

/**
 * Tab "Atom Properties" (F-42), port of {@code AtomPropertyPanel}: the tables "Atom Properties" and
 * "Carbon Shifts" side by side (etched titled borders, 200x400 each), the button "Autoassignment" and
 * the H balance message below. All logic is in the core ({@link AtomProperties}, DEFAULT mode).
 *
 * <ul>
 *   <li>"Atom Properties": {@code No.} (1-based), {@code Symbol}, {@code Hydrogens} (editable, 0..4,
 *       "undefined" for -1; anything else is ignored), {@code Assigned 13C Shift} (editable float, "-" if
 *       none; port: text that is not a number is ignored, 2001 threw on the event thread).</li>
 *   <li>"Carbon Shifts": rows = 1D carbon signals; {@code [ppm]}, {@code Int.} (intensity x normalize
 *       factor), {@code DEPT-90}, {@code Int.}, {@code DEPT-135}, {@code Int.}; the k-th DEPT signal in row
 *       k, "-" beyond the list. Read-only (2001: editable cells whose edits were discarded; DV-85).</li>
 *   <li>"Autoassignment": {@code AtomProperties.autoassign(sd, DEFAULT)}; when the core refuses (1D
 *       intensities that cannot be normalized, DV-42) a message says so (DV-83).</li>
 *   <li>Message: {@code "HCount in molecular formula: X - Sum of HCounts in this table: Y"}, red if X != Y,
 *       dark green if equal. {@link AtomProperties#balance} runs where 2001's {@code updateMessageLabel()}
 *       ran (every paint of the table and every edit; its side effect sets undefined H counts to 0 when
 *       balanced), and also when the window opens, after Autoassignment and on dataset changes.</li>
 * </ul>
 * Edits and Autoassignment fire the dataset's change event so that the other tabs (judge tables showing
 * shifts) refresh.
 */
public final class AtomPropertiesPanel extends JPanel {

    private static final Logger LOG = Logger.getLogger(AtomPropertiesPanel.class.getName());

    /** Colour of the balanced message (2001: {@code new Color(Color.green.getRGB()).darker()}). */
    public static final Color BALANCED = new Color(Color.green.getRGB()).darker();
    public static final Color UNBALANCED = Color.red;

    private final DatasetContext ctx;
    private final SenecaDataset sd;
    private final AtomsModel atomsModel = new AtomsModel();
    private final ShiftsModel shiftsModel = new ShiftsModel();
    private final JTable atomsTable;
    private final JTable shiftTable;
    private final JButton autoButton = new JButton("Autoassignment");
    private final JLabel messageLabel = new JLabel();

    @SuppressWarnings("this-escape")    // Swing: listeners and children in the constructor
    public AtomPropertiesPanel(DatasetContext ctx) {
        super(new BorderLayout());
        this.ctx = ctx;
        this.sd = ctx.dataset();
        shiftTable = new JTable(shiftsModel);
        atomsTable = new JTable(atomsModel) {
            @Override
            protected void paintComponent(Graphics g) {
                updateMessageLabel();      // 2001: the "Hydrogens" cell renderer called updateMessageLabel()
                super.paintComponent(g);
            }
        };
        atomsTable.setName(ComponentNames.ATOM_TABLE);
        shiftTable.setName(ComponentNames.CARBON_SHIFT_TABLE);
        autoButton.setName(ComponentNames.AUTOASSIGN);
        messageLabel.setName(ComponentNames.H_BALANCE);

        Border etched = BorderFactory.createEtchedBorder();
        Box centerBox = new Box(BoxLayout.X_AXIS);
        JScrollPane atoms = new JScrollPane(atomsTable);
        atoms.setColumnHeaderView(atomsTable.getTableHeader());     // headers also before addNotify (offscreen renderings)
        atoms.setBorder(BorderFactory.createTitledBorder(etched, "Atom Properties"));
        atoms.setPreferredSize(new Dimension(200, 400));
        JScrollPane shifts = new JScrollPane(shiftTable);
        shifts.setColumnHeaderView(shiftTable.getTableHeader());     // headers also before addNotify (offscreen renderings)
        shifts.setBorder(BorderFactory.createTitledBorder(etched, "Carbon Shifts"));
        shifts.setPreferredSize(new Dimension(200, 400));
        centerBox.add(atoms);
        centerBox.add(shifts);

        autoButton.addActionListener(e -> autoassign());
        messageLabel.setBorder(BorderFactory.createEmptyBorder(0, 30, 0, 0));
        Box southBox = new Box(BoxLayout.X_AXIS);
        southBox.add(autoButton);
        southBox.add(messageLabel);
        add(centerBox, BorderLayout.CENTER);
        add(southBox, BorderLayout.SOUTH);

        Runnable refresh = () -> {
            atomsModel.fireTableDataChanged();
            shiftsModel.fireTableDataChanged();
            updateMessageLabel();
        };
        sd.addChangeListener(refresh);
        ctx.frame().addInternalFrameListener(new InternalFrameAdapter() {
            @Override
            public void internalFrameClosed(InternalFrameEvent e) {
                sd.removeChangeListener(refresh);
            }
        });
        updateMessageLabel();
    }

    public JTable atomsTable() {
        return atomsTable;
    }

    public JTable shiftTable() {
        return shiftTable;
    }

    public JLabel messageLabel() {
        return messageLabel;
    }

    /** "Autoassignment". */
    private void autoassign() {
        boolean ok;
        try {
            ok = sd.getSetOfNodes() != null && AtomProperties.autoassign(sd, ctx.app().compat);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Autoassignment failed", e);
            ok = false;
        }
        sd.fireChange();
        if (!ok) {
            ctx.app().ui.dialogs().show(this, "Autoassignment", OptionPanes.message(
                    "Autoassignment failed.\nThe 1D carbon spectrum must contain the carbon signals with a positive"
                            + "\nintensity each (and the dataset a molecular formula).", JOptionPane.WARNING_MESSAGE));
        }
    }

    /** 2001 {@code updateMessageLabel()}: the H balance (with its side effect) and the label. */
    private void updateMessageLabel() {
        if (sd.getSetOfNodes() == null || sd.getSetOfNodes().length == 0 || sd.getMolecularFormula() == null
                || sd.getMolecularFormula().isEmpty()) {
            if (!messageLabel.getText().isEmpty()) messageLabel.setText("");
            return;
        }
        AtomProperties.Balance b = AtomProperties.balance(sd);
        Color c = b.balanced() ? BALANCED : UNBALANCED;
        if (!c.equals(messageLabel.getForeground())) messageLabel.setForeground(c);
        if (!b.message().equals(messageLabel.getText())) messageLabel.setText(b.message());
    }

    private final class AtomsModel extends AbstractTableModel {
        private final String[] names = {"No.", "Symbol", "Hydrogens", "Assigned 13C Shift"};

        @Override
        public int getColumnCount() {
            return names.length;
        }

        @Override
        public String getColumnName(int column) {
            return names[column];
        }

        @Override
        public int getRowCount() {
            return sd.getSetOfNodes() == null ? 0 : sd.getSetOfNodes().length;
        }

        @Override
        public Object getValueAt(int row, int column) {
            HeavyAtom[] atoms = sd.getSetOfNodes();
            if (atoms == null || row >= atoms.length) return "";
            return switch (column) {
                case 0 -> String.valueOf(row + 1);
                case 1 -> atoms[row].symbol;
                case 2 -> AtomProperties.hydrogensText(atoms[row]);
                default -> AtomProperties.shiftText(atoms[row]);
            };
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column > 1;
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            if (sd.getSetOfNodes() == null || row >= sd.getSetOfNodes().length) return;
            String s = value == null ? "" : value.toString().trim();
            if (column == 2) {
                AtomProperties.setHydrogens(sd, row, s);
            } else if (column == 3) {
                try {
                    AtomProperties.setAssignedShift(sd, row, s);
                } catch (NumberFormatException e) {
                    LOG.log(Level.FINE, "Assigned 13C Shift: not a number: " + s, e);
                }
            }
            sd.fireChange();
        }
    }

    private final class ShiftsModel extends AbstractTableModel {
        private final String[] names = {"[ppm]", "Int.", "DEPT-90", "Int.", "DEPT-135", "Int."};

        @Override
        public int getColumnCount() {
            return names.length;
        }

        @Override
        public String getColumnName(int column) {
            return names[column];
        }

        @Override
        public int getRowCount() {
            return sd.spectrum(SpectrumSlot.CARBON1D).size();
        }

        @Override
        public Object getValueAt(int row, int column) {
            return switch (column) {
                case 0, 1 -> cell(sd.spectrum(SpectrumSlot.CARBON1D), row, column == 1);
                case 2, 3 -> cell(sd.spectrum(SpectrumSlot.DEPT90), row, column == 3);
                default -> cell(sd.spectrum(SpectrumSlot.DEPT135), row, column == 5);
            };
        }

        private String cell(NMRSpectrum s, int row, boolean intensity) {
            if (row >= s.size()) return "-";
            NMRSignal g = s.getSignal(row);
            if (intensity) return String.valueOf(g.intensity * g.normalizeFactor);
            return g.shift.length == 0 ? "-" : String.valueOf(g.shift[0]);
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    }
}
