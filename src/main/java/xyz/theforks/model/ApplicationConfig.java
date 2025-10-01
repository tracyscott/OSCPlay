package xyz.theforks.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration settings for the OSCPlay application.
 * This includes playback mode and other application-wide preferences.
 * Note: Recording always captures raw input messages.
 */
public class ApplicationConfig {

    private PlaybackMode playbackMode;
    private String lastHandlerConfigFile;
    private List<OutputConfig> outputs;

    public ApplicationConfig() {
        this.playbackMode = PlaybackMode.WITHOUT_REWRITE;
        this.lastHandlerConfigFile = null;
        this.outputs = new ArrayList<>();
        // Add default output
        this.outputs.add(createDefaultOutput());
    }

    @JsonCreator
    public ApplicationConfig(
            @JsonProperty("playbackMode") PlaybackMode playbackMode,
            @JsonProperty("lastHandlerConfigFile") String lastHandlerConfigFile,
            @JsonProperty("outputs") List<OutputConfig> outputs) {
        this.playbackMode = playbackMode != null ? playbackMode : PlaybackMode.WITHOUT_REWRITE;
        this.lastHandlerConfigFile = lastHandlerConfigFile;
        this.outputs = outputs != null ? outputs : new ArrayList<>();
        // Ensure default output exists
        if (this.outputs.isEmpty() || !hasOutput("default")) {
            this.outputs.add(0, createDefaultOutput());
        }
    }

    private static OutputConfig createDefaultOutput() {
        return new OutputConfig();
    }

    private boolean hasOutput(String id) {
        return outputs.stream().anyMatch(o -> o.getId().equals(id));
    }

    public PlaybackMode getPlaybackMode() {
        return playbackMode;
    }
    
    public void setPlaybackMode(PlaybackMode playbackMode) {
        this.playbackMode = playbackMode;
    }
    
    public String getLastHandlerConfigFile() {
        return lastHandlerConfigFile;
    }

    public void setLastHandlerConfigFile(String lastHandlerConfigFile) {
        this.lastHandlerConfigFile = lastHandlerConfigFile;
    }

    public List<OutputConfig> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<OutputConfig> outputs) {
        this.outputs = outputs;
    }

    /**
     * Find an output configuration by ID.
     */
    public OutputConfig getOutput(String id) {
        return outputs.stream()
                .filter(o -> o.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    /**
     * Add or update an output configuration.
     */
    public void addOrUpdateOutput(OutputConfig output) {
        OutputConfig existing = getOutput(output.getId());
        if (existing != null) {
            outputs.remove(existing);
        }
        outputs.add(output);
    }

    /**
     * Remove an output configuration.
     */
    public boolean removeOutput(String id) {
        if ("default".equals(id)) {
            return false; // Cannot remove default
        }
        return outputs.removeIf(o -> o.getId().equals(id));
    }
}