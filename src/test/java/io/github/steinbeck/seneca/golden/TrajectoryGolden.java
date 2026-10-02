package io.github.steinbeck.seneca.golden;

import java.util.List;

import io.github.steinbeck.seneca.golden.JsonValue.JObject;

/**
 * Reader for golden/trajectories/&lt;key&gt;.&lt;profile&gt;.seed&lt;s&gt;.jsonl.gz (golden/FORMAT.md (c)):
 * {@code meta}, {@code start}, {@code init} x 500 per schedule, {@code schedule} (absent for a Q1
 * trajectory), {@code step} per connected candidate, {@code end}.
 * Compare kT/start_kT fields by lexeme (signed zero).
 */
public final class TrajectoryGolden {

    public final GoldenFiles.RunId id;
    public final List<String> lines;
    private List<JObject> parsed;

    private TrajectoryGolden(GoldenFiles.RunId id, List<String> lines) {
        this.id = id;
        this.lines = lines;
    }

    public static TrajectoryGolden load(GoldenFiles.RunId id) {
        return new TrajectoryGolden(id, GoldenFiles.lines(id.file()));
    }

    public List<JObject> records() {
        if (parsed == null) parsed = lines.stream().map(l -> JsonValue.parse(l).asObject()).toList();
        return parsed;
    }

    public JObject meta() {
        return JsonValue.parse(lines.get(0)).asObject();
    }

    public JObject end() {
        return JsonValue.parse(lines.get(lines.size() - 1)).asObject();
    }

    /** "step_limit", "finished", "no_main_step(Q1)", "exception_in_run" (or "timeout": not golden). */
    public String endReason() {
        return end().str("reason");
    }

    public boolean failed() {
        String r = endReason();
        return r.equals("exception_in_run") || r.startsWith("no_main_step");
    }
}
