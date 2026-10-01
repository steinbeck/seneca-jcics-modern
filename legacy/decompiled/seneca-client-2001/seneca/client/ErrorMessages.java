package seneca.client;

public class ErrorMessages {
   public static boolean DEBUG = false;

   public static void error(String var0) {
      System.err.print("Seneca: ");
      System.err.println(var0);
   }

   public static void error(String var0, Exception var1) {
      if (DEBUG && var1 != null) {
         var1.printStackTrace();
      }

      System.err.print("Seneca: ");
      System.err.println(var0);
   }

   public static void fatalError(String var0, Exception var1) {
      if (DEBUG && var1 != null) {
         var1.printStackTrace();
      }

      System.err.print("Seneca: ");
      System.err.println(var0);
      System.err.println("Exiting.....");
      System.exit(-1);
   }
}
