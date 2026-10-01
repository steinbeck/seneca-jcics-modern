package seneca.client;

import seneca.core.SenecaDataset;
import seneca.judges.HMBCJudge;

public class HMBCJudgeConfigurator extends TwoDSpectrumJudgeConfigurator {
   HMBCJudgeConfigurator(SenecaDataset var1) {
      super(var1, (HMBCJudge)var1.getJudge("HMBCJudge"), new HMBCAssigner(var1), "HMBC");
   }
}
