package io.github.steinbeck.seneca.judges;

/**
 * Notified when a judge's configuration or enabled flag changes (legacy {@code JudgeListener}; the
 * GUI's configurator panels listen). Not copied with the judge (legacy field is transient).
 */
@FunctionalInterface
public interface JudgeListener {
    void judgeDataChanged();
}
