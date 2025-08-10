package xyz.theforks.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Configuration settings for the OSCPlay application.
 * This includes recording/playback modes and other application-wide preferences.
 */
public class ApplicationConfig {
    
    private RecordingMode recordingMode;
    private PlaybackMode playbackMode;
    private String lastHandlerConfigFile;
    
    public ApplicationConfig() {
        this.recordingMode = RecordingMode.PRE_REWRITE;
        this.playbackMode = PlaybackMode.WITHOUT_REWRITE;
        this.lastHandlerConfigFile = null;
    }
    
    @JsonCreator
    public ApplicationConfig(
            @JsonProperty("recordingMode") RecordingMode recordingMode,
            @JsonProperty("playbackMode") PlaybackMode playbackMode,
            @JsonProperty("lastHandlerConfigFile") String lastHandlerConfigFile) {
        this.recordingMode = recordingMode != null ? recordingMode : RecordingMode.PRE_REWRITE;
        this.playbackMode = playbackMode != null ? playbackMode : PlaybackMode.WITHOUT_REWRITE;
        this.lastHandlerConfigFile = lastHandlerConfigFile;
    }
    
    public RecordingMode getRecordingMode() {
        return recordingMode;
    }
    
    public void setRecordingMode(RecordingMode recordingMode) {
        this.recordingMode = recordingMode;
    }
    
    public PlaybackMode getPlaybackMode() {
        return playbackMode;
    }
    
    public void setPlaybackMode(PlaybackMode playbackMode) {
        this.playbackMode = playbackMode;
    }
    
    public String getLastHandlerConfigFile() {
        return lastHandlerConfigFile;
    }
    
    public void setLastHandlerConfigFile(String lastHandlerConfigFile) {
        this.lastHandlerConfigFile = lastHandlerConfigFile;
    }
}