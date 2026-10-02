package io.github.steinbeck.seneca.app.edit;

import static io.github.steinbeck.seneca.app.GuiTestSupport.onEdt;
import static io.github.steinbeck.seneca.app.GuiTestSupport.shot;
import static io.github.steinbeck.seneca.app.edit.EditSupport.cell;
import static io.github.steinbeck.seneca.app.edit.EditSupport.columns;
import static io.github.steinbeck.seneca.app.edit.EditSupport.edit;
import static io.github.steinbeck.seneca.app.edit.EditSupport.named;
import static io.github.steinbeck.seneca.app.edit.EditSupport.press;
import static io.github.steinbeck.seneca.app.edit.EditSupport.rows;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.JTable;

import org.junit.jupiter.api.Test;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.app.ComponentNames;
import io.github.steinbeck.seneca.app.GuiTestSupport;
import io.github.steinbeck.seneca.app.GuiTestSupport.TestApp;
import io.github.steinbeck.seneca.app.TestHooks;
import io.github.steinbeck.seneca.app.dataset.DatasetFrame;
import io.github.steinbeck.seneca.app.spectra.SpectraPanel;
import io.github.steinbeck.seneca.app.spectra.SpectrumTablePanel;
import io.github.steinbeck.seneca.io.WinNmrPeakPicking;
import io.github.steinbeck.seneca.model.NMRSignal;
import io.github.steinbeck.seneca.model.NMRSpectrum;
import io.github.steinbeck.seneca.model.SenecaDataset;
import io.github.steinbeck.seneca.model.SpectrumSlot;

/** Tab "NMR Spectra" (F-41, 7.6): sub-tabs, signal tables, Add/Delete Signal, WinNMR paste. */
class SpectrumTablePanelTest {

    static final String PP_1D = """
            WIN-NMR 1D
            Peak Picking results:
            Peak   Point   Frequency   ppm   Intensity   %Int
            1   1024   7543.21   75.0123   1234.5   100.0
            2   2048   5432.10   54.25   -617.2   -50.0
            3   3000   3210.00   32.1   0.0   0.0
            """;

    @Test
    void subTabsTablesAndLabelsAsIn2001() {
        try (TestApp t = GuiTestSupport.newApp("edit-spectra", TestHooks.none())) {
            DatasetFrame f = EditSupport.open(t, "pinene");
            SenecaDataset sd = f.dataset();
            EditSupport.showTab(f, "NMR Spectra");
            JTabbedPane tabs = named(f, ComponentNames.SPECTRA_TABS, JTabbedPane.class);
            assertEquals(List.of("1D Carbon", "DEPT-90", "DEPT-135", "H,C-HetCor", "H,C-lr-HetCor", "H,H-COSY"), onEdt(() -> {
                List<String> l = new java.util.ArrayList<>();
                for (int i = 0; i < tabs.getTabCount(); i++) l.add(tabs.getTitleAt(i) + (tabs.getTitleAt(i).equals(tabs.getToolTipTextAt(i)) ? "" : "!"));
                return l;
            }));
            for (int i = 0; i < SpectraPanel.SLOTS.length; i++) {
                SpectrumSlot slot = SpectraPanel.SLOTS[i];
                NMRSpectrum s = sd.spectrum(slot);
                JTable table = named(f, ComponentNames.SPECTRUM_TABLE + slot.key, JTable.class);
                JLabel label = named(f, ComponentNames.SPECTRUM_LABEL + slot.key, JLabel.class);
                assertEquals(s.size() + " signals in " + s.specType + ".", onEdt(label::getText));
                List<String> expected = new java.util.ArrayList<>(List.of("No."));
                expected.addAll(List.of(s.nucleus));
                expected.addAll(List.of("Intensity", "Phase"));
                assertEquals(expected, columns(table), slot.key);
                assertEquals(s.size(), rows(table));
                for (int r = 0; r < s.size(); r++) {
                    NMRSignal g = s.getSignal(r);
                    assertEquals(String.valueOf(r + 1), cell(table, r, 0));
                    for (int a = 0; a < s.dim; a++) assertEquals(String.valueOf(g.shift[a]), cell(table, r, a + 1));
                    assertEquals(String.valueOf(g.intensity), cell(table, r, s.dim + 1));
                    assertEquals(NMRSignal.PHASENAMES.get(g.phase), cell(table, r, s.dim + 2));
                }
                int k = i;
                onEdt(() -> tabs.setSelectedIndex(k));
                shot(t.view, 1000, 700, "edit-spectra-" + (i + 1) + "-" + slot.key);
            }
            assertEquals(List.of("No.", "1H", "13C", "Intensity", "Phase"), columns(named(f, ComponentNames.SPECTRUM_TABLE + "hetcor", JTable.class)),
                    "2D axes in stored order (f1 = 1H)");
            @SuppressWarnings("unchecked")
            JComboBox<String> combo = named(f, ComponentNames.SPECTRUM_IMPORT + "carbon1D", JComboBox.class);
            assertEquals(List.of("Import peak picking", "Paste WinNMR pp from clipboard"),
                    onEdt(() -> List.of(combo.getItemAt(0), combo.getItemAt(1))));
            assertEquals(2, onEdt(combo::getItemCount), "GIFA import dropped");
        }
    }

    @Test
    void addEditAndDeleteSignals() {
        try (TestApp t = GuiTestSupport.newApp("edit-spectra-rows", TestHooks.none())) {
            DatasetFrame f = EditSupport.open(t, "pinene");
            NMRSpectrum c = f.dataset().spectrum(SpectrumSlot.CARBON1D);
            JTable table = named(f, ComponentNames.SPECTRUM_TABLE + "carbon1D", JTable.class);
            JLabel label = named(f, ComponentNames.SPECTRUM_LABEL + "carbon1D", JLabel.class);
            int n = c.size();

            press(f, ComponentNames.SPECTRUM_ADD + "carbon1D");
            assertEquals(n + 1, c.size());
            assertEquals(n + 1, rows(table));
            NMRSignal added = c.getSignal(n);
            assertArrayEquals(new float[] {0f}, added.shift);
            assertEquals(0f, added.intensity);
            assertEquals(NMRSignal.PHASE_NONE, added.phase);
            assertEquals((n + 1) + " signals in Carbon1D.", onEdt(label::getText), "lower case after a change, as 2001");

            assertTrue(edit(table, n, 1, "123.4"));
            assertEquals(123.4f, added.shift[0]);
            assertTrue(edit(table, n, 2, "2.5"));
            assertEquals(2.5f, added.intensity);
            assertFalse(edit(table, n, 1, "abc"), "the float editor refuses text");
            assertEquals(123.4f, added.shift[0]);
            assertTrue(edit(table, n, 3, "NEGATIVE"));
            assertEquals(NMRSignal.PHASE_NEGATIVE, added.phase);
            EditSupport.showTab(f, "NMR Spectra");
            onEdt(() -> named(f, ComponentNames.SPECTRA_TABS, JTabbedPane.class).setSelectedIndex(0));
            shot(t.view, 1000, 700, "edit-spectra-added-signal");

            // Delete Signal: nothing without a selection, else the selected row
            onEdt(table::clearSelection);
            press(f, ComponentNames.SPECTRUM_DELETE + "carbon1D");
            assertEquals(n + 1, c.size());
            onEdt(() -> table.setRowSelectionInterval(0, 0));
            NMRSignal second = c.getSignal(1);
            press(f, ComponentNames.SPECTRUM_DELETE + "carbon1D");
            assertEquals(n, c.size());
            assertEquals(second, c.getSignal(0));
            assertEquals(n, rows(table));
        }
    }

    @Test
    void pasteWinNmrPeakPicking1D2DAndErrors() {
        try (TestApp t = GuiTestSupport.newApp("edit-spectra-paste", TestHooks.none())) {
            DatasetFrame f = EditSupport.open(t, "eurabidiol");
            SenecaDataset sd = f.dataset();
            @SuppressWarnings("unchecked")
            JComboBox<String> d135 = named(f, ComponentNames.SPECTRUM_IMPORT + "dept135", JComboBox.class);
            NMRSpectrum dept135 = sd.spectrum(SpectrumSlot.DEPT135);
            int n = dept135.size();

            // 1D: the same signals as the core parser appends
            t.ui.setText(PP_1D);
            onEdt(() -> d135.setSelectedItem(SpectrumTablePanel.PASTE_WINNMR));
            assertEquals(0, onEdt(d135::getSelectedIndex), "combo back at 'Import peak picking'");
            NMRSpectrum ref = new SenecaDataset("x").spectrum(SpectrumSlot.DEPT135);
            WinNmrPeakPicking.paste(PP_1D, ref, LegacyCompat.DEFAULT);
            assertEquals(n + 3, dept135.size());
            for (int i = 0; i < 3; i++) {
                NMRSignal a = ref.getSignal(i), b = dept135.getSignal(n + i);
                assertArrayEquals(a.shift, b.shift);
                assertEquals(a.intensity, b.intensity);
                assertEquals(a.phase, b.phase);
            }
            assertEquals(n + 3, rows(named(f, ComponentNames.SPECTRUM_TABLE + "dept135", JTable.class)));

            // 2D into H,H-COSY, which eurabidiol.sml has empty (dim 0): the slot's axes first (DV-85)
            NMRSpectrum cosy = sd.spectrum(SpectrumSlot.HHCOSY);
            assertEquals(0, cosy.dim);
            JTable cosyTable = named(f, ComponentNames.SPECTRUM_TABLE + "hhcosy", JTable.class);
            assertEquals(List.of("No.", "Intensity", "Phase"), columns(cosyTable));
            t.ui.setText("1.25 2.5\n3.75 1.0\n");
            @SuppressWarnings("unchecked")
            JComboBox<String> cosyCombo = named(f, ComponentNames.SPECTRUM_IMPORT + "hhcosy", JComboBox.class);
            onEdt(() -> cosyCombo.setSelectedItem(SpectrumTablePanel.PASTE_WINNMR));
            assertArrayEquals(new String[] {"1H", "1H"}, cosy.nucleus);
            assertEquals(2, cosy.size());
            assertArrayEquals(new float[] {1.25f, 2.5f}, cosy.getSignal(0).shift);
            assertArrayEquals(new float[] {3.75f, 1.0f}, cosy.getSignal(1).shift, "own shift array per signal (DV-40)");
            assertEquals(List.of("No.", "1H", "1H", "Intensity", "Phase"), columns(cosyTable));
            assertEquals("POSITIVE", cell(cosyTable, 1, 4));
            assertEquals("Phase", onEdt(() -> cosyTable.getColumnName(4)));
            assertTrue(edit(cosyTable, 0, 4, "NONE"), "phase editor installed again after the columns changed");
            assertEquals(NMRSignal.PHASE_NONE, cosy.getSignal(0).phase);
            onEdt(() -> named(f, ComponentNames.SPECTRA_TABS, JTabbedPane.class).setSelectedIndex(5));
            EditSupport.showTab(f, "NMR Spectra");
            shot(t.view, 1000, 700, "edit-spectra-pasted-cosy");

            // malformed text: the pairs before the error stay, a message is shown
            t.ui.setText("4.0 5.0\n6.0 abc\n");
            onEdt(() -> cosyCombo.setSelectedItem(SpectrumTablePanel.PASTE_WINNMR));
            assertEquals(3, cosy.size());
            assertEquals(SpectrumTablePanel.PASTE_WINNMR, t.ui.lastShown().title());
            assertTrue(t.ui.lastShown().message().startsWith("The clipboard text is not a complete WinNMR peak picking list."));
            shot(t.ui.lastShown().pane(), "edit-dialog-paste-error");

            // empty clipboard
            int shown = t.ui.shown.size();
            t.ui.setText(null);
            onEdt(() -> cosyCombo.setSelectedItem(SpectrumTablePanel.PASTE_WINNMR));
            assertEquals(shown + 1, t.ui.shown.size());
            assertEquals("The clipboard holds no text.", t.ui.lastShown().message());
            assertEquals(3, cosy.size());
        }
    }

    @Test
    void addSignalToAnEmptySpectrumGivesItTheSlotAxes() {
        try (TestApp t = GuiTestSupport.newApp("edit-spectra-empty", TestHooks.none())) {
            DatasetFrame f = EditSupport.open(t, "eurabidiol");
            NMRSpectrum cosy = f.dataset().spectrum(SpectrumSlot.HHCOSY);
            press(f, ComponentNames.SPECTRUM_ADD + "hhcosy");
            assertEquals(2, cosy.dim);
            assertArrayEquals(new float[] {0f, 0f}, cosy.getSignal(0).shift);
            JTable table = named(f, ComponentNames.SPECTRUM_TABLE + "hhcosy", JTable.class);
            assertTrue(edit(table, 0, 2, "3.5"));
            assertEquals(3.5f, cosy.getSignal(0).shift[1]);
            assertEquals("1 signals in H,H-COSY.", onEdt(() -> named(f, ComponentNames.SPECTRUM_LABEL + "hhcosy", JLabel.class).getText()));
        }
    }
}
