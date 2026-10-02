package io.github.steinbeck.seneca.app.judges;

import java.awt.Dimension;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

import io.github.steinbeck.seneca.app.ComponentNames;
import io.github.steinbeck.seneca.app.dataset.DatasetContext;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.config.JudgeConfiguration;
import io.github.steinbeck.seneca.judges.HybridizationJudge;

/**
 * Sub-tab "Hybridization" (F-55, {@link HybridizationJudge}), port of
 * {@code HybridizationJudgeConfigurator}: a table titled with the judge name, one row per atom:
 * {@code No.} (0-based, as 2001), {@code Symb.}, {@code Shift} ("-" if none), {@code sp3}, {@code sp2},
 * {@code sp} (the stored scores); the three sp columns are edited with a combo box 1.0, 0.9 ... 0.0
 * ({@link JudgeConfiguration#setHybridizationCell}, stored x100). Autoconfigure only enables the judge
 * (2001 "dummy"). Rows beyond the judge's table (the formula grew after the window opened) show empty
 * cells; an edit there enlarges the table (DEFAULT, DV-66).
 */
public final class HybridizationJudgePanel extends JudgeConfiguratorPanel {

    private final HybridizationJudge hyJudge;
    private final Model model = new Model();
    private final JTable table;

    @SuppressWarnings("this-escape")    // Swing: children in the constructor
    public HybridizationJudgePanel(DatasetContext ctx) {
        super(ctx, HybridizationJudge.class);
        this.hyJudge = (HybridizationJudge) judge;
        table = new JTable(model);
        table.setName(ComponentNames.JUDGE_TABLE + "HybridizationJudge");
        installEditors();
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

    private void installEditors() {
        JComboBox<String> comboBox = new JComboBox<>(JudgeConfiguration.HYBRIDIZATION_VALUES.toArray(new String[0]));
        for (int c = JudgeConfiguration.HYB_COLUMN_SP3; c <= JudgeConfiguration.HYB_COLUMN_SP; c++)
            table.getColumnModel().getColumn(c).setCellEditor(new DefaultCellEditor(comboBox));
    }

    @Override
    protected void refresh() {
        if (refreshTable(table, model)) installEditors();
    }

    private final class Model extends AbstractTableModel {
        private final String[] names = {"No.", "Symb.", "Shift", "sp3", "sp2", "sp"};

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
            int[][] scores = hyJudge.scores;
            if (atoms == null || row >= atoms.length || scores == null || scores.length < 1) return "";
            return switch (column) {
                case 0 -> String.valueOf(row);
                case 1 -> atoms[row].symbol;
                case 2 -> atoms[row].shift13C == null ? "-" : String.valueOf(atoms[row].shift13C);
                default -> row < scores.length ? Integer.toString(scores[row][column - JudgeConfiguration.HYB_COLUMN_SP3]) : "";
            };
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column >= JudgeConfiguration.HYB_COLUMN_SP3;
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            if (value == null) return;
            JudgeConfiguration.setHybridizationCell(sd, row, column, value.toString(), ctx.app().compat);
            fireTableDataChanged();
        }
    }
}
