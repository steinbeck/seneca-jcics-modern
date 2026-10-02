package io.github.steinbeck.seneca.golden;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.config.GuiRecipe;
import io.github.steinbeck.seneca.config.Profile;
import io.github.steinbeck.seneca.judges.JudgeSet;
import io.github.steinbeck.seneca.structgen.RunInput;

/**
 * Emits golden/references/&lt;key&gt;.ref.jsonl (golden/FORMAT.md (e)): the literature structure and,
 * if it is mappable onto the configured dataset, its best labelling per profile gui, paper, all, manual.
 * Port of {@code GoldenMaster.ref} and {@code GoldenMaster.searchLabelling}: simulated annealing on swaps
 * within an (element, H) class ({@code java.util.Random(2001)}, 8 restarts x 4000 steps per Kekulé
 * variant), then steepest ascent. Harness code, transcribed so that a port recording reproduces the
 * golden files byte for byte (same random calls, same {@code Math.exp}/{@code Math.pow}).
 */
public final class ReferenceRecords {

    public static final long SEARCH_SEED = 2001L;
    public static final int RESTARTS = 8;
    public static final int STEPS = 4000;

    /** {@code GoldenMaster.REF_PROFILES}. */
    public static final List<Profile> PROFILES = List.of(Profile.GUI, Profile.PAPER, Profile.ALL, Profile.MANUAL);

    private ReferenceRecords() {
    }

    /** All lines of the reference file for {@code smlFile} / {@code key} (GUI recipe, STRICT). */
    public static List<String> references(Path smlFile, String key, Path referencesTsv) throws IOException {
        List<String> out = new ArrayList<>();
        ReferenceCheck.Ref rf = ReferenceCheck.load(referencesTsv).get(GuiRecipe.baseKey(key));
        Json head = Json.obj().put("t", "reference").put("key", key);
        if (rf == null) {
            out.add(head.put("defined", false).put("note", "no reference structure: compound not identified").toString());
            return out;
        }
        ReferenceCheck.Graph g = ReferenceCheck.parseSmiles(rf.smiles());
        HeavyAtom[] at = GuiRecipe.prepare(smlFile, key, Profile.GUI).sd.setOfNodes;
        String[] el = new String[at.length];
        int[] h = new int[at.length];
        for (int f = 0; f < at.length; f++) {
            el[f] = at[f].symbol;
            h[f] = at[f].hCount;
        }
        List<String> rc = ReferenceCheck.classes(g.el(), g.h()), dc = ReferenceCheck.classes(el, h);
        boolean mappable = rc.equals(dc);
        List<int[][]> variants = ReferenceCheck.kekuleVariants(g);
        Json refAtoms = Json.arr();
        for (int a = 0; a < g.n(); a++) refAtoms.add(Json.obj().put("i", a).put("el", g.el()[a]).put("h", g.h()[a]));
        head.put("defined", true).put("name", rf.name()).put("status", rf.status()).put("source", rf.source())
                .put("smiles", rf.smiles()).put("nAtoms", g.n()).put("refAtoms", refAtoms).put("refCt", Hashes.ct(g.bm()))
                .put("kekuleVariants", variants.size()).put("mappable", mappable)
                .put("classDifference", ReferenceCheck.classDiff(rc, dc));
        out.add(head.toString());
        if (!mappable) return out;
        for (Profile profile : PROFILES) {
            GuiRecipe.Prepared p = GuiRecipe.prepare(smlFile, key, profile);
            RunInput go = RunInput.fromDataset(p.sd, LegacyCompat.STRICT);
            ScoreRecords.Scorer scorer = new ScoreRecords.Scorer(go.atoms(), new ArrayList<>(go.judges()), LegacyCompat.STRICT);
            long max = scorer.judges.maxScore();
            Search best = searchLabelling(g, variants, el, h, scorer);
            int[][] bm = ReferenceCheck.place(variants.get(best.variant), best.map);
            JudgeSet.Breakdown sc = scorer.score(bm);
            if (sc.raw != best.raw) throw new IllegalStateException("labelling search rescore mismatch");
            out.add(Json.obj().put("t", "best").put("profile", profile.key)
                    .put("enabled", Json.strings(GuiRecipe.enabledJudges(p.sd)))
                    .put("maxScore", max).put("total", sc.total).put("raw", sc.raw).put("atMax", sc.total >= max)
                    .put("kekule", best.variant).put("map", Json.ints(best.map)).put("hash", Hashes.hash(bm)).put("ct", Hashes.ct(bm))
                    .put("judges", ScoreRecords.breakdown(sc))
                    .put("search", Json.obj().put("seed", SEARCH_SEED).put("restarts", RESTARTS)
                            .put("stepsPerRestart", STEPS).put("evaluations", best.evaluations)
                            .put("exceptions", best.exceptions).put("firstException", best.firstException)).toString());
        }
        return out;
    }

    static final class Search {
        int variant;
        int[] map;
        long raw = Long.MIN_VALUE;
        long evaluations, exceptions;
        String firstException;
    }

    /** {@code GoldenMaster.searchLabelling}, statement by statement. */
    static Search searchLabelling(ReferenceCheck.Graph g, List<int[][]> variants, String[] el, int[] h, ScoreRecords.Scorer scorer) {
        int n = g.n();
        Map<String, List<Integer>> dataByClass = new TreeMap<>();
        for (int i = 0; i < n; i++) dataByClass.computeIfAbsent(ReferenceCheck.cls(el[i], h[i]), k -> new ArrayList<>()).add(i);
        Map<String, List<Integer>> refByClass = new TreeMap<>();
        for (int a = 0; a < n; a++) refByClass.computeIfAbsent(ReferenceCheck.cls(g.el()[a], g.h()[a]), k -> new ArrayList<>()).add(a);
        List<int[]> swapPairs = new ArrayList<>();
        for (List<Integer> l : refByClass.values())
            for (int x = 0; x < l.size(); x++)
                for (int y = x + 1; y < l.size(); y++) swapPairs.add(new int[]{l.get(x), l.get(y)});
        Random rnd = new Random(SEARCH_SEED);
        Search best = new Search();
        for (int v = 0; v < variants.size(); v++) {
            int[][] refBm = variants.get(v);
            for (int r = 0; r < RESTARTS; r++) {
                int[] map = new int[n];
                for (Map.Entry<String, List<Integer>> e : refByClass.entrySet()) {
                    List<Integer> targets = new ArrayList<>(dataByClass.get(e.getKey()));
                    Collections.shuffle(targets, rnd);
                    for (int x = 0; x < e.getValue().size(); x++) map[e.getValue().get(x)] = targets.get(x);
                }
                long cur = eval(refBm, map, scorer, best);
                consider(best, v, map, cur);
                if (swapPairs.isEmpty()) continue;
                double t = 300.0, alpha = Math.pow(1.0 / 300.0, 1.0 / STEPS);
                for (int s = 0; s < STEPS; s++, t *= alpha) {
                    int[] pr = swapPairs.get(rnd.nextInt(swapPairs.size()));
                    swap(map, pr);
                    long sc = eval(refBm, map, scorer, best);
                    if (sc >= cur || (sc != Long.MIN_VALUE && rnd.nextDouble() < Math.exp((sc - cur) / t))) {
                        cur = sc;
                        consider(best, v, map, cur);
                    } else {
                        swap(map, pr);
                    }
                }
            }
        }
        int[] map = best.map.clone();
        int[][] refBm = variants.get(best.variant);
        long cur = best.raw;
        boolean improved = true;
        while (improved && !swapPairs.isEmpty()) {
            improved = false;
            int[] bestPair = null;
            long bestSc = cur;
            for (int[] pr : swapPairs) {
                swap(map, pr);
                long sc = eval(refBm, map, scorer, best);
                swap(map, pr);
                if (sc > bestSc) {
                    bestSc = sc;
                    bestPair = pr;
                }
            }
            if (bestPair != null) {
                swap(map, bestPair);
                cur = bestSc;
                improved = true;
                consider(best, best.variant, map, cur);
            }
        }
        return best;
    }

    private static void swap(int[] map, int[] pr) {
        int x = map[pr[0]];
        map[pr[0]] = map[pr[1]];
        map[pr[1]] = x;
    }

    private static void consider(Search best, int v, int[] map, long sc) {
        if (sc > best.raw) {
            best.raw = sc;
            best.variant = v;
            best.map = map.clone();
        }
    }

    private static long eval(int[][] refBm, int[] map, ScoreRecords.Scorer scorer, Search stats) {
        stats.evaluations++;
        try {
            return scorer.score(ReferenceCheck.place(refBm, map)).raw;
        } catch (RuntimeException e) {
            stats.exceptions++;
            if (stats.firstException == null) stats.firstException = e.getClass().getName() + ": " + e.getMessage();
            return Long.MIN_VALUE;
        }
    }
}
