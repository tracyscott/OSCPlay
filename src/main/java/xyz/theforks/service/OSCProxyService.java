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
    private final IntegerProperty totalMessageCount = new SimpleIntegerProperty(0);
    private ProjectManager projectManager;
    private xyz.theforks.ui.SamplerPadUI samplerPadUI;
    private ProxyDelayProcessor delayProcessor;

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
        defaultOutput.setOutHost("127.0.0.1");
        defaultOutput.setOutPort(3030);
        defaultOutput.setEnabled(true);
        outputs.put(defaultOutput.getId(), defaultOutput);

        // Create delay processor for proxy mode
        delayProcessor = new ProxyDelayProcessor(this);

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
        // Set the delay processor for this output
        output.setDelayProcessor(delayProcessor);
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

    /**
     * Clear all outputs except default.
     * Used when switching projects to reset the output configuration.
     */
    public void clearAllOutputs() {
        // Stop and remove all non-default outputs
        List<String> idsToRemove = new ArrayList<>();
        for (String id : outputs.keySet()) {
            if (!"default".equals(id)) {
                idsToRemove.add(id);
            }
        }
        for (String id : idsToRemove) {
            OSCOutputService output = outputs.remove(id);
            if (output != null) {
                output.stop();
            }
        }
        // Clear the default output's node chain if it exists
        OSCOutputService defaultOutput = outputs.get("default");
        if (defaultOutput != null) {
            defaultOutput.getNodeChain().getNodes().clear();
        }
    }

    public IntegerProperty messageCountProperty() {
        return messageCount;
    }

    public IntegerProperty totalMessageCountProperty() {
        return totalMessageCount;
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

        // Start delay processor
        delayProcessor.start();

        // Start all enabled outputs and set their delay processor
        for (OSCOutputService output : outputs.values()) {
            output.setDelayProcessor(delayProcessor);
            if (output.isEnabled()) {
                output.start();
            }
        }
        System.out.println("Proxy started with " + outputs.size() + " output(s)");
    }

    public void stopProxy() {
        inputService.stop();

        // Stop delay processor
        if (delayProcessor != null) {
            delayProcessor.stop();
        }

        // Stop all outputs
        for (OSCOutputService output : outputs.values()) {
            output.stop();
        }
        System.out.println("Proxy stopped");
    }

    private void handleMessage(OSCMessage oscMessage) {
        try {
            // Increment total message count for all messages (including /oscplay)
            if (oscMessage != null) {
                Platform.runLater(() -> totalMessageCount.set(totalMessageCount.get() + 1));
            }

            // Check if this is an /oscplay command
            if (oscMessage != null && oscMessage.getAddress().startsWith("/oscplay")) {
                // Handle /oscplay messages without recording or forwarding
                handleOSCPlayCommand(oscMessage);
                return;
            }

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

    /**
     * Handle /oscplay command messages.
     * Format: /oscplay/sampler<bank> <padNumber>
     * Example: /oscplay/sampler1 1 triggers bank 1, pad 1
     */
    private void handleOSCPlayCommand(OSCMessage message) {
        String address = message.getAddress();

        // Parse the address pattern: /oscplay/sampler<bank>
        if (address.matches("/oscplay/sampler[1-4]")) {
            try {
                // Extract bank number from address
                int bankNumber = Integer.parseInt(address.substring("/oscplay/sampler".length()));

                // Get pad number from first argument
                if (message.getArguments().isEmpty()) {
                    System.err.println("OSCPlay command missing pad number: " + address);
                    return;
                }

                Object arg = message.getArguments().get(0);
                int padNumber;
                if (arg instanceof Integer) {
                    padNumber = (Integer) arg;
                } else if (arg instanceof Float) {
                    padNumber = ((Float) arg).intValue();
                } else if (arg instanceof String) {
                    padNumber = Integer.parseInt((String) arg);
                } else {
                    System.err.println("OSCPlay command has invalid pad number type: " + arg.getClass().getName());
                    return;
                }

                // Trigger the sampler pad
                if (samplerPadUI != null) {
                    samplerPadUI.triggerPadFromOSC(bankNumber, padNumber);
                } else {
                    System.err.println("SamplerPadUI not set - cannot trigger pad");
                }

            } catch (NumberFormatException | IndexOutOfBoundsException e) {
                System.err.println("Error parsing OSCPlay command: " + address + " - " + e.getMessage());
            }
        } else {
            System.err.println("Unknown OSCPlay command: " + address);
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

    public void setSamplerPadUI(xyz.theforks.ui.SamplerPadUI samplerPadUI) {
        this.samplerPadUI = samplerPadUI;
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

    /**
     * Reset message counters to zero.
     * Useful when loading a new project or clearing state.
     */
    public void resetMessageCounters() {
        Platform.runLater(() -> {
            messageCount.set(0);
            totalMessageCount.set(0);
        });
    }
}
