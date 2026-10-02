package io.github.steinbeck.seneca.app.runs;

import static io.github.steinbeck.seneca.app.GuiTestSupport.onEdt;
import static io.github.steinbeck.seneca.app.GuiTestSupport.shot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.steinbeck.seneca.app.ComponentNames;
import io.github.steinbeck.seneca.app.GoldenRunAssert;
import io.github.steinbeck.seneca.app.GuiTestSupport;
import io.github.steinbeck.seneca.app.GuiTestSupport.TestApp;
import io.github.steinbeck.seneca.app.TestHooks;
import io.github.steinbeck.seneca.app.structure.StructureDisplayPanel;
import io.github.steinbeck.seneca.config.GuiRecipe;
import io.github.steinbeck.seneca.config.Profile;
import io.github.steinbeck.seneca.structgen.RunInput;
import io.github.steinbeck.seneca.structgen.RunOptions;
import io.github.steinbeck.seneca.structgen.RunResult;
import io.github.steinbeck.seneca.structgen.StochasticGenerator;
import io.github.steinbeck.seneca.structgen.StructureGeneratorResult;

/**
 * pinene "gui" with three slots, all selected, one "Start as selected", seeds 1, 2, 3 through the hook
 * (docs/port/gui-design.md 10.3): concurrent runs give exactly the golden seeds 1 and 2 (seed 3: the
 * core's DEFAULT run, legacy had no result) and Summarize merges all three hosts. Seed 2 runs ~40 s.
 */
@Tag("slow")
class RunPanelSlowTest {

    @Test
    void threeSlotsReproduceGoldenSeeds1To3() throws Exception {
        try (TestApp t = GuiTestSupport.newApp("runs-slow", new TestHooks(1L, RunFixture.CAP))) {
            RunFixture fx = RunFixture.open(t, "pinene");
            fx.addSlots(3);
            fx.select(0, 1, 2);
            List<String> enabled = fx.enabledJudges();
            fx.press(ComponentNames.RUN_START);
            fx.settle();
            // poll while running (as the timer would)
            while (fx.slot(1).isRunning()) {
                fx.poll();
                Thread.sleep(200);
            }
            RunResult[] r = {fx.awaitResult(0), fx.awaitResult(1), fx.awaitResult(2)};
            GoldenRunAssert.assertMatches(GoldenRunAssert.golden("pinene", "gui", 1), enabled, r[0]);
            GoldenRunAssert.assertMatches(GoldenRunAssert.golden("pinene", "gui", 2), enabled, r[1]);
            RunInput in = RunInput.fromDataset(GuiRecipe.prepare(GuiTestSupport.sml("pinene"), "pinene", Profile.GUI, t.app.compat).sd, t.app.compat);
            RunFixture.assertSameRun("seed 3", new StochasticGenerator(in, RunOptions.application().withSeed(3L)
                    .withIterationCap(RunFixture.CAP)).run(), r[2]);
            GuiTestSupport.flushEdt();
            for (int i = 0; i < 3; i++) fx.poll();
            for (int i = 0; i < 3; i++) {
                assertEquals("Holding Results", fx.row(i).get(5));
                assertEquals(String.valueOf(r[i].iterations()), fx.row(i).get(6));
            }
            // the chart holds every log entry of every run exactly once, in order (polled during the runs)
            for (int i = 0; i < 3; i++) {
                int k = i;
                assertEquals(fx.slot(i).annealingLogSince(0), onEdt(() -> fx.panel.chart().data().series(k)), "series " + i);
            }
            shot(t.view, 1000, 700, "runs-slow-three-hosts");
            shot(fx.panel.chart(), 500, 350, "runs-slow-chart");

            fx.press(ComponentNames.RUN_SUMMARIZE);
            StructureDisplayPanel display = (StructureDisplayPanel) t.ui.lastWindow().content;
            StructureGeneratorResult merged = fx.frame.runs().summarize(t.app.compat);
            assertEquals(merged.size(), onEdt(display::tileList).size());
            boolean[] hosts = new boolean[3];
            for (int i = 0; i < merged.size(); i++)
                for (int h = 0; h < 3; h++)
                    if (merged.scoreSummaries.get(i).description.endsWith("<p>Result from host " + (h + 1) + "</p>")) hosts[h] = true;
            assertTrue(hosts[0] && hosts[1], "results of hosts 1 and 2 in the summary");
        }
    }
}
