package io.github.steinbeck.seneca.app.judges;

import java.awt.Dimension;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

import io.github.steinbeck.seneca.app.ComponentNames;
import io.github.steinbeck.seneca.app.dataset.DatasetContext;
import io.github.steinbeck.seneca.app.ui.MatrixTable;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.judges.SymmetryJudge;

/**
 * Sub-tab "Symmetric Atoms Classes" (F-53, {@link SymmetryJudge}), port of
 * {@code SymmetryJudgeConfigurator}: a read-only table titled with the judge name, one row per symmetry
 * class: {@code No.} (class number, 1-based), {@code Symb.}, {@code Shift} (shift of the class's first
 * member), then one check-box column per atom titled {@code <symbol><index+1>}. Port (DV-85): {@code Symb.}
 * is the symbol of the class's first member (2001 showed the symbol of the atom whose index equals the
 * class number). Autoconfigure builds the classes from equal assigned shifts (core).
 */
public final class SymmetryJudgePanel extends JudgeConfiguratorPanel {

    private final SymmetryJudge symJudge;
    private final Model model = new Model();
    private final JTable table;

    @SuppressWarnings("this-escape")    // Swing: children in the constructor
    public SymmetryJudgePanel(DatasetContext ctx) {
        super(ctx, SymmetryJudge.class);
        this.symJudge = (SymmetryJudge) judge;
        table = new MatrixTable(model, 3);   // one column per atom: scrolls instead of "..." headers (DV-88)
        table.setName(ComponentNames.JUDGE_TABLE + "SymmetryJudge");
        Box centerBox = new Box(BoxLayout.X_AXIS);
        JScrollPane scrollpane = new JScrollPane(table);
        scrollpane.setColumnHeaderView(table.getTableHeader());     // headers also before addNotify (offscreen renderings)
        scrollpane.setBorder(titledBorder());
        scrollpane.setPreferredSize(new Dimension(200, 400));
        centerBox.add(scrollpane);
        setCenter(centerBox);
    }

    public JTable table() {
        return table;
    }

    @Override
    protected void refresh() {
        refreshTable(table, model);
    }

    private HeavyAtom[] atoms() {
        return sd.getSetOfNodes();
    }

    private final class Model extends AbstractTableModel {
        @Override
        public int getColumnCount() {
            return atoms() == null ? 0 : atoms().length + 3;
        }

        @Override
        public int getRowCount() {
            return atoms() == null ? 0 : symJudge.getSymmetryClassCount();
        }

        @Override
        public String getColumnName(int column) {
            return switch (column) {
                case 0 -> "No.";
                case 1 -> "Symb.";
                case 2 -> "Shift";
                default -> atoms()[column - 3].symbol + (column - 2);
            };
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column < 3 ? String.class : Boolean.class;
        }

        @Override
        public Object getValueAt(int row, int column) {
            HeavyAtom[] atoms = atoms();
            boolean[] members = symJudge.getSymmetryClass(row);
            if (atoms == null || members == null) return column < 3 ? "" : Boolean.FALSE;
            int first = symJudge.getClassMember(row, 1);
            return switch (column) {
                case 0 -> String.valueOf(row + 1);
                case 1 -> first >= 0 && first < atoms.length ? atoms[first].symbol : "";
                case 2 -> first >= 0 && first < atoms.length ? String.valueOf(atoms[first].shift13C) : "";
                default -> Boolean.valueOf(column - 3 < members.length && members[column - 3]);
            };
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    }
}
