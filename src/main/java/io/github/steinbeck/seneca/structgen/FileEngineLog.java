package io.github.steinbeck.seneca.structgen;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * {@link EngineLog} that appends to files, as the legacy log4j configuration did ({@code ~/seneca.log}
 * for the INFO messages, {@code ~/seneca.results.sdf} for every new best structure; feature-inventory
 * F-06, 7.7). Either path may be null (that part is discarded). Thread-safe; several runs may share one
 * instance. Every message is flushed. An I/O error does not reach the run (log4j swallowed them too;
 * a logging problem must not end a structure generation): the first one is kept in {@link #error()} and
 * writing to that file stops. {@link #close()} releases the files.
 */
public final class FileEngineLog implements EngineLog, AutoCloseable {

    private BufferedWriter log;
    private BufferedWriter results;
    private IOException error;

    public FileEngineLog(Path logFile, Path resultsSdf) throws IOException {
        this.log = open(logFile);
        this.results = open(resultsSdf);
    }

    /** {@code ~/seneca.log} and {@code ~/seneca.results.sdf}, the legacy locations. */
    public static FileEngineLog inUserHome() throws IOException {
        Path home = Path.of(System.getProperty("user.home"));
        return new FileEngineLog(home.resolve("seneca.log"), home.resolve("seneca.results.sdf"));
    }

    private static BufferedWriter open(Path p) throws IOException {
        if (p == null) return null;
        return Files.newBufferedWriter(p, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    @Override
    public synchronized void info(String message) {
        if (!write(log, message.endsWith("\n") ? message : message + "\n")) log = null;
    }

    @Override
    public synchronized void result(String sdfBlock) {
        if (!write(results, sdfBlock)) results = null;
    }

    /** The first I/O error, or null. */
    public synchronized IOException error() {
        return error;
    }

    private boolean write(BufferedWriter w, String s) {
        if (w == null) return true;
        try {
            w.write(s);
            w.flush();
            return true;
        } catch (IOException e) {
            if (error == null) error = e;
            return false;
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (log != null) log.close();
        if (results != null) results.close();
    }
}
