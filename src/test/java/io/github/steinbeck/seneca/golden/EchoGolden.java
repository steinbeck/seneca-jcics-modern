package io.github.steinbeck.seneca.golden;

import java.util.List;

import io.github.steinbeck.seneca.golden.JsonValue.JObject;

/**
 * Reader for golden/datasets/&lt;key&gt;.echo.jsonl (golden/FORMAT.md (a)). Stages: {@code as_read},
 * {@code configured_gui|all|paper|manual} (atoms/spectra/schedules only for gui).
 */
public final class EchoGolden {

    public final String key;
    public final List<String> lines;
    public final List<JObject> records;

    private EchoGolden(String key, List<String> lines) {
        this.key = key;
        this.lines = lines;
        this.records = lines.stream().map(l -> JsonValue.parse(l).asObject()).toList();
    }

    public static EchoGolden load(String key) {
        return new EchoGolden(key, GoldenFiles.lines(GoldenFiles.echo(key)));
    }

    private JObject find(String type, String field, String value) {
        for (JObject r : records) {
            if (type.equals(r.type()) && (field == null || (r.has(field) && value.equals(r.str(field))))) return r;
        }
        return null;
    }

    private JObject find(String type, String f1, String v1, String f2, String v2) {
        for (JObject r : records) {
            if (type.equals(r.type()) && r.has(f1) && v1.equals(r.str(f1)) && r.has(f2) && v2.equals(r.str(f2))) return r;
        }
        return null;
    }

    public JObject dataset() {
        return find("dataset", null, null);
    }

    /** {@code atoms} record of a stage ("as_read" or "configured_gui"), or null. */
    public JObject atoms(String stage) {
        return find("atoms", "stage", stage);
    }

    public JObject spectrum(String stage, String slot) {
        return find("spectrum", "stage", stage, "slot", slot);
    }

    public JObject schedules(String stage) {
        return find("schedules", "stage", stage);
    }

    /** {@code judges} record of a stage ("as_read", "configured_&lt;profile&gt;"). */
    public JObject judges(String stage) {
        return find("judges", "stage", stage);
    }

    public JObject recipe(String profile) {
        return find("recipe", "profile", profile);
    }

    public JObject generatorInput(String profile) {
        return find("generatorInput", "profile", profile);
    }

    /** Number of heavy atoms. */
    public int nAtoms() {
        return dataset().integer("nAtoms");
    }
}
