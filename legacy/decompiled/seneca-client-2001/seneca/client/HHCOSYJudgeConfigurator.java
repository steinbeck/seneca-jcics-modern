package seneca.client;

import seneca.core.SenecaDataset;
import seneca.judges.HHCOSYJudge;

public class HHCOSYJudgeConfigurator extends TwoDSpectrumJudgeConfigurator {
   HHCOSYJudgeConfigurator(SenecaDataset var1) {
      super(var1, (HHCOSYJudge)var1.getJudge("HHCOSYJudge"), new HHCOSYAssigner(var1), "HHCOSY");
   }
}
