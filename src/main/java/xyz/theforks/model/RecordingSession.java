package xyz.theforks.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RecordingSession {
    private String name;
    private List<OSCMessageRecord> messages;
    private long startTime;

    // Default constructor for Jackson
    public RecordingSession() {
        this.messages = new ArrayList<>();
        this.startTime = System.currentTimeMillis();
    }

    @JsonCreator
    public RecordingSession(
        @JsonProperty("name") String name,
        @JsonProperty("messages") List<OSCMessageRecord> messages,
        @JsonProperty("startTime") long startTime) {
        this.name = name;
        this.messages = messages != null ? messages : new ArrayList<>();
        this.startTime = startTime;
    }

    public RecordingSession(String name) {
        this.name = name;
        this.messages = new ArrayList<>();
        this.startTime = System.currentTimeMillis();
    }

    public void addMessage(OSCMessageRecord message) {
        messages.add(message);
    }

    // Getters and setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<OSCMessageRecord> getMessages() { return messages; }
    public void setMessages(List<OSCMessageRecord> messages) { this.messages = messages; }
    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }
}