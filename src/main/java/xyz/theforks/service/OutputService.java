package xyz.theforks.service;

import java.io.IOException;

import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCSerializeException;
import xyz.theforks.nodes.NodeChain;
import xyz.theforks.ui.MonitorWindow;

/**
 * Common interface for output services (OSC, MIDI, etc.).
 */
public interface OutputService {
    /**
     * Get the unique identifier for this output.
     * @return The output ID
     */
    String getId();

    /**
     * Start the output service.
     * @throws IOException if the service cannot be started
     */
    void start() throws IOException;

    /**
     * Stop the output service.
     */
    void stop();

    /**
     * Check if the output is enabled.
     * @return true if enabled
     */
    boolean isEnabled();

    /**
     * Set whether the output is enabled.
     * @param enabled true to enable, false to disable
     */
    void setEnabled(boolean enabled);

    /**
     * Check if the output service has been started.
     * @return true if started
     */
    boolean isStarted();

    /**
     * Send an OSC message through this output.
     * The message may be converted to the appropriate output format (e.g., MIDI).
     * @param message The message to send
     * @throws IOException if sending fails
     * @throws OSCSerializeException if message serialization fails
     */
    void send(OSCMessage message) throws IOException, OSCSerializeException;

    /**
     * Send an OSC message through this output.
     * @param message The message to send
     * @param bypassEnabledCheck If true, send even if output is disabled
     * @throws IOException if sending fails
     * @throws OSCSerializeException if message serialization fails
     */
    void send(OSCMessage message, boolean bypassEnabledCheck) throws IOException, OSCSerializeException;

    /**
     * Send an OSC message through this output.
     * @param message The message to send
     * @param bypassEnabledCheck If true, send even if output is disabled
     * @param bypassNodeChain If true, send directly without node chain processing
     * @throws IOException if sending fails
     * @throws OSCSerializeException if message serialization fails
     */
    void send(OSCMessage message, boolean bypassEnabledCheck, boolean bypassNodeChain) throws IOException, OSCSerializeException;

    /**
     * Get the node chain for this output.
     * @return The node chain
     */
    NodeChain getNodeChain();

    /**
     * Set the delay processor for handling delayed messages.
     * @param delayProcessor The delay processor
     */
    void setDelayProcessor(ProxyDelayProcessor delayProcessor);

    /**
     * Get the delay processor.
     * @return The delay processor, or null if not set
     */
    ProxyDelayProcessor getDelayProcessor();

    /**
     * Set the monitor window for this output.
     * @param monitorWindow The monitor window
     */
    void setMonitorWindow(MonitorWindow monitorWindow);

    /**
     * Get the monitor window for this output.
     * @return The monitor window, or null if not set
     */
    MonitorWindow getMonitorWindow();
}
