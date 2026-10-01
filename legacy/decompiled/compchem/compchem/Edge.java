package compchem;

public class Edge {
   public int from;
   public int to;

   public Edge() {
   }

   public Edge(int var1, int var2) {
      this.from = var1;
      this.to = var2;
   }

   public String toString() {
      return new String("Edge: " + this.from + " - " + this.to);
   }
}
