package io.github.steinbeck.seneca.app.run;

import java.util.ArrayList;
import java.util.List;

import io.github.steinbeck.seneca.structgen.AnnealingLog;

/**
 * The data behind the "Annealing Data" chart of one "Structure Generation" tab (port of
 * {@code SGServerPanel.AnnealingDataSource} and its client-side {@code AnnealingLog}; feature-inventory
 * F-68, F-70). No Swing; EDT only.
 *
 * <ul>
 *   <li>One series per run slot ("host 1", "host 2", ... = table row): the annealing-log entries the
 *       polling fetched for that slot, appended in order (x = the entry's iteration, lagged by one step
 *       of 500 as in legacy). Created on the first data for as many slots as exist then (legacy
 *       {@code init(sgServerProcesses.size())}); grows if a later row brings data (legacy threw).</li>
 *   <li>{@link #clear()}: "Reset selected" drops all chart data, of every slot (legacy
 *       {@code setData(null)}); afterwards {@link #hasData()} is false ("no data").</li>
 *   <li>{@link #maxScore()}: the max score of the last polled slot with a best structure; drawn as the
 *       horizontal series "max. Score" in score mode.</li>
 *   <li>{@link #display()}: what the y axis shows, the best score or kT; a click on the chart toggles it.</li>
 *   <li>{@link #logLines()}: the "Log Annealing Data" table.</li>
 * </ul>
 */
public final class AnnealingData {

    /** What the chart's y axis shows (legacy {@code SCORES} / {@code KT}). */
    public enum Display {
        SCORES("Score"), KT("kT");

        /** The y-axis label. */
        public final String axisLabel;

        Display(String axisLabel) {
            this.axisLabel = axisLabel;
        }
    }

    private List<List<AnnealingLog.Entry>> series;   // null = no data (legacy al == null)
    private float maxScore;
    private Display display = Display.SCORES;

    /** Appends {@code entries} to the series of row {@code row}; {@code rows} = current number of slots. */
    public void add(int row, int rows, List<AnnealingLog.Entry> entries) {
        if (series == null) {
            series = new ArrayList<>();
            for (int i = 0; i < rows; i++) series.add(new ArrayList<>());
        }
        while (series.size() <= row) series.add(new ArrayList<>());
        series.get(row).addAll(entries);
    }

    /** "Reset selected": all chart data is dropped. */
    public void clear() {
        series = null;
    }

    public boolean hasData() {
        return series != null;
    }

    /** Number of series (0 without data). */
    public int seriesCount() {
        return series == null ? 0 : series.size();
    }

    /** The entries of series {@code i}. */
    public List<AnnealingLog.Entry> series(int i) {
        return List.copyOf(series.get(i));
    }

    /** Series name: {@code "host " + (i + 1)} (legacy {@code getSeriesName}). */
    public static String seriesName(int i) {
        return "host " + (i + 1);
    }

    public float maxScore() {
        return maxScore;
    }

    public void setMaxScore(float maxScore) {
        this.maxScore = maxScore;
    }

    public Display display() {
        return display;
    }

    /** A click on the chart: score &lt;-&gt; kT. */
    public void toggleDisplay() {
        display = display == Display.SCORES ? Display.KT : Display.SCORES;
    }

    /** The y value of an entry in the current display mode. */
    public float y(AnnealingLog.Entry e) {
        return display == Display.SCORES ? e.bestScore() : e.kT();
    }

    /**
     * "Log Annealing Data" (legacy {@code AnnealingDataSource.logResults}): the header
     * {@code "Iteration; Server 1; Server 2; ...; "}, then one line per item index
     * {@code "<x>; <score1>; <score2>; ...; "} up to the longest series (a series without that item
     * leaves its field empty; x is the iteration of the last series that has the item). Values as
     * {@link Float#toString(float)}. Lines without line separator; empty without data (legacy threw a
     * NullPointerException and wrote nothing).
     */
    public List<String> logLines() {
        List<String> lines = new ArrayList<>();
        if (series == null) return lines;
        int n = series.size();
        int maxItemCount = 0;
        for (List<AnnealingLog.Entry> s : series) maxItemCount = Math.max(maxItemCount, s.size());
        Float[][] data = new Float[maxItemCount][n + 1];
        for (int x = 0; x < n; x++) {
            List<AnnealingLog.Entry> s = series.get(x);
            for (int y = 0; y < s.size(); y++) {
                data[y][0] = s.get(y).iteration();
                data[y][x + 1] = s.get(y).bestScore();
            }
        }
        StringBuilder header = new StringBuilder("Iteration; ");
        for (int x = 0; x < n; x++) header.append("Server ").append(x + 1).append("; ");
        lines.add(header.toString());
        for (int x = 0; x < maxItemCount; x++) {
            StringBuilder row = new StringBuilder();
            for (int y = 0; y < n + 1; y++) {
                if (data[x][y] != null) row.append(data[x][y]);
                row.append("; ");
            }
            lines.add(row.toString());
        }
        return lines;
    }
}
