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
import io.github.steinbeck.seneca.judges.HHCOSYJudge;
import io.github.steinbeck.seneca.judges.HMBCJudge;
import io.github.steinbeck.seneca.judges.TwoDSpectrumJudge;

/**
 * Sub-tabs "H,H-COSY" (F-50, {@link HHCOSYJudge}) and "C,H-long-range Corr." (F-51, {@link HMBCJudge});
 * port of {@code TwoDSpectrumJudgeConfigurator} and its two subclasses. Centre: a read-only table
 * titled with the judge name; one row per assigned correlation {@code assignment[i][j][j]} (in i, j
 * order), columns {@code No.} (atom i+1) and {@code 1..n} ({@code assignment[i][j][col-1]}, check boxes).
 * Autoconfigure runs the H,H-COSY or HMBC assigner (core); a failed assignment disables the judge (DV-46);
 * "Report Judgeconfig" opens the judge's {@code toString()} in "Judge Configuration Report".
 */
public final class TwoDJudgePanel extends JudgeConfiguratorPanel {

    private final TwoDSpectrumJudge twoD;
    private final Model model = new Model();
    private final JTable table;

    @SuppressWarnings("this-escape")    // Swing: children in the constructor
    public TwoDJudgePanel(DatasetContext ctx, Class<? extends TwoDSpectrumJudge> type) {
        super(ctx, type);
        this.twoD = (TwoDSpectrumJudge) judge;
        table = new MatrixTable(model, 1);   // one column per atom: scrolls instead of "..." headers (DV-88)
        table.setName(ComponentNames.JUDGE_TABLE + type.getSimpleName());
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
        @Override
        public int getColumnCount() {
            return twoD.assignment == null ? 0 : twoD.assignment.length + 1;
        }

        @Override
        public String getColumnName(int column) {
            return column == 0 ? "No." : Integer.toString(column);
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 0 ? Integer.class : Boolean.class;
        }

        @Override
        public int getRowCount() {
            boolean[][][] a = twoD.assignment;
            if (a == null) return 0;
            int rc = 0;
            for (int i = 0; i < a.length; i++)
                for (int j = 0; j < a.length; j++)
                    if (a[i][j][j]) rc++;
            return rc;
        }

        @Override
        public Object getValueAt(int row, int column) {
            boolean[][][] a = twoD.assignment;
            if (a == null) return null;
            int rc = 0;
            for (int i = 0; i < a.length; i++) {
                for (int j = 0; j < a.length; j++) {
                    if (!a[i][j][j]) continue;
                    if (rc++ == row) return column == 0 ? Integer.valueOf(i + 1) : Boolean.valueOf(column - 1 < a.length && a[i][j][column - 1]);
                }
            }
            return null;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    }
}
