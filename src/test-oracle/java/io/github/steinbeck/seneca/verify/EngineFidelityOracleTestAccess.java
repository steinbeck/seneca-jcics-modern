package io.github.steinbeck.seneca.verify;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.github.steinbeck.seneca.verify.EngineDiffSupport.Outcome;
import io.github.steinbeck.seneca.verify.EngineDiffSupport.Spec;

/** Verifier "fidelity", round 3: the legacy engine run with a time limit (shared by the round-3 tests). */
final class EngineFidelityOracleTestAccess {

    private EngineFidelityOracleTestAccess() {
    }

    static Outcome legacyWithTimeout(Spec s) throws Exception {
        ExecutorService ex = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "legacy-engine");
            t.setDaemon(true);
            return t;
        });
        try {
            Future<Outcome> f = ex.submit(() -> EngineDiffSupport.runLegacy(s));
            try {
                return f.get(300, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                throw new AssertionError("legacy engine did not finish within 300 s (hang?) for " + s.name);
            }
        } finally {
            ex.shutdownNow();
        }
    }
}
