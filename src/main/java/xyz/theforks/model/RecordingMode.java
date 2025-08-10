package xyz.theforks.model;

/**
 * Defines when rewrite handlers should be applied during OSC message recording.
 */
public enum RecordingMode {
    /**
     * Record original OSC messages before any rewrite handlers are applied.
     * This preserves the raw messages as they were received.
     */
    PRE_REWRITE("Record Original Messages"),
    
    /**
     * Record OSC messages after rewrite handlers have been applied.
     * This captures the modified messages that would be sent to the destination.
     */
    POST_REWRITE("Record Processed Messages");
    
    private final String displayName;
    
    RecordingMode(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}