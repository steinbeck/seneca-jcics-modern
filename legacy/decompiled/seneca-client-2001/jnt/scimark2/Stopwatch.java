package jnt.scimark2;

public class Stopwatch {
   private boolean running;
   private double last_time;
   private double total;

   public static final double seconds() {
      return System.currentTimeMillis() * 0.001;
   }

   public void reset() {
      this.running = false;
      this.last_time = 0.0;
      this.total = 0.0;
   }

   public Stopwatch() {
      this.reset();
   }

   public void start() {
      if (!this.running) {
         this.running = true;
         this.total = 0.0;
         this.last_time = seconds();
      }
   }

   public void resume() {
      if (!this.running) {
         this.last_time = seconds();
         this.running = true;
      }
   }

   public double stop() {
      if (this.running) {
         this.total = this.total + (seconds() - this.last_time);
         this.running = false;
      }

      return this.total;
   }

   public double read() {
      if (this.running) {
         this.total = this.total + (seconds() - this.last_time);
         this.last_time = seconds();
      }

      return this.total;
   }
}
