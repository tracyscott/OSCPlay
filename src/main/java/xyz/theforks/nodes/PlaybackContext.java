package xyz.theforks.nodes;

import xyz.theforks.model.MessageRequest;

/**
 * Context interface that provides playback-specific operations to node chains.
 * This interface is injected into NodeChain when processing messages during playback,
 * allowing nodes to schedule delayed messages.
 *
 * In proxy/input mode, this context will be null.
 * In playback mode, Playback class implements this interface.
 */
public interface PlaybackContext {

    /**
     * Schedule a delayed message to be sent through a specific output.
     * The message will be re-inserted into the playback timeline at the appropriate timestamp.
     *
     * @param request The message request with delay and routing info
     * @param outputId The output ID that this message should be sent through
     */
    void scheduleDelayedMessage(MessageRequest request, String outputId);

    /**
     * Get the current playback timestamp (milliseconds since playback start).
     * Useful for nodes that need to know the current position in the timeline.
     *
     * @return Current playback time in milliseconds
     */
    long getCurrentPlaybackTime();
}
