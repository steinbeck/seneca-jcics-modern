package io.github.steinbeck.seneca.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import io.github.steinbeck.seneca.golden.GoldenFiles;

/**
 * Rules for the GUI package that {@code PortHygieneTest} (which skips {@code app}) does not check
 * (docs/port/gui-design.md): no legacy packages (compchem, seneca, jmdraw, com.jrefinery, log4j), no RMI,
 * no random source (runs are seeded only through the core's {@code RunRandom}), no {@code System.exit}
 * outside {@link SenecaApp} and {@link AppContext} (tests replace the exit hook), and no direct creation of
 * dialogs, file choosers or frames outside {@code app.ui} (everything that blocks on a user goes through
 * {@code UiServices}, so the GUI tests can script it).
 */
class AppHygieneTest {

    static final Pattern LEGACY = Pattern.compile("^import\\s+(compchem|seneca|jmdraw|com\\.jrefinery|org\\.apache\\.log4j|jnt)\\.",
            Pattern.MULTILINE);
    static final Pattern FORBIDDEN = Pattern.compile("\\bjava\\.rmi\\.|\\bjava\\.util\\.Random\\b|\\bThreadLocalRandom\\b|\\bMath\\.random\\(");
    static final Pattern EXIT = Pattern.compile("System\\.exit\\(");
    static final Pattern BLOCKING_UI = Pattern.compile(
            "new\\s+(JFileChooser|FileDialog|JDialog|JFrame)\\s*\\(|JOptionPane\\.show\\w*\\(|\\.createDialog\\(|getSystemClipboard\\(");

    @Test
    void appCodeFollowsTheGuiRules() throws IOException {
        Path app = GoldenFiles.repo().resolve("src/main/java/io/github/steinbeck/seneca/app");
        List<String> problems = new ArrayList<>();
        try (Stream<Path> s = Files.walk(app)) {
            for (Path p : s.filter(f -> f.toString().endsWith(".java")).toList()) {
                String src = Files.readString(p);
                String rel = app.relativize(p).toString();
                if (LEGACY.matcher(src).find()) problems.add(rel + ": legacy import");
                if (FORBIDDEN.matcher(src).find()) problems.add(rel + ": RMI or random source");
                if (EXIT.matcher(src).find() && !rel.equals("SenecaApp.java") && !rel.equals("AppContext.java"))
                    problems.add(rel + ": System.exit");
                if (BLOCKING_UI.matcher(src).find() && !rel.startsWith("ui/") && !rel.equals("MainFrame.java"))
                    problems.add(rel + ": dialog/frame/clipboard outside app.ui (use UiServices)");
            }
        }
        assertEquals(List.of(), problems);
    }
}
