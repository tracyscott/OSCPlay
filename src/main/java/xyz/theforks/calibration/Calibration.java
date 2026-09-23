package xyz.theforks.calibration;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A saved sensor calibration. Stores the data the model was fit from rather than the fitted
 * parameters, so loading re-fits the model and calibrations benefit from fitting improvements.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Calibration {
    private String name;
    private String model;
    private String address;
    private int dimensions;
    private String sourceRecording;
    private long created;
    private List<CalibrationSample> samples = new ArrayList<>();
    private List<LabeledPoint> points = new ArrayList<>();

    @JsonIgnore
    private CalibrationModel fitted;

    public Calibration() {
        // Default constructor for Jackson
    }

    public Calibration(String name, String model, String address, int dimensions,
                       List<CalibrationSample> samples, List<LabeledPoint> points) {
        this.name = name;
        this.model = model;
        this.address = address;
        this.dimensions = dimensions;
        this.samples = samples;
        this.points = points;
        this.created = System.currentTimeMillis();
    }

    /**
     * The fitted model, fitting it on first use.
     *
     * @throws IllegalArgumentException if the stored data can't support the model
     */
    @JsonIgnore
    public CalibrationModel getFittedModel() {
        if (fitted == null) {
            CalibrationModel m = CalibrationModel.create(model);
            m.fit(samples, points);
            fitted = m;
        }
        return fitted;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    /** Model type name, e.g. {@link CurveModel#TYPE} or {@link LinearModel#TYPE}. */
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; fitted = null; }

    /** Address pattern (regex) of the sensor messages this calibration was built from. */
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public int getDimensions() { return dimensions; }
    public void setDimensions(int dimensions) { this.dimensions = dimensions; }

    public String getSourceRecording() { return sourceRecording; }
    public void setSourceRecording(String sourceRecording) { this.sourceRecording = sourceRecording; }

    /** Creation time, epoch milliseconds. */
    public long getCreated() { return created; }
    public void setCreated(long created) { this.created = created; }

    public List<CalibrationSample> getSamples() { return samples; }
    public void setSamples(List<CalibrationSample> samples) { this.samples = samples; fitted = null; }

    public List<LabeledPoint> getPoints() { return points; }
    public void setPoints(List<LabeledPoint> points) { this.points = points; fitted = null; }
}
