package io.github.steinbeck.seneca.app;

import static io.github.steinbeck.seneca.app.GuiTestSupport.click;
import static io.github.steinbeck.seneca.app.GuiTestSupport.flushEdt;
import static io.github.steinbeck.seneca.app.GuiTestSupport.onEdt;
import static io.github.steinbeck.seneca.app.GuiTestSupport.shot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.FontMetrics;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import io.github.steinbeck.seneca.app.GuiTestSupport.TestApp;
import io.github.steinbeck.seneca.app.dataset.DatasetFrame;
import io.github.steinbeck.seneca.app.run.StructureGenerationPanel;

/**
 * The main window at its default size ({@link MainView#DEFAULT_SIZE}, DV-88) with a dataset open: every
 * tab of the dataset window and of its nested tab panes lies completely inside its tab pane (with the
 * macOS look and feel, 2001's 800x600 hid "Structure Generation" and "Logbook" behind an overflow arrow),
 * the run table's columns are wide enough for typical values, and the best-structure drawing is large
 * enough to read. Meaningful with the native look and feel ({@code -Dseneca.headless=false}); headless
 * (Metal wraps tab rows) it checks the same at least for Metal.
 */
class DefaultLayoutTest {

    /** Tabs that are cut off or hidden at {@code w x h}: "pane title / tab title (bounds)". */
    static List<String> hiddenTabs(TestApp t, int w, int h, String shotName) {
        shot(t.view, w, h, shotName);
        return onEdt(() -> {
            List<String> bad = new ArrayList<>();
            for (Component c : GuiTestSupport.all(t.view)) {
                if (!(c instanceof JTabbedPane tp) || !tp.isShowing() && !visibleInTree(tp)) continue;
                Rectangle area = new Rectangle(0, 0, tp.getWidth(), tp.getHeight());
                for (int i = 0; i < tp.getTabCount(); i++) {
                    Rectangle r = tp.getUI().getTabBounds(tp, i);
                    if (r == null || r.width <= 0 || r.height <= 0 || !area.contains(r))
                        bad.add(tp.getName() + " / " + tp.getTitleAt(i) + " " + r + " in " + area);
                }
            }
            return bad;
        });
    }

    /** True if {@code c} and its ancestors are visible and selected in their tab panes (no peer offscreen). */
    private static boolean visibleInTree(Component c) {
        for (Component p = c; p != null; p = p.getParent()) {
            if (!p.isVisible()) return false;
            if (p.getParent() instanceof JTabbedPane tp && tp.getSelectedComponent() != p) return false;
        }
        return true;
    }

    @Test
    void everyTabFitsAndTheRunTabIsReadableAtTheDefaultSize() {
        try (TestApp t = GuiTestSupport.newApp("default-layout", TestHooks.none())) {
            t.ui.answerFiles(GuiTestSupport.sml("polycarpol").toFile());
            click(GuiTestSupport.find(t.view, ComponentNames.TOOLBAR + ".openAll", JButton.class));
            flushEdt();
            DatasetFrame f = onEdt(() -> t.view.datasetFrames().get(0));
            int w = MainView.DEFAULT_SIZE.width, h = MainView.DEFAULT_SIZE.height;

            // every tab pane, with each of the dataset window's tabs selected in turn (Judges: its sub-tabs)
            List<String> bad = new ArrayList<>();
            int n = onEdt(() -> f.tabs().getTabCount());
            for (int i = 0; i < n; i++) {
                int k = i;
                onEdt(() -> f.tabs().setSelectedIndex(k));
                bad.addAll(hiddenTabs(t, w, h, "layout-default-tab" + (i + 1)));
            }
            assertEquals(List.of(), bad, "tabs cut off or hidden at " + w + "x" + h);

            // 2001 size for comparison (reported only: Metal wraps, Aqua hides tabs)
            onEdt(() -> f.tabs().setSelectedIndex(0));
            System.out.println("DefaultLayoutTest: tabs hidden at 800x600 (2001 size, "
                    + javax.swing.UIManager.getLookAndFeel().getName() + "): " + hiddenTabs(t, 800, 600, "layout-800x600"));

            // run tab: column widths for typical values, a drawing of a readable size
            f.showTab("Structure Generation");
            StructureGenerationPanel p = onEdt(() -> (StructureGenerationPanel) f.tabs().getSelectedComponent());
            shot(t.view, w, h, "layout-default-run");
            String[] samples = {"10", "local10", "local", "AMAZ.COL", "C30H48O2", "Holding Results", "3090784"};
            List<String> narrow = onEdt(() -> {
                JTable table = p.table();
                FontMetrics fm = table.getFontMetrics(table.getFont());
                List<String> l = new ArrayList<>();
                for (int c = 0; c < samples.length; c++) {
                    int need = fm.stringWidth(samples[c]) + 2 * table.getIntercellSpacing().width + 4;
                    int have = table.getColumnModel().getColumn(c).getWidth();
                    if (have < need) l.add(table.getColumnName(c) + ": " + have + " < " + need);
                }
                return l;
            });
            assertEquals(List.of(), narrow, "run table columns too narrow");
            Rectangle best = onEdt(() -> SwingUtilities.convertRectangle(p.bestStructureView(),
                    new Rectangle(p.bestStructureView().getSize()), p));
            assertTrue(best.width >= 400 && best.height >= 220, "best structure drawing " + best);
        }
    }
}
