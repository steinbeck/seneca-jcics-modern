package io.github.steinbeck.seneca.golden;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

/**
 * Locations of the golden master and the legacy inputs. Tests read golden/ in place (no copies).
 * Resolution: system properties set by surefire ({@code seneca.repo}, {@code seneca.golden},
 * {@code seneca.sml}, {@code seneca.references}); otherwise the repository root is found by walking up
 * from the working directory to the directory containing {@code golden/FORMAT.md}.
 */
public final class GoldenFiles {

    private GoldenFiles() {
    }

    public static Path repo() {
        String p = System.getProperty("seneca.repo");
        if (p != null) return Path.of(p);
        Path d = Path.of("").toAbsolutePath();
        while (d != null) {
            if (Files.exists(d.resolve("golden/FORMAT.md"))) return d;
            d = d.getParent();
        }
        throw new IllegalStateException("repository root (with golden/FORMAT.md) not found from " + Path.of("").toAbsolutePath());
    }

    public static Path golden() {
        String p = System.getProperty("seneca.golden");
        return p != null ? Path.of(p) : repo().resolve("golden");
    }

    public static Path smlDir() {
        String p = System.getProperty("seneca.sml");
        return p != null ? Path.of(p) : repo().resolve("legacy/original/sml");
    }

    public static Path referencesTsv() {
        String p = System.getProperty("seneca.references");
        return p != null ? Path.of(p) : repo().resolve("legacy/harness/references.tsv");
    }

    /** Dataset file of a key ("polycarpol@hblock" reads polycarpol.sml). */
    public static Path sml(String key) {
        int i = key.indexOf('@');
        return smlDir().resolve((i < 0 ? key : key.substring(0, i)) + ".sml");
    }

    /** All dataset keys that have an echo file, sorted. */
    public static List<String> keys() {
        return list(golden().resolve("datasets"), ".echo.jsonl").stream()
                .map(p -> strip(p, ".echo.jsonl")).sorted().toList();
    }

    public static Path echo(String key) {
        return golden().resolve("datasets/" + key + ".echo.jsonl");
    }

    public static Path scores(String key) {
        return golden().resolve("scores/" + key + ".scores.jsonl");
    }

    public static Path reference(String key) {
        return golden().resolve("references/" + key + ".ref.jsonl");
    }

    /** A trajectory or run file name decomposed: key, profile, seed. */
    public record RunId(String key, String profile, long seed, Path file) {
        @Override
        public String toString() {
            return key + "." + profile + ".seed" + seed;
        }
    }

    private static final Pattern RUN_NAME = Pattern.compile("(.+)\\.(gui|paper|all|manual)\\.seed(\\d+)\\.(json|jsonl\\.gz)");

    public static List<RunId> trajectories() {
        return runIds(golden().resolve("trajectories"), ".jsonl.gz");
    }

    public static List<RunId> runs() {
        return runIds(golden().resolve("runs"), ".json");
    }

    private static List<RunId> runIds(Path dir, String suffix) {
        List<RunId> l = new ArrayList<>();
        for (Path p : list(dir, suffix)) {
            Matcher m = RUN_NAME.matcher(p.getFileName().toString());
            if (!m.matches()) continue;
            l.add(new RunId(m.group(1), m.group(2), Long.parseLong(m.group(3)), p));
        }
        l.sort((a, b) -> a.toString().compareTo(b.toString()));
        return l;
    }

    private static List<Path> list(Path dir, String suffix) {
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().endsWith(suffix)).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String strip(Path p, String suffix) {
        String n = p.getFileName().toString();
        return n.substring(0, n.length() - suffix.length());
    }

    /** All lines of a golden file (gzip-aware), without line terminators. Golden files are ASCII. */
    public static List<String> lines(Path file) {
        try {
            if (file.getFileName().toString().endsWith(".gz")) {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(
                        new GZIPInputStream(Files.newInputStream(file)), StandardCharsets.US_ASCII))) {
                    return r.lines().toList();
                }
            }
            return Files.readAllLines(file, StandardCharsets.US_ASCII);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Parsed records of a JSON-lines golden file. */
    public static List<JsonValue.JObject> records(Path file) {
        List<JsonValue.JObject> l = new ArrayList<>();
        for (String line : lines(file)) l.add(JsonValue.parse(line).asObject());
        return l;
    }
}
