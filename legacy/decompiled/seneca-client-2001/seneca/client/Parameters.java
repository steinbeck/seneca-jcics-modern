package seneca.client;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public abstract class Parameters {
   private boolean DEBUG = false;
   private String propertiesFilename;
   private String propertiesDescription;
   protected Properties properties = null;

   protected Parameters(String var1, String var2) {
      this.propertiesFilename = var1;
      this.propertiesDescription = var2;
   }

   protected abstract void setDefaults(Properties var1);

   protected abstract void updatePropertiesFromSettings();

   protected abstract void updateSettingsFromProperties();

   protected void getParameters() {
      Properties var1 = new Properties();
      FileInputStream var2 = null;
      this.setDefaults(var1);
      this.properties = new Properties(var1);

      try {
         String var3 = System.getProperty("user.home");
         String var4 = System.getProperty("file.separator");
         var2 = new FileInputStream(var3 + var4 + this.propertiesFilename);
         this.properties.load(var2);
      } catch (FileNotFoundException var16) {
         var2 = null;
         ErrorMessages.error("Can't find properties file. Using defaults.");
      } catch (IOException var17) {
         ErrorMessages.error("Can't read properties file. Using defaults.");
      } finally {
         if (var2 != null) {
            try {
               var2.close();
            } catch (IOException var15) {
            }

            Object var19 = null;
         }
      }

      this.updateSettingsFromProperties();
   }

   protected void saveParameters() {
      this.updatePropertiesFromSettings();
      if (this.DEBUG) {
         System.out.println("Just set properties: " + this.propertiesDescription);
         System.out.println(this.toString());
      }

      FileOutputStream var1 = null;

      try {
         String var2 = System.getProperty("user.home");
         String var3 = System.getProperty("file.separator");
         var1 = new FileOutputStream(var2 + var3 + this.propertiesFilename);
         this.properties.store(var1, this.propertiesDescription);
      } catch (IOException var13) {
         ErrorMessages.error("Can't save properties. Oh well, it's not a big deal.");
      } finally {
         if (var1 != null) {
            try {
               var1.close();
            } catch (IOException var12) {
            }

            Object var15 = null;
         }
      }
   }

   public Properties getProperties() {
      return this.properties;
   }

   public void setProperties(Properties var1) {
      this.properties = var1;
   }
}
