package seneca.structgen;

public interface AnnealingScheduleInterface {
   boolean cool();

   void init();

   boolean isAccepted(double var1);

   String toString();
}
