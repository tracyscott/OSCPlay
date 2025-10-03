package xyz.theforks.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents configuration for a single sampler pad.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SamplerPad {
    private final String sessionName;
    private final String label;
    private final String color;
    private final String midiMapping;
    private final String outputRoute;

    @JsonCreator
    public SamplerPad(
            @JsonProperty("sessionName") String sessionName,
            @JsonProperty("label") String label,
            @JsonProperty("color") String color,
            @JsonProperty("midiMapping") String midiMapping,
            @JsonProperty("outputRoute") String outputRoute) {
        this.sessionName = sessionName;
        this.label = label;
        this.color = color;
        this.midiMapping = midiMapping;
        this.outputRoute = outputRoute != null ? outputRoute : "Proxy";
    }

    public SamplerPad() {
        this(null, "", "#888888", null, "Proxy");
    }

    public String getSessionName() {
        return sessionName;
    }

    public String getLabel() {
        return label;
    }

    public String getColor() {
        return color;
    }

    public String getMidiMapping() {
        return midiMapping;
    }

    public String getOutputRoute() {
        return outputRoute;
    }

    public boolean isEmpty() {
        return sessionName == null || sessionName.isEmpty();
    }
}
