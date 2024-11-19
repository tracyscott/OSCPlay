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

import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import xyz.theforks.model.OSCMessageRecord;
import xyz.theforks.model.RecordingSession;

public class OSCInputService {

    private OSCPortIn receiver;
    private String inHost = "127.0.0.1";
    private int inPort = 8000;
    private final IntegerProperty messageCount = new SimpleIntegerProperty(0);
    private MessageHandlerClass messageHandler;
    private RecordingSession currentSession;
    private boolean isRecording = false;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String RECORDINGS_DIR = "recordings";
    private boolean isStarted;

    public OSCInputService() {
        createDirectories();
        isStarted = false;
    }

    private void createDirectories() {
        new File(RECORDINGS_DIR).mkdirs();
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

    public void startRecording(String sessionName) throws IOException {
        start();
        currentSession = new RecordingSession(sessionName);
        messageCount.set(0);
        isRecording = true;
    }

    public void stopRecording() {
        // if recording, save the session
        if (isRecording && currentSession != null) {
            try {
                objectMapper.writeValue(new File(RECORDINGS_DIR + "/" + currentSession.getFilename()), currentSession);
            } catch (IOException e) {
                System.err.println("Error saving recording: " + e.getMessage());
            }
        }
        isRecording = false;
        currentSession = null;
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
            // Forward the message
            //if (outputEnabled && proxySender != null)
            //    proxySender.send(oscMessage);
            //System.out.println("Forwarded message: " + oscMessage.getAddress());

            // Record if recording is active
            if (isRecording && currentSession != null) {
                OSCMessageRecord record = new OSCMessageRecord(
                        oscMessage.getAddress(),
                        oscMessage.getArguments().toArray()
                );
                currentSession.addMessage(record);

                // Update message count on JavaFX thread
                Platform.runLater(() -> messageCount.set(messageCount.get() + 1));
            }

            if (messageHandler != null) {
                messageHandler.handleMessage(oscMessage);
            }
        } catch (IOException e) {
            System.err.println("Error handling message: " + e.getMessage());
            e.printStackTrace();
        } catch (OSCSerializeException e) {
            System.err.println("Error serializing message: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
