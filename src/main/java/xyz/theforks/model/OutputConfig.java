package xyz.theforks.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Configuration for a single OSC output including its node chain.
 */
public class OutputConfig {

    private String id;
    private String host;
    private int port;
    private boolean enabled;
    private NodeChainConfig nodeChain;

    public OutputConfig() {
        this.id = "default";
        this.host = "127.0.0.1";
        this.port = 3030;
        this.enabled = true;
        this.nodeChain = new NodeChainConfig();
    }

    @JsonCreator
    public OutputConfig(
            @JsonProperty("id") String id,
            @JsonProperty("host") String host,
            @JsonProperty("port") int port,
            @JsonProperty("enabled") boolean enabled,
            @JsonProperty("nodeChain") NodeChainConfig nodeChain) {
        this.id = id;
        this.host = host;
        this.port = port;
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
}
