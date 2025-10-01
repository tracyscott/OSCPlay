package xyz.theforks.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for a chain of OSC processing nodes.
 */
public class NodeChainConfig {

    private List<NodeConfig> nodes;

    public NodeChainConfig() {
        this.nodes = new ArrayList<>();
    }

    @JsonCreator
    public NodeChainConfig(@JsonProperty("nodes") List<NodeConfig> nodes) {
        this.nodes = nodes != null ? nodes : new ArrayList<>();
    }

    public List<NodeConfig> getNodes() {
        return nodes;
    }

    public void setNodes(List<NodeConfig> nodes) {
        this.nodes = nodes;
    }

    /**
     * Configuration for a single node in the chain.
     */
    public static class NodeConfig {
        private String type; // Fully qualified class name
        private boolean enabled;
        private List<String> args;

        public NodeConfig() {
            this.enabled = true;
            this.args = new ArrayList<>();
        }

        @JsonCreator
        public NodeConfig(
                @JsonProperty("type") String type,
                @JsonProperty("enabled") boolean enabled,
                @JsonProperty("args") List<String> args) {
            this.type = type;
            this.enabled = enabled;
            this.args = args != null ? args : new ArrayList<>();
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getArgs() {
            return args;
        }

        public void setArgs(List<String> args) {
            this.args = args;
        }
    }
}
