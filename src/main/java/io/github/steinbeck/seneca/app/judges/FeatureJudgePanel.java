package io.github.steinbeck.seneca.app.judges;

import java.awt.event.ItemEvent;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

import io.github.steinbeck.seneca.app.ComponentNames;
import io.github.steinbeck.seneca.app.dataset.DatasetContext;
import io.github.steinbeck.seneca.config.JudgeConfiguration;
import io.github.steinbeck.seneca.judges.FeatureJudge;

/**
 * Sub-tab "Features" (F-54, {@link FeatureJudge}), port of {@code FeatureJudgeConfigurator}: the table
 * "Ringsize Scores" ({@code Ring Size}, {@code Score}; read-only, rows in the order of the judge's
 * {@code Hashtable}, as 2001) and the check box "Hetero-Hetero Atom Bonds forbidden"
 * ({@link JudgeConfiguration#setHetHetBondsForbidden}), which follows the judge (Autoconfigure ticks it).
 * Autoconfigure sets ring 3 -&gt; -500, ring 4 -&gt; -300 and the het-het penalty (core).
 */
public final class FeatureJudgePanel extends JudgeConfiguratorPanel {

    private final FeatureJudge featureJudge;
    private final Model model = new Model();
    private final JTable table;
    private final JCheckBox hetHetCheckBox = new JCheckBox("Hetero-Hetero Atom Bonds forbidden");

    @SuppressWarnings("this-escape")    // Swing: children in the constructor
    public FeatureJudgePanel(DatasetContext ctx) {
        super(ctx, FeatureJudge.class);
        this.featureJudge = (FeatureJudge) judge;
        table = new JTable(model);
        table.setName(ComponentNames.JUDGE_TABLE + "FeatureJudge");
        hetHetCheckBox.setName(ComponentNames.FEATURE_HETHET);
        hetHetCheckBox.setSelected(featureJudge.isHetHetPenaltyEnabled());
        hetHetCheckBox.addItemListener(e -> {
            boolean selected = e.getStateChange() == ItemEvent.SELECTED;
            if (featureJudge.isHetHetPenaltyEnabled() != selected) JudgeConfiguration.setHetHetBondsForbidden(sd, selected);
        });
        Box centerBox = new Box(BoxLayout.Y_AXIS);
        JScrollPane scrollpane = new JScrollPane(table);
        scrollpane.setColumnHeaderView(table.getTableHeader());     // headers also before addNotify (offscreen renderings)
        scrollpane.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Ringsize Scores"));
        centerBox.add(scrollpane);
        centerBox.add(hetHetCheckBox);
        setCenter(centerBox);
    }

    public JTable table() {
        return table;
    }

    public JCheckBox hetHetCheckBox() {
        return hetHetCheckBox;
    }

    @Override
    protected void refresh() {
        refreshTable(table, model);
        if (hetHetCheckBox.isSelected() != featureJudge.isHetHetPenaltyEnabled())
            hetHetCheckBox.setSelected(featureJudge.isHetHetPenaltyEnabled());
    }

    private final class Model extends AbstractTableModel {
        private final String[] names = {"Ring Size", "Score"};

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public String getColumnName(int column) {
            return names[column];
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return Integer.class;
        }

        @Override
        public int getRowCount() {
            return featureJudge.ringSizeScores.size();
        }

        @Override
        public Object getValueAt(int row, int column) {
            int count = 0;
            for (Integer key : featureJudge.ringSizeScores.keySet()) {
                if (count++ == row) return column == 0 ? key : featureJudge.ringSizeScores.get(key);
            }
            return null;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    }
}
