/* AnnealingScheduleConfigurator.java
 *
 * Copyright (C) 1997, 1998, 1999  Dr. Christoph Steinbeck
 *
 * Contact: steinbeck@ice.mpg.de
 *
 * This software is published and distributed under artistic license.
 * The intent of this license is to state the conditions under which this Package 
 * may be copied, such that the Copyright Holder maintains some semblance
 * of artistic control over the development of the package, while giving the 
 * users of the package the right to use and distribute the Package in a
 * more-or-less customary fashion, plus the right to make reasonable modifications.
 *
 * THIS PACKAGE IS PROVIDED "AS IS" AND WITHOUT ANY EXPRESS OR IMPLIED WARRANTIES, 
 * INCLUDING, WITHOUT LIMITATION, THE IMPLIED WARRANTIES OF MERCHANTIBILITY AND 
 * FITNESS FOR A PARTICULAR PURPOSE.
 * 
 * The complete text of the license can be found in a file called LICENSE 
 * accompanying this package.
 */

package seneca.client;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.table.*;
import javax.swing.event.*;
import javax.swing.border.*;
import java.util.Vector;
import compchem.*;
import seneca.core.*;
import seneca.structgen.*;

public class AnnealingScheduleConfigurator extends JPanel implements ListSelectionListener {

   Vector annealingSchedules;

   JTabbedPane tabbedPane= new JTabbedPane();
	JPanel schedulesPanel = new JPanel(); 
   JPanel asymptPanel = new JPanel(new GridLayout(6,2));
   Box centerPanel = new Box(BoxLayout.X_AXIS);
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
   
   Box linkerPanel = new Box(BoxLayout.Y_AXIS);
   SenecaDataset sd;
               
	public AnnealingScheduleConfigurator(SenecaDataset sd){
	   super();	
       this.sd = sd;
      this.annealingSchedules = sd.annealingSchedules;
	  setLayout(new BorderLayout());
      schedules = new JList(annealingSchedules);

      updateButton.addActionListener(new updateAction());
      removeButton.addActionListener(new removeAction());
      newButton.addActionListener(new newAction());
      

      
      asymptPanel.add(new JLabel("Initial Accept. Prob.: "));
      asymptPanel.add(a_accProb);
      asymptPanel.add(new JLabel("Plateau Steps: "));
      asymptPanel.add(a_platSteps);
      asymptPanel.add(new JLabel("Cooling Factor: "));
      asymptPanel.add(a_coolFact);
      asymptPanel.add(new JLabel("max. Uphill Steps: "));
      asymptPanel.add(a_maxUphillSteps);
      
      tabbedPane.addTab("Asymptotic Cooling", asymptPanel);
 
      schedules.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLoweredBevelBorder(), "Schedules"));
      schedules.setMinimumSize(new Dimension(100, 100)); 
      schedules.setPreferredSize(new Dimension(250, 150));
      schedules.setMaximumSize(new Dimension(Short.MAX_VALUE, Short.MAX_VALUE)); 

      linkerPanel.add(updateButton);
      linkerPanel.add(removeButton);    
      linkerPanel.add(newButton);    
      schedules.addListSelectionListener(this);

      updateButton.setAlignmentY(Component.CENTER_ALIGNMENT);
      updateButton.setAlignmentX(Component.CENTER_ALIGNMENT);
      removeButton.setAlignmentY(Component.CENTER_ALIGNMENT);
      removeButton.setAlignmentX(Component.CENTER_ALIGNMENT);
      newButton.setAlignmentY(Component.CENTER_ALIGNMENT);
      newButton.setAlignmentX(Component.CENTER_ALIGNMENT);

      centerPanel.add(tabbedPane);
      centerPanel.add(linkerPanel);
      centerPanel.add(schedules);
      
      
      add("Center", centerPanel);
      
      if (annealingSchedules.size() > 0) schedules.setSelectedIndex(annealingSchedules.size() - 1);
      
	}
   
   
   public void valueChanged(ListSelectionEvent e) {
      if (!e.getValueIsAdjusting()) {
         JList theList = (JList)e.getSource();
         if (theList.getSelectedIndex() >= 0){
            AnnealingSchedule as = ((AnnealingSchedule)annealingSchedules.elementAt(theList.getSelectedIndex()));
            a_accProb.setText(new Double(as.initialAcceptanceProbability).toString());
            a_platSteps.setText(new Long(as.plateauSteps).toString());
            a_coolFact.setText(new Double(as.asymptoticCoolingFactor).toString());
            a_maxUphillSteps.setText(new Double(as.maxUphillSteps).toString());
         }
      }
   }   
   
     
    class updateAction extends AbstractAction {
       updateAction() {
           super("Update");
       }
        
     public void actionPerformed(ActionEvent e) {
         if (schedules.getSelectedIndex() >= 0){
            AnnealingSchedule as = ((AnnealingSchedule)annealingSchedules.elementAt(schedules.getSelectedIndex()));
             as.initialAcceptanceProbability = new Double(a_accProb.getText()).doubleValue();
             as.plateauSteps = (long)new Double(a_platSteps.getText()).doubleValue();
             as.asymptoticCoolingFactor = new Double(a_coolFact.getText()).doubleValue();
             as.maxUphillSteps = (long)new Double(a_maxUphillSteps.getText()).doubleValue();
             schedules.repaint();
          }
       }
     }

    class removeAction extends AbstractAction {
       removeAction() {
           super("Remove");
       }
        
     public void actionPerformed(ActionEvent e) 
	 {
		 if (schedules.getSelectedIndex() >= 0){
		    annealingSchedules.removeElementAt(schedules.getSelectedIndex());
			if (schedules.getSelectedIndex() > 0)
			{
				schedules.setSelectedIndex(schedules.getSelectedIndex() - 1); schedules.setSelectedIndex(schedules.getSelectedIndex() - 1);
            	AnnealingSchedule as = ((AnnealingSchedule)annealingSchedules.elementAt(schedules.getSelectedIndex()));			
				as.initialAcceptanceProbability = new Double(a_accProb.getText()).doubleValue();
				as.plateauSteps = (long)new Double(a_platSteps.getText()).doubleValue();
				as.asymptoticCoolingFactor = new Double(a_coolFact.getText()).doubleValue();
				as.maxUphillSteps = (long)new Double(a_maxUphillSteps.getText()).doubleValue();
		     }
		  }
		     schedules.repaint();          
     }
    }

    class newAction extends AbstractAction {
       newAction() {
           super("New");
       }
        
     public void actionPerformed(ActionEvent e) {
       annealingSchedules.addElement(new AnnealingSchedule());
        schedules.setListData(annealingSchedules);
        schedules.setSelectedIndex(annealingSchedules.size() - 1);
        schedules.repaint();
       }
     }


     

}