package xyz.theforks.service;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCSerializeException;
import com.illposed.osc.messageselector.OSCPatternAddressMessageSelector;
import com.illposed.osc.transport.OSCPortIn;
import com.illposed.osc.transport.OSCPortInBuilder;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;

public class OSCInputService {

    private OSCPortIn receiver;
    private String inHost = "127.0.0.1";
    private int inPort = 8000;
    private final IntegerProperty messageCount = new SimpleIntegerProperty(0);
    private MessageHandlerClass messageHandler;
    private boolean isStarted;

    public OSCInputService() {
        isStarted = false;
    }

    public String getInHost() {
        return inHost;
    }

    public int getInPort() {
        return inPort;
    }

    public void setInHost(String inHost) {
        this.inHost = inHost;
    }

    public void setInPort(int inPort) {
        this.inPort = inPort;
    }


    public void start() throws IOException {
        if (!isStarted) {
            InetSocketAddress localhostPort = new InetSocketAddress(inHost, inPort);
            receiver = new OSCPortInBuilder()
                    .setPort(inPort)
                    .setLocalSocketAddress(localhostPort)
                    .build();

            receiver.getDispatcher().addListener(
                    new OSCPatternAddressMessageSelector("//"),
                    event -> handleMessage(event.getMessage())
            );
            receiver.startListening();
            isStarted = true;
        }
    }

    public void stop() {
        if (isStarted) {
            if (receiver != null) {
                try {
                    receiver.close();
                } catch (IOException e) {
                    System.err.println("Error stopping input: " + e.getMessage());
                }
            }
            isStarted = false;
        }
    }

    /**
     * Each pad can have a custom message filter so that only messages matching
     * the address are stored in the pad.
     */
    public void addMessageHandlerWithFilter(String filter, MessageHandler messageHandler) {
        receiver.getDispatcher().addListener(
                new OSCPatternAddressMessageSelector(filter),
                event -> {
                    messageHandler.handleMessage(event.getMessage());
                }
        );
    }

    // Add a listener interface so that classes using this class can be notified
    // of incoming messages.  
    public void setMessageHandler(MessageHandlerClass messageHandler) {
        this.messageHandler = messageHandler;
    }

    private void handleMessage(OSCMessage oscMessage) {
        try {
            if (messageHandler != null) {
                messageHandler.handleMessage(oscMessage);
            }
        } catch (IOException | OSCSerializeException e) {
            System.err.println("Error handling message: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
