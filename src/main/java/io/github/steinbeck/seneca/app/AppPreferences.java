package io.github.steinbeck.seneca.app;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Port of {@code SenecaParameters}/{@code Parameters} (feature-inventory 7.8): a {@link Properties} file
 * with the header "Seneca Properties" that remembers the last directories of the SML and LUCY file
 * dialogs. Location: {@link AppPaths#preferencesFile()}. The legacy keys are kept
 * ({@code filelocation.seneca}, {@code filelocation.lucy}; the unused {@code filelocation.logfile} and
 * {@code filelocation.resultfile} are not written). Default directory: the user's home directory
 * (legacy "/"); for the SML dialogs the example datasets' directory if there is one
 * ({@link #setDefaultSenecaLocation}, DV-87). A remembered directory that no longer exists counts as not
 * remembered. A missing or unreadable file means defaults; a write error is logged and ignored.
 */
public final class AppPreferences {

    private static final Logger LOG = Logger.getLogger(AppPreferences.class.getName());

    public static final String SENECA_LOCATION = "filelocation.seneca";
    public static final String LUCY_LOCATION = "filelocation.lucy";

    private final Path file;
    private final Properties properties = new Properties();
    private Path defaultSenecaLocation;

    public AppPreferences(Path file) {
        this.file = file;
        if (file != null && Files.isReadable(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                properties.load(in);
            } catch (IOException e) {
                LOG.warning("Seneca: Can't read properties file " + file + ". Using defaults.");
            }
        }
    }

    /** Last directory of an SML open/save dialog; before the first one the default (see the class comment). */
    public synchronized Path senecaLocation() {
        if (remembered(SENECA_LOCATION) == null && defaultSenecaLocation != null) return defaultSenecaLocation;
        return location(SENECA_LOCATION);
    }

    /** Directory of the SML dialogs while none has been remembered (null: the home directory). */
    public synchronized void setDefaultSenecaLocation(Path dir) {
        this.defaultSenecaLocation = dir;
    }

    public void setSenecaLocation(Path dir) {
        set(SENECA_LOCATION, dir);
    }

    /** Last directory of the LUCY import dialog. */
    public Path lucyLocation() {
        return location(LUCY_LOCATION);
    }

    public void setLucyLocation(Path dir) {
        set(LUCY_LOCATION, dir);
    }

    private synchronized Path location(String key) {
        Path p = remembered(key);
        return p == null ? Path.of(System.getProperty("user.home")) : p;
    }

    /** The directory stored under {@code key}, or null if none is stored or it is no longer a directory. */
    private Path remembered(String key) {
        String s = properties.getProperty(key);
        if (s == null) return null;
        try {
            Path p = Path.of(s);
            return Files.isDirectory(p) ? p : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private synchronized void set(String key, Path dir) {
        if (dir == null) return;
        properties.setProperty(key, dir.toAbsolutePath().toString());
        if (file == null) return;
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                properties.store(out, "Seneca Properties");
            }
        } catch (IOException e) {
            LOG.warning("Seneca: Can't write properties file " + file + ": " + e);
        }
    }
}
