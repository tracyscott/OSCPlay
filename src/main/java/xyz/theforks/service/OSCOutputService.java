package xyz.theforks.service;

import java.io.IOException;
import java.net.InetSocketAddress;

import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCSerializeException;
import com.illposed.osc.transport.OSCPortOut;
import com.illposed.osc.transport.OSCPortOutBuilder;
import xyz.theforks.nodes.NodeChain;
import xyz.theforks.ui.MonitorWindow;

public class OSCOutputService {
    private final String id;
    private OSCPortOut sender;
    private String outHost;
    private int outPort;
    private final NodeChain nodeChain;
    private boolean enabled = true;
    private MonitorWindow monitorWindow;
    private ProxyDelayProcessor delayProcessor;

    public OSCOutputService(String id) {
        this.id = id;
        this.nodeChain = new NodeChain(NodeChain.Context.PROXY);
    }

    public OSCOutputService() {
        this("default");
    }

    public void setOutHost(String outHost) {
        this.outHost = outHost;
    }

    public void setOutPort(int outPort) {
        this.outPort = outPort;
    }
    
    public void start() throws IOException {
        sender = new OSCPortOutBuilder()
                .setRemoteSocketAddress(new InetSocketAddress(outHost, outPort))
                .build();
    }

    /**
     * Set the delay processor for handling delayed messages in proxy mode.
     * @param delayProcessor The delay processor
     */
    public void setDelayProcessor(ProxyDelayProcessor delayProcessor) {
        this.delayProcessor = delayProcessor;
    }

    /**
     * Get the delay processor.
     * @return The delay processor, or null if not set
     */
    public ProxyDelayProcessor getDelayProcessor() {
        return delayProcessor;
    }


    public void send(OSCMessage message) throws IOException, OSCSerializeException {
        send(message, false, false);
    }

    /**
     * Send an OSC message through this output.
     * @param message The message to send
     * @param bypassEnabledCheck If true, send even if output is disabled (for direct routing)
     */
    public void send(OSCMessage message, boolean bypassEnabledCheck) throws IOException, OSCSerializeException {
        send(message, bypassEnabledCheck, false);
    }

    /**
     * Send an OSC message through this output.
     * @param message The message to send
     * @param bypassEnabledCheck If true, send even if output is disabled (for direct routing)
     * @param bypassNodeChain If true, send directly without processing through node chain (for playback)
     */
    public void send(OSCMessage message, boolean bypassEnabledCheck, boolean bypassNodeChain) throws IOException, OSCSerializeException {
        // If bypassing enabled check and sender is not initialized, start the output
        System.out.println("Output " + id + " trying to send message: " + message);
        if (bypassEnabledCheck && sender == null) {
            start();
        }

        System.out.println("Checking sender and message for output " + id);
        if (sender == null || message == null) {
            return;
        }

        // Only check enabled flag if not bypassing
        System.out.println("Checking enabled and bypass state for output " + id);
        if (!bypassEnabledCheck && !enabled) {
            return;
        }

        if (bypassNodeChain) {
            // Send directly without node chain processing (already processed in playback)
            sender.send(message);

            // Send to monitor window if one is open
            if (monitorWindow != null && monitorWindow.isOpen()) {
                monitorWindow.addMessage(message);
            }
        } else {
            // Apply node chain to message (no playback context in proxy mode)
            java.util.List<xyz.theforks.model.MessageRequest> requests = nodeChain.processMessage(message);

            for (xyz.theforks.model.MessageRequest req : requests) {
                if (req.isImmediate()) {
                    // Send immediately
                    sender.send(req.getMessage());

                    // Send to monitor window if one is open
                    if (monitorWindow != null && monitorWindow.isOpen()) {
                        monitorWindow.addMessage(req.getMessage());
                    }
                } else if (delayProcessor != null && delayProcessor.isRunning()) {
                    // Schedule delayed message through the delay processor
                    delayProcessor.scheduleMessage(req, id);
                } else {
                    // No delay processor available, send immediately as fallback
                    System.err.println("Warning: Delayed message requested but no delay processor available, sending immediately");
                    sender.send(req.getMessage());

                    // Send to monitor window if one is open
                    if (monitorWindow != null && monitorWindow.isOpen()) {
                        monitorWindow.addMessage(req.getMessage());
                    }
                }
            }
        }
    }

    public void stop() {
        if (sender != null) {
            try {
                sender.close();
            } catch (IOException e) {
                System.err.println("Error stopping output: " + e.getMessage());
            }
        }
    }

    public String getId() {
        return id;
    }

    public String getOutHost() {
        return outHost;
    }

    public int getOutPort() {
        return outPort;
    }

    public NodeChain getNodeChain() {
        return nodeChain;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isStarted() {
        return sender != null;
    }

    public void setMonitorWindow(MonitorWindow monitorWindow) {
        this.monitorWindow = monitorWindow;
    }

    public MonitorWindow getMonitorWindow() {
        return monitorWindow;
    }
}
