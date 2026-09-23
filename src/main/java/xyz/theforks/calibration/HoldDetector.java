package xyz.theforks.calibration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Finds the stretches of a recording where the sensor was held still, e.g. while the
 * installation was parked at each calibration mark.
 *
 * A hold grows from a starting sample for as long as each new sample stays within the
 * stillness threshold of the hold's running mean. Holds shorter than the minimum duration
 * are discarded. Each hold's representative reading is the mean of its middle portion, so
 * the tail of the movement into the mark doesn't bias it.
 */
public class HoldDetector {
    /** Default stillness threshold as a multiple of the estimated sensor noise. */
    public static final double DEFAULT_NOISE_MULTIPLE = 4.0;
    public static final long DEFAULT_MIN_HOLD_MS = 1000;
    /** Fraction of the hold trimmed from each end before averaging. */
    private static final double TRIM_FRACTION = 0.2;

    private final double threshold;
    private final long minHoldMs;

    /**
     * @param threshold Maximum distance from the hold's running mean for a sample to count as still
     * @param minHoldMs Minimum duration for a still stretch to count as a hold
     */
    public HoldDetector(double threshold, long minHoldMs) {
        this.threshold = threshold;
        this.minHoldMs = minHoldMs;
    }

    /**
     * Detector with the threshold derived from the noise in the given samples.
     */
    public static HoldDetector forSamples(List<CalibrationSample> samples, long minHoldMs) {
        return new HoldDetector(estimateNoise(samples) * DEFAULT_NOISE_MULTIPLE, minHoldMs);
    }

    public double getThreshold() {
        return threshold;
    }

    public long getMinHoldMs() {
        return minHoldMs;
    }

    public List<Hold> detect(List<CalibrationSample> samples) {
        List<Hold> holds = new ArrayList<>();
        int n = samples.size();
        int start = 0;
        while (start < n) {
            int dims = samples.get(start).getValues().length;
            double[] sum = samples.get(start).getValues().clone();
            int end = start + 1;
            while (end < n) {
                double[] mean = new double[dims];
                for (int k = 0; k < dims; k++) {
                    mean[k] = sum[k] / (end - start);
                }
                double[] next = samples.get(end).getValues();
                if (distance(mean, next) > threshold) {
                    break;
                }
                for (int k = 0; k < dims; k++) {
                    sum[k] += next[k];
                }
                end++;
            }

            long duration = samples.get(end - 1).getTimestamp() - samples.get(start).getTimestamp();
            if (duration >= minHoldMs) {
                holds.add(new Hold(start, end, samples.get(start).getTimestamp(),
                    samples.get(end - 1).getTimestamp(), trimmedMean(samples, start, end)));
                start = end;
            } else {
                start++;
            }
        }
        return holds;
    }

    private static double[] trimmedMean(List<CalibrationSample> samples, int start, int end) {
        int trim = (int) ((end - start) * TRIM_FRACTION);
        int from = start + trim;
        int to = end - trim;
        int dims = samples.get(start).getValues().length;
        double[] mean = new double[dims];
        for (int i = from; i < to; i++) {
            for (int k = 0; k < dims; k++) {
                mean[k] += samples.get(i).getValues()[k];
            }
        }
        for (int k = 0; k < dims; k++) {
            mean[k] /= (to - from);
        }
        return mean;
    }

    /**
     * Estimate the sensor noise (standard deviation of a reading's distance from the true value)
     * from sample-to-sample differences. Uses the lower quartile so movement between holds, which
     * makes up a minority of consecutive pairs in a mark-to-mark recording, barely affects it.
     */
    public static double estimateNoise(List<CalibrationSample> samples) {
        if (samples.size() < 2) {
            return 0;
        }
        int dims = samples.get(0).getValues().length;
        double variance = 0;
        for (int k = 0; k < dims; k++) {
            double[] diffs = new double[samples.size() - 1];
            for (int i = 1; i < samples.size(); i++) {
                diffs[i - 1] = Math.abs(samples.get(i).getValues()[k] - samples.get(i - 1).getValues()[k]);
            }
            Arrays.sort(diffs);
            // For Gaussian noise, the lower quartile of |difference| = 0.3186 * sqrt(2) * sigma
            double sigma = diffs[diffs.length / 4] / (0.3186 * Math.sqrt(2));
            variance += sigma * sigma;
        }
        return Math.sqrt(variance);
    }

    static double distance(double[] a, double[] b) {
        double sum = 0;
        for (int k = 0; k < a.length; k++) {
            double d = a[k] - b[k];
            sum += d * d;
        }
        return Math.sqrt(sum);
    }

    /**
     * A still stretch of the recording.
     */
    public static class Hold {
        private final int startIndex;
        private final int endIndex;
        private final long startTime;
        private final long endTime;
        private final double[] mean;

        public Hold(int startIndex, int endIndex, long startTime, long endTime, double[] mean) {
            this.startIndex = startIndex;
            this.endIndex = endIndex;
            this.startTime = startTime;
            this.endTime = endTime;
            this.mean = mean;
        }

        /** Index of the first sample in the hold. */
        public int getStartIndex() {
            return startIndex;
        }

        /** Index one past the last sample in the hold. */
        public int getEndIndex() {
            return endIndex;
        }

        public long getStartTime() {
            return startTime;
        }

        public long getEndTime() {
            return endTime;
        }

        /** Representative reading for the hold. */
        public double[] getMean() {
            return mean;
        }
    }
}
