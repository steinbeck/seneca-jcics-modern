package io.github.steinbeck.seneca.golden;

import java.util.ArrayList;
import java.util.List;

import io.github.steinbeck.seneca.chem.AtomSets;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.golden.JsonValue.JArray;
import io.github.steinbeck.seneca.golden.JsonValue.JObject;
import io.github.steinbeck.seneca.judges.Attic;
import io.github.steinbeck.seneca.judges.FeatureJudge;
import io.github.steinbeck.seneca.judges.HHCOSYJudge;
import io.github.steinbeck.seneca.judges.HMBCJudge;
import io.github.steinbeck.seneca.judges.HOSECodeJudge;
import io.github.steinbeck.seneca.judges.HeteroBondsJudge;
import io.github.steinbeck.seneca.judges.HybridizationJudge;
import io.github.steinbeck.seneca.judges.Judge;
import io.github.steinbeck.seneca.judges.JudgeSet;
import io.github.steinbeck.seneca.judges.SymmetryJudge;
import io.github.steinbeck.seneca.judges.TwoDSpectrumJudge;
import io.github.steinbeck.seneca.model.AnnealingSchedule;
import io.github.steinbeck.seneca.structgen.RunInput;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Builds port inputs directly from the golden echo records - the atoms of stage
 * {@code configured_gui} and the judges exactly as the legacy GUI handed them to the generator
 * ({@code judges} stage {@code configured_<profile>}) - <b>without</b> the port's SML reader and GUI
 * recipe (work package B). This decouples the judge (A) and generator (C) golden tests from B.
 * The end-to-end path (SML -&gt; recipe -&gt; RunInput) is tested separately.
 *
 * <p>Judges are configured through the port's public judge API; for enabled judges
 * {@code calcMaxScore()} is called as in {@code goAction} and its result asserted against the echo.
 */
public final class GoldenFixtures {

    private GoldenFixtures() {
    }

    /** Heavy atoms of the configured dataset (dataset order, H counts and shifts after the recipe). */
    public static HeavyAtom[] atoms(EchoGolden echo) {
        JArray arr = echo.atoms("configured_gui").arr("atoms");
        HeavyAtom[] atoms = new HeavyAtom[arr.size()];
        for (int f = 0; f < atoms.length; f++) {
            JObject a = arr.get(f).asObject();
            HeavyAtom h = new HeavyAtom(a.str("el"), a.integer("number"));
            h.name = a.str("id");
            h.hCount = a.integer("h");
            h.maxBondOrder = a.integer("maxBondOrder");
            h.maxBondOrderSum = a.integer("maxBondOrderSum");
            h.shift13C = a.isNull("shift") ? null : a.flt("shift");
            atoms[f] = h;
        }
        return atoms;
    }

    /** Schedules after the recipe (stage configured_gui; identical for all profiles). */
    public static List<AnnealingSchedule> schedules(EchoGolden echo) {
        List<AnnealingSchedule> l = new ArrayList<>();
        for (JsonValue v : echo.schedules("configured_gui").arr("schedules").items()) {
            JObject s = v.asObject();
            l.add(new AnnealingSchedule(s.dbl("initialAcceptanceProbability"), s.lng("maxUphillSteps"),
                    s.lng("plateauSteps"), s.dbl("asymptoticCoolingFactor")));
        }
        return l;
    }

    /**
     * The 7 judges of {@code profile} as handed to the generator (configured, enabled flags,
     * calcMaxScore done on the enabled ones and checked against the echo).
     */
    public static List<Judge> judges(EchoGolden echo, String profile) {
        int n = echo.nAtoms();
        List<Judge> judges = new ArrayList<>();
        for (JsonValue v : echo.judges("configured_" + profile).arr("judges").items()) {
            JObject j = v.asObject();
            Judge judge = switch (j.str("class")) {
                case "HHCOSYJudge" -> new HHCOSYJudge();
                case "HMBCJudge" -> new HMBCJudge();
                case "HeteroBondsJudge" -> new HeteroBondsJudge();
                case "SymmetryJudge" -> new SymmetryJudge();
                case "HybridizationJudge" -> new HybridizationJudge();
                case "HOSECodeJudge" -> new HOSECodeJudge();
                case "FeatureJudge" -> new FeatureJudge();
                default -> throw new IllegalArgumentException("unknown judge class " + j.str("class"));
            };
            configure(judge, j, n);
            judge.setEnabled(j.bool("enabled"));
            if (judge.getEnabled()) {
                judge.calcMaxScore();
                assertEquals(j.integer("maxScore"), judge.getMaxScore(),
                        echo.key + "/" + profile + ": " + judge.name + ".calcMaxScore()");
                if (judge instanceof HeteroBondsJudge hb)
                    assertEquals(j.integer("privateMaxScore"), hb.getPrivateMaxScore(), echo.key + ": HeteroBondsJudge private maxScore");
                if (judge instanceof TwoDSpectrumJudge t)
                    assertEquals(j.integer("numberOf2DSignals"), t.getNumberOf2DSignals(), echo.key + ": " + judge.name + " numberOf2DSignals");
            }
            judges.add(judge);
        }
        return judges;
    }

    private static void configure(Judge judge, JObject j, int n) {
        switch (judge) {
            case TwoDSpectrumJudge t -> {
                t.setScores(j.arr("scores").ints());
                if (!j.isNull("assignment")) {
                    int size = j.integer("assignmentSize");
                    boolean[][][] a = new boolean[size][size][size];
                    for (int[] fgh : j.arr("assignment").intRows()) a[fgh[0]][fgh[1]][fgh[2]] = true;
                    t.assignment = a;
                }
            }
            case HOSECodeJudge h -> h.setCarbonShifts(j.arr("carbonShifts").doubles());
            case SymmetryJudge s -> {
                for (JsonValue c : j.arr("classes").items()) {
                    boolean[] members = new boolean[n];
                    for (int m : c.asArray().ints()) members[m] = true;
                    s.addSymmetryClass(members);
                }
            }
            case FeatureJudge f -> {
                for (int[] sizeScore : j.arr("ringSizeScores").intRows()) f.addRingSizeScore(sizeScore[0], sizeScore[1]);
                f.setHetHetPenalty(j.integer("hetHetPenalty"));
                f.setHetHetPenaltyEnabled(j.bool("hetHetPenaltyEnabled"));
            }
            case HybridizationJudge h -> {
                if (!j.isNull("scores")) h.scores = j.arr("scores").intRows();
            }
            case HeteroBondsJudge h -> {
                h.setScore(j.integer("score"));
                if (!j.isNull("isHeteroBonded")) {
                    JArray a = j.arr("isHeteroBonded");
                    h.isHeteroBonded = new boolean[a.size()];
                    for (int i = 0; i < a.size(); i++) h.isHeteroBonded[i] = a.get(i).asBoolean();
                }
            }
            default -> throw new IllegalArgumentException(judge.name);
        }
    }

    /** The generator input of {@code key}/{@code profile} built from the echo. */
    public static RunInput runInput(String key, String profile) {
        EchoGolden echo = EchoGolden.load(key);
        return new RunInput(atoms(echo), judges(echo, profile), schedules(echo), echo.dataset().str("title"));
    }

    /**
     * The harness's independent scorer ({@code GoldenMaster.Scorer}): the judges of {@code input}
     * (use a fresh input or {@code input.copy()}), {@code init()} once on an attic holding an unbonded
     * clone of the atoms; then {@link Scorer#score} per structure.
     */
    public static Scorer scorer(RunInput input) {
        return new Scorer(input);
    }

    public static final class Scorer {
        public final Attic attic = new Attic();
        public final JudgeSet judges;

        Scorer(RunInput input) {
            judges = new JudgeSet(new ArrayList<>(input.judges()));
            attic.atomSet = AtomSets.cloneAtomSet(input.atoms());
            attic.normalized = AtomSets.cloneAtomSet(input.atoms());
            judges.initJudges(attic);
        }

        public JudgeSet.Breakdown score(int[][] bm) {
            return judges.scoreStructure(attic, bm);
        }
    }
}
