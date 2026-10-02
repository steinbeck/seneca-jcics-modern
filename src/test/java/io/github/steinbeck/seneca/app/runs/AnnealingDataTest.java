package io.github.steinbeck.seneca.app.runs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.steinbeck.seneca.app.run.AnnealingData;
import io.github.steinbeck.seneca.structgen.AnnealingLog;

/** The chart data and the "Log Annealing Data" table (legacy {@code AnnealingDataSource}, F-68, F-70). */
class AnnealingDataTest {

    static AnnealingLog.Entry e(float x, float kT, float score) {
        return new AnnealingLog.Entry(x, kT, score);
    }

    @Test
    void seriesLogTableToggleAndReset() {
        AnnealingData d = new AnnealingData();
        assertFalse(d.hasData());
        assertEquals(List.of(), d.logLines(), "2001 wrote nothing without data");

        // first data: one series per slot existing then (3), row 2 has more items than row 0
        d.add(0, 3, List.of(e(0, 900, 100), e(500, 800, 150)));
        d.add(2, 3, List.of(e(0, 700, 50), e(500, 600, 60), e(1000, 500, 70)));
        assertEquals(3, d.seriesCount());
        assertEquals("host 3", AnnealingData.seriesName(2));
        assertEquals(List.of(
                "Iteration; Server 1; Server 2; Server 3; ",
                "0.0; 100.0; ; 50.0; ",
                "500.0; 150.0; ; 60.0; ",
                "1000.0; ; ; 70.0; "), d.logLines());

        // a fourth slot brings data later: the series list grows (2001: ArrayIndexOutOfBounds, lost)
        d.add(3, 4, List.of(e(0, 1, 2)));
        assertEquals(4, d.seriesCount());
        assertEquals("Iteration; Server 1; Server 2; Server 3; Server 4; ", d.logLines().get(0));

        assertEquals(AnnealingData.Display.SCORES, d.display());
        assertEquals(150f, d.y(e(500, 800, 150)));
        d.toggleDisplay();
        assertEquals(AnnealingData.Display.KT, d.display());
        assertEquals("kT", d.display().axisLabel);
        assertEquals(800f, d.y(e(500, 800, 150)));
        d.toggleDisplay();
        assertEquals("Score", d.display().axisLabel);

        d.setMaxScore(2400);
        d.clear();
        assertFalse(d.hasData());
        assertEquals(0, d.seriesCount());
        assertEquals(2400f, d.maxScore(), "Reset keeps the max score (2001)");
        d.add(1, 2, List.of());
        assertTrue(d.hasData());
        assertEquals(2, d.seriesCount());
    }
}
