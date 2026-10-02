package io.github.steinbeck.seneca.app.dataset;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;

import javax.swing.AbstractListModel;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;

import io.github.steinbeck.seneca.app.ComponentNames;
import io.github.steinbeck.seneca.app.ui.OptionPanes;
import io.github.steinbeck.seneca.config.ScheduleConfiguration;
import io.github.steinbeck.seneca.model.AnnealingSchedule;

/**
 * Tab "Annealing Schedule" (F-43), port of {@code AnnealingScheduleConfigurator}: on the left a tabbed
 * pane with the one tab "Asymptotic Cooling" (fields {@code Initial Accept. Prob.:}, {@code Plateau
 * Steps:}, {@code Cooling Factor:}, {@code max. Uphill Steps:}), in the middle the buttons
 * "-&gt; Update -&gt;", "Remove &lt;-", "New -&gt;", on the right the list "Schedules" (the dataset's own
 * schedules, rendered {@code A-F:<cool>-IAP:<p0>-PS:<plateau>-UHS: <uphill>}).
 *
 * <p>The panel is a view of a {@link ScheduleConfiguration} (DEFAULT mode, DV-67): the list selection and
 * the four field texts are the configuration's; Update hands the field texts to it and an unparsable field
 * is reported with a message (nothing is changed); Remove selects the schedule that took the removed one's
 * place; New appends a default schedule (0.8 / 500 / 0.95 / 50) and selects it.
 */
public final class AnnealingSchedulePanel extends JPanel {

    private final DatasetContext ctx;
    private final ScheduleConfiguration config;
    private final ScheduleListModel listModel = new ScheduleListModel();
    private final JList<AnnealingSchedule> schedules = new JList<>(listModel);
    private final JTextField acceptance = new JTextField();
    private final JTextField plateau = new JTextField();
    private final JTextField cooling = new JTextField();
    private final JTextField uphill = new JTextField();
    private final JButton updateButton = new JButton("-> Update ->");
    private final JButton removeButton = new JButton("Remove <-");
    private final JButton newButton = new JButton("New ->");
    private boolean syncing;

    @SuppressWarnings("this-escape")    // Swing: listeners and children in the constructor
    public AnnealingSchedulePanel(DatasetContext ctx) {
        super(new BorderLayout());
        this.ctx = ctx;
        this.config = new ScheduleConfiguration(ctx.dataset(), ctx.app().compat);
        schedules.setName(ComponentNames.SCHEDULE_LIST);
        acceptance.setName(ComponentNames.SCHEDULE_ACCEPTANCE);
        plateau.setName(ComponentNames.SCHEDULE_PLATEAU);
        cooling.setName(ComponentNames.SCHEDULE_COOLING);
        uphill.setName(ComponentNames.SCHEDULE_UPHILL);
        updateButton.setName(ComponentNames.SCHEDULE_UPDATE);
        removeButton.setName(ComponentNames.SCHEDULE_REMOVE);
        newButton.setName(ComponentNames.SCHEDULE_NEW);

        JPanel asymptPanel = new JPanel(new GridLayout(6, 2));
        asymptPanel.add(new JLabel("Initial Accept. Prob.: "));
        asymptPanel.add(acceptance);
        asymptPanel.add(new JLabel("Plateau Steps: "));
        asymptPanel.add(plateau);
        asymptPanel.add(new JLabel("Cooling Factor: "));
        asymptPanel.add(cooling);
        asymptPanel.add(new JLabel("max. Uphill Steps: "));
        asymptPanel.add(uphill);
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Asymptotic Cooling", asymptPanel);

        schedules.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        schedules.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLoweredBevelBorder(), "Schedules"));
        schedules.setMinimumSize(new Dimension(100, 100));
        schedules.setPreferredSize(new Dimension(250, 150));
        schedules.setMaximumSize(new Dimension(Short.MAX_VALUE, Short.MAX_VALUE));
        schedules.addListSelectionListener(e -> {
            if (syncing || e.getValueIsAdjusting()) return;
            int before = config.selectedIndex();
            config.select(schedules.getSelectedIndex());
            if (config.selectedIndex() != before) sync();     // 2001: fields reload on a selection change only
            else if (schedules.getSelectedIndex() != before) selectInList();
        });

        Box linkerPanel = new Box(BoxLayout.Y_AXIS);
        for (JButton b : new JButton[] {updateButton, removeButton, newButton}) {
            b.setAlignmentX(Component.CENTER_ALIGNMENT);
            b.setAlignmentY(Component.CENTER_ALIGNMENT);
            linkerPanel.add(b);
        }
        updateButton.addActionListener(e -> update());
        removeButton.addActionListener(e -> {
            config.remove();
            sync();
        });
        newButton.addActionListener(e -> {
            config.addNew();
            sync();
        });

        Box centerPanel = new Box(BoxLayout.X_AXIS);
        centerPanel.add(tabbedPane);
        centerPanel.add(linkerPanel);
        centerPanel.add(schedules);
        add(centerPanel, BorderLayout.CENTER);
        sync();
    }

    public ScheduleConfiguration configuration() {
        return config;
    }

    /** "-&gt; Update -&gt;": the field texts into the selected schedule. */
    private void update() {
        config.setAcceptanceProbability(acceptance.getText());
        config.setPlateauSteps(plateau.getText());
        config.setCoolingFactor(cooling.getText());
        config.setMaxUphillSteps(uphill.getText());
        try {
            config.update();
        } catch (NumberFormatException e) {
            ctx.app().ui.dialogs().show(this, "Annealing Schedule", OptionPanes.message(
                    "The schedule was not changed: every field must be a number.\n" + e.getMessage(), JOptionPane.ERROR_MESSAGE));
        }
        sync();
    }

    /** List contents, selection and fields from the configuration. */
    private void sync() {
        syncing = true;
        try {
            listModel.changed();
            selectInList();
            acceptance.setText(config.acceptanceProbability());
            plateau.setText(config.plateauSteps());
            cooling.setText(config.coolingFactor());
            uphill.setText(config.maxUphillSteps());
        } finally {
            syncing = false;
        }
        schedules.repaint();
    }

    /** The list selection = the configuration's selection (without feedback into the configuration). */
    private void selectInList() {
        boolean was = syncing;
        syncing = true;
        try {
            int i = config.selectedIndex();
            if (i >= 0 && i < listModel.getSize()) schedules.setSelectedIndex(i);
            else schedules.clearSelection();
        } finally {
            syncing = was;
        }
    }

    /** The dataset's schedule list as list model. */
    private final class ScheduleListModel extends AbstractListModel<AnnealingSchedule> {
        private int lastSize = -1;

        @Override
        public int getSize() {
            return config.schedules().size();
        }

        @Override
        public AnnealingSchedule getElementAt(int index) {
            return config.schedules().get(index);
        }

        void changed() {
            int n = getSize();
            if (lastSize > n) fireIntervalRemoved(this, n, lastSize - 1);
            fireContentsChanged(this, 0, Math.max(0, n - 1));
            if (lastSize >= 0 && lastSize < n) fireIntervalAdded(this, lastSize, n - 1);
            lastSize = n;
        }
    }
}
