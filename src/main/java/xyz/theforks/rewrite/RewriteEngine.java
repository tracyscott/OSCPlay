package xyz.theforks.rewrite;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.illposed.osc.OSCMessage;

/**
 * Centralized engine for applying rewrite handler chains to OSC messages.
 * This engine can be used in different contexts (proxy, playback, recording)
 * and provides thread-safe operations for concurrent use.
 */
public class RewriteEngine {
    
    public enum Context {
        PROXY,
        PLAYBACK,
        RECORDING
    }
    
    private final CopyOnWriteArrayList<RewriteHandler> rewriteHandlers;
    private volatile boolean enabled;
    private final Context context;
    
    public RewriteEngine(Context context) {
        this.context = context;
        this.rewriteHandlers = new CopyOnWriteArrayList<>();
        this.enabled = true;
    }
    
    /**
     * Apply the rewrite handler chain to an OSC message.
     * @param message The original OSC message
     * @return The processed message, or null if a handler cancelled the message
     */
    public OSCMessage processMessage(OSCMessage message) {
        if (!enabled || message == null) {
            return message;
        }
        
        OSCMessage processedMessage = message;
        String address = message.getAddress();
        
        for (RewriteHandler handler : rewriteHandlers) {
            if (address.matches(handler.getAddressPattern())) {
                processedMessage = handler.process(processedMessage);
                if (processedMessage == null) {
                    return null; // Handler cancelled the message
                }
                // Update address in case it was changed by the handler
                address = processedMessage.getAddress();
            }
        }
        
        return processedMessage;
    }
    
    /**
     * Register a rewrite handler with this engine.
     * @param handler The handler to add
     */
    public void registerHandler(RewriteHandler handler) {
        if (handler != null) {
            rewriteHandlers.add(handler);
        }
    }
    
    /**
     * Unregister a rewrite handler from this engine.
     * @param handler The handler to remove
     */
    public void unregisterHandler(RewriteHandler handler) {
        rewriteHandlers.remove(handler);
    }
    
    /**
     * Clear all rewrite handlers from this engine.
     */
    public void clearHandlers() {
        rewriteHandlers.clear();
    }
    
    /**
     * Set the list of rewrite handlers, replacing any existing handlers.
     * @param handlers The new list of handlers
     */
    public void setHandlers(List<RewriteHandler> handlers) {
        rewriteHandlers.clear();
        if (handlers != null) {
            rewriteHandlers.addAll(handlers);
        }
    }
    
    /**
     * Get a copy of the current list of rewrite handlers.
     * @return A new list containing the current handlers
     */
    public List<RewriteHandler> getHandlers() {
        return new ArrayList<>(rewriteHandlers);
    }
    
    /**
     * Enable or disable this rewrite engine.
     * When disabled, processMessage() returns the original message unchanged.
     * @param enabled true to enable, false to disable
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    /**
     * Check if this rewrite engine is enabled.
     * @return true if enabled, false if disabled
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Get the context this rewrite engine is used in.
     * @return The context (PROXY, PLAYBACK, RECORDING)
     */
    public Context getContext() {
        return context;
    }
    
    /**
     * Get the number of registered handlers.
     * @return The number of handlers
     */
    public int getHandlerCount() {
        return rewriteHandlers.size();
    }
}