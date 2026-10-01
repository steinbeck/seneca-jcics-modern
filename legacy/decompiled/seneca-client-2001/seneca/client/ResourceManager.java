package seneca.client;

import java.net.URL;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public class ResourceManager {
   private ResourceBundle resources;

   public ResourceManager(ResourceBundle var1) {
      this.resources = var1;
   }

   protected String getResourceString(String var1) {
      String var2;
      try {
         var2 = this.resources.getString(var1);
      } catch (MissingResourceException var4) {
         var2 = null;
      }

      return var2;
   }

   protected URL getResource(String var1) {
      String var2 = this.getResourceString(var1);
      return var2 != null ? this.getClass().getResource(var2) : null;
   }
}
