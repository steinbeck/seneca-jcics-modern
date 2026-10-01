package compchem.exceptions;

public class TooManyBondsException extends Exception {
   public TooManyBondsException() {
      super("Attempt to add too many bonds to this atom, according to the atoms maxBondSum value");
   }
}
