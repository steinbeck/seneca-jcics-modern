package io.github.steinbeck.seneca.golden;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parsed JSON that keeps what golden/FORMAT.md needs for exact comparison: key order and the
 * <b>number lexemes</b> as written ({@code -0.0} vs {@code 0.0}, float {@code 116.3} vs double
 * {@code 116.30000305175781}). Use {@link #asFloat()} for Java-float fields and {@link #asDouble()}
 * for double fields; both are exact conversions of the lexeme. {@link #toJson()} re-serialises with
 * the harness's rules, so {@code parse(line).toJson().equals(line)} for every golden line.
 */
public abstract sealed class JsonValue {

    private JsonValue() {
    }

    // ------------------------------------------------------------------ types

    public static final class JObject extends JsonValue {
        final LinkedHashMap<String, JsonValue> members = new LinkedHashMap<>();

        public Map<String, JsonValue> members() {
            return Collections.unmodifiableMap(members);
        }

        public boolean has(String key) {
            return members.containsKey(key);
        }

        /** @throws IllegalArgumentException if the key is missing */
        public JsonValue get(String key) {
            JsonValue v = members.get(key);
            if (v == null) throw new IllegalArgumentException("missing key \"" + key + "\" in " + abbreviate(toJson()));
            return v;
        }

        public String str(String key) {
            return get(key).asString();
        }

        public long lng(String key) {
            return get(key).asLong();
        }

        public int integer(String key) {
            return (int) get(key).asLong();
        }

        public double dbl(String key) {
            return get(key).asDouble();
        }

        public float flt(String key) {
            return get(key).asFloat();
        }

        public boolean bool(String key) {
            return get(key).asBoolean();
        }

        public JArray arr(String key) {
            return get(key).asArray();
        }

        public JObject obj(String key) {
            return get(key).asObject();
        }

        public boolean isNull(String key) {
            return get(key) instanceof JNull;
        }

        /** Record type field "t" (golden convention). */
        public String type() {
            return has("t") ? str("t") : null;
        }
    }

    public static final class JArray extends JsonValue {
        final List<JsonValue> items = new ArrayList<>();

        public List<JsonValue> items() {
            return Collections.unmodifiableList(items);
        }

        public int size() {
            return items.size();
        }

        public JsonValue get(int i) {
            return items.get(i);
        }

        public int[] ints() {
            int[] a = new int[items.size()];
            for (int i = 0; i < a.length; i++) a[i] = (int) items.get(i).asLong();
            return a;
        }

        public double[] doubles() {
            double[] a = new double[items.size()];
            for (int i = 0; i < a.length; i++) a[i] = items.get(i).asDouble();
            return a;
        }

        public float[] floats() {
            float[] a = new float[items.size()];
            for (int i = 0; i < a.length; i++) a[i] = items.get(i).asFloat();
            return a;
        }

        public List<String> strings() {
            List<String> l = new ArrayList<>();
            for (JsonValue v : items) l.add(v.asString());
            return l;
        }

        /** {@code [[i,j,o],...]} lists such as {@code ct}. */
        public int[][] intRows() {
            int[][] a = new int[items.size()][];
            for (int i = 0; i < a.length; i++) a[i] = items.get(i).asArray().ints();
            return a;
        }
    }

    public static final class JString extends JsonValue {
        final String value;

        JString(String value) {
            this.value = value;
        }
    }

    /** A number with its exact source lexeme. */
    public static final class JNumber extends JsonValue {
        final String lexeme;

        JNumber(String lexeme) {
            this.lexeme = lexeme;
        }

        public String lexeme() {
            return lexeme;
        }
    }

    public static final class JBool extends JsonValue {
        final boolean value;

        JBool(boolean value) {
            this.value = value;
        }
    }

    public static final class JNull extends JsonValue {
        static final JNull INSTANCE = new JNull();
    }

    // ------------------------------------------------------------------ accessors

    public JObject asObject() {
        if (this instanceof JObject o) return o;
        throw new IllegalArgumentException("not an object: " + abbreviate(toJson()));
    }

    public JArray asArray() {
        if (this instanceof JArray a) return a;
        throw new IllegalArgumentException("not an array: " + abbreviate(toJson()));
    }

    public String asString() {
        if (this instanceof JString s) return s.value;
        if (this instanceof JNull) return null;
        throw new IllegalArgumentException("not a string: " + abbreviate(toJson()));
    }

    public boolean asBoolean() {
        if (this instanceof JBool b) return b.value;
        throw new IllegalArgumentException("not a boolean: " + abbreviate(toJson()));
    }

    public long asLong() {
        if (this instanceof JNumber n) return Long.parseLong(n.lexeme);
        throw new IllegalArgumentException("not a number: " + abbreviate(toJson()));
    }

    /**
     * Java double with sign of zero preserved; the strings "NaN", "Infinity", "-Infinity" (how the
     * harness writes non-finite values) are accepted too.
     */
    public double asDouble() {
        if (this instanceof JNumber n) return Double.parseDouble(n.lexeme);
        if (this instanceof JString s) return Double.parseDouble(s.value);
        throw new IllegalArgumentException("not a number: " + abbreviate(toJson()));
    }

    /** Java float (binary32), as {@code Float.toString} wrote it. */
    public float asFloat() {
        if (this instanceof JNumber n) return Float.parseFloat(n.lexeme);
        if (this instanceof JString s) return Float.parseFloat(s.value);
        throw new IllegalArgumentException("not a number: " + abbreviate(toJson()));
    }

    public boolean isNull() {
        return this instanceof JNull;
    }

    // ------------------------------------------------------------------ serialisation

    /** Compact serialisation with the harness's rules (golden/Json.java), number lexemes unchanged. */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        write(sb);
        return sb.toString();
    }

    void write(StringBuilder sb) {
        switch (this) {
            case JObject o -> {
                sb.append('{');
                boolean first = true;
                for (Map.Entry<String, JsonValue> e : o.members.entrySet()) {
                    if (!first) sb.append(',');
                    first = false;
                    writeString(sb, e.getKey());
                    sb.append(':');
                    e.getValue().write(sb);
                }
                sb.append('}');
            }
            case JArray a -> {
                sb.append('[');
                for (int i = 0; i < a.items.size(); i++) {
                    if (i > 0) sb.append(',');
                    a.items.get(i).write(sb);
                }
                sb.append(']');
            }
            case JString s -> writeString(sb, s.value);
            case JNumber n -> sb.append(n.lexeme);
            case JBool b -> sb.append(b.value);
            case JNull nul -> sb.append("null");
        }
    }

    static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20 || c > 0x7e) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    static String abbreviate(String s) {
        return s.length() <= 200 ? s : s.substring(0, 200) + "...";
    }

    @Override
    public String toString() {
        return toJson();
    }

    // ------------------------------------------------------------------ parser

    /** Parses one JSON text (a golden line). */
    public static JsonValue parse(String text) {
        Parser p = new Parser(text);
        JsonValue v = p.value();
        p.ws();
        if (p.pos != text.length()) throw p.error("trailing characters");
        return v;
    }

    private static final class Parser {
        final String s;
        int pos;

        Parser(String s) {
            this.s = s;
        }

        IllegalArgumentException error(String msg) {
            return new IllegalArgumentException(msg + " at " + pos + ": " + abbreviate(s));
        }

        void ws() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }

        JsonValue value() {
            ws();
            if (pos >= s.length()) throw error("unexpected end");
            char c = s.charAt(pos);
            switch (c) {
                case '{': return object();
                case '[': return array();
                case '"': return new JString(string());
                case 't': expect("true"); return new JBool(true);
                case 'f': expect("false"); return new JBool(false);
                case 'n': expect("null"); return JNull.INSTANCE;
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) return number();
                    throw error("unexpected '" + c + "'");
            }
        }

        void expect(String word) {
            if (!s.startsWith(word, pos)) throw error("expected " + word);
            pos += word.length();
        }

        JObject object() {
            JObject o = new JObject();
            pos++;
            ws();
            if (s.charAt(pos) == '}') {
                pos++;
                return o;
            }
            while (true) {
                ws();
                if (s.charAt(pos) != '"') throw error("expected key");
                String k = string();
                ws();
                if (s.charAt(pos) != ':') throw error("expected ':'");
                pos++;
                if (o.members.put(k, value()) != null) throw error("duplicate key " + k);
                ws();
                char c = s.charAt(pos++);
                if (c == '}') return o;
                if (c != ',') throw error("expected ',' or '}'");
            }
        }

        JArray array() {
            JArray a = new JArray();
            pos++;
            ws();
            if (s.charAt(pos) == ']') {
                pos++;
                return a;
            }
            while (true) {
                a.items.add(value());
                ws();
                char c = s.charAt(pos++);
                if (c == ']') return a;
                if (c != ',') throw error("expected ',' or ']'");
            }
        }

        String string() {
            StringBuilder sb = new StringBuilder();
            pos++;
            while (true) {
                char c = s.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                char e = s.charAt(pos++);
                switch (e) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default -> throw error("bad escape \\" + e);
                }
            }
        }

        JNumber number() {
            int start = pos;
            if (s.charAt(pos) == '-') pos++;
            while (pos < s.length() && "0123456789.eE+-".indexOf(s.charAt(pos)) >= 0) pos++;
            return new JNumber(s.substring(start, pos));
        }
    }
}
