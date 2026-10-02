package io.github.steinbeck.seneca.app.edit;

import static io.github.steinbeck.seneca.app.GuiTestSupport.flushEdt;
import static io.github.steinbeck.seneca.app.GuiTestSupport.labels;
import static io.github.steinbeck.seneca.app.GuiTestSupport.menuItem;
import static io.github.steinbeck.seneca.app.GuiTestSupport.onEdt;
import static io.github.steinbeck.seneca.app.GuiTestSupport.shot;
import static io.github.steinbeck.seneca.app.edit.EditSupport.cell;
import static io.github.steinbeck.seneca.app.edit.EditSupport.columns;
import static io.github.steinbeck.seneca.app.edit.EditSupport.edit;
import static io.github.steinbeck.seneca.app.edit.EditSupport.named;
import static io.github.steinbeck.seneca.app.edit.EditSupport.press;
import static io.github.steinbeck.seneca.app.edit.EditSupport.rows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import javax.swing.JMenu;
import javax.swing.JTable;

import org.junit.jupiter.api.Test;

import io.github.steinbeck.seneca.app.ComponentNames;
import io.github.steinbeck.seneca.app.GuiTestSupport;
import io.github.steinbeck.seneca.app.GuiTestSupport.TestApp;
import io.github.steinbeck.seneca.app.TestHooks;
import io.github.steinbeck.seneca.app.dataset.DatasetFrame;
import io.github.steinbeck.seneca.chem.MolecularFormula;
import io.github.steinbeck.seneca.judges.HybridizationJudge;
import io.github.steinbeck.seneca.model.SenecaDataset;

/** Tab "General Settings" (F-40). */
class GeneralSettingsPanelTest {

    @Test
    void rowsAndEdits() {
        try (TestApp t = GuiTestSupport.newApp("edit-general", TestHooks.none())) {
            DatasetFrame f = EditSupport.open(t, "pinene");
            SenecaDataset sd = f.dataset();
            JTable table = named(f, ComponentNames.GENERAL_TABLE, JTable.class);
            assertEquals(List.of("Parameter", "Setting"), columns(table));
            assertEquals(4, rows(table));
            assertEquals(List.of("Filename", "Description", "Molecular Formula", "Molecular Mass"),
                    List.of(cell(table, 0, 0), cell(table, 1, 0), cell(table, 2, 0), cell(table, 3, 0)));
            assertEquals(List.of("PINEN.COL", "none", "C10H16", String.valueOf(MolecularFormula.mass("C10H16"))),
                    List.of(cell(table, 0, 1), cell(table, 1, 1), cell(table, 2, 1), cell(table, 3, 1)));
            assertFalse(onEdt(() -> table.isCellEditable(0, 0)), "column 1 is not editable");
            shot(t.view, 1000, 700, "edit-general-1-read");

            // Filename: dataset name, window title, Datasets menu
            assertTrue(edit(table, 0, 1, "alpha-pinene"));
            flushEdt();
            assertEquals("alpha-pinene", sd.getName());
            assertEquals("alpha-pinene", onEdt(f::getTitle));
            assertEquals(List.of("alpha-pinene"), labels((JMenu) menuItem(t.menuBar, "Datasets", "switch")));
            assertTrue(edit(table, 1, 1, "monoterpene"));
            assertEquals("monoterpene", sd.getDescription());

            // Molecular Mass: a float is stored and shown; anything else ignored
            assertTrue(edit(table, 3, 1, "136.2"));
            assertEquals(136.2f, sd.getMolecularMass());
            assertEquals("136.2", cell(table, 3, 1));
            edit(table, 3, 1, "heavy");
            assertEquals(136.2f, sd.getMolecularMass());

            // Molecular Formula: re-entering the same formula keeps the assignments (DV-85)
            EditSupport.showTab(f, "Atom Properties");
            press(f, ComponentNames.AUTOASSIGN);
            assertNotNull(sd.getSetOfNodes()[0].shift13C);
            EditSupport.showTab(f, "General Settings");
            assertTrue(edit(table, 2, 1, "C10H16"));
            assertNotNull(sd.getSetOfNodes()[0].shift13C);
            assertEquals(16, MolecularFormula.atomCount(sd.getSetOfNodes(), "H"));

            // a new formula regenerates the atoms; every tab follows
            assertTrue(edit(table, 2, 1, "C10H16O"));
            assertEquals("C10H16O", sd.getMolecularFormula());
            assertEquals(11, sd.getSetOfNodes().length);
            assertNull(sd.getSetOfNodes()[0].shift13C);
            assertEquals(11, rows(named(f, ComponentNames.ATOM_TABLE, JTable.class)));
            assertEquals(11, rows(named(f, ComponentNames.JUDGE_TABLE + "HybridizationJudge", JTable.class)));
            assertEquals(11, rows(named(f, ComponentNames.JUDGE_TABLE + "HeteroBondsJudge", JTable.class)));
            assertEquals("O", cell(named(f, ComponentNames.ATOM_TABLE, JTable.class), 10, 1));
            assertEquals("C10H16O", onEdt(() -> t.view.statusBar().status(2)));
            // the Hybridization table was allocated for 10 atoms when the window opened: row 10 is empty
            // until edited, then the table grows (DEFAULT, DV-66)
            JTable hy = named(f, ComponentNames.JUDGE_TABLE + "HybridizationJudge", JTable.class);
            assertEquals("", cell(hy, 10, 3));
            assertTrue(edit(hy, 10, 3, "0.5"));
            assertEquals(50, sd.getJudge(HybridizationJudge.class).scores[10][0]);
            assertEquals("50", cell(hy, 10, 3));
            shot(t.view, 1000, 700, "edit-general-2-new-formula");

            // an empty formula is refused with a message
            int shown = t.ui.shown.size();
            assertTrue(edit(table, 2, 1, " "));
            assertEquals("C10H16O", sd.getMolecularFormula());
            assertEquals(shown + 1, t.ui.shown.size());
            assertEquals("General Settings", t.ui.lastShown().title());
        }
    }
}
