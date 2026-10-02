// Renders the windows of the 2001 SENECA client to PNG for comparison with the port's GUI
// (docs/port/gui-design.md, "Comparing with the legacy GUI"). Single-file program, run by
// scripts/legacy-gui-shots.sh with the legacy classes on the class path. Nothing is shown on screen: the
// dataset window (an internal frame) is hosted in a hidden, never shown frame and painted offscreen.
// Uses the legacy look and feel (cross-platform Metal, as Seneca's constructor sets it).
//
// The main window cannot be rendered this way (its constructor shows it); its content is simple and
// documented in docs/legacy/feature-inventory.md section 2.

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class LegacyShots {

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]);
        File sml = new File(args[1]);
        Files.createDirectories(out);
        UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        SwingUtilities.invokeAndWait(() -> {
            try {
                // Seneca.a4 (the Logbook appender) is set by Seneca's constructor, which shows the main
                // window; set it directly instead.
                Class<?> seneca = Class.forName("seneca.client.Seneca");
                Field a4 = seneca.getDeclaredField("a4");
                a4.setAccessible(true);
                Object appender = Class.forName("org.apache.log4j.gui.TextPaneAppender")
                        .getConstructor(Class.forName("org.apache.log4j.Layout"), String.class)
                        .newInstance(Class.forName("org.apache.log4j.PatternLayout").getConstructor(String.class).newInstance("%m"), "A4");
                a4.set(null, appender);

                Object reader = Class.forName("seneca.core.SpecMLReader").getConstructor(File.class).newInstance(sml);
                Object sd = reader.getClass().getMethod("getSenecaDataset").invoke(reader);
                JInternalFrame f = (JInternalFrame) Class.forName("seneca.client.SenecaDatasetFrame")
                        .getConstructor(Class.forName("seneca.core.SenecaDataset")).newInstance(sd);
                JFrame host = new JFrame();
                host.setUndecorated(true);
                host.getContentPane().setLayout(null);
                host.getContentPane().add(f);
                host.addNotify();
                host.setSize(1000, 700);
                f.setBounds(0, 0, 1000, 700);
                f.setVisible(true);
                JTabbedPane tabs = firstTabs(f);
                for (int i = 0; i < tabs.getTabCount(); i++) {
                    tabs.setSelectedIndex(i);
                    String name = "legacy-dataset-" + (i + 1) + "-" + tabs.getTitleAt(i).replace(' ', '_');
                    shot(f, out.resolve(name + ".png"));
                    Component sel = tabs.getSelectedComponent();
                    JTabbedPane sub = sel instanceof JTabbedPane t ? t : null;
                    if (sub != null) {
                        for (int k = 0; k < sub.getTabCount(); k++) {
                            sub.setSelectedIndex(k);
                            shot(f, out.resolve(name + "-" + (k + 1) + "-" + sub.getTitleAt(k).replace(' ', '_').replace(',', '_') + ".png"));
                        }
                    }
                }
                host.dispose();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        System.exit(0);
    }

    static JTabbedPane firstTabs(Component c) {
        if (c instanceof JTabbedPane t) return t;
        if (c instanceof Container k) for (Component ch : k.getComponents()) {
            JTabbedPane t = firstTabs(ch);
            if (t != null) return t;
        }
        return null;
    }

    static void layout(Component c) {
        if (c instanceof Container k) {
            k.doLayout();
            for (Component ch : k.getComponents()) layout(ch);
        }
    }

    static void shot(Component c, Path file) throws Exception {
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
