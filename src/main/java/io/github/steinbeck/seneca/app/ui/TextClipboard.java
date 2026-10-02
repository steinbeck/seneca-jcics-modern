package io.github.steinbeck.seneca.app.ui;

import java.awt.datatransfer.Transferable;

/**
 * The clipboard as the application uses it: reading text ("Paste WinNMR pp from clipboard", F-41/7.6) and
 * putting structures on it (copy in the Structure Display). {@link SystemTextClipboard} in the application,
 * an in-memory clipboard in tests (the system clipboard is unavailable headless).
 */
public interface TextClipboard {

    /** The clipboard's text, or null if it holds no text. */
    String getText();

    /** Puts plain text on the clipboard. */
    void setText(String text);

    /** Puts arbitrary content on the clipboard (e.g. an image and MDL text of a structure). */
    void setContents(Transferable contents);
}
