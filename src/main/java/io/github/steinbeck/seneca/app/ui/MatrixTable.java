package io.github.steinbeck.seneca.app.ui;

import java.awt.Component;
import java.awt.FontMetrics;
import java.awt.event.MouseEvent;

import javax.swing.JTable;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;

/**
 * A table with one column per atom (the judges' correlation and symmetry matrices; DV-88). 2001 squeezed
 * every column into the window, so with 30 and more atoms every header was cut to "...". Here each
 * column is at least wide enough for its header (at least {@link #MIN_CELL}), the leading
 * {@code fixedColumns} also for their cell values; when that is wider than the window, the table scrolls
 * horizontally instead of shrinking. Header tooltips show the full column name. EDT only.
 */
public class MatrixTable extends JTable {

    /** Narrowest atom column (a check box and "C32"). */
    public static final int MIN_CELL = 34;
    private static final int PAD = 12;

    private final int fixedColumns;

    public MatrixTable(TableModel model, int fixedColumns) {
        super(model);
        this.fixedColumns = fixedColumns;
        setAutoResizeMode(AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        fitColumns();
    }

    @Override
    public void createDefaultColumnsFromModel() {
        super.createDefaultColumnsFromModel();
        fitColumns();
    }

    @Override
    public void tableChanged(javax.swing.event.TableModelEvent e) {
        super.tableChanged(e);
        // new values in the leading columns (e.g. shifts) may need more room
        if (e != null && e.getFirstRow() != javax.swing.event.TableModelEvent.HEADER_ROW) fitColumns();
    }

    /** Sets every column's minimum and preferred width from its header (and leading columns' values). */
    public void fitColumns() {
        if (getColumnModel() == null || getTableHeader() == null) return;
        FontMetrics hfm = getTableHeader().getFontMetrics(getTableHeader().getFont());
        for (int c = 0; c < getColumnCount(); c++) {
            TableColumn col = getColumnModel().getColumn(c);
            int w = hfm.stringWidth(String.valueOf(col.getHeaderValue())) + PAD;
            if (c < fixedColumns) {
                for (int r = 0; r < getRowCount(); r++) {
                    TableCellRenderer rd = getCellRenderer(r, c);
                    Component comp = prepareRenderer(rd, r, c);
                    w = Math.max(w, comp.getPreferredSize().width + PAD / 2);
                }
            } else {
                w = Math.max(w, MIN_CELL);
            }
            col.setMinWidth(w);
            col.setPreferredWidth(w);
        }
    }

    /** Fills the window when the columns fit, else keeps their widths and scrolls. */
    @Override
    public boolean getScrollableTracksViewportWidth() {
        if (getParent() == null) return true;
        int needed = 0;
        for (int c = 0; c < getColumnCount(); c++) needed += getColumnModel().getColumn(c).getMinWidth();
        return needed <= getParent().getWidth();
    }

    @Override
    protected JTableHeader createDefaultTableHeader() {
        return new JTableHeader(columnModel) {
            @Override
            public String getToolTipText(MouseEvent e) {
                int c = columnAtPoint(e.getPoint());
                return c < 0 ? null : String.valueOf(getColumnModel().getColumn(c).getHeaderValue());
            }
        };
    }
}
