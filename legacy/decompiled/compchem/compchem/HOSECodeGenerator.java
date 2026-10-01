package compchem;

import java.io.Serializable;
import java.util.Vector;

public class HOSECodeGenerator implements Serializable {
   boolean debug = false;
   static String[] rankedSymbols = new String[]{"C", "O", "N", "S", "P", "Si", "B", "F", "Cl", "Br", ";", "I", "#", "&", ","};
   static int[] symbolRankings = new int[]{9000, 8900, 8800, 8700, 8600, 8500, 8400, 8300, 8200, 8100, 8000, 7900, 1200, 1100, 1000};
   static int[] bondRankings = new int[]{0, 0, 200000, 300000, 100000};
   protected int[] ranking;
   protected boolean[] visited;
   protected Vector sphereNodes = null;
   protected Vector nextSphereNodes = null;
   protected setOfRings sof = null;
   protected Atom[] setOfNodes = null;
   protected int[] ringScores;
   protected int sphere = 0;
   protected int maxSphere = 0;
   protected StringBuffer HOSECode = null;
   protected String[] sphereDelimiters = new String[]{"(", "/", "/", ")", "/", "/", "/", "/", "/", "/", "/", "/"};
   protected String[] bondSymbols = new String[]{"", "", "=", "%", "*"};

   public HOSECodeGenerator() {
      this.sphereNodes = new Vector();
      this.nextSphereNodes = new Vector();
      this.HOSECode = new StringBuffer();
   }

   public String getHOSECode(Molecule var1, Atom var2, int var3) {
      return this.getHOSECode(var1.getAtoms(), var2, var3);
   }

   public String getHOSECode(Atom[] var1, Atom var2, int var3) {
      this.setOfNodes = var1;
      this.maxSphere = var3;
      this.ranking = new int[var2.degree];
      this.visited = new boolean[var1.length];

      for (int var4 = 0; var4 < this.visited.length; var4++) {
         this.visited[var4] = false;
      }

      this.visited[var2.number] = true;
      this.HOSECode = new StringBuffer();
      this.breadthFirstSearch(var2);
      this.fillUpSphereDelimiters();

      while (this.HOSECode.charAt(0) == 'H') {
         this.HOSECode = this.HOSECode.deleteCharAt(0);
      }

      if (this.debug) {
         System.out.println("HOSECodeGenerator -> HOSECode: " + this.HOSECode.toString());
      }

      return this.HOSECode.toString();
   }

   private void breadthFirstSearch(Atom var1) {
      this.sphere = 0;
      this.sphereNodes.removeAllElements();

      for (int var3 = 0; var3 < var1.degree; var3++) {
         Atom var2 = var1.nodeTable[var3];
         this.sphereNodes.addElement(new HOSECodeGenerator.TreeNode(var1.nodeTable[var3].symbol, var1, var2, var1.bondTable[var3], 0L));
         this.visited[var2.number] = true;
      }

      this.nextSphere(this.sphereNodes);
   }

   private void nextSphere(Vector var1) {
      this.sphere++;
      if (this.debug) {
         System.out.println("nextSphere() -> sphere: " + this.sphere);
      }

      if (this.debug) {
         System.out.println("nextSphere() -> maxSphere: " + this.maxSphere);
      }

      if (this.sphere <= this.maxSphere) {
         String var2 = this.getSphereCode(var1);
         if (this.debug) {
            System.out.println("nextSphere() -> getSphereCode(): " + var2);
         }

         this.HOSECode.append(var2);
         if (this.debug) {
            System.out.println("nextSphere() -> printing the HOSECode StringBuffer: " + this.HOSECode.toString());
         }

         Atom var3 = null;
         Atom var4 = null;
         HOSECodeGenerator.TreeNode var5 = null;
         this.nextSphereNodes = new Vector();

         for (int var6 = 0; var6 < var1.size(); var6++) {
            var5 = (HOSECodeGenerator.TreeNode)var1.elementAt(var6);
            if ("&;#:,".indexOf(var5.symbol) < 0) {
               var3 = var5.number;
               if (var3.degree == 1) {
                  this.nextSphereNodes.addElement(new HOSECodeGenerator.TreeNode(",", var5.number, null, 0, var5.score * 1000000L));
               } else {
                  for (int var7 = 0; var7 < var3.degree; var7++) {
                     if (this.debug) {
                        System.out.println("HOSECodeGenerator->nextSphere()->node.number:" + var3.number);
                     }

                     var4 = var3.nodeTable[var7];
                     if (this.debug) {
                        System.out.println("HOSECodeGenerator->nextSphere()->toNode.number:" + var4.number);
                     }

                     if (!this.visited[var4.number]) {
                        this.nextSphereNodes
                           .addElement(
                              new HOSECodeGenerator.TreeNode(
                                 this.setOfNodes[var4.number].symbol, var5.number, var4, var3.bondTable[var7], var5.score * 1000000L
                              )
                           );
                        this.visited[var4.number] = true;
                     } else if (!var4.equals(var5.source)) {
                        this.nextSphereNodes.addElement(new HOSECodeGenerator.TreeNode("&", var5.number, var4, var3.bondTable[var7], var5.score * 1000000L));
                     }
                  }
               }
            }
         }

         this.nextSphere(this.nextSphereNodes);
      }
   }

   private String getSphereCode(Vector var1) {
      if (var1 != null && var1.size() >= 1) {
         boolean[] var2 = new boolean[var1.size()];
         Object var3 = null;
         HOSECodeGenerator.TreeNode var4 = null;
         StringBuffer var5 = new StringBuffer();
         this.calculateNodeScores(var1);
         this.sortNodes(var1);
         Atom var6 = ((HOSECodeGenerator.TreeNode)var1.elementAt(0)).source;
         int var8 = 0;

         Atom var7;
         do {
            var7 = null;

            for (int var9 = var8; var9 < var1.size(); var9++) {
               var4 = (HOSECodeGenerator.TreeNode)var1.elementAt(var9);
               if (var4.source == var6 && !var2[var9]) {
                  if (var4.bondType <= 4) {
                     var5.append(this.bondSymbols[var4.bondType]);
                  }

                  var5.append(this.getElementSymbol(var4.symbol));
                  var2[var9] = true;
               }

               if (var4.source != var6 && var7 == null && !var2[var9]) {
                  var7 = var4.source;
               }

               if (this.debug) {
                  System.out.println("HOSECodeGenerator->getSphereCode()->treeNode.bondType:" + var4.bondType);
               }

               if (this.debug) {
                  System.out.println("HOSECodeGenerator->getSphereCode()->treeNode.score:" + var4.score);
               }
            }

            if (var7 != null) {
               var5.append(",");
               var6 = var7;
               var8++;
            }
         } while (var7 != null);

         var5.append(this.sphereDelimiters[this.sphere - 1]);
         return var5.toString();
      } else {
         return "";
      }
   }

   private void calculateNodeScores(Vector var1) {
      HOSECodeGenerator.TreeNode var2 = null;
      Object var3 = null;

      for (int var4 = 0; var4 < var1.size(); var4++) {
         var2 = (HOSECodeGenerator.TreeNode)var1.elementAt(var4);
         var2.score = var2.score + this.getElementRank(var2.symbol);
         if (var2.bondType <= 4) {
            var2.score = var2.score + bondRankings[var2.bondType];
         }
      }
   }

   private void sortNodes(Vector var1) {
      boolean var3;
      do {
         var3 = false;

         for (int var4 = 0; var4 < var1.size() - 1; var4++) {
            if (((HOSECodeGenerator.TreeNode)var1.elementAt(var4 + 1)).score > ((HOSECodeGenerator.TreeNode)var1.elementAt(var4)).score) {
               Object var2 = var1.elementAt(var4 + 1);
               var1.removeElementAt(var4 + 1);
               var1.insertElementAt(var2, var4);
               var3 = true;
            }
         }
      } while (var3);
   }

   private int getElementRank(String var1) {
      if (this.debug) {
         System.out.println("getElementRank() -> symbol: " + var1);
      }

      for (int var2 = 0; var2 < rankedSymbols.length; var2++) {
         if (rankedSymbols[var2].equals(var1)) {
            if (this.debug) {
               System.out.println("getElementRank() -> symbolRankings[f]: " + symbolRankings[var2]);
            }

            return symbolRankings[var2];
         }
      }

      Isotope var3 = new StandardIsotopes().getMajorIsotope(var1);
      return 800000 - var3.atomicMass;
   }

   private String getElementSymbol(String var1) {
      if (var1.equals("Si")) {
         return "Q";
      } else if (var1.equals("Cl")) {
         return "X";
      } else if (var1.equals("Br")) {
         return "Y";
      } else {
         return var1.equals(",") ? "" : var1;
      }
   }

   private void fillUpSphereDelimiters() {
      for (int var1 = this.sphere; var1 < 5; var1++) {
         this.HOSECode.append(this.sphereDelimiters[var1 - 1]);
      }
   }

   private void makeRingList() {
      int var1 = 0;
      int var2 = -1;
      this.ringScores = new int[this.setOfNodes.length];

      for (int var3 = 0; var3 < this.setOfNodes.length; var3++) {
         var1 += this.setOfNodes[var3].degree;
      }

      var1 /= 2;
      int var4 = var1 - this.setOfNodes.length + 1;
      if (this.debug) {
         System.out.println(var4 + " Rings exspected.");
      }

      if (var4 > 0) {
         setOfRings var5 = new SetOfRingsFinder().findSSSR(this.setOfNodes);
         var5.sort();
         Vector var6 = null;

         for (int var7 = 0; var7 < this.setOfNodes.length; var7++) {
            this.ringScores[var7] = 0;
         }

         for (int var8 = 0; var8 < var5.size(); var8++) {
            var6 = (Ring)var5.elementAt(var8);

            for (int var9 = 0; var9 < var6.size(); var9++) {
               var2 = ((Atom)var6.elementAt(var9)).number;
               if (this.ringScores[var2] == 0) {
                  this.ringScores[var2] = var6.size() * 1000000;
                  if (this.debug) {
                     System.out.println("ring score for node " + var2 + ": " + this.ringScores[var2]);
                  }
               }
            }
         }
      }
   }

   class TreeNode implements Serializable {
      String symbol;
      Atom source;
      Atom number;
      int bondType;
      long score;

      TreeNode(String var2, Atom var3, Atom var4, int var5, long var6) {
         this.symbol = var2;
         this.source = var3;
         this.number = var4;
         this.score = var6;
         this.bondType = var5;
         if (HOSECodeGenerator.this.debug) {
            System.out.println("This is tree node '" + var2 + "' no " + var4 + " with source " + var3 + " and bondType " + var5);
         }
      }
   }
}
