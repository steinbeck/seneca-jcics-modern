/* ConnTablePanel.java
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
import jmdraw.*;
import seneca.structgen.*;

/** Subclass of JFrame that allows for editing a connectivity matrix or any other square
    matrix in a JTable */

public class ConnTablePanel extends JPanel{

    protected TableModel tableModel;
    protected JTable table;
    protected JScrollPane scrollpane;
    protected JPanel southPanel;
    protected JPanel centerPanel;    

    protected int maxRowHeight;
    protected int sumColWidth = 0;
    protected int sumRowHeight = 0;
    protected int[][] matrix;

/** Constructs a ConTableFrame from a given MolMatrix */
    ConnTablePanel(int[][] matrix){
   	   super();
        tableModel = new ConnTableModel();
        setLayout(new BorderLayout());
        southPanel = new JPanel();        
        southPanel.setLayout(new FlowLayout());
        centerPanel = new JPanel();        
        centerPanel.setLayout(new BorderLayout());
        add("South", southPanel);
        table = new JTable(tableModel);
     table.sizeColumnsToFit(JTable.AUTO_RESIZE_ALL_COLUMNS);
     table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);         
     scrollpane = new JScrollPane(table);
     scrollpane.setBorder(new BevelBorder(BevelBorder.LOWERED));
     centerPanel.add("Center", scrollpane);
    add("Center", centerPanel);
        this.setVisible(true);
        scrollpane.invalidate();
        scrollpane.validate();

    }
    
    
    class ConnTableModel extends AbstractTableModel{
        ConnTableModel(){}
        public int getColumnCount() {return matrix.length;}
        
        public String getColumnName(int col) {
            return new Integer(col + 1).toString();
        }
        
        public int getRowCount() {return matrix.length;}
        
        public Object getValueAt(int row, int column) {
            return new Integer(matrix[row][column]);
        }
        
        public Class getColumnClass(int c) {return getValueAt(0, c).getClass();}
        
        public boolean isCellEditable(int row, int col){
           return false;
        }
        
        public void setValueAt(Object aValue, int row, int column) {
        }
    }
 
    /** Action class for the "Stop" button of ConnTableFrame */
    class stopAction extends AbstractAction {
    	StructureGenerator dg;
    	stopAction(StructureGenerator dg) {
	        super("Stop");
	        this.dg = dg;
    	}
        
	      public void actionPerformed(ActionEvent e) {

      	}
    }
}