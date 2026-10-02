package io.github.steinbeck.seneca.golden;

/**
 * Calls into the port from the golden tests. Checked exceptions are wrapped in an {@link AssertionError}
 * so that a test fails with the port's exception as cause. (While the port was incomplete this helper
 * also turned "not implemented yet" stubs into skipped tests; the stubs are gone.)
 */
public final class Port {

    private Port() {
    }

    @FunctionalInterface
    public interface Call<T> {
        T call() throws Exception;
    }

    @FunctionalInterface
    public interface Action {
        void run() throws Exception;
    }

    public static <T> T call(Call<T> c) {
        try {
            return c.call();
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Exception e) {
            throw new AssertionError("port threw " + e, e);
        }
    }

    public static void run(Action a) {
        call(() -> {
            a.run();
            return null;
        });
    }
}
