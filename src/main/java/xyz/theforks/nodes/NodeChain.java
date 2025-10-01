package xyz.theforks.nodes;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.illposed.osc.OSCMessage;
import xyz.theforks.ui.NodeChainDebugWindow;

/**
 * Centralized engine for applying node chains to OSC messages.
 * This engine can be used in different contexts (proxy, playback, recording)
 * and provides thread-safe operations for concurrent use.
 */
public class NodeChain {

    public enum Context {
        PROXY,
        PLAYBACK,
        RECORDING
    }

    private final CopyOnWriteArrayList<OSCNode> nodes;
    private volatile boolean enabled;
    private final Context context;
    private volatile NodeChainDebugWindow debugWindow;

    public NodeChain(Context context) {
        this.context = context;
        this.nodes = new CopyOnWriteArrayList<>();
        this.enabled = true;
    }

    /**
     * Apply the node chain to an OSC message.
     * @param message The original OSC message
     * @return The processed message, or null if a node cancelled the message
     */
    public OSCMessage processMessage(OSCMessage message) {
        if (!enabled || message == null) {
            return message;
        }

        // Debug: log raw input
        if (debugWindow != null && debugWindow.isOpen()) {
            debugWindow.addRawMessage(message);
        }

        OSCMessage processedMessage = message;
        String address = message.getAddress();

        for (OSCNode node : nodes) {
            if (address.matches(node.getAddressPattern())) {
                processedMessage = node.process(processedMessage);

                // Debug: log node output
                if (debugWindow != null && debugWindow.isOpen()) {
                    debugWindow.addNodeOutput(node.label(), processedMessage);
                }

                if (processedMessage == null) {
                    return null; // Node cancelled the message
                }
                // Update address in case it was changed by the node
                address = processedMessage.getAddress();
            }
        }

        return processedMessage;
    }

    /**
     * Register a node with this chain.
     * @param node The node to add
     */
    public void registerNode(OSCNode node) {
        if (node != null) {
            nodes.add(node);
        }
    }

    /**
     * Unregister a node from this chain.
     * @param node The node to remove
     */
    public void unregisterNode(OSCNode node) {
        nodes.remove(node);
    }

    /**
     * Clear all nodes from this chain.
     */
    public void clearNodes() {
        nodes.clear();
    }

    /**
     * Set the list of nodes, replacing any existing nodes.
     * @param nodeList The new list of nodes
     */
    public void setNodes(List<OSCNode> nodeList) {
        nodes.clear();
        if (nodeList != null) {
            nodes.addAll(nodeList);
        }
    }

    /**
     * Get a copy of the current list of nodes.
     * @return A new list containing the current nodes
     */
    public List<OSCNode> getNodes() {
        return new ArrayList<>(nodes);
    }

    /**
     * Enable or disable this node chain.
     * When disabled, processMessage() returns the original message unchanged.
     * @param enabled true to enable, false to disable
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Check if this node chain is enabled.
     * @return true if enabled, false if disabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Get the context this node chain is used in.
     * @return The context (PROXY, PLAYBACK, RECORDING)
     */
    public Context getContext() {
        return context;
    }

    /**
     * Get the number of registered nodes.
     * @return The number of nodes
     */
    public int getNodeCount() {
        return nodes.size();
    }

    /**
     * Set the debug window for this node chain.
     * When set, the chain will log each processing step to the window.
     * @param debugWindow The debug window, or null to disable debugging
     */
    public void setDebugWindow(NodeChainDebugWindow debugWindow) {
        this.debugWindow = debugWindow;
    }

    /**
     * Get the current debug window.
     * @return The debug window, or null if not set
     */
    public NodeChainDebugWindow getDebugWindow() {
        return debugWindow;
    }
}
