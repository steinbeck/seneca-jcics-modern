package io.github.steinbeck.seneca.golden;

import java.util.List;
import java.util.Set;

import io.github.steinbeck.seneca.golden.JsonValue.JObject;

/**
 * Reader for golden/runs/&lt;key&gt;.&lt;profile&gt;.seed&lt;s&gt;.json (golden/FORMAT.md (d)): one
 * {@code run} record per file (single line).
 */
public final class RunGolden {

    /** Runs above this many iterations are tagged "slow" (excluded by default; mvn -Pslow). */
    public static final long SLOW_ITERATIONS = 200_000;

    /** The golden iteration cap. */
    public static final long ITERATION_CAP = 5_000_000;

    /**
     * Fields the port cannot reproduce byte for byte: the top stack frame of the crash, which names
     * {@code compchem.SetOfRingsFinder.breakBond:302} in the legacy recording (DEVIATIONS.md).
     */
    public static final Set<String> IGNORED_PATHS = Set.of("$.crash.at");

    public final GoldenFiles.RunId id;
    public final String line;
    public final JObject record;

    private RunGolden(GoldenFiles.RunId id, String line) {
        this.id = id;
        this.line = line;
        this.record = JsonValue.parse(line).asObject();
    }

    public static RunGolden load(GoldenFiles.RunId id) {
        List<String> l = GoldenFiles.lines(id.file());
        if (l.size() != 1) throw new IllegalStateException(id.file() + ": expected one line, got " + l.size());
        return new RunGolden(id, l.get(0));
    }

    public String status() {
        return record.str("status");
    }

    public long iterations() {
        return record.lng("iterations");
    }

    public boolean slow() {
        return iterations() > SLOW_ITERATIONS;
    }

    /** exception_in_run or no_result(Q1): the legacy run failed. */
    public boolean failed() {
        return status().equals("exception_in_run") || status().startsWith("no_result");
    }
}
