package xyz.theforks.calibration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Piecewise-linear mapping for single-value sensors (pots, faders, single-axis sensors).
 * The labeled points define the mapping; values between them are interpolated.
 */
public class LinearModel implements CalibrationModel {
    public static final String TYPE = "linear";

    private double[] raw;
    private double[] labels;

    @Override
    public void fit(List<CalibrationSample> samples, List<LabeledPoint> points) {
        if (points.size() < 2) {
            throw new IllegalArgumentException("Linear calibration needs at least 2 labeled points, got " + points.size());
        }
        List<LabeledPoint> sorted = new ArrayList<>(points);
        for (LabeledPoint p : sorted) {
            if (p.getValues().length != 1) {
                throw new IllegalArgumentException("Linear calibration needs 1-value readings, got " + p.getValues().length);
            }
        }
        sorted.sort(Comparator.comparingDouble(p -> p.getValues()[0]));

        raw = new double[sorted.size()];
        labels = new double[sorted.size()];
        for (int i = 0; i < sorted.size(); i++) {
            raw[i] = sorted.get(i).getValues()[0];
            labels[i] = sorted.get(i).getLabel();
            if (i > 0 && raw[i] == raw[i - 1]) {
                throw new IllegalArgumentException("Two labeled points have the same reading: " + raw[i]);
            }
        }
    }

    @Override
    public double map(double[] values) {
        return Interpolation.interpolate(raw, labels, values[0]);
    }

    @Override
    public int getDimensions() {
        return 1;
    }
}
