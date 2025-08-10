package xyz.theforks.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class OSCProxyServiceTest {

    @TempDir
    Path tempDir;
    
    private OSCProxyService proxyService;

    @BeforeEach
    void setUp() {
        // Set up proxy service with temporary recordings directory
        proxyService = new OSCProxyService();
        proxyService.setRecordingsDir(tempDir.resolve("recordings").toString());
    }

    @Test
    void testConstructor() {
        assertNotNull(proxyService.getInputService());
        assertNotNull(proxyService.getOutputService());
        assertNotNull(proxyService.messageCountProperty());
        assertEquals(0, proxyService.messageCountProperty().get());
    }

    @Test
    void testSetAndGetHostPort() {
        String testHost = "192.168.1.100";
        int testInPort = 8001;
        int testOutPort = 9001;
        
        proxyService.setInHost(testHost);
        proxyService.setInPort(testInPort);
        proxyService.setOutHost(testHost);
        proxyService.setOutPort(testOutPort);
        
        // Verify through the input service (output service doesn't expose getters)
        assertEquals(testHost, proxyService.getInputService().getInHost());
        assertEquals(testInPort, proxyService.getInputService().getInPort());
    }

    @Test
    void testRecordingDirectoryCreation() {
        // The constructor should create the recordings directory
        File recordingsDir = new File(proxyService.getRecordingsDir());
        assertTrue(recordingsDir.exists());
        assertTrue(recordingsDir.isDirectory());
    }

    @Test
    void testGetRecordedSessionsEmptyDirectory() {
        // Clear any existing files first
        File recordingsDir = new File(proxyService.getRecordingsDir());
        if (recordingsDir.exists()) {
            File[] files = recordingsDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
        }
        
        List<String> sessions = proxyService.getRecordedSessions();
        assertNotNull(sessions);
        assertTrue(sessions.isEmpty());
    }

    @Test
    void testGetRecordedSessionsWithFiles() throws IOException {
        // Clear existing files first
        File recordingsDir = new File(proxyService.getRecordingsDir());
        if (recordingsDir.exists()) {
            File[] files = recordingsDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
        }
        recordingsDir.mkdirs();
        
        // Create some test session files
        Files.createFile(recordingsDir.toPath().resolve("session1.json"));
        Files.createFile(recordingsDir.toPath().resolve("session2.json"));
        Files.createFile(recordingsDir.toPath().resolve("not-a-session.txt")); // Should be ignored
        
        List<String> sessions = proxyService.getRecordedSessions();
        assertNotNull(sessions);
        assertEquals(2, sessions.size());
        assertTrue(sessions.contains("session1"));
        assertTrue(sessions.contains("session2"));
        assertFalse(sessions.contains("not-a-session"));
    }

    @Test
    void testStartAndStopRecording() throws IOException {
        String sessionName = "test-recording";
        
        // Start recording
        proxyService.startRecording(sessionName);
        
        // Verify recording state
        assertEquals(0, proxyService.messageCountProperty().get());
        
        // Stop recording
        proxyService.stopRecording();
        
        // Verify session file was created
        File sessionFile = new File(proxyService.getRecordingsDir(), sessionName + ".json");
        assertTrue(sessionFile.exists());
        
        // Verify it appears in the sessions list
        List<String> sessions = proxyService.getRecordedSessions();
        assertTrue(sessions.contains(sessionName));
    }

    @Test
    void testStopRecordingWithoutStarting() {
        // Should not throw exception when stopping without starting
        assertDoesNotThrow(() -> proxyService.stopRecording());
    }

    @Test
    void testMultipleRecordingSessions() throws IOException {
        // Clear existing files first
        File recordingsDir = new File(proxyService.getRecordingsDir());
        if (recordingsDir.exists()) {
            File[] files = recordingsDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
        }
        
        String session1 = "recording1";
        String session2 = "recording2";
        
        // Record first session
        proxyService.startRecording(session1);
        proxyService.stopRecording();
        
        // Record second session
        proxyService.startRecording(session2);
        proxyService.stopRecording();
        
        // Verify both sessions exist
        List<String> sessions = proxyService.getRecordedSessions();
        assertTrue(sessions.contains(session1));
        assertTrue(sessions.contains(session2));
        assertEquals(2, sessions.size());
    }

    @Test
    void testRecordingOverwritesPrevious() throws IOException {
        String sessionName = "overwrite-test";
        
        // Create first recording
        proxyService.startRecording(sessionName);
        proxyService.stopRecording();
        
        File sessionFile = new File(proxyService.getRecordingsDir(), sessionName + ".json");
        long firstModified = sessionFile.lastModified();
        
        // Wait a bit to ensure timestamp difference
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Create second recording with same name
        proxyService.startRecording(sessionName);
        proxyService.stopRecording();
        
        long secondModified = sessionFile.lastModified();
        assertTrue(secondModified > firstModified);
        
        // Should still only have one session with this name
        List<String> sessions = proxyService.getRecordedSessions();
        long count = sessions.stream().filter(s -> s.equals(sessionName)).count();
        assertEquals(1, count);
    }

    @Test
    void testProxyStartStopCycle() throws IOException {
        // This tests the basic proxy lifecycle without actual network operations
        proxyService.setInHost("127.0.0.1");
        proxyService.setInPort(8000);
        proxyService.setOutHost("127.0.0.1");
        proxyService.setOutPort(9000);
        
        // Should not throw exceptions
        assertDoesNotThrow(() -> {
            proxyService.startProxy();
            proxyService.stopProxy();
        });
        
        // Multiple start/stop cycles should work
        assertDoesNotThrow(() -> {
            proxyService.startProxy();
            proxyService.startProxy(); // Should stop previous first
            proxyService.stopProxy();
            proxyService.stopProxy(); // Should be safe to call multiple times
        });
    }
}