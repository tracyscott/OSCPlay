package xyz.theforks.model;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import xyz.theforks.util.DataDirectory;

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
    public String getFilename() { return name + ".json"; }
    public void setName(String name) { this.name = name; }
    public List<OSCMessageRecord> getMessages() { return messages; }
    public void setMessages(List<OSCMessageRecord> messages) { this.messages = messages; }
    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }

    static public RecordingSession loadSession(String sessionName) throws IOException {
        final ObjectMapper objectMapper = new ObjectMapper();

        File file = DataDirectory.getRecordingFile(sessionName + ".json").toFile();
        if (!file.exists()) {
            System.err.println("Recording file not found: " + file.getAbsolutePath());
            return null;
        }

        RecordingSession session = objectMapper.readValue(file, RecordingSession.class);

        if (session == null || session.getMessages() == null || session.getMessages().isEmpty()) {
            System.err.println("Invalid session data");
            return null;
        }

        return session;
    }
}