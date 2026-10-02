package io.github.steinbeck.seneca.golden;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import io.github.steinbeck.seneca.golden.JsonValue.JObject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Self-test of the golden test infrastructure (runs now, independent of the port):
 * every golden file is found and parses; parse + re-serialise reproduces every line byte for byte
 * (so {@link JsonDiff} compares exactly what the harness wrote); every {@code hash} equals the SHA-1
 * of its {@code ct} as defined in golden/FORMAT.md; signed zero and float lexemes survive.
 */
class GoldenReadersTest {

    @Test
    void layoutIsComplete() {
        List<String> keys = GoldenFiles.keys();
        assertEquals(11, keys.size(), "dataset keys: " + keys);
        assertTrue(keys.contains("polycarpol@hblock"));
        for (String k : keys) {
            assertTrue(GoldenFiles.sml(k).toFile().isFile(), "sml for " + k);
            assertTrue(GoldenFiles.scores(k).toFile().isFile(), "scores for " + k);
            assertTrue(GoldenFiles.reference(k).toFile().isFile(), "reference for " + k);
        }
        assertEquals(88, GoldenFiles.trajectories().size(), "trajectory files");
        assertEquals(72, GoldenFiles.runs().size(), "run files");
        assertTrue(GoldenFiles.referencesTsv().toFile().isFile());
    }

    @Test
    void hashDefinition() {
        // empty structure hashes "" ; a 2-atom single bond hashes "0,1,1"
        assertEquals(Hashes.sha1Hex(""), Hashes.hash(new int[3][3]));
        int[][] bm = Hashes.matrixFromCt(3, new int[][]{{0, 1, 1}, {1, 2, 2}});
        assertEquals("0,1,1;1,2,2", Hashes.ctString(bm));
        assertEquals("[[0,1,1],[1,2,2]]", Hashes.ct(bm).toString());
    }

    @TestFactory
    List<DynamicTest> jsonLinesRoundTripAndHashes() {
        List<DynamicTest> tests = new ArrayList<>();
        for (String k : GoldenFiles.keys()) {
            tests.add(file("echo " + k, GoldenFiles.echo(k), r -> { }));
            tests.add(file("scores " + k, GoldenFiles.scores(k), GoldenReadersTest::checkHashes));
            tests.add(file("reference " + k, GoldenFiles.reference(k), GoldenReadersTest::checkHashes));
        }
        for (GoldenFiles.RunId id : GoldenFiles.runs()) tests.add(file("run " + id, id.file(), GoldenReadersTest::checkHashes));
        for (GoldenFiles.RunId id : GoldenFiles.trajectories())
            tests.add(file("trajectory " + id, id.file(), GoldenReadersTest::checkHashes));
        tests.add(file("MANIFEST", GoldenFiles.golden().resolve("MANIFEST.jsonl"), r -> { }));
        return tests;
    }

    private static DynamicTest file(String name, Path p, Consumer<JObject> check) {
        return DynamicTest.dynamicTest(name, () -> {
            List<String> lines = GoldenFiles.lines(p);
            assertFalse(lines.isEmpty(), p + " is empty");
            for (String line : lines) {
                JsonValue v = JsonValue.parse(line);
                assertEquals(line, v.toJson(), "round trip of " + p);
                check.accept(v.asObject());
            }
        });
    }

    /** Every object (recursively) with ct+hash, candidateCt+candidateHash, bestCt+bestHash is consistent. */
    static void checkHashes(JsonValue v) {
        if (v instanceof JObject o) {
            pair(o, "ct", "hash");
            pair(o, "candidateCt", "candidateHash");
            pair(o, "bestCt", "bestHash");
            for (JsonValue m : o.members().values()) checkHashes(m);
        } else if (v instanceof JsonValue.JArray a) {
            for (JsonValue m : a.items()) checkHashes(m);
        }
    }

    private static void pair(JObject o, String ctKey, String hashKey) {
        if (!o.has(ctKey) || !o.has(hashKey) || o.isNull(ctKey)) return;
        int[][] ct = o.arr(ctKey).intRows();
        int n = 0;
        for (int[] b : ct) n = Math.max(n, b[1] + 1);
        assertEquals(o.str(hashKey), Hashes.hash(Hashes.matrixFromCt(n, ct)), hashKey + " of " + JsonValue.abbreviate(o.toJson()));
    }

    @Test
    void signedZeroIsPreserved() {
        // eupatorio gui seed 1 anneals at kT = -0.0 (golden/FORMAT.md "Annealing at kT = -0.0")
        GoldenFiles.RunId id = GoldenFiles.trajectories().stream()
                .filter(r -> r.toString().equals("eupatorio.gui.seed1")).findFirst().orElseThrow();
        JObject schedule = TrajectoryGolden.load(id).records().stream()
                .filter(r -> "schedule".equals(r.type())).findFirst().orElseThrow();
        JsonValue kt = schedule.get("start_kT");
        assertEquals("-0.0", ((JsonValue.JNumber) kt).lexeme());
        assertEquals(Double.doubleToRawLongBits(-0.0), Double.doubleToRawLongBits(kt.asDouble()));
        assertNotEquals(Double.doubleToRawLongBits(0.0), Double.doubleToRawLongBits(kt.asDouble()));
        // the difference is visible to the comparator
        assertTrue(JsonDiff.firstDifference(JsonValue.parse("{\"k\":-0.0}"), JsonValue.parse("{\"k\":0.0}"), java.util.Set.of()) != null);
    }

    @Test
    void floatFieldsAreBinary32() {
        // HOSECodeJudge.carbonShifts are doubles converted from float shifts: 31.299999237060547 == (double) 31.3f
        EchoGolden e = EchoGolden.load("pinene");
        JObject atom6 = e.atoms("configured_gui").arr("atoms").get(6).asObject();
        float shift = atom6.flt("shift");
        assertEquals(Float.toString(shift), ((JsonValue.JNumber) atom6.get("shift")).lexeme());
        double[] cs = null;
        for (JsonValue j : e.judges("configured_gui").arr("judges").items())
            if (j.asObject().str("name").equals("HOSECodeJudge")) cs = j.asObject().arr("carbonShifts").doubles();
        assertEquals((double) shift, cs[6]);
    }

    @Test
    void readersExposeRecords() {
        EchoGolden e = EchoGolden.load("pinene");
        assertEquals(10, e.nAtoms());
        assertEquals(2400, e.generatorInput("gui").lng("maxScore"));
        ScoresGolden s = ScoresGolden.load("pinene");
        assertEquals(50, s.structures().size());
        ReferenceGolden r = ReferenceGolden.load("pinene");
        assertTrue(r.mappable());
        assertEquals(4, r.best().size());
        RunGolden run = RunGolden.load(GoldenFiles.runs().stream().filter(x -> x.toString().equals("pinene.gui.seed1")).findFirst().orElseThrow());
        assertEquals("finished", run.status());
        assertEquals(21248, run.iterations());
        GoldenFiles.RunId q1 = GoldenFiles.trajectories().stream().filter(x -> x.toString().equals("pinene.gui.seed3")).findFirst().orElseThrow();
        TrajectoryGolden t = TrajectoryGolden.load(q1);
        assertEquals("no_main_step(Q1)", t.endReason());
        assertNull(t.records().stream().filter(x -> "schedule".equals(x.type())).findFirst().orElse(null),
                "a Q1 trajectory has no schedule record");
    }
}
