/* CMLGenerator.java
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
import java.util.*;

public class CMLGenerator
{
	private String indent;
	private int indentLevel;
	private boolean verbose = false;

	public CMLGenerator ()
	{
		this.indent = "  ";
		this.indentLevel = 0;
	}

	public void setIndentString(String indent)
	{
		this.indent = indent;
	}

	public void setVerbose(boolean verbose)
	{
		this.verbose = verbose;
	}

	private String getIndent()
	{
		StringBuffer result = new StringBuffer();
		for (int i = 1; i <= this.indentLevel; i ++)
		{
			result.append(this.indent);
		}
		return result.toString();
	}

	public String convert(SenecaDataset sd)
	{
		StringBuffer cml = new StringBuffer();
		cml.append("<?xml version=\"1.0\" ?>\n");
		cml.append("<!DOCTYPE molecule SYSTEM \"cml.dtd\" []>\n");
		cml.append("<molecule convention=\"SenecaDocument\">\n");
		indentLevel++;
		cml.append(getIndent() + "<string title=\"DocumentName\">" + sd.getName() + "</string>\n");
		cml.append(getIndent() + "<string title=\"DocumentDescription\">\n");
		indentLevel++;
		cml.append(getIndent() + escape(sd.getDescription()) + "\n");
		indentLevel--;
		cml.append(getIndent() + "</string>\n");
		cml.append(getIndent() + "<formula>" + sd.getMolecularFormula() + "</formula>\n");
		cml.append(getIndent() + "<float title=\"Mass\">" + sd.getMolecularMass() + "</float>\n");

		// insert connectiontable
		cml.append(convert(sd.getSetOfNodes()));

		// Start streaming the spectra

		cml.append(convert(sd.carbon1D));	  		
		cml.append(convert(sd.hetcor));	  		
		cml.append(convert(sd.hetcorlr));	  		
		cml.append(convert(sd.hhcosy));	  		
		cml.append(convert(sd.noesy));	  		

		indentLevel--;
		cml.append("</molecule>\n");
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
		Vector printedAtoms = new Vector(); // We store the handled atoms here.
		cml.append(getIndent() + "<molecule title=\"ConnectionTable\">\n");
		indentLevel++;
		for (int i = 0; i < son.length; i++)
		{
			cml.append(getIndent() + "<atom id=\"a" + son[i].number + "\">\n");
			indentLevel++;
			cml.append(getIndent() + "<string builtin=\"elementType\">" + 
				son[i].symbol + "</string>\n");
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
		StringBuffer cml = new StringBuffer();
		StringBuffer nuclei = new StringBuffer();
		StringBuffer properties = new StringBuffer();

		cml.append(getIndent() + "<molecule title=\"NMR Experiment\" " +
			"id=\"" + nmrspect.name + "\" convention=\"Jena2000NMR\">\n");
		if (verbose) System.out.println("Converting " + nmrspect.specType);
		indentLevel++;
		cml.append(getIndent() + "<string title=\"ExperimentType\">" + 
			nmrspect.specType + "</string>\n");
		if (verbose) System.out.println(".");
		cml.append(getIndent() + "<float title=\"Frequency\">" + 
			nmrspect.getSpectrometerFrequency() + "</string>\n");
		/* this has to be modified to handle an array
				
				cml.append(getIndent() + "<float title=\"PickPrecision\">" + 
					    	   nmrspect.getPickPrecision() + "</string>\n");
		*/

		cml.append(getIndent() + "<string title=\"Solvent\">" + 
			nmrspect.getSolvent() + "</string>\n");
		if (verbose) System.out.println(".");
		cml.append(getIndent() + "<string title=\"Standard\">" + 
			nmrspect.getStandard() + "</string>\n");
		if (verbose) System.out.println(".");

		if (nmrspect.size() > 0)
		{
			for (int i = 0; i < ((NMRSignal)nmrspect.getSignal(0)).nucleus.length; i++)
			{
				nuclei.append(((NMRSignal)nmrspect.getSignal(0)).nucleus[i] + " ");
				properties.append("Shift ");		
			}
			if (verbose) System.out.println(".");
			cml.append(getIndent() + "<stringArray title=\"Nucleus\">" + 
				nuclei.toString() + "</string>\n");
			if (verbose) System.out.println(".");
			cml.append(getIndent() + "<stringArray title=\"Properties\">" + 
				properties.toString() + "</string>\n");
			if (verbose) System.out.println(".");
			cml.append(getIndent() + "<list title=\"Signals\">\n");
			if (verbose) System.out.println(".");   		
			indentLevel ++;
			for (int i = 0; i < nmrspect.size(); i++)
			{
				cml.append(getIndent() + "<list title=\"Signal\" " +
					" id=\"" + nmrspect.name + ".p" + i +  "\">\n");
				indentLevel ++;
				cml.append(getIndent() + "<floatArray title=\"Value\">");

				for (int j = 0; j < ((NMRSignal)nmrspect.getSignal(i)).shift.length; j++)
				{
					cml.append(((NMRSignal)nmrspect.getSignal(i)).shift[j] + " ");
				}
				cml.append(getIndent() + "</floatArray>\n");
				cml.append(getIndent() + "<float title=\"Intensity\">" + 
					((NMRSignal)nmrspect.getSignal(i)).intensity + "</float>\n");
				cml.append(getIndent() + "<float title=\"Phase\">" + 
					((NMRSignal)nmrspect.getSignal(i)).phase + "</float>\n");
				indentLevel --;
				cml.append(getIndent() + "</list>\n");
			}

			indentLevel --;
			cml.append(getIndent() + "</list>\n");
			indentLevel--;
		}
		cml.append(getIndent() + "</molecule>\n");
		return cml.toString();

	}
}
