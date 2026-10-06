package xyz.theforks.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.illposed.osc.OSCMessage;

import xyz.theforks.model.RecordingSession;
import xyz.theforks.util.DataDirectory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.regex.PatternSyntaxException;

class OSCProxyServiceTest {

    /**
     * recordMessage updates a JavaFX property through Platform.runLater, so the
     * toolkit has to be up for the tests that exercise the full record path. On a
     * machine where it cannot start, those tests are skipped rather than failed.
     */
    private static boolean toolkitRunning;

    @BeforeAll
    static void startToolkit() {
        try {
            javafx.application.Platform.startup(() -> { });
            toolkitRunning = true;
        } catch (IllegalStateException alreadyStarted) {
            toolkitRunning = true;
        } catch (Throwable noToolkit) {
            toolkitRunning = false;
        }
    }

    @TempDir
    Path tempDir;

    private OSCProxyService proxyService;

    @BeforeEach
    void setUp() {
        // Use temp directory instead of real data directory
        DataDirectory.setTestOverrideDir(tempDir);

        // Set up proxy service (uses DataDirectory for recordings)
        proxyService = new OSCProxyService();
    }

    @AfterEach
    void tearDown() {
        // Reset to default directory after each test
        DataDirectory.setTestOverrideDir(null);
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
        List<String> sessions = proxyService.getRecordedSessions();
        assertNotNull(sessions);
        assertTrue(sessions.isEmpty());
    }

    @Test
    void testGetRecordedSessionsWithFiles() throws IOException {
        File recordingsDir = new File(proxyService.getRecordingsDir());
        recordingsDir.mkdirs();

        // Create some test session directories with data.json
        File session1Dir = recordingsDir.toPath().resolve("session1").toFile();
        session1Dir.mkdirs();
        Files.createFile(session1Dir.toPath().resolve("data.json"));

        File session2Dir = recordingsDir.toPath().resolve("session2").toFile();
        session2Dir.mkdirs();
        Files.createFile(session2Dir.toPath().resolve("data.json"));

        // Create a directory without data.json - should be ignored
        File invalidDir = recordingsDir.toPath().resolve("invalid-session").toFile();
        invalidDir.mkdirs();

        Files.createFile(recordingsDir.toPath().resolve("not-a-session.txt")); // Should be ignored

        List<String> sessions = proxyService.getRecordedSessions();
        assertNotNull(sessions);
        assertEquals(2, sessions.size());
        assertTrue(sessions.contains("session1"));
        assertTrue(sessions.contains("session2"));
        assertFalse(sessions.contains("invalid-session"));
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

        // Verify session directory and data file were created (new structure)
        File sessionDir = new File(proxyService.getRecordingsDir(), sessionName);
        assertTrue(sessionDir.exists());
        assertTrue(sessionDir.isDirectory());

        File dataFile = new File(sessionDir, "data.json");
        assertTrue(dataFile.exists());

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

        File sessionDir = new File(proxyService.getRecordingsDir(), sessionName);
        File dataFile = new File(sessionDir, "data.json");
        long firstModified = dataFile.lastModified();

        // Wait a bit to ensure timestamp difference
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Create second recording with same name
        proxyService.startRecording(sessionName);
        proxyService.stopRecording();

        long secondModified = dataFile.lastModified();
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

    private static OSCMessage msg(String address) {
        return new OSCMessage(address, Collections.singletonList(1.0f));
    }

    @Test
    void testNoFilterRecordsEveryAddress() {
        proxyService.startRecording("unfiltered");

        assertNull(proxyService.getRecordFilter());
        assertTrue(proxyService.matchesRecordFilter(msg("/mag1/xyz")));
        assertTrue(proxyService.matchesRecordFilter(msg("/anything/at/all")));
    }

    @Test
    void testBlankFilterIsTreatedAsNoFilter() {
        proxyService.startRecording("blank-filter", "   ");

        assertNull(proxyService.getRecordFilter());
        assertTrue(proxyService.matchesRecordFilter(msg("/mag3/xyz")));
    }

    @Test
    void testFilterKeepsOnlyMatchingAddresses() {
        // One Interlace tower while all three are streaming.
        proxyService.startRecording("tower2", "/mag2/xyz");

        assertEquals("/mag2/xyz", proxyService.getRecordFilter());
        assertTrue(proxyService.matchesRecordFilter(msg("/mag2/xyz")));
        assertFalse(proxyService.matchesRecordFilter(msg("/mag1/xyz")));
        assertFalse(proxyService.matchesRecordFilter(msg("/mag3/xyz")));
    }

    @Test
    void testFilterMatchesWholeAddress() {
        // As with node address patterns, a partial match is not enough.
        proxyService.startRecording("whole", "/mag2/xyz");

        assertFalse(proxyService.matchesRecordFilter(msg("/mag2/xyzz")));
        assertFalse(proxyService.matchesRecordFilter(msg("/prefix/mag2/xyz")));
    }

    @Test
    void testFilterAcceptsRegex() {
        proxyService.startRecording("two-towers", "/mag[23]/xyz");

        assertTrue(proxyService.matchesRecordFilter(msg("/mag2/xyz")));
        assertTrue(proxyService.matchesRecordFilter(msg("/mag3/xyz")));
        assertFalse(proxyService.matchesRecordFilter(msg("/mag1/xyz")));
    }

    @Test
    void testInvalidFilterIsRejected() {
        assertThrows(PatternSyntaxException.class,
            () -> proxyService.startRecording("bad-filter", "/mag[2/xyz"));
    }

    @Test
    void testFilteredRecordingOnlyStoresMatchingMessages() throws IOException {
        assumeTrue(toolkitRunning, "JavaFX toolkit unavailable");
        proxyService.startRecording("tower2-only", "/mag2/xyz");
        proxyService.recordIfMatching(msg("/mag1/xyz"));
        proxyService.recordIfMatching(msg("/mag2/xyz"));
        proxyService.recordIfMatching(msg("/mag3/xyz"));
        proxyService.recordIfMatching(msg("/mag2/xyz"));
        proxyService.stopRecording();

        RecordingSession saved = RecordingSession.loadSession("tower2-only");
        assertEquals(2, saved.getMessages().size());
        saved.getMessages().forEach(
            m -> assertEquals("/mag2/xyz", m.getAddress()));
        // The filter is kept with the recording, so its contents are self-describing.
        assertEquals("/mag2/xyz", saved.getAddressFilter());
    }

    @Test
    void testFilterIsClearedAfterStopping() {
        proxyService.startRecording("filtered", "/mag2/xyz");
        proxyService.stopRecording();

        assertNull(proxyService.getRecordFilter());
    }

    @Test
    void testRecordIfMatchingIgnoredWhenNotRecording() {
        // No exception, and nothing to save.
        proxyService.recordIfMatching(msg("/mag2/xyz"));

        assertEquals(0, proxyService.messageCountProperty().get());
    }

    @Test
    void testUnfilteredRecordingHasNoStoredFilter() throws IOException {
        assumeTrue(toolkitRunning, "JavaFX toolkit unavailable");
        proxyService.startRecording("everything");
        proxyService.recordIfMatching(msg("/a"));
        proxyService.recordIfMatching(msg("/b"));
        proxyService.stopRecording();

        RecordingSession saved = RecordingSession.loadSession("everything");
        assertEquals(2, saved.getMessages().size());
        assertNull(saved.getAddressFilter());
    }
}