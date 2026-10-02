package io.github.steinbeck.seneca.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.Test;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.app.log.LogFiles;
import io.github.steinbeck.seneca.app.ui.EdtRunListener;
import io.github.steinbeck.seneca.judges.ScoreSummary;
import io.github.steinbeck.seneca.model.SenecaDataset;
import io.github.steinbeck.seneca.structgen.RunController;
import io.github.steinbeck.seneca.structgen.RunListener;
import io.github.steinbeck.seneca.structgen.RunOptions;
import io.github.steinbeck.seneca.structgen.RunResult;
import io.github.steinbeck.seneca.structgen.RunSlot;
import io.github.steinbeck.seneca.structgen.RunStatus;

/** Non-visual pieces of the application: test hooks, dataset collection, EDT bridge, log files, preferences. */
class AppUnitTest {

    @Test
    void seedHookGivesSlotKBaseSeedPlusK() {
        RunController rc = new RunController();
        RunSlot s0 = rc.addSlot(), s1 = rc.addSlot(), s2 = rc.addSlot();
        TestHooks h = new TestHooks(1L, 5_000_000);
        assertEquals(Long.valueOf(1), h.apply(RunOptions.application(), s0).seed());
        assertEquals(Long.valueOf(2), h.apply(RunOptions.application(), s1).seed());
        assertEquals(Long.valueOf(3), h.apply(RunOptions.application(), s2).seed());
        assertEquals(5_000_000, h.apply(RunOptions.application(), s2).iterationCap());
        assertEquals(LegacyCompat.DEFAULT, h.apply(RunOptions.application(), s2).compat());
        RunOptions none = TestHooks.none().apply(RunOptions.application(), s1);
        assertNull(none.seed());
        assertEquals(0, none.iterationCap());
    }

    @Test
    void seedHookFromSystemProperties() {
        String old = System.getProperty(TestHooks.SEED);
        try {
            System.setProperty(TestHooks.SEED, " 7 ");
            assertEquals(Long.valueOf(7), TestHooks.fromSystemProperties().seed());
            System.clearProperty(TestHooks.SEED);
            assertNull(TestHooks.fromSystemProperties().seed());
        } finally {
            if (old != null) System.setProperty(TestHooks.SEED, old);
        }
    }

    @Test
    void datasetCollectionKeepsACurrentDataset() {
        DatasetCollection c = new DatasetCollection();
        int[] changes = {0};
        c.addChangeListener(() -> changes[0]++);
        assertEquals(1, changes[0]);
        assertNull(c.current());
        assertEquals(-1, c.currentIndex());
        SenecaDataset a = new SenecaDataset("a"), b = new SenecaDataset("b"), d = new SenecaDataset("a");
        c.add(a);
        c.add(b);
        c.add(d);
        assertSame(d, c.current());
        c.setCurrent(0);
        assertSame(a, c.current());
        c.setCurrent(b);
        assertSame(b, c.current());
        int before = changes[0];
        b.setName("bb");                       // a dataset change reaches the listeners
        assertEquals(before + 1, changes[0]);
        assertEquals(List.of("a", "bb", "a"), c.names());
        c.remove(a);                           // current (b) stays current
        assertSame(b, c.current());
        c.remove(b);                           // the next one takes its place
        assertSame(d, c.current());
        c.remove(d);
        assertNull(c.current());
        before = changes[0];
        b.setName("x");                        // removed datasets are no longer observed
        assertEquals(before, changes[0]);
    }

    @Test
    void edtRunListenerKeepsOrderAndCoalescesStatus() {
        List<Runnable> posted = new ArrayList<>();
        Executor manual = posted::add;
        List<String> got = new ArrayList<>();
        RunListener target = new RunListener() {
            @Override
            public void onStatus(RunSlot slot, RunStatus status) {
                got.add("status " + status.iteration());
            }

            @Override
            public void onLog(RunSlot slot, String message) {
                got.add("log " + message);
            }

            @Override
            public void onNewBest(RunSlot slot, String mdl, ScoreSummary summary) {
                got.add("best " + summary.score);
            }

            @Override
            public void onFinished(RunSlot slot, RunResult result) {
                got.add("finished");
            }
        };
        EdtRunListener l = new EdtRunListener(target, manual);
        RunSlot slot = new RunController().addSlot();
        l.onStatus(slot, status(500));
        l.onLog(slot, "a");
        l.onStatus(slot, status(1000));        // replaces the pending 500
        l.onNewBest(slot, "mdl", new ScoreSummary(42, ""));
        l.onLog(slot, "b");
        assertEquals(1, posted.size(), "one drain task");
        posted.remove(0).run();
        assertEquals(List.of("status 1000", "log a", "best 42", "log b"), got);
        l.onStatus(slot, status(1500));
        l.onFinished(slot, null);
        posted.remove(0).run();
        assertEquals(List.of("status 1000", "log a", "best 42", "log b", "status 1500", "finished"), got);
    }

    private static RunStatus status(long it) {
        return new RunStatus("d", "C10H16", it, null, null, RunStatus.State.RUNNING, 0);
    }

    @Test
    void logFilesAreTruncatedAtStartAndFollowTheLog4jLayouts() throws Exception {
        Path dir = GuiTestSupport.scratchDir("logfiles");
        AppPaths p = AppPaths.under(dir);
        Files.writeString(p.logFile(), "old content");
        try (LogFiles f = LogFiles.open(p)) {
            f.info("Start of StoicGent\n");
            f.info("Iteration: 1000 ");
            f.result("Score: 1\n...\nM  END\n\n$$$$\n");
            f.annealing("Iteration; Server 1; \n");
        }
        assertEquals("Start of StoicGent\n\nIteration: 1000 \nScore: 1\n...\nM  END\n\n$$$$\n\nIteration; Server 1; \n\n",
                Files.readString(p.logFile()));
        assertEquals("Score: 1\n...\nM  END\n\n$$$$\n", Files.readString(p.resultsFile()));
        assertEquals("Iteration; Server 1; \n", Files.readString(p.annealingLogFile()));
    }

    @Test
    void preferencesRememberDirectories() throws Exception {
        Path dir = GuiTestSupport.scratchDir("prefs");
        Path file = dir.resolve("sub").resolve("seneca.props");
        AppPreferences p = new AppPreferences(file);
        Files.createDirectories(dir.resolve("lucy"));
        p.setSenecaLocation(dir);
        p.setLucyLocation(dir.resolve("lucy"));
        AppPreferences q = new AppPreferences(file);
        assertEquals(dir.toAbsolutePath(), q.senecaLocation());
        assertEquals(dir.resolve("lucy").toAbsolutePath(), q.lucyLocation());
        assertTrue(Files.readString(file).startsWith("#Seneca Properties"));
    }

    /** A remembered directory that no longer exists is ignored (the default applies). */
    @Test
    void preferencesIgnoreMissingDirectories() throws Exception {
        Path dir = GuiTestSupport.scratchDir("prefs-stale");
        Path file = dir.resolve("seneca.props");
        Files.writeString(file, "#Seneca Properties\nfilelocation.seneca=" + dir.resolve("gone") + "\nfilelocation.lucy="
                + dir.resolve("gone-too") + "\n");
        AppPreferences p = new AppPreferences(file);
        Path examples = GuiTestSupport.sml("pinene").getParent();
        p.setDefaultSenecaLocation(examples);
        assertEquals(examples, p.senecaLocation());
        assertEquals(Path.of(System.getProperty("user.home")), p.lucyLocation());
    }

    /** macOS: the preferences live in a folder of their own (not the "Seneca" folder of another SENECA program). */
    @Test
    void macPreferencesFolder() {
        Path prefs = AppPaths.fromSystem().preferencesFile();
        if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("mac"))
            assertEquals(Path.of(System.getProperty("user.home"), "Library", "Application Support", AppPaths.MAC_SUPPORT_FOLDER,
                    "seneca.props"), prefs);
        else
            assertEquals(Path.of(System.getProperty("user.home"), "seneca.props"), prefs);
    }

    @Test
    void examplesAndOpenDialogDefault() throws Exception {
        Examples ex = new Examples(GuiTestSupport.sml("pinene").getParent());
        assertEquals(10, ex.files().size());
        assertEquals("pinene", Examples.label(ex.file("pinene")));
        assertEquals(ex.file("pinene"), ex.file("PINENE.sml"));
        assertEquals(List.of(), new Examples(null).files());
        assertEquals(null, new Examples(null).file("pinene"));

        Path dir = GuiTestSupport.scratchDir("prefs-default");
        AppPreferences p = new AppPreferences(dir.resolve("seneca.props"));
        p.setDefaultSenecaLocation(ex.dir());
        assertEquals(ex.dir(), p.senecaLocation(), "first Open dialog: the examples");
        p.setSenecaLocation(dir);
        assertEquals(dir.toAbsolutePath(), p.senecaLocation(), "then the remembered directory");
        assertEquals(Path.of(System.getProperty("user.home")), p.lucyLocation());
    }

    @Test
    void commandLineOptions() {
        String[] keys = {AppPaths.LOGDIR_PROPERTY, Examples.PROPERTY, TestHooks.SEED, TestHooks.ITERATION_CAP};
        java.util.Map<String, String> old = new java.util.HashMap<>();
        for (String k : keys) old.put(k, System.getProperty(k));
        try {
            SenecaApp.Arguments a = SenecaApp.parse(new String[] {"-psn_0_12345", "--logdir=/tmp/x", "--seed=7",
                    "--iteration-cap=100", "--examples=/tmp/ex", "--bogus", "a.sml", "b.sml"});
            assertEquals("a.sml", a.file());
            assertFalse(a.smoke());
            assertEquals(List.of("--bogus"), a.unknown());
            assertEquals("/tmp/x", System.getProperty(AppPaths.LOGDIR_PROPERTY));
            assertEquals("/tmp/ex", System.getProperty(Examples.PROPERTY));
            assertEquals(new TestHooks(7L, 100), TestHooks.fromSystemProperties());
            SenecaApp.Arguments s = SenecaApp.parse(new String[] {"--smoke=/tmp/r.txt"});
            assertTrue(s.smoke());
            assertEquals(Path.of("/tmp/r.txt"), s.smokeReport());
            assertEquals(null, s.file());
            assertTrue(SenecaApp.parse(new String[] {"--smoke"}).smoke());
        } finally {
            for (String k : keys) {
                if (old.get(k) == null) System.clearProperty(k);
                else System.setProperty(k, old.get(k));
            }
        }
    }

    @Test
    void resourcesHoldTheLegacyTexts() {
        Resources r = Resources.load();
        assertEquals("Do you really want to exit Seneca?\n", r.lines("Exit"));
        assertEquals("Close pinene?", r.format("CloseTitle", "pinene"));
        assertEquals("You are about to close this dataset.\nDo you want to save your changes?", r.text("CloseMsg"));
        assertTrue(r.icon("newImage") != null && r.icon("openAllImage") != null && r.icon("saveAllImage") != null
                && r.icon("curSolImage") != null && r.logo() != null);
    }

    /** macOS "Settings...": the Look&Feel choice as a dialog; Cancel changes nothing, OK applies the selection. */
    @Test
    void preferencesDialog() {
        try (GuiTestSupport.TestApp t = GuiTestSupport.newApp("prefs-dialog", TestHooks.none())) {
            String before = GuiTestSupport.onEdt(() -> javax.swing.UIManager.getLookAndFeel().getClass().getName());
            t.ui.answerDialog(javax.swing.JOptionPane.CANCEL_OPTION);
            assertNull(GuiTestSupport.onEdt(() -> t.actions().showPreferences()));
            assertEquals("Preferences", t.ui.lastShown().title());
            javax.swing.JComboBox<?> box = GuiTestSupport.find(t.ui.lastShown().pane(), ComponentNames.PREFERENCES_LOOK_AND_FEEL,
                    javax.swing.JComboBox.class);
            assertEquals(LookAndFeels.installed().size(), box.getItemCount());
            assertEquals(javax.swing.UIManager.getLookAndFeel().getName(), box.getSelectedItem());
            t.ui.answerDialog(javax.swing.JOptionPane.OK_OPTION);
            assertEquals(before, GuiTestSupport.onEdt(() -> t.actions().showPreferences()), "OK applies the selection (unchanged)");
        }
    }
}
