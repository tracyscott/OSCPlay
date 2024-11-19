package xyz.theforks.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCSerializeException;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import xyz.theforks.model.RecordingSession;

public class OSCProxyService {

    private OSCInputService inputService;
    private OSCOutputService outputService;
    private RecordingSession currentSession;
    private boolean isRecording = false;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String RECORDINGS_DIR = "recordings";
    private final IntegerProperty messageCount = new SimpleIntegerProperty(0);

    public OSCProxyService() {
        inputService = new OSCInputService();
        inputService.setMessageHandler(new MessageHandlerClass() {
            @Override
            public void handleMessage(OSCMessage message) {
                OSCProxyService.this.handleMessage(message);
            }
        });
        outputService = new OSCOutputService();
    
        createDirectories();
    }

    public OSCInputService getInputService() {
        return inputService;
    }

    public OSCOutputService getOutputService() {
        return outputService;
    }

    private void createDirectories() {
        new File(RECORDINGS_DIR).mkdirs();
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
            outputService.send(oscMessage);
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
            File dir = new File(RECORDINGS_DIR);
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
        File dir = new File(RECORDINGS_DIR);
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
}
