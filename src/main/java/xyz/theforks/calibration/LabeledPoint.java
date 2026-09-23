package xyz.theforks.calibration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A sensor reading paired with the known physical value at that position
 * (e.g. the tower angle at a calibration mark).
 */
public class LabeledPoint {
    private final double[] values;
    private final double label;

    @JsonCreator
    public LabeledPoint(
            @JsonProperty("values") double[] values,
            @JsonProperty("label") double label) {
        this.values = values;
        this.label = label;
    }

    public double[] getValues() {
        return values;
    }

    public double getLabel() {
        return label;
    }
}
