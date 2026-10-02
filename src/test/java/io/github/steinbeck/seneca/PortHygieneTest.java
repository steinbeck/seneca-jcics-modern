package io.github.steinbeck.seneca;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import io.github.steinbeck.seneca.golden.GoldenFiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Static rules for the main code (docs/port/design.md section 2):
 * no legacy packages ({@code compchem}, {@code seneca.*}, {@code jmdraw}, log4j 1.x) - they are only on
 * the test classpath of the oracle profile; no {@code Math.random()} (every draw goes through the run's
 * {@code RunRandom}); no other random source ({@code java.util.Random}, {@code ThreadLocalRandom}, ...)
 * outside {@code RunRandom} and the golden reference recorder; no {@code java.awt}/{@code javax.swing}
 * (imported or fully qualified) outside a future GUI package; no {@code java.rmi} (policy point 4).
 */
class PortHygieneTest {

    static final Pattern FORBIDDEN = Pattern.compile(
            "^\\s*import\\s+(compchem|seneca|jmdraw|org\\.apache\\.log4j|jnt)\\.|Math\\.random\\s*\\("
                    + "|\\b(java\\.awt|javax\\.swing|java\\.rmi)\\.",
            Pattern.MULTILINE);

    /** Random sources other than the run's {@code RunRandom}. */
    static final Pattern RANDOM = Pattern.compile("\\bjava\\.util\\.Random\\b|\\bThreadLocalRandom\\b|\\bSplittableRandom\\b|\\bSecureRandom\\b|\\bnew\\s+Random\\s*\\(");

    /**
     * The only main classes that may create a {@code java.util.Random}: the run's random source, and the
     * golden reference recorder's labelling search (a fixed-seed search of the harness, not part of a run).
     */
    static final List<String> RANDOM_ALLOWED = List.of(
            "io/github/steinbeck/seneca/structgen/RunRandom.java",
            "io/github/steinbeck/seneca/golden/ReferenceRecords.java");

    @Test
    void mainCodeUsesNoLegacyPackagesAndNoGlobalRandom() throws IOException {
        Path main = GoldenFiles.repo().resolve("src/main/java");
        List<String> violations = new ArrayList<>();
        try (Stream<Path> s = Files.walk(main)) {
            for (Path p : s.filter(f -> f.toString().endsWith(".java")).toList()) {
                if (p.toString().contains("/seneca/app/")) continue; // future Swing GUI package
                String src = Files.readString(p, StandardCharsets.UTF_8)
                        .replaceAll("(?s)/\\*.*?\\*/", "")     // block and javadoc comments
                        .replaceAll("//[^\\n]*", "");          // line comments
                var m = FORBIDDEN.matcher(src);
                while (m.find()) violations.add(main.relativize(p) + ": " + m.group().trim());
                String rel = main.relativize(p).toString().replace('\\', '/');
                if (!RANDOM_ALLOWED.contains(rel)) {
                    var r = RANDOM.matcher(src);
                    while (r.find()) violations.add(rel + ": " + r.group().trim());
                }
            }
        }
        assertEquals(List.of(), violations);
    }
}
