package seneca.judges;

import java.io.Serializable;

public class HMBCJudge extends TwoDSpectrumJudge implements Serializable, Cloneable {
   public HMBCJudge() {
      super("HMBCJudge");
      this.setScore(100, 0);
      this.setScore(100, 1);
      this.setScore(5, 2);
   }
}
