package xyz.theforks.calibration;

import java.util.ArrayList;
import java.util.List;

import xyz.theforks.model.OSCMessageRecord;

/**
 * Builds a {@link Calibration} from a recording.
 *
 * With labels, the recording is expected to hold still at each calibration mark in order;
 * each detected hold is paired with the next label. Without labels, only the path through
 * sensor space is used (supported by {@link CurveModel}, which then outputs 0-1).
 */
public class CalibrationBuilder {
    private String name;
    private String address;
    private int dimensions = 1;
    private String model = LinearModel.TYPE;
    private List<Double> labels;
    private long minHoldMs = HoldDetector.DEFAULT_MIN_HOLD_MS;
    private double threshold = Double.NaN;

    public CalibrationBuilder name(String name) {
        this.name = name;
        return this;
    }

    /** Address pattern (regex) of the sensor messages. */
    public CalibrationBuilder address(String address) {
        this.address = address;
        return this;
    }

    /** Number of leading numeric arguments that make up one reading. */
    public CalibrationBuilder dimensions(int dimensions) {
        this.dimensions = dimensions;
        return this;
    }

    public CalibrationBuilder model(String model) {
        this.model = model;
        return this;
    }

    /** Known values at the calibration marks, in the order the marks were visited. Null for none. */
    public CalibrationBuilder labels(List<Double> labels) {
        this.labels = labels;
        return this;
    }

    public CalibrationBuilder minHoldMs(long minHoldMs) {
        this.minHoldMs = minHoldMs;
        return this;
    }

    /** Stillness threshold in sensor units. NaN (the default) derives it from the recording's noise. */
    public CalibrationBuilder threshold(double threshold) {
        this.threshold = threshold;
        return this;
    }

    public String getName() { return name; }
    public String getAddress() { return address; }
    public int getDimensions() { return dimensions; }
    public String getModel() { return model; }
    public List<Double> getLabels() { return labels; }

    /**
     * Evenly spaced labels from start to end inclusive, e.g. labelRange(0, 270, 10) for 0, 10, ... 270.
     */
    public static List<Double> labelRange(double start, double end, double step) {
        if (step == 0 || Math.signum(end - start) * Math.signum(step) < 0) {
            throw new IllegalArgumentException("Step " + step + " doesn't lead from " + start + " to " + end);
        }
        List<Double> labels = new ArrayList<>();
        int count = (int) Math.round((end - start) / step);
        for (int i = 0; i <= count; i++) {
            labels.add(start + i * step);
        }
        return labels;
    }

    public List<CalibrationSample> extractSamples(List<OSCMessageRecord> messages) {
        return SampleExtractor.extract(messages, address, dimensions);
    }

    public HoldDetector holdDetector(List<CalibrationSample> samples) {
        return Double.isNaN(threshold)
            ? HoldDetector.forSamples(samples, minHoldMs)
            : new HoldDetector(threshold, minHoldMs);
    }

    /**
     * Build and fit the calibration.
     *
     * @throws IllegalArgumentException if no samples match, the number of holds doesn't match
     *         the number of labels, or the model can't be fit
     */
    public Result build(List<OSCMessageRecord> messages) {
        List<CalibrationSample> samples = extractSamples(messages);
        if (samples.isEmpty()) {
            throw new IllegalArgumentException("No messages matching " + address + " with "
                + dimensions + " numeric argument(s)");
        }
        return buildFromSamples(samples);
    }

    /**
     * Build and fit the calibration from samples that have already been extracted.
     *
     * @throws IllegalArgumentException if the number of holds doesn't match the number of
     *         labels, or the model can't be fit
     */
    public Result buildFromSamples(List<CalibrationSample> samples) {
        List<HoldDetector.Hold> holds = new ArrayList<>();
        List<LabeledPoint> points = new ArrayList<>();
        if (labels != null) {
            holds = holdDetector(samples).detect(samples);
            if (holds.size() != labels.size()) {
                throw new IllegalArgumentException("Found " + holds.size() + " holds but "
                    + labels.size() + " labels. Adjust the stillness threshold or minimum hold time, "
                    + "or check the recording visits every mark.");
            }
            for (int i = 0; i < holds.size(); i++) {
                points.add(new LabeledPoint(holds.get(i).getMean(), labels.get(i)));
            }
        }

        Calibration calibration = new Calibration(name, model, address, dimensions, samples, points);
        calibration.getFittedModel();
        return new Result(calibration, holds);
    }

    public static class Result {
        private final Calibration calibration;
        private final List<HoldDetector.Hold> holds;

        Result(Calibration calibration, List<HoldDetector.Hold> holds) {
            this.calibration = calibration;
            this.holds = holds;
        }

        public Calibration getCalibration() {
            return calibration;
        }

        public List<HoldDetector.Hold> getHolds() {
            return holds;
        }
    }
}
