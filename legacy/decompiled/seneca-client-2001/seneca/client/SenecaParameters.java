package seneca.client;

import java.util.Properties;

class SenecaParameters extends Parameters {
   private String lucyFileLocationName = "filelocation.lucy";
   private String senecaFileLocationName = "filelocation.seneca";
   private String logFileName = "filelocation.logfile";
   private String resultFileName = "filelocation.resultfile";
   private String lucyFileLocation = "/";
   private String senecaFileLocation = "/";
   private String logFile = "/senecalog";
   private String resultFile = "/senecaresults";

   SenecaParameters() {
      super("seneca.props", "Seneca Properties");
      this.getParameters();
   }

   protected void setDefaults(Properties var1) {
      var1.put(this.lucyFileLocationName, this.lucyFileLocation);
      var1.put(this.senecaFileLocationName, this.senecaFileLocation);
      var1.put(this.logFileName, this.logFile);
      var1.put(this.resultFileName, this.resultFile);
   }

   protected void updateSettingsFromProperties() {
      try {
         this.lucyFileLocation = super.properties.getProperty(this.lucyFileLocationName);
         this.senecaFileLocation = super.properties.getProperty(this.senecaFileLocationName);
         this.logFile = super.properties.getProperty(this.logFileName);
         this.resultFile = super.properties.getProperty(this.resultFileName);
      } catch (NumberFormatException var2) {
      }
   }

   protected void updatePropertiesFromSettings() {
      super.properties.put(this.lucyFileLocationName, this.lucyFileLocation);
      super.properties.put(this.senecaFileLocationName, this.senecaFileLocation);
      super.properties.put(this.logFileName, this.logFile);
      super.properties.put(this.resultFileName, this.resultFile);
   }

   public String toString() {
      return "[lucyFileLocation="
         + this.lucyFileLocation
         + ","
         + "senecaFileLocation="
         + this.senecaFileLocation
         + ","
         + "logFile="
         + this.logFile
         + ","
         + "resultFile="
         + this.resultFile
         + ","
         + "]";
   }

   void setLucyFileLocation(String var1) {
      this.lucyFileLocation = var1;
      this.saveParameters();
   }

   public String getLucyFileLocation() {
      return this.lucyFileLocation;
   }

   public void setSenecaFileLocation(String var1) {
      this.senecaFileLocation = var1;
      this.saveParameters();
   }

   public String getSenecaFileLocation() {
      return this.senecaFileLocation;
   }

   public String getLogFile() {
      return this.logFile;
   }

   public void setLogFile(String var1) {
      this.logFile = var1;
   }

   public String getResultFile() {
      return this.resultFile;
   }

   public void setResultFile(String var1) {
      this.resultFile = var1;
   }
}
