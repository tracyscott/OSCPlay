package xyz.theforks.model;

import com.illposed.osc.OSCMessage;

/**
 * Wrapper for OSC messages that can carry additional metadata for processing.
 * Used across input, playback, and output contexts.
 *
 * MessageRequest enables:
 * - Delayed message sending (via delayMs field)
 * - Output-specific routing (via targetOutputId field)
 * - Multi-message expansion from single input
 */
public class MessageRequest {
    private final OSCMessage message;
    private final long delayMs;              // 0 = immediate, >0 = delay in ms
    private final String targetOutputId;      // null = current output only, specific ID = route to that output

    /**
     * Constructor for immediate send (most common case).
     * Message will be sent immediately to current output.
     *
     * @param message The OSC message to send
     */
    public MessageRequest(OSCMessage message) {
        this(message, 0, null);
    }

    /**
     * Constructor with delay.
     * Message will be delayed by specified milliseconds.
     *
     * @param message The OSC message to send
     * @param delayMs Delay in milliseconds (0 = immediate)
     */
    public MessageRequest(OSCMessage message, long delayMs) {
        this(message, delayMs, null);
    }

    /**
     * Full constructor with delay and routing.
     *
     * @param message The OSC message to send
     * @param delayMs Delay in milliseconds (0 = immediate)
     * @param targetOutputId Target output ID (null = current output only)
     */
    public MessageRequest(OSCMessage message, long delayMs, String targetOutputId) {
        this.message = message;
        this.delayMs = delayMs;
        this.targetOutputId = targetOutputId;
    }

    /**
     * Get the OSC message.
     * @return The OSC message
     */
    public OSCMessage getMessage() {
        return message;
    }

    /**
     * Get the delay in milliseconds.
     * @return Delay in milliseconds (0 = immediate)
     */
    public long getDelayMs() {
        return delayMs;
    }

    /**
     * Get the target output ID.
     * @return Target output ID, or null for current output
     */
    public String getTargetOutputId() {
        return targetOutputId;
    }

    /**
     * Check if this is an immediate send (no delay).
     * @return true if delayMs is 0
     */
    public boolean isImmediate() {
        return delayMs == 0;
    }

    /**
     * Check if this has a specific target output.
     * @return true if targetOutputId is not null
     */
    public boolean hasTargetOutput() {
        return targetOutputId != null;
    }
}
