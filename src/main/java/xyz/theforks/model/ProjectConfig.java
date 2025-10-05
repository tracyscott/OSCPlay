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
    private java.util.Map<Integer, String> midiMappings;
    private String midiDeviceName;
    private String inHost;
    private int inPort;

    public ProjectConfig() {
        this.projectName = "Untitled";
        this.playbackMode = PlaybackMode.WITHOUT_REWRITE;
        this.outputs = new java.util.ArrayList<>();
        this.outputs.add(createDefaultOutput());
        this.midiMappings = new java.util.HashMap<>();
        this.midiDeviceName = null;
        this.inHost = "127.0.0.1";
        this.inPort = 8000;
    }

    @JsonCreator
    public ProjectConfig(
            @JsonProperty("projectName") String projectName,
            @JsonProperty("playbackMode") PlaybackMode playbackMode,
            @JsonProperty("outputs") java.util.List<OutputConfig> outputs,
            @JsonProperty("midiMappings") java.util.Map<Integer, String> midiMappings,
            @JsonProperty("midiDeviceName") String midiDeviceName,
            @JsonProperty("inHost") String inHost,
            @JsonProperty("inPort") Integer inPort) {
        this.projectName = projectName != null ? projectName : "Untitled";
        this.playbackMode = playbackMode != null ? playbackMode : PlaybackMode.WITHOUT_REWRITE;
        this.outputs = outputs != null ? outputs : new java.util.ArrayList<>();
        this.midiMappings = midiMappings != null ? midiMappings : new java.util.HashMap<>();
        this.midiDeviceName = midiDeviceName;
        this.inHost = inHost != null ? inHost : "127.0.0.1";
        this.inPort = inPort != null ? inPort : 8000;
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

    public java.util.Map<Integer, String> getMidiMappings() {
        return midiMappings;
    }

    public void setMidiMappings(java.util.Map<Integer, String> midiMappings) {
        this.midiMappings = midiMappings;
    }

    public void setMidiMapping(int padNumber, String midiKey) {
        if (midiKey == null) {
            midiMappings.remove(padNumber);
        } else {
            midiMappings.put(padNumber, midiKey);
        }
    }

    public String getMidiMapping(int padNumber) {
        return midiMappings.get(padNumber);
    }

    public String getMidiDeviceName() {
        return midiDeviceName;
    }

    public void setMidiDeviceName(String midiDeviceName) {
        this.midiDeviceName = midiDeviceName;
    }

    public String getInHost() {
        return inHost;
    }

    public void setInHost(String inHost) {
        this.inHost = inHost;
    }

    public int getInPort() {
        return inPort;
    }

    public void setInPort(int inPort) {
        this.inPort = inPort;
    }
}
