/* HybridizationJudgeConfigurator.java
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
public class HybridizationJudgeConfigurator extends JudgeConfigurator implements SpecMLConstants
{
	HybridizationJudge hyJudge = null;

    HybridizationJudgeConfigurator(SenecaDataset sd)
    {
        super(sd, (HybridizationJudge)sd.getJudge("HybridizationJudge"));
		this.hyJudge = (HybridizationJudge)sd.getJudge("HybridizationJudge");
		hyJudge.getAttic().atomSet = sd.getSetOfNodes();
		hyJudge.init();
		
    }
	

    protected Box contructCenterBox()
    {

    	Box centerBox = new Box(BoxLayout.X_AXIS);	
		JTable table = new JTable(new HybridizationTableModel());		
    	JScrollPane scrollpane = new JScrollPane(table);
    	scrollpane.setBorder(getTitledBorder());
    	scrollpane.setPreferredSize(new Dimension(200, 400));
    	JComboBox comboBox = new JComboBox();
    	comboBox.addItem("1.0");
    	comboBox.addItem("0.9");
    	comboBox.addItem("0.8");
    	comboBox.addItem("0.7");
    	comboBox.addItem("0.6");
    	comboBox.addItem("0.5");
    	comboBox.addItem("0.4");
    	comboBox.addItem("0.3");
    	comboBox.addItem("0.2");
    	comboBox.addItem("0.1");
    	comboBox.addItem("0.0");
    	table.getColumnModel().getColumn(3).setCellEditor(
    	  new DefaultCellEditor(comboBox));
    	table.getColumnModel().getColumn(4).setCellEditor(
    	  new DefaultCellEditor(comboBox));
    	table.getColumnModel().getColumn(5).setCellEditor(
    	  new DefaultCellEditor(comboBox));
    	centerBox.add(scrollpane);
    	return centerBox;
    }

	public static String getXML(Judge judge, Integer indentLevel, String indentString)
	{
		
		int iL = indentLevel.intValue();
		String iS = indentString;
		StringBuffer xml = new StringBuffer();
		HybridizationJudge hyJudge = (HybridizationJudge)judge;
		xml.append(SpecMLGenerator.getIndent(iL, iS) + "<judge title=\"" + hyJudge.name + "\">\n");
		iL++;
		for (int i = 0; i < hyJudge.scores.length; i ++)
		{
			xml.append(SpecMLGenerator.getIndent(iL, iS));
			for (int j = 0; j < 3; j ++)
			{
				xml.append("<judgeScore ");
				xml.append("atomref=\"" + i + "\" "); 
				xml.append("role=\"" + HybridizationJudge.HYBRIDIZATIONSTRINGS[j] + "\">"); 			
				iL++;
				xml.append(hyJudge.scores[i][j]);
				iL--;
				xml.append("</judgeScore>");
			}
			xml.append("\n");			
		}
		iL--;
		xml.append(SpecMLGenerator.getIndent(iL, iS) + "</judge>\n");
		return xml.toString();
	}
	public static Judge getJudgeFromXML(org.w3c.dom.Node node)
	{
		boolean debug = false;
		HybridizationJudge hyJudge = new HybridizationJudge();
		int score;
		int atomref;
		String role = null;
		if (debug) System.out.println("readAtom");
		org.w3c.dom.Node newNode = null;
		hyJudge.name = node.getAttributes().getNamedItem("title").getNodeValue();		
		hyJudge.initScoreArray(node.getChildNodes().getLength());
		for (int f = 0; f < node.getChildNodes().getLength(); f++)
		{
			newNode = node.getChildNodes().item(f);
			if (typeName[newNode.getNodeType()].equals("Element"))
			{
				if (newNode.getNodeName().toLowerCase().equals("judgescore"))
				{
					atomref = new Integer(newNode.getAttributes().getNamedItem("atomref").getNodeValue()).intValue();						
					role = newNode.getAttributes().getNamedItem("role").getNodeValue().trim();											
					score = new Integer(newNode.getChildNodes().item(0).getNodeValue()).intValue();	
					if (role.equals(hyJudge.HYBRIDIZATIONSTRINGS[hyJudge.SP3]))
					{
						hyJudge.addHybridizationScore(atomref, hyJudge.SP3, score);
					}																				
					
					if (debug) System.out.println(score);
				}
			}
		}
		return (Judge)hyJudge;
	}


    class HybridizationTableModel extends AbstractTableModel
    {
        String[] names = {"No.", "Symb.", "Shift", "sp3", "sp2", "sp"};
        HybridizationTableModel()
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
            if (hyJudge == null) return "";
            if (hyJudge.scores == null) return "";
            if (hyJudge.scores.length < 1) return "";
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
                    return new Integer(hyJudge.scores[row][0]).toString();
                case 4:
                    return new Integer(hyJudge.scores[row][1]).toString();
                case 5:
                    return new Integer(hyJudge.scores[row][2]).toString();
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
                    hyJudge.addHybridizationScore(row + 1, hyJudge.SP3,
                                           (new Double((String) aValue)).doubleValue());
                    break;
                case 4:
                    hyJudge.addHybridizationScore(row + 1, hyJudge.SP2,
                                           (new Double((String) aValue)).doubleValue());
                    break;
                case 5:
                    hyJudge.addHybridizationScore(row + 1, hyJudge.SP,
                                           (new Double((String) aValue)).doubleValue());
                    break;
            }
			fireTableDataChanged();
        }

    }
}














