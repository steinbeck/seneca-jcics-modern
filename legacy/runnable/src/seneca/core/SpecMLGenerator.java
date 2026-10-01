/* SpecMLGenerator.java
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
 
package seneca.core;
 
import compchem.*; 
import seneca.core.*;
import seneca.judges.*;
import seneca.structgen.*;
import seneca.gatekeeper.*;
import java.lang.reflect.*;
import java.util.*;

public class SpecMLGenerator
{
	private static String indent = "  ";
	private static int indentLevel  = 0;
	private static boolean verbose = false;

	public SpecMLGenerator ()
	{
	}

	public void setIndentString(String indent)
	{
		this.indent = indent;
	}

	public void setVerbose(boolean verbose)
	{
		this.verbose = verbose;
	}

	public static String getIndent()
	{
		return getIndent(indentLevel, indent);
	}

	public static String getIndent(int indentLevel)
	{
		return getIndent(indentLevel, indent);
	}


	public static String getIndent(int indentLevel, String indent)
	{
		StringBuffer result = new StringBuffer();
		for (int i = 1; i <= indentLevel; i ++)
		{
			result.append(indent);
		}
		return result.toString();
	}

	public String convert(SenecaDataset sd)
	{
		Class judgeConf = null;
		String className = null;
		String judgeClassName = null;
		String fullJudgeClassName = null;
		Class[] parameterTypes = new Class[3];
		Method method = null;
		Object[] parameters = new Object[3];
		
		StringBuffer cml = new StringBuffer();
		cml.append("<?xml version=\"1.0\" ?>\n");
		cml.append("<senecadataset title=\"" + sd.getName() + "\">\n");
		indentLevel++;
		cml.append(getIndent() + "<formula>" + sd.getMolecularFormula() + "</formula>\n");

		// insert connectiontable
		cml.append(convert(sd.getSetOfNodes()));

		// Start streaming the spectra

		cml.append(convert(sd.carbon1D));	  		
		cml.append(convert(sd.dept90));	  		
		cml.append(convert(sd.dept135));	  		
		cml.append(convert(sd.hetcor));	  		
		cml.append(convert(sd.hetcorlr));	  		
		cml.append(convert(sd.hhcosy));	  		
		cml.append(convert(sd.noesy));
		
		for (int f = 0; f < sd.judges.size(); f++)
		{
//			fullJudgeClassName = sd.judges.elementAt(f).getClass().getName();
//			judgeClassName = fullJudgeClassName.substring(fullJudgeClassName.lastIndexOf('.') + 1);
//			className = "seneca.client." + judgeClassName + "Configurator";
//			try
//			{
//				judgeConf = Class.forName(className);
//				parameterTypes[0] = Judge.class;
//				parameterTypes[1] = Integer.class;
//				parameterTypes[2] = String.class;
//			}
//			catch(Exception exc)
//			{
//				System.err.println("No Configurator " + className + " found for judge " + judgeClassName + "\n");
//				exc.printStackTrace();
//			}
//			try
//			{
//				method = judgeConf.getMethod("getXML", parameterTypes);
//				parameters[0] = (Judge)sd.judges.elementAt(f);
//				parameters[1] = new Integer(indentLevel);
//				parameters[2] = indent;
//				cml.append((String)method.invoke(null, parameters));
//			}
//			catch(Exception exc)
//			{
//				System.err.println("No getXML method found for judgeConfigurator " + className + "\n");
//				exc.printStackTrace();
//			}


		}
		for (int i = 0; i < sd.annealingSchedules.size(); i++)
		{
			cml.append(convert((AnnealingSchedule)sd.annealingSchedules.elementAt(i)));	
		}
		
		for (int i = 0; i < sd.serverList.size(); i++)
		{
			cml.append(convert((ServerInfo)sd.serverList.elementAt(i), indentLevel));	
		}
		
		
		indentLevel--;
		cml.append("</senecadataset>\n");
		return cml.toString();
	}

	public String escape(String s)
	{
		StringBuffer result = new StringBuffer();
		for (int i = 0; i < s.length(); i++)
		{
			char c = s.charAt(i);
			if (c == '&')
			{
				result.append("&amp;");
			}
			else if ( c == '<')
			{
				result.append("&lt;");
			}
			else
			{
				result.append(c);
			}
		}
		return result.toString();
	}

	public String convert(Atom[] son)
	{
		StringBuffer cml = new StringBuffer();
		Vector printedAtoms = new Vector();
		cml.append(getIndent() + "<molecule xmlns=\"http://www.xml-cml.org/cml.dtd\" title=\"ConnectionTable\">\n");
		indentLevel++;
		for (int i = 0; i < son.length; i++)
		{
			cml.append(getIndent() + "<atom id=\"a" + son[i].number + "\">\n");
			indentLevel++;
			cml.append(getIndent() + "<string builtin=\"elementType\">" + 
				son[i].symbol + "</string>\n");
			if (son[i].HCount >= 0)
			{
				cml.append(getIndent() + "<integer builtin=\"hydrogenCount\">" +
					son[i].HCount + "</integer>\n");
			}
			if (son[i].getPhysicalProperty(Atom.PHYS_PROP_13C_SHIFT) != null)
			{
				cml.append(getIndent() + "<float title=\"assignedCarbonShift\">" +
					son[i].getPhysicalProperty(Atom.PHYS_PROP_13C_SHIFT) + "</float>\n");
			}
			indentLevel--;
			cml.append(getIndent() + "</atom>\n");
		}
		int bondId = 1;
		for (int i = 0; i < son.length; i++)
		{
			printedAtoms.addElement(son[i]);
			for (int j = 0; j < son[i].degree; j++)
			{
				if (!printedAtoms.contains(son[i].nodeTable[j]))
				{
					bondId++;
					cml.append(getIndent() + "<bond id=\"b" + bondId + 
						"\" atomRefs=\"a" + son[i].number + " a" +
						son[i].nodeTable[j].number + "\">\n");
					indentLevel++;
					cml.append(getIndent() + "<string builtin=\"order\">" +
						son[i].bondTable[j] + "</string>\n");
						
					indentLevel--;
					cml.append(getIndent() + "</bond>\n");
				}	
			}
		}
		indentLevel--;
		cml.append(getIndent() + "</molecule>\n");
		return cml.toString();
	}


	public String convert(NMRSpectrum nmrspect)
	{
		if (nmrspect == null) return null;
		StringBuffer cml = new StringBuffer();
		StringBuffer nuclei = new StringBuffer();
		StringBuffer properties = new StringBuffer();
		String temp = null;
		cml.append(getIndent() + "<spectrum xmlns=\"http://www.nmrshiftdb.org/\" type=\"NMR Experiment\" " +
			"id=\"" + nmrspect.name + "\" convention=\"Seneca\">\n");
			
		if (verbose) System.out.println("Converting " + nmrspect.specType);
		indentLevel++;
		cml.append(getIndent() + "<spectruminfo>\n"); // <spectruminfo>
		indentLevel++;
		cml.append(getIndent() + "<dimension>" +  nmrspect.dim + "</dimension>\n");		
		cml.append(getIndent() + "<type>" +  nmrspect.specType + "</type>\n");
		cml.append(getIndent() + "<frequency unit=\"MHz\">" + nmrspect.getSpectrometerFrequency() + "</frequency>\n");
		cml.append(getIndent() + "<solvent>" + nmrspect.getSolvent() + "</solvent>\n");
		cml.append(getIndent() + "<standard role=\"calibration\">" + nmrspect.getStandard() + "</standard>\n");

		if (nmrspect.size() == 0)
		{
			indentLevel--;	
			cml.append(getIndent() + "</spectruminfo>\n"); // </spectruminfo>	
			indentLevel--;	
			cml.append(getIndent() + "</spectrum>\n"); // </spectrum>					
		    return cml.toString();
		}
		for (int i = 0; i < ((NMRSignal)nmrspect.getSignal(0)).nucleus.length; i++)
		{
			temp = "f" + (i + 1);
			cml.append(getIndent() + "<axisinfo role=\"" + temp + "\">\n"); // <axisinfo>
			indentLevel++;

			cml.append(getIndent() + "<nucleus>" + ((NMRSignal)nmrspect.getSignal(0)).nucleus[i] + "</nucleus>\n");
			cml.append(getIndent() + "<property>shift</property>\n");						
			cml.append(getIndent() + "<unit>ppm</unit>\n");
			
			indentLevel--;
			cml.append(getIndent() + "</axisinfo>\n"); // </axisinfo>
		}

		indentLevel--;
    	cml.append(getIndent() + "</spectruminfo>\n"); // </spectruminfo>


		if (verbose) System.out.println(".");

		if (nmrspect.size() > 0)
		{
			for (int i = 0; i < nmrspect.size(); i++)
			{
				cml.append(getIndent() + "<signal" + " id=\"" + nmrspect.name + ".p" + i +  "\">\n");
				indentLevel ++;
				for (int j = 0; j < ((NMRSignal)nmrspect.getSignal(i)).shift.length; j++)
				{
					temp = "f" + (j + 1);
					cml.append(getIndent() + "<location role=\"" + temp + "\">" + ((NMRSignal)nmrspect.getSignal(i)).shift[j] + "</location>\n");
				}
				cml.append(getIndent() + "<intensity type=\"relative\">" + 
					((NMRSignal)nmrspect.getSignal(i)).intensity + "</intensity>\n");
				cml.append(getIndent() + "<normalizefactor>" + 
					((NMRSignal)nmrspect.getSignal(i)).normalizeFactor + "</normalizefactor>\n");
					
				cml.append(getIndent() + "<phase>" + 
					((NMRSignal)nmrspect.getSignal(i)).phase + "</phase>\n");
				indentLevel --;
				cml.append(getIndent() + "</signal>\n");
			}
		}
		indentLevel--;
		cml.append(getIndent() + "</spectrum>\n");
		return cml.toString();

	}

	public String convert(AnnealingSchedule annSched)
	{
		if (annSched == null) return null;
		StringBuffer cml = new StringBuffer();
		String temp = null;
		cml.append(getIndent() + "<annealingschedule>\n");
		if (verbose) System.out.println("Converting " + annSched);
		indentLevel++;
		cml.append(getIndent() + "<initialAcceptanceProbability>" +  annSched.initialAcceptanceProbability + "</initialAcceptanceProbability>\n");		
		cml.append(getIndent() + "<maxUphillSteps>" + annSched.maxUphillSteps  + "</maxUphillSteps>\n");
		cml.append(getIndent() + "<plateauSteps>" + annSched.plateauSteps  + "</plateauSteps>\n");
		cml.append(getIndent() + "<asymptoticCoolingFactor>" + annSched.asymptoticCoolingFactor  + "</asymptoticCoolingFactor>\n");
	   indentLevel--;
	   cml.append(getIndent() + "</annealingschedule>\n");
	   return cml.toString();
	}


	public static String convert(ServerInfo serverInfo, int indentLevel)
	{
		if (serverInfo == null) return null;
		StringBuffer xml = new StringBuffer();
		SpecMLGenerator sg = new SpecMLGenerator();
		String temp = null;
		xml.append(sg.getIndent() + "<serverinfo>\n");
		if (verbose) System.out.println("Converting " + serverInfo);
		indentLevel++;
		xml.append(sg.getIndent() + "<hostname>" +  serverInfo.getHostname() + "</hostname>\n");		
		xml.append(sg.getIndent() + "<responseTime>" + serverInfo.getResponseTime()  + "</responseTime>\n");
		xml.append(sg.getIndent() + "<speed>" + serverInfo.getSpeed()  + "</speed>\n");
		indentLevel--;
		xml.append(sg.getIndent() + "</serverinfo>\n");
		return xml.toString();
	}
}
