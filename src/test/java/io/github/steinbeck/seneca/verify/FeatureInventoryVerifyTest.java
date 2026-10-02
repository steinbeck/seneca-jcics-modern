package io.github.steinbeck.seneca.verify;

import static io.github.steinbeck.seneca.app.GuiTestSupport.click;
import static io.github.steinbeck.seneca.app.GuiTestSupport.find;
import static io.github.steinbeck.seneca.app.GuiTestSupport.flushEdt;
import static io.github.steinbeck.seneca.app.GuiTestSupport.menuItem;
import static io.github.steinbeck.seneca.app.GuiTestSupport.onEdt;
import static io.github.steinbeck.seneca.app.GuiTestSupport.shot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextPane;
import javax.swing.ListSelectionModel;
import javax.swing.WindowConstants;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import io.github.steinbeck.seneca.app.AppContext;
import io.github.steinbeck.seneca.app.AppPaths;
import io.github.steinbeck.seneca.app.ComponentNames;
import io.github.steinbeck.seneca.app.GuiTestSupport;
import io.github.steinbeck.seneca.app.GuiTestSupport.TestApp;
import io.github.steinbeck.seneca.app.MainFrame;
import io.github.steinbeck.seneca.app.Resources;
import io.github.steinbeck.seneca.app.ScriptedUi;
import io.github.steinbeck.seneca.app.TestHooks;
import io.github.steinbeck.seneca.app.dataset.DatasetFrame;
import io.github.steinbeck.seneca.app.run.AnnealingChartPanel;
import io.github.steinbeck.seneca.app.run.StructureGenerationPanel;
import io.github.steinbeck.seneca.app.structure.StructureDisplayPanel;
import io.github.steinbeck.seneca.model.SenecaDataset;
import io.github.steinbeck.seneca.model.SpectrumSlot;

/**
 * Completeness verification against docs/legacy/feature-inventory.md: exercises KEEP / KEEP-ADAPT items
 * that the G1/G2/integration tests do not drive directly (main frame shell, log-file truncation at start,
 * tab tooltips, close box of a dataset window with "Yes", WinNMR 1D paste, the real 2-second poll timer,
 * chart click toggle, run controls without selection or data, slot ids after "Clear Slot List", shared
 * Logbook). Headless except {@link #mainFrameShell()} (needs a display; hidden frame, never shown).
 */
class FeatureInventoryVerifyTest {

    static DatasetFrame open(TestApp t, String key) {
        t.ui.answerFiles(GuiTestSupport.sml(key).toFile());
        click(menuItem(t.menuBar, "File", "Open", "SENECA Dataset"));
        flushEdt();
        SenecaDataset sd = onEdt(t.app.datasets::current);
        return onEdt(() -> t.app.frameOf(sd));
    }

    static <T extends Component> T named(Component root, String name, Class<T> type) {
        T c = find(root, name, type);
        assertNotNull(c, "no component named " + name);
        return c;
    }

    static List<String> tabsAndTips(JTabbedPane p) {
        return onEdt(() -> {
            List<String> l = new ArrayList<>();
            for (int i = 0; i < p.getTabCount(); i++) l.add(p.getTitleAt(i) + "|" + p.getToolTipTextAt(i));
            return l;
        });
    }

    /** F-10, F-23: frame title, default content size (2001: 800x600, DV-88), centred, close box asks before exiting. */
    @Test
    void mainFrameShell() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display (hidden frame)");
        Path dir = GuiTestSupport.scratchDir("verify-mainframe");
        ScriptedUi ui = new ScriptedUi();
        AppContext app = onEdt(() -> AppContext.create(Resources.load(), ui.services(), AppPaths.under(dir), TestHooks.none()));
        int[] exits = {0};
        app.setExitHook(() -> exits[0]++);
        MainFrame f = onEdt(() -> new MainFrame(app));
        try {
            assertEquals("Seneca", onEdt(f::getTitle));
            assertEquals(io.github.steinbeck.seneca.app.MainView.DEFAULT_SIZE, onEdt(() -> f.getContentPane().getPreferredSize()));
            assertEquals(WindowConstants.DO_NOTHING_ON_CLOSE, onEdt(f::getDefaultCloseOperation));
            assertNotNull(onEdt(f::getJMenuBar));
            java.awt.Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            assertTrue(f.getWidth() <= screen.width && f.getHeight() <= screen.height, "fits the screen");
            assertEquals(screen.x + (screen.width - f.getWidth()) / 2, onEdt(() -> f.getX()));
            assertEquals(screen.y + (screen.height - f.getHeight()) / 2, onEdt(() -> f.getY()));
            ui.answerDialog(JOptionPane.NO_OPTION);
            onEdt(() -> f.dispatchEvent(new WindowEvent(f, WindowEvent.WINDOW_CLOSING)));
            assertEquals("Exit Seneca", ui.lastShown().title());
            assertEquals(0, exits[0]);
            ui.answerDialog(JOptionPane.YES_OPTION);
            onEdt(() -> f.dispatchEvent(new WindowEvent(f, WindowEvent.WINDOW_CLOSING)));
            assertEquals(1, exits[0]);
            assertFalse(onEdt(f::isVisible), "never shown");
        } finally {
            onEdt(f::dispose);
            app.logFiles.close();
        }
    }

    /** F-06: the three log files are truncated when the application starts. */
    @Test
    void logFilesAreTruncatedAtStart() throws Exception {
        Path dir = GuiTestSupport.scratchDir("verify-truncate");
        for (String n : List.of("seneca.log", "seneca.results.sdf", "seneca.annealing.log")) Files.writeString(dir.resolve(n), "old content\n");
        AppContext app = onEdt(() -> AppContext.create(Resources.load(), new ScriptedUi().services(), AppPaths.under(dir), TestHooks.none()));
        try {
            for (String n : List.of("seneca.log", "seneca.results.sdf", "seneca.annealing.log")) assertEquals(0, Files.size(dir.resolve(n)), n);
        } finally {
            app.logFiles.close();
        }
    }

    /** F-13, F-20, F-40..F-48, 7.8: New Dataset by menu, tab tooltips, close box with Yes saves SML, preferences file. */
    @Test
    void datasetWindowTooltipsCloseBoxAndPreferences() throws Exception {
        try (TestApp t = GuiTestSupport.newApp("verify-dataset", TestHooks.none())) {
            click(menuItem(t.menuBar, "File", "New Dataset"));
            SenecaDataset untitled = onEdt(t.app.datasets::current);
            assertTrue(untitled.getName().startsWith("untitled"));
            DatasetFrame nf = onEdt(() -> t.app.frameOf(untitled));
            assertEquals(untitled.getName(), onEdt(nf::getTitle));

            DatasetFrame f = open(t, "pinene");
            assertEquals(new Dimension(500, 300), onEdt(f::getPreferredSize));
            assertEquals(List.of("General Settings|General Settings", "NMR Spectra|NMR Spectra", "Atom Properties|Atom Properties",
                    "Annealing Schedule|Annealing Schedule", "Judges|Judges", "Structure Generation|Structure Generation", "Logbook|LogBook"),
                    tabsAndTips(f.tabs()));
            assertEquals(List.of("1D Carbon|1D Carbon", "DEPT-90|DEPT-90", "DEPT-135|DEPT-135", "H,C-HetCor|H,C-HetCor",
                    "H,C-lr-HetCor|H,C-lr-HetCor", "H,H-COSY|H,H-COSY"), tabsAndTips(named(f, ComponentNames.SPECTRA_TABS, JTabbedPane.class)));
            assertEquals(List.of("H,H-COSY|H,H-COSY", "C,H-long-range Corr.|C,H-long-range Corr.", "Carbon Shifts|Shifts",
                    "Symmetric Atoms Classes|Symmetric Atoms Classes", "Features|Features", "Hybridization|Hybridization",
                    "Heterobond Attachments|Heterobond Attachments"), tabsAndTips(named(f, ComponentNames.JUDGE_TABS, JTabbedPane.class)));

            // 7.8: the Open dialog remembered its directory in the preferences file "Seneca Properties"
            String props = Files.readString(t.dir.resolve("seneca.props"));
            assertTrue(props.startsWith("#Seneca Properties"), props);
            assertTrue(props.contains("filelocation.seneca="), props);

            // F-48 close box (not the button): Cancel keeps it, Yes asks for a file, writes SML, closes
            t.ui.answerDialog(JOptionPane.CANCEL_OPTION);
            onEdt(f::doDefaultCloseAction);
            assertEquals("Close PINEN.COL?", t.ui.lastShown().title());
            assertFalse(f.isClosedByUser());
            Path out = t.dir.resolve("closed-pinene");
            t.ui.answerDialog(JOptionPane.YES_OPTION).answerFiles(out.toFile());
            onEdt(f::doDefaultCloseAction);
            assertTrue(f.isClosedByUser());
            Path written = t.dir.resolve("closed-pinene.sml");
            assertTrue(Files.exists(written));
            String sml = Files.readString(written);
            assertTrue(sml.startsWith("<?xml version=\"1.0\" ?>\n<senecadataset title=\"PINEN.COL\">"), sml.substring(0, 80));
            assertSame(untitled, onEdt(t.app.datasets::current));
            assertEquals(untitled.getName(), onEdt(() -> t.view.statusBar().status(1)));
        }
    }

    /** F-41, 7.6: spectrum table columns and editors, combo items, WinNMR 1D paste, label case. */
    @Test
    void spectrumTableAndWinNmr1dPaste() throws Exception {
        try (TestApp t = GuiTestSupport.newApp("verify-spectra", TestHooks.none())) {
            click(menuItem(t.menuBar, "File", "New Dataset"));
            SenecaDataset sd = onEdt(t.app.datasets::current);
            DatasetFrame f = onEdt(() -> t.app.frameOf(sd));
            JLabel label = named(f, ComponentNames.SPECTRUM_LABEL + "carbon1D", JLabel.class);
            assertEquals("0 signals in Carbon1D.", onEdt(label::getText));
            @SuppressWarnings("unchecked")
            JComboBox<String> combo = named(f, ComponentNames.SPECTRUM_IMPORT + "carbon1D", JComboBox.class);
            assertEquals(List.of("Import peak picking", "Paste WinNMR pp from clipboard"),
                    onEdt(() -> List.of(combo.getItemAt(0), combo.getItemAt(1))));
            assertEquals(2, onEdt(combo::getItemCount));
            t.ui.clipboardText = "XWIN-NMR 2.6\nPeak Picking results:\n"
                    + "   1   1201   15123.45   144.01   2345678.0   100.0\n"
                    + "   2   3044   11690.12   116.20  -1172839.0   -50.0\n";
            onEdt(() -> combo.setSelectedIndex(1));
            flushEdt();
            assertEquals(0, onEdt(combo::getSelectedIndex), "combo returns to 'Import peak picking'");
            var spec = sd.spectrum(SpectrumSlot.CARBON1D);
            assertEquals(2, spec.size());
            assertEquals(144.01f, spec.getSignal(0).shift[0]);
            assertEquals(100.0f, spec.getSignal(0).intensity);
            assertEquals(1, spec.getSignal(0).phase);
            assertEquals(116.2f, spec.getSignal(1).shift[0]);
            assertEquals(-50.0f, spec.getSignal(1).intensity);
            assertEquals(2, spec.getSignal(1).phase);
            assertEquals("2 signals in Carbon1D.", onEdt(label::getText));
            JTable table = named(f, ComponentNames.SPECTRUM_TABLE + "carbon1D", JTable.class);
            assertEquals(List.of("No.", "13C", "Intensity", "Phase"), onEdt(() -> {
                List<String> l = new ArrayList<>();
                for (int c = 0; c < table.getColumnCount(); c++) l.add(table.getColumnName(c));
                return l;
            }));
            assertEquals("NEGATIVE", onEdt(() -> table.getValueAt(1, 3)));
            DefaultCellEditor ed = (DefaultCellEditor) onEdt(() -> table.getColumnModel().getColumn(3).getCellEditor());
            JComboBox<?> phase = (JComboBox<?>) ed.getComponent();
            assertEquals(List.of("NONE", "POSITIVE", "NEGATIVE"), onEdt(() -> List.of(phase.getItemAt(0), phase.getItemAt(1), phase.getItemAt(2))));
            f.showTab("NMR Spectra");
            shot(t.view, 1000, 700, "verify-spectra-winnmr-1d");
        }
    }

    /** F-46, F-60..F-70, F-47: run tab with the real 2-s timer, chart click, Log Annealing Data, Summarize, Reset, Clear. */
    @Test
    void runTabWithTheRealPollTimer() throws Exception {
        try (TestApp t = GuiTestSupport.newApp("verify-run", new TestHooks(1L, 30_000))) {
            DatasetFrame f = open(t, "pinene");
            DatasetFrame other = open(t, "eurabidiol");
            // F-47 / DV-80: every Logbook tab shows the one shared document
            JTextPane lb1 = named(f, ComponentNames.LOGBOOK, JTextPane.class);
            JTextPane lb2 = named(other, ComponentNames.LOGBOOK, JTextPane.class);
            assertSame(t.app.logbook.document(), onEdt(lb1::getDocument));
            assertSame(t.app.logbook.document(), onEdt(lb2::getDocument));
            onEdt(() -> f.raise());

            // configure: Autoassignment, Autoconfigure HMBC and HOSE
            click(named(f, ComponentNames.AUTOASSIGN, JButton.class));
            click(named(f, ComponentNames.JUDGE_AUTOCONFIGURE + "HMBCJudge", JButton.class));
            click(named(f, ComponentNames.JUDGE_AUTOCONFIGURE + "HOSECodeJudge", JButton.class));
            f.showTab("Structure Generation");
            StructureGenerationPanel p = onEdt(() -> {
                for (Component c : GuiTestSupport.all(f)) if (c instanceof StructureGenerationPanel s) return s;
                return null;
            });
            assertNotNull(p);
            JTable table = named(f, ComponentNames.RUN_TABLE, JTable.class);
            assertEquals(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION, onEdt(() -> table.getSelectionModel().getSelectionMode()));
            assertTrue(onEdt(() -> p.timer().isRunning()));
            assertEquals(2000, onEdt(() -> p.timer().getDelay()));

            // without slots, selection or data: nothing happens, nothing is written
            click(named(f, ComponentNames.RUN_START, JButton.class));
            click(named(f, ComponentNames.RUN_STOP, JButton.class));
            click(named(f, ComponentNames.RUN_RESET, JButton.class));
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            onEdt(() -> p.setStdout(new PrintStream(stdout, true, StandardCharsets.UTF_8)));
            click(named(f, ComponentNames.RUN_LOG_ANNEALING, JButton.class));
            assertEquals("", stdout.toString(StandardCharsets.UTF_8));
            int windows = t.ui.windows.size();
            click(named(f, ComponentNames.RUN_SUMMARIZE, JButton.class));
            assertEquals(windows + 1, t.ui.windows.size());
            assertTrue(t.ui.lastWindow().title.startsWith("Structure Display - "), t.ui.lastWindow().title);   // dataset name in the title (DV-88)
            assertEquals(0, ((StructureDisplayPanel) t.ui.lastWindow().content).tileList().size());

            // two slots, Clear Slot List, add again: ids and idle row (F-60, F-61, F-63)
            click(named(f, ComponentNames.RUN_ADD_SLOT, JButton.class));
            click(named(f, ComponentNames.RUN_ADD_SLOT, JButton.class));
            assertEquals(List.of("2", "local1", "local", "none", "none", "idle", "none"), row(table, 1));
            click(named(f, ComponentNames.RUN_CLEAR, JButton.class));
            t.app.awaitControl(30_000);
            flushEdt();
            assertEquals(0, onEdt(table::getRowCount));
            click(named(f, ComponentNames.RUN_ADD_SLOT, JButton.class));
            List<String> afterClear = row(table, 0);
            assertEquals("local0", afterClear.get(1), "slot id after Clear Slot List (2001: local0)");
            assertTrue(onEdt(() -> p.timer().isRunning()), "polling continues after clear (2001 stopped)");

            // start row 1 and let the real timer poll (F-64, F-69)
            onEdt(() -> table.setRowSelectionInterval(0, 0));
            click(named(f, ComponentNames.RUN_START, JButton.class));
            assertTrue(GuiTestSupport.waitFor(() -> onEdt(() -> p.bestStructureTitle()).equals("Best structure of no.1"), 30_000),
                    "the 2-s timer shows the best structure");
            assertTrue(GuiTestSupport.waitFor(() -> !onEdt(() -> p.model().slot(0).isRunning()), 60_000));
            assertTrue(GuiTestSupport.waitFor(() -> !"Running".equals(onEdt(() -> table.getValueAt(0, 5))), 10_000));
            String eval = onEdt(() -> p.evaluationPane().getText());
            assertTrue(eval.contains("<b>Score: "), eval);
            assertEquals("Evaluation of no. 1", onEdt(p::evaluationTitle));
            assertEquals("PINEN.COL", onEdt(() -> table.getValueAt(0, 3)));
            assertEquals("C10H16", onEdt(() -> table.getValueAt(0, 4)));

            // F-70: a mouse click toggles score / kT
            AnnealingChartPanel chart = p.chart();
            assertTrue(GuiTestSupport.waitFor(() -> onEdt(() -> chart.data().hasData() && chart.data().series(0).size() > 0), 10_000));
            assertEquals("Score", onEdt(() -> chart.getChart().getXYPlot().getRangeAxis().getLabel()));
            assertEquals("Iteration", onEdt(() -> chart.getChart().getXYPlot().getDomainAxis().getLabel()));
            onEdt(() -> chart.dispatchEvent(new MouseEvent(chart, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 30, 30, 1, false)));
            assertEquals("kT", onEdt(() -> chart.getChart().getXYPlot().getRangeAxis().getLabel()));
            onEdt(() -> chart.dispatchEvent(new MouseEvent(chart, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 30, 30, 1, false)));
            assertEquals("Score", onEdt(() -> chart.getChart().getXYPlot().getRangeAxis().getLabel()));
            List<String> seriesNames = onEdt(() -> {
                List<String> l = new ArrayList<>();
                for (int i = 0; i < chart.dataset().getSeriesCount(); i++) l.add(String.valueOf(chart.dataset().getSeriesKey(i)));
                return l;
            });
            assertEquals(List.of("host 1", "max. Score"), seriesNames);
            shot(t.view, 1000, 700, "verify-run-polled-by-timer");

            // F-68: Log Annealing Data to stdout and seneca.annealing.log (also seneca.log)
            click(named(f, ComponentNames.RUN_LOG_ANNEALING, JButton.class));
            String printed = stdout.toString(StandardCharsets.UTF_8);
            assertTrue(printed.startsWith("Iteration; Server 1; \n"), printed.substring(0, Math.min(60, printed.length())));
            String annealing = Files.readString(t.dir.resolve("seneca.annealing.log"));
            assertEquals(printed, annealing);
            assertTrue(Files.readString(t.dir.resolve("seneca.log")).contains("Iteration; Server 1; "));
            assertTrue(Files.readString(t.dir.resolve("seneca.results.sdf")).contains("$$$$"));

            // F-67: Summarize Result tiles titled "Score s", tooltip with the evaluation and the host
            click(named(f, ComponentNames.RUN_SUMMARIZE, JButton.class));
            StructureDisplayPanel sdp = (StructureDisplayPanel) t.ui.lastWindow().content;
            assertFalse(sdp.tileList().isEmpty());
            var tile = sdp.tileList().get(0);
            assertTrue(tile.title().startsWith("Score "), tile.title());
            String tip = onEdt(() -> tile.getToolTipText());
            assertTrue(tip.startsWith("<html><b>Score ") && tip.contains("Result from host 1"), tip);
            assertEquals(new Dimension(300, 200), onEdt(() -> tile.getPreferredSize()));

            // F-47: the Logbook got the engine messages
            String logbook = onEdt(t.app.logbook::text);
            assertTrue(logbook.contains("start_kT"), logbook.substring(0, Math.min(200, logbook.length())));

            // F-66: Reset selected drops the chart data and returns the row to idle
            click(named(f, ComponentNames.RUN_RESET, JButton.class));
            t.app.awaitControl(30_000);
            flushEdt();
            assertFalse(onEdt(() -> chart.data().hasData()));
            assertEquals("idle", onEdt(() -> table.getValueAt(0, 5)));
        }
    }

    /** F-12, F-33, DV-73: activating (selecting) a dataset window makes its dataset current (status bar, Save). */
    @Test
    void activatingAWindowMakesItsDatasetCurrent() throws Exception {
        try (TestApp t = GuiTestSupport.newApp("verify-activate", TestHooks.none())) {
            DatasetFrame pin = open(t, "pinene");
            DatasetFrame eur = open(t, "eurabidiol");
            assertSame(eur.dataset(), onEdt(t.app.datasets::current));
            onEdt(() -> pin.raise());
            flushEdt();
            System.out.println("VERIFY after raise(): selected pinene=" + onEdt(pin::isSelected) + " eurabidiol=" + onEdt(eur::isSelected)
                    + " current=" + onEdt(() -> t.app.datasets.current().getName()) + " status=" + onEdt(() -> t.view.statusBar().status(1)));
            // JInternalFrame.setSelected(true) is a no-op while the window is not showing (tests): deliver the
            // activation event Swing sends when the user clicks the window
            onEdt(() -> {
                for (javax.swing.event.InternalFrameListener l : pin.getInternalFrameListeners())
                    l.internalFrameActivated(new javax.swing.event.InternalFrameEvent(pin, javax.swing.event.InternalFrameEvent.INTERNAL_FRAME_ACTIVATED));
            });
            flushEdt();
            assertSame(pin.dataset(), onEdt(t.app.datasets::current), "selected window's dataset is current");
            assertEquals("PINEN.COL", onEdt(() -> t.view.statusBar().status(1)));
        }
    }

    static List<String> row(JTable table, int r) {
        return onEdt(() -> {
            List<String> l = new ArrayList<>();
            for (int c = 0; c < table.getColumnCount(); c++) l.add(String.valueOf(table.getValueAt(r, c)));
            return l;
        });
    }

    /** F-30, F-90: the empty "Structure Display" window: buttons in legacy order plus the additions. */
    @Test
    void structureDisplayButtons() throws Exception {
        try (TestApp t = GuiTestSupport.newApp("verify-structdisp", TestHooks.none())) {
            click(menuItem(t.menuBar, "View", "Current Structures"));
            StructureDisplayPanel p = (StructureDisplayPanel) t.ui.lastWindow().content;
            List<String> buttons = onEdt(() -> {
                List<String> l = new ArrayList<>();
                for (Component c : GuiTestSupport.all(p)) if (c instanceof JButton b && b.getText() != null && !b.getText().isEmpty()) l.add(b.getText());
                return l;
            });
            assertEquals(List.of("Load MDL", "Load LUCY Result", "Save MDL", "Save SDF", "Copy", "Close"), buttons);
            assertTrue(onEdt(() -> p.getPreferredSize().width) >= 600);
            assertNotNull(onEdt(() -> menuItem(t.menuBar, "View", "Current Structures").getIcon()), "strucDisp.gif on the menu item");
            assertNotNull(onEdt(() -> menuItem(t.menuBar, "File", "New Dataset").getIcon()), "new.gif on the menu item");
            assertNotNull(onEdt(() -> menuItem(t.menuBar, "File", "Open", "SENECA Dataset").getIcon()), "open.gif");
            assertNotNull(onEdt(() -> menuItem(t.menuBar, "File", "Save", "SENECA Dataset").getIcon()), "save.gif");
        }
    }
}
