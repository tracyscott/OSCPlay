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

    /**
     * Save this session to the new directory structure.
     * Creates: ~/Documents/OSCPlay/recordings/{sessionName}/data.json
     */
    public void save() throws IOException {
        final ObjectMapper objectMapper = new ObjectMapper();

        // Create session directory if it doesn't exist
        File sessionDir = DataDirectory.getSessionDir(name).toFile();
        if (!sessionDir.exists()) {
            sessionDir.mkdirs();
        }

        // Save session data
        File dataFile = DataDirectory.getSessionDataFile(name).toFile();
        objectMapper.writeValue(dataFile, this);
        System.out.println("Saved recording to: " + dataFile.getAbsolutePath());
    }

    /**
     * Load a session from the directory structure.
     * Loads: ~/Documents/OSCPlay/recordings/{sessionName}/data.json
     */
    static public RecordingSession loadSession(String sessionName) throws IOException {
        final ObjectMapper objectMapper = new ObjectMapper();

        File dataFile = DataDirectory.getSessionDataFile(sessionName).toFile();
        if (!dataFile.exists()) {
            System.err.println("Recording file not found: " + sessionName);
            return null;
        }

        RecordingSession session = objectMapper.readValue(dataFile, RecordingSession.class);

        if (session == null || session.getMessages() == null || session.getMessages().isEmpty()) {
            System.err.println("Invalid session data");
            return null;
        }

        return session;
    }

    /**
     * Save settings for this session.
     * @param settings The settings to save
     */
    public void saveSettings(SessionSettings settings) throws IOException {
        final ObjectMapper objectMapper = new ObjectMapper();

        // Create session directory if it doesn't exist
        File sessionDir = DataDirectory.getSessionDir(name).toFile();
        if (!sessionDir.exists()) {
            sessionDir.mkdirs();
        }

        File settingsFile = DataDirectory.getSessionSettingsFile(name).toFile();
        objectMapper.writeValue(settingsFile, settings);
    }

    /**
     * Load settings for a session.
     * @param sessionName The session name
     * @return The settings, or null if not found
     */
    static public SessionSettings loadSettings(String sessionName) throws IOException {
        final ObjectMapper objectMapper = new ObjectMapper();

        File settingsFile = DataDirectory.getSessionSettingsFile(sessionName).toFile();
        if (!settingsFile.exists()) {
            return null;
        }

        return objectMapper.readValue(settingsFile, SessionSettings.class);
    }
}