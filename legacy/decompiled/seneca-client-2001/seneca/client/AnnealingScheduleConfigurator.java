package seneca.client;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.util.Vector;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import seneca.core.SenecaDataset;
import seneca.structgen.AnnealingSchedule;

public class AnnealingScheduleConfigurator extends JPanel implements ListSelectionListener {
   Vector annealingSchedules;
   JTabbedPane tabbedPane = new JTabbedPane();
   JPanel schedulesPanel = new JPanel();
   JPanel asymptPanel = new JPanel(new GridLayout(6, 2));
   Box centerPanel = new Box(0);
   JPanel northPanel = new JPanel();
   JPanel southPanel = new JPanel();
   JTextField a_accProb = new JTextField();
   JTextField a_platSteps = new JTextField();
   JTextField a_coolFact = new JTextField();
   JTextField a_maxUphillSteps = new JTextField();
   JButton updateButton = new JButton("-> Update ->");
   JButton removeButton = new JButton("Remove <-");
   JButton newButton = new JButton("New ->");
   JList schedules;
   Box linkerPanel = new Box(1);
   SenecaDataset sd;

   public AnnealingScheduleConfigurator(SenecaDataset var1) {
      this.sd = var1;
      this.annealingSchedules = var1.annealingSchedules;
      this.setLayout(new BorderLayout());
      this.schedules = new JList(this.annealingSchedules);
      this.updateButton.addActionListener(new AnnealingScheduleConfigurator.updateAction());
      this.removeButton.addActionListener(new AnnealingScheduleConfigurator.removeAction());
      this.newButton.addActionListener(new AnnealingScheduleConfigurator.newAction());
      this.asymptPanel.add(new JLabel("Initial Accept. Prob.: "));
      this.asymptPanel.add(this.a_accProb);
      this.asymptPanel.add(new JLabel("Plateau Steps: "));
      this.asymptPanel.add(this.a_platSteps);
      this.asymptPanel.add(new JLabel("Cooling Factor: "));
      this.asymptPanel.add(this.a_coolFact);
      this.asymptPanel.add(new JLabel("max. Uphill Steps: "));
      this.asymptPanel.add(this.a_maxUphillSteps);
      this.tabbedPane.addTab("Asymptotic Cooling", this.asymptPanel);
      this.schedules.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLoweredBevelBorder(), "Schedules"));
      this.schedules.setMinimumSize(new Dimension(100, 100));
      this.schedules.setPreferredSize(new Dimension(250, 150));
      this.schedules.setMaximumSize(new Dimension(32767, 32767));
      this.linkerPanel.add(this.updateButton);
      this.linkerPanel.add(this.removeButton);
      this.linkerPanel.add(this.newButton);
      this.schedules.addListSelectionListener(this);
      this.updateButton.setAlignmentY(0.5F);
      this.updateButton.setAlignmentX(0.5F);
      this.removeButton.setAlignmentY(0.5F);
      this.removeButton.setAlignmentX(0.5F);
      this.newButton.setAlignmentY(0.5F);
      this.newButton.setAlignmentX(0.5F);
      this.centerPanel.add(this.tabbedPane);
      this.centerPanel.add(this.linkerPanel);
      this.centerPanel.add(this.schedules);
      this.add("Center", this.centerPanel);
      if (this.annealingSchedules.size() > 0) {
         this.schedules.setSelectedIndex(this.annealingSchedules.size() - 1);
      }
   }

   public void valueChanged(ListSelectionEvent var1) {
      if (!var1.getValueIsAdjusting()) {
         JList var2 = (JList)var1.getSource();
         if (var2.getSelectedIndex() >= 0) {
            AnnealingSchedule var3 = (AnnealingSchedule)this.annealingSchedules.elementAt(var2.getSelectedIndex());
            this.a_accProb.setText(new Double(var3.initialAcceptanceProbability).toString());
            this.a_platSteps.setText(new Long(var3.plateauSteps).toString());
            this.a_coolFact.setText(new Double(var3.asymptoticCoolingFactor).toString());
            this.a_maxUphillSteps.setText(new Double(var3.maxUphillSteps).toString());
         }
      }
   }

   class newAction extends AbstractAction {
      newAction() {
         super("New");
      }

      public void actionPerformed(ActionEvent var1) {
         AnnealingScheduleConfigurator.this.annealingSchedules.addElement(new AnnealingSchedule());
         AnnealingScheduleConfigurator.this.schedules.setListData(AnnealingScheduleConfigurator.this.annealingSchedules);
         AnnealingScheduleConfigurator.this.schedules.setSelectedIndex(AnnealingScheduleConfigurator.this.annealingSchedules.size() - 1);
         AnnealingScheduleConfigurator.this.schedules.repaint();
      }
   }

   class removeAction extends AbstractAction {
      removeAction() {
         super("Remove");
      }

      public void actionPerformed(ActionEvent var1) {
         if (AnnealingScheduleConfigurator.this.schedules.getSelectedIndex() >= 0) {
            AnnealingScheduleConfigurator.this.annealingSchedules.removeElementAt(AnnealingScheduleConfigurator.this.schedules.getSelectedIndex());
            if (AnnealingScheduleConfigurator.this.schedules.getSelectedIndex() > 0) {
               AnnealingScheduleConfigurator.this.schedules.setSelectedIndex(AnnealingScheduleConfigurator.this.schedules.getSelectedIndex() - 1);
               AnnealingScheduleConfigurator.this.schedules.setSelectedIndex(AnnealingScheduleConfigurator.this.schedules.getSelectedIndex() - 1);
               AnnealingSchedule var2 = (AnnealingSchedule)AnnealingScheduleConfigurator.this.annealingSchedules
                  .elementAt(AnnealingScheduleConfigurator.this.schedules.getSelectedIndex());
               var2.initialAcceptanceProbability = new Double(AnnealingScheduleConfigurator.this.a_accProb.getText());
               var2.plateauSteps = (long)new Double(AnnealingScheduleConfigurator.this.a_platSteps.getText()).doubleValue();
               var2.asymptoticCoolingFactor = new Double(AnnealingScheduleConfigurator.this.a_coolFact.getText());
               var2.maxUphillSteps = (long)new Double(AnnealingScheduleConfigurator.this.a_maxUphillSteps.getText()).doubleValue();
            }
         }

         AnnealingScheduleConfigurator.this.schedules.repaint();
      }
   }

   class updateAction extends AbstractAction {
      updateAction() {
         super("Update");
      }

      public void actionPerformed(ActionEvent var1) {
         if (AnnealingScheduleConfigurator.this.schedules.getSelectedIndex() >= 0) {
            AnnealingSchedule var2 = (AnnealingSchedule)AnnealingScheduleConfigurator.this.annealingSchedules
               .elementAt(AnnealingScheduleConfigurator.this.schedules.getSelectedIndex());
            var2.initialAcceptanceProbability = new Double(AnnealingScheduleConfigurator.this.a_accProb.getText());
            var2.plateauSteps = (long)new Double(AnnealingScheduleConfigurator.this.a_platSteps.getText()).doubleValue();
            var2.asymptoticCoolingFactor = new Double(AnnealingScheduleConfigurator.this.a_coolFact.getText());
            var2.maxUphillSteps = (long)new Double(AnnealingScheduleConfigurator.this.a_maxUphillSteps.getText()).doubleValue();
            AnnealingScheduleConfigurator.this.schedules.repaint();
         }
      }
   }
}
