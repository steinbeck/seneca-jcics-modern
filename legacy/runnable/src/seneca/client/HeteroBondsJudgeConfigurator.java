/* HeteroBondsJudgeConfigurator.java
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
import javax.swing.border.*;
import javax.swing.event.*;
import javax.swing.table.*;
import java.util.Vector;
import seneca.structgen.*;
import seneca.judges.*;
import seneca.core.*;
import compchem.*;



/**
 * This class provides a gui for configuring a HybridizationJudge
 */
public class HeteroBondsJudgeConfigurator extends JudgeConfigurator
{
	HeteroBondsJudge hbJudge = null;

    HeteroBondsJudgeConfigurator(SenecaDataset sd)
    {
        super(sd, (HeteroBondsJudge)sd.getJudge("HeteroBondsJudge"));
		this.hbJudge = (HeteroBondsJudge)sd.getJudge("HeteroBondsJudge");
		hbJudge.getAttic().atomSet = sd.getSetOfNodes();
		hbJudge.init();
    }


    protected Box contructCenterBox()
    {
    	Box centerBox = new Box(BoxLayout.X_AXIS);			
    	JScrollPane scrollpane = new JScrollPane(new JTable(new HeteroBondsTableModel()));
    	scrollpane.setBorder(getTitledBorder());
    	scrollpane.setPreferredSize(new Dimension(200, 400));
    	centerBox.add(scrollpane);

    	return centerBox;
    }




    class HeteroBondsTableModel extends AbstractTableModel
    {
        String[] names = {"No.", "Symb.", "Shift", "hetero-attached?"};
        HeteroBondsTableModel()
        {}

        public int getColumnCount()
        {
            return names.length;
        }

        public int getRowCount()
        {
            if (sd.setOfNodes == null)
                return 0;
            return sd.setOfNodes.length;
        }

        public Object getValueAt(int row, int column)
        {
            Atom node;
			if (hbJudge == null) return "";
			if (hbJudge.isHeteroBonded == null) return "";
            if (hbJudge.isHeteroBonded.length < 1) return "";
            switch (column)
            {
                case 0:
                    return String.valueOf(row);
                case 1:
                    return sd.setOfNodes[row].symbol;
                case 2:
					if (sd.setOfNodes[row].getPhysicalProperty(Atom.PHYS_PROP_13C_SHIFT) != null)
					    return String.valueOf(sd.setOfNodes[row].getPhysicalProperty(Atom.PHYS_PROP_13C_SHIFT));
					else return "-";
                case 3:
                    return new Boolean(hbJudge.isHeteroBonded[row]);
            }
            return "-";
        }

        public String getColumnName(int column)
        {
            return names[column];
        }

        public Class getColumnClass(int c)
        {
            return getValueAt(0, c).getClass();
        }

        public boolean isCellEditable(int row, int col)
        {
            if (col < 2)
                return false;
            else
                return true;
        }

        public void setValueAt(Object aValue, int row, int column)
        {
            switch (column)
            {
                case 0:
                    // You can't change the numbering
                    break;
                case 1:
                    // You can't change the symbols.
                    break;
                case 2:
                        // You can't change the shift values
                        break;
                case 3:
                    hbJudge.isHeteroBonded[row] = ((Boolean)aValue).booleanValue();
                    break;
            }
			fireTableDataChanged();
        }

    }
}