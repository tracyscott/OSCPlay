package xyz.theforks.nodes;

import com.illposed.osc.OSCMessage;
import xyz.theforks.model.MessageRequest;
import java.util.List;

public interface OSCNode {
    String getAddressPattern();

    /**
     * Process an OSC message by modifying the requests list in-place.
     *
     * The input list contains a single MessageRequest with the original message.
     * Nodes should modify this list to express their intent:
     *
     * - Pass through unchanged: Do nothing (just return)
     * - Drop message: Clear the list (or call dropMessage(requests))
     * - Transform message: Clear list, add new MessageRequest with transformed message
     *   (or call replaceMessage(requests, newMessage))
     * - Expand to multiple: Clear list, add multiple MessageRequests
     *
     * @param requests Mutable list to modify in-place (initially contains 1 request)
     */
    void process(List<MessageRequest> requests);

    String getHelp();
    String label();
    int getNumArgs();
    boolean configure(String[] args);
    void showPreferences();
    String[] getArgs();
    String[] getArgNames();

    // ========== Utility Methods ==========

    /**
     * Get the input OSC message from the requests list.
     * Assumes list contains exactly 1 element (the typical case).
     *
     * @param requests The requests list
     * @return The OSC message, or null if list is empty
     */
    default OSCMessage inputMessage(List<MessageRequest> requests) {
        return requests.isEmpty() ? null : requests.get(0).getMessage();
    }

    /**
     * Drop the message by clearing the requests list.
     *
     * @param requests The requests list to clear
     */
    default void dropMessage(List<MessageRequest> requests) {
        requests.clear();
    }

    /**
     * Replace the input message with a new message (immediate send, no specific output).
     *
     * @param requests The requests list
     * @param newMessage The new message to send
     */
    default void replaceMessage(List<MessageRequest> requests, OSCMessage newMessage) {
        requests.clear();
        requests.add(new MessageRequest(newMessage));
    }

    /**
     * Replace the input message with a new message request (with delay/routing).
     *
     * @param requests The requests list
     * @param newRequest The new message request to send
     */
    default void replaceMessage(List<MessageRequest> requests, MessageRequest newRequest) {
        requests.clear();
        requests.add(newRequest);
    }

    /**
     * Replace the input message with multiple new messages.
     *
     * @param requests The requests list
     * @param newMessages The new messages to send
     */
    default void replaceWithMultiple(List<MessageRequest> requests, List<MessageRequest> newMessages) {
        requests.clear();
        requests.addAll(newMessages);
    }

    /**
     * Add an additional message without removing the original.
     * Useful for "echo" or "split" type nodes.
     *
     * @param requests The requests list
     * @param additionalMessage The message to add
     */
    default void addMessage(List<MessageRequest> requests, OSCMessage additionalMessage) {
        requests.add(new MessageRequest(additionalMessage));
    }

    /**
     * Add an additional message request without removing the original.
     *
     * @param requests The requests list
     * @param additionalRequest The message request to add
     */
    default void addMessage(List<MessageRequest> requests, MessageRequest additionalRequest) {
        requests.add(additionalRequest);
    }
}
