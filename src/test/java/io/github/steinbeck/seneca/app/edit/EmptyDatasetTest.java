package io.github.steinbeck.seneca.app.edit;

import static io.github.steinbeck.seneca.app.GuiTestSupport.click;
import static io.github.steinbeck.seneca.app.GuiTestSupport.onEdt;
import static io.github.steinbeck.seneca.app.GuiTestSupport.render;
import static io.github.steinbeck.seneca.app.GuiTestSupport.shot;
import static io.github.steinbeck.seneca.app.edit.EditSupport.edit;
import static io.github.steinbeck.seneca.app.edit.EditSupport.named;
import static io.github.steinbeck.seneca.app.edit.EditSupport.press;
import static io.github.steinbeck.seneca.app.edit.EditSupport.rows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import javax.swing.JCheckBox;
import javax.swing.JTabbedPane;
import javax.swing.JTable;

import org.junit.jupiter.api.Test;

import io.github.steinbeck.seneca.app.ComponentNames;
import io.github.steinbeck.seneca.app.GuiTestSupport;
import io.github.steinbeck.seneca.app.GuiTestSupport.TestApp;
import io.github.steinbeck.seneca.app.TestHooks;
import io.github.steinbeck.seneca.app.dataset.DatasetFrame;
import io.github.steinbeck.seneca.model.SenecaDataset;

/**
 * "File &gt; New Dataset" (no formula, no atoms, empty spectra): every tab and sub-tab renders and every
 * button works without an exception; after a formula is typed into General Settings the dataset can be
 * built up by hand (signals, H counts, shifts, judges, schedule).
 */
class EmptyDatasetTest {

    @Test
    void newDatasetIsUsableInEveryTab() {
        try (TestApp t = GuiTestSupport.newApp("edit-empty", TestHooks.none())) {
            click(EditSupport.named(t.view, ComponentNames.TOOLBAR + ".new", javax.swing.JButton.class));
            SenecaDataset sd = onEdt(t.app.datasets::current);
            DatasetFrame f = onEdt(() -> t.app.frameOf(sd));
            JTabbedPane tabs = f.tabs();
            JTabbedPane spectra = named(f, ComponentNames.SPECTRA_TABS, JTabbedPane.class);
            JTabbedPane judges = named(f, ComponentNames.JUDGE_TABS, JTabbedPane.class);
            for (int i = 0; i < 5; i++) {
                int k = i;
                onEdt(() -> tabs.setSelectedIndex(k));
                render(t.view, 1000, 700);
            }
            onEdt(() -> tabs.setSelectedIndex(1));
            for (int i = 0; i < 6; i++) {
                int k = i;
                onEdt(() -> spectra.setSelectedIndex(k));
                render(t.view, 1000, 700);
            }
            onEdt(() -> tabs.setSelectedIndex(4));
            for (int i = 0; i < 7; i++) {
                int k = i;
                onEdt(() -> judges.setSelectedIndex(k));
                render(t.view, 1000, 700);
            }
            // every judge button on a dataset without atoms
            for (String key : JudgesPanelTest.JUDGES) {
                press(f, ComponentNames.JUDGE_AUTOCONFIGURE + key);
                press(f, ComponentNames.JUDGE_REPORT + key);
                JCheckBox activate = named(f, ComponentNames.JUDGE_ACTIVATE + key, JCheckBox.class);
                assertEquals(sd.getJudge(key).getEnabled(), EditSupport.selected(activate), key);
            }
            // the 2D judges cannot be assigned without atoms: a warning each (DV-83), no error message
            assertEquals(List.of(), t.ui.shown.stream().filter(s -> !s.title().equals("Autoassignment"))
                    .filter(s -> s.pane().getMessageType() != javax.swing.JOptionPane.WARNING_MESSAGE).toList(),
                    "no error message from the judge buttons");

            // build it up by hand
            JTable general = named(f, ComponentNames.GENERAL_TABLE, JTable.class);
            assertTrue(edit(general, 2, 1, "C3H8O"));
            assertEquals(4, rows(named(f, ComponentNames.ATOM_TABLE, JTable.class)));
            press(f, ComponentNames.SPECTRUM_ADD + "carbon1D");
            JTable c13 = named(f, ComponentNames.SPECTRUM_TABLE + "carbon1D", JTable.class);
            assertTrue(edit(c13, 0, 1, "64.0"));
            JTable atoms = named(f, ComponentNames.ATOM_TABLE, JTable.class);
            assertTrue(edit(atoms, 0, 2, "2"));
            assertTrue(edit(atoms, 0, 3, "64.0"));
            assertEquals(64.0f, sd.getSetOfNodes()[0].shift13C);
            press(f, ComponentNames.JUDGE_AUTOCONFIGURE + "HOSECodeJudge");
            assertEquals(1, sd.getJudge("HOSECodeJudge") instanceof io.github.steinbeck.seneca.judges.HOSECodeJudge h ? h.getCarbonShifts().length : -1);
            assertTrue(edit(named(f, ComponentNames.JUDGE_TABLE + "HybridizationJudge", JTable.class), 3, 3, "1.0"),
                    "table allocated for no atoms grows (DV-66)");
            assertEquals(1, sd.annealingSchedules.size());
            onEdt(() -> tabs.setSelectedIndex(2));
            shot(t.view, 1000, 700, "edit-empty-built-up");
        }
    }
}
