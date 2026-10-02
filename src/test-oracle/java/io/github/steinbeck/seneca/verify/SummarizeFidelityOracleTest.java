package io.github.steinbeck.seneca.verify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.model.AnnealingSchedule;
import io.github.steinbeck.seneca.structgen.EngineLog;
import io.github.steinbeck.seneca.structgen.RunController;
import io.github.steinbeck.seneca.structgen.RunInput;
import io.github.steinbeck.seneca.structgen.RunOptions;
import io.github.steinbeck.seneca.structgen.RunSlot;
import io.github.steinbeck.seneca.structgen.StructureGeneratorResult;
import io.github.steinbeck.seneca.verify.EngineDiffSupport.Spec;

/**
 * Verifier "fidelity": "Summarize Result" with a slot that has no task.
 *
 * <p>Legacy {@code SGServerPanel.ShowCurrentAction} collects the results of all servers inside ONE
 * {@code try}; {@code StructureGeneratorServer.getTaskResult()} dereferences {@code stoicGent}, which is
 * null for a never-started or reset server, so the NPE aborts the collection loop and every later server
 * contributes nothing (and with no structure collected, {@code removeIsomorphism()} loops for ever).
 * The port's {@code RunController.summarize} skips such slots ({@code if (!s.hasRun()) continue;}) in
 * both modes and collects the later slots.
 */
@Tag("verify")
class SummarizeFidelityOracleTest {

    @Test
    void legacyGetTaskResultOnIdleServerThrows() throws Exception {
        seneca.engine.StructureGeneratorServer server = new seneca.engine.StructureGeneratorServer("local0");
        try {
            assertThrows(NullPointerException.class, server::getTaskResult,
                    "legacy getTaskResult() on a server without a task (never started / reset)");
        } finally {
            java.rmi.server.UnicastRemoteObject.unexportObject(server, true);
        }
    }

    @Test
    void portSummarizeSkipsIdleSlotInStrict() throws Exception {
        Spec s = EngineFidelityOracleTest.pinene();
        RunInput in = new RunInput(EngineDiffSupport.portAtoms(s), EngineDiffSupport.portJudges(s),
                List.of(new AnnealingSchedule(0.8, 50, 100, 0.9)), "x");
        RunController rc = new RunController();
        RunSlot idle = rc.addSlot();     // "Start local server", never started (legacy: stoicGent == null)
        RunSlot busy = rc.addSlot();
        rc.start(busy, in, new RunOptions(1L, LegacyCompat.STRICT, 2000, null, EngineLog.NONE));
        while (busy.isRunning()) Thread.sleep(20);
        int produced = busy.result().size();
        String outcome;
        try {
            StructureGeneratorResult merged = rc.summarize(LegacyCompat.STRICT);
            outcome = "returned " + merged.size() + " structures";
        } catch (RuntimeException e) {
            outcome = "threw " + e;
        }
        System.out.println("[verify] slot local1 produced " + produced + " structures, idle slot " + idle
                + " first; STRICT summarize " + outcome
                + " (legacy: collection aborted at local0 -> 0 structures -> removeIsomorphism loops for ever,"
                + " which the port's STRICT reports as IllegalStateException)");
        assertEquals(true, outcome.startsWith("threw java.lang.IllegalStateException"), outcome);
        // DEFAULT (DV-47): the idle slot is skipped and local1's structures are summarized
        assertEquals(true, rc.summarize(LegacyCompat.DEFAULT).size() > 0, "DEFAULT collects the later slots");
    }
}
