package io.github.steinbeck.seneca.golden;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.config.GuiRecipe;
import io.github.steinbeck.seneca.config.Profile;
import io.github.steinbeck.seneca.io.SpecMLReader;
import io.github.steinbeck.seneca.judges.FeatureJudge;
import io.github.steinbeck.seneca.judges.HOSECodeJudge;
import io.github.steinbeck.seneca.judges.HeteroBondsJudge;
import io.github.steinbeck.seneca.judges.HybridizationJudge;
import io.github.steinbeck.seneca.judges.Judge;
import io.github.steinbeck.seneca.judges.SymmetryJudge;
import io.github.steinbeck.seneca.judges.TwoDSpectrumJudge;
import io.github.steinbeck.seneca.model.AnnealingSchedule;
import io.github.steinbeck.seneca.model.NMRSignal;
import io.github.steinbeck.seneca.model.NMRSpectrum;
import io.github.steinbeck.seneca.model.SenecaDataset;
import io.github.steinbeck.seneca.model.SpectrumSlot;
import io.github.steinbeck.seneca.structgen.RunInput;

/**
 * Emits golden/datasets/&lt;key&gt;.echo.jsonl (golden/FORMAT.md (a)) from the port: the same record
 * types, key order and values as {@code GoldenMaster.echo} ({@code legacy/harness/src/seneca/structgen/GoldenMaster.java}
 * methods {@code echo}, {@code atomsRecord}, {@code spectrumRecord}, {@code schedulesRecord},
 * {@code judgesRecord}/{@code judgeJson}, {@code generatorInput}).
 *
 * <p>Judge fields come from the port's judge API ({@code getJudgeScoreField()},
 * {@code TwoDSpectrumJudge.getCutoff()}, {@code HeteroBondsJudge.getPrivateMaxScore()}, ...). The
 * "configured" judges are those of {@code RunInput.fromDataset(sd, STRICT)} (copies after calcMaxScore).
 */
public final class EchoRecords {

    /** Profiles in golden order. */
    public static final List<Profile> ECHO_PROFILES = List.of(Profile.GUI, Profile.ALL, Profile.PAPER, Profile.MANUAL);

    private EchoRecords() {
    }

    /** All lines of the echo file for {@code smlFile} / {@code key}, in golden order (without "\n"). */
    public static List<String> echo(Path smlFile, String key) throws IOException {
        List<String> out = new ArrayList<>();
        // as read: a plain load, no GUI steps
        SenecaDataset raw = SpecMLReader.read(smlFile);
        if (raw.setOfNodes == null) {
            out.add(Json.obj().put("t", "unreadable").put("key", key).put("reason", "no atoms").toString());
            return out;
        }
        boolean anyShift = false;
        for (HeavyAtom at : raw.setOfNodes) if (at.shift13C != null) anyShift = true;
        out.add(Json.obj().put("t", "dataset").put("key", key).put("file", smlFile.getFileName().toString())
                .put("title", raw.getName()).put("formula", raw.getMolecularFormula())
                .put("description", raw.getDescription()).putFloat("molecularMass", raw.getMolecularMass())
                .put("nAtoms", raw.setOfNodes.length).put("shiftsRead", anyShift)
                .put("ownStructure", "none: no bundled file contains bonds; see references/").toString());
        out.add(atomsRecord("as_read", raw.setOfNodes).toString());
        for (SpectrumSlot slot : SpectrumSlot.values()) out.add(spectrumRecord("as_read", slot, raw.spectrum(slot)).toString());
        out.add(schedulesRecord("as_read", raw.annealingSchedules).toString());
        out.add(judgesRecord("as_read", raw.judges, false).toString());

        for (Profile profile : ECHO_PROFILES) {
            GuiRecipe.Prepared p = GuiRecipe.prepare(smlFile, key, profile);
            RunInput go = RunInput.fromDataset(p.sd, LegacyCompat.STRICT);
            String st = "configured_" + profile.key;
            out.add(Json.obj().put("t", "recipe").put("profile", profile.key)
                    .put("autoAssign", p.autoAssigned).put("scheduleAdded", p.scheduleAdded)
                    .put("hOverrides", Json.strings(p.hOverrides)).put("steps", Json.strings(p.steps)).toString());
            if (profile == Profile.GUI) {
                out.add(atomsRecord(st, p.sd.setOfNodes).toString());
                for (SpectrumSlot slot : SpectrumSlot.values()) out.add(spectrumRecord(st, slot, p.sd.spectrum(slot)).toString());
                out.add(schedulesRecord(st, p.sd.annealingSchedules).toString());
            }
            // the judges exactly as handed to the generator (deep copies, after calcMaxScore)
            out.add(judgesRecord(st, go.judges(), true).toString());
            out.add(Json.obj().put("t", "generatorInput").put("profile", profile.key)
                    .put("enabled", Json.strings(GuiRecipe.enabledJudges(p.sd)))
                    .put("maxScore", generatorMaxScore(go.judges())).toString());
        }
        return out;
    }

    static Json atomsRecord(String stage, HeavyAtom[] atoms) {
        Json arr = Json.arr();
        int hSum = 0;
        for (int f = 0; f < atoms.length; f++) {
            HeavyAtom at = atoms[f];
            hSum += at.hCount;
            arr.add(Json.obj().put("i", f).put("el", at.symbol).put("id", at.name).put("h", at.hCount)
                    .putFloat("shift", at.shift13C).put("number", at.number)
                    .put("maxBondOrder", at.maxBondOrder).put("maxBondOrderSum", at.maxBondOrderSum));
        }
        return Json.obj().put("t", "atoms").put("stage", stage).put("hSum", hSum).put("atoms", arr);
    }

    static Json spectrumRecord(String stage, SpectrumSlot slot, NMRSpectrum s) {
        Json sig = Json.arr();
        for (int f = 0; f < s.size(); f++) {
            NMRSignal g = s.getSignal(f);
            sig.add(Json.obj().put("shift", Json.floats(g.shift)).putFloat("intensity", g.intensity)
                    .putFloat("normalizeFactor", g.normalizeFactor).put("phase", g.phase)
                    .put("nucleus", Json.strings(g.nucleus)));
        }
        return Json.obj().put("t", "spectrum").put("stage", stage).put("slot", slot.key).put("name", s.name)
                .put("specType", s.specType).put("dim", s.dim).put("nucleus", Json.strings(s.nucleus))
                .putFloat("frequency", s.spectrometerFrequency).put("solvent", s.solvent)
                .put("standard", s.standard).put("nSignals", s.size()).put("signals", sig);
    }

    static Json schedulesRecord(String stage, List<AnnealingSchedule> v) {
        Json arr = Json.arr();
        for (AnnealingSchedule s : v) {
            arr.add(Json.obj().put("initialAcceptanceProbability", s.initialAcceptanceProbability)
                    .put("maxUphillSteps", s.maxUphillSteps).put("plateauSteps", s.plateauSteps)
                    .put("asymptoticCoolingFactor", s.asymptoticCoolingFactor));
        }
        return Json.obj().put("t", "schedules").put("stage", stage).put("schedules", arr);
    }

    static Json judgesRecord(String stage, List<Judge> judges, boolean full) {
        Json arr = Json.arr();
        for (Judge j : judges) arr.add(judgeJson(j, full));
        return Json.obj().put("t", "judges").put("stage", stage).put("judges", arr);
    }

    /** {@code GoldenMaster.judgeJson}. */
    static Json judgeJson(Judge j, boolean full) {
        Json o = Json.obj().put("name", j.name).put("class", j.getClass().getSimpleName())
                .put("enabled", j.getEnabled()).put("hasMaxScore", j.getHasMaxScore())
                .put("maxScore", j.getMaxScore()).put("multiplicator", j.getMultiplicator())
                .put("ringSetRequired", j.isRingSetRequired())
                .put("judgeScoreField", j.getJudgeScoreField());
        if (!full) return o;
        switch (j) {
            case TwoDSpectrumJudge t -> {
                o.put("scores", Json.ints(t.scores));
                if (t.assignment == null) {
                    o.putNull("assignment");
                } else {
                    Json tr = Json.arr();
                    int n = t.assignment.length;
                    for (int f = 0; f < n; f++)
                        for (int g = 0; g < n; g++)
                            for (int h = 0; h < n; h++)
                                if (t.assignment[f][g][h]) tr.add(Json.arr().add(f).add(g).add(h));
                    o.put("assignmentSize", n).put("assignment", tr);
                }
                o.put("rules", t.rules.size()).put("numberOf2DSignals", t.getNumberOf2DSignals()).put("cutoff", t.getCutoff());
            }
            case HOSECodeJudge h -> o.put("score", h.score).put("carbonShifts", Json.doubles(h.getCarbonShifts()));
            case SymmetryJudge s -> {
                Json cl = Json.arr();
                for (int c = 0; c < s.getSymmetryClassCount(); c++) {
                    boolean[] b = s.getSymmetryClass(c);
                    Json m = Json.arr();
                    for (int f = 0; f < b.length; f++) if (b[f]) m.add(f);
                    cl.add(m);
                }
                o.put("classes", cl);
            }
            case FeatureJudge fj -> {
                Map<Integer, Integer> sorted = new TreeMap<>(fj.ringSizeScores);
                Json rs = Json.arr();
                for (Map.Entry<Integer, Integer> e : sorted.entrySet()) rs.add(Json.arr().add(e.getKey()).add(e.getValue()));
                o.put("ringSizeScores", rs).put("hetHetPenalty", fj.getHetHetPenalty())
                        .put("hetHetPenaltyEnabled", fj.isHetHetPenaltyEnabled())
                        .put("ringsizeRestrictionEnabled", fj.isRingsizeRestrictionEnabled());
            }
            case HybridizationJudge h -> {
                Json sc = Json.arr();
                if (h.scores != null) for (int[] row : h.scores) sc.add(Json.ints(row));
                o.put("scores", h.scores == null ? null : sc);
            }
            case HeteroBondsJudge h -> {
                Json hb = Json.arr();
                if (h.isHeteroBonded != null) for (boolean b : h.isHeteroBonded) hb.add(b);
                o.put("score", h.score).put("isHeteroBonded", h.isHeteroBonded == null ? null : hb)
                        .put("privateMaxScore", h.getPrivateMaxScore());
            }
            default -> {
            }
        }
        return o;
    }

    /** The generator's private {@code getMaxScore()}: sum over enabled judges, -1 if one has no max. */
    static long generatorMaxScore(List<Judge> judges) {
        int max = 0;
        for (Judge j : judges) {
            if (j.getEnabled()) {
                if (!j.getHasMaxScore()) return -1;
                max += j.getMaxScore();
            }
        }
        return max;
    }
}
