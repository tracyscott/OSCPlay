package xyz.theforks.model;

/**
 * Defines whether rewrite handlers should be applied during OSC message playback.
 */
public enum PlaybackMode {
    /**
     * Play recorded messages without applying any rewrite handlers.
     * Messages are sent exactly as they were recorded.
     */
    WITHOUT_REWRITE("Play Without Rewrite"),
    
    /**
     * Apply rewrite handlers to recorded messages during playback.
     * This allows recorded sessions to be modified in real-time during playback.
     */
    WITH_REWRITE("Play With Rewrite");
    
    private final String displayName;
    
    PlaybackMode(String displayName) {
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