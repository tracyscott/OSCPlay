package xyz.theforks.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SessionMetadata {
    private String sessionName;
    private String audioFile;

    public SessionMetadata() {
    }

    @JsonCreator
    public SessionMetadata(
        @JsonProperty("sessionName") String sessionName,
        @JsonProperty("audioFile") String audioFile) {
        this.sessionName = sessionName;
        this.audioFile = audioFile;
    }

    // Getters and setters
    public String getSessionName() { return sessionName; }
    public void setSessionName(String sessionName) { this.sessionName = sessionName; }
    public String getAudioFile() { return audioFile; }
    public void setAudioFile(String audioFile) { this.audioFile = audioFile; }
}
