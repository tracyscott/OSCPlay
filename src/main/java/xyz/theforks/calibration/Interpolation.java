package xyz.theforks.calibration;

final class Interpolation {

    private Interpolation() {
    }

    /**
     * Piecewise-linear interpolation, clamped to the end values outside the range.
     *
     * @param xs Strictly increasing x values
     * @param ys Values at each x
     */
    static double interpolate(double[] xs, double[] ys, double x) {
        int n = xs.length;
        if (x <= xs[0]) {
            return ys[0];
        }
        if (x >= xs[n - 1]) {
            return ys[n - 1];
        }
        int lo = 0;
        int hi = n - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (xs[mid] <= x) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        double frac = (x - xs[lo]) / (xs[hi] - xs[lo]);
        return ys[lo] + frac * (ys[hi] - ys[lo]);
    }
}
