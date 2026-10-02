package io.github.steinbeck.seneca.app;

import java.net.URL;
import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import javax.swing.Icon;
import javax.swing.ImageIcon;

/**
 * The application's texts and icons: {@code io/github/steinbeck/seneca/app/Seneca.properties} (port of
 * the legacy {@code seneca/client/Seneca.properties}, F-04) and {@code images/*.gif}.
 */
public final class Resources {

    public static final String BUNDLE = "io.github.steinbeck.seneca.app.Seneca";

    private final ResourceBundle bundle;

    private Resources(ResourceBundle bundle) {
        this.bundle = bundle;
    }

    /** @throws MissingResourceException if the bundle is missing (legacy: "Seneca.properties not found", exit 1) */
    public static Resources load() {
        return new Resources(ResourceBundle.getBundle(BUNDLE));
    }

    /** The text for {@code key}, or null. */
    public String text(String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return null;
        }
    }

    /** {@link MessageFormat} of the text for {@code key}. */
    public String format(String key, Object... args) {
        String s = text(key);
        return s == null ? null : MessageFormat.format(s.replace("'", "''"), args);
    }

    /**
     * Legacy multi-line message ({@code AboutLines}/{@code AboutMsg1..n}, {@code ExitLines}/...): every
     * line followed by "\n", as the legacy loop built it. {@code prefix} "About" or "Exit".
     */
    public String lines(String prefix, Object... args) {
        int n = Integer.parseInt(text(prefix + "Lines"));
        StringBuilder sb = new StringBuilder();
        for (int f = 0; f < n; f++) sb.append(format(prefix + "Msg" + (f + 1), args)).append("\n");
        return sb.toString();
    }

    /** The icon named by the text of {@code key} (e.g. "newImage" -&gt; images/new.gif), or null. */
    public Icon icon(String key) {
        String path = text(key);
        if (path == null) return null;
        URL u = Resources.class.getResource(path);
        return u == null ? null : new ImageIcon(u);
    }

    /** The SENECA logo (images/seneca.gif): About box and Dock icon. */
    public ImageIcon logo() {
        URL u = Resources.class.getResource("images/seneca.gif");
        return u == null ? null : new ImageIcon(u);
    }

    /** The application version: the jar manifest's Implementation-Version, else "development build". */
    public static String version() {
        String v = Resources.class.getPackage().getImplementationVersion();
        return v == null ? "development build" : v;
    }
}
