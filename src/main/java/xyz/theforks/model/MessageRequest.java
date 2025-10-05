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
    private long delayMs;              // 0 = immediate, >0 = delay in ms
    private final String targetOutputId;      // null = current output only, specific ID = route to that output
    private final long previousDelay;         // Delay previously applied (used during playback to prevent re-delay)

    /**
     * Constructor for immediate send (most common case).
     * Message will be sent immediately to current output.
     *
     * @param message The OSC message to send
     */
    public MessageRequest(OSCMessage message) {
        this(message, 0, null, 0);
    }

    /**
     * Constructor with delay.
     * Message will be delayed by specified milliseconds.
     *
     * @param message The OSC message to send
     * @param delayMs Delay in milliseconds (0 = immediate)
     */
    public MessageRequest(OSCMessage message, long delayMs) {
        this(message, delayMs, null, 0);
    }

    /**
     * Constructor with delay and routing.
     *
     * @param message The OSC message to send
     * @param delayMs Delay in milliseconds (0 = immediate)
     * @param targetOutputId Target output ID (null = current output only)
     */
    public MessageRequest(OSCMessage message, long delayMs, String targetOutputId) {
        this(message, delayMs, targetOutputId, 0);
    }

    /**
     * Full constructor with delay, routing, and previous delay tracking.
     *
     * @param message The OSC message to send
     * @param delayMs Delay in milliseconds (0 = immediate)
     * @param targetOutputId Target output ID (null = current output only)
     * @param previousDelay Previously applied delay (used to prevent re-delaying during playback)
     */
    public MessageRequest(OSCMessage message, long delayMs, String targetOutputId, long previousDelay) {
        this.message = message;
        this.delayMs = delayMs;
        this.targetOutputId = targetOutputId;
        this.previousDelay = previousDelay;
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
     * Set the delay in milliseconds.
     * @param delayMs Delay in milliseconds (0 = immediate)
     * @return
     */
    public void setDelayMs(long delayMs) {
        this.delayMs = delayMs;
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

    /**
     * Get the previously applied delay.
     * @return Previously applied delay in milliseconds
     */
    public long getPreviousDelay() {
        return previousDelay;
    }

    /**
     * Check if this message was previously delayed.
     * @return true if previousDelay > 0
     */
    public boolean wasPreviouslyDelayed() {
        return previousDelay > 0;
    }
}
