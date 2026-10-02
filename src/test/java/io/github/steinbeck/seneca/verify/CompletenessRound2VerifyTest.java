package io.github.steinbeck.seneca.verify;

import static io.github.steinbeck.seneca.app.GuiTestSupport.all;
import static io.github.steinbeck.seneca.app.GuiTestSupport.click;
import static io.github.steinbeck.seneca.app.GuiTestSupport.find;
import static io.github.steinbeck.seneca.app.GuiTestSupport.flushEdt;
import static io.github.steinbeck.seneca.app.GuiTestSupport.labels;
import static io.github.steinbeck.seneca.app.GuiTestSupport.menuItem;
import static io.github.steinbeck.seneca.app.GuiTestSupport.onEdt;
import static io.github.steinbeck.seneca.app.GuiTestSupport.shot;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.swing.AbstractButton;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;

import org.junit.jupiter.api.Test;

import io.github.steinbeck.seneca.app.ComponentNames;
import io.github.steinbeck.seneca.app.GuiTestSupport;
import io.github.steinbeck.seneca.app.GuiTestSupport.TestApp;
import io.github.steinbeck.seneca.app.ScriptedUi;
import io.github.steinbeck.seneca.app.TestHooks;
import io.github.steinbeck.seneca.app.dataset.DatasetFrame;
import io.github.steinbeck.seneca.app.judges.JudgeReportPanel;
import io.github.steinbeck.seneca.app.run.StructureGenerationPanel;
import io.github.steinbeck.seneca.app.structure.StructureDisplayPanel;
import io.github.steinbeck.seneca.judges.FeatureJudge;
import io.github.steinbeck.seneca.judges.HeteroBondsJudge;
import io.github.steinbeck.seneca.judges.HybridizationJudge;
import io.github.steinbeck.seneca.judges.Judge;
import io.github.steinbeck.seneca.model.AnnealingSchedule;
import io.github.steinbeck.seneca.model.NMRSpectrum;
import io.github.steinbeck.seneca.model.SenecaDataset;
import io.github.steinbeck.seneca.model.SpectrumSlot;

/**
 * Completeness verification, round 2 (after the fix round): walks docs/legacy/feature-inventory.md item by
 * item for the items not already driven by {@link FeatureInventoryVerifyTest} and the G1/G2 tests, and
 * compares labels, columns, tooltips and defaults with the 2001 client sources. Collects every deviation
 * ("VERIFY-DIFF ...") and fails once at the end with the full list, so one run reports all of them.
 * Headless, scripted UI, scratch home.
 */
class CompletenessRound2VerifyTest {

    private final List<String> diffs = new ArrayList<>();

    private void eq(String item, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            String d = item + ": expected <" + expected + "> but was <" + actual + ">";
            diffs.add(d);
            System.out.println("VERIFY-DIFF " + d);
        } else {
            System.out.println("VERIFY-OK " + item);
        }
    }

    private void check(String item, boolean ok, String detail) {
        if (!ok) {
            diffs.add(item + ": " + detail);
            System.out.println("VERIFY-DIFF " + item + ": " + detail);
        } else {
            System.out.println("VERIFY-OK " + item);
        }
    }

    private void done() {
        assertTrue(diffs.isEmpty(), "deviations:\n" + String.join("\n", diffs));
    }

    static DatasetFrame open(TestApp t, String key) {
        t.ui.answerFiles(GuiTestSupport.sml(key).toFile());
        click(menuItem(t.menuBar, "File", "Open", "SENECA Dataset"));
        flushEdt();
        SenecaDataset sd = onEdt(t.app.datasets::current);
        return onEdt(() -> t.app.frameOf(sd));
    }

    static <T extends Component> T named(Component root, String name, Class<T> type) {
        T c = find(root, name, type);
        if (c == null) throw new AssertionError("no component named " + name);
        return c;
    }

    static List<String> columns(JTable t) {
        return onEdt(() -> {
            List<String> l = new ArrayList<>();
            for (int c = 0; c < t.getColumnCount(); c++) l.add(t.getColumnName(c));
            return l;
        });
    }

    static List<String> row(JTable t, int r) {
        return onEdt(() -> {
            List<String> l = new ArrayList<>();
            for (int c = 0; c < t.getColumnCount(); c++) l.add(String.valueOf(t.getValueAt(r, c)));
            return l;
        });
    }

    static List<String> buttonTexts(Component root) {
        return onEdt(() -> {
            List<String> l = new ArrayList<>();
            for (Component c : all(root))
                if (c instanceof AbstractButton b && !(c instanceof javax.swing.JMenuItem) && b.getText() != null && !b.getText().isEmpty())
                    l.add(b.getText());
            return l;
        });
    }

    static List<String> titledBorders(Component root) {
        return onEdt(() -> {
            List<String> l = new ArrayList<>();
            for (Component c : all(root)) if (c instanceof JComponent j && j.getBorder() instanceof TitledBorder tb) l.add(tb.getTitle());
            return l;
        });
    }

    static <T extends Component> T first(Component root, Class<T> type) {
        return onEdt(() -> {
            for (Component c : all(root)) if (type.isInstance(c)) return type.cast(c);
            return null;
        });
    }

    static List<String> menuLabels(JMenuBar bar) {
        return onEdt(() -> {
            List<String> l = new ArrayList<>();
            for (int i = 0; i < bar.getMenuCount(); i++) l.add(bar.getMenu(i).getText());
            return l;
        });
    }

    // ---------------------------------------------------------------- main window, menus, toolbar

    /** F-10..F-34: toolbar, status bar, menu structure and labels, Datasets switch, Look&amp;Feel, About, Exit. */
    @Test
    void mainWindowMenusToolbarStatusBar() throws Exception {
        try (TestApp t = GuiTestSupport.newApp("v2-main", TestHooks.none())) {
            // F-11 toolbar: new/openAll/saveAll, icons only, tooltips
            Container tb = t.view.toolbar();
            List<String> tb1 = onEdt(() -> {
                List<String> l = new ArrayList<>();
                for (Component c : tb.getComponents())
                    if (c instanceof JButton b) l.add(b.getActionCommand() + "|" + (b.getIcon() != null) + "|" + (b.getHideActionText() || b.getText() == null || b.getText().isEmpty())
                            + "|" + b.getToolTipText());
                return l;
            });
            eq("F-11 toolbar buttons (command|icon|no text|tooltip)", List.of("new|true|true|Initialize a new dataset", "openAll|true|true|Open a file",
                    "saveAll|true|true|Save to a file"), tb1);

            // F-12 status bar
            eq("F-12 status 1 at start", "No dataset available", onEdt(() -> t.view.statusBar().status(1)));
            eq("F-12 status bar labels", 3, onEdt(() -> t.view.statusBar().getComponentCount()));

            // menus (2001 menus without DROP items)
            eq("2.1 menu bar", List.of("File", "Edit", "View", "Datasets", "Help"), menuLabels(t.menuBar));
            eq("2.1 File menu", List.of("New Dataset", "Open", "Save", "Import", "---", "Exit"), labels(t.menuBar.getMenu(0)));
            eq("F-21 File>Open", List.of("SENECA Dataset", "Example Dataset"), labels((JMenu) menuItem(t.menuBar, "File", "Open")));
            eq("F-22 File>Save", List.of("SENECA Dataset"), labels((JMenu) menuItem(t.menuBar, "File", "Save")));
            eq("F-24 File>Import", List.of("LUCY dataset"), labels((JMenu) menuItem(t.menuBar, "File", "Import")));
            eq("F-29 Edit", List.of("Preferences"), labels(t.menuBar.getMenu(1)));
            eq("F-29 Edit>Preferences", List.of("Look&Feel"), labels((JMenu) menuItem(t.menuBar, "Edit", "Preferences")));
            List<String> lnfs = labels((JMenu) menuItem(t.menuBar, "Edit", "Preferences", "Look&Feel"));
            List<String> installed = new ArrayList<>();
            for (UIManager.LookAndFeelInfo i : UIManager.getInstalledLookAndFeels()) installed.add(i.getName());
            eq("F-29 Look&Feel = installed look and feels", installed, lnfs);
            eq("F-30 View", List.of("Current Structures"), labels(t.menuBar.getMenu(2)));
            eq("F-33 Datasets", List.of("switch"), labels(t.menuBar.getMenu(3)));
            eq("F-34 Help", List.of("About Seneca"), labels(t.menuBar.getMenu(4)));
            eq("menu items without tooltips", null, onEdt(() -> menuItem(t.menuBar, "File", "New Dataset").getToolTipText()));

            // F-29: choosing Metal applies it
            String before = UIManager.getLookAndFeel().getClass().getName();
            click(menuItem(t.menuBar, "Edit", "Preferences", "Look&Feel", "Metal"));
            eq("F-29 Look&Feel > Metal applies Metal", "javax.swing.plaf.metal.MetalLookAndFeel", UIManager.getLookAndFeel().getClass().getName());
            onEdt(() -> io.github.steinbeck.seneca.app.LookAndFeels.apply(before, t.view));

            // F-34 About
            click(menuItem(t.menuBar, "Help", "About Seneca"));
            ScriptedUi.Shown about = t.ui.lastShown();
            eq("F-34 About title", "About Seneca", about.title());
            String msg = about.message();
            check("F-34 About keeps the original credit", msg.contains("SENECA 0.8b") && msg.contains("(c) 1998, 1999, 2000 by Dr. Christoph Steinbeck")
                    && msg.contains("Max-Planck-Institute of Chemical Ecology, Jena"), msg);
            System.out.println("VERIFY about text: " + msg.replace("\n", " / "));

            // F-20 New Dataset via toolbar; D10 fixed
            click(named(t.view, ComponentNames.TOOLBAR + ".new", JButton.class));
            SenecaDataset nd = onEdt(t.app.datasets::current);
            check("F-20 name untitled<hash>", nd.getName().matches("untitled-?\\d+"), nd.getName());
            eq("F-20 judges registered", 7, nd.judges.size());
            check("F-20 judges disabled", nd.judges.stream().noneMatch(Judge::getEnabled), "some enabled");
            eq("F-20 one default schedule", "A-F:0.95-IAP:0.8-PS:500-UHS: 50",
                    nd.annealingSchedules.size() == 1 ? nd.annealingSchedules.get(0).toString() : String.valueOf(nd.annealingSchedules));
            eq("F-12 status 1 = name", nd.getName(), onEdt(() -> t.view.statusBar().status(1)));

            // open two datasets via the toolbar openAll (F-21) and menu
            t.ui.answerFiles(GuiTestSupport.sml("pinene").toFile());
            click(named(t.view, ComponentNames.TOOLBAR + ".openAll", JButton.class));
            ScriptedUi.FileRequest req = t.ui.fileRequests.get(t.ui.fileRequests.size() - 1);
            eq("F-21 open dialog filter", "sml", req.extension());
            DatasetFrame pin = onEdt(() -> t.app.frameOf(t.app.datasets.current()));
            eq("F-12 status 2 = formula", "C10H16", onEdt(() -> t.view.statusBar().status(2)));
            DatasetFrame eur = open(t, "eurabidiol");

            // F-33 Datasets > switch lists the datasets; choosing one makes it current (status, Save)
            JMenu sw = (JMenu) menuItem(t.menuBar, "Datasets", "switch");
            eq("F-33 switch entries", List.of(nd.getName(), "PINEN.COL", "EURABIDIOL.COL"), labels(sw));
            click(menuItem(t.menuBar, "Datasets", "switch", "PINEN.COL"));
            eq("F-33 switch makes current", "PINEN.COL", onEdt(() -> t.app.datasets.current().getName()));
            eq("F-33 status follows switch", "PINEN.COL", onEdt(() -> t.view.statusBar().status(1)));

            // F-22 Save (toolbar saveAll) writes the current dataset
            Path out = t.dir.resolve("saved-pinene");
            t.ui.answerFiles(out.toFile());
            click(named(t.view, ComponentNames.TOOLBAR + ".saveAll", JButton.class));
            Path written = t.dir.resolve("saved-pinene.sml");
            check("F-22 save writes the current dataset (.sml added)", Files.exists(written)
                    && Files.readString(written).contains("<senecadataset title=\"PINEN.COL\">"), String.valueOf(Files.exists(written)));
            SenecaDataset reread = io.github.steinbeck.seneca.io.SpecMLReader.read(written);
            eq("F-22 saved file reads back (atoms)", pin.dataset().getSetOfNodes().length, reread.getSetOfNodes().length);

            // F-40 rename: frame title, switch menu, status bar follow
            JTable general = named(pin, ComponentNames.GENERAL_TABLE, JTable.class);
            onEdt(() -> general.setValueAt("PINENE-RENAMED", 0, 1));
            flushEdt();
            flushEdt();
            eq("F-40 rename: frame title", "PINENE-RENAMED", onEdt(pin::getTitle));
            eq("F-40 rename: switch menu", List.of(nd.getName(), "PINENE-RENAMED", "EURABIDIOL.COL"), labels(sw));
            System.out.println("VERIFY status after rename: " + onEdt(() -> t.view.statusBar().status(1)));

            // F-48 close No on all; status "No dataset loaded"
            for (DatasetFrame f : List.of(eur, pin, onEdt(() -> t.app.frameOf(nd)))) {
                t.ui.answerDialog(JOptionPane.NO_OPTION);
                click(named(f, ComponentNames.DATASET_CLOSE, JButton.class));
            }
            eq("F-48 No closes every window", 0, onEdt(() -> t.app.datasetFrames().size()));
            eq("F-12 status after last close", "No dataset loaded", onEdt(() -> t.view.statusBar().status(1)));
            eq("F-33 switch empty after close", List.of(), labels(sw));
            ScriptedUi.Shown close = t.ui.lastShown();
            eq("F-48 close prompt text", "You are about to close this dataset.\nDo you want to save your changes?", close.message());
            eq("F-48 close prompt options", JOptionPane.YES_NO_CANCEL_OPTION, close.pane().getOptionType());

            // F-23 Exit: No keeps, Yes exits
            t.ui.answerDialog(JOptionPane.NO_OPTION);
            click(menuItem(t.menuBar, "File", "Exit"));
            eq("F-23 Exit No", 0, t.exits);
            eq("F-23 Exit text (2001: each line + newline)", "Do you really want to exit Seneca?\n", t.ui.lastShown().message());
            eq("F-23 Exit options", JOptionPane.YES_NO_OPTION, t.ui.lastShown().pane().getOptionType());
            t.ui.answerDialog(JOptionPane.YES_OPTION);
            click(menuItem(t.menuBar, "File", "Exit"));
            eq("F-23 Exit Yes", 1, t.exits);
        }
        done();
    }

    // ---------------------------------------------------------------- dataset window tabs

    /** F-40..F-43: General Settings, NMR Spectra, Atom Properties, Annealing Schedule against the 2001 sources. */
    @Test
    void editingTabs() throws Exception {
        try (TestApp t = GuiTestSupport.newApp("v2-edit", TestHooks.none())) {
            DatasetFrame f = open(t, "pinene");
            SenecaDataset sd = f.dataset();

            // F-40
            JTable g = named(f, ComponentNames.GENERAL_TABLE, JTable.class);
            eq("F-40 columns", List.of("Parameter", "Setting"), columns(g));
            List<String> col0 = onEdt(() -> List.of(String.valueOf(g.getValueAt(0, 0)), String.valueOf(g.getValueAt(1, 0)),
                    String.valueOf(g.getValueAt(2, 0)), String.valueOf(g.getValueAt(3, 0))));
            eq("F-40 rows", List.of("Filename", "Description", "Molecular Formula", "Molecular Mass"), col0);
            eq("F-40 description default", "none", onEdt(() -> String.valueOf(g.getValueAt(1, 1))));
            System.out.println("VERIFY F-40 mass shown: " + onEdt(() -> g.getValueAt(3, 1)));
            eq("F-40 column 1 read-only, column 2 editable", List.of(false, true, true, true, true),
                    onEdt(() -> List.of(g.isCellEditable(0, 0), g.isCellEditable(0, 1), g.isCellEditable(1, 1), g.isCellEditable(2, 1), g.isCellEditable(3, 1))));
            onEdt(() -> g.setValueAt("my description", 1, 1));
            eq("F-40 description edit", "my description", sd.getDescription());
            onEdt(() -> g.setValueAt("136.5", 3, 1));
            eq("F-40 mass edit", 136.5f, sd.getMolecularMass());

            // F-41
            JTabbedPane spectra = named(f, ComponentNames.SPECTRA_TABS, JTabbedPane.class);
            eq("F-41 sub-tabs", 6, onEdt(spectra::getTabCount));
            JTable c13 = named(f, ComponentNames.SPECTRUM_TABLE + "carbon1D", JTable.class);
            NMRSpectrum carbon = sd.spectrum(SpectrumSlot.CARBON1D);
            int n0 = carbon.size();
            // the General Settings edits above fired a dataset change: from then on "signals" (as 2001)
            eq("F-41 label after a change", n0 + " signals in Carbon1D.", onEdt(() -> named(f, ComponentNames.SPECTRUM_LABEL + "carbon1D", JLabel.class).getText()));
            eq("F-41 buttons", List.of("Add Signal", "Delete Signal"), onEdt(() -> {
                List<String> l = new ArrayList<>();
                l.add(named(f, ComponentNames.SPECTRUM_ADD + "carbon1D", JButton.class).getText());
                l.add(named(f, ComponentNames.SPECTRUM_DELETE + "carbon1D", JButton.class).getText());
                return l;
            }));
            click(named(f, ComponentNames.SPECTRUM_ADD + "carbon1D", JButton.class));
            eq("F-41 Add Signal appends", n0 + 1, carbon.size());
            eq("F-41 empty signal row", List.of(String.valueOf(n0 + 1), "0.0", "0.0", "NONE"), row(c13, n0));
            check("F-41 all cells editable except No.", onEdt(() -> !c13.isCellEditable(0, 0) && c13.isCellEditable(0, 1) && c13.isCellEditable(0, 2)
                    && c13.isCellEditable(0, 3)), "editability");
            onEdt(() -> c13.setValueAt(12.5f, n0, 1));
            eq("F-41 shift edit", 12.5f, carbon.getSignal(n0).shift[0]);
            onEdt(() -> c13.setRowSelectionInterval(n0, n0));
            click(named(f, ComponentNames.SPECTRUM_DELETE + "carbon1D", JButton.class));
            eq("F-41 Delete Signal removes the selected row", n0, carbon.size());
            JTable hetcor = named(f, ComponentNames.SPECTRUM_TABLE + "hetcor", JTable.class);
            eq("F-41 1JCH columns (stored order)", List.of("No.", "1H", "13C", "Intensity", "Phase"), columns(hetcor));
            // 7.6 2D paste into the (empty) H,H-COSY spectrum
            NMRSpectrum cosy = sd.spectrum(SpectrumSlot.HHCOSY);
            int c0 = cosy.size();
            t.ui.clipboardText = "1.25 2.50\n3.10 1.25\n";
            @SuppressWarnings("unchecked")
            JComboBox<String> combo = named(f, ComponentNames.SPECTRUM_IMPORT + "hhcosy", JComboBox.class);
            onEdt(() -> combo.setSelectedIndex(1));
            flushEdt();
            eq("7.6 2D paste adds pairs", c0 + 2, cosy.size());
            if (cosy.size() == c0 + 2) {
                eq("7.6 2D paste signal", "1.25/2.5/1.0/1", cosy.getSignal(c0).shift[0] + "/" + cosy.getSignal(c0).shift[1] + "/"
                        + cosy.getSignal(c0).intensity + "/" + cosy.getSignal(c0).phase);
            }
            eq("F-41 COSY columns", List.of("No.", "1H", "1H", "Intensity", "Phase"), columns(named(f, ComponentNames.SPECTRUM_TABLE + "hhcosy", JTable.class)));

            // F-42
            JTable atoms = named(f, ComponentNames.ATOM_TABLE, JTable.class);
            JTable shifts = named(f, ComponentNames.CARBON_SHIFT_TABLE, JTable.class);
            eq("F-42 atom columns", List.of("No.", "Symbol", "Hydrogens", "Assigned 13C Shift"), columns(atoms));
            eq("F-42 shift columns", List.of("[ppm]", "Int.", "DEPT-90", "Int.", "DEPT-135", "Int."), columns(shifts));
            eq("F-42 titled boxes", List.of("Atom Properties", "Carbon Shifts"), titledBorders(atoms.getParent().getParent().getParent()).subList(0, 2));
            eq("F-42 row 1 before autoassignment (pinene.sml: molecule element, H 0, no shift)", List.of("1", "C", "0", "-"), row(atoms, 0));
            click(named(f, ComponentNames.AUTOASSIGN, JButton.class));
            eq("F-42 button label", "Autoassignment", onEdt(() -> named(f, ComponentNames.AUTOASSIGN, JButton.class).getText()));
            System.out.println("VERIFY F-42 row 1 after autoassignment: " + row(atoms, 0));
            JLabel bal = named(f, ComponentNames.H_BALANCE, JLabel.class);
            eq("F-42 balance message", "HCount in molecular formula: 16 - Sum of HCounts in this table: 16", onEdt(bal::getText));
            eq("F-42 balanced colour", new Color(Color.green.getRGB()).darker(), onEdt(bal::getForeground));
            onEdt(() -> atoms.setValueAt("3", 0, 2));
            flushEdt();
            System.out.println("VERIFY F-42 after H edit: " + onEdt(bal::getText));
            eq("F-42 unbalanced colour", Color.red, onEdt(bal::getForeground));
            onEdt(() -> atoms.setValueAt("7", 0, 2));
            eq("F-42 H outside 0..4 ignored", "3", onEdt(() -> String.valueOf(atoms.getValueAt(0, 2))));
            onEdt(() -> atoms.setValueAt("33.3", 0, 3));
            eq("F-42 shift edit", "33.3", onEdt(() -> String.valueOf(atoms.getValueAt(0, 3))));
            System.out.println("VERIFY F-42 carbon shifts row 1: " + row(shifts, 0) + " rows=" + onEdt(shifts::getRowCount));

            // F-43
            @SuppressWarnings("unchecked")
            JList<AnnealingSchedule> list = named(f, ComponentNames.SCHEDULE_LIST, JList.class);
            eq("F-43 list entries", List.of("A-F:0.95-IAP:0.8-PS:500-UHS: 50"), onEdt(() -> {
                List<String> l = new ArrayList<>();
                for (int i = 0; i < list.getModel().getSize(); i++) l.add(String.valueOf(list.getModel().getElementAt(i)));
                return l;
            }));
            eq("F-43 last entry selected", 0, onEdt(list::getSelectedIndex));
            List<String> fields = onEdt(() -> List.of(named(f, ComponentNames.SCHEDULE_ACCEPTANCE, JTextField.class).getText(),
                    named(f, ComponentNames.SCHEDULE_PLATEAU, JTextField.class).getText(), named(f, ComponentNames.SCHEDULE_COOLING, JTextField.class).getText(),
                    named(f, ComponentNames.SCHEDULE_UPHILL, JTextField.class).getText()));
            eq("F-43 fields (2001: Double, Long, Double, Double)", List.of("0.8", "500", "0.95", "50.0"), fields);
            Component schedTab = named(f, ComponentNames.SCHEDULE_LIST, JList.class).getParent();
            List<String> lbls = onEdt(() -> {
                List<String> l = new ArrayList<>();
                for (Component c : all(f)) if (c instanceof JLabel jl && jl.getText() != null && jl.getText().endsWith(": ")) l.add(jl.getText());
                return l;
            });
            eq("F-43 field labels", List.of("Initial Accept. Prob.: ", "Plateau Steps: ", "Cooling Factor: ", "max. Uphill Steps: "), lbls);
            eq("F-43 buttons", List.of("-> Update ->", "Remove <-", "New ->"), onEdt(() -> List.of(
                    named(f, ComponentNames.SCHEDULE_UPDATE, JButton.class).getText(), named(f, ComponentNames.SCHEDULE_REMOVE, JButton.class).getText(),
                    named(f, ComponentNames.SCHEDULE_NEW, JButton.class).getText())));
            check("F-43 tab 'Asymptotic Cooling'", onEdt(() -> {
                for (Component c : all(f)) if (c instanceof JTabbedPane tp && tp.indexOfTab("Asymptotic Cooling") >= 0) return true;
                return false;
            }), "missing");
            check("F-43 'Schedules' border", onEdt(() -> list.getBorder() instanceof TitledBorder tb && "Schedules".equals(tb.getTitle())), "missing");
            click(named(f, ComponentNames.SCHEDULE_NEW, JButton.class));
            eq("F-43 New appends default and selects it", "2|1", sd.annealingSchedules.size() + "|" + onEdt(list::getSelectedIndex));
            onEdt(() -> named(f, ComponentNames.SCHEDULE_PLATEAU, JTextField.class).setText("1234"));
            click(named(f, ComponentNames.SCHEDULE_UPDATE, JButton.class));
            eq("F-43 Update", 1234L, sd.annealingSchedules.get(1).plateauSteps);
            click(named(f, ComponentNames.SCHEDULE_REMOVE, JButton.class));
            eq("F-43 Remove", 1, sd.annealingSchedules.size());
            eq("F-43 Remove reloads fields from the neighbour", "500", onEdt(() -> named(f, ComponentNames.SCHEDULE_PLATEAU, JTextField.class).getText()));
            Objects.requireNonNull(schedTab);
            shot(t.view, 1000, 700, "v2-edit-after");
        }
        done();
    }

    /** F-44, F-50..F-56, F-81: every judge sub-tab against the 2001 configurators. */
    @Test
    void judgeTabs() throws Exception {
        try (TestApp t = GuiTestSupport.newApp("v2-judges", TestHooks.none())) {
            DatasetFrame f = open(t, "pinene");
            SenecaDataset sd = f.dataset();
            click(named(f, ComponentNames.AUTOASSIGN, JButton.class));
            String[] keys = {"HHCOSYJudge", "HMBCJudge", "HOSECodeJudge", "SymmetryJudge", "FeatureJudge", "HybridizationJudge", "HeteroBondsJudge"};
            for (String k : keys) {
                JButton auto = named(f, ComponentNames.JUDGE_AUTOCONFIGURE + k, JButton.class);
                JButton rep = named(f, ComponentNames.JUDGE_REPORT + k, JButton.class);
                JCheckBox act = named(f, ComponentNames.JUDGE_ACTIVATE + k, JCheckBox.class);
                Judge j = sd.getJudge(k);
                eq("F-44 " + k + " bottom row", List.of("Autoconfigure Judge", "Autoconfigures " + j.name, "Report Judgeconfig",
                        "Opens a window with judge configuration in ASCII output", "Activate this Judge?"),
                        onEdt(() -> List.of(auto.getText(), auto.getToolTipText(), rep.getText(), rep.getToolTipText(), act.getText())));
                eq("F-44 " + k + " disabled after load (D9)", false, j.getEnabled() || onEdt(act::isSelected));
                // checkbox -> judge, judge -> checkbox
                click(act);
                eq("F-44 " + k + " checkbox enables", true, j.getEnabled());
                onEdt(() -> j.setEnabled(false));
                flushEdt();
                eq("F-44 " + k + " checkbox follows the judge", false, onEdt(act::isSelected));
                int windows = t.ui.windows.size();
                click(rep);
                boolean twoD = k.equals("HHCOSYJudge") || k.equals("HMBCJudge");
                eq("F-81 " + k + " report window opened", twoD ? windows + 1 : windows, t.ui.windows.size());
                click(auto);
                System.out.println("VERIFY " + k + " after autoconfigure: enabled=" + j.getEnabled());
                if (!k.equals("HHCOSYJudge")) eq("F-44 " + k + " Autoconfigure enables", true, j.getEnabled());
                if (twoD && j.getEnabled()) {
                    click(rep);
                    JudgeReportPanel rp = (JudgeReportPanel) t.ui.lastWindow().content;
                    eq("F-81 report title", "Judge Configuration Report", t.ui.lastWindow().title);
                    String txt = onEdt(rp::text);
                    // pinene has no H,H-COSY signals: the assignment stays null and toString() is "" (2001 identical)
                    if (k.equals("HHCOSYJudge") && sd.spectrum(SpectrumSlot.HHCOSY).size() == 0) eq("F-81 HHCOSY report on empty COSY", "", txt);
                    else check("F-81 " + k + " report text", txt.startsWith("Configuration of TwoDSpectrumJudge") && txt.contains("End of listing"),
                            txt.substring(0, Math.min(80, txt.length())));
                }
            }
            // F-50/F-51 tables
            JTable hmbc = named(f, ComponentNames.JUDGE_TABLE + "HMBCJudge", JTable.class);
            int nAtoms = sd.getSetOfNodes().length;
            List<String> hc = columns(hmbc);
            eq("F-51 HMBC columns", nAtoms + 1, hc.size());
            eq("F-51 HMBC first columns", List.of("No.", "1", "2"), hc.subList(0, Math.min(3, hc.size())));
            check("F-51 HMBC table titled with the judge name", titledBorders(f).contains(sd.getJudge("HMBCJudge").name), titledBorders(f).toString());
            check("F-51 HMBC read-only", onEdt(() -> hmbc.getRowCount() == 0 || !hmbc.isCellEditable(0, 1)), "editable");
            System.out.println("VERIFY HMBC rows after autoconfigure: " + onEdt(hmbc::getRowCount));
            // F-52 HOSE text
            JEditorPane hose = onEdt(() -> {
                for (Component c : all(f)) if (c instanceof JEditorPane ep && ep.getText().contains("HOSECodeJudge")) return ep;
                return null;
            });
            check("F-52 HOSE text", hose != null && onEdt(() -> hose.getText().contains("uses about 600 1-sphere HOSE codes") && !hose.isEditable()), "missing");
            // F-53 Symmetry
            JTable sym = named(f, ComponentNames.JUDGE_TABLE + "SymmetryJudge", JTable.class);
            List<String> sc = columns(sym);
            eq("F-53 Symmetry first columns", List.of("No.", "Symb.", "Shift", "C1"), sc.subList(0, Math.min(4, sc.size())));
            eq("F-53 Symmetry column count", nAtoms + 3, sc.size());
            // F-54 Features
            JTable ft = named(f, ComponentNames.JUDGE_TABLE + "FeatureJudge", JTable.class);
            eq("F-54 Feature columns", List.of("Ring Size", "Score"), columns(ft));
            check("F-54 'Ringsize Scores' box", titledBorders(f).contains("Ringsize Scores"), titledBorders(f).toString());
            List<String> rings = onEdt(() -> {
                List<String> l = new ArrayList<>();
                for (int r = 0; r < ft.getRowCount(); r++) l.add(ft.getValueAt(r, 0) + "=" + ft.getValueAt(r, 1));
                return l;
            });
            check("F-54 autoconfigure rings 3,4", rings.contains("3=-500") && rings.contains("4=-300") && rings.size() == 2, rings.toString());
            JCheckBox hh = named(f, ComponentNames.FEATURE_HETHET, JCheckBox.class);
            eq("F-54 het-het label", "Hetero-Hetero Atom Bonds forbidden", onEdt(hh::getText));
            eq("F-54 het-het ticked by autoconfigure", true, onEdt(hh::isSelected));
            click(hh);
            eq("F-54 het-het untick -> judge", false, sd.getJudge(FeatureJudge.class).isHetHetPenaltyEnabled());
            // F-55 Hybridization
            JTable hy = named(f, ComponentNames.JUDGE_TABLE + "HybridizationJudge", JTable.class);
            eq("F-55 columns", List.of("No.", "Symb.", "Shift", "sp3", "sp2", "sp"), columns(hy));
            eq("F-55 row 0", "0|C", onEdt(() -> hy.getValueAt(0, 0) + "|" + hy.getValueAt(0, 1)));
            JComboBox<?> hyCombo = (JComboBox<?>) ((DefaultCellEditor) onEdt(() -> hy.getColumnModel().getColumn(3).getCellEditor())).getComponent();
            List<String> items = onEdt(() -> {
                List<String> l = new ArrayList<>();
                for (int i = 0; i < hyCombo.getItemCount(); i++) l.add(String.valueOf(hyCombo.getItemAt(i)));
                return l;
            });
            eq("F-55 combo values", List.of("1.0", "0.9", "0.8", "0.7", "0.6", "0.5", "0.4", "0.3", "0.2", "0.1", "0.0"), items);
            onEdt(() -> hy.setValueAt("0.7", 2, 4));
            eq("F-55 edit stored x100 at that atom", 70, sd.getJudge(HybridizationJudge.class).scores[2][1]);
            eq("F-55 edit shown", "70", onEdt(() -> String.valueOf(hy.getValueAt(2, 4))));
            // F-56 HeteroBonds
            JTable hb = named(f, ComponentNames.JUDGE_TABLE + "HeteroBondsJudge", JTable.class);
            eq("F-56 columns", List.of("No.", "Symb.", "Shift", "hetero-attached?"), columns(hb));
            eq("F-56 checkbox column", Boolean.class, onEdt(() -> hb.getColumnClass(3)));
            onEdt(() -> hb.setValueAt(Boolean.TRUE, 1, 3));
            eq("F-56 edit stored", true, sd.getJudge(HeteroBondsJudge.class).isHeteroBonded[1]);
            f.showTab("Judges");
            shot(t.view, 1000, 700, "v2-judges");
        }
        done();
    }

    // ---------------------------------------------------------------- run tab, Logbook, Structure Display

    /** F-46, F-60, F-69, F-86, F-87, F-47: static content of the run tab before any run; Logbook pane. */
    @Test
    void runTabAndLogbookLayout() throws Exception {
        try (TestApp t = GuiTestSupport.newApp("v2-run", TestHooks.none())) {
            DatasetFrame f = open(t, "pinene");
            StructureGenerationPanel p = first(f, StructureGenerationPanel.class);
            JTable table = named(f, ComponentNames.RUN_TABLE, JTable.class);
            eq("F-60 columns", List.of("No.", "ID", "Host", "Dataset", "MF", "Status ", "Iter."), columns(table));
            List<String> borders = titledBorders(p);
            for (String s : List.of("Annealing Data", "No structure available", "Current Structure Evaluation", "Selections", "Result"))
                check("F-46 titled box '" + s + "'", borders.contains(s), borders.toString());
            // 2001 "Server Admin": "Start local server", "Update server list", "Clear Server List" (DV-82, documented)
            check("F-61 group 'Run Slots' (2001 'Server Admin', DV-82)", borders.contains("Run Slots"), borders.toString());
            List<String> btns = buttonTexts(p);
            eq("F-61..F-68 buttons", List.of("Add run slot", "Clear Slot List", "Start as selected", "Stop Selected",
                    "Reset selected", "Summarize Result", "Log Annealing Data"), btns);
            // 2001: the label read "n signals in ..." from the start (SenecaDataset.addChangeListener called
            // stateChanged at once, see the legacy rendering '9 signals in 1JCH correlation.')
            String fresh = onEdt(() -> named(f, ComponentNames.SPECTRUM_LABEL + "hetcor", JLabel.class).getText());
            check("F-41 label at open in lower case", fresh.matches("\\d+ signals in 1JCH correlation\\."), fresh);
            click(named(f, ComponentNames.RUN_ADD_SLOT, JButton.class));
            eq("F-60 idle row", List.of("1", "local0", "local", "none", "none", "idle", "none"), row(table, 0));
            Color bg = onEdt(() -> table.prepareRenderer(table.getCellRenderer(0, 1), 0, 1).getBackground());
            eq("F-60 yellow cells", Color.yellow, bg);
            eq("F-60 cells read-only", false, onEdt(() -> table.isCellEditable(0, 1)));
            JTextPane lb = named(f, ComponentNames.LOGBOOK, JTextPane.class);
            eq("F-47 Logbook white", Color.white, onEdt(lb::getBackground));
            check("F-47 Logbook scrollable", onEdt(() -> javax.swing.SwingUtilities.getAncestorOfClass(JScrollPane.class, lb) != null), "no scroll pane");
            f.showTab("Structure Generation");
            shot(t.view, 1000, 700, "v2-run-idle");
        }
        done();
    }

    /** F-90..F-94, 7.4, 7.5: Load MDL (several files), Load LUCY Result, Save MDL file names and format, Close. */
    @Test
    void structureDisplayFiles() throws Exception {
        try (TestApp t = GuiTestSupport.newApp("v2-structdisp", TestHooks.none())) {
            click(menuItem(t.menuBar, "View", "Current Structures"));
            ScriptedUi.Window w = t.ui.lastWindow();
            eq("F-30 title", "Structure Display", w.title);
            StructureDisplayPanel p = (StructureDisplayPanel) w.content;
            Path a = t.dir.resolve("ethanol.mol");
            Files.writeString(a, "ethanol\n\n\n  3  2  0  0  0  0  0  0  0  0  1 V2000\n"
                    + "    0.0000    0.0000    0.0000 C   0  0  0  0  0  0  0  0  0  0  0  0\n"
                    + "    1.5000    0.0000    0.0000 C   0  0  0  0  0  0  0  0  0  0  0  0\n"
                    + "    2.2500    1.2990    0.0000 O   0  0  0  0  0  0  0  0  0  0  0  0\n"
                    + "  1  2  1  0  0  0  0\n  2  3  1  0  0  0  0\nM  END\n", StandardCharsets.ISO_8859_1);
            Path b = t.dir.resolve("propene.mol");
            Files.writeString(b, "propene\n\n\n  3  2  0  0  0  0  0  0  0  0  1 V2000\n"
                    + "    0.0000    0.0000    0.0000 C   0  0  0  0  0  0  0  0  0  0  0  0\n"
                    + "    1.5000    0.0000    0.0000 C   0  0  0  0  0  0  0  0  0  0  0  0\n"
                    + "    2.2500    1.2990    0.0000 C   0  0  0  0  0  0  0  0  0  0  0  0\n"
                    + "  1  2  2  0  0  0  0\n  2  3  1  0  0  0  0\nM  END\n", StandardCharsets.ISO_8859_1);
            t.ui.answerFiles(a.toFile(), b.toFile());
            click(named(p, ComponentNames.STRUCTURE_LOAD_MDL, JButton.class));
            eq("F-91 Load MDL: one tile per file, titled with the name", List.of("ethanol.mol", "propene.mol"),
                    p.tileList().stream().map(StructureDisplayPanel.Tile::title).toList());
            Path lucy = t.dir.resolve("result.lucy");
            Files.writeString(lucy, "LUCY result\n3 2\n1 C 0 0 3\n2 C 0 0 2\n3 O 0 0 1\n1 2 1\n2 3 1\n", StandardCharsets.ISO_8859_1);
            t.ui.answerFiles(lucy.toFile());
            click(named(p, ComponentNames.STRUCTURE_LOAD_LUCY, JButton.class));
            eq("F-92 Load LUCY Result tile", "result.lucy", p.tileList().get(p.tileList().size() - 1).title());
            check("F-92 LUCY structure read", p.tileList().get(2).structure() != null && p.tileList().get(2).structure().atoms() != null,
                    "no atoms");
            eq("F-90 tile size", new java.awt.Dimension(300, 200), onEdt(() -> p.tileList().get(0).getPreferredSize()));
            Path saveDir = Files.createDirectories(t.dir.resolve("out"));
            t.ui.answerFiles(saveDir.resolve("hits.mol").toFile());
            click(named(p, ComponentNames.STRUCTURE_SAVE_MDL, JButton.class));
            ScriptedUi.FileRequest req = t.ui.fileRequests.get(t.ui.fileRequests.size() - 1);
            eq("F-93 dialog title", "Save MDL MOL Files", req.title());
            List<String> files = new ArrayList<>();
            try (var s = Files.list(saveDir)) {
                s.map(x -> x.getFileName().toString()).sorted().forEach(files::add);
            }
            eq("F-93 file names <name><index>.mol", List.of("hits0.mol", "hits1.mol", "hits2.mol"), files);
            String m0 = Files.readString(saveDir.resolve("hits0.mol"), StandardCharsets.ISO_8859_1);
            System.out.println("VERIFY hits0.mol:\n" + m0);
            check("F-93 title line = file name, legacy counts line", m0.startsWith("hits0.mol\n\n\n  3  2  0  0  0  0  0  0  0  0  1 V2000\n")
                    && m0.endsWith("M  END\n"), m0.substring(0, Math.min(60, m0.length())));
            click(named(p, ComponentNames.STRUCTURE_CLOSE, JButton.class));
            eq("F-94 Close disposes", true, w.closed);
            shot(p, 640, 520, "v2-structdisp");
        }
        done();
    }

    /** Helper used above: the titled border of a component or null. */
    static String title(Border b) {
        return b instanceof TitledBorder tb ? tb.getTitle() : null;
    }
}
