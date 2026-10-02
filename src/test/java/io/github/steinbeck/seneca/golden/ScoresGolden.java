package io.github.steinbeck.seneca.golden;

import java.util.List;

import io.github.steinbeck.seneca.golden.JsonValue.JObject;

/**
 * Reader for golden/scores/&lt;key&gt;.scores.jsonl (golden/FORMAT.md (b)): a {@code header} and 50
 * {@code structure} records ({@code k, seed, moves, startDraws, hash, ct, gui, all, manual}).
 */
public final class ScoresGolden {

    public final String key;
    public final List<String> lines;
    public final List<JObject> records;

    private ScoresGolden(String key, List<String> lines) {
        this.key = key;
        this.lines = lines;
        this.records = lines.stream().map(l -> JsonValue.parse(l).asObject()).toList();
    }

    public static ScoresGolden load(String key) {
        return new ScoresGolden(key, GoldenFiles.lines(GoldenFiles.scores(key)));
    }

    public JObject header() {
        return records.get(0);
    }

    public List<JObject> structures() {
        return records.stream().filter(r -> "structure".equals(r.type())).toList();
    }

    /** Line index of structure k (for exact line comparison). */
    public String structureLine(int k) {
        for (int i = 0; i < records.size(); i++)
            if ("structure".equals(records.get(i).type()) && records.get(i).integer("k") == k) return lines.get(i);
        throw new IllegalArgumentException("no structure k=" + k);
    }
}
