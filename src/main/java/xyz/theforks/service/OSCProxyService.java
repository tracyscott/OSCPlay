package xyz.theforks.service;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.illposed.osc.MessageSelector;
import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCMessageEvent;
import com.illposed.osc.OSCMessageListener;
import com.illposed.osc.OSCSerializeException;
import com.illposed.osc.messageselector.OSCPatternAddressMessageSelector;
import com.illposed.osc.transport.OSCPortIn;
import com.illposed.osc.transport.OSCPortInBuilder;
import com.illposed.osc.transport.OSCPortOut;
import com.illposed.osc.transport.OSCPortOutBuilder;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import xyz.theforks.model.OSCMessageRecord;
import xyz.theforks.model.RecordingSession;
import xyz.theforks.model.SessionConfig;
import xyz.theforks.model.SessionMetadata;


public class OSCProxyService {
    private OSCPortIn receiver;
    private OSCPortOut proxySender;
     private OSCPortOut playbackSender;
    private RecordingSession currentSession;
    private boolean isRecording = false;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String RECORDINGS_DIR = "recordings";
    private final IntegerProperty messageCount = new SimpleIntegerProperty(0);
    private final DoubleProperty playbackProgress = new SimpleDoubleProperty(0);
    private final BooleanProperty isPlaying = new SimpleBooleanProperty(false);
    private AtomicBoolean stopPlayback = new AtomicBoolean(false);
    private Thread playbackThread;
    private int listenPort = 7000;
    private String forwardHost = "127.0.0.1";
    private int forwardPort = 8000;
    private String playbackHost = "127.0.0.1";
    private int playbackPort = 9000;
    private final String AUDIO_DIR = "audio";
    private final String CONFIG_FILE = "session_config.json";
    private SessionConfig sessionConfig;
    private MediaPlayer mediaPlayer;
    private boolean mediaReady = false;


public OSCProxyService() {
        loadSessionConfig();
        createDirectories();
    }

    private void createDirectories() {
        new File(RECORDINGS_DIR).mkdirs();
        new File(AUDIO_DIR).mkdirs();
    }

    private void loadSessionConfig() {
        try {
            File configFile = new File(CONFIG_FILE);
            if (configFile.exists()) {
                sessionConfig = objectMapper.readValue(configFile, SessionConfig.class);
            } else {
                sessionConfig = new SessionConfig();
            }
        } catch (Exception e) {
            System.err.println("Error loading session config: " + e.getMessage());
            sessionConfig = new SessionConfig();
        }
    }

    private void saveSessionConfig() {
        try {
            objectMapper.writeValue(new File(CONFIG_FILE), sessionConfig);
        } catch (Exception e) {
            System.err.println("Error saving session config: " + e.getMessage());
        }
    }

    public void associateAudioFile(String sessionName, File audioFile) {
        try {
            // Copy or move audio file to audio directory if it's not already there
            Path targetPath = Paths.get(AUDIO_DIR, audioFile.getName());
            if (!audioFile.getAbsolutePath().equals(targetPath.toAbsolutePath().toString())) {
                java.nio.file.Files.copy(
                    audioFile.toPath(),
                    targetPath,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                );
            }
            
            sessionConfig.addSession(sessionName, audioFile.getName());
            saveSessionConfig();
            
            System.out.println("Associated audio file " + audioFile.getName() + 
                             " with session " + sessionName);
        } catch (Exception e) {
            System.err.println("Error associating audio file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public String getAssociatedAudioFile(String sessionName) {
        SessionMetadata metadata = sessionConfig.getSession(sessionName);
        return metadata != null ? metadata.getAudioFile() : null;
    }

    public DoubleProperty playbackProgressProperty() {
        return playbackProgress;
    }

    public BooleanProperty isPlayingProperty() {
        return isPlaying;
    }

    public IntegerProperty messageCountProperty() {
        return messageCount;
    }

    public void setListenPort(int port) {
        this.listenPort = port;
    }

    public void setForwardHost(String host) {
        this.forwardHost = host;
    }

    public void setForwardPort(int port) {
        this.forwardPort = port;
    }

    public void setPlaybackHost(String host) {
        this.playbackHost = host;
    }

    public void setPlaybackPort(int port) {
        this.playbackPort = port;
    }

    public void startProxy() throws IOException {
        // Stop existing connections if any
        stopProxy();

        // Create receiver
        InetSocketAddress localhostPort = new InetSocketAddress("127.0.0.1", listenPort);
        receiver = new OSCPortInBuilder()
            .setPort(listenPort)
            .setLocalSocketAddress(localhostPort)
            .build();

        // Create sender
        proxySender = new OSCPortOutBuilder()
            .setRemoteSocketAddress(new InetSocketAddress(forwardHost, forwardPort))
            .build();


        // Set up message listener
        OSCMessageListener listener;
        listener = new OSCMessageListener() {
            public void acceptMessage(OSCMessageEvent event) {
                //System.out.println("Message received: " + event.getMessage().getAddress());
                handleMessage(event.getMessage());
            }
        };
        MessageSelector selector = new OSCPatternAddressMessageSelector("//");
        receiver.getDispatcher().addListener(selector, listener);
        
        receiver.startListening();
        
        System.out.println("Proxy started - listening on port " + listenPort + 
                          " and forwarding to port " + forwardPort);
                          System.out.println("Proxy started successfully:");
        //receiver.getTransport().
            //System.out.println("  - Listening on: " + receiver.getLocalAddress() + ":" + receiver.getLocalPort());
           // System.out.println("  - Forwarding to: " + sender.getRemoteAddress() + ":" + sender.getRemotePort());

    }

    public void stopProxy() {
        try {
            if (receiver != null) {
                receiver.close();
            }
            if (proxySender != null) {
                proxySender.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("Proxy stopped");
    }

    private void handleMessage(OSCMessage oscMessage) {
        try {
            // Forward the message
            proxySender.send(oscMessage);
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
        } catch (IOException e) {
            System.err.println("Error handling message: " + e.getMessage());
            e.printStackTrace();
        } catch (OSCSerializeException e) {
            System.err.println("Error serializing message: " + e.getMessage());
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

    public void stopPlayback() {
        stopPlayback.set(true);
        if (playbackThread != null) {
            playbackThread.interrupt();
        }
    }

    @Override
    protected void finalize() throws Throwable {
        stopProxy();
        if (playbackSender != null) {
            playbackSender.close();
        }
        super.finalize();
    }

    private void ensurePlaybackSender() throws IOException {
        if (playbackSender != null) {
            //if (playbackSender.transport.getRemotePort() != port) {
           //     playbackSender.close();
           //     playbackSender = null;
           // }
        }
        
        if (playbackSender == null) {
            playbackSender = new OSCPortOutBuilder()
            .setRemoteSocketAddress(new InetSocketAddress(playbackHost, playbackPort))
            .build();
            System.out.println("Created playback sender on port: " + playbackPort);
        }
    }


    public void playSession(String sessionName) {
        try {
            File file = new File(RECORDINGS_DIR, sessionName + ".json");
            if (!file.exists()) {
                System.err.println("Recording file not found: " + file.getAbsolutePath());
                return;
            }

            RecordingSession session = objectMapper.readValue(file, RecordingSession.class);
            
            if (session == null || session.getMessages() == null || session.getMessages().isEmpty()) {
                System.err.println("Invalid session data");
                return;
            }

            ensurePlaybackSender();
            
            System.out.println("Playing session: " + sessionName + 
                             " (" + session.getMessages().size() + " messages)");
            
            // Reset control flags
            stopPlayback.set(false);

            Platform.runLater(() -> {
                playbackProgress.set(0);
                isPlaying.set(true);
            });

            // Create and start playback thread
            playbackThread = new Thread(() -> {
                try {
                    long startTime = System.currentTimeMillis();
                    int totalMessages = session.getMessages().size();
                    boolean firstMessage = true;
                    long sessionStartTime = session.getStartTime();

                    for (int i = 0; i < totalMessages; i++) {
                        if (stopPlayback.get()) {
                            break;
                        }

                        OSCMessageRecord msg = session.getMessages().get(i);

                        if (firstMessage) {
                            // Start audio if associated
                            // This is done right before sending the first message so we have better sync.  Any audio
                            // based recording should send an OSC message immediately after starting the audio since
                            // we have no other way of knowing when to start the audio.
                            String audioFileName = getAssociatedAudioFile(sessionName);
                            if (audioFileName != null) {
                                File audioFile = new File(AUDIO_DIR, audioFileName);
                                if (audioFile.exists()) {
                                    Media media = new Media(audioFile.toURI().toString());
                                    mediaPlayer = new MediaPlayer(media);
                                    mediaReady = false;
                                    mediaPlayer.setOnReady(() -> {
                                        mediaPlayer.setOnPlaying(() -> {
                                            mediaReady = true;
                                        });
                                        mediaPlayer.play();   
                                    });
                                    System.out.println("Started audio playback: " + audioFileName);
                                }
                            }
                        }
                        if (firstMessage) {
                            while (!mediaReady) {
                                try {
                                    Thread.sleep(5);
                                } catch (Exception e) {
                                }
                            }
                            sessionStartTime = msg.getTimestamp();
                            firstMessage = false;
                        }
                        // Update progress
                        final double progress = (double) (i + 1) / totalMessages;
                        Platform.runLater(() -> playbackProgress.set(progress));

                        
                        try {
                            if (msg != null && msg.getAddress() != null && msg.getArguments() != null) {
                                // The session start time should be the time of the first message, not the time
                                // we started recording the session. This way we can maintain the original timing.
                                
                                long delay = msg.getTimestamp() - sessionStartTime;
                                Thread.sleep(Math.max(0, delay - (System.currentTimeMillis() - startTime)));
                                
                                OSCMessage oscMsg = new OSCMessage(msg.getAddress(), List.of(msg.getArguments()));
                                playbackSender.send(oscMsg);
                                //System.out.println("Played back message " + (i + 1) + "/" + totalMessages + 
                                //                 ": " + msg.getAddress());
                            }
                        } catch (InterruptedException e) {
                            break;
                        } catch (Exception e) {
                            System.err.println("Error playing message: " + e.getMessage());
                        }
                    }
                } finally {
                    // Reset UI state
                    Platform.runLater(() -> {
                        isPlaying.set(false);
                        if (stopPlayback.get()) {
                            playbackProgress.set(0);
                        } else {
                            playbackProgress.set(1.0);
                        }
                    });
                    System.out.println("Finished playing session: " + sessionName);
                }
            });
            
            playbackThread.start();

        } catch (IOException e) {
            System.err.println("Error loading session: " + e.getMessage());
            e.printStackTrace();
            Platform.runLater(() -> {
                isPlaying.set(false);
                playbackProgress.set(0);
            });
        }
    }
}