package io.github.steinbeck.seneca.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * The bundled example datasets (the ten SML files of the 2001 distribution, {@code legacy/original/sml}),
 * offered under File &gt; Open &gt; Example Dataset and used as the first directory of the "Open SENECA
 * Dataset" dialog (DV-87). Their directory is the system property {@value #PROPERTY}: the packaged
 * application sets it to {@code $APPDIR/examples} (scripts/build-app.sh), {@code scripts/run-app.sh} and
 * the tests to {@code legacy/original/sml}. Unset or not a directory: no examples (the menu entry is
 * disabled).
 *
 * @param dir the directory, or null
 */
public record Examples(Path dir) {

    private static final Logger LOG = Logger.getLogger(Examples.class.getName());

    /** System property: directory of the example datasets. */
    public static final String PROPERTY = "seneca.examples";

    /** From {@value #PROPERTY}. */
    public static Examples fromSystem() {
        String p = System.getProperty(PROPERTY);
        if (p == null || p.isBlank()) return new Examples(null);
        Path d = Path.of(p.trim());
        return new Examples(Files.isDirectory(d) ? d.toAbsolutePath().normalize() : null);
    }

    /** The {@code *.sml} files of {@link #dir()}, sorted by name ignoring case; empty if none. */
    public List<Path> files() {
        if (dir == null) return List.of();
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".sml") && Files.isRegularFile(f))
                    .sorted(Comparator.comparing(f -> f.getFileName().toString().toLowerCase(Locale.ROOT))).toList();
        } catch (IOException e) {
            LOG.warning("cannot list the example datasets in " + dir + ": " + e);
            return List.of();
        }
    }

    /** The example {@code name} ({@code "pinene"} or {@code "pinene.sml"}), or null. */
    public Path file(String name) {
        String n = name.toLowerCase(Locale.ROOT).endsWith(".sml") ? name : name + ".sml";
        for (Path f : files()) if (f.getFileName().toString().equalsIgnoreCase(n)) return f;
        return null;
    }

    /** Menu label of an example file: its name without {@code .sml}. */
    public static String label(Path file) {
        String n = file.getFileName().toString();
        return n.toLowerCase(Locale.ROOT).endsWith(".sml") ? n.substring(0, n.length() - 4) : n;
    }
}
