package xyz.theforks.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Configuration for an OSCPlay project (.opp file).
 * Contains all project-specific settings including outputs and node chains.
 */
public class ProjectConfig {

    private String projectName;
    private PlaybackMode playbackMode;
    private java.util.List<OutputConfig> outputs;

    public ProjectConfig() {
        this.projectName = "Untitled";
        this.playbackMode = PlaybackMode.WITHOUT_REWRITE;
        this.outputs = new java.util.ArrayList<>();
        this.outputs.add(createDefaultOutput());
    }

    @JsonCreator
    public ProjectConfig(
            @JsonProperty("projectName") String projectName,
            @JsonProperty("playbackMode") PlaybackMode playbackMode,
            @JsonProperty("outputs") java.util.List<OutputConfig> outputs) {
        this.projectName = projectName != null ? projectName : "Untitled";
        this.playbackMode = playbackMode != null ? playbackMode : PlaybackMode.WITHOUT_REWRITE;
        this.outputs = outputs != null ? outputs : new java.util.ArrayList<>();
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

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public PlaybackMode getPlaybackMode() {
        return playbackMode;
    }

    public void setPlaybackMode(PlaybackMode playbackMode) {
        this.playbackMode = playbackMode;
    }

    public java.util.List<OutputConfig> getOutputs() {
        return outputs;
    }

    public void setOutputs(java.util.List<OutputConfig> outputs) {
        this.outputs = outputs;
    }

    public OutputConfig getOutput(String id) {
        return outputs.stream()
                .filter(o -> o.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    public void addOrUpdateOutput(OutputConfig output) {
        OutputConfig existing = getOutput(output.getId());
        if (existing != null) {
            outputs.remove(existing);
        }
        outputs.add(output);
    }

    public boolean removeOutput(String id) {
        if ("default".equals(id)) {
            return false;
        }
        return outputs.removeIf(o -> o.getId().equals(id));
    }
}
