package io.github.steinbeck.seneca.oracle;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Vector;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;

import compchem.Atom;
import compchem.DataStructureTools;
import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.chem.AtomSets;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.golden.EchoGolden;
import io.github.steinbeck.seneca.golden.GoldenFiles;
import io.github.steinbeck.seneca.golden.GoldenFixtures;
import io.github.steinbeck.seneca.golden.JsonValue;
import io.github.steinbeck.seneca.golden.JsonValue.JArray;
import io.github.steinbeck.seneca.golden.JsonValue.JObject;
import io.github.steinbeck.seneca.judges.Attic;
import io.github.steinbeck.seneca.judges.Judge;
import io.github.steinbeck.seneca.judges.JudgeResult;
import io.github.steinbeck.seneca.judges.JudgeSet;
import io.github.steinbeck.seneca.oracle.OracleSupport.Struct;
import io.github.steinbeck.seneca.oracle.OracleSupport.Tally;

/**
 * The port's judges against the original SENECA judge classes ({@code seneca.judges.*} from
 * {@code legacy/runnable/classes}, compchem underneath), configured identically from the golden echo
 * records, for every dataset and profile ({@code gui}, {@code paper}, {@code all}, {@code manual}):
 * {@code calcMaxScore}, then the generator's life cycle ({@code setAttic} + {@code init()} on the enabled
 * judges, {@code synchronizeBonds} + {@code evaluate} per structure), comparing every enabled judge's
 * {@code JudgeResult} (score, maxScore, propertyValue, description) or exception, the summed score and
 * {@code getMaxScore()} - on the golden score structures, every annealed result structure of the golden
 * runs, the crash candidates and random walks (1,000 moves from a score structure, 300 from each crash
 * candidate). Also checks that a {@link Judge#copy()} scores identically to the original.
 */
@Tag("A")
class JudgeOracleTest {

    static final String[] PROFILES = {"gui", "paper", "all", "manual"};

    /** Legacy judges configured from the echo exactly like {@code GoldenFixtures.judges}. */
    static Vector<seneca.judges.Judge> legacyJudges(EchoGolden echo, String profile) {
        int n = echo.nAtoms();
        Vector<seneca.judges.Judge> judges = new Vector<>();
        for (JsonValue v : echo.judges("configured_" + profile).arr("judges").items()) {
            JObject j = v.asObject();
            seneca.judges.Judge judge = switch (j.str("class")) {
                case "HHCOSYJudge" -> new seneca.judges.HHCOSYJudge();
                case "HMBCJudge" -> new seneca.judges.HMBCJudge();
                case "HeteroBondsJudge" -> new seneca.judges.HeteroBondsJudge();
                case "SymmetryJudge" -> new seneca.judges.SymmetryJudge();
                case "HybridizationJudge" -> new seneca.judges.HybridizationJudge();
                case "HOSECodeJudge" -> new seneca.judges.HOSECodeJudge();
                case "FeatureJudge" -> new seneca.judges.FeatureJudge();
                default -> throw new IllegalArgumentException(j.str("class"));
            };
            switch (judge) {
                case seneca.judges.TwoDSpectrumJudge t -> {
                    t.setScores(j.arr("scores").ints());
                    if (!j.isNull("assignment")) {
                        int size = j.integer("assignmentSize");
                        boolean[][][] a = new boolean[size][size][size];
                        for (int[] fgh : j.arr("assignment").intRows()) a[fgh[0]][fgh[1]][fgh[2]] = true;
                        t.assignment = a;
                    }
                }
                case seneca.judges.HOSECodeJudge h -> h.setCarbonShifts(j.arr("carbonShifts").doubles());
                case seneca.judges.SymmetryJudge s -> {
                    for (JsonValue c : j.arr("classes").items()) {
                        boolean[] members = new boolean[n];
                        for (int m : c.asArray().ints()) members[m] = true;
                        s.addSymmetryClass(members);
                    }
                }
                case seneca.judges.FeatureJudge f -> {
                    for (int[] sizeScore : j.arr("ringSizeScores").intRows()) f.addRingSizeScore(sizeScore[0], sizeScore[1]);
                    f.setHetHetPenalty(j.integer("hetHetPenalty"));
                    f.setHetHetPenaltyEnabled(j.bool("hetHetPenaltyEnabled"));
                }
                case seneca.judges.HybridizationJudge h -> {
                    if (!j.isNull("scores")) h.scores = j.arr("scores").intRows();
                }
                case seneca.judges.HeteroBondsJudge h -> {
                    h.setScore(j.integer("score"));
                    if (!j.isNull("isHeteroBonded")) {
                        JArray a = j.arr("isHeteroBonded");
                        h.isHeteroBonded = new boolean[a.size()];
                        for (int i = 0; i < a.size(); i++) h.isHeteroBonded[i] = a.get(i).asBoolean();
                    }
                }
                default -> throw new IllegalArgumentException(judge.name);
            }
            judge.setEnabled(j.bool("enabled"));
            if (judge.getEnabled()) judge.calcMaxScore();
            judges.add(judge);
        }
        return judges;
    }

    static String describe(JudgeResult r) {
        return r.score + "|" + r.maxScore + "|" + r.propertyValue + "|" + r.scoreDescription;
    }

    static String describe(seneca.judges.JudgeResult r) {
        return r.score + "|" + r.maxScore + "|" + r.propertyValue + "|" + r.scoreDescription;
    }

    static List<Struct> structures(String key) {
        List<Struct> l = new ArrayList<>(OracleSupport.scoreStructures(key));
        l.addAll(OracleSupport.runStructures(key));
        Random rng = new Random(31L * key.hashCode() + 7);
        l.addAll(OracleSupport.randomWalk(l.get(10), 1000, rng, false));
        for (Struct c : OracleSupport.crashCandidates()) {
            if (c.name().startsWith(key + ".")) l.addAll(OracleSupport.randomWalk(c, 300, rng, false));
        }
        return l;
    }

    @TestFactory
    List<DynamicTest> judgesAgainstLegacy() {
        List<DynamicTest> t = new ArrayList<>();
        for (String key : GoldenFiles.keys()) {
            for (String profile : PROFILES) {
                t.add(DynamicTest.dynamicTest(key + " " + profile, () -> {
                    EchoGolden echo = EchoGolden.load(key);
                    Tally tally = new Tally();
                    // legacy
                    Vector<seneca.judges.Judge> lj = legacyJudges(echo, profile);
                    HeavyAtom[] atoms = GoldenFixtures.atoms(echo);
                    seneca.structgen.Attic la = new seneca.structgen.Attic();
                    Struct proto = OracleSupport.datasetStruct(key, "unbonded", new int[atoms.length][atoms.length]);
                    la.atomSet = proto.legacy();
                    la.normalized = proto.legacy();
                    for (seneca.judges.Judge j : lj) {
                        if (j.getEnabled()) {
                            j.setAttic(la);
                            j.init();
                        }
                    }
                    // port, and an independent deep copy of it
                    List<Judge> pj = GoldenFixtures.judges(echo, profile);
                    List<Judge> pc = JudgeSet.deepCopy(pj);
                    Attic pa = new Attic();
                    pa.atomSet = AtomSets.cloneAtomSet(atoms);
                    JudgeSet ps = new JudgeSet(pj);
                    ps.initJudges(pa);
                    Attic pca = new Attic();
                    pca.atomSet = AtomSets.cloneAtomSet(atoms);
                    JudgeSet pcs = new JudgeSet(pc);
                    pcs.initJudges(pca);
                    for (int i = 0; i < lj.size(); i++) {
                        tally.same("getMaxScore", lj.get(i).getMaxScore(), pj.get(i).getMaxScore(), lj.get(i).name);
                        tally.same("enabled", lj.get(i).getEnabled(), pj.get(i).getEnabled(), lj.get(i).name);
                    }
                    for (Struct s : structures(key)) {
                        DataStructureTools.synchronizeBonds(la.atomSet, s.bm());
                        AtomSets.synchronizeBonds(pa.atomSet, s.bm());
                        AtomSets.synchronizeBonds(pca.atomSet, s.bm());
                        long[] sum = new long[2];
                        for (int i = 0; i < lj.size(); i++) {
                            if (!lj.get(i).getEnabled()) continue;
                            seneca.judges.Judge l = lj.get(i);
                            Judge p = pj.get(i);
                            Judge c = pc.get(i);
                            String lr = OracleSupport.outcome(() -> {
                                seneca.judges.JudgeResult r = l.evaluate(s.bm());
                                sum[0] += r.score;
                                return describe(r);
                            });
                            String pr = OracleSupport.outcome(() -> {
                                JudgeResult r = p.evaluate(s.bm());
                                sum[1] += r.score;
                                return describe(r);
                            });
                            String cr = OracleSupport.outcome(() -> describe(c.evaluate(s.bm())));
                            tally.same(l.name, lr, pr, s.name());
                            tally.same(l.name + " (copy)", pr, cr, s.name());
                        }
                        tally.same("sum", sum[0], sum[1], s.name());
                    }
                    tally.assertNone();
                }));
            }
        }
        return t;
    }

    /** DEFAULT mode: identical results on every structure on which the legacy finder does not fail. */
    @TestFactory
    List<DynamicTest> defaultModeEqualsLegacyWhereLegacyWorks() {
        List<DynamicTest> t = new ArrayList<>();
        for (String key : GoldenFiles.keys()) {
            t.add(DynamicTest.dynamicTest(key, () -> {
                EchoGolden echo = EchoGolden.load(key);
                Tally tally = new Tally();
                HeavyAtom[] atoms = GoldenFixtures.atoms(echo);
                List<Judge> strict = GoldenFixtures.judges(echo, "gui");
                List<Judge> dflt = GoldenFixtures.judges(echo, "gui");
                Attic as = new Attic();
                as.atomSet = AtomSets.cloneAtomSet(atoms);
                Attic ad = new Attic();
                ad.atomSet = AtomSets.cloneAtomSet(atoms);
                ad.compat = LegacyCompat.DEFAULT;
                JudgeSet js = new JudgeSet(strict), jd = new JudgeSet(dflt);
                js.initJudges(as);
                jd.initJudges(ad);
                int failures = 0;
                for (Struct s : structures(key)) {
                    Atom[] la = s.legacy();
                    boolean legacyFails = RandomIsomerOracleTest.legacyRings(la).startsWith("java.");
                    String rs = OracleSupport.outcome(() -> {
                        JudgeSet.Breakdown b = js.scoreStructure(as, s.bm());
                        return b.raw + " " + b.description;
                    });
                    String rd = OracleSupport.outcome(() -> {
                        JudgeSet.Breakdown b = jd.scoreStructure(ad, s.bm());
                        return b.raw + " " + b.description;
                    });
                    if (legacyFails) {
                        failures++;
                        tally.check("STRICT fails where legacy fails", !rs.startsWith("java.lang.ArrayIndexOutOfBounds"), s.name() + ": " + rs);
                        tally.check("DEFAULT scores where legacy fails", rd.startsWith("java."), s.name() + ": " + rd);
                    } else {
                        tally.same("DEFAULT == STRICT", rs, rd, s.name());
                    }
                }
                tally.assertNone();
                System.out.println(key + ": " + failures + " structures on which the legacy ring finder fails");
            }));
        }
        return t;
    }
}
