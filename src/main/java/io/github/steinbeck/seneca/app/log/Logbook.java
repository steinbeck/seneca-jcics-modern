package io.github.steinbeck.seneca.app.log;

import java.awt.Color;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * The one application-wide Logbook (F-47): legacy log4j {@code TextPaneAppender} "A4" of category
 * {@code seneca.structgen.StochasticGenerator.log}, layout {@code %m}, option Fancy=false (set by the
 * 2001 {@code Seneca} constructor: no priority icons). Every engine INFO message of every run is
 * appended verbatim (no timestamps, no added newline: the messages carry their own "\n" where legacy
 * had one), in the appender's style: 14 pt, INFO grey, FATAL ("Error in StoicGent.run()") in the
 * default colour. Every dataset window's "Logbook" tab shows a view of the same {@link #document()}
 * (legacy moved the one text pane into the most recently opened frame, DV-80).
 *
 * <p>Verified against a 2001 run (scripts/legacy-run-shots.sh, pinene seed 1): the same text.
 *
 * <p>Size (DV-90): the document keeps at most {@link #MAX_CHARS} characters; when an append goes beyond
 * that, the oldest whole lines are removed down to {@link #TRIM_TO_CHARS}. 2001 kept everything, but the
 * port's engine writes the log many times faster (about 27 MB in 200 s for one long run), and every
 * insert into a long text costs the Swing thread more and more layout work until the window stops
 * responding. {@code seneca.log} still receives the complete text.
 *
 * <p>Thread safety: {@link #append} may be called from any thread (run threads); the text is added to
 * the document on the EDT, in call order, consecutive texts of the same style in one insert.
 */
public final class Logbook {

    /** TextPaneAppender attributes: every priority 14 pt; INFO grey (ERROR red, WARN orange, DEBUG black). */
    static final SimpleAttributeSet INFO = new SimpleAttributeSet();
    static final SimpleAttributeSet FATAL = new SimpleAttributeSet();

    static {
        StyleConstants.setFontSize(INFO, 14);
        StyleConstants.setForeground(INFO, Color.gray);
        StyleConstants.setFontSize(FATAL, 14);
    }

    /** Most characters the Logbook keeps. */
    public static final int MAX_CHARS = 1_000_000;
    /** Length after trimming (the oldest lines go). */
    public static final int TRIM_TO_CHARS = 750_000;

    private final StyledDocument document = new DefaultStyledDocument();
    private final Queue<String> pending = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean scheduled = new AtomicBoolean();

    public StyledDocument document() {
        return document;
    }

    /** Appends {@code text} (any thread). */
    public void append(String text) {
        pending.add(text);
        if (scheduled.compareAndSet(false, true)) SwingUtilities.invokeLater(this::drain);
    }

    private void drain() {
        scheduled.set(false);
        try {
            StringBuilder batch = new StringBuilder();
            boolean batchFatal = false;
            String s;
            while ((s = pending.poll()) != null) {
                boolean fatal = s.startsWith(AppEngineLog.FATAL_MESSAGE);
                if (batch.length() > 0 && fatal != batchFatal) {
                    insert(batch.toString(), batchFatal);
                    batch.setLength(0);
                }
                batchFatal = fatal;
                batch.append(s);
            }
            if (batch.length() > 0) insert(batch.toString(), batchFatal);
            trim();
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
    }

    private void insert(String text, boolean fatal) throws BadLocationException {
        document.insertString(document.getLength(), text, fatal ? FATAL : INFO);
    }

    /** Above {@link #MAX_CHARS}: removes the oldest lines down to about {@link #TRIM_TO_CHARS}. */
    private void trim() throws BadLocationException {
        int len = document.getLength();
        if (len <= MAX_CHARS) return;
        int cut = len - TRIM_TO_CHARS;
        String head = document.getText(cut, Math.min(len - cut, 10_000));
        int nl = head.indexOf('\n');
        if (nl >= 0) cut += nl + 1;            // whole lines only
        document.remove(0, cut);
    }

    /** The whole text (EDT). */
    public String text() {
        try {
            return document.getText(0, document.getLength());
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
    }
}
