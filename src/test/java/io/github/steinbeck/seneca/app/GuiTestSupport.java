package io.github.steinbeck.seneca.app;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Assumptions;

import io.github.steinbeck.seneca.golden.GoldenFiles;

/**
 * Helpers for unattended GUI tests (docs/port/gui-design.md, "Testing without a human"):
 *
 * <ul>
 *   <li>EDT: {@link #onEdt(Callable)}, {@link #onEdt(EdtRunnable)}, {@link #flushEdt()},
 *       {@link #waitFor}.</li>
 *   <li>Offscreen rendering: {@link #render} lays a component tree out without a window (no native peer,
 *       works headless) and paints it into a {@link BufferedImage}; {@link #shot} writes it as PNG to
 *       {@link #shotsDir()} ({@code <build dir>/gui-shots}, surefire property {@code seneca.guishots}).</li>
 *   <li>Driving: {@link #find}/{@link #require} by {@link ComponentNames} name, {@link #click},
 *       {@link #menuItem} by label path.</li>
 *   <li>A complete application without a window: {@link #newApp} (scratch directory for the log files and
 *       preferences, {@code user.home} pointed at a scratch directory while the app is open, scripted UI).</li>
 * </ul>
 * Tests never use {@code java.awt.Robot}, never show a window and never touch the user's real
 * {@code ~/seneca.*} files. The surefire configuration runs them headless ({@code java.awt.headless=true}).
 */
public final class GuiTestSupport {

    private GuiTestSupport() {
    }

    // ------------------------------------------------------------------ EDT

    /** A runnable that may throw. */
    @FunctionalInterface
    public interface EdtRunnable {
        void run() throws Exception;
    }

    /** Runs {@code r} on the EDT and waits; exceptions are rethrown on the caller's thread. */
    public static void onEdt(EdtRunnable r) {
        onEdt(() -> {
            r.run();
            return null;
        });
    }

    /** Calls {@code c} on the EDT and returns its result; exceptions are rethrown on the caller's thread. */
    public static <T> T onEdt(Callable<T> c) {
        if (SwingUtilities.isEventDispatchThread()) {
            try {
                return c.call();
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    result.set(c.call());
                } catch (Throwable t) {
                    failure.set(t);
                }
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException(e.getCause());
        }
        Throwable t = failure.get();
        if (t instanceof RuntimeException re) throw re;
        if (t instanceof Error er) throw er;
        if (t != null) throw new IllegalStateException(t);
        return result.get();
    }

    /** Waits until every event queued on the EDT so far (and what those queue directly) has run. */
    public static void flushEdt() {
        onEdt(() -> { });
        onEdt(() -> { });
    }

    /** Polls {@code condition} (off the EDT) until true or {@code timeoutMillis}; returns the last value. */
    public static boolean waitFor(BooleanSupplier condition, long timeoutMillis) {
        long end = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < end) {
            if (condition.getAsBoolean()) return true;
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return condition.getAsBoolean();
    }

    // ------------------------------------------------------------------ rendering

    /** Directory of the PNG renderings ({@code seneca.guishots}, default {@code <repo>/target/gui-shots}). */
    public static Path shotsDir() {
        String p = System.getProperty("seneca.guishots");
        return p != null ? Path.of(p) : GoldenFiles.repo().resolve("target").resolve("gui-shots");
    }

    /** Lays out {@code c} and its descendants at its current bounds (no native peer needed). */
    public static void layoutTree(Component c) {
        if (c instanceof Container k) {
            k.doLayout();
            for (Component ch : k.getComponents()) layoutTree(ch);
        }
    }

    /**
     * Paints {@code c} at {@code width x height} (preferred size if 0) into an image (EDT). The component
     * keeps the size afterwards.
     */
    public static BufferedImage render(Component c, int width, int height) {
        return onEdt(() -> {
            Dimension pref = c.getPreferredSize();
            int w = width > 0 ? width : Math.max(1, pref.width);
            int h = height > 0 ? height : Math.max(1, pref.height);
            c.setSize(w, h);
            layoutTree(c);
            layoutTree(c);     // second pass: sizes of nested scroll panes and tables settle
            // with a display, native look and feels (Aqua) paint only components that have a peer: host a
            // parentless component in a hidden frame (addNotify creates the peer; the frame is never shown)
            javax.swing.JFrame host = null;
            if (!GraphicsEnvironment.isHeadless() && c.getParent() == null && !(c instanceof java.awt.Window)) {
                host = new javax.swing.JFrame();
                host.setUndecorated(true);
                host.getContentPane().setLayout(null);
                host.getContentPane().add(c);
                host.addNotify();
                host.setSize(w, h);
                c.setBounds(0, 0, w, h);
                host.validate();
            }
            try {
                layoutTree(c);
                BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();
                g.setColor(c.getBackground() != null ? c.getBackground() : Color.white);
                g.fillRect(0, 0, w, h);
                // a Swing component is painted, not printed: printing makes tables draw selected rows
                // unselected (JTable checks isPaintingForPrint), so the renderings would hide the selection.
                // Double buffering is off meanwhile (the image is the buffer). Windows paint their children
                // only while showing, so they are printed.
                if (c instanceof JComponent jc) {
                    javax.swing.RepaintManager rm = javax.swing.RepaintManager.currentManager(jc);
                    boolean db = rm.isDoubleBufferingEnabled();
                    rm.setDoubleBufferingEnabled(false);
                    try {
                        jc.paint(g);
                    } finally {
                        rm.setDoubleBufferingEnabled(db);
                    }
                } else {
                    c.printAll(g);
                }
                g.dispose();
                return img;
            } finally {
                if (host != null) {
                    host.getContentPane().remove(c);
                    host.dispose();
                }
            }
        });
    }

    /** Renders {@code c} (preferred size) and writes {@code <shotsDir>/<name>.png}. */
    public static Path shot(Component c, String name) {
        return shot(c, 0, 0, name);
    }

    /** Renders {@code c} at {@code width x height} and writes {@code <shotsDir>/<name>.png}. */
    public static Path shot(Component c, int width, int height, String name) {
        BufferedImage img = render(c, width, height);
        try {
            Files.createDirectories(shotsDir());
            Path p = shotsDir().resolve(name + ".png");
            ImageIO.write(img, "png", p.toFile());
            return p;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Renders the drop-down of a menu (its popup menu as a component). Aqua's popup menu UI paints only
     * on screen, so the popup itself is painted by the basic UI during the rendering (the items keep the
     * current look and feel).
     */
    public static Path shotMenu(JMenu menu, String name) {
        JPopupMenu p = onEdt(menu::getPopupMenu);
        onEdt(() -> p.setUI(new javax.swing.plaf.basic.BasicPopupMenuUI()));
        try {
            return shot(p, name);
        } finally {
            onEdt(() -> p.updateUI());
        }
    }

    // ------------------------------------------------------------------ finding and driving

    /** All components below {@code root} (depth first, including root; menus include their popup items). */
    public static List<Component> all(Component root) {
        List<Component> l = new ArrayList<>();
        collect(root, l);
        return l;
    }

    private static void collect(Component c, List<Component> out) {
        out.add(c);
        if (c instanceof JMenu m) {
            for (Component ch : m.getMenuComponents()) collect(ch, out);
        } else if (c instanceof Container k) {
            for (Component ch : k.getComponents()) collect(ch, out);
        }
    }

    /** The first component named {@code name} of type {@code type} below {@code root}, or null. */
    public static <T extends Component> T find(Component root, String name, Class<T> type) {
        return onEdt(() -> {
            for (Component c : all(root)) if (name.equals(c.getName()) && type.isInstance(c)) return type.cast(c);
            return null;
        });
    }

    /**
     * Like {@link #find}, but a missing component <b>aborts</b> the test (reported as skipped) with
     * {@code pending}: the scripted end-to-end tests run as far as the implementation goes.
     */
    public static <T extends Component> T require(Component root, String name, Class<T> type, String pending) {
        T c = find(root, name, type);
        Assumptions.assumeTrue(c != null, () -> "component '" + name + "' not implemented yet (" + pending + ")");
        return c;
    }

    /** {@code doClick()} on the EDT. */
    public static void click(AbstractButton b) {
        onEdt(() -> b.doClick());
    }

    /** The menu item at {@code path} of labels (e.g. "File", "Open", "SENECA Dataset"), or null. */
    public static JMenuItem menuItem(JMenuBar bar, String... path) {
        return onEdt(() -> {
            JMenuItem cur = null;
            for (int i = 0; i < bar.getMenuCount() && cur == null; i++)
                if (path[0].equals(bar.getMenu(i).getText())) cur = bar.getMenu(i);
            for (int p = 1; p < path.length && cur != null; p++) {
                JMenuItem next = null;
                for (Component c : ((JMenu) cur).getMenuComponents())
                    if (c instanceof JMenuItem mi && path[p].equals(mi.getText())) next = mi;
                cur = next;
            }
            return cur;
        });
    }

    /** Labels of the entries of {@code menu} ("---" for a separator). */
    public static List<String> labels(JMenu menu) {
        return onEdt(() -> {
            List<String> l = new ArrayList<>();
            for (Component c : menu.getMenuComponents()) l.add(c instanceof JMenuItem mi ? mi.getText() : "---");
            return l;
        });
    }

    /** Tooltip of a component (EDT). */
    public static String tooltip(JComponent c) {
        return onEdt(() -> c.getToolTipText());
    }

    // ------------------------------------------------------------------ application

    /** A scratch directory below the build directory ({@code <build>/gui-scratch/<name>-<nanos>}). */
    public static Path scratchDir(String name) {
        try {
            Path base = shotsDir().getParent().resolve("gui-scratch");
            Files.createDirectories(base);
            return Files.createTempDirectory(base, name + "-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The bundled example dataset {@code legacy/original/sml/<key>.sml}. */
    public static Path sml(String key) {
        return GoldenFiles.sml(key);
    }

    /**
     * A complete application without a window: {@link AppContext} with scripted UI and log files in a
     * scratch directory, {@link MainView} and the menu bar built on the EDT, system look and feel.
     * {@code user.home} points at the scratch directory until {@link TestApp#close()}.
     */
    public static TestApp newApp(String name, TestHooks hooks) {
        Path dir = scratchDir(name);
        String oldHome = System.getProperty("user.home");
        System.setProperty("user.home", dir.toString());
        ScriptedUi ui = new ScriptedUi();
        return onEdt(() -> {
            applyTestLookAndFeel();
            AppContext app = AppContext.create(Resources.load(), ui.services(), AppPaths.under(dir), hooks);
            AppActions actions = new AppActions(app);
            MainView view = new MainView(app, actions);
            JMenuBar bar = MainMenuBar.create(app, actions);
            TestApp t = new TestApp(app, view, bar, ui, dir, oldHome);
            app.setExitHook(() -> t.exits++);
            return t;
        });
    }

    /**
     * Look and feel of the GUI tests: {@code seneca.test.laf} (class name) if set; else headless the
     * cross-platform one (Metal: the native macOS look and feel paints badly without a display), else
     * the system one ({@link LookAndFeels#systemClassName()}).
     */
    public static void applyTestLookAndFeel() {
        String laf = System.getProperty("seneca.test.laf");
        if (laf == null) laf = GraphicsEnvironment.isHeadless() ? javax.swing.UIManager.getCrossPlatformLookAndFeelClassName()
                : LookAndFeels.systemClassName();
        LookAndFeels.apply(laf);
    }

    /** An application built by {@link #newApp}. */
    public static final class TestApp implements AutoCloseable {
        public final AppContext app;
        public final MainView view;
        public final JMenuBar menuBar;
        public final ScriptedUi ui;
        public final Path dir;
        private final String oldHome;
        /** How often the exit hook ran. */
        public int exits;

        TestApp(AppContext app, MainView view, JMenuBar menuBar, ScriptedUi ui, Path dir, String oldHome) {
            this.app = app;
            this.view = view;
            this.menuBar = menuBar;
            this.ui = ui;
            this.dir = dir;
            this.oldHome = oldHome;
        }

        public AppActions actions() {
            return view.actions();
        }

        /** Stops all runs, closes the log files, restores {@code user.home}. */
        @Override
        public void close() {
            onEdt(app::shutdown);
            System.setProperty("user.home", oldHome);
        }
    }
}
