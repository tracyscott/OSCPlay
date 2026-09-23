package xyz.theforks.calibration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One sensor reading: a vector of values and the time it arrived.
 */
public class CalibrationSample {
    private final long timestamp;
    private final double[] values;

    /**
     * @param timestamp Milliseconds since the first sample of the recording
     * @param values Sensor values (one per dimension)
     */
    @JsonCreator
    public CalibrationSample(
            @JsonProperty("timestamp") long timestamp,
            @JsonProperty("values") double[] values) {
        this.timestamp = timestamp;
        this.values = values;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public double[] getValues() {
        return values;
    }
}
