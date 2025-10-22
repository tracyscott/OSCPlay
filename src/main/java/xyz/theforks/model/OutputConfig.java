package xyz.theforks.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Configuration for a single output (OSC or MIDI) including its node chain.
 */
public class OutputConfig {

    private String id;
    private OutputType outputType;
    // OSC-specific fields
    private String host;
    private int port;
    // MIDI-specific fields
    private String midiDeviceName;
    private boolean enabled;
    private NodeChainConfig nodeChain;

    public OutputConfig() {
        this.id = "default";
        this.outputType = OutputType.OSC;
        this.host = "127.0.0.1";
        this.port = 3030;
        this.enabled = true;
        this.nodeChain = new NodeChainConfig();
    }

    @JsonCreator
    public OutputConfig(
            @JsonProperty("id") String id,
            @JsonProperty("outputType") OutputType outputType,
            @JsonProperty("host") String host,
            @JsonProperty("port") int port,
            @JsonProperty("midiDeviceName") String midiDeviceName,
            @JsonProperty("enabled") boolean enabled,
            @JsonProperty("nodeChain") NodeChainConfig nodeChain) {
        this.id = id;
        this.outputType = outputType != null ? outputType : OutputType.OSC;
        this.host = host;
        this.port = port;
        this.midiDeviceName = midiDeviceName;
        this.enabled = enabled;
        this.nodeChain = nodeChain != null ? nodeChain : new NodeChainConfig();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public NodeChainConfig getNodeChain() {
        return nodeChain;
    }

    public void setNodeChain(NodeChainConfig nodeChain) {
        this.nodeChain = nodeChain;
    }

    public OutputType getOutputType() {
        return outputType;
    }

    public void setOutputType(OutputType outputType) {
        this.outputType = outputType;
    }

    public String getMidiDeviceName() {
        return midiDeviceName;
    }

    public void setMidiDeviceName(String midiDeviceName) {
        this.midiDeviceName = midiDeviceName;
    }
}
