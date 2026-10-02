package io.github.steinbeck.seneca.app.judges;

import java.awt.Dimension;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JEditorPane;
import javax.swing.JScrollPane;
import javax.swing.text.html.HTMLEditorKit;

import io.github.steinbeck.seneca.app.ComponentNames;
import io.github.steinbeck.seneca.app.dataset.DatasetContext;
import io.github.steinbeck.seneca.judges.HOSECodeJudge;

/**
 * Sub-tab "Carbon Shifts" (F-52, {@link HOSECodeJudge}), port of {@code HOSECodeJudgeConfigurator}: a
 * read-only HTML text that explains the judge. Autoconfigure copies the assigned 13C shifts into the
 * judge (core).
 */
public final class HoseCodeJudgePanel extends JudgeConfiguratorPanel {

    /** The 2001 text, verbatim. */
    public static final String MESSAGE = "<html>"
            + "<b>HOSECodeJudge</b> uses about 600 1-sphere HOSE codes "
            + "to validate the carbon environment of a structure "
            + "during the stochastic search process in terms of "
            + "hybridization state and hetero atom attachments. "
            + "There is no need for any configurations here. "
            + "Press the 'autoconfigure' button or check the 'activate' box. "
            + "<b>Important Note: </b><i>You should not use this Judge in conjunction "
            + "with HybridizationJudge and HeteroBondsJudge. Use either HOSECodeJudge "
            + "or the combination of HybridizationJudge or HeteroBondsJudge</i>."
            + "</html>";

    private final JEditorPane reportPane = new JEditorPane();

    @SuppressWarnings("this-escape")    // Swing: children in the constructor
    public HoseCodeJudgePanel(DatasetContext ctx) {
        super(ctx, HOSECodeJudge.class);
        reportPane.setName(ComponentNames.JUDGE_TABLE + "HOSECodeJudge");
        reportPane.setEditable(false);
        reportPane.setEditorKit(new HTMLEditorKit());
        reportPane.setText(MESSAGE);
        Box centerBox = new Box(BoxLayout.X_AXIS);
        JScrollPane scrollpane = new JScrollPane(reportPane);
        scrollpane.setPreferredSize(new Dimension(200, 400));
        centerBox.add(scrollpane);
        setCenter(centerBox);
    }

    public JEditorPane textPane() {
        return reportPane;
    }
}
