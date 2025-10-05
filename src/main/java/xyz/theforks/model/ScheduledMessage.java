package xyz.theforks.model;

/**
 * Represents a scheduled OSC message with timing and routing information.
 * Used by both Playback and ProxyDelayProcessor to manage delayed message delivery.
 */
public class ScheduledMessage implements Comparable<ScheduledMessage> {
    private final OSCMessageRecord record;
    private final long absoluteTimestamp;
    private final String targetOutputId;  // null = all enabled, specific = route to this output only
    private final long previousDelay;      // Delay that was applied to create this scheduled message

    /**
     * Create a scheduled message.
     *
     * @param record The OSC message record to send
     * @param absoluteTimestamp When to send the message (absolute timestamp in milliseconds)
     * @param targetOutputId Target output ID (null = all enabled outputs)
     * @param previousDelay The delay that was applied to create this scheduled message
     */
    public ScheduledMessage(OSCMessageRecord record, long absoluteTimestamp, String targetOutputId, long previousDelay) {
        this.record = record;
        this.absoluteTimestamp = absoluteTimestamp;
        this.targetOutputId = targetOutputId;
        this.previousDelay = previousDelay;
    }

    public OSCMessageRecord getRecord() {
        return record;
    }

    public long getAbsoluteTimestamp() {
        return absoluteTimestamp;
    }

    public String getTargetOutputId() {
        return targetOutputId;
    }

    public long getPreviousDelay() {
        return previousDelay;
    }

    @Override
    public int compareTo(ScheduledMessage other) {
        return Long.compare(this.absoluteTimestamp, other.absoluteTimestamp);
    }
}
