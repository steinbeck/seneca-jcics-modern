package io.github.steinbeck.seneca.app.judges;

import java.awt.BorderLayout;
import java.awt.Dimension;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;

import io.github.steinbeck.seneca.app.ComponentNames;

/**
 * Content of the window "Judge Configuration Report" (F-81, 2001 {@code TwoDSpectrumJudgeConfigurator.report}):
 * a 400x400 text pane with the judge's configuration as ASCII text ({@code JudgeConfiguration.report}).
 * Shown through {@code UiServices.windows()}. Port: in a scroll pane and read-only (2001: an editable text
 * pane without scroll bars, so long reports were cut off).
 */
public final class JudgeReportPanel extends JPanel {

    public static final String TITLE = "Judge Configuration Report";

    private final JTextPane text = new JTextPane();

    @SuppressWarnings("this-escape")    // Swing: children in the constructor
    public JudgeReportPanel(String report) {
        super(new BorderLayout());
        text.setName(ComponentNames.JUDGE_REPORT_TEXT);
        text.setEditable(false);
        text.setText(report);
        text.setCaretPosition(0);
        JScrollPane sp = new JScrollPane(text);
        sp.setPreferredSize(new Dimension(400, 400));
        add(sp, BorderLayout.CENTER);
    }

    public String text() {
        return text.getText();
    }
}
