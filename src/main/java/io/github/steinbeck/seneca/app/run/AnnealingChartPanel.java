package io.github.steinbeck.seneca.app.run;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import javax.swing.BorderFactory;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.title.TextTitle;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import io.github.steinbeck.seneca.app.ComponentNames;
import io.github.steinbeck.seneca.structgen.AnnealingLog;

/**
 * The "Annealing Data" chart of the "Structure Generation" tab (feature-inventory F-70, F-88; legacy
 * {@code SGServerPanel.getAnnealingViewPanel}, {@code ChartPanel} and {@code AnnealingDataSource} on
 * {@code com.jrefinery.chart}), with JFreeChart 1.5:
 *
 * <ul>
 *   <li>XY line chart with a tiny empty title (SansSerif 6 pt), x axis "Iteration", y axis "Score" or
 *       "kT", legend on the right (as the 2001 chart), grey chart background, white plot area;</li>
 *   <li>one series {@code "host <i>"} per run slot with the polled annealing-log entries
 *       ({@link AnnealingData}); in score mode also {@code "max. Score"}, a horizontal line at the last
 *       polled max score across the iterations shown;</li>
 *   <li>without data the single, empty series "no data" (legacy: "no data" with one invisible point);</li>
 *   <li>a mouse click anywhere on the chart toggles the y axis between the best score and kT.</li>
 * </ul>
 * No zooming and no context menu (the 2001 panel had neither; the JFreeChart menu would open file
 * dialogs outside {@code UiServices}).
 *
 * <p>Colours (DV-88): 2001 took the chart library's default colour of each series position, so the
 * "max. Score" line was pure yellow on white with three slots. Here the host lines use a fixed palette of
 * dark colours ({@link #HOST_COLOURS}, red and blue first as in 2001) and "max. Score" is always a dashed
 * black line. The y axis spans at least 1 (a maximum score of 0 gave labels like "4E-9"). EDT only.
 */
public final class AnnealingChartPanel extends ChartPanel {

    /** Line colours of "host 1", "host 2", ... (repeated after the last). */
    public static final Color[] HOST_COLOURS = {
            new Color(204, 0, 0), new Color(0, 0, 204), new Color(0, 128, 0), new Color(214, 110, 0),
            new Color(150, 0, 150), new Color(0, 128, 150), new Color(130, 85, 30), new Color(90, 90, 90)};

    /** Colour of the "max. Score" line. */
    public static final Color MAX_SCORE_COLOUR = Color.black;

    private static final BasicStroke HOST_STROKE = new BasicStroke(1.0f);
    private static final BasicStroke MAX_SCORE_STROKE = new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
            10f, new float[] {6f, 4f}, 0f);

    private final AnnealingData data;

    @SuppressWarnings("this-escape")    // Swing: listeners are set up in the constructor
    public AnnealingChartPanel(AnnealingData data) {
        super(createChart(), false, false, false, false, true);
        this.data = data;
        setName(ComponentNames.RUN_CHART);
        setDomainZoomable(false);
        setRangeZoomable(false);
        setPreferredSize(new Dimension(400, 400));
        setMinimumDrawWidth(10);
        setMinimumDrawHeight(10);
        setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Annealing Data"));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                toggleDisplay();
            }
        });
        refresh();
    }

    private static JFreeChart createChart() {
        JFreeChart chart = ChartFactory.createXYLineChart(null, "Iteration", "Score", new XYSeriesCollection(),
                PlotOrientation.VERTICAL, true, true, false);
        chart.setTitle(new TextTitle("", new Font("SansSerif", Font.PLAIN, 6)));
        chart.setBackgroundPaint(new Color(192, 192, 192));
        chart.getLegend().setPosition(RectangleEdge.RIGHT);
        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(Color.white);
        plot.setDomainGridlinePaint(Color.lightGray);
        plot.setRangeGridlinePaint(Color.lightGray);
        // 2001: the y axis spans the data only (e.g. 2100..2400), not from zero
        NumberAxis range = (NumberAxis) plot.getRangeAxis();
        range.setAutoRangeIncludesZero(false);
        range.setAutoRangeMinimumSize(1.0);
        return chart;
    }

    /** The chart's data. */
    public AnnealingData data() {
        return data;
    }

    /** Mouse click: score &lt;-&gt; kT, axis label follows. */
    public void toggleDisplay() {
        data.toggleDisplay();
        refresh();
    }

    /** Rebuilds the series from {@link #data()} (after every poll, reset and toggle). */
    public void refresh() {
        XYSeriesCollection c = new XYSeriesCollection();
        if (!data.hasData()) {
            c.addSeries(new XYSeries("no data", false, true));
        } else {
            double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < data.seriesCount(); i++) {
                XYSeries s = new XYSeries(AnnealingData.seriesName(i), false, true);
                List<AnnealingLog.Entry> entries = data.series(i);
                for (AnnealingLog.Entry e : entries) {
                    s.add(e.iteration(), data.y(e), false);
                    minX = Math.min(minX, e.iteration());
                    maxX = Math.max(maxX, e.iteration());
                }
                c.addSeries(s);
            }
            if (data.display() == AnnealingData.Display.SCORES) {
                XYSeries max = new XYSeries("max. Score", false, true);
                if (minX <= maxX) {
                    max.add(minX, data.maxScore(), false);
                    max.add(maxX, data.maxScore(), false);
                }
                c.addSeries(max);
            }
        }
        XYPlot plot = getChart().getXYPlot();
        plot.setDataset(c);
        XYItemRenderer r = plot.getRenderer();
        for (int i = 0; i < c.getSeriesCount(); i++) {
            boolean max = "max. Score".equals(c.getSeriesKey(i));
            r.setSeriesPaint(i, max ? MAX_SCORE_COLOUR : HOST_COLOURS[i % HOST_COLOURS.length]);
            r.setSeriesStroke(i, max ? MAX_SCORE_STROKE : HOST_STROKE);
        }
        plot.getRangeAxis().setLabel(data.display().axisLabel);
        plot.getDomainAxis().setLabel("Iteration");
    }

    /** The series currently drawn (tests): name and number of points. */
    public XYSeriesCollection dataset() {
        return (XYSeriesCollection) getChart().getXYPlot().getDataset();
    }
}
