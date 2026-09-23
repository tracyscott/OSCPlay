package xyz.theforks.calibration;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import xyz.theforks.model.OSCMessageRecord;

/**
 * Simulated 3-axis sensor on a rotating installation, for calibration tests.
 */
public final class SyntheticSensor {
    public static final double NOISE = 15;
    public static final long SAMPLE_INTERVAL_MS = 44;

    private SyntheticSensor() {
    }

    /**
     * Reading at the given rotation angle. With distort=false the path is a true circle in a
     * tilted plane, so distance along it is proportional to angle. With distort=true it is a
     * warped, non-planar loop where distance along it is not proportional to angle.
     */
    public static double[] curve(double degrees, boolean distort) {
        double a = Math.toRadians(degrees);
        double rx = 2000, ry = 2000, bump = 0;
        if (distort) {
            rx = 2400;
            ry = 1400;
            bump = 600 * Math.sin(2 * a);
        }
        double u = rx * Math.cos(a);
        double v = ry * Math.sin(a);
        // The basis vectors (0.8, 0, -0.6) and (0.36, 0.8, 0.48) are orthonormal,
        // so an undistorted curve stays a true circle
        return new double[]{
            -2400 + 0.8 * u + 0.36 * v,
            3100 + 0.8 * v + bump,
            -3500 - 0.6 * u + 0.48 * v
        };
    }

    /**
     * Angles for a continuous sweep from 0 to 270: rest at the start, uneven speed with one
     * backtrack, rest at the end.
     */
    public static List<Double> sweepAngles() {
        List<Double> angles = new ArrayList<>();
        for (int i = 0; i < 40; i++) angles.add(0.0);
        double a = 0;
        while (a < 120) { a += 0.5; angles.add(a); }       // slow
        while (a > 105) { a -= 1.0; angles.add(a); }       // back up
        while (a < 270) { a += 3.0; angles.add(Math.min(a, 270)); } // fast
        for (int i = 0; i < 40; i++) angles.add(270.0);
        return angles;
    }

    /**
     * Angles for a mark-to-mark calibration: hold at each mark for holdMs, easing between marks.
     */
    public static List<Double> markToMarkAngles(List<Double> marks, long holdMs, long moveMs) {
        List<Double> angles = new ArrayList<>();
        int holdSamples = (int) (holdMs / SAMPLE_INTERVAL_MS);
        int moveSamples = (int) (moveMs / SAMPLE_INTERVAL_MS);
        for (int m = 0; m < marks.size(); m++) {
            if (m > 0) {
                double from = marks.get(m - 1);
                double to = marks.get(m);
                for (int i = 1; i < moveSamples; i++) {
                    double f = i / (double) moveSamples;
                    double eased = (1 - Math.cos(Math.PI * f)) / 2;
                    angles.add(from + eased * (to - from));
                }
            }
            for (int i = 0; i < holdSamples; i++) {
                angles.add(marks.get(m));
            }
        }
        return angles;
    }

    public static List<CalibrationSample> samples(List<Double> angles, boolean distort, long seed) {
        Random random = new Random(seed);
        List<CalibrationSample> samples = new ArrayList<>();
        for (int i = 0; i < angles.size(); i++) {
            double[] p = curve(angles.get(i), distort);
            for (int k = 0; k < p.length; k++) {
                p[k] += random.nextGaussian() * NOISE;
            }
            samples.add(new CalibrationSample(i * SAMPLE_INTERVAL_MS, p));
        }
        return samples;
    }

    /**
     * Samples as recorded OSC messages on the given address, with integer arguments like the
     * real sensor, plus unrelated traffic on another address.
     */
    public static List<OSCMessageRecord> messages(List<CalibrationSample> samples, String address) {
        List<OSCMessageRecord> messages = new ArrayList<>();
        long start = 1_700_000_000_000L;
        for (CalibrationSample s : samples) {
            double[] v = s.getValues();
            OSCMessageRecord record = new OSCMessageRecord(address,
                new Object[]{(int) Math.round(v[0]), (int) Math.round(v[1]), (int) Math.round(v[2])});
            record.setTimestamp(start + s.getTimestamp());
            messages.add(record);

            OSCMessageRecord other = new OSCMessageRecord("/other/sensor", new Object[]{1.0f});
            other.setTimestamp(start + s.getTimestamp());
            messages.add(other);
        }
        return messages;
    }
}
