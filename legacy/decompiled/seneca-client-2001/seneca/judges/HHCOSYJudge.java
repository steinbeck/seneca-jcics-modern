package seneca.judges;

import java.io.Serializable;

public class HHCOSYJudge extends TwoDSpectrumJudge implements Serializable, Cloneable {
   public HHCOSYJudge() {
      super("HHCOSYJudge");
      this.setScore(100, 0);
   }
}
