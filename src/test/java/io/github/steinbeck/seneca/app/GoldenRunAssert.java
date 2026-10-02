package io.github.steinbeck.seneca.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import io.github.steinbeck.seneca.chem.AtomSets;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.chem.LegacyMdlReader;
import io.github.steinbeck.seneca.golden.GoldenFiles;
import io.github.steinbeck.seneca.golden.Hashes;
import io.github.steinbeck.seneca.golden.JsonValue;
import io.github.steinbeck.seneca.golden.RunGolden;
import io.github.steinbeck.seneca.structgen.RunResult;
import io.github.steinbeck.seneca.structgen.StructureGeneratorResult;

/**
 * Compares a run started from the GUI with a golden run record (golden/runs/&lt;key&gt;.&lt;profile&gt;.seed&lt;s&gt;.json):
 * status, termination, enabled judges, counters, kT values, best structure, and every result
 * (score, structure hash, description). Valid for runs in which legacy does not fail (the application
 * runs DEFAULT, golden is STRICT; they are identical there, DefaultModeGoldenTest).
 */
public final class GoldenRunAssert {

    private GoldenRunAssert() {
    }

    public static RunGolden golden(String key, String profile, long seed) {
        return GoldenFiles.runs().stream().filter(r -> r.key().equals(key) && r.profile().equals(profile) && r.seed() == seed)
                .map(RunGolden::load).findFirst().orElseThrow();
    }

    /** {@code GoldenMaster.ctFromMDL}: bond matrix of an MDL string (dataset atom order). */
    public static int[][] ct(String mdl) {
        HeavyAtom[] atoms = LegacyMdlReader.read(mdl);
        int[][] bm = AtomSets.createConnectionTable(atoms);
        AtomSets.synchronizeConnectionTable(bm);
        return bm;
    }

    public static void assertMatches(RunGolden g, List<String> enabledJudges, RunResult r) {
        String id = g.id.toString();
        JsonValue.JObject rec = g.record;
        assertEquals(rec.str("status"), r.goldenStatus(), id + " status");
        assertEquals(rec.str("termination"), r.goldenTermination(), id + " termination");
        assertEquals(rec.arr("enabled").strings(), enabledJudges, id + " enabled judges");
        assertEquals(rec.lng("iterations"), r.iterations(), id + " iterations");
        assertEquals(rec.lng("rngDraws"), r.rngDraws(), id + " rngDraws");
        assertEquals(rec.lng("maxScore"), r.maxScore(), id + " maxScore");
        assertEquals(rec.dbl("start_kT"), r.startKT(), id + " start_kT");
        assertEquals(rec.dbl("final_kT"), r.finalKT(), id + " final_kT");
        assertEquals(rec.lng("plateauSteps"), r.plateauSteps(), id + " plateauSteps");
        assertEquals(rec.lng("maxUphillSteps"), r.maxUphillSteps(), id + " maxUphillSteps");
        if (!rec.isNull("bestScore")) {
            assertEquals(rec.lng("bestScore"), r.bestScore().score, id + " bestScore");
            assertEquals(rec.str("bestMDL"), r.bestStructure(), id + " bestMDL");
            assertEquals(rec.str("bestHash"), Hashes.hash(ct(r.bestStructure())), id + " bestHash");
        }
        StructureGeneratorResult res = r.result();
        assertEquals(rec.lng("nResults"), res.size(), id + " nResults");
        JsonValue.JArray results = rec.arr("results");
        for (int i = 0; i < res.size(); i++) {
            JsonValue.JObject e = results.get(i).asObject();
            assertEquals(e.lng("score"), res.scoreSummaries.get(i).score, id + " result " + i + " score");
            assertEquals(e.str("hash"), Hashes.hash(ct(res.structures.get(i))), id + " result " + i + " hash");
            assertEquals(e.str("description"), res.scoreSummaries.get(i).description, id + " result " + i + " description");
        }
    }
}
