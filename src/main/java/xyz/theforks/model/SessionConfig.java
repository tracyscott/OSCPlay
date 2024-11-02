package xyz.theforks.model;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SessionConfig {
    private Map<String, SessionMetadata> sessions;

    public SessionConfig() {
        this.sessions = new HashMap<>();
    }

    @JsonCreator
    public SessionConfig(@JsonProperty("sessions") Map<String, SessionMetadata> sessions) {
        this.sessions = sessions != null ? sessions : new HashMap<>();
    }

    public Map<String, SessionMetadata> getSessions() { return sessions; }
    public void setSessions(Map<String, SessionMetadata> sessions) { this.sessions = sessions; }

    public void addSession(String sessionName, String audioFile) {
        sessions.put(sessionName, new SessionMetadata(sessionName, audioFile));
    }

    public SessionMetadata getSession(String sessionName) {
        return sessions.get(sessionName);
    }
}