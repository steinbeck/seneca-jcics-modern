package io.github.steinbeck.seneca.app.ui;

/**
 * Everything of the GUI that talks to the operating system or blocks on the user: file dialogs, modal
 * option panes, top-level windows and the clipboard (docs/port/gui-design.md, section "Testing without
 * a human"). Application code never creates a {@code JFrame}, {@code JDialog}, {@code FileDialog} or
 * {@code JFileChooser} and never touches the system clipboard directly; it goes through this object.
 * The application uses {@link #swing()}; the GUI tests install scripted implementations, so that every
 * action can be triggered headless with {@code doClick()} and nothing ever waits for a human.
 *
 * @param files     open/save dialogs
 * @param dialogs   modal option panes (confirmations, messages)
 * @param windows   top-level windows (Structure Display, Judge Configuration Report)
 * @param clipboard text/structure clipboard (WinNMR paste, copy of structures)
 */
public record UiServices(FileDialogs files, Dialogs dialogs, WindowHost windows, TextClipboard clipboard) {

    /** The real implementations (native macOS file dialogs, Swing dialogs and frames, system clipboard). */
    public static UiServices swing() {
        return new UiServices(new SwingFileDialogs(), new SwingDialogs(), new FrameWindowHost(), new SystemTextClipboard());
    }
}
