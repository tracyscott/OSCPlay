package xyz.theforks.nodes;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.illposed.osc.OSCMessage;
import xyz.theforks.model.MessageRequest;
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
    private ThreadLocal<PlaybackContext> currentContext = new ThreadLocal<>();

    public NodeChain(Context context) {
        this.context = context;
        this.nodes = new CopyOnWriteArrayList<>();
        this.enabled = true;
    }

    /**
     * Process message without context (for proxy/input use).
     * @param message The original OSC message
     * @return List of message requests (may be empty to drop, or multiple for expansion)
     */
    public List<MessageRequest> processMessage(OSCMessage message) {
        return processMessage(message, null);
    }

    /**
     * Process message with optional playback context.
     * @param message The original OSC message
     * @param playbackContext Optional context for playback operations (null for non-playback)
     * @return List of message requests (may be empty to drop, or multiple for expansion)
     */
    public List<MessageRequest> processMessage(OSCMessage message, PlaybackContext playbackContext) {
        if (!enabled || message == null) {
            List<MessageRequest> result = new ArrayList<>(1);
            result.add(new MessageRequest(message));
            return result;
        }

        // Store context for this processing chain
        currentContext.set(playbackContext);

        try {
            // Debug: log raw input
            if (debugWindow != null && debugWindow.isOpen()) {
                debugWindow.addRawMessage(message);
            }

            // Create working list with initial message
            List<MessageRequest> requests = new ArrayList<>();
            requests.add(new MessageRequest(message));

            // Process through each node
            for (OSCNode node : nodes) {
                // We need to process each request separately and collect results
                // because a node might need to expand some requests but not others
                List<MessageRequest> nextRequests = new ArrayList<>();

                for (MessageRequest req : requests) {
                    String address = req.getMessage().getAddress();

                    if (address.matches(node.getAddressPattern())) {
                        // Node matches - process it
                        // Create a temporary list with just this request
                        List<MessageRequest> tempList = new ArrayList<>();
                        tempList.add(req);

                        // Node modifies list in-place
                        node.process(tempList);

                        // Debug: log node output
                        if (debugWindow != null && debugWindow.isOpen()) {
                            for (MessageRequest processed : tempList) {
                                debugWindow.addNodeOutput(node.label(), processed.getMessage());
                            }
                        }

                        // Collect results
                        nextRequests.addAll(tempList);
                    } else {
                        // Node doesn't match - pass through unchanged
                        nextRequests.add(req);
                    }
                }

                requests = nextRequests;

                // If all messages dropped, exit early
                if (requests.isEmpty()) {
                    return requests;
                }
            }

            return requests;

        } finally {
            currentContext.remove();
        }
    }

    /**
     * Get the current playback context (for use by nodes if needed in future).
     * @return The context, or null if not in playback mode
     */
    public PlaybackContext getPlaybackContext() {
        return currentContext.get();
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
