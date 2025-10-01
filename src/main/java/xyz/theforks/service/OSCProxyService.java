package xyz.theforks.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCSerializeException;

import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import xyz.theforks.model.OSCMessageRecord;
import xyz.theforks.model.RecordingSession;
import xyz.theforks.nodes.NodeChain;
import xyz.theforks.nodes.OSCNode;
import xyz.theforks.util.DataDirectory;

public class OSCProxyService {

    private OSCInputService inputService;
    private final Map<String, OSCOutputService> outputs;
    private RecordingSession currentSession;
    private boolean isRecording = false;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final IntegerProperty messageCount = new SimpleIntegerProperty(0);
    private ProjectManager projectManager;

    public OSCProxyService() {
        this(null);
    }

    public OSCProxyService(ProjectManager projectManager) {
        this.projectManager = projectManager;
        inputService = new OSCInputService();
        inputService.setMessageHandler(new MessageHandlerClass() {
            @Override
            public void handleMessage(OSCMessage message) {
                OSCProxyService.this.handleMessage(message);
            }
        });

        outputs = new HashMap<>();
        // Create default output for backward compatibility
        OSCOutputService defaultOutput = new OSCOutputService("default");
        outputs.put(defaultOutput.getId(), defaultOutput);

        DataDirectory.createDirectories();
    }

    public OSCInputService getInputService() {
        return inputService;
    }

    /**
     * Get the default output service (for backward compatibility).
     * @return The default output service
     */
    public OSCOutputService getOutputService() {
        return outputs.get("default");
    }

    /**
     * Get an output service by ID.
     * @param id The output ID
     * @return The output service, or null if not found
     */
    public OSCOutputService getOutput(String id) {
        return outputs.get(id);
    }

    /**
     * Get all output services.
     * @return List of all output services
     */
    public List<OSCOutputService> getOutputs() {
        return new ArrayList<>(outputs.values());
    }

    /**
     * Add a new output service.
     * @param output The output service to add
     * @return true if added, false if ID already exists
     */
    public boolean addOutput(OSCOutputService output) {
        if (outputs.containsKey(output.getId())) {
            return false;
        }
        outputs.put(output.getId(), output);
        return true;
    }

    /**
     * Remove an output service.
     * @param id The output ID to remove
     * @return true if removed, false if not found or is default output
     */
    public boolean removeOutput(String id) {
        if ("default".equals(id)) {
            return false; // Don't allow removing default output
        }
        OSCOutputService output = outputs.remove(id);
        if (output != null) {
            output.stop();
            return true;
        }
        return false;
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

    /**
     * Set output host for default output (backward compatibility).
     */
    public void setOutHost(String host) {
        OSCOutputService defaultOutput = getOutputService();
        if (defaultOutput != null) {
            defaultOutput.setOutHost(host);
        }
    }

    /**
     * Set output port for default output (backward compatibility).
     */
    public void setOutPort(int port) {
        OSCOutputService defaultOutput = getOutputService();
        if (defaultOutput != null) {
            defaultOutput.setOutPort(port);
        }
    }

    public void startProxy() throws IOException {
        stopProxy();
        inputService.start();

        // Start all enabled outputs
        for (OSCOutputService output : outputs.values()) {
            if (output.isEnabled()) {
                output.start();
            }
        }
        System.out.println("Proxy started with " + outputs.size() + " output(s)");
    }

    public void stopProxy() {
        inputService.stop();

        // Stop all outputs
        for (OSCOutputService output : outputs.values()) {
            output.stop();
        }
        System.out.println("Proxy stopped");
    }

    private void handleMessage(OSCMessage oscMessage) {
        try {
            // Always record raw input messages (before any processing)
            if (isRecording && currentSession != null && oscMessage != null) {
                recordMessage(oscMessage);
            }

            // Send to all enabled outputs
            // Each output applies its own node chain
            for (OSCOutputService output : outputs.values()) {
                if (output.isEnabled() && output.isStarted()) {
                    output.send(oscMessage);
                }
            }

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
            session.save();
        } catch (IOException e) {
            System.err.println("Error saving session: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public List<String> getRecordedSessions() {
        List<String> sessions = new ArrayList<>();
        File dir = getRecordingsDirFile();
        if (dir.exists()) {
            File[] entries = dir.listFiles();
            if (entries != null) {
                for (File entry : entries) {
                    if (entry.isDirectory()) {
                        // Check if directory contains data.json
                        File dataFile = new File(entry, "data.json");
                        if (dataFile.exists()) {
                            sessions.add(entry.getName());
                        }
                    }
                }
            }
        }
        return sessions;
    }

    public String getRecordingsDir() {
        if (projectManager != null && projectManager.hasOpenProject()) {
            return projectManager.getRecordingsDir().toString();
        }
        return DataDirectory.getRecordingsDir().toString();
    }

    private File getRecordingsDirFile() {
        if (projectManager != null && projectManager.hasOpenProject()) {
            return projectManager.getRecordingsDir().toFile();
        }
        return DataDirectory.getRecordingsDirFile();
    }

    public void setProjectManager(ProjectManager projectManager) {
        this.projectManager = projectManager;
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
     * Get the node chain for the default output (backward compatibility).
     * @return The default output's node chain
     */
    public NodeChain getNodeChain() {
        OSCOutputService defaultOutput = getOutputService();
        return defaultOutput != null ? defaultOutput.getNodeChain() : null;
    }

    /**
     * Get the node chain for a specific output.
     * @param outputId The output ID
     * @return The output's node chain, or null if output not found
     */
    public NodeChain getNodeChain(String outputId) {
        OSCOutputService output = outputs.get(outputId);
        return output != null ? output.getNodeChain() : null;
    }

    /**
     * Register a node with the default output (backward compatibility).
     * @param node The node to register
     */
    public void registerNode(OSCNode node) {
        OSCOutputService defaultOutput = getOutputService();
        if (defaultOutput != null) {
            defaultOutput.getNodeChain().registerNode(node);
        }
    }

    /**
     * Register a node with a specific output.
     * @param outputId The output ID
     * @param node The node to register
     */
    public void registerNode(String outputId, OSCNode node) {
        OSCOutputService output = outputs.get(outputId);
        if (output != null) {
            output.getNodeChain().registerNode(node);
        }
    }

    /**
     * Unregister a node from the default output (backward compatibility).
     * @param node The node to unregister
     */
    public void unregisterNode(OSCNode node) {
        OSCOutputService defaultOutput = getOutputService();
        if (defaultOutput != null) {
            defaultOutput.getNodeChain().unregisterNode(node);
        }
    }

    /**
     * Unregister a node from a specific output.
     * @param outputId The output ID
     * @param node The node to unregister
     */
    public void unregisterNode(String outputId, OSCNode node) {
        OSCOutputService output = outputs.get(outputId);
        if (output != null) {
            output.getNodeChain().unregisterNode(node);
        }
    }

    /**
     * Clear all nodes from the default output (backward compatibility).
     */
    public void clearNodes() {
        OSCOutputService defaultOutput = getOutputService();
        if (defaultOutput != null) {
            defaultOutput.getNodeChain().clearNodes();
        }
    }

    /**
     * Clear all nodes from a specific output.
     * @param outputId The output ID
     */
    public void clearNodes(String outputId) {
        OSCOutputService output = outputs.get(outputId);
        if (output != null) {
            output.getNodeChain().clearNodes();
        }
    }

    /**
     * Set the list of nodes for the default output (backward compatibility).
     * @param nodes The new list of nodes
     */
    public void setNodes(List<OSCNode> nodes) {
        OSCOutputService defaultOutput = getOutputService();
        if (defaultOutput != null) {
            defaultOutput.getNodeChain().setNodes(nodes);
        }
    }

    /**
     * Set the list of nodes for a specific output.
     * @param outputId The output ID
     * @param nodes The new list of nodes
     */
    public void setNodes(String outputId, List<OSCNode> nodes) {
        OSCOutputService output = outputs.get(outputId);
        if (output != null) {
            output.getNodeChain().setNodes(nodes);
        }
    }
}
