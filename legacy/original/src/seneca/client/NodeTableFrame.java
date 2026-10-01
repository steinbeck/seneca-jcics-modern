/* NodeTableFrame.java
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
import compchem.*;


public class NodeTableFrame extends JDialog{
	
	protected Atom[] setOfNodes;

    protected TableModel tableModel;
    protected JTable table;
    protected JScrollPane scrollpane;
    protected JPanel southPanel;
    protected JPanel centerPanel;    
    protected JButton closeButton;
    
    
    public NodeTableFrame(Atom[] setOfNodes, JFrame frame){
        super(frame, "Atom Properties");
        this.setOfNodes = setOfNodes;
        table = new JTable(new NodeTableModel()){
            public Dimension getPreferredSize(){
                return(new Dimension(scrollpane.getSize().width, (getRowCount() + 2) * getRowHeight()));   
            }
         };;
        FontMetrics fm = getFontMetrics(table.getFont());

        for(int f = 0; f < table.getColumnCount(); f++){
            TableColumn column = table.getColumn(table.getColumnName(f)); 
            column.setWidth((int)(fm.stringWidth(table.getColumnName(f)) * 1.2));
            DefaultTableCellRenderer columnRenderer = new DefaultTableCellRenderer(); 
            columnRenderer.setBackground(Color.yellow); 
            
        }
        
        setCellRendering();

        scrollpane = new JScrollPane(table);
        scrollpane.setBorder(new BevelBorder(BevelBorder.LOWERED));

        getContentPane().setLayout(new BorderLayout());
        southPanel = new JPanel();        
        southPanel.setLayout(new FlowLayout());
        centerPanel = new JPanel();        
        centerPanel.setLayout(new BorderLayout());
        closeButton = new JButton("Close");
        closeButton.addActionListener(new closeAction(this));
        southPanel.add(closeButton);
        getContentPane().add("South", southPanel);
        scrollpane.setPreferredSize(new Dimension(400, 400));
        centerPanel.add("Center", scrollpane);
        getContentPane().add("Center", centerPanel);
    
        this.pack();
        this.setVisible(true);
        scrollpane.repaint();
        
        
      }
    
    protected void setCellRendering(){
        JLabel label = new JLabel();
        TableColumn column;
        table.setRowSelectionAllowed(false);
        table.setColumnSelectionAllowed(false);

        for (int f = 0; f < table.getColumnCount(); f++){
            column = table.getColumn(table.getColumnName(f));
            label.setVerticalAlignment(JLabel.CENTER);
            label.setHorizontalAlignment(JLabel.CENTER);
            DefaultTableCellRenderer columnRenderer = new DefaultTableCellRenderer();
            columnRenderer.setBackground(Color.lightGray);
            column.setCellRenderer(columnRenderer);
     
        }
    }
    
    
    class NodeTableModel extends AbstractTableModel{
        String[] names = {"No.", "Symb.", "Max.Bond", "Max.Bond.Sum", "HCount"}; 
        NodeTableModel(){}
        public int getColumnCount() {return names.length;}
        public int getRowCount() {
            return setOfNodes.length;
        }
        
        public Object getValueAt(int row, int column) {
            Atom node;
            
            switch (column){
                case 0: 
                    return String.valueOf(row);
                case 1:
                    return setOfNodes[row].symbol;
                case 2: 
                    return String.valueOf(setOfNodes[row].maxBondOrder);
                case 3: 
                    return String.valueOf(setOfNodes[row].maxBondOrderSum);
                case 4: 
                    return String.valueOf(setOfNodes[row].HCount);
            }
            return "-";
        }

        public String getColumnName(int column) {
            return names[column];
        }
        
        public Class getColumnClass(int c) {return getValueAt(0, c).getClass();}
        
        public boolean isCellEditable(int row, int col){
        	if (col == 0) return false;
           	else return true;
        }
        
        public void setValueAt(Object aValue, int row, int column) {
            switch (column){
            	case 0: 
					// You can't change the numbering
                   break;
                case 1:
					setOfNodes[row].symbol = (String)aValue;
                   break;
                case 2: 
					setOfNodes[row].maxBondOrder = new Integer((String)aValue).intValue();
                   break;                    
                case 3: 
					setOfNodes[row].maxBondOrderSum = new Integer((String)aValue).intValue();
                   break;
                case 4: 
					setOfNodes[row].HCount = new Integer((String)aValue).intValue();
                   break;
            }
        
        }

    }
    
    class closeAction extends AbstractAction {
    	JDialog dialog;
    	closeAction(JDialog d) {
	        super("CloseTableFrame");
	        this.dialog = d;
    	}
        
        public void actionPerformed(ActionEvent e) {
            dialog.dispose();
      	}
    }
    

}