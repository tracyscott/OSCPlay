package xyz.theforks;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.List;

import com.illposed.osc.MessageSelector;
import com.illposed.osc.OSCMessageEvent;
import com.illposed.osc.OSCMessageListener;
import com.illposed.osc.transport.OSCPortIn;
import com.illposed.osc.transport.OSCPortInBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCSerializeException;
import com.illposed.osc.transport.OSCPortOut;
import com.illposed.osc.transport.OSCPortOutBuilder;

import xyz.theforks.model.OSCMessageRecord;
import xyz.theforks.model.QLab2OSCConfig;
import xyz.theforks.model.RecordingSession;

/**
 * QLab2OSCConverter - Converts OSC recordings to QLab Network OSC cues
 * 
 * This application reads OSC recording sessions and creates corresponding
 * Network OSC cues in QLab using the QLab OSC API. Each recorded OSC message
 * becomes a Network OSC cue with appropriate timing.
 */
public class QLab2OSCConverter {
    
    private OSCPortOut qlabOutput;
    private OSCPortIn qlabInput;
    private QLab2OSCConfig config;
    private String actualCueListId = null; // Store the actual UUID of the created cue list
    
    public QLab2OSCConverter() throws SocketException {
        this(new QLab2OSCConfig());
    }
    
    public QLab2OSCConverter(QLab2OSCConfig config) throws SocketException {
        this.config = config;
        try {
            this.qlabOutput = new OSCPortOutBuilder()
                .setRemoteSocketAddress(new InetSocketAddress(config.getQlabHost(), config.getQlabPort()))
                .build();
                
            // Set up reply listener on port 53001
            this.qlabInput = new OSCPortInBuilder()
                .setLocalSocketAddress(new InetSocketAddress(53001))
                .build();
                
            // Listen for all replies from QLab
            qlabInput.getDispatcher().addListener(new MessageSelector() {
                @Override
                public boolean isInfoRequired() { return false; }
                @Override
                public boolean matches(OSCMessageEvent messageEvent) { return true; }
            }, new OSCMessageListener() {
                @Override
                public void acceptMessage(OSCMessageEvent event) {
                    System.out.println("← QLab Reply: " + event.getMessage().getAddress() + " " + event.getMessage().getArguments());
                }
            });
            
        } catch (java.net.UnknownHostException e) {
            throw new SocketException("Unknown host: " + config.getQlabHost());
        } catch (IOException e) {
            throw new SocketException("Failed to create OSC ports: " + e.getMessage());
        }
    }
    
    /**
     * Load configuration from file
     */
    public static QLab2OSCConfig loadConfig(String configPath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        File configFile = new File(configPath);
        if (configFile.exists()) {
            return mapper.readValue(configFile, QLab2OSCConfig.class);
        } else {
            // Create default config file
            QLab2OSCConfig defaultConfig = new QLab2OSCConfig();
            mapper.writerWithDefaultPrettyPrinter().writeValue(configFile, defaultConfig);
            System.out.println("Created default config file: " + configPath);
            return defaultConfig;
        }
    }
    
    /**
     * Convert an OSC recording session to QLab Network OSC cues
     */
    public void convertSession(String sessionName) throws IOException, OSCSerializeException {
        RecordingSession session = RecordingSession.loadSession(sessionName);
        if (session == null) {
            throw new IOException("Failed to load session: " + sessionName);
        }
        
        System.out.println("Converting session: " + sessionName);
        System.out.println("Found " + session.getMessages().size() + " messages");
        
        // Connect to QLab workspace
        System.out.println("Connecting to QLab...");
        testQLab();
        connectToWorkspace();
        
        // Convert each message to a Network OSC cue
        System.out.println("Converting " + session.getMessages().size() + " messages to cues...");
        convertMessages(session);
        
        System.out.println("Conversion complete!");
    }
    
    /**
     * Test QLab connectivity and query existing OSC cue
     */
    private void testQLab() throws IOException, OSCSerializeException {
        System.out.println("Testing QLab connectivity...");
        
        // Start listening for replies
        qlabInput.startListening();
        
        // Enable always reply mode
        System.out.println("→ Enabling alwaysReply...");
        OSCMessage alwaysReplyMsg = new OSCMessage("/alwaysReply", List.of(1));
        qlabOutput.send(alwaysReplyMsg);
        
        // Test with version command
        System.out.println("→ Requesting version...");
        OSCMessage versionMsg = new OSCMessage("/version", List.of());
        qlabOutput.send(versionMsg);
        
        // List all cues in the workspace to see what's there
        System.out.println("→ Listing all cues in workspace...");
        OSCMessage cueListMsg = new OSCMessage(getWorkspacePrefix() + "/cueLists", List.of());
        qlabOutput.send(cueListMsg);
        
        // Try querying the existing OSC cue (number 41)
        System.out.println("→ Querying existing OSC cue (number 41)...");
        String[] properties = {"type", "messageType", "customString", "patch", "number", "name"};
        for (String prop : properties) {
            OSCMessage queryMsg = new OSCMessage(getWorkspacePrefix() + "/cue/41/" + prop, List.of());
            qlabOutput.send(queryMsg);
        }
        
        try {
            Thread.sleep(2000); // Give QLab time to respond to all queries
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Connect to QLab workspace (current workspace if none specified)
     */
    private void connectToWorkspace() throws IOException, OSCSerializeException {
        OSCMessage connectMsg;
        if (config.getWorkspaceId() != null) {
            System.out.println("Connecting to workspace: " + config.getWorkspaceId());
            connectMsg = new OSCMessage("/workspace/" + config.getWorkspaceId() + "/connect", List.of());
        } else {
            System.out.println("Connecting to current workspace");
            connectMsg = new OSCMessage("/connect", List.of());
        }
        
        qlabOutput.send(connectMsg);
        
        // Wait a moment for connection
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    
    /**
     * Convert all messages in the session to Network OSC cues
     */
    private void convertMessages(RecordingSession session) throws IOException, OSCSerializeException {
        List<OSCMessageRecord> messages = session.getMessages();
        if (messages.isEmpty()) {
            return;
        }
        
        long baseTime = messages.get(0).getTimestamp();
        
        for (int i = 0; i < messages.size(); i++) {
            OSCMessageRecord message = messages.get(i);
            double delayFromStart = ((message.getTimestamp() - baseTime) / 1000.0) * config.getTimeScale();
            
            System.out.println("Creating cue " + (i + 1) + ": " + message.getAddress() + " (delay: " + String.format("%.2f", delayFromStart) + "s)");
            createNetworkOSCCue(message, i + 1, delayFromStart);
        }
    }
    
    /**
     * Create a Network OSC cue for a single OSC message
     */
    private void createNetworkOSCCue(OSCMessageRecord message, int cueNumber, double preWait) throws IOException, OSCSerializeException {
        // Create new cue (OSC or memo based on config)
        String cueType = config.getCueType();
        if ("osc".equals(cueType)) {
            cueType = "Network"; // QLab uses "Network" not "osc"
        }
        
        // Create new cue as child of the selected cue list
        String newCueAddress;
        
        // Create normal cue
        newCueAddress = getWorkspacePrefix() + "/new";
        System.out.println("  → Sending: " + newCueAddress + " [\"" + cueType + "\"]");
        OSCMessage newCueMsg = new OSCMessage(newCueAddress, List.of(cueType));
        qlabOutput.send(newCueMsg);
        
        // Wait for cue creation
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Set cue number
        String numberAddress = getWorkspacePrefix() + "/cue/selected/number";
        System.out.println("  → Sending: " + numberAddress + " [\"" + cueNumber + "\"]");
        OSCMessage numberMsg = new OSCMessage(numberAddress, List.of(String.valueOf(cueNumber)));
        qlabOutput.send(numberMsg);
        
        // Set cue name based on cue type (use simple names for both)
        String cueName;
        if (message.getArguments() != null && message.getArguments().length > 0) {
            cueName = message.getArguments()[0].toString();
        } else {
            cueName = message.getAddress(); // Fallback if no arguments
        }
        String nameAddress = getWorkspacePrefix() + "/cue/selected/name";
        System.out.println("  → Sending: " + nameAddress + " [\"" + cueName + "\"]");
        OSCMessage nameMsg = new OSCMessage(nameAddress, List.of(cueName));
        qlabOutput.send(nameMsg);
        
        // Set pre-wait timing
        if (config.isUsePreWait() && preWait > 0) {
            String preWaitAddress = getWorkspacePrefix() + "/cue/selected/preWait";
            System.out.println("  → Sending: " + preWaitAddress + " [" + (float) preWait + "]");
            OSCMessage preWaitMsg = new OSCMessage(preWaitAddress, List.of((float) preWait));
            qlabOutput.send(preWaitMsg);
        }
        
        // Set to auto-continue for seamless playback
        if (config.isAutoContinue()) {
            String continueAddress = getWorkspacePrefix() + "/cue/selected/continueMode";
            System.out.println("  → Sending: " + continueAddress + " [1]");
            OSCMessage continueMsg = new OSCMessage(continueAddress, List.of(1));
            qlabOutput.send(continueMsg);
        }
        
        // Configure the OSC message content (only for Network OSC cues)
        if ("Network".equals(cueType)) {
            configureOSCMessage(message);
        }
        
        // Small delay between cue creation
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Configure the OSC message content for the Network OSC cue
     */
    private void configureOSCMessage(OSCMessageRecord message) throws IOException, OSCSerializeException {
        // Set message type to custom string (assuming this is available)
        String typeAddress = getWorkspacePrefix() + "/cue/selected/messageType";
        System.out.println("  → Sending: " + typeAddress + " [5]");
        OSCMessage typeMsg = new OSCMessage(typeAddress, List.of(5)); // Custom string
        qlabOutput.send(typeMsg);
        
        // Build the OSC command string matching the original recording format
        StringBuilder oscCommand = new StringBuilder(message.getAddress());
        
        if (message.getArguments() != null && message.getArguments().length > 0) {
            for (Object arg : message.getArguments()) {
                oscCommand.append(" ");
                // Don't add quotes for string arguments to match original format
                oscCommand.append(arg.toString());
            }
        }
        
        // Set the custom string
        String customStringAddress = getWorkspacePrefix() + "/cue/selected/customString";
        System.out.println("  → Sending: " + customStringAddress + " [\"" + oscCommand.toString() + "\"]");
        OSCMessage customStringMsg = new OSCMessage(customStringAddress, List.of(oscCommand.toString()));
        qlabOutput.send(customStringMsg);
        
        // Set destination patch
        String patchAddress = getWorkspacePrefix() + "/cue/selected/patch";
        System.out.println("  → Sending: " + patchAddress + " [" + config.getOscPatch() + "]");
        OSCMessage patchMsg = new OSCMessage(patchAddress, List.of(config.getOscPatch()));
        qlabOutput.send(patchMsg);
    }
    
    /**
     * Get the workspace prefix for OSC commands
     */
    private String getWorkspacePrefix() {
        if (config.getWorkspaceId() != null) {
            return "/workspace/" + config.getWorkspaceId();
        }
        return "";
    }
    
    /**
     * Close the OSC connection
     */
    public void close() throws IOException {
        if (qlabInput != null) {
            qlabInput.stopListening();
            qlabInput.close();
        }
        if (qlabOutput != null) {
            qlabOutput.close();
        }
    }
    
    /**
     * Main entry point
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java QLab2OSCConverter <session-name> [--config config.json] [--cue-type type]");
            System.err.println("  session-name: Name of the recording session (without .json extension)");
            System.err.println("  --config: Path to configuration file (optional, creates default if not found)");
            System.err.println("  --cue-type: Type of cues to create - 'osc' or 'memo' (optional, uses config file value)");
            System.err.println();
            System.err.println("Examples:");
            System.err.println("  java QLab2OSCConverter mission1");
            System.err.println("  java QLab2OSCConverter mission1 --config my_qlab_config.json");
            System.err.println("  java QLab2OSCConverter mission1 --cue-type memo");
            System.exit(1);
        }
        
        String sessionName = args[0];
        String configPath = "qlab2osc_config.json";
        String cueType = null; // Will use config file value if not specified
        
        // Parse arguments
        for (int i = 1; i < args.length; i++) {
            if ("--config".equals(args[i]) && i + 1 < args.length) {
                configPath = args[i + 1];
                i++; // Skip the next argument
            } else if ("--cue-type".equals(args[i]) && i + 1 < args.length) {
                cueType = args[i + 1];
                i++; // Skip the next argument
            }
        }
        
        QLab2OSCConverter converter = null;
        try {
            // Load configuration
            QLab2OSCConfig config = loadConfig(configPath);
            
            // Override cue type if specified on command line
            if (cueType != null) {
                if ("osc".equals(cueType) || "memo".equals(cueType)) {
                    config.setCueType(cueType);
                } else {
                    System.err.println("Invalid cue type: " + cueType + ". Must be 'osc' or 'memo'");
                    System.exit(1);
                }
            }
            
            System.out.println("Using configuration from: " + configPath);
            System.out.println("QLab host: " + config.getQlabHost() + ":" + config.getQlabPort());
            System.out.println("Destination: " + config.getDestinationHost() + ":" + config.getDestinationPort());
            System.out.println("Cue type: " + config.getCueType());
            
            converter = new QLab2OSCConverter(config);
            converter.convertSession(sessionName);
            
        } catch (Exception e) {
            System.err.println("Error during conversion: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            if (converter != null) {
                try {
                    converter.close();
                } catch (IOException e) {
                    System.err.println("Error closing converter: " + e.getMessage());
                }
            }
        }
    }
}