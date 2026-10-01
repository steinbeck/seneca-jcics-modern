import java.io.*;
import java.util.*;
import compchem.*;
import seneca.core.*;
import seneca.judges.*;
import seneca.structgen.*;
import seneca.client.CarbonShiftAssigner;
import seneca.client.HHCOSYAssigner;
import seneca.client.HMBCAssigner;

/**
 * Verified sketch of an in-process, headless SENECA run against legacy/runnable/classes.
 * Mirrors the GUI sequence: open .sml -> (Atom Properties) AutoAssign -> (Judges)
 * "Autoconfigure Judge" -> (Structure Generation) "Start local server" + "Start as selected",
 * with the RMI layer (StructureGeneratorServer) bypassed. See HOW-TO-DRIVE.md.
 *
 *   javac -cp ../classes:$(ls ../../original/jar/*.jar | tr '\n' ':') -d /tmp/drive DriveSketch.java
 *   java  -cp /tmp/drive:../classes:<jars> DriveSketch ../../original/sml/pinene.sml 1 assign
 *
 * args: <file.sml> <seed> [assign]  ("assign" = run CarbonShiftAssigner, needed when the
 *        .sml molecule block is not read, see HOW-TO-DRIVE.md)
 */
public class DriveSketch {
    public static void main(String[] a) throws Exception {
        File file = new File(a[0]);
        long seed = Long.parseLong(a[1]);
        boolean autoAssign = a.length > 2 && a[2].equals("assign");

        // log4j 1.x: the generator logs through Category "seneca.structgen.StochasticGenerator.log"
        // and ".results". Configure something, otherwise log4j prints "no appenders" warnings.
        org.apache.log4j.BasicConfigurator.configure();
        org.apache.log4j.Category.getRoot().setPriority(org.apache.log4j.Priority.WARN);

        // 1. Dataset (Seneca.openFile): SpecMLReader parses, getSenecaDataset() calls sd.init(),
        //    which appends 7 default, disabled judges to sd.judges.
        SenecaDataset sd = new SpecMLReader(file).getSenecaDataset();

        // 2. Atom Properties -> "AutoAssign": 13C shifts + H counts from 1D carbon / DEPT spectra.
        if (autoAssign) new CarbonShiftAssigner(sd).assign();

        // 3. Side effect of opening the dataset window: these two configurator constructors
        //    point the judge's attic at sd.setOfNodes and call init().
        HybridizationJudge hy = (HybridizationJudge) sd.getJudge("HybridizationJudge");
        hy.getAttic().atomSet = sd.getSetOfNodes();
        hy.init();
        HeteroBondsJudge hb = (HeteroBondsJudge) sd.getJudge("HeteroBondsJudge");
        hb.getAttic().atomSet = sd.getSetOfNodes();
        hb.init();

        // 4. Judges -> "Autoconfigure Judge" = judge.setEnabled(true); configurator.autoconfigure()
        TwoDSpectrumJudge cosy = (TwoDSpectrumJudge) sd.getJudge("HHCOSYJudge");
        cosy.setEnabled(true);
        if (!new HHCOSYAssigner(sd).assign()) cosy.setEnabled(false);      // GUI behaviour
        if (cosy.assignment == null) cosy.setEnabled(false);                // harness guard, see doc
        TwoDSpectrumJudge hmbc = (TwoDSpectrumJudge) sd.getJudge("HMBCJudge");
        hmbc.setEnabled(true);
        if (!new HMBCAssigner(sd).assign()) hmbc.setEnabled(false);
        if (hmbc.assignment == null) hmbc.setEnabled(false);

        HOSECodeJudge hose = (HOSECodeJudge) sd.getJudge("HOSECodeJudge");  // HOSECodeJudgeConfigurator.autoconfigure
        hose.setEnabled(true);
        Atom[] son = sd.setOfNodes;
        double[] tmp = new double[son.length];
        int cc = 0;
        for (int f = 0; f < son.length; f++)
            if (son[f].getPhysicalProperty(Atom.PHYS_PROP_13C_SHIFT) != null)
                tmp[cc++] = ((Float) son[f].getPhysicalProperty(Atom.PHYS_PROP_13C_SHIFT)).floatValue();
        double[] shifts = new double[cc];
        System.arraycopy(tmp, 0, shifts, 0, cc);
        hose.setCarbonShifts(shifts);

        FeatureJudge fj = (FeatureJudge) sd.getJudge("FeatureJudge");     // FeatureJudgeConfigurator.autoconfigure
        fj.setEnabled(true);
        fj.addRingSizeScore(3, -500);
        fj.addRingSizeScore(4, -300);
        fj.setHetHetPenaltyEnabled(true);
        // SymmetryJudge: see HOW-TO-DRIVE.md (equal-shift classes); left disabled here.

        // 5. "Start as selected" (SGServerPanel.goAction) + StructureGeneratorServer.executeTask
        for (int g = 0; g < sd.judges.size(); g++) {
            Judge j = (Judge) sd.judges.elementAt(g);
            if (j.getEnabled()) j.calcMaxScore();          // on the dataset's judges, before cloning
        }
        Vector sched = new Vector();
        for (int g = 0; g < sd.annealingSchedules.size(); g++)
            sched.addElement(((AnnealingSchedule) sd.annealingSchedules.elementAt(g)).clone());
        Molecule mol = (Molecule) serClone(new Molecule(sd.getSetOfNodes()));
        Vector judges = (Vector) serClone(sd.judges);       // ALL judges, enabled flag travels along
        Vector schedules = (Vector) serClone(sched);

        Rng.seed(seed);                                      // before the generator draws anything
        StochasticGenerator sg = new StochasticGenerator(true);
        sg.setMolecule(mol);
        sg.setJudges(judges);
        sg.setDatasetName(sd.getName());
        sg.setSASchedules(schedules);
        sg.run();                                            // synchronous; the GUI uses execute() = start()

        StructureGeneratorResult r = (StructureGeneratorResult) sg.getResult();
        System.out.println("results=" + r.size());
        for (int i = 0; i < r.size(); i++) {
            ScoreSummary s = (ScoreSummary) r.scoreSummaries.elementAt(i);
            System.out.println("score=" + s.score + "\n" + r.structures.elementAt(i) + "$$$$");
        }
        if (r.size() > 0) {   // getStatus() throws NPE while bestStructure == null
            StructureGeneratorStatus st = (StructureGeneratorStatus) sg.getStatus();
            System.out.println("iterations=" + st.iteration + " best=" + st.bestEvaluation.score
                    + "/" + st.bestEvaluation.maxScore);
        }
    }

    /** SGServerPanel.goAction.serClone: deep copy via Java serialization. */
    static Object serClone(Object o) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        new ObjectOutputStream(buf).writeObject(o);
        return new ObjectInputStream(new ByteArrayInputStream(buf.toByteArray())).readObject();
    }
}
