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

    @JsonCreator
    public SamplerPad(
            @JsonProperty("sessionName") String sessionName,
            @JsonProperty("label") String label,
            @JsonProperty("color") String color,
            @JsonProperty("midiMapping") String midiMapping) {
        this.sessionName = sessionName;
        this.label = label;
        this.color = color;
        this.midiMapping = midiMapping;
    }

    public SamplerPad() {
        this(null, "", "#888888", null);
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

    public boolean isEmpty() {
        return sessionName == null || sessionName.isEmpty();
    }
}
