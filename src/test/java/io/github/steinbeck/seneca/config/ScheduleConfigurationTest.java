package io.github.steinbeck.seneca.config;

import org.junit.jupiter.api.Test;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.model.AnnealingSchedule;
import io.github.steinbeck.seneca.model.SenecaDataset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The schedule editor core (DV-67); the legacy side is oracle/ScheduleConfigurationOracleTest. */
class ScheduleConfigurationTest {

    private static SenecaDataset dataset(int n) {
        SenecaDataset sd = new SenecaDataset("s");
        for (int i = 0; i < n; i++) sd.annealingSchedules.add(new AnnealingSchedule(0.5 + i / 10.0, 10 + i, 100 + i, 0.9));
        return sd;
    }

    private static String values(AnnealingSchedule a) {
        return a.initialAcceptanceProbability + "/" + a.plateauSteps + "/" + a.asymptoticCoolingFactor + "/" + a.maxUphillSteps;
    }

    @Test
    void legacyParsingAndDisplayInBothModes() {
        for (LegacyCompat c : LegacyCompat.values()) {
            SenecaDataset sd = dataset(1);
            ScheduleConfiguration sc = new ScheduleConfiguration(sd, c);
            assertEquals(0, sc.selectedIndex());
            assertEquals("0.5", sc.acceptanceProbability());
            assertEquals("100", sc.plateauSteps());
            assertEquals("0.9", sc.coolingFactor());
            assertEquals("10.0", sc.maxUphillSteps(), "uphill steps shown as a double, as legacy");
            sc.update();                                   // unchanged fields: a no-op thanks to the double parse
            assertEquals("0.5/100/0.9/10", values(sd.annealingSchedules.get(0)));
            sc.setPlateauSteps(" 500.9 ");
            sc.setMaxUphillSteps("1e3");
            sc.update();
            assertEquals("0.5/500/0.9/1000", values(sd.annealingSchedules.get(0)), c.name());
            sc.addNew();
            assertEquals(1, sc.selectedIndex());
            assertEquals("0.8", sc.acceptanceProbability());
            assertEquals("50.0", sc.maxUphillSteps());
        }
    }

    @Test
    void updateWithAnUnparsableField() {
        SenecaDataset strict = dataset(1), def = dataset(1);
        ScheduleConfiguration s = new ScheduleConfiguration(strict, LegacyCompat.STRICT);
        ScheduleConfiguration d = new ScheduleConfiguration(def, LegacyCompat.DEFAULT);
        for (ScheduleConfiguration sc : new ScheduleConfiguration[] {s, d}) {
            sc.setAcceptanceProbability("0.7");
            sc.setCoolingFactor("x");
            assertThrows(NumberFormatException.class, sc::update);
        }
        assertEquals("0.7/100/0.9/10", values(strict.annealingSchedules.get(0)), "STRICT: fields before the bad one applied");
        assertEquals("0.5/100/0.9/10", values(def.annealingSchedules.get(0)), "DEFAULT: nothing applied");
    }

    @Test
    void removeInStrictKeepsTheLegacySelectionShuffle() {
        SenecaDataset sd = dataset(4);
        ScheduleConfiguration sc = new ScheduleConfiguration(sd, LegacyCompat.STRICT);
        assertEquals(3, sc.selectedIndex());
        sc.remove();                                       // removes 3, selection 2 then 1
        assertEquals(3, sd.annealingSchedules.size());
        assertEquals(1, sc.selectedIndex());
        assertEquals("0.6", sc.acceptanceProbability());
        sc.select(0);
        sc.remove();                                       // removes 0; selection stays 0, fields still show the removed one
        assertEquals(0, sc.selectedIndex());
        assertEquals("0.5", sc.acceptanceProbability());
        sc.update();                                       // copies the removed schedule's values into the next one
        assertEquals("0.5/100/0.9/10", values(sd.annealingSchedules.get(0)));
        sc.remove();
        sc.remove();
        assertEquals(0, sd.annealingSchedules.size());
        assertThrows(ArrayIndexOutOfBoundsException.class, sc::update);
    }

    @Test
    void removeInDefaultSelectsTheScheduleThatTookItsPlace() {
        SenecaDataset sd = dataset(4);
        ScheduleConfiguration sc = new ScheduleConfiguration(sd, LegacyCompat.DEFAULT);
        sc.select(1);
        sc.remove();
        assertEquals(1, sc.selectedIndex());
        assertEquals("0.7", sc.acceptanceProbability(), "the former third schedule");
        sc.select(2);
        sc.remove();                                       // the last one: select the new last one
        assertEquals(1, sc.selectedIndex());
        assertEquals("0.7", sc.acceptanceProbability());
        sc.select(0);
        sc.remove();
        assertEquals(0, sc.selectedIndex());
        assertEquals("0.7", sc.acceptanceProbability());
        sc.update();
        assertEquals("0.7/102/0.9/12", values(sd.annealingSchedules.get(0)), "update writes the shown schedule's own values");
        sc.remove();
        assertEquals(-1, sc.selectedIndex());
        assertEquals("", sc.acceptanceProbability());
        sc.update();                                       // no selection: nothing happens
        sc.remove();
        assertEquals(0, sd.annealingSchedules.size());
    }
}
