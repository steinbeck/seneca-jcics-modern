package io.github.steinbeck.seneca.app.spectra;

import java.awt.BorderLayout;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import io.github.steinbeck.seneca.app.ComponentNames;
import io.github.steinbeck.seneca.app.dataset.DatasetContext;
import io.github.steinbeck.seneca.model.SpectrumSlot;

/**
 * Tab "NMR Spectra" (F-41): sub-tabs 1D Carbon, DEPT-90, DEPT-135, H,C-HetCor (1JCH), H,C-lr-HetCor
 * (HMBC), H,H-COSY, each a {@link SpectrumTablePanel} on the dataset's spectrum of that slot. NOESY is
 * kept in the data (read and written) but has no tab, as in 2001. Legacy: {@code SenecaDatasetFrame}
 * (sub-tab construction) and {@code NMRSpectrumTablePanel}; core: {@code model.NMRSpectrum},
 * {@code io.WinNmrPeakPicking}.
 */
public final class SpectraPanel extends JPanel {

    /** Sub-tab titles in legacy order (the tooltips are the titles). */
    public static final String[] TITLES = {"1D Carbon", "DEPT-90", "DEPT-135", "H,C-HetCor", "H,C-lr-HetCor", "H,H-COSY"};
    /** The spectrum of each sub-tab. */
    public static final SpectrumSlot[] SLOTS = {SpectrumSlot.CARBON1D, SpectrumSlot.DEPT90, SpectrumSlot.DEPT135,
        SpectrumSlot.HETCOR, SpectrumSlot.HETCORLR, SpectrumSlot.HHCOSY};

    private final JTabbedPane tabs = new JTabbedPane();

    @SuppressWarnings("this-escape")    // Swing: children in the constructor
    public SpectraPanel(DatasetContext ctx) {
        super(new BorderLayout());
        tabs.setName(ComponentNames.SPECTRA_TABS);
        for (int i = 0; i < TITLES.length; i++) tabs.addTab(TITLES[i], null, new SpectrumTablePanel(ctx, SLOTS[i]), TITLES[i]);
        add(tabs, BorderLayout.CENTER);
    }

    public JTabbedPane tabs() {
        return tabs;
    }
}
