package compchem;

import java.io.Serializable;

public class Path implements Serializable {
   public boolean delete = false;
   public boolean isNew = false;
   public boolean debug = false;
   public boolean[] isMember;
   public int[] members;
   private int capacity = 40;
   private int load = 0;

   public Path(int var1) {
      this.capacity = var1;
      this.isMember = new boolean[var1];
      this.members = new int[var1];
   }

   public Path(int var1, int var2, int var3) {
      this(var3);
      this.add(var1);
      this.add(var2);
   }

   public void add(int var1) {
      if (this.debug) {
         System.out.println("Adding " + var1 + " to path.");
      }

      this.members[this.load] = var1;
      this.isMember[var1] = true;
      this.load++;
   }

   public void remove(int var1) {
      this.isMember[this.members[var1]] = false;

      for (int var2 = var1; var2 < this.load - 1; var2++) {
         this.members[var2] = this.members[var2 + 1];
      }

      this.load--;
   }

   public void removeLast() {
      this.remove(this.load - 1);
   }

   public void removeFirst() {
      this.remove(0);
   }

   public int get(int var1) {
      return this.members[var1];
   }

   public int getLast() {
      return this.members[this.load - 1];
   }

   public int getFirst() {
      return this.members[0];
   }

   public boolean isInPath(int var1) {
      return this.isMember[var1];
   }

   public void reverse() {
      int var2 = this.load / 2;

      for (int var3 = 0; var3 < var2; var3++) {
         int var1 = this.members[var3];
         this.members[var3] = this.members[this.load - var3 - 1];
         this.members[this.load - var3 - 1] = var1;
      }
   }

   public void merge(Path var1) {
      if (this.load + var1.load > this.members.length) {
         this.grow(this.load + var1.load - this.members.length);
      }

      System.arraycopy(var1.members, 0, this.members, this.load, var1.load);
      if (this.debug) {
         System.out.println("Path.merge() -> Mergin " + this.toString() + " with " + var1.toString());
      }

      for (int var2 = 0; var2 < this.load + var1.load; var2++) {
         this.isMember[this.members[var2]] = true;
      }

      if (this.debug) {
         System.out.println("Path.merge() -> " + this.isMemberToString());
      }

      this.load = this.load + var1.load;
   }

   public int getIntersectionSize(Path var1) {
      int var2 = 0;

      for (int var3 = 0; var3 < this.isMember.length; var3++) {
         if (this.isMember[var3] && var1.isMember[var3]) {
            var2++;
         }
      }

      return var2;
   }

   public void grow(int var1) {
      int var2 = this.capacity + var1;
      int[] var3 = new int[var2];
      boolean[] var4 = new boolean[var2];
      System.arraycopy(this.members, 0, var3, 0, this.capacity);
      System.arraycopy(this.isMember, 0, var4, 0, this.capacity);
      this.capacity = var2;
      this.members = var3;
      this.isMember = var4;
   }

   public int size() {
      return this.load;
   }

   public Object clone() {
      Path var1 = new Path(this.capacity);
      System.arraycopy(this.members, 0, var1.members, 0, this.capacity);
      System.arraycopy(this.members, 0, var1.members, 0, this.load);
      var1.isNew = this.isNew;
      var1.delete = this.delete;
      var1.load = this.load;
      return var1;
   }

   public static Path join(Path var0, Path var1, int var2) {
      Path var3 = null;
      if (var2 == var0.getFirst()) {
         if (var2 == var1.getLast()) {
            var3 = (Path)var1.clone();
            var3.removeLast();
            var3.merge(var0);
            return var3;
         }

         if (var2 == var1.getFirst()) {
            var3 = (Path)var1.clone();
            var3.removeFirst();
            var3.reverse();
            var3.merge(var0);
            return var3;
         }
      } else if (var2 == var0.getLast()) {
         if (var2 == var1.getLast()) {
            var3 = (Path)var0.clone();
            var1.reverse();
            var3.removeLast();
            var3.merge(var1);
            return var3;
         }

         if (var2 == var1.getFirst()) {
            var3 = (Path)var0.clone();
            var3.removeLast();
            var3.merge(var1);
            return var3;
         }
      }

      return null;
   }

   public String toString() {
      if (this.size() == 0) {
         return "";
      } else {
         String var1 = "" + this.get(0);

         for (int var2 = 1; var2 < this.size(); var2++) {
            var1 = var1 + "-" + this.get(var2);
         }

         return var1;
      }
   }

   public String isMemberToString() {
      if (this.size() == 0) {
         return "";
      } else {
         String var1 = "isMember array: [";

         for (int var2 = 0; var2 < this.isMember.length; var2++) {
            if (this.isMember[var2]) {
               var1 = var1 + "1";
            } else {
               var1 = var1 + "0";
            }
         }

         return var1 + "]";
      }
   }
}
