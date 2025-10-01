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

    @JsonCreator
    public SamplerPad(
            @JsonProperty("sessionName") String sessionName,
            @JsonProperty("label") String label,
            @JsonProperty("color") String color) {
        this.sessionName = sessionName;
        this.label = label;
        this.color = color;
    }

    public SamplerPad() {
        this(null, "", "#888888");
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

    public boolean isEmpty() {
        return sessionName == null || sessionName.isEmpty();
    }
}
