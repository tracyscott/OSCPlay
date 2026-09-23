package xyz.theforks.calibration;

import java.util.List;

/**
 * Maps a raw sensor reading to a calibrated value.
 */
public interface CalibrationModel {

    /**
     * Fit the model.
     *
     * @param samples The full calibration recording, in time order
     * @param points Readings at known positions (may be empty for models that don't require them)
     * @throws IllegalArgumentException if the data can't support this model
     */
    void fit(List<CalibrationSample> samples, List<LabeledPoint> points);

    /**
     * Map a reading to its calibrated value. Readings outside the calibrated range are clamped.
     */
    double map(double[] values);

    /**
     * Number of values in a reading.
     */
    int getDimensions();

    /**
     * Create an unfitted model by type name, as stored in a calibration file.
     */
    static CalibrationModel create(String type) {
        switch (type) {
            case LinearModel.TYPE:
                return new LinearModel();
            case CurveModel.TYPE:
                return new CurveModel();
            default:
                throw new IllegalArgumentException("Unknown calibration model: " + type);
        }
    }
}
