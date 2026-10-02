package io.github.steinbeck.seneca.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.steinbeck.seneca.golden.GoldenFiles;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The command-line interface: exit codes, golden-identical output of {@code run}/{@code record}, {@code generate}. */
@Tag("C")
class MainTest {

    record Out(int rc, String out, String err) {
    }

    static Out main(String... args) {
        ByteArrayOutputStream o = new ByteArrayOutputStream(), e = new ByteArrayOutputStream();
        int rc = Main.run(args, new PrintStream(o, true, StandardCharsets.UTF_8), new PrintStream(e, true, StandardCharsets.UTF_8));
        return new Out(rc, o.toString(StandardCharsets.UTF_8), e.toString(StandardCharsets.UTF_8));
    }

    @Test
    void usage() {
        assertEquals(2, main().rc());
        assertEquals(2, main("nonsense").rc());
        assertEquals(2, main("run", "x.sml").rc());
        assertEquals(4, main("echo", "/nonexistent/file.sml", "x").rc());
    }

    @Test
    void jobListIsTheGoldenLayout() {
        List<String> keys = new ArrayList<>(GoldenFiles.keys());
        List<Main.Job> jobs = Main.jobs(List.of("echo", "scores", "ref", "traj", "run"), keys);
        long runs = jobs.stream().filter(j -> j.stage().equals("run")).count();
        long trajs = jobs.stream().filter(j -> j.stage().equals("traj")).count();
        assertEquals(GoldenFiles.runs().size(), runs);
        assertEquals(GoldenFiles.trajectories().size(), trajs);
        assertEquals(3 * keys.size() + runs + trajs, jobs.size());
        assertEquals("run", jobs.get(0).stage(), "complete runs first");
        for (GoldenFiles.RunId id : GoldenFiles.runs())
            assertTrue(jobs.stream().anyMatch(j -> j.stage().equals("run") && j.key().equals(id.key())
                    && j.profile().key.equals(id.profile()) && j.seed() == id.seed()), id.toString());
    }

    @Test
    void runAndRecordReproduceGolden(@TempDir Path tmp) throws Exception {
        Path refs = GoldenFiles.referencesTsv();
        Out r = main("run", GoldenFiles.sml("pinene").toString(), "pinene", "paper", "1", "--references", refs.toString());
        assertEquals(0, r.rc(), r.err());
        assertEquals(Files.readString(GoldenFiles.golden().resolve("runs/pinene.paper.seed1.json")), r.out());

        Out rec = main("record", GoldenFiles.smlDir().toString(), tmp.toString(), "--datasets", "pinene", "--stages", "echo,traj",
                "--references", refs.toString(), "--jobs", "4");
        assertEquals(0, rec.rc(), rec.err());
        assertArrayEquals(Files.readAllBytes(GoldenFiles.golden().resolve("datasets/pinene.echo.jsonl")),
                Files.readAllBytes(tmp.resolve("datasets/pinene.echo.jsonl")));
        for (int s = 1; s <= 10; s++) {
            String name = "trajectories/pinene.gui.seed" + s + ".jsonl.gz";
            assertArrayEquals(gunzip(GoldenFiles.golden().resolve(name)), gunzip(tmp.resolve(name)), name);
        }
    }

    static byte[] gunzip(Path p) throws Exception {
        try (GZIPInputStream in = new GZIPInputStream(Files.newInputStream(p))) {
            return in.readAllBytes();
        }
    }

    @Test
    void generate(@TempDir Path tmp) throws Exception {
        Path sdf = tmp.resolve("results.sdf");
        Out g = main("generate", GoldenFiles.sml("pinene").toString(), "--profile", "paper", "--seed", "1", "-o", sdf.toString());
        assertEquals(0, g.rc(), g.err());
        assertTrue(g.out().contains("finished (max_score_reached) after 21101 iterations"), g.out());
        assertTrue(g.out().contains("best      2400/2400"), g.out());
        assertTrue(Files.readString(sdf).startsWith("Score: 2400\n"));
        assertEquals(22, Files.readString(sdf).split("\\$\\$\\$\\$\n").length);
    }
}
