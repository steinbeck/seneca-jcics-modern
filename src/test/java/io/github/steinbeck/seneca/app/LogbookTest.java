package io.github.steinbeck.seneca.app;

import static io.github.steinbeck.seneca.app.GuiTestSupport.flushEdt;
import static io.github.steinbeck.seneca.app.GuiTestSupport.onEdt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.JTextPane;

import org.junit.jupiter.api.Test;

import io.github.steinbeck.seneca.app.log.AppEngineLog;
import io.github.steinbeck.seneca.app.log.LogFiles;
import io.github.steinbeck.seneca.app.log.Logbook;
import io.github.steinbeck.seneca.app.log.LogbookPanel;

/**
 * The Logbook with concurrent and long runs (DV-90): whole lines per run, a bounded document, and the
 * Swing thread stays responsive.
 */
class LogbookTest {

    /** Two runs write their two-part progress lines interleaved: the Logbook keeps each line whole. */
    @Test
    void concurrentRunsGiveWholeLines() throws Exception {
        Path dir = GuiTestSupport.scratchDir("logbook-lines");
        LogFiles files = LogFiles.open(AppPaths.under(dir));
        Logbook book = new Logbook();
        AppEngineLog log = new AppEngineLog(book, files);
        ExecutorService a = Executors.newSingleThreadExecutor(), b = Executors.newSingleThreadExecutor();
        try {
            a.submit(() -> log.info("start_kT = 50.0")).get();
            a.submit(() -> log.info("Iteration: 1000 ")).get();
            b.submit(() -> log.info("Iteration: 2000 ")).get();
            a.submit(() -> log.info("Current kT = 1.5\n")).get();
            b.submit(() -> log.info("Current kT = 2.5\n")).get();
            b.submit(() -> log.info("Error in StoicGent.run()\n")).get();
        } finally {
            a.shutdown();
            b.shutdown();
            files.close();
        }
        flushEdt();
        assertEquals("start_kT = 50.0Iteration: 1000 Current kT = 1.5\nIteration: 2000 Current kT = 2.5\nError in StoicGent.run()\n",
                onEdt(book::text));
        // seneca.log as 2001 (log4j %m%n): every message on its own line, in call order
        assertEquals("start_kT = 50.0\nIteration: 1000 \nIteration: 2000 \nCurrent kT = 1.5\n\nCurrent kT = 2.5\n\n"
                + "Error in StoicGent.run()\n\n", Files.readString(dir.resolve("seneca.log")));
    }

    /** 20 MB of log text: the document stays bounded, ends with the newest line, starts at a line. */
    @Test
    void longRunsKeepTheLogbookBounded() throws Exception {
        Logbook book = new Logbook();
        JTextPane pane = onEdt(() -> {
            LogbookPanel p = new LogbookPanel(book);
            p.setSize(800, 600);
            GuiTestSupport.layoutTree(p);
            return (JTextPane) GuiTestSupport.all(p).stream().filter(c -> c instanceof JTextPane).findFirst().orElseThrow();
        });
        String line = "New best Structure: \n" + "Score: 1234, ".repeat(30) + "\n";
        int n = 20_000_000 / line.length();
        long t0 = System.nanoTime();
        for (int i = 0; i < n; i++) {
            book.append("Iteration: " + i + " ");
            book.append(line);
            if (i % 2000 == 0) flushEdt();
        }
        flushEdt();
        long millis = (System.nanoTime() - t0) / 1_000_000;
        String text = onEdt(book::text);
        assertTrue(text.length() <= Logbook.MAX_CHARS, "length " + text.length());
        assertTrue(text.length() >= Logbook.TRIM_TO_CHARS - line.length(), "length " + text.length());
        assertTrue(text.startsWith("Iteration: ") || text.startsWith("Score: "), "starts at a whole line: " + text.substring(0, 40));
        assertTrue(text.endsWith("Iteration: " + (n - 1) + " " + line));
        assertEquals(text.length(), (int) onEdt(() -> pane.getDocument().getLength()));
        System.out.println("LogbookTest: " + n + " appends (" + (n * (long) line.length() / 1_000_000) + " MB) in " + millis + " ms");
        assertTrue(millis < 60_000, "took " + millis + " ms");
    }

    /**
     * DV-92: text appended while the Logbook tab is hidden ends in view when the tab is shown; after the
     * user scrolls up, new text does not move the view; scrolled back down, it follows again.
     */
    @Test
    void theNewestTextStaysInView() throws Exception {
        Logbook book = new Logbook();
        record Ui(javax.swing.JTabbedPane tabs, LogbookPanel panel, javax.swing.JViewport vp) {}
        Ui ui = onEdt(() -> {
            javax.swing.JTabbedPane tabs = new javax.swing.JTabbedPane();
            tabs.addTab("Structure Generation", new javax.swing.JPanel());
            LogbookPanel p = new LogbookPanel(book);
            tabs.addTab("Logbook", p);
            tabs.setSize(800, 600);
            GuiTestSupport.layoutTree(tabs);
            JTextPane tp = (JTextPane) GuiTestSupport.all(p).stream().filter(c -> c instanceof JTextPane).findFirst().orElseThrow();
            return new Ui(tabs, p, (javax.swing.JViewport) tp.getParent());
        });
        for (int i = 0; i < 300; i++) book.append("Iteration: " + i + " Current kT = 1.5\n");
        flushEdt();
        onEdt(() -> {
            ui.tabs.setSelectedIndex(1);
            ui.tabs.validate();
            GuiTestSupport.layoutTree(ui.tabs);
        });
        flushEdt();
        java.util.function.Supplier<int[]> state = () -> onEdt(() -> new int[] {ui.vp.getViewPosition().y,
                ui.vp.getViewSize().height - ui.vp.getExtentSize().height, ui.vp.getViewSize().height});
        int[] s0 = state.get();
        assertTrue(s0[2] > 2000, "long text " + s0[2]);
        assertEquals(s0[1], s0[0], "shown at the end of the text");
        // scrolled up: new text does not move the view
        onEdt(() -> ui.vp.setViewPosition(new java.awt.Point(0, 100)));
        for (int i = 0; i < 50; i++) book.append("more " + i + "\n");
        flushEdt();
        onEdt(() -> GuiTestSupport.layoutTree(ui.tabs));
        flushEdt();
        assertEquals(100, state.get()[0], "stays where the user scrolled to");
        assertTrue(!onEdt(ui.panel::following));
        // back at the bottom: follows again
        onEdt(() -> ui.vp.setViewPosition(new java.awt.Point(0, ui.vp.getViewSize().height - ui.vp.getExtentSize().height)));
        for (int i = 0; i < 50; i++) book.append("again " + i + "\n");
        flushEdt();
        onEdt(() -> GuiTestSupport.layoutTree(ui.tabs));
        flushEdt();
        int[] s2 = state.get();
        assertTrue(s2[2] > s0[2], "text grew");
        assertEquals(s2[1], s2[0], "follows the end again");
    }
}
