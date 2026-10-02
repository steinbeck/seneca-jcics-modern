package io.github.steinbeck.seneca.cli;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

import org.openscience.cdk.smiles.SmiFlavor;
import org.openscience.cdk.smiles.SmilesGenerator;

import io.github.steinbeck.seneca.LegacyCompat;
import io.github.steinbeck.seneca.chem.AtomSets;
import io.github.steinbeck.seneca.chem.CdkBridge;
import io.github.steinbeck.seneca.chem.HeavyAtom;
import io.github.steinbeck.seneca.chem.LegacyMdlReader;
import io.github.steinbeck.seneca.chem.MolecularFormula;
import io.github.steinbeck.seneca.config.GuiRecipe;
import io.github.steinbeck.seneca.config.Profile;
import io.github.steinbeck.seneca.golden.EchoRecords;
import io.github.steinbeck.seneca.golden.Json;
import io.github.steinbeck.seneca.golden.ReferenceRecords;
import io.github.steinbeck.seneca.golden.RunRecords;
import io.github.steinbeck.seneca.golden.ScoreRecords;
import io.github.steinbeck.seneca.golden.TrajectoryRecords;
import io.github.steinbeck.seneca.judges.JudgeSet;
import io.github.steinbeck.seneca.judges.ScoreSummary;
import io.github.steinbeck.seneca.model.SenecaDataset;
import io.github.steinbeck.seneca.structgen.EngineLog;
import io.github.steinbeck.seneca.structgen.RunInput;
import io.github.steinbeck.seneca.structgen.RunOptions;
import io.github.steinbeck.seneca.structgen.RunResult;
import io.github.steinbeck.seneca.structgen.StochasticGenerator;
import io.github.steinbeck.seneca.structgen.StructureGeneratorResult;

/**
 * Command-line entry point. Replays the golden GUI recipe for a dataset + profile and emits the same
 * JSON shapes as the legacy harness ({@code legacy/harness/src/seneca/structgen/GoldenMaster.java},
 * golden/FORMAT.md), so that a complete port recording can be compared with
 * {@code legacy/harness/compare.sh golden <dir>}. Design: docs/port/design.md section 10.
 *
 * <pre>
 * seneca echo   &lt;file.sml&gt; &lt;key&gt; [-o out.jsonl]
 * seneca scores &lt;file.sml&gt; &lt;key&gt; [-o out.jsonl]
 * seneca ref    &lt;file.sml&gt; &lt;key&gt; [--references tsv] [-o out.jsonl]
 * seneca traj   &lt;file.sml&gt; &lt;key&gt; &lt;profile&gt; &lt;seed&gt; [--steps 5000] [--compat strict|default] [-o out.jsonl]
 * seneca run    &lt;file.sml&gt; &lt;key&gt; &lt;profile&gt; &lt;seed&gt; [--cap 5000000] [--references tsv] [--compat ...] [-o out.json]
 * seneca record &lt;smlDir&gt; &lt;outDir&gt; [--stages echo,scores,ref,traj,run] [--datasets k1,k2] [--jobs n]
 *               [--references tsv] [--cap n] [--steps n] [--compat ...] [--manifest summarize.py]
 * seneca generate &lt;file.sml&gt; [--key k] [--profile gui] [--seed s] [--compat default] [--cap n] [--verbose] [-o results.sdf]
 * </pre>
 * {@code key} is the dataset key of golden/ (file base name, or a variant such as
 * {@code polycarpol@hblock}); {@code profile} is gui|paper|all|manual. Default compat for the golden
 * commands is STRICT, for {@code generate} DEFAULT. Output goes to stdout unless {@code -o} is given;
 * records end with "\n". {@code record} writes the golden layout (datasets/, scores/, references/,
 * trajectories/*.jsonl.gz, runs/ with timing.jsonl) with the same job list as
 * {@code legacy/harness/run.sh}; jobs run in parallel threads (no shared state). Trajectories are
 * compressed with the system's {@code gzip -n -9} when available (byte-identical container), else with
 * {@link GZIPOutputStream} (same content; compare.sh reports "gzip container differs, content identical").
 * {@code MANIFEST.jsonl} is written only with {@code --manifest} (path of the harness's summarize.py);
 * {@code PROVENANCE.json} describes the legacy inputs and is not produced.
 * Exit codes: 0 ok, 1 a record job failed, 2 usage, 4 I/O error (3 was "not implemented" while the port was incomplete; no longer used).
 */
public final class Main {

    /** {@code legacy/harness/run.sh} defaults. */
    static final String[] DEFAULT_STAGES = {"echo", "scores", "ref", "traj", "run"};
    static final String[] VARIANTS = {"polycarpol@hblock"};
    static final long[] SEEDS = {1, 2, 3};
    static final long[] EXTRA_SEEDS = {4, 5, 6, 7, 8, 9, 10};
    static final Set<String> EXTRA_TRAJ = Set.of("pinene", "eurabidiol");
    static final Set<String> MANUAL_FULL = Set.of("pinene", "preconen", "eurabidiol");
    static final Profile[] TRAJ_PROFILES = {Profile.GUI, Profile.PAPER};
    static final Profile[] FULL_PROFILES = {Profile.GUI, Profile.PAPER};
    static final Profile[] VARIANT_PROFILES = {Profile.PAPER};

    private Main() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    /** Testable entry point. */
    public static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 0) {
            err.println("usage: seneca echo|scores|ref|traj|run|record|generate ... (see Main javadoc)");
            return 2;
        }
        Args a = new Args(args);
        try {
            List<String> lines = new ArrayList<>();
            switch (a.command) {
                case "echo" -> lines.addAll(EchoRecords.echo(a.path(0), a.pos(1)));
                case "scores" -> lines.addAll(ScoreRecords.scores(a.path(0), a.pos(1)));
                case "ref" -> lines.addAll(ReferenceRecords.references(a.path(0), a.pos(1), a.references()));
                case "traj" -> lines.addAll(TrajectoryRecords.trajectory(a.path(0), a.pos(1), Profile.byKey(a.pos(2)),
                        Long.parseLong(a.pos(3)), Integer.parseInt(a.opt("--steps", "5000")), a.compat("strict")));
                case "run" -> lines.add(RunRecords.run(a.path(0), a.pos(1), Profile.byKey(a.pos(2)), Long.parseLong(a.pos(3)),
                        Long.parseLong(a.opt("--cap", "5000000")), a.references(), a.compat("strict")));
                case "record" -> {
                    return record(a, out, err);
                }
                case "generate" -> {
                    return generate(a, out, err);
                }
                default -> {
                    err.println("unknown command " + a.command);
                    return 2;
                }
            }
            String o = a.opt("-o", null);
            StringBuilder sb = new StringBuilder();
            for (String l : lines) sb.append(l).append('\n');
            if (o == null) out.print(sb);
            else Files.writeString(Path.of(o), sb, StandardCharsets.UTF_8);
            return 0;
        } catch (IOException e) {
            err.println("I/O error: " + e.getMessage());
            return 4;
        } catch (UncheckedIOException e) {
            err.println("I/O error: " + e.getCause().getMessage());
            return 4;
        } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
            err.println("usage error: " + e.getMessage());
            return 2;
        }
    }

    // ------------------------------------------------------------------ record

    /** One job of {@code legacy/harness/run.sh}: stage, dataset key, seed, profile. */
    record Job(String stage, String key, long seed, Profile profile) {
        @Override
        public String toString() {
            return stage + " " + key + (profile == null ? "" : " " + profile.key + " seed " + seed);
        }
    }

    /** The job list of run.sh for the given stages and keys (complete runs first: they are the long ones). */
    static List<Job> jobs(List<String> stages, List<String> keys) {
        List<Job> runs = new ArrayList<>(), rest = new ArrayList<>();
        for (String st : stages) {
            for (String k : keys) {
                boolean variant = k.contains("@");
                switch (st) {
                    case "echo", "scores", "ref" -> rest.add(new Job(st, k, 0, null));
                    case "traj" -> {
                        if (variant) continue;
                        List<Long> seeds = new ArrayList<>();
                        for (long s : SEEDS) seeds.add(s);
                        if (EXTRA_TRAJ.contains(k)) for (long s : EXTRA_SEEDS) seeds.add(s);
                        for (Profile p : TRAJ_PROFILES) for (long s : seeds) rest.add(new Job(st, k, s, p));
                    }
                    case "run" -> {
                        List<Profile> profs = new ArrayList<>(Arrays.asList(variant ? VARIANT_PROFILES : FULL_PROFILES));
                        if (!variant && MANUAL_FULL.contains(k)) profs.add(Profile.MANUAL);
                        for (Profile p : profs) for (long s : SEEDS) runs.add(new Job(st, k, s, p));
                    }
                    default -> throw new IllegalArgumentException("unknown stage " + st + " (echo, scores, ref, traj, run)");
                }
            }
        }
        runs.addAll(rest);
        return runs;
    }

    static int record(Args a, PrintStream out, PrintStream err) throws IOException {
        Path smlDir = a.path(0);
        Path outDir = a.path(1);
        List<String> stages = new ArrayList<>();
        for (String s : a.opt("--stages", String.join(",", DEFAULT_STAGES)).split(",")) stages.add(s.equals("full") ? "run" : s);
        List<String> keys = new ArrayList<>();
        if (a.options.containsKey("--datasets")) {
            keys.addAll(Arrays.asList(a.opt("--datasets", "").split(",")));
        } else {
            try (Stream<Path> files = Files.list(smlDir)) {
                files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".sml"))
                        .map(n -> n.substring(0, n.length() - 4)).sorted().forEach(keys::add);
            }
            for (String v : VARIANTS) if (keys.contains(GuiRecipe.baseKey(v))) keys.add(v);
        }
        Path refs = a.references();
        long cap = Long.parseLong(a.opt("--cap", "5000000"));
        int steps = Integer.parseInt(a.opt("--steps", "5000"));
        LegacyCompat compat = a.compat("strict");
        int nJobs = Integer.parseInt(a.opt("--jobs", String.valueOf(Runtime.getRuntime().availableProcessors())));
        // a stage that is re-run replaces its whole output directory (no stale files), as run.sh
        for (String st : stages) deleteTree(outDir.resolve(stageDir(st)));
        for (String d : new String[]{"datasets", "scores", "references", "trajectories", "runs"}) Files.createDirectories(outDir.resolve(d));
        List<Job> jobs = jobs(stages, keys);
        List<String> timing = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, nJobs), r -> {
            Thread t = new Thread(r, "seneca-record");
            t.setDaemon(true);
            return t;
        });
        int failed = 0;
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Job j : jobs) futures.add(pool.submit(() -> {
                runJob(j, smlDir, outDir, refs, cap, steps, compat, timing);
                synchronized (out) {
                    out.println(j + " ok");
                }
                return null;
            }));
            for (int i = 0; i < futures.size(); i++) {
                try {
                    futures.get(i).get();
                } catch (ExecutionException e) {
                    failed++;
                    synchronized (out) {
                        err.println(jobs.get(i) + " FAILED: " + e.getCause());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return 1;
                }
            }
        } finally {
            pool.shutdownNow();
        }
        if (stages.contains("run")) {
            List<String> sorted = new ArrayList<>(timing);
            Collections.sort(sorted);
            StringBuilder sb = new StringBuilder();
            for (String l : sorted) sb.append(l).append('\n');
            Files.writeString(outDir.resolve("runs/timing.jsonl"), sb, StandardCharsets.UTF_8);
        }
        String manifest = a.opt("--manifest", null);
        if (manifest != null) {
            try {
                Process p = new ProcessBuilder("python3", manifest, outDir.toString())
                        .redirectOutput(outDir.resolve("MANIFEST.jsonl").toFile()).redirectErrorStream(false).start();
                if (p.waitFor() != 0) {
                    err.println("summarize.py failed");
                    failed++;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return 1;
            }
        }
        out.println(jobs.size() + " jobs, " + failed + " failed");
        return failed == 0 ? 0 : 1;
    }

    private static String stageDir(String stage) {
        return switch (stage) {
            case "echo" -> "datasets";
            case "scores" -> "scores";
            case "ref" -> "references";
            case "traj" -> "trajectories";
            case "run" -> "runs";
            default -> throw new IllegalArgumentException("unknown stage " + stage + " (echo, scores, ref, traj, run)");
        };
    }

    private static void runJob(Job j, Path smlDir, Path outDir, Path refs, long cap, int steps, LegacyCompat compat,
                               List<String> timing) throws IOException {
        Path sml = smlDir.resolve(GuiRecipe.baseKey(j.key()) + ".sml");
        switch (j.stage()) {
            case "echo" -> writeLines(outDir.resolve("datasets/" + j.key() + ".echo.jsonl"), EchoRecords.echo(sml, j.key()));
            case "scores" -> writeLines(outDir.resolve("scores/" + j.key() + ".scores.jsonl"), ScoreRecords.scores(sml, j.key()));
            case "ref" -> writeLines(outDir.resolve("references/" + j.key() + ".ref.jsonl"), ReferenceRecords.references(sml, j.key(), refs));
            case "traj" -> {
                Path t = outDir.resolve("trajectories/" + j.key() + "." + j.profile().key + ".seed" + j.seed() + ".jsonl");
                writeLines(t, TrajectoryRecords.trajectory(sml, j.key(), j.profile(), j.seed(), steps, compat));
                gzip(t);
            }
            case "run" -> {
                long t0 = System.nanoTime();
                RunRecords.Outcome o = RunRecords.outcome(sml, j.key(), j.profile(), j.seed(), cap, refs, compat);
                long wallMs = (System.nanoTime() - t0) / 1_000_000L;
                writeLines(outDir.resolve("runs/" + j.key() + "." + j.profile().key + ".seed" + j.seed() + ".json"), List.of(o.line()));
                timing.add(Json.obj().put("key", j.key()).put("profile", j.profile().key).put("seed", j.seed())
                        .put("status", o.result().goldenStatus()).put("wallMs", wallMs).put("iterations", o.result().iterations())
                        .put("iterationCap", cap).put("wallCapSeconds", 0L)
                        .put("java", System.getProperty("java.version")).toString());
            }
            default -> throw new IllegalArgumentException(j.stage());
        }
    }

    private static void writeLines(Path p, List<String> lines) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String l : lines) sb.append(l).append('\n');
        Files.writeString(p, sb, StandardCharsets.UTF_8);
    }

    /** {@code gzip -n -9 -f file} (run.sh), or the JDK's GZIP stream at level 9 if no gzip is installed. */
    static void gzip(Path file) throws IOException {
        Path gz = file.resolveSibling(file.getFileName() + ".gz");
        try {
            Process p = new ProcessBuilder("gzip", "-n", "-9", "-f", file.toString()).redirectErrorStream(true).start();
            p.getInputStream().transferTo(OutputStream.nullOutputStream());
            if (p.waitFor() == 0 && Files.exists(gz) && !Files.exists(file)) return;
        } catch (IOException e) {
            // no gzip on this system: fall through
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while compressing " + file, e);
        }
        try (OutputStream o = new GZIPOutputStream(Files.newOutputStream(gz)) {
            {
                def.setLevel(Deflater.BEST_COMPRESSION);
            }
        }) {
            Files.copy(file, o);
        }
        Files.delete(file);
    }

    private static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> s = Files.walk(dir)) {
            List<Path> all = s.sorted(Collections.reverseOrder()).toList();
            for (Path p : all) Files.delete(p);
        }
    }

    // ------------------------------------------------------------------ generate

    /**
     * A human-readable structure generation: the GUI recipe of the profile, "Start", one run on the
     * calling thread; progress (INFO log) on stderr with {@code --verbose}; summary and result list on
     * stdout; with {@code -o}, the result structures as SD file (legacy MDL text, "$$$$" separated).
     */
    static int generate(Args a, PrintStream out, PrintStream err) throws IOException {
        Path sml = a.path(0);
        String file = sml.getFileName().toString();
        String key = a.opt("--key", file.endsWith(".sml") ? file.substring(0, file.length() - 4) : file);
        Profile profile = Profile.byKey(a.opt("--profile", "gui"));
        LegacyCompat compat = a.compat("default");
        String seedText = a.opt("--seed", null);
        Long seed = seedText == null ? null : Long.valueOf(seedText);
        long cap = Long.parseLong(a.opt("--cap", "0"));
        boolean verbose = a.flags.contains("--verbose");
        GuiRecipe.Prepared p = GuiRecipe.prepare(sml, key, profile, compat);
        SenecaDataset sd = p.sd;
        RunInput in = RunInput.fromDataset(sd, compat);
        JudgeSet js = new JudgeSet(new ArrayList<>(in.judges()));
        out.println("SENECA structure generation (" + StochasticGenerator.VERSION + ")");
        out.println("dataset   " + sd.getName() + " (" + key + "), " + MolecularFormula.formulaOf(in.atoms()) + ", "
                + in.atoms().length + " heavy atoms");
        out.println("profile   " + profile.key + ": " + String.join(", ", js.enabledNames()) + "; max score " + js.maxScore());
        out.println("random    " + (seed == null ? "unseeded" : "seed " + seed) + ", compat " + compat
                + (cap > 0 ? ", iteration cap " + cap : ""));
        EngineLog log = !verbose ? EngineLog.NONE : new EngineLog() {
            @Override
            public void info(String message) {
                err.print(message.endsWith("\n") ? message : message + "\n");
            }

            @Override
            public void result(String sdfBlock) {
            }
        };
        StochasticGenerator g;
        try {
            g = new StochasticGenerator(in, new RunOptions(seed, compat, cap, null, log));
        } catch (IllegalArgumentException e) {
            err.println("cannot start: " + e.getMessage());
            return 2;
        }
        long t0 = System.nanoTime();
        RunResult r = g.run();
        double secs = (System.nanoTime() - t0) / 1e9;
        out.println("result    " + r.goldenStatus() + " (" + r.goldenTermination() + ") after " + r.iterations()
                + " iterations, " + String.format(Locale.ROOT, "%.1f", secs) + " s");
        if (r.exception() != null) out.println("exception " + r.exception());
        StructureGeneratorResult res = r.result();
        if (res.size() > 0) out.println("best      " + r.bestScore().score + "/" + r.maxScore() + "  " + smiles(r.bestStructure()));
        out.println("results   " + res.size() + " (newest first)");
        for (int i = 0; i < res.size(); i++) {
            ScoreSummary ss = res.scoreSummaries.get(i);
            out.println(String.format(Locale.ROOT, "%3d %7d  %s", i, ss.score, smiles(res.structures.get(i))));
            if (verbose) out.println("      " + ss.description.replaceAll("<[^>]*>", "").replace("\n", "\n      ").trim());
        }
        String o = a.opt("-o", null);
        if (o != null) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < res.size(); i++) sb.append(res.structures.get(i)).append("\n$$$$\n");
            Files.writeString(Path.of(o), sb, StandardCharsets.UTF_8);
        }
        return 0;
    }

    /** Canonical SMILES of a stored structure (CDK), for display only. */
    static String smiles(String mdl) {
        try {
            HeavyAtom[] atoms = LegacyMdlReader.read(mdl);
            int[][] bm = AtomSets.createConnectionTable(atoms);
            AtomSets.synchronizeConnectionTable(bm);
            return new SmilesGenerator(SmiFlavor.Canonical).create(CdkBridge.toAtomContainer(atoms, bm));
        } catch (Exception e) {
            return "(no SMILES: " + e.getMessage() + ")";
        }
    }

    // ------------------------------------------------------------------ arguments

    /** Minimal argument holder: command, positionals, "--name value" / "-o value" options, "--verbose" flag. */
    static final class Args {
        static final Set<String> FLAGS = Set.of("--verbose");
        final String command;
        final List<String> positional = new ArrayList<>();
        final java.util.Map<String, String> options = new java.util.HashMap<>();
        final Set<String> flags = new LinkedHashSet<>();

        Args(String[] args) {
            command = args[0];
            for (int i = 1; i < args.length; i++) {
                if (FLAGS.contains(args[i])) flags.add(args[i]);
                else if (args[i].startsWith("-") && i + 1 < args.length) options.put(args[i], args[++i]);
                else positional.add(args[i]);
            }
        }

        String pos(int i) {
            return positional.get(i);
        }

        Path path(int i) {
            return Path.of(positional.get(i));
        }

        String opt(String name, String def) {
            return options.getOrDefault(name, def);
        }

        Path references() {
            return Path.of(opt("--references", "legacy/harness/references.tsv"));
        }

        LegacyCompat compat(String def) {
            return LegacyCompat.valueOf(opt("--compat", def).toUpperCase(Locale.ROOT));
        }
    }
}
