package xyz.theforks.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Settings for a recording session, stored in settings.json within the session directory.
 * Tracks metadata like associated audio files and other session-specific configuration.
 */
public class SessionSettings {
    private String audioFileName;

    public SessionSettings() {
    }

    @JsonCreator
    public SessionSettings(@JsonProperty("audioFileName") String audioFileName) {
        this.audioFileName = audioFileName;
    }

    public String getAudioFileName() {
        return audioFileName;
    }

    public void setAudioFileName(String audioFileName) {
        this.audioFileName = audioFileName;
    }
}