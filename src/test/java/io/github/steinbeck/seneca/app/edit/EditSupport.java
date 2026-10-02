package io.github.steinbeck.seneca.app.edit;

import static io.github.steinbeck.seneca.app.GuiTestSupport.click;
import static io.github.steinbeck.seneca.app.GuiTestSupport.find;
import static io.github.steinbeck.seneca.app.GuiTestSupport.flushEdt;
import static io.github.steinbeck.seneca.app.GuiTestSupport.menuItem;
import static io.github.steinbeck.seneca.app.GuiTestSupport.onEdt;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.Component;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.text.JTextComponent;

import io.github.steinbeck.seneca.app.GuiTestSupport;
import io.github.steinbeck.seneca.app.GuiTestSupport.TestApp;
import io.github.steinbeck.seneca.app.dataset.DatasetFrame;
import io.github.steinbeck.seneca.model.SenecaDataset;

/**
 * Helpers of the G1 tests (dataset editing UI): open a dataset through File &gt; Open with a scripted file
 * dialog, edit table cells the way a user does (start the cell editor, put the value into the editor
 * component, commit), find named controls. Everything runs on the EDT; nothing is shown.
 */
final class EditSupport {

    private EditSupport() {
    }

    /** File &gt; Open &gt; SENECA Dataset with {@code file}; returns the new dataset window. */
    static DatasetFrame open(TestApp t, Path file) {
        t.ui.answerFiles(file.toFile());
        click(menuItem(t.menuBar, "File", "Open", "SENECA Dataset"));
        flushEdt();
        SenecaDataset sd = onEdt(t.app.datasets::current);
        assertNotNull(sd, "no dataset opened from " + file);
        DatasetFrame f = onEdt(() -> t.app.frameOf(sd));
        assertNotNull(f);
        return f;
    }

    /** The bundled dataset {@code key} ({@code legacy/original/sml}). */
    static DatasetFrame open(TestApp t, String key) {
        return open(t, GuiTestSupport.sml(key));
    }

    /** The component named {@code name} below {@code root}; fails if absent. */
    static <T extends Component> T named(Component root, String name, Class<T> type) {
        T c = find(root, name, type);
        assertNotNull(c, "no component named " + name);
        return c;
    }

    static void press(Component root, String name) {
        click(named(root, name, AbstractButton.class));
    }

    /**
     * Edits cell ({@code row}, {@code column}) like a user: {@code editCellAt}, the value into the editor
     * component (text field: text; combo box: selected item; check box: selected), {@code stopCellEditing}.
     * Returns whether the editor accepted the value (a refused value is cancelled).
     */
    static boolean edit(JTable table, int row, int column, Object value) {
        return onEdt(() -> {
            if (!table.editCellAt(row, column)) throw new AssertionError("cell " + row + "/" + column + " is not editable");
            Component c = table.getEditorComponent();
            switch (c) {
                case JTextComponent tc -> tc.setText(String.valueOf(value));
                case JComboBox<?> cb -> cb.setSelectedItem(value);
                case JCheckBox cb -> cb.setSelected((Boolean) value);
                default -> throw new AssertionError("unexpected editor " + c);
            }
            if (!table.isEditing()) return true;          // a combo box editor commits on selection
            boolean ok = table.getCellEditor().stopCellEditing();
            if (!ok) table.getCellEditor().cancelCellEditing();
            return ok;
        });
    }

    /** Text of a cell as the table shows it ({@code getValueAt}). */
    static String cell(JTable table, int row, int column) {
        return onEdt(() -> String.valueOf(table.getValueAt(row, column)));
    }

    /** Column titles of a table. */
    static List<String> columns(JTable table) {
        return onEdt(() -> {
            List<String> l = new ArrayList<>();
            for (int c = 0; c < table.getColumnCount(); c++) l.add(table.getColumnName(c));
            return l;
        });
    }

    static int rows(JTable table) {
        return onEdt(table::getRowCount);
    }

    /** Selects the tab titled {@code title} of the window and sub-tab {@code subTitle} (may be null). */
    static void showTab(DatasetFrame f, String title) {
        onEdt(() -> f.showTab(title));
    }

    static boolean selected(JCheckBox b) {
        return onEdt(b::isSelected);
    }
}
