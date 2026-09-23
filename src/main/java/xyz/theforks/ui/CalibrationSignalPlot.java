package xyz.theforks.ui;

import java.util.ArrayList;
import java.util.List;

import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import xyz.theforks.calibration.CalibrationSample;
import xyz.theforks.calibration.HoldDetector;

/**
 * Plots a calibration recording over time: one line per sensor value, with detected holds
 * shaded and labeled. Holds with no matching label are shaded red.
 */
public class CalibrationSignalPlot extends Pane {
    private static final Color BACKGROUND = Color.web("#141414");
    private static final Color GRID = Color.web("#2a2a2a");
    private static final Color TEXT = Color.web("#b0b0b0");
    private static final Color HOLD = Color.web("#2e7dff", 0.22);
    private static final Color UNLABELED_HOLD = Color.web("#ff4d4d", 0.3);
    private static final Color[] SERIES = {
        Color.web("#ff6b6b"), Color.web("#51cf66"), Color.web("#4dabf7"),
        Color.web("#fcc419"), Color.web("#cc5de8"), Color.web("#22b8cf")
    };
    private static final double LEFT = 60;
    private static final double RIGHT = 10;
    private static final double TOP = 22;
    private static final double BOTTOM = 22;

    private final Canvas canvas = new Canvas();
    private List<CalibrationSample> samples = new ArrayList<>();
    private List<HoldDetector.Hold> holds = new ArrayList<>();
    private List<Double> labels;

    public CalibrationSignalPlot() {
        getChildren().add(canvas);
        setMinSize(200, 120);
        widthProperty().addListener((obs, o, n) -> redraw());
        heightProperty().addListener((obs, o, n) -> redraw());
    }

    @Override
    protected void layoutChildren() {
        canvas.setWidth(getWidth());
        canvas.setHeight(getHeight());
        redraw();
    }

    /**
     * @param labels Label for each hold in order, or null when labels aren't used
     */
    public void setData(List<CalibrationSample> samples, List<HoldDetector.Hold> holds, List<Double> labels) {
        this.samples = samples;
        this.holds = holds;
        this.labels = labels;
        redraw();
    }

    private void redraw() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(BACKGROUND);
        g.fillRect(0, 0, w, h);
        g.setFont(Font.font(11));

        double plotW = w - LEFT - RIGHT;
        double plotH = h - TOP - BOTTOM;
        if (samples.isEmpty() || plotW <= 0 || plotH <= 0) {
            g.setFill(TEXT);
            g.setTextAlign(TextAlignment.CENTER);
            g.setTextBaseline(VPos.CENTER);
            g.fillText("No samples: choose a recording and a sensor address", w / 2, h / 2);
            return;
        }

        long t0 = samples.get(0).getTimestamp();
        long t1 = Math.max(t0 + 1, samples.get(samples.size() - 1).getTimestamp());
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (CalibrationSample s : samples) {
            for (double v : s.getValues()) {
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
        }
        if (max == min) {
            max = min + 1;
        }
        double pad = (max - min) * 0.05;
        final double lo = min - pad;
        final double hi = max + pad;

        // Holds
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.BOTTOM);
        for (int i = 0; i < holds.size(); i++) {
            HoldDetector.Hold hold = holds.get(i);
            double x0 = x(hold.getStartTime(), t0, t1, plotW);
            double x1 = x(hold.getEndTime(), t0, t1, plotW);
            boolean labeled = labels == null || i < labels.size();
            g.setFill(labeled ? HOLD : UNLABELED_HOLD);
            g.fillRect(x0, TOP, Math.max(1, x1 - x0), plotH);
            g.setFill(TEXT);
            String text = labels == null ? String.valueOf(i + 1)
                : labeled ? format(labels.get(i)) : "?";
            g.fillText(text, (x0 + x1) / 2, TOP - 4);
        }

        // Grid and value axis
        g.setStroke(GRID);
        g.setLineWidth(1);
        g.setTextAlign(TextAlignment.RIGHT);
        g.setTextBaseline(VPos.CENTER);
        for (int i = 0; i <= 4; i++) {
            double v = lo + (hi - lo) * i / 4;
            double y = y(v, lo, hi, plotH);
            g.strokeLine(LEFT, y, LEFT + plotW, y);
            g.setFill(TEXT);
            g.fillText(format(v), LEFT - 6, y);
        }

        // Time axis
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.TOP);
        double seconds = (t1 - t0) / 1000.0;
        double step = niceStep(seconds / 8);
        for (double s = 0; s <= seconds; s += step) {
            double x = x(t0 + (long) (s * 1000), t0, t1, plotW);
            g.setFill(TEXT);
            g.fillText(format(s) + "s", x, TOP + plotH + 4);
        }

        // One line per value
        int dims = samples.get(0).getValues().length;
        g.setLineWidth(1.2);
        for (int k = 0; k < dims; k++) {
            g.setStroke(SERIES[k % SERIES.length]);
            g.beginPath();
            for (int i = 0; i < samples.size(); i++) {
                CalibrationSample s = samples.get(i);
                double x = x(s.getTimestamp(), t0, t1, plotW);
                double y = y(s.getValues()[k], lo, hi, plotH);
                if (i == 0) {
                    g.moveTo(x, y);
                } else {
                    g.lineTo(x, y);
                }
            }
            g.stroke();
        }
    }

    private static double x(long t, long t0, long t1, double plotW) {
        return LEFT + (t - t0) * plotW / (t1 - t0);
    }

    private static double y(double v, double lo, double hi, double plotH) {
        return TOP + plotH - (v - lo) * plotH / (hi - lo);
    }

    private static double niceStep(double raw) {
        if (raw <= 0) {
            return 1;
        }
        double magnitude = Math.pow(10, Math.floor(Math.log10(raw)));
        for (double m : new double[]{1, 2, 5, 10}) {
            if (raw <= m * magnitude) {
                return m * magnitude;
            }
        }
        return 10 * magnitude;
    }

    private static String format(double v) {
        return v == Math.rint(v) && Math.abs(v) < 1e9 ? String.valueOf((long) v) : String.format("%.1f", v);
    }
}
