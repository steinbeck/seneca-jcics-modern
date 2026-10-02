package io.github.steinbeck.seneca.app.judges;

import java.awt.BorderLayout;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import io.github.steinbeck.seneca.app.ComponentNames;
import io.github.steinbeck.seneca.app.dataset.DatasetContext;
import io.github.steinbeck.seneca.judges.HHCOSYJudge;
import io.github.steinbeck.seneca.judges.HMBCJudge;

/**
 * Tab "Judges" (F-44, F-50..F-56, F-81): one sub-tab per judge configurator, in 2001 order, each with
 * its centre panel and the common bottom row "Autoconfigure Judge", "Report Judgeconfig",
 * "Activate this Judge?" ({@link JudgeConfiguratorPanel}). Legacy: {@code SenecaDatasetFrame.getJudgeTabbedPane}
 * and the 7 {@code *JudgeConfigurator} classes; core: {@code config.JudgeConfiguration}.
 */
public final class JudgesPanel extends JPanel {

    /** Sub-tab titles (legacy order), their tooltips (legacy: "Shifts" for "Carbon Shifts") and judge classes. */
    public static final String[][] TABS = {
        {"H,H-COSY", "H,H-COSY", "HHCOSYJudge"},
        {"C,H-long-range Corr.", "C,H-long-range Corr.", "HMBCJudge"},
        {"Carbon Shifts", "Shifts", "HOSECodeJudge"},
        {"Symmetric Atoms Classes", "Symmetric Atoms Classes", "SymmetryJudge"},
        {"Features", "Features", "FeatureJudge"},
        {"Hybridization", "Hybridization", "HybridizationJudge"},
        {"Heterobond Attachments", "Heterobond Attachments", "HeteroBondsJudge"},
    };

    private final JTabbedPane tabs = new JTabbedPane();

    @SuppressWarnings("this-escape")    // Swing: children in the constructor
    public JudgesPanel(DatasetContext ctx) {
        super(new BorderLayout());
        tabs.setName(ComponentNames.JUDGE_TABS);
        JudgeConfiguratorPanel[] panels = {
            new TwoDJudgePanel(ctx, HHCOSYJudge.class),
            new TwoDJudgePanel(ctx, HMBCJudge.class),
            new HoseCodeJudgePanel(ctx),
            new SymmetryJudgePanel(ctx),
            new FeatureJudgePanel(ctx),
            new HybridizationJudgePanel(ctx),
            new HeteroBondsJudgePanel(ctx),
        };
        for (int i = 0; i < TABS.length; i++) tabs.addTab(TABS[i][0], null, panels[i], TABS[i][1]);
        add(tabs, BorderLayout.CENTER);
    }

    public JTabbedPane tabs() {
        return tabs;
    }

    /** The configurator of the judge class with this simple name, or null. */
    public JudgeConfiguratorPanel configurator(String judgeClass) {
        for (int i = 0; i < tabs.getTabCount(); i++)
            if (tabs.getComponentAt(i) instanceof JudgeConfiguratorPanel p && p.judge().getClass().getSimpleName().equals(judgeClass)) return p;
        return null;
    }
}
