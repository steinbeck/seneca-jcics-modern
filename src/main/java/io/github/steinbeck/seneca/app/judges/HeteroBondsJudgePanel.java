package io.github.steinbeck.seneca.app.judges;

import java.awt.Dimension;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

import io.github.steinbeck.seneca.app.ComponentNames;
import io.github.steinbeck.seneca.app.dataset.DatasetContext;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.config.JudgeConfiguration;
import io.github.steinbeck.seneca.judges.HeteroBondsJudge;

/**
 * Sub-tab "Heterobond Attachments" (F-56, {@link HeteroBondsJudge}), port of
 * {@code HeteroBondsJudgeConfigurator}: a table titled with the judge name, one row per atom:
 * {@code No.} (0-based, as 2001), {@code Symb.}, {@code Shift} ("-" if none) and the check box column
 * {@code hetero-attached?} ({@link JudgeConfiguration#setHeteroBondsCell}). Autoconfigure only enables the
 * judge (2001 "dummy"). Rows beyond the judge's table show an unticked box; ticking one enlarges the table
 * (DEFAULT, DV-66).
 */
public final class HeteroBondsJudgePanel extends JudgeConfiguratorPanel {

    private final HeteroBondsJudge hbJudge;
    private final Model model = new Model();
    private final JTable table;

    @SuppressWarnings("this-escape")    // Swing: children in the constructor
    public HeteroBondsJudgePanel(DatasetContext ctx) {
        super(ctx, HeteroBondsJudge.class);
        this.hbJudge = (HeteroBondsJudge) judge;
        table = new JTable(model);
        table.setName(ComponentNames.JUDGE_TABLE + "HeteroBondsJudge");
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

    private final class Model extends AbstractTableModel {
        private final String[] names = {"No.", "Symb.", "Shift", "hetero-attached?"};

        @Override
        public int getColumnCount() {
            return names.length;
        }

        @Override
        public String getColumnName(int column) {
            return names[column];
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == JudgeConfiguration.HETERO_COLUMN ? Boolean.class : String.class;
        }

        @Override
        public int getRowCount() {
            return sd.getSetOfNodes() == null ? 0 : sd.getSetOfNodes().length;
        }

        @Override
        public Object getValueAt(int row, int column) {
            HeavyAtom[] atoms = sd.getSetOfNodes();
            boolean[] flags = hbJudge.isHeteroBonded;
            if (column == JudgeConfiguration.HETERO_COLUMN)
                return Boolean.valueOf(flags != null && row < flags.length && flags[row]);
            if (atoms == null || row >= atoms.length || flags == null || flags.length < 1) return "";
            return switch (column) {
                case 0 -> String.valueOf(row);
                case 1 -> atoms[row].symbol;
                default -> atoms[row].shift13C == null ? "-" : String.valueOf(atoms[row].shift13C);
            };
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column == JudgeConfiguration.HETERO_COLUMN;
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            if (!(value instanceof Boolean b)) return;
            JudgeConfiguration.setHeteroBondsCell(sd, row, column, b, ctx.app().compat);
            fireTableDataChanged();
        }
    }
}
