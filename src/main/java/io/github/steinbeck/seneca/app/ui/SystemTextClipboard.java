package io.github.steinbeck.seneca.app.ui;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

/** {@link TextClipboard} on the system clipboard. */
public final class SystemTextClipboard implements TextClipboard {

    private static Clipboard clipboard() {
        return Toolkit.getDefaultToolkit().getSystemClipboard();
    }

    @Override
    public String getText() {
        try {
            Transferable t = clipboard().getContents(null);
            if (t == null || !t.isDataFlavorSupported(DataFlavor.stringFlavor)) return null;
            return (String) t.getTransferData(DataFlavor.stringFlavor);
        } catch (UnsupportedFlavorException | IOException | IllegalStateException e) {
            return null;
        }
    }

    @Override
    public void setText(String text) {
        StringSelection s = new StringSelection(text);
        clipboard().setContents(s, s);
    }

    @Override
    public void setContents(Transferable contents) {
        clipboard().setContents(contents, null);
    }
}
