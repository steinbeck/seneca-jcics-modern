package io.github.steinbeck.seneca.app.edit;

import static io.github.steinbeck.seneca.app.GuiTestSupport.click;
import static io.github.steinbeck.seneca.app.GuiTestSupport.onEdt;
import static io.github.steinbeck.seneca.app.GuiTestSupport.shot;
import static io.github.steinbeck.seneca.app.GuiTestSupport.tooltip;
import static io.github.steinbeck.seneca.app.edit.EditSupport.cell;
import static io.github.steinbeck.seneca.app.edit.EditSupport.columns;
import static io.github.steinbeck.seneca.app.edit.EditSupport.edit;
import static io.github.steinbeck.seneca.app.edit.EditSupport.named;
import static io.github.steinbeck.seneca.app.edit.EditSupport.press;
import static io.github.steinbeck.seneca.app.edit.EditSupport.rows;
import static io.github.steinbeck.seneca.app.edit.EditSupport.selected;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JEditorPane;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.border.TitledBorder;

import org.junit.jupiter.api.Test;

import io.github.steinbeck.seneca.app.ComponentNames;
import io.github.steinbeck.seneca.app.GuiTestSupport;
import io.github.steinbeck.seneca.app.GuiTestSupport.TestApp;
import io.github.steinbeck.seneca.app.ScriptedUi;
import io.github.steinbeck.seneca.app.TestHooks;
import io.github.steinbeck.seneca.app.dataset.DatasetFrame;
import io.github.steinbeck.seneca.app.judges.JudgeReportPanel;
import io.github.steinbeck.seneca.app.judges.JudgesPanel;
import io.github.steinbeck.seneca.config.JudgeConfiguration;
import io.github.steinbeck.seneca.judges.FeatureJudge;
import io.github.steinbeck.seneca.judges.HeteroBondsJudge;
import io.github.steinbeck.seneca.judges.HOSECodeJudge;
import io.github.steinbeck.seneca.judges.HybridizationJudge;
import io.github.steinbeck.seneca.judges.Judge;
import io.github.steinbeck.seneca.judges.SymmetryJudge;
import io.github.steinbeck.seneca.judges.TwoDSpectrumJudge;
import io.github.steinbeck.seneca.model.SenecaDataset;

/** Tab "Judges" (F-44, F-50..F-56, F-81): sub-tabs, common bottom row, the seven centre panels, the report window. */
class JudgesPanelTest {

    static final List<String> JUDGES = List.of("HHCOSYJudge", "HMBCJudge", "HOSECodeJudge", "SymmetryJudge", "FeatureJudge",
            "HybridizationJudge", "HeteroBondsJudge");

    @Test
    void subTabsAndCommonRow() {
        try (TestApp t = GuiTestSupport.newApp("edit-judges", TestHooks.none())) {
            DatasetFrame f = EditSupport.open(t, "pinene");
            SenecaDataset sd = f.dataset();
            EditSupport.showTab(f, "Judges");
            JTabbedPane tabs = named(f, ComponentNames.JUDGE_TABS, JTabbedPane.class);
            List<String> titles = onEdt(() -> {
                List<String> l = new ArrayList<>();
                for (int i = 0; i < tabs.getTabCount(); i++) l.add(tabs.getTitleAt(i) + "|" + tabs.getToolTipTextAt(i));
                return l;
            });
            assertEquals(List.of("H,H-COSY|H,H-COSY", "C,H-long-range Corr.|C,H-long-range Corr.", "Carbon Shifts|Shifts",
                    "Symmetric Atoms Classes|Symmetric Atoms Classes", "Features|Features", "Hybridization|Hybridization",
                    "Heterobond Attachments|Heterobond Attachments"), titles);
            for (int i = 0; i < JUDGES.size(); i++) {
                String key = JUDGES.get(i);
                Judge j = sd.getJudge(key);
                assertTrue(sd.judges.contains(j), "the dataset's own judge");
                JButton auto = named(f, ComponentNames.JUDGE_AUTOCONFIGURE + key, JButton.class);
                JButton report = named(f, ComponentNames.JUDGE_REPORT + key, JButton.class);
                JCheckBox activate = named(f, ComponentNames.JUDGE_ACTIVATE + key, JCheckBox.class);
                assertEquals("Autoconfigure Judge", onEdt(auto::getText));
                assertEquals("Autoconfigures " + key, tooltip(auto));
                assertEquals("Report Judgeconfig", onEdt(report::getText));
                assertEquals("Opens a window with judge configuration in ASCII output", tooltip(report));
                assertEquals("Activate this Judge?", onEdt(activate::getText));
                assertFalse(selected(activate), "judges are disabled after loading");

                // the check box follows programmatic changes, and sets the flag
                onEdt(() -> j.setEnabled(true));
                assertTrue(selected(activate));
                click(activate);
                assertFalse(j.getEnabled());

                // centre table titled with the judge name (not Carbon Shifts, Features)
                if (!key.equals("HOSECodeJudge") && !key.equals("FeatureJudge")) {
                    JTable table = named(f, ComponentNames.JUDGE_TABLE + key, JTable.class);
                    JScrollPane sp = (JScrollPane) onEdt(() -> table.getParent().getParent());
                    assertEquals(key, ((TitledBorder) onEdt(sp::getBorder)).getTitle());
                }
                int k = i;
                onEdt(() -> tabs.setSelectedIndex(k));
                shot(t.view, 1000, 700, "edit-judges-" + (i + 1) + "-" + key + "-read");
            }
            JEditorPane hose = named(f, ComponentNames.JUDGE_TABLE + "HOSECodeJudge", JEditorPane.class);
            assertTrue(onEdt(() -> { return hose.getText(); }).contains("uses about 600 1-sphere HOSE codes"));
            assertFalse(onEdt(hose::isEditable));
            assertNotNull(onEdt(() -> ((JudgesPanel) tabs.getParent()).configurator("FeatureJudge")));
        }
    }

    @Test
    void autoconfigureEveryJudgeAndTheTables() {
        try (TestApp t = GuiTestSupport.newApp("edit-judges-auto", TestHooks.none())) {
            DatasetFrame f = EditSupport.open(t, "pinene");
            SenecaDataset sd = f.dataset();
            EditSupport.showTab(f, "Atom Properties");
            press(f, ComponentNames.AUTOASSIGN);
            EditSupport.showTab(f, "Judges");
            JTabbedPane tabs = named(f, ComponentNames.JUDGE_TABS, JTabbedPane.class);
            int n = sd.getSetOfNodes().length;

            // 2D judges: Autoconfigure runs the assigner; the table lists assignment[i][j][j]
            for (String key : List.of("HHCOSYJudge", "HMBCJudge")) {
                TwoDSpectrumJudge j = (TwoDSpectrumJudge) sd.getJudge(key);
                press(f, ComponentNames.JUDGE_AUTOCONFIGURE + key);
                assertEquals(j.getEnabled(), selected(named(f, ComponentNames.JUDGE_ACTIVATE + key, JCheckBox.class)));
                JTable table = named(f, ComponentNames.JUDGE_TABLE + key, JTable.class);
                if (j.assignment == null) {
                    assertEquals(0, onEdt(table::getColumnCount));
                    continue;
                }
                List<String> cols = new ArrayList<>(List.of("No."));
                for (int c = 1; c <= j.assignment.length; c++) cols.add(String.valueOf(c));
                assertEquals(cols, columns(table));
                int row = 0;
                for (int a = 0; a < j.assignment.length; a++) {
                    for (int b = 0; b < j.assignment.length; b++) {
                        if (!j.assignment[a][b][b]) continue;
                        assertEquals(String.valueOf(a + 1), cell(table, row, 0));
                        for (int c = 0; c < j.assignment.length; c++) assertEquals(String.valueOf(j.assignment[a][b][c]), cell(table, row, c + 1));
                        row++;
                    }
                }
                assertEquals(row, rows(table));
                assertTrue(row > 0, key + ": pinene has correlations");

                // Report Judgeconfig: the window "Judge Configuration Report" with the judge's toString()
                press(f, ComponentNames.JUDGE_REPORT + key);
                ScriptedUi.Window w = t.ui.lastWindow();
                assertEquals("Judge Configuration Report", w.title);
                assertEquals(JudgeConfiguration.report(j), ((JudgeReportPanel) w.content).text());
                assertTrue(((JudgeReportPanel) w.content).text().startsWith("Configuration of TwoDSpectrumJudge"));
                shot(w.content, "edit-judges-report-" + key);
                onEdt(() -> tabs.setSelectedIndex(key.equals("HHCOSYJudge") ? 0 : 1));
                shot(t.view, 1000, 700, "edit-judges-" + key + "-configured");
            }

            // the other judges open no report window (2001: "dummy" on stdout)
            int windows = t.ui.windows.size();
            for (String key : List.of("HOSECodeJudge", "SymmetryJudge", "FeatureJudge", "HybridizationJudge", "HeteroBondsJudge"))
                press(f, ComponentNames.JUDGE_REPORT + key);
            assertEquals(windows, t.ui.windows.size());

            // Carbon Shifts: the assigned shifts in atom order
            press(f, ComponentNames.JUDGE_AUTOCONFIGURE + "HOSECodeJudge");
            HOSECodeJudge hose = sd.getJudge(HOSECodeJudge.class);
            assertTrue(hose.getEnabled());
            assertEquals(n, hose.getCarbonShifts().length);
            assertEquals((double) sd.getSetOfNodes()[3].shift13C, hose.getCarbonShifts()[3]);

            // Features: ring scores 3 -> -500, 4 -> -300 and the het-het check box
            FeatureJudge fj = sd.getJudge(FeatureJudge.class);
            JCheckBox hethet = named(f, ComponentNames.FEATURE_HETHET, JCheckBox.class);
            assertEquals("Hetero-Hetero Atom Bonds forbidden", onEdt(hethet::getText));
            assertFalse(selected(hethet));
            press(f, ComponentNames.JUDGE_AUTOCONFIGURE + "FeatureJudge");
            assertTrue(selected(hethet), "follows the judge");
            assertTrue(selected(named(f, ComponentNames.JUDGE_ACTIVATE + "FeatureJudge", JCheckBox.class)));
            JTable rings = named(f, ComponentNames.JUDGE_TABLE + "FeatureJudge", JTable.class);
            assertEquals(List.of("Ring Size", "Score"), columns(rings));
            assertEquals(2, rows(rings));
            for (int r = 0; r < 2; r++) {
                int size = Integer.parseInt(cell(rings, r, 0));
                assertEquals(Map.of(3, -500, 4, -300).get(size), Integer.valueOf(cell(rings, r, 1)));
            }
            click(hethet);
            assertFalse(fj.isHetHetPenaltyEnabled());
            click(hethet);
            assertTrue(fj.isHetHetPenaltyEnabled());
            onEdt(() -> tabs.setSelectedIndex(4));
            shot(t.view, 1000, 700, "edit-judges-FeatureJudge-configured");

            // Symmetric atoms: two atoms with the same shift form a class
            JTable atoms = named(f, ComponentNames.ATOM_TABLE, JTable.class);
            String s0 = cell(atoms, 0, 3);
            assertTrue(edit(atoms, 4, 3, s0));
            press(f, ComponentNames.JUDGE_AUTOCONFIGURE + "SymmetryJudge");
            SymmetryJudge sym = sd.getJudge(SymmetryJudge.class);
            assertEquals(1, sym.getSymmetryClassCount());
            JTable symTable = named(f, ComponentNames.JUDGE_TABLE + "SymmetryJudge", JTable.class);
            List<String> cols = new ArrayList<>(List.of("No.", "Symb.", "Shift"));
            for (int a = 0; a < n; a++) cols.add(sd.getSetOfNodes()[a].symbol + (a + 1));
            assertEquals(cols, columns(symTable));
            assertEquals(1, rows(symTable));
            assertEquals(List.of("1", "C", s0), List.of(cell(symTable, 0, 0), cell(symTable, 0, 1), cell(symTable, 0, 2)));
            for (int a = 0; a < n; a++) assertEquals(String.valueOf(a == 0 || a == 4), cell(symTable, 0, a + 3));
            assertFalse(onEdt(() -> symTable.isCellEditable(0, 3)));
            onEdt(() -> tabs.setSelectedIndex(3));
            shot(t.view, 1000, 700, "edit-judges-SymmetryJudge-configured");

            // Hybridization: combo 1.0 ... 0.0, stored x100; No. is 0-based
            press(f, ComponentNames.JUDGE_AUTOCONFIGURE + "HybridizationJudge");
            HybridizationJudge hy = sd.getJudge(HybridizationJudge.class);
            assertTrue(hy.getEnabled());
            JTable hyTable = named(f, ComponentNames.JUDGE_TABLE + "HybridizationJudge", JTable.class);
            assertEquals(List.of("No.", "Symb.", "Shift", "sp3", "sp2", "sp"), columns(hyTable));
            assertEquals(List.of("0", "C", String.valueOf(sd.getSetOfNodes()[0].shift13C), "0", "0", "0"),
                    List.of(cell(hyTable, 0, 0), cell(hyTable, 0, 1), cell(hyTable, 0, 2), cell(hyTable, 0, 3), cell(hyTable, 0, 4), cell(hyTable, 0, 5)));
            assertTrue(edit(hyTable, 0, JudgeConfiguration.HYB_COLUMN_SP2, "0.7"));
            assertTrue(edit(hyTable, 1, JudgeConfiguration.HYB_COLUMN_SP, "0.0"));
            assertTrue(edit(hyTable, 2, JudgeConfiguration.HYB_COLUMN_SP3, "1.0"));
            assertEquals(70, hy.scores[0][HybridizationJudge.SP2]);
            assertEquals(0, hy.scores[1][HybridizationJudge.SP]);
            assertEquals(100, hy.scores[2][HybridizationJudge.SP3]);
            assertEquals("70", cell(hyTable, 0, 4));
            assertFalse(onEdt(() -> hyTable.isCellEditable(0, 2)), "shifts are not edited here");
            onEdt(() -> tabs.setSelectedIndex(5));
            shot(t.view, 1000, 700, "edit-judges-HybridizationJudge-configured");

            // Heterobond attachments: check box column
            HeteroBondsJudge hb = sd.getJudge(HeteroBondsJudge.class);
            JTable hbTable = named(f, ComponentNames.JUDGE_TABLE + "HeteroBondsJudge", JTable.class);
            assertEquals(List.of("No.", "Symb.", "Shift", "hetero-attached?"), columns(hbTable));
            assertEquals(Boolean.class, onEdt(() -> hbTable.getColumnClass(3)));
            assertTrue(edit(hbTable, 2, JudgeConfiguration.HETERO_COLUMN, Boolean.TRUE));
            assertTrue(hb.isHeteroBonded[2]);
            assertEquals("true", cell(hbTable, 2, 3));
            assertTrue(edit(hbTable, 2, JudgeConfiguration.HETERO_COLUMN, Boolean.FALSE));
            assertFalse(hb.isHeteroBonded[2]);
            click(named(f, ComponentNames.JUDGE_ACTIVATE + "HeteroBondsJudge", JCheckBox.class));
            assertTrue(hb.getEnabled());
            onEdt(() -> tabs.setSelectedIndex(6));
            shot(t.view, 1000, 700, "edit-judges-HeteroBondsJudge-configured");
        }
    }

    /** A 2D assignment the core refuses (no H,H-COSY data in eurabidiol): the judge and its check box are unticked. */
    @Test
    void failedTwoDAssignmentUnticksTheJudge() {
        try (TestApp t = GuiTestSupport.newApp("edit-judges-fail", TestHooks.none())) {
            DatasetFrame f = EditSupport.open(t, "eurabidiol");
            SenecaDataset sd = f.dataset();
            press(f, ComponentNames.AUTOASSIGN);
            press(f, ComponentNames.JUDGE_AUTOCONFIGURE + "HHCOSYJudge");
            Judge cosy = sd.getJudge("HHCOSYJudge");
            assertEquals(cosy.getEnabled(), selected(named(f, ComponentNames.JUDGE_ACTIVATE + "HHCOSYJudge", JCheckBox.class)));
            // DV-83: a failed assignment is reported (2001: "Assignment failed." on stdout only); no exception in DEFAULT
            if (cosy.getEnabled()) {
                assertEquals(0, t.ui.shown.size());
            } else {
                assertEquals(1, t.ui.shown.size());
                assertEquals("Autoconfigure Judge", t.ui.lastShown().title());
                assertEquals(javax.swing.JOptionPane.WARNING_MESSAGE, t.ui.lastShown().pane().getMessageType());
                assertTrue(String.valueOf(t.ui.lastShown().pane().getMessage()).startsWith("Assignment failed: HHCOSYJudge"));
            }
        }
    }
}
