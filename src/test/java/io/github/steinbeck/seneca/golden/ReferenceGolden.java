package io.github.steinbeck.seneca.golden;

import java.util.List;

import io.github.steinbeck.seneca.golden.JsonValue.JObject;

/**
 * Reader for golden/references/&lt;key&gt;.ref.jsonl (golden/FORMAT.md (e)): a {@code reference}
 * record ({@code defined}, {@code mappable}, ...) and, if mappable, one {@code best} record per profile
 * (gui, paper, all, manual) with the best labelling ({@code ct} in dataset order) and its scores.
 */
public final class ReferenceGolden {

    public final String key;
    public final List<String> lines;
    public final List<JObject> records;

    private ReferenceGolden(String key, List<String> lines) {
        this.key = key;
        this.lines = lines;
        this.records = lines.stream().map(l -> JsonValue.parse(l).asObject()).toList();
    }

    public static ReferenceGolden load(String key) {
        return new ReferenceGolden(key, GoldenFiles.lines(GoldenFiles.reference(key)));
    }

    public JObject reference() {
        return records.get(0);
    }

    public boolean defined() {
        return reference().bool("defined");
    }

    public boolean mappable() {
        return defined() && reference().bool("mappable");
    }

    /** {@code best} records in file order. */
    public List<JObject> best() {
        return records.stream().filter(r -> "best".equals(r.type())).toList();
    }
}
