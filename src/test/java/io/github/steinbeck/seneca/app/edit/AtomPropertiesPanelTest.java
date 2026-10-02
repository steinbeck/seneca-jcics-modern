package io.github.steinbeck.seneca.app.edit;

import static io.github.steinbeck.seneca.app.GuiTestSupport.onEdt;
import static io.github.steinbeck.seneca.app.GuiTestSupport.shot;
import static io.github.steinbeck.seneca.app.edit.EditSupport.cell;
import static io.github.steinbeck.seneca.app.edit.EditSupport.columns;
import static io.github.steinbeck.seneca.app.edit.EditSupport.edit;
import static io.github.steinbeck.seneca.app.edit.EditSupport.named;
import static io.github.steinbeck.seneca.app.edit.EditSupport.press;
import static io.github.steinbeck.seneca.app.edit.EditSupport.rows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import javax.swing.JLabel;
import javax.swing.JTable;

import org.junit.jupiter.api.Test;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.app.ComponentNames;
import io.github.steinbeck.seneca.app.GuiTestSupport;
import io.github.steinbeck.seneca.app.GuiTestSupport.TestApp;
import io.github.steinbeck.seneca.app.TestHooks;
import io.github.steinbeck.seneca.app.dataset.AtomPropertiesPanel;
import io.github.steinbeck.seneca.app.dataset.DatasetFrame;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.config.CarbonShiftAssigner;
import io.github.steinbeck.seneca.io.SpecMLReader;
import io.github.steinbeck.seneca.model.NMRSignal;
import io.github.steinbeck.seneca.model.SenecaDataset;
import io.github.steinbeck.seneca.model.SpectrumSlot;

/** Tab "Atom Properties" (F-42): the two tables, Autoassignment, H balance, the README tutorial. */
class AtomPropertiesPanelTest {

    @Test
    void tablesAutoassignmentAndBalanceOnPinene() throws Exception {
        try (TestApp t = GuiTestSupport.newApp("edit-atoms", TestHooks.none())) {
            DatasetFrame f = EditSupport.open(t, "pinene");
            SenecaDataset sd = f.dataset();
            EditSupport.showTab(f, "Atom Properties");
            JTable atoms = named(f, ComponentNames.ATOM_TABLE, JTable.class);
            JTable shifts = named(f, ComponentNames.CARBON_SHIFT_TABLE, JTable.class);
            JLabel balance = named(f, ComponentNames.H_BALANCE, JLabel.class);
            assertEquals(List.of("No.", "Symbol", "Hydrogens", "Assigned 13C Shift"), columns(atoms));
            assertEquals(List.of("[ppm]", "Int.", "DEPT-90", "Int.", "DEPT-135", "Int."), columns(shifts));
            assertEquals(10, rows(atoms));
            assertEquals(List.of("1", "C", "0", "-"), List.of(cell(atoms, 0, 0), cell(atoms, 0, 1), cell(atoms, 0, 2), cell(atoms, 0, 3)));
            int n1d = sd.spectrum(SpectrumSlot.CARBON1D).size();
            assertEquals(n1d, rows(shifts));
            NMRSignal d90last = sd.spectrum(SpectrumSlot.DEPT90).getSignal(sd.spectrum(SpectrumSlot.DEPT90).size() - 1);
            int k = sd.spectrum(SpectrumSlot.DEPT90).size();
            assertEquals(String.valueOf(d90last.shift[0]), cell(shifts, k - 1, 2));
            assertEquals("-", cell(shifts, k, 2), "the k-th DEPT signal in row k, '-' beyond");
            assertEquals("HCount in molecular formula: 16 - Sum of HCounts in this table: 0", onEdt(balance::getText));
            assertEquals(AtomPropertiesPanel.UNBALANCED, onEdt(balance::getForeground));
            shot(t.view, 1000, 700, "edit-atoms-1-read");

            // Autoassignment = the core's CarbonShiftAssigner (DEFAULT) on the same file
            SenecaDataset ref = SpecMLReader.read(GuiTestSupport.sml("pinene"));
            new CarbonShiftAssigner(ref, LegacyCompat.DEFAULT).assign();
            press(f, ComponentNames.AUTOASSIGN);
            for (int i = 0; i < ref.getSetOfNodes().length; i++) {
                HeavyAtom e = ref.getSetOfNodes()[i], a = sd.getSetOfNodes()[i];
                assertEquals(e.hCount, a.hCount, "H of atom " + i);
                assertEquals(e.shift13C, a.shift13C, "shift of atom " + i);
                assertEquals(String.valueOf(a.hCount), cell(atoms, i, 2));
                assertEquals(String.valueOf(a.shift13C), cell(atoms, i, 3));
            }
            NMRSignal s0 = sd.spectrum(SpectrumSlot.CARBON1D).getSignal(0);
            assertEquals(String.valueOf(s0.intensity * s0.normalizeFactor), cell(shifts, 0, 1));
            assertEquals("HCount in molecular formula: 16 - Sum of HCounts in this table: 16", onEdt(balance::getText));
            assertEquals(AtomPropertiesPanel.BALANCED, onEdt(balance::getForeground));
            assertEquals(0, t.ui.shown.size(), "no message");
            shot(t.view, 1000, 700, "edit-atoms-2-autoassigned");

            // assigned shift: a float is stored, anything else ignored
            assertTrue(edit(atoms, 0, 3, "55.5"));
            assertEquals(55.5f, sd.getSetOfNodes()[0].shift13C);
            edit(atoms, 0, 3, "abc");
            assertEquals(55.5f, sd.getSetOfNodes()[0].shift13C);

            // balanced: an undefined H count becomes 0 when the table is painted (2001 updateMessageLabel side effect)
            int quaternary = -1;
            for (int i = 0; i < sd.getSetOfNodes().length; i++) if (sd.getSetOfNodes()[i].hCount == 0) quaternary = i;
            int q = quaternary;
            onEdt(() -> { sd.getSetOfNodes()[q].hCount = -1; });
            assertEquals("undefined", cell(atoms, q, 2));
            shot(t.view, 1000, 700, "edit-atoms-3-painted");
            assertEquals(0, sd.getSetOfNodes()[q].hCount);
        }
    }

    /** README tutorial: Autoassignment leaves 2 H unassigned (red); 1 typed into O-16 and O-17 gives green. */
    @Test
    void readmeTutorialEurabidiol() {
        try (TestApp t = GuiTestSupport.newApp("edit-atoms-tutorial", TestHooks.none())) {
            DatasetFrame f = EditSupport.open(t, "eurabidiol");
            SenecaDataset sd = f.dataset();
            EditSupport.showTab(f, "Atom Properties");
            JTable atoms = named(f, ComponentNames.ATOM_TABLE, JTable.class);
            JLabel balance = named(f, ComponentNames.H_BALANCE, JLabel.class);
            press(f, ComponentNames.AUTOASSIGN);
            assertEquals("HCount in molecular formula: 28 - Sum of HCounts in this table: 26", onEdt(balance::getText));
            assertEquals(AtomPropertiesPanel.UNBALANCED, onEdt(balance::getForeground));
            assertEquals("O", cell(atoms, 15, 1));
            assertEquals("16", cell(atoms, 15, 0));
            shot(t.view, 1000, 700, "edit-atoms-tutorial-1-red");

            edit(atoms, 15, 2, "5");          // only 0..4 accepted
            edit(atoms, 15, 2, "x");
            assertEquals(0, sd.getSetOfNodes()[15].hCount);
            assertTrue(edit(atoms, 15, 2, "1"));
            assertEquals("HCount in molecular formula: 28 - Sum of HCounts in this table: 27", onEdt(balance::getText));
            assertTrue(edit(atoms, 16, 2, "1"));
            assertEquals(1, sd.getSetOfNodes()[16].hCount);
            assertEquals("HCount in molecular formula: 28 - Sum of HCounts in this table: 28", onEdt(balance::getText));
            assertEquals(AtomPropertiesPanel.BALANCED, onEdt(balance::getForeground));
            shot(t.view, 1000, 700, "edit-atoms-tutorial-2-green");
        }
    }

    @Test
    void autoassignmentThatTheCoreRefusesIsReported() {
        try (TestApp t = GuiTestSupport.newApp("edit-atoms-refused", TestHooks.none())) {
            onEdt(() -> t.actions().newDataset());
            SenecaDataset sd = onEdt(t.app.datasets::current);
            DatasetFrame f = onEdt(() -> t.app.frameOf(sd));
            JLabel balance = named(f, ComponentNames.H_BALANCE, JLabel.class);
            assertEquals("", onEdt(balance::getText), "no atoms, no message");
            press(f, ComponentNames.AUTOASSIGN);
            assertEquals("Autoassignment", t.ui.lastShown().title());
            assertTrue(t.ui.lastShown().message().startsWith("Autoassignment failed."));

            JTable general = named(f, ComponentNames.GENERAL_TABLE, JTable.class);
            assertTrue(edit(general, 2, 1, "C2H6O"));
            assertEquals(3, rows(named(f, ComponentNames.ATOM_TABLE, JTable.class)));
            assertEquals("HCount in molecular formula: 6 - Sum of HCounts in this table: 0", onEdt(balance::getText));
            int shown = t.ui.shown.size();
            press(f, ComponentNames.AUTOASSIGN);      // C atoms, empty 1D spectrum (legacy NPE, DV-42)
            assertEquals(shown + 1, t.ui.shown.size());
            assertNull(sd.getSetOfNodes()[0].shift13C);
            shot(t.ui.lastShown().pane(), "edit-dialog-autoassign-failed");
        }
    }
}
