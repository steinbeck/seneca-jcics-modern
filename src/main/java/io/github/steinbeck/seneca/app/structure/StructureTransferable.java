package io.github.steinbeck.seneca.app.structure;

import java.awt.Image;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;

/**
 * Clipboard content of a copied structure tile (Structure Display "Copy", DV-84): the structure as
 * MDL molfile text (plain text: pastes into editors and most chemistry programs) and as a picture.
 */
public final class StructureTransferable implements Transferable {

    private final String mdl;
    private final Image image;

    public StructureTransferable(String mdl, Image image) {
        this.mdl = mdl;
        this.image = image;
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[] {DataFlavor.stringFlavor, DataFlavor.imageFlavor};
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return DataFlavor.stringFlavor.equals(flavor) || DataFlavor.imageFlavor.equals(flavor);
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
        if (DataFlavor.stringFlavor.equals(flavor)) return mdl;
        if (DataFlavor.imageFlavor.equals(flavor)) return image;
        throw new UnsupportedFlavorException(flavor);
    }
}
