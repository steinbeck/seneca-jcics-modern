package golden;

import java.util.List;

/**
 * Minimal JSON writer with stable (insertion) key order.
 *
 * Number rendering (see golden/FORMAT.md):
 *   int/long  -> decimal
 *   double    -> Double.toString(d)   (NaN/Infinity -> JSON string "NaN"/"Infinity"/"-Infinity")
 *   float     -> Float.toString(f)    (the value is a Java float; parse to binary32 for exact compare)
 */
public final class Json {
    private final StringBuilder sb = new StringBuilder();
    private boolean first = true;
    private final boolean array;

    private Json(boolean array) {
        this.array = array;
        sb.append(array ? '[' : '{');
    }

    public static Json obj() { return new Json(false); }
    public static Json arr() { return new Json(true); }

    private void sep() {
        if (!first) sb.append(',');
        first = false;
    }

    private void key(String k) {
        if (array) throw new IllegalStateException("key in array");
        sep();
        str(sb, k);
        sb.append(':');
    }

    private void elem() {
        if (!array) throw new IllegalStateException("element in object");
        sep();
    }

    // ---- object puts ----
    public Json put(String k, String v) { key(k); if (v == null) sb.append("null"); else str(sb, v); return this; }
    public Json put(String k, long v) { key(k); sb.append(v); return this; }
    public Json put(String k, int v) { key(k); sb.append(v); return this; }
    public Json put(String k, boolean v) { key(k); sb.append(v); return this; }
    public Json put(String k, double v) { key(k); dbl(sb, v); return this; }
    public Json putFloat(String k, float v) { key(k); flt(sb, v); return this; }
    public Json putFloat(String k, Float v) { key(k); if (v == null) sb.append("null"); else flt(sb, v.floatValue()); return this; }
    public Json put(String k, Json v) { key(k); sb.append(v == null ? "null" : v.toString()); return this; }
    public Json putNull(String k) { key(k); sb.append("null"); return this; }
    public Json putRaw(String k, String raw) { key(k); sb.append(raw); return this; }

    // ---- array adds ----
    public Json add(String v) { elem(); if (v == null) sb.append("null"); else str(sb, v); return this; }
    public Json add(long v) { elem(); sb.append(v); return this; }
    public Json add(int v) { elem(); sb.append(v); return this; }
    public Json add(boolean v) { elem(); sb.append(v); return this; }
    public Json add(double v) { elem(); dbl(sb, v); return this; }
    public Json addFloat(float v) { elem(); flt(sb, v); return this; }
    public Json add(Json v) { elem(); sb.append(v == null ? "null" : v.toString()); return this; }

    public static Json ints(int[] a) { Json j = arr(); if (a != null) for (int x : a) j.add(x); return j; }
    public static Json doubles(double[] a) { Json j = arr(); if (a != null) for (double x : a) j.add(x); return j; }
    public static Json floats(float[] a) { Json j = arr(); if (a != null) for (float x : a) j.addFloat(x); return j; }
    public static Json strings(List<String> a) { Json j = arr(); for (String x : a) j.add(x); return j; }
    public static Json strings(String[] a) { Json j = arr(); if (a != null) for (String x : a) j.add(x); return j; }

    @Override
    public String toString() {
        return sb.toString() + (array ? ']' : '}');
    }

    static void dbl(StringBuilder sb, double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) str(sb, Double.toString(v));
        else sb.append(Double.toString(v));
    }

    static void flt(StringBuilder sb, float v) {
        if (Float.isNaN(v) || Float.isInfinite(v)) str(sb, Float.toString(v));
        else sb.append(Float.toString(v));
    }

    static void str(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20 || c > 0x7e) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
    }
}
