/* JudgeConfigurator.java
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

import org.xml.sax.*;
import org.xml.sax.helpers.ParserFactory;
import com.sun.xml.parser.Resolver;

import javax.xml.parsers.DocumentBuilderFactory;  
import javax.xml.parsers.FactoryConfigurationError;  
import javax.xml.parsers.ParserConfigurationException;
 
import javax.xml.parsers.DocumentBuilder;  

import org.w3c.dom.Document;
import org.w3c.dom.DOMException;



/**
 * This class provides a gui for configuring a  Judges
 */
public abstract class JudgeConfigurator extends JPanel implements JudgeListener
{
	protected SenecaDataset sd;	
	protected Judge judge;
	protected JCheckBox enabled;
	protected JTable table;
	protected JScrollPane scrollpane;

	
	JudgeConfigurator()
	{
	
	}

	JudgeConfigurator(SenecaDataset sd, Judge judge)
	{
		super();
		this.sd = sd;
		this.judge = judge; 
		System.out.println(this + " - " + judge);
		judge.setJudgeListener(this);
		setLayout(new BorderLayout());
		add("Center", contructCenterBox());
		add("South", contructSouthBox());
	}


	abstract protected Box contructCenterBox();

	protected Border getTitledBorder()
	{
		Border etchedBorder = BorderFactory.createEtchedBorder();
		return BorderFactory.createTitledBorder(etchedBorder, judge.name);
	}

	protected Box contructSouthBox()
	{

		Box southBox = new Box(BoxLayout.X_AXIS);
		JButton generateButton = new JButton("Autoconfigure Judge");
		generateButton.setToolTipText("Autoconfigures " + judge.name);
		generateButton.addActionListener(new AutoAssignAction());
		JButton reportButton = new JButton("Report Judgeconfig");
		reportButton.setToolTipText("Opens a window with judge configuration in ASCII output");
		reportButton.addActionListener(new ReportAction());
		southBox.add(generateButton);
		southBox.add(reportButton);
		southBox.add(Box.createHorizontalGlue());		
		enabled = new JCheckBox("Activate this Judge?", judge.getEnabled());
		enabled.addActionListener(new EnableAction());
		southBox.add(enabled);		
		return southBox;

	}
	
	protected void autoconfigure()
	{
		System.out.println("dummy");
	}


	protected void report()
	{
		System.out.println("dummy");
	}


	public static String getXML(Judge judge, Integer indentLevel, String indentString)
	{
		int iL = indentLevel.intValue();
		String iS = indentString;
		StringBuffer xml = new StringBuffer();
		xml.append(SpecMLGenerator.getIndent(iL, iS) + "<judge title=\"" + judge.name + "\">\n");
		xml.append(SpecMLGenerator.getIndent(iL, iS) + "</judge>\n");
		return xml.toString();
	}


	public static Judge getJudgeFromXML()
	{
		return null;
	}

	protected void reactOnJudgeDataChange()
	{
	
	}

	public void judgeDataChanged()
	{
		System.out.println("JudgeDataChanged: " + judge.getEnabled());
		enabled.setSelected(judge.getEnabled());
		reactOnJudgeDataChange();
	
	}

	class AutoAssignAction extends AbstractAction
	{
		/**
	     *
	     **/
		AutoAssignAction()
		{
			super("Autoconfigure");
		}

		/**
	     *
	     **/
		public void actionPerformed(ActionEvent e)
		{
			judge.setEnabled(true);
			autoconfigure();
		}
	}

	class ReportAction extends AbstractAction
	{
		/**
	     *
	     **/
		ReportAction()
		{
			super("report");
		}

		/**
	     *
	     **/
		public void actionPerformed(ActionEvent e)
		{
			report();
		}
	}


	class EnableAction extends AbstractAction
	{
		/**
	     *
	     **/
		EnableAction()
		{
			super("Enable");
		}

		/**
	     *
	     **/
		public void actionPerformed(ActionEvent e)
		{
			JCheckBox jcb = (JCheckBox)e.getSource();
			judge.setEnabled(jcb.isSelected());
		}
	}


}


