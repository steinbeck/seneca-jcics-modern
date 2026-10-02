package io.github.steinbeck.seneca.app.structure;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.Border;

import io.github.steinbeck.seneca.app.AppContext;
import io.github.steinbeck.seneca.app.ComponentNames;
import io.github.steinbeck.seneca.app.ui.OptionPanes;
import io.github.steinbeck.seneca.app.ui.WindowHost;
import io.github.steinbeck.seneca.judges.ScoreSummary;
import io.github.steinbeck.seneca.structgen.StructureGeneratorResult;

/**
 * Content of the "Structure Display" window (port of {@code StrucDispFrame}, feature-inventory F-30,
 * F-67, F-90..F-94; docs/port/gui-design.md section 8): a scroll pane (600x400) with a left-aligned flow
 * of 300x200 structure tiles, each a {@link StructureView} with a titled etched border ("Score s" for
 * "Summarize Result", the file name for "Load MDL" / "Load LUCY Result") and, for results, the
 * evaluation HTML as tooltip; below it the buttons
 *
 * <ul>
 *   <li>"Load MDL" (F-91): several files; each read like the engine's own MDL strings
 *       ({@code chem.LegacyMdlReader}), one tile per file titled with the file name;</li>
 *   <li>"Load LUCY Result" (F-92): several files, {@code chem.LucyStructureReader};</li>
 *   <li>"Save MDL" (F-93): file dialog "Save MDL MOL Files"; a trailing ".mol" of the entered name is
 *       stripped (legacy rule); every tile is written as {@code <dir>/<name><index>.mol} (index 0-based,
 *       title line = that file name) in the legacy molfile format ({@code chem.LegacyMdlWriter}) with the
 *       coordinates of the drawing's layout;</li>
 *   <li>"Save SDF" (addition, DV-84): all tiles into one SD file (title line = tile title);</li>
 *   <li>"Copy" (addition, DV-84; also the tile's context menu and the menu shortcut C): the selected
 *       tile as MDL text and as image to the clipboard (a click selects a tile);</li>
 *   <li>"Close" (F-94).</li>
 * </ul>
 * Files, messages, the window and the clipboard go through {@code AppContext.ui} (scripted in tests).
 * EDT only.
 */
public final class StructureDisplayPanel extends JPanel {

    private static final Logger LOG = Logger.getLogger(StructureDisplayPanel.class.getName());

    /** Size of one tile (legacy {@code jmdDim}). */
    public static final Dimension TILE = new Dimension(300, 200);

    private final AppContext app;
    private final TilePanel tiles = new TilePanel();
    private final List<Tile> tileList = new ArrayList<>();
    private final JButton copyButton;
    private Tile selected;
    private WindowHost.Handle handle;
    private File directory;

    @SuppressWarnings("this-escape")    // Swing: children and listeners are set up in the constructor
    public StructureDisplayPanel(AppContext app) {
        super(new BorderLayout());
        this.app = app;
        setName(ComponentNames.STRUCTURE_DISPLAY);
        tiles.setName(ComponentNames.STRUCTURE_TILES);
        JScrollPane scroll = new JScrollPane(tiles);
        // 2001: 600x400, two tiles per row; plus the vertical scroll bar, so that two tiles still fit
        scroll.setPreferredSize(new Dimension(600 + scroll.getVerticalScrollBar().getPreferredSize().width + 4, 400));
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        add(scroll, BorderLayout.CENTER);

        // legacy GridLayout(2, 4) with four buttons = 2 x 2; with the two additions 2 x 3
        JPanel south = new JPanel(new GridLayout(2, 3));
        south.add(button("Load MDL", ComponentNames.STRUCTURE_LOAD_MDL, e -> loadMdl()));
        south.add(button("Load LUCY Result", ComponentNames.STRUCTURE_LOAD_LUCY, e -> loadLucy()));
        south.add(button("Save MDL", ComponentNames.STRUCTURE_SAVE_MDL, e -> saveMdl()));
        south.add(button("Save SDF", ComponentNames.STRUCTURE_SAVE_SDF, e -> saveSdf()));
        copyButton = button("Copy", ComponentNames.STRUCTURE_COPY, e -> copySelected());
        copyButton.setEnabled(false);
        copyButton.setToolTipText("Copy the selected structure (MDL text and picture)");
        south.add(copyButton);
        south.add(button("Close", ComponentNames.STRUCTURE_CLOSE, e -> close()));
        add(south, BorderLayout.SOUTH);

        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_C, menuShortcutMask()), "copy");
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_C, menuShortcutMask()), "copy");
        getActionMap().put("copy", new AbstractAction("copy") {
            @Override
            public void actionPerformed(ActionEvent e) {
                copySelected();
            }
        });
    }

    private static JButton button(String label, String name, java.awt.event.ActionListener l) {
        JButton b = new JButton(label);
        b.setName(name);
        b.addActionListener(l);
        return b;
    }

    /** Command on macOS, Ctrl elsewhere (also headless, where the toolkit cannot be asked). */
    static int menuShortcutMask() {
        if (!GraphicsEnvironment.isHeadless()) return Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac") ? InputEvent.META_DOWN_MASK
                : InputEvent.CTRL_DOWN_MASK;
    }

    // ------------------------------------------------------------------ opening

    /** Opens an empty "Structure Display" window ("View &gt; Current Structures", F-30). */
    public static StructureDisplayPanel open(AppContext app, String title) {
        StructureDisplayPanel p = new StructureDisplayPanel(app);
        p.handle = app.ui.windows().show(title, p, null);
        return p;
    }

    /**
     * Title of a "Structure Display" window showing results of the dataset {@code datasetName}:
     * {@code "Structure Display - " + name} (2001: always "Structure Display", so that several summaries
     * could not be told apart, DV-88); just "Structure Display" without a name.
     */
    public static String title(String datasetName) {
        return datasetName == null || datasetName.isBlank() ? "Structure Display" : "Structure Display - " + datasetName;
    }

    /**
     * "Summarize Result" (F-67): a new "Structure Display" window ({@link #title}) with one tile per
     * structure of the merged result, in its order, titled {@code "Score " + score} with the tooltip
     * {@code "<html><b>Score " + score + ": </b>" + description + "</html>"} (legacy ShowCurrentAction).
     */
    public static StructureDisplayPanel showResult(AppContext app, StructureGeneratorResult result, String datasetName) {
        StructureDisplayPanel p = open(app, title(datasetName));
        // the layouts (Layout2D: up to ~0.1 s for a large polycycle) in parallel, the tiles in order
        List<Depictions.Structure> drawn = java.util.stream.IntStream.range(0, result.size()).parallel()
                .mapToObj(f -> Depictions.fromMdl(result.structures.get(f))).toList();
        for (int f = 0; f < result.size(); f++) {
            ScoreSummary ss = result.scoreSummaries.get(f);
            String tip = "<html><b>Score " + ss.score + ": </b>" + ss.description + "</html>";
            p.addStructure(drawn.get(f), "Score " + ss.score, tip);
        }
        return p;
    }

    // ------------------------------------------------------------------ tiles

    /** Adds a tile (legacy {@code addStructure(jmd, title)}). */
    public Tile addStructure(Depictions.Structure s, String title, String tooltip) {
        Tile t = new Tile(s, title);
        t.setToolTipText(tooltip);
        tileList.add(t);
        tiles.add(t);
        tiles.revalidate();
        tiles.repaint();
        return t;
    }

    /** The panel holding the tiles. */
    public JPanel tiles() {
        return tiles;
    }

    /** The tiles, in display order. */
    public List<Tile> tileList() {
        return List.copyOf(tileList);
    }

    /** The selected tile, or null. */
    public Tile selected() {
        return selected;
    }

    /** Selects {@code t} (null: none); "Copy" acts on the selected tile. */
    public void select(Tile t) {
        if (selected != null) selected.setSelected(false);
        selected = t;
        if (t != null) t.setSelected(true);
        copyButton.setEnabled(t != null);
    }

    /** One structure tile: a {@link StructureView} 300x200 with a titled border. */
    public final class Tile extends StructureView {
        private final String title;
        private boolean isSelected;

        @SuppressWarnings("this-escape")
        Tile(Depictions.Structure s, String title) {
            this.title = title;
            setName(ComponentNames.STRUCTURE_TILE);
            setPreferredSize(TILE);
            setStructure(s);
            updateBorder();
            JPopupMenu popup = new JPopupMenu();
            JMenuItem copy = new JMenuItem("Copy");
            copy.addActionListener(e -> {
                select(this);
                copySelected();
            });
            popup.add(copy);
            setComponentPopupMenu(popup);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    select(Tile.this);
                    StructureDisplayPanel.this.requestFocusInWindow();
                }
            });
        }

        public String title() {
            return title;
        }

        public boolean isSelected() {
            return isSelected;
        }

        void setSelected(boolean b) {
            isSelected = b;
            updateBorder();
            repaint();
        }

        private void updateBorder() {
            Border line;
            if (isSelected) {
                Color c = UIManager.getColor("List.selectionBackground");
                line = BorderFactory.createLineBorder(c != null ? c : Color.blue, 2);
            } else {
                line = BorderFactory.createEtchedBorder();
            }
            setBorder(BorderFactory.createTitledBorder(line, title));
        }
    }

    /**
     * Flow of tiles (legacy {@code StrucContainer}: {@code FlowLayout(LEFT, 0, 0)}, as wide as the
     * viewport, as high as the rows need, and at least as high as the viewport, so that the panel's
     * background fills the window). A single tile (e.g. the best structure opened by a double click)
     * fills the whole viewport, so that its drawing is as large as the window allows (DV-88).
     */
    static final class TilePanel extends JPanel implements Scrollable {
        TilePanel() {
            super(new FlowLayout(FlowLayout.LEFT, 0, 0));
            setOpaque(true);
        }

        @Override
        public Dimension getPreferredSize() {
            int width = getParent() != null && getParent().getWidth() > 0 ? getParent().getWidth() : 600;
            width = Math.max(width, TILE.width);
            int perRow = Math.max(1, width / TILE.width);
            int n = getComponentCount();
            int rows = Math.max(1, (n + perRow - 1) / perRow);
            return new Dimension(width, rows * TILE.height);
        }

        @Override
        public void doLayout() {
            if (getComponentCount() == 1) {
                getComponent(0).setBounds(0, 0, Math.max(getWidth(), TILE.width), Math.max(getHeight(), TILE.height));
            } else {
                super.doLayout();
            }
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return new Dimension(600, 400);
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return 20;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return orientation == SwingConstants.VERTICAL ? TILE.height : TILE.width;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            // fill a taller viewport (no white band below the tiles); scroll when the rows need more
            return getParent() instanceof javax.swing.JViewport v && v.getHeight() > getPreferredSize().height;
        }
    }

    // ------------------------------------------------------------------ buttons

    /** "Load MDL" (F-91). */
    public void loadMdl() {
        List<File> files = app.ui.files().openMultiple(this, "Load MDL", directory(), null, null);
        for (File f : files) {
            remember(f);
            Depictions.Structure s;
            try {
                s = Depictions.fromMdl(Files.readString(f.toPath(), StandardCharsets.ISO_8859_1));
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Load MDL: cannot read " + f, e);
                message("Could not read " + f.getName() + ":\n" + e.getMessage());
                continue;
            }
            addStructure(s, f.getName(), null);
        }
    }

    /** "Load LUCY Result" (F-92). */
    public void loadLucy() {
        List<File> files = app.ui.files().openMultiple(this, "Load LUCY Result", directory(), null, null);
        for (File f : files) {
            remember(f);
            addStructure(Depictions.fromLucyFile(f.toPath()), f.getName(), null);
        }
    }

    /** "Save MDL" (F-93). Returns the files written. */
    public List<Path> saveMdl() {
        File f = app.ui.files().save(this, "Save MDL MOL Files", directory(), null, "mol", "MDL Molfile");
        if (f == null) return List.of();
        remember(f);
        Path dir = f.getAbsoluteFile().getParentFile().toPath();
        String name = baseName(f.getName());
        List<Path> written = new ArrayList<>();
        for (int g = 0; g < tileList.size(); g++) {
            String outName = name + g + ".mol";
            String mdl = tileList.get(g).structure() == null ? null : tileList.get(g).structure().mdl(outName);
            if (mdl == null) continue;
            Path out = dir.resolve(outName);
            if (!write(out, mdl)) return written;
            written.add(out);
        }
        return written;
    }

    /**
     * Legacy rule of "Save MDL": {@code i = name.lastIndexOf("mol")}; if it directly follows the last
     * ".", the name is cut at that "." ("x.mol" -&gt; "x"; legacy also cut "x.molfile").
     */
    public static String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot >= 0 && fileName.lastIndexOf("mol") == dot + 1) return fileName.substring(0, dot);
        return fileName;
    }

    /** "Save SDF" (DV-84): every tile, title line = tile title, then {@code $$$$}. Returns the file or null. */
    public Path saveSdf() {
        File f = app.ui.files().save(this, "Save SDF File", directory(), null, "sdf", "SD File");
        if (f == null) return null;
        remember(f);
        File target = f.getName().contains(".") ? f : new File(f.getPath() + ".sdf");
        if (target != f && !OptionPanes.mayReplace(app.ui.dialogs(), this, "Save SDF File", target)) return null;
        StringBuilder sb = new StringBuilder();
        for (Tile t : tileList) {
            String mdl = t.structure() == null ? null : t.structure().mdl(t.title());
            if (mdl == null) continue;
            sb.append(mdl).append("$$$$\n");
        }
        return write(target.toPath(), sb.toString()) ? target.toPath() : null;
    }

    /** Copy of the selected tile: MDL text (title line = tile title) and picture. */
    public void copySelected() {
        Tile t = selected;
        if (t == null || t.structure() == null || t.structure().atoms() == null) return;
        app.ui.clipboard().setContents(new StructureTransferable(t.structure().mdl(t.title()), t.image(TILE.width * 2, TILE.height * 2)));
    }

    /** "Close" (F-94). */
    public void close() {
        if (handle != null) handle.close();
    }

    private boolean write(Path p, String text) {
        try {
            Files.writeString(p, text, StandardCharsets.ISO_8859_1);
            return true;
        } catch (IOException e) {
            LOG.log(Level.WARNING, "cannot write " + p, e);
            message("Could not write " + p + ":\n" + e.getMessage());
            return false;
        }
    }

    private void message(String text) {
        app.ui.dialogs().show(this, "Structure Display", OptionPanes.message(text, JOptionPane.ERROR_MESSAGE));
    }

    private File directory() {
        if (directory == null) directory = app.preferences.senecaLocation().toFile();
        return directory;
    }

    private void remember(File f) {
        File parent = f.getAbsoluteFile().getParentFile();
        if (parent != null) directory = parent;
    }
}
