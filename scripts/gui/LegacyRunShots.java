// Drives the 2001 SENECA client's "Structure Generation" tab (seneca.client.SGServerPanel) in-process
// for a seeded pinene run and records what the user saw and what was written, for comparison with the
// port's run UI (docs/port/gui-design.md sections 6, 8, 9). Single-file program, run by
// scripts/legacy-run-shots.sh with the legacy classes, the harness's GuiRecipe and the legacy jars on
// the class path, user.home = a scratch directory (the legacy log4j configuration writes
// ~/seneca.log, ~/seneca.results.sdf, ~/seneca.annealing.log). Nothing is shown on screen.
//
// Steps (exactly the 2001 code paths, via the panel's own buttons and timer action):
//   1. log4j configured from seneca/client/logging.properties, Logbook appender A4 with Fancy=false
//      (as Seneca's constructor does) on a white pane (SenecaDatasetFrame);
//   2. the dataset prepared by the golden "gui" recipe (legacy/harness GuiRecipe);
//   3. new SGServerPanel(sd) in a hidden frame; its 2-second timer is stopped (polled by hand below);
//   4. "Start local server" x slots, select every row, Rng.seed(seed), "Start as selected";
//   5. wait for "Simulated annealing run finished." x slots in ~/seneca.log;
//   6. the timer action once per slot (round robin: every slot polled once after its run ended);
//   7. renderings: tab in score mode, chart toggled to kT by a mouse click;
//   8. "Log Annealing Data" (stdout captured);
//   9. the Structure Display content of "Summarize Result" (ShowCurrentAction's tiles; the frame itself
//      is not created because its constructor shows it on screen);
//  10. copies of the three log files and the Logbook text.
//
// With slots > 1 the runs share the legacy JVM-global Rng and are not reproducible; use slots = 1 for
// the comparison of files (golden seed 1 = 21248 iterations, ~1 s).

import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Vector;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;

public class LegacyRunShots {

    public static void main(String[] args) throws Exception {
        try {
            run(args);
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }

    static void run(String[] args) throws Exception {
        Path out = Path.of(args[0]);
        File sml = new File(args[1]);
        long seed = Long.parseLong(args[2]);
        int slots = Integer.parseInt(args[3]);
        Files.createDirectories(out);
        Path home = Path.of(System.getProperty("user.home"));
        UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());

        Properties props = new Properties();
        props.load(LegacyRunShots.class.getClassLoader().getResourceAsStream("seneca/client/logging.properties"));
        org.apache.log4j.PropertyConfigurator.configure(props);
        // Seneca's constructor: a4.setOption("Fancy", "false"); SenecaDatasetFrame: white background
        org.apache.log4j.gui.TextPaneAppender a4 = (org.apache.log4j.gui.TextPaneAppender)
                org.apache.log4j.Category.getInstance("seneca.structgen.StochasticGenerator.log").getAppender("A4");
        a4.setOption("Fancy", "false");
        a4.getTextPane().setBackground(java.awt.Color.white);

        seneca.client.GuiRecipe.Prepared prep = seneca.client.GuiRecipe.prepare(sml, "pinene", "gui");
        Object sd = prep.sd;
        Object[] holder = new Object[2];
        SwingUtilities.invokeAndWait(() -> {
            try {
                JComponent panel = (JComponent) Class.forName("seneca.client.SGServerPanel")
                        .getConstructor(Class.forName("seneca.core.SenecaDataset")).newInstance(sd);
                ((Timer) field(panel, "tableUpdateTimer")).stop();
                JFrame host = new JFrame();
                host.setUndecorated(true);
                host.getContentPane().setLayout(null);
                host.getContentPane().add(panel);
                host.addNotify();
                host.setSize(990, 590);
                panel.setBounds(0, 0, 990, 590);
                holder[0] = panel;
                holder[1] = host;
                for (int i = 0; i < slots; i++) ((AbstractButton) field(panel, "startLocalButton")).doClick();
                JTable table = (JTable) field(panel, "table");
                table.setRowSelectionInterval(0, slots - 1);
                shot(panel, out.resolve("legacy-run-0-slots.png"));
                seneca.structgen.Rng.seed(seed);
                ((AbstractButton) field(panel, "goButton")).doClick();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        JComponent panel = (JComponent) holder[0];

        long end = System.currentTimeMillis() + 600_000;
        while (System.currentTimeMillis() < end) {
            String log = Files.exists(home.resolve("seneca.log")) ? Files.readString(home.resolve("seneca.log"), StandardCharsets.ISO_8859_1) : "";
            if (count(log, "Simulated annealing run finished.") >= slots) break;
            Thread.sleep(200);
        }
        Thread.sleep(500);

        PrintStream stdout = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        SwingUtilities.invokeAndWait(() -> {
            try {
                Timer t = (Timer) field(panel, "tableUpdateTimer");
                for (int i = 0; i < slots; i++) t.getActionListeners()[0].actionPerformed(null);
                shot(panel, out.resolve("legacy-run-1-score.png"));
                JComponent chart = (JComponent) field(panel, "chartPanel");
                shot(chart, out.resolve("legacy-run-chart-score.png"));
                java.awt.event.MouseListener source = (java.awt.event.MouseListener) field(panel, "dataSource");
                source.mouseClicked(new MouseEvent(chart, MouseEvent.MOUSE_CLICKED, 0, 0, 10, 10, 1, false));
                shot(chart, out.resolve("legacy-run-chart-kt.png"));
                source.mouseClicked(new MouseEvent(chart, MouseEvent.MOUSE_CLICKED, 0, 0, 10, 10, 1, false));

                System.setOut(new PrintStream(captured, true, StandardCharsets.ISO_8859_1));
                ((AbstractButton) field(panel, "logAnnealingDataButton")).doClick();
                System.setOut(stdout);

                // "Summarize Result" without StrucDispFrame (its constructor shows the frame): the same tiles
                Vector<?> servers = (Vector<?>) field(panel, "sgServerProcesses");
                seneca.structgen.StructureGeneratorResult merged = new seneca.structgen.StructureGeneratorResult(servers.size() * 30);
                for (int i = 0; i < servers.size(); i++) {
                    seneca.structgen.StructureGeneratorResult r = (seneca.structgen.StructureGeneratorResult)
                            ((seneca.compute.Compute) servers.get(i)).getTaskResult();
                    int members = r.structures.size();
                    for (int f = 0; f < members; f++) {
                        merged.structures.push(r.structures.pop());
                        seneca.judges.ScoreSummary ss = (seneca.judges.ScoreSummary) r.scoreSummaries.pop();
                        ss.description += "<p>Result from host " + (i + 1) + "</p>";
                        merged.scoreSummaries.push(ss);
                    }
                }
                merged.removeIsomorphism();
                merged.sort();
                JPanel tiles = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
                StringBuilder titles = new StringBuilder();
                for (int f = 0; f < merged.structures.size(); f++) {
                    seneca.judges.ScoreSummary ss = (seneca.judges.ScoreSummary) merged.scoreSummaries.elementAt(f);
                    seneca.client.JMDrawPanel p = new seneca.client.JMDrawPanel();
                    p.setJMDraw(new jmdraw.JMDraw(compchem.ChemFileUtils.readMDLMolFile(new java.io.BufferedReader(
                            new java.io.StringReader((String) merged.structures.elementAt(f))))));
                    p.getJMDraw().draw();
                    p.setPreferredSize(new Dimension(300, 200));
                    p.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Score " + ss.score));
                    tiles.add(p);
                    // one line per tile: title, tab, tooltip HTML (newlines as \n)
                    String tip = "<html><b>Score " + ss.score + ": </b>" + ss.description + "</html>";
                    titles.append("Score ").append(ss.score).append('\t').append(tip.replace("\n", "\\n")).append('\n');
                }
                int rows = (merged.structures.size() + 1) / 2;
                JFrame host2 = new JFrame();
                host2.setUndecorated(true);
                host2.getContentPane().setLayout(null);
                host2.getContentPane().add(tiles);
                host2.addNotify();
                tiles.setBounds(0, 0, 600, Math.max(200, rows * 200));
                shot(tiles, out.resolve("legacy-structure-display.png"));
                host2.dispose();
                Files.writeString(out.resolve("legacy-summarize.txt"), titles.toString(), StandardCharsets.UTF_8);

                javax.swing.JTextPane pane = a4.getTextPane();
                String logbook = pane.getText();
                Files.writeString(out.resolve("legacy-logbook.txt"), logbook, StandardCharsets.ISO_8859_1);
                // the Logbook tab: the appender's text pane in a scroll pane (SenecaDatasetFrame)
                javax.swing.JScrollPane sp = new javax.swing.JScrollPane(pane);
                JFrame host3 = new JFrame();
                host3.setUndecorated(true);
                host3.getContentPane().setLayout(null);
                host3.getContentPane().add(sp);
                host3.addNotify();
                sp.setBounds(0, 0, 990, 590);
                shot(sp, out.resolve("legacy-logbook.png"));
                host3.dispose();
                ((JFrame) holder[1]).dispose();
            } catch (Exception e) {
                System.setOut(stdout);
                throw new RuntimeException(e);
            }
        });
        Files.write(out.resolve("legacy-stdout-annealing.txt"), captured.toByteArray());
        for (String f : new String[] {"seneca.log", "seneca.results.sdf", "seneca.annealing.log"})
            Files.copy(home.resolve(f), out.resolve("legacy-" + f), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        System.out.println("done " + out);
        System.exit(0);
    }

    static int count(String s, String what) {
        int n = 0;
        for (int i = s.indexOf(what); i >= 0; i = s.indexOf(what, i + 1)) n++;
        return n;
    }

    static Object field(Object o, String name) throws Exception {
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(o);
            } catch (NoSuchFieldException e) {
                // superclass
            }
        }
        throw new NoSuchFieldException(name);
    }

    static void layout(Component c) {
        if (c instanceof java.awt.Container k) {
            k.doLayout();
            for (Component ch : k.getComponents()) layout(ch);
        }
    }

    static void shot(JComponent c, Path file) throws Exception {
        c.validate();
        layout(c);
        layout(c);
        BufferedImage img = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        c.printAll(g);
        g.dispose();
        ImageIO.write(img, "png", file.toFile());
        System.out.println(file);
    }
}
