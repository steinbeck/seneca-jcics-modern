/* JMDrawPanel.java
 *
 * $RCSfile: JMDrawPanel.java,v $    $Author: steinbeck $    $Date: 2001/04/23 08:17:56 $    $Revision: 1.2 $
 *
 * Copyright (C) 1997, 1998, 1999, 2000  The JChemPaint project
 * 
 * Contact: steinbeck@ice.mpg.de
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * All I ask is that proper credit is given for my work, which includes
 * - but is not limited to - adding the above copyright notice to the beginning
 * of your source code files, and to any copyright notice that you may distribute
 * with programs based on this work.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA. 
 * 
 */
 
package seneca.client;

import java.awt.*;
import javax.swing.*;
import jmdraw.*;
import compchem.*;

public class JMDrawPanel extends JPanel  
{
	JMDraw jmd = null;
	
	
	public JMDrawPanel()
	{
		this(new JMDraw());
	}
	
	
	public JMDrawPanel(JMDraw jmd)
	{
		super();
        ToolTipManager toolTipManager = ToolTipManager.sharedInstance();
		toolTipManager.setDismissDelay(100000);
		this.jmd = jmd;
	}

	public void paint(Graphics g)
	{
		super.paint(g);
		if (jmd != null && jmd.atomSet != null && jmd.atomSet.length > 0)
		{
//			jmd.setSize(getSize());
			setBackground(((RenderingAttributes)jmd.getRenderingAttributes()).getBackColor());
//			jmd.paint(g);
			Molecule molecule = new Molecule(jmd.getAtomSet());
			RenderingEngine strdrw = new RenderingEngine();
			RenderingEngine.scaleIntoArea(molecule, getSize(), 0.8);
			RenderingEngine.translateAllPositive(molecule);
			RenderingEngine.center(molecule, getSize());
					
			strdrw.drawMolecule(g, molecule, jmd.getRenderingAttributes());


		}
			
	}
	
	public JMDraw getJMDraw()
	{
		return jmd;
	}

	public void setJMDraw(JMDraw jmd)
	{
		this.jmd = jmd;
	}
}
