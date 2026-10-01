/* 
 * SenecaParameters.java    
 *
 * $RCSfile: SenecaParameters.java,v $    $Author: steinbeck $    $Date: 2001/04/10 14:43:16 $    $Revision: 1.2 $
 * 
 * Copyright (C) 1997-1999  The JChemPaint project
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

import java.util.Properties;

class SenecaParameters extends Parameters{

    private String lucyFileLocationName = "filelocation.lucy";
    private String senecaFileLocationName = "filelocation.seneca";	
	private String logFileName  = "filelocation.logfile";	
	private String resultFileName  = "filelocation.resultfile";	
	private String lucyFileLocation ="/"; 
	private String senecaFileLocation ="/"; 
	private String logFile  = "/senecalog";	
	private String resultFile  = "/senecaresults";	


	

    SenecaParameters() {
        super("seneca.props", "Seneca Properties");
        getParameters();
    }

    protected void setDefaults(Properties defaults) {
        defaults.put(lucyFileLocationName, lucyFileLocation);
        defaults.put(senecaFileLocationName, senecaFileLocation);
        defaults.put(logFileName, logFile);
        defaults.put(resultFileName, resultFile);		

    }

    protected void updateSettingsFromProperties() {
        try {
            lucyFileLocation = properties.getProperty(lucyFileLocationName);
            senecaFileLocation = properties.getProperty(senecaFileLocationName);
            logFile = properties.getProperty(logFileName);
            resultFile = properties.getProperty(resultFileName);
			
			
			
        } catch (NumberFormatException e) {
            // we don't care if the property was of the wrong format,
            // they've all got default values. So catch the exception
            // and keep going.
        }
    }

    protected void updatePropertiesFromSettings() {
        properties.put(lucyFileLocationName, lucyFileLocation);
        properties.put(senecaFileLocationName, senecaFileLocation);		
        properties.put(logFileName, logFile);
        properties.put(resultFileName, resultFile);		
		
    }

    public String toString() {
        return "["
               + "lucyFileLocation=" + lucyFileLocation + ","
               + "senecaFileLocation=" + senecaFileLocation + ","
               + "logFile=" + logFile + ","
               + "resultFile=" + resultFile + ","			   			   
			   + "]";
    }

    void setLucyFileLocation(String lfl) {
        this.lucyFileLocation = lfl;
        saveParameters();
    }

	public String getLucyFileLocation() {
        return lucyFileLocation;
    }

    public void setSenecaFileLocation(String sfl) {
        this.senecaFileLocation = sfl;
        saveParameters();
    }

    public String getSenecaFileLocation() {
        return senecaFileLocation;
    }
	
	public String getLogFile()
	{
		return this.logFile;
	}

	public void setLogFile(String logFile)
	{
		this.logFile = logFile;
	}

	public String getResultFile()
	{
		return this.resultFile;
	}

	public void setResultFile(String resultFile)
	{
		this.resultFile = resultFile;
	}


}
