package io.github.steinbeck.seneca.app.edit;

import static io.github.steinbeck.seneca.app.GuiTestSupport.onEdt;
import static io.github.steinbeck.seneca.app.GuiTestSupport.shot;
import static io.github.steinbeck.seneca.app.edit.EditSupport.named;
import static io.github.steinbeck.seneca.app.edit.EditSupport.press;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JList;
import javax.swing.JTextField;

import org.junit.jupiter.api.Test;

import io.github.steinbeck.seneca.app.ComponentNames;
import io.github.steinbeck.seneca.app.GuiTestSupport;
import io.github.steinbeck.seneca.app.GuiTestSupport.TestApp;
import io.github.steinbeck.seneca.app.TestHooks;
import io.github.steinbeck.seneca.app.dataset.DatasetFrame;
import io.github.steinbeck.seneca.model.AnnealingSchedule;
import io.github.steinbeck.seneca.model.SenecaDataset;

/** Tab "Annealing Schedule" (F-43): fields, list, Update, Remove, New (DEFAULT, DV-67). */
class AnnealingSchedulePanelTest {

    static List<String> fields(DatasetFrame f) {
        return onEdt(() -> List.of(
                named(f, ComponentNames.SCHEDULE_ACCEPTANCE, JTextField.class).getText(),
                named(f, ComponentNames.SCHEDULE_PLATEAU, JTextField.class).getText(),
                named(f, ComponentNames.SCHEDULE_COOLING, JTextField.class).getText(),
                named(f, ComponentNames.SCHEDULE_UPHILL, JTextField.class).getText()));
    }

    static void type(DatasetFrame f, String acc, String plateau, String cooling, String uphill) {
        onEdt(() -> {
            named(f, ComponentNames.SCHEDULE_ACCEPTANCE, JTextField.class).setText(acc);
            named(f, ComponentNames.SCHEDULE_PLATEAU, JTextField.class).setText(plateau);
            named(f, ComponentNames.SCHEDULE_COOLING, JTextField.class).setText(cooling);
            named(f, ComponentNames.SCHEDULE_UPHILL, JTextField.class).setText(uphill);
        });
    }

    static List<String> listed(JList<?> list) {
        return onEdt(() -> {
            List<String> l = new ArrayList<>();
            for (int i = 0; i < list.getModel().getSize(); i++) l.add(String.valueOf(list.getModel().getElementAt(i)));
            return l;
        });
    }

    @Test
    void editSchedules() {
        try (TestApp t = GuiTestSupport.newApp("edit-schedule", TestHooks.none())) {
            DatasetFrame f = EditSupport.open(t, "pinene");
            SenecaDataset sd = f.dataset();
            EditSupport.showTab(f, "Annealing Schedule");
            JList<?> list = named(f, ComponentNames.SCHEDULE_LIST, JList.class);
            assertEquals(List.of("A-F:0.95-IAP:0.8-PS:500-UHS: 50"), listed(list));
            assertEquals(0, onEdt(list::getSelectedIndex), "last schedule selected");
            assertEquals(List.of("0.8", "500", "0.95", "50.0"), fields(f));
            shot(t.view, 1000, 700, "edit-schedule-1-read");

            // New ->: default schedule appended and selected
            press(f, ComponentNames.SCHEDULE_NEW);
            assertEquals(2, sd.annealingSchedules.size());
            assertEquals(1, onEdt(list::getSelectedIndex));
            assertEquals(List.of("0.8", "500", "0.95", "50.0"), fields(f));

            // -> Update ->: the fields into the selected schedule (plateau/uphill truncated to long)
            type(f, "0.7", "1500.9", "0.9", "60");
            press(f, ComponentNames.SCHEDULE_UPDATE);
            AnnealingSchedule s1 = sd.annealingSchedules.get(1);
            assertEquals(0.7, s1.initialAcceptanceProbability);
            assertEquals(1500, s1.plateauSteps);
            assertEquals(0.9, s1.asymptoticCoolingFactor);
            assertEquals(60, s1.maxUphillSteps);
            assertEquals("A-F:0.9-IAP:0.7-PS:1500-UHS: 60", listed(list).get(1));
            shot(t.view, 1000, 700, "edit-schedule-2-updated");

            // a field that is not a number: message, nothing changed (DEFAULT)
            type(f, "0.5", "abc", "0.8", "10");
            press(f, ComponentNames.SCHEDULE_UPDATE);
            assertEquals("Annealing Schedule", t.ui.lastShown().title());
            assertTrue(t.ui.lastShown().message().startsWith("The schedule was not changed"));
            assertEquals(0.7, s1.initialAcceptanceProbability);
            assertEquals(1500, s1.plateauSteps);
            shot(t.ui.lastShown().pane(), "edit-dialog-schedule-not-a-number");

            // selecting a schedule loads its fields
            onEdt(() -> list.setSelectedIndex(0));
            assertEquals(List.of("0.8", "500", "0.95", "50.0"), fields(f));

            // Remove <- on the first: the next one takes its place and is shown (DEFAULT)
            press(f, ComponentNames.SCHEDULE_REMOVE);
            assertEquals(1, sd.annealingSchedules.size());
            assertEquals(s1, sd.annealingSchedules.get(0));
            assertEquals(0, onEdt(list::getSelectedIndex));
            assertEquals(List.of("0.7", "1500", "0.9", "60.0"), fields(f));
            assertEquals(List.of("A-F:0.9-IAP:0.7-PS:1500-UHS: 60"), listed(list));

            // removing the last one: empty list and fields, Update does nothing
            press(f, ComponentNames.SCHEDULE_REMOVE);
            assertEquals(0, sd.annealingSchedules.size());
            assertEquals(List.of("", "", "", ""), fields(f));
            assertEquals(-1, onEdt(list::getSelectedIndex));
            int shown = t.ui.shown.size();
            press(f, ComponentNames.SCHEDULE_UPDATE);
            press(f, ComponentNames.SCHEDULE_REMOVE);
            assertEquals(shown, t.ui.shown.size());
            assertEquals(0, sd.annealingSchedules.size());
            shot(t.view, 1000, 700, "edit-schedule-3-empty");
            press(f, ComponentNames.SCHEDULE_NEW);
            assertEquals(1, sd.annealingSchedules.size());
            assertEquals("A-F:0.95-IAP:0.8-PS:500-UHS: 50", listed(list).get(0));
        }
    }

    @Test
    void datasetWithoutSchedules() {
        try (TestApp t = GuiTestSupport.newApp("edit-schedule-none", TestHooks.none())) {
            DatasetFrame f = EditSupport.open(t, "Hyperaspine");
            JList<?> list = named(f, ComponentNames.SCHEDULE_LIST, JList.class);
            assertEquals(List.of(), listed(list));
            assertEquals(List.of("", "", "", ""), fields(f));
            press(f, ComponentNames.SCHEDULE_NEW);
            assertEquals(List.of("A-F:0.95-IAP:0.8-PS:500-UHS: 50"), listed(list));
            assertEquals(List.of("0.8", "500", "0.95", "50.0"), fields(f));
        }
    }
}
