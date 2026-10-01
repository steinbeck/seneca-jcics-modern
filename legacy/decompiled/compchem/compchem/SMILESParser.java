package compchem;

import compchem.exceptions.InvalidSMILESException;
import java.io.Serializable;
import java.util.Stack;
import java.util.Vector;

public class SMILESParser implements Serializable {
   Vector atoms;
   Atom[] rings;
   int[] ringbonds;
   int thisRing;
   int nodeCounter;
   String currentSymbol;
   int bondStatus;
   int position;
   String message = "Can't handle SMILES string";

   public Atom[] parseSmiles(String var1) throws InvalidSMILESException {
      this.atoms = new Vector();
      this.thisRing = -1;
      this.rings = new Atom[1024];
      this.ringbonds = new int[1024];

      for (int var2 = 0; var2 < 1024; var2++) {
         this.rings[var2] = null;
         this.ringbonds[var2] = -1;
      }

      this.currentSymbol = null;
      this.bondStatus = 0;
      this.position = 0;
      char[] var4 = new char[1];
      Atom var5 = null;
      Object var6 = null;
      Stack var7 = new Stack();
      Stack var8 = new Stack();
      Atom var9 = null;
      Object var10 = null;

      do {
         try {
            char var3 = var1.charAt(this.position);
            if ((var3 < 'A' || var3 > 'Z') && (var3 < 'a' || var3 > 'z')) {
               if (var3 == '=') {
                  this.bondStatus = 2;
                  this.position++;
               } else if (var3 == '#') {
                  this.bondStatus = 3;
                  this.position++;
               } else if (var3 == '(') {
                  var7.push(var5);
                  var8.push(new Integer(this.bondStatus));
                  this.position++;
               } else if (var3 == ')') {
                  var5 = (Atom)var7.pop();
                  this.bondStatus = (Integer)var8.pop();
                  this.position++;
               } else if (var3 >= '0' && var3 <= '9') {
                  var4[0] = var3;
                  this.currentSymbol = new String(var4);
                  this.thisRing = new Integer(new String(var4));
                  this.handleRing();
                  this.position++;
               } else if (var3 == '%') {
                  this.currentSymbol = this.getRingNumber(var1, this.position);
                  this.thisRing = new Integer(this.currentSymbol);
                  this.handleRing();
                  this.position = this.position + this.currentSymbol.length() + 1;
               } else {
                  if (var3 != '[') {
                     throw new InvalidSMILESException(this.message);
                  }

                  this.currentSymbol = this.getAtomString(var1, this.position);
                  var9 = this.assembleAtom(this.currentSymbol, this.nodeCounter);
                  if (var5 != null) {
                     var9.nodeTable[var9.degree] = var5;
                     var9.bondTable[var9.degree] = this.bondStatus;
                     var9.degree++;
                     var5.nodeTable[var5.degree] = var9;
                     var5.bondTable[var5.degree] = this.bondStatus;
                     var5.degree++;
                  }

                  this.atoms.addElement(var9);
                  this.bondStatus = 1;
                  if (var3 == 'c' || var3 == 'n' || var3 == 's' || var3 == 'o') {
                     this.bondStatus = 4;
                  }

                  var5 = var9;
                  this.nodeCounter++;
                  this.position = this.position + this.currentSymbol.length();
               }
            } else {
               this.currentSymbol = this.getElementSymbol(var1, this.position);
               if (this.bondStatus == 4 && var3 != 'c' && var3 != 'n' && var3 != 's' && var3 != 'o') {
                  this.bondStatus = 1;
               }

               var9 = new Atom(this.currentSymbol, this.nodeCounter);
               if (var5 != null) {
                  var9.nodeTable[var9.degree] = var5;
                  var9.bondTable[var9.degree] = this.bondStatus;
                  var9.degree++;
                  var5.nodeTable[var5.degree] = var9;
                  var5.bondTable[var5.degree] = this.bondStatus;
                  var5.degree++;
               }

               this.atoms.addElement(var9);
               this.bondStatus = 1;
               if (var3 == 'c' || var3 == 'n' || var3 == 's' || var3 == 'o') {
                  this.bondStatus = 4;
               }

               var5 = var9;
               this.nodeCounter++;
               this.position = this.position + this.currentSymbol.length();
            }
         } catch (Exception var12) {
            throw new InvalidSMILESException(this.message);
         }
      } while (this.position < var1.length());

      return new Molecule(this.atoms).getAtoms();
   }

   private String getAtomString(String var1, int var2) {
      StringBuffer var3 = new StringBuffer();
      return var3.toString();
   }

   private Atom assembleAtom(String var1, int var2) throws InvalidSMILESException {
      Atom var3 = new Atom();
      var3.number = var2;
      int var4 = 0;

      do {
         try {
            char var5 = var1.charAt(var4);
            if (var5 >= 'A' && var5 <= 'Z' || var5 >= 'a' && var5 <= 'z') {
               this.currentSymbol = this.getElementSymbol(var1, var4);
               var4 += this.currentSymbol.length();
            } else {
               if (var5 < '0' || var5 > '9') {
                  throw new InvalidSMILESException(this.message);
               }

               var4++;
            }
         } catch (Exception var7) {
            throw new InvalidSMILESException(this.message);
         }
      } while (var4 < var1.length());

      return var3;
   }

   private int getCharge(String var1) {
      byte var2 = 0;
      int var3 = var1.lastIndexOf("-");
      if (var3 != -1) {
         var3 = var1.indexOf("+");
      } else {
         var2 = -1;
      }

      return 0;
   }

   private void handleRing() {
      int var1 = this.bondStatus;
      Atom var2 = null;
      Object var3 = null;
      Atom var4 = this.rings[this.thisRing];
      if (var4 != null) {
         if (var1 == 4 && this.ringbonds[this.thisRing] != 4) {
            var1 = 1;
         }

         var2 = (Atom)this.atoms.elementAt(this.nodeCounter - 1);
         var2.nodeTable[var2.degree] = var4;
         var2.bondTable[var2.degree] = var1;
         var2.degree++;
         var4.nodeTable[var4.degree] = var2;
         var4.bondTable[var4.degree] = var1;
         var4.degree++;
         this.rings[this.thisRing] = null;
         this.ringbonds[this.thisRing] = -1;
      } else {
         this.rings[this.thisRing] = (Atom)this.atoms.elementAt(this.nodeCounter - 1);
         this.ringbonds[this.thisRing] = this.bondStatus;
      }
   }

   private String getElementSymbol(String var1, int var2) {
      if (var2 < var1.length() - 1 && "BrCl".indexOf(var1.substring(var2, var2 + 2)) >= 0) {
         return var1.substring(var2, var2 + 2);
      } else {
         return "BCcNnOoPSsFI".indexOf(var1.substring(var2, var2 + 1)) >= 0 ? var1.substring(var2, var2 + 1).toUpperCase() : null;
      }
   }

   private String getRingNumber(String var1, int var2) {
      byte var3 = 32;
      String var4 = "";
      var2++;

      do {
         var4 = var4 + var1.charAt(var2);
         var2++;
      } while (var2 < var1.length() && var1.charAt(var2) >= '0' && var1.charAt(var2) <= '9');

      return var4;
   }
}
