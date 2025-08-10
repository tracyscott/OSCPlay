package xyz.theforks.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCSerializeException;

import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import xyz.theforks.model.OSCMessageRecord;
import xyz.theforks.model.RecordingSession;
import xyz.theforks.model.RecordingMode;
import xyz.theforks.rewrite.RewriteEngine;
import xyz.theforks.rewrite.RewriteHandler;

public class OSCProxyService {

    private OSCInputService inputService;
    private OSCOutputService outputService;
    private RecordingSession currentSession;
    private boolean isRecording = false;
    private RecordingMode recordingMode = RecordingMode.PRE_REWRITE;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private String recordingsDir = "recordings";
    private final IntegerProperty messageCount = new SimpleIntegerProperty(0);
    private final RewriteEngine rewriteEngine;

    public OSCProxyService() {
        inputService = new OSCInputService();
        inputService.setMessageHandler(new MessageHandlerClass() {
            @Override
            public void handleMessage(OSCMessage message) {
                OSCProxyService.this.handleMessage(message);
            }
        });
        outputService = new OSCOutputService();
        rewriteEngine = new RewriteEngine(RewriteEngine.Context.PROXY);
    
        createDirectories();
    }

    public OSCInputService getInputService() {
        return inputService;
    }

    public OSCOutputService getOutputService() {
        return outputService;
    }

    private void createDirectories() {
        new File(recordingsDir).mkdirs();
    }

    public IntegerProperty messageCountProperty() {
        return messageCount;
    }

    public void setInPort(int port) {
        inputService.setInPort(port);
    }

    public void setInHost(String host) {
        inputService.setInHost(host);
    }

    public void setOutHost(String host) {
        outputService.setOutHost(host);
    }

    public void setOutPort(int port) {
        outputService.setOutPort(port);
    }

    public void startProxy() throws IOException {
        stopProxy();
        inputService.start();
        outputService.start();
        System.out.println("Proxy started");
    }

    public void stopProxy() {
        inputService.stop();
        outputService.stop();
        System.out.println("Proxy stopped");
    }

    private void handleMessage(OSCMessage oscMessage) {
        try {
            OSCMessage messageToRecord = null;
            OSCMessage messageToSend = oscMessage;
            
            // Handle recording based on mode
            if (recordingMode == RecordingMode.PRE_REWRITE) {
                messageToRecord = oscMessage; // Record original message
            }
            
            // Apply rewrite handlers
            messageToSend = rewriteEngine.processMessage(oscMessage);
            if (messageToSend == null) {
                // Message was cancelled by a rewrite handler
                return;
            }
            
            // Handle recording based on mode
            if (recordingMode == RecordingMode.POST_REWRITE) {
                messageToRecord = messageToSend; // Record processed message
            }
            
            // Record the message if recording is active
            if (isRecording && currentSession != null && messageToRecord != null) {
                recordMessage(messageToRecord);
            }
            
            // Send the processed message
            outputService.send(messageToSend);
        } catch (IOException | OSCSerializeException e) {
            System.err.println("Error handling message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void startRecording(String sessionName) {
        currentSession = new RecordingSession(sessionName);
        isRecording = true;
        messageCount.set(0);
        System.out.println("Started recording session: " + sessionName);
    }

    public void stopRecording() {
        if (isRecording && currentSession != null) {
            saveSession(currentSession);
            isRecording = false;
            currentSession = null;
            System.out.println("Stopped recording. Total messages: " + messageCount.get());
        }
    }

    private void saveSession(RecordingSession session) {
        try {
            File dir = new File(recordingsDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File file = new File(dir, session.getName() + ".json");
            objectMapper.writeValue(file, session);
            System.out.println("Saved recording to: " + file.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Error saving session: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public List<String> getRecordedSessions() {
        List<String> sessions = new ArrayList<>();
        File dir = new File(recordingsDir);
        if (dir.exists()) {
            File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
            if (files != null) {
                for (File file : files) {
                    sessions.add(file.getName().replace(".json", ""));
                }
            }
        }
        return sessions;
    }

    public void setRecordingsDir(String recordingsDir) {
        this.recordingsDir = recordingsDir;
        createDirectories();
    }

    public String getRecordingsDir() {
        return recordingsDir;
    }
    
    /**
     * Record an OSC message to the current session.
     * @param message The message to record
     */
    private void recordMessage(OSCMessage message) {
        OSCMessageRecord record = new OSCMessageRecord(
                message.getAddress(),
                message.getArguments().toArray()
        );
        currentSession.addMessage(record);
        
        // Update message count on JavaFX thread
        Platform.runLater(() -> messageCount.set(messageCount.get() + 1));
    }
    
    /**
     * Get the current recording mode.
     * @return The recording mode
     */
    public RecordingMode getRecordingMode() {
        return recordingMode;
    }
    
    /**
     * Set the recording mode.
     * @param mode The recording mode
     */
    public void setRecordingMode(RecordingMode mode) {
        this.recordingMode = mode;
    }
    
    /**
     * Get the rewrite engine used by this proxy service.
     * @return The rewrite engine
     */
    public RewriteEngine getRewriteEngine() {
        return rewriteEngine;
    }
    
    /**
     * Register a rewrite handler with this proxy service.
     * @param handler The handler to register
     */
    public void registerRewriteHandler(RewriteHandler handler) {
        rewriteEngine.registerHandler(handler);
    }
    
    /**
     * Unregister a rewrite handler from this proxy service.
     * @param handler The handler to unregister
     */
    public void unregisterRewriteHandler(RewriteHandler handler) {
        rewriteEngine.unregisterHandler(handler);
    }
    
    /**
     * Clear all rewrite handlers from this proxy service.
     */
    public void clearRewriteHandlers() {
        rewriteEngine.clearHandlers();
    }
    
    /**
     * Set the list of rewrite handlers, replacing any existing handlers.
     * @param handlers The new list of handlers
     */
    public void setRewriteHandlers(List<RewriteHandler> handlers) {
        rewriteEngine.setHandlers(handlers);
    }
}
