package io.github.steinbeck.seneca.golden;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.steinbeck.seneca.golden.JsonValue.JArray;
import io.github.steinbeck.seneca.golden.JsonValue.JBool;
import io.github.steinbeck.seneca.golden.JsonValue.JNull;
import io.github.steinbeck.seneca.golden.JsonValue.JNumber;
import io.github.steinbeck.seneca.golden.JsonValue.JObject;
import io.github.steinbeck.seneca.golden.JsonValue.JString;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Exact structural comparison of a port record with a golden record, reporting the first difference
 * with its JSON path. Rules (golden/FORMAT.md):
 * <ul>
 *   <li>objects: same keys in the same order (the harness writes a fixed key order);</li>
 *   <li>numbers: identical lexemes - this is bit-exactness for doubles ({@code Double.toString} is a
 *       bijection on finite doubles), keeps {@code -0.0} distinct from {@code 0.0} and float distinct from
 *       double printing;</li>
 *   <li>strings, booleans, null: equal.</li>
 * </ul>
 * Paths in {@code ignore} (e.g. {@code "$.crash.at"}) are skipped; {@code "*"} matches any array index.
 */
public final class JsonDiff {

    private JsonDiff() {
    }

    /** @return null if equal, else a description of the first difference */
    public static String firstDifference(JsonValue expected, JsonValue actual, Set<String> ignore) {
        List<String> out = new ArrayList<>(1);
        diff("$", expected, actual, ignore, out);
        return out.isEmpty() ? null : out.get(0);
    }

    /** Fails with a readable message if the two golden-format lines differ. */
    public static void assertSameRecord(String context, String expectedLine, String actualLine, Set<String> ignore) {
        if (ignore.isEmpty() && expectedLine.equals(actualLine)) return;
        String d = firstDifference(JsonValue.parse(expectedLine), JsonValue.parse(actualLine), ignore);
        if (d != null) {
            fail(context + ": " + d + "\n  golden: " + JsonValue.abbreviate(expectedLine)
                    + "\n  port:   " + JsonValue.abbreviate(actualLine));
        }
    }

    private static boolean ignored(String path, Set<String> ignore) {
        if (ignore.contains(path)) return true;
        String generic = path.replaceAll("\\[\\d+]", "[*]");
        return ignore.contains(generic);
    }

    private static void diff(String path, JsonValue e, JsonValue a, Set<String> ignore, List<String> out) {
        if (!out.isEmpty() || ignored(path, ignore)) return;
        if (e.getClass() != a.getClass()) {
            out.add(path + ": type differs: golden " + JsonValue.abbreviate(e.toJson()) + " vs port " + JsonValue.abbreviate(a.toJson()));
            return;
        }
        switch (e) {
            case JObject eo -> {
                JObject ao = (JObject) a;
                Iterator<Map.Entry<String, JsonValue>> ie = eo.members.entrySet().iterator();
                Iterator<Map.Entry<String, JsonValue>> ia = ao.members.entrySet().iterator();
                while (ie.hasNext() && ia.hasNext()) {
                    Map.Entry<String, JsonValue> me = ie.next(), ma = ia.next();
                    if (!me.getKey().equals(ma.getKey())) {
                        out.add(path + ": key order/name differs: golden \"" + me.getKey() + "\" vs port \"" + ma.getKey() + "\"");
                        return;
                    }
                    diff(path + "." + me.getKey(), me.getValue(), ma.getValue(), ignore, out);
                    if (!out.isEmpty()) return;
                }
                if (ie.hasNext()) out.add(path + ": port lacks key \"" + ie.next().getKey() + "\"");
                else if (ia.hasNext()) out.add(path + ": port has extra key \"" + ia.next().getKey() + "\"");
            }
            case JArray ea -> {
                JArray aa = (JArray) a;
                int n = Math.min(ea.items.size(), aa.items.size());
                for (int i = 0; i < n && out.isEmpty(); i++) diff(path + "[" + i + "]", ea.items.get(i), aa.items.get(i), ignore, out);
                if (out.isEmpty() && ea.items.size() != aa.items.size())
                    out.add(path + ": array length golden " + ea.items.size() + " vs port " + aa.items.size());
            }
            case JNumber en -> {
                String al = ((JNumber) a).lexeme;
                if (!en.lexeme.equals(al)) out.add(path + ": number golden " + en.lexeme + " vs port " + al);
            }
            case JString es -> {
                String av = ((JString) a).value;
                if (!es.value.equals(av)) out.add(path + ": string golden \"" + JsonValue.abbreviate(es.value) + "\" vs port \"" + JsonValue.abbreviate(av) + "\"");
            }
            case JBool eb -> {
                if (eb.value != ((JBool) a).value) out.add(path + ": boolean golden " + eb.value + " vs port " + ((JBool) a).value);
            }
            case JNull n -> {
                // equal
            }
        }
    }
}
