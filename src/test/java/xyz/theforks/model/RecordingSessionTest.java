package xyz.theforks.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

class RecordingSessionTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // Create recordings directory in temp location for testing
        File recordingsDir = tempDir.resolve("recordings").toFile();
        recordingsDir.mkdirs();
    }

    @Test
    void testDefaultConstructor() {
        RecordingSession session = new RecordingSession();
        
        assertNull(session.getName());
        assertNotNull(session.getMessages());
        assertTrue(session.getMessages().isEmpty());
        assertTrue(session.getStartTime() > 0);
        assertTrue(System.currentTimeMillis() - session.getStartTime() < 1000); // Within 1 second
    }

    @Test
    void testConstructorWithName() {
        String sessionName = "test-session";
        RecordingSession session = new RecordingSession(sessionName);
        
        assertEquals(sessionName, session.getName());
        assertEquals(sessionName + ".json", session.getFilename());
        assertNotNull(session.getMessages());
        assertTrue(session.getMessages().isEmpty());
        assertTrue(session.getStartTime() > 0);
    }

    @Test
    void testJsonConstructor() {
        String name = "json-session";
        List<OSCMessageRecord> messages = new ArrayList<>();
        messages.add(new OSCMessageRecord("/test", new Object[]{1.0f}));
        long startTime = 1234567890L;
        
        RecordingSession session = new RecordingSession(name, messages, startTime);
        
        assertEquals(name, session.getName());
        assertEquals(messages, session.getMessages());
        assertEquals(1, session.getMessages().size());
        assertEquals(startTime, session.getStartTime());
    }

    @Test
    void testJsonConstructorWithNullMessages() {
        String name = "null-messages-session";
        long startTime = 1234567890L;
        
        RecordingSession session = new RecordingSession(name, null, startTime);
        
        assertEquals(name, session.getName());
        assertNotNull(session.getMessages());
        assertTrue(session.getMessages().isEmpty());
        assertEquals(startTime, session.getStartTime());
    }

    @Test
    void testAddMessage() {
        RecordingSession session = new RecordingSession("test-add");
        OSCMessageRecord message1 = new OSCMessageRecord("/test/1", new Object[]{1.0f});
        OSCMessageRecord message2 = new OSCMessageRecord("/test/2", new Object[]{2.0f, "hello"});
        
        session.addMessage(message1);
        assertEquals(1, session.getMessages().size());
        assertEquals(message1, session.getMessages().get(0));
        
        session.addMessage(message2);
        assertEquals(2, session.getMessages().size());
        assertEquals(message2, session.getMessages().get(1));
    }

    @Test
    void testSettersAndGetters() {
        RecordingSession session = new RecordingSession();
        
        String name = "setter-test";
        List<OSCMessageRecord> messages = new ArrayList<>();
        messages.add(new OSCMessageRecord("/test", new Object[]{3.14f}));
        long startTime = 9876543210L;
        
        session.setName(name);
        session.setMessages(messages);
        session.setStartTime(startTime);
        
        assertEquals(name, session.getName());
        assertEquals(name + ".json", session.getFilename());
        assertEquals(messages, session.getMessages());
        assertEquals(startTime, session.getStartTime());
    }

    @Test
    void testLoadSessionNonExistentFile() throws IOException {
        // This will look for nonexistent.json in the default recordings dir which won't exist
        RecordingSession session = RecordingSession.loadSession("nonexistent");
        assertNull(session);
    }

    @Test
    void testSerializationRoundTrip() throws IOException {
        // Create a session with test data
        RecordingSession originalSession = new RecordingSession("serialization-test");
        originalSession.addMessage(new OSCMessageRecord("/test/1", new Object[]{1.0f, "hello"}));
        originalSession.addMessage(new OSCMessageRecord("/test/2", new Object[]{2.0f, 42}));
        
        // Serialize to JSON
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(originalSession);
        
        // Deserialize back
        RecordingSession deserializedSession = mapper.readValue(json, RecordingSession.class);
        
        // Verify data integrity
        assertEquals(originalSession.getName(), deserializedSession.getName());
        assertEquals(originalSession.getStartTime(), deserializedSession.getStartTime());
        assertEquals(originalSession.getMessages().size(), deserializedSession.getMessages().size());
        
        // Check first message
        OSCMessageRecord originalMsg = originalSession.getMessages().get(0);
        OSCMessageRecord deserializedMsg = deserializedSession.getMessages().get(0);
        assertEquals(originalMsg.getAddress(), deserializedMsg.getAddress());
        assertEquals(originalMsg.getTimestamp(), deserializedMsg.getTimestamp());
        
        // JSON deserialization converts Float to Double, so we need to handle this
        Object[] originalArgs = originalMsg.getArguments();
        Object[] deserializedArgs = deserializedMsg.getArguments();
        assertEquals(originalArgs.length, deserializedArgs.length);
        
        // Check each argument with type conversion handling
        assertEquals(((Float) originalArgs[0]).doubleValue(), ((Double) deserializedArgs[0]).doubleValue(), 0.001);
        assertEquals(originalArgs[1], deserializedArgs[1]); // String should be unchanged
    }

    @Test
    void testSaveAndLoadSettings() throws IOException {
        String sessionName = "settings-test";
        String audioFileName = "test-audio.wav";

        // Create and save settings
        RecordingSession session = new RecordingSession(sessionName);
        SessionSettings settings = new SessionSettings(audioFileName);
        session.saveSettings(settings);

        // Load settings back
        SessionSettings loadedSettings = RecordingSession.loadSettings(sessionName);
        assertNotNull(loadedSettings);
        assertEquals(audioFileName, loadedSettings.getAudioFileName());
    }

    @Test
    void testLoadSettingsNonExistent() throws IOException {
        SessionSettings settings = RecordingSession.loadSettings("nonexistent-session");
        assertNull(settings);
    }
}