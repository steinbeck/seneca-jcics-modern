package compchem;

public class DPoint {
   public double x;
   public double y;

   public DPoint() {
   }

   public DPoint(double var1, double var3) {
      this.x = var1;
      this.y = var3;
   }

   public DPoint(int var1, int var2) {
      this.x = var1;
      this.y = var2;
   }

   public DPoint(DPoint var1) {
      this.x = var1.x;
      this.y = var1.y;
   }
}
