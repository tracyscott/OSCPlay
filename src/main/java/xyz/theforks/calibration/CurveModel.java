package xyz.theforks.calibration;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;

/**
 * Calibration for a single degree of freedom measured by a multi-value sensor, e.g. a
 * rotating tower measured by a 3-axis magnetometer whose field is distorted by nearby steel.
 *
 * The recording's path through sensor space is smoothed, cleaned of rest periods and
 * backtracking, and fit with an open cubic spline spaced by distance along the path.
 * A reading is projected onto the nearest point of the curve. With labeled points, the
 * result is interpolated between the labels (e.g. true angles at calibration marks);
 * without them it is the normalized distance along the curve (0 at the start, 1 at the end).
 */
public class CurveModel implements CalibrationModel {
    public static final String TYPE = "curve";

    // Centered moving-average window applied to the raw calibration samples
    private static final int SMOOTHING_WINDOW = 9;
    // Centered moving-average window applied to the knots. Knots are evenly spaced along the
    // curve, so this removes noise equally regardless of how fast the sensor was moved.
    private static final int KNOT_SMOOTHING_WINDOW = 5;
    // Minimum spacing between spline knots, as a fraction of the bounding-box diagonal.
    // Must exceed the residual sensor jitter so rest periods collapse to a single knot.
    private static final double MIN_STEP_FRACTION = 0.01;
    // Number of dense samples along the spline used for arc-length lookup and projection
    private static final int CURVE_SAMPLES = 2000;

    private int dimensions;
    private PolynomialSplineFunction[] splines;
    private double curveLength;
    // Dense samples along the spline and the normalized arc length (0-1) at each
    private double[][] curvePoints;
    private double[] curveArcLength;
    // Maps normalized arc length to label, when labeled points are given
    private double[] labelPositions;
    private double[] labelValues;

    @Override
    public void fit(List<CalibrationSample> samples, List<LabeledPoint> points) {
        if (samples.isEmpty()) {
            throw new IllegalArgumentException("No calibration samples");
        }
        dimensions = samples.get(0).getValues().length;
        List<double[]> raw = new ArrayList<>(samples.size());
        for (CalibrationSample s : samples) {
            raw.add(s.getValues());
        }
        double noise = HoldDetector.estimateNoise(samples);
        fitCurve(smooth(buildKnots(smooth(raw, SMOOTHING_WINDOW), noise), KNOT_SMOOTHING_WINDOW));
        fitLabels(points);
    }

    private void fitCurve(List<double[]> knots) {
        int n = knots.size();
        if (n < 3) {
            throw new IllegalArgumentException("Calibration sweep too short: " + n + " usable points");
        }

        // Parameterize knots by cumulative chord length so the spline is spaced by distance,
        // not by sample index (the sensor isn't moved at constant speed during calibration)
        double[] t = new double[n];
        for (int i = 1; i < n; i++) {
            t[i] = t[i - 1] + HoldDetector.distance(knots.get(i - 1), knots.get(i));
        }
        double chordLength = t[n - 1];
        for (int i = 0; i < n; i++) {
            t[i] /= chordLength;
        }

        SplineInterpolator interpolator = new SplineInterpolator();
        splines = new PolynomialSplineFunction[dimensions];
        for (int k = 0; k < dimensions; k++) {
            double[] values = new double[n];
            for (int i = 0; i < n; i++) {
                values[i] = knots.get(i)[k];
            }
            splines[k] = interpolator.interpolate(t, values);
        }

        // Sample the spline densely and record true arc length at each sample. Chord-length
        // parameterization is only approximately arc length, so output uses this table.
        curvePoints = new double[CURVE_SAMPLES + 1][];
        curveArcLength = new double[CURVE_SAMPLES + 1];
        curveLength = 0;
        for (int i = 0; i <= CURVE_SAMPLES; i++) {
            curvePoints[i] = pointAt(i / (double) CURVE_SAMPLES);
            if (i > 0) {
                curveLength += HoldDetector.distance(curvePoints[i - 1], curvePoints[i]);
            }
            curveArcLength[i] = curveLength;
        }
        for (int i = 0; i <= CURVE_SAMPLES; i++) {
            curveArcLength[i] /= curveLength;
        }
    }

    private void fitLabels(List<LabeledPoint> points) {
        labelPositions = null;
        labelValues = null;
        if (points.isEmpty()) {
            return;
        }
        if (points.size() < 2) {
            throw new IllegalArgumentException("Curve calibration needs 0 or at least 2 labeled points, got 1");
        }

        // Order the labels by where their readings fall along the curve
        List<double[]> byPosition = new ArrayList<>();
        for (LabeledPoint p : points) {
            byPosition.add(new double[]{position(p.getValues()), p.getLabel()});
        }
        byPosition.sort((a, b) -> Double.compare(a[0], b[0]));

        int n = byPosition.size();
        labelPositions = new double[n];
        labelValues = new double[n];
        for (int i = 0; i < n; i++) {
            labelPositions[i] = byPosition.get(i)[0];
            labelValues[i] = byPosition.get(i)[1];
        }

        // Labels must run in one direction along the curve, otherwise the marks were visited
        // out of order or two readings landed on the same part of the curve
        double direction = Math.signum(labelValues[n - 1] - labelValues[0]);
        for (int i = 1; i < n; i++) {
            if (labelPositions[i] <= labelPositions[i - 1]
                    || Math.signum(labelValues[i] - labelValues[i - 1]) != direction) {
                throw new IllegalArgumentException(String.format(
                    "Labels are not in order along the curve near %.3g and %.3g",
                    labelValues[i - 1], labelValues[i]));
            }
        }
    }

    @Override
    public double map(double[] values) {
        double position = position(values);
        if (labelPositions == null) {
            return position;
        }
        return Interpolation.interpolate(labelPositions, labelValues, position);
    }

    @Override
    public int getDimensions() {
        return dimensions;
    }

    /**
     * Project a reading onto the calibration curve.
     *
     * @return Normalized arc length (0 at the start of the calibration sweep, 1 at the end)
     */
    public double position(double[] p) {
        int nearest = 0;
        double minDist = Double.MAX_VALUE;
        for (int i = 0; i < curvePoints.length; i++) {
            double d = HoldDetector.distance(p, curvePoints[i]);
            if (d < minDist) {
                minDist = d;
                nearest = i;
            }
        }

        // Refine by projecting onto the segments either side of the nearest sample
        double best = curveArcLength[nearest];
        for (int i = Math.max(0, nearest - 1); i < Math.min(curvePoints.length - 1, nearest + 1); i++) {
            double[] a = curvePoints[i];
            double[] b = curvePoints[i + 1];
            double segLenSq = 0;
            double dotProduct = 0;
            for (int k = 0; k < dimensions; k++) {
                segLenSq += (b[k] - a[k]) * (b[k] - a[k]);
                dotProduct += (p[k] - a[k]) * (b[k] - a[k]);
            }
            if (segLenSq == 0) continue;
            double frac = Math.max(0, Math.min(1, dotProduct / segLenSq));
            double[] proj = new double[dimensions];
            for (int k = 0; k < dimensions; k++) {
                proj[k] = a[k] + frac * (b[k] - a[k]);
            }
            double d = HoldDetector.distance(p, proj);
            if (d < minDist) {
                minDist = d;
                best = curveArcLength[i] + frac * (curveArcLength[i + 1] - curveArcLength[i]);
            }
        }
        return best;
    }

    /**
     * Point on the spline at parameter u (0-1). The parameter is approximately, not exactly,
     * proportional to arc length.
     */
    public double[] pointAt(double u) {
        double[] p = new double[dimensions];
        for (int k = 0; k < dimensions; k++) {
            p[k] = splines[k].value(u);
        }
        return p;
    }

    public double getCurveLength() {
        return curveLength;
    }

    /**
     * Centered moving average to suppress sensor noise. The window shrinks symmetrically near
     * the ends so the endpoints stay put instead of being pulled toward the interior.
     */
    private static List<double[]> smooth(List<double[]> points, int window) {
        List<double[]> smoothed = new ArrayList<>(points.size());
        for (int i = 0; i < points.size(); i++) {
            int half = Math.min(window / 2, Math.min(i, points.size() - 1 - i));
            int dims = points.get(i).length;
            double[] avg = new double[dims];
            for (int j = i - half; j <= i + half; j++) {
                for (int k = 0; k < dims; k++) {
                    avg[k] += points.get(j)[k];
                }
            }
            for (int k = 0; k < dims; k++) {
                avg[k] /= (2 * half + 1);
            }
            smoothed.add(avg);
        }
        return smoothed;
    }

    /**
     * Reduce the smoothed path to a sequence of knots that only moves forward along it.
     * Jitter smaller than the minimum step (including rest periods) adds nothing, and when the
     * sensor backs up the knots it retraces are removed so the curve never folds back on itself.
     * Returns the furthest extent reached, so a return sweep back to the start doesn't erase it.
     *
     * @param noise Raw sensor noise; knots are never spaced closer than this, so a recording
     *              without real movement produces too few knots to fit
     */
    private static List<double[]> buildKnots(List<double[]> points, double noise) {
        int dims = points.get(0).length;
        double[] min = points.get(0).clone();
        double[] max = points.get(0).clone();
        for (double[] p : points) {
            for (int k = 0; k < dims; k++) {
                min[k] = Math.min(min[k], p[k]);
                max[k] = Math.max(max[k], p[k]);
            }
        }
        double minStep = Math.max(HoldDetector.distance(min, max) * MIN_STEP_FRACTION, noise);

        List<double[]> knots = new ArrayList<>();
        List<double[]> furthest = knots;
        knots.add(points.get(0));
        for (double[] p : points) {
            // A point clearly closer to the second-to-last knot than the last knot is heading
            // backwards. The margin keeps jitter while resting from eroding the end knot.
            while (knots.size() >= 2) {
                double[] last = knots.get(knots.size() - 1);
                double[] prev = knots.get(knots.size() - 2);
                if (HoldDetector.distance(p, prev) < HoldDetector.distance(last, prev) - minStep * 0.5) {
                    if (furthest == knots) {
                        furthest = new ArrayList<>(knots);
                    }
                    knots.remove(knots.size() - 1);
                } else {
                    break;
                }
            }
            if (HoldDetector.distance(p, knots.get(knots.size() - 1)) >= minStep) {
                knots.add(p);
                if (knots.size() > furthest.size()) {
                    furthest = knots;
                }
            }
        }
        return furthest;
    }
}
