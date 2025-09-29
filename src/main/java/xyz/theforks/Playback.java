package xyz.theforks;


import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.illposed.osc.OSCMessage;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import xyz.theforks.model.OSCMessageRecord;
import xyz.theforks.model.PlaybackMode;
import xyz.theforks.model.RecordingSession;
import xyz.theforks.model.SessionConfig;
import xyz.theforks.model.SessionMetadata;
import xyz.theforks.rewrite.RewriteEngine;
import xyz.theforks.rewrite.RewriteHandler;
import xyz.theforks.service.OSCOutputService;
import xyz.theforks.util.DataDirectory;

/**
 * Encapsulates the playback of a recorded session.  For mult-trigger, there will be multiple instances
 * of this class, one for each trigger.
 */

public class Playback {

    private final DoubleProperty playbackProgress = new SimpleDoubleProperty(0);
    private final BooleanProperty isPlaying = new SimpleBooleanProperty(false);
    private AtomicBoolean stopPlayback = new AtomicBoolean(false);
    private Thread playbackThread;
    private String playbackHost = "127.0.0.1";
    private int playbackPort = 9000;
    private SessionConfig sessionConfig;
    private MediaPlayer mediaPlayer;
    private boolean mediaReady = false;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private OSCOutputService outputService;
    private PlaybackMode playbackMode = PlaybackMode.WITHOUT_REWRITE;
    private final RewriteEngine rewriteEngine;

    public Playback() {
        DataDirectory.createDirectories();
        loadSessionConfig();
        rewriteEngine = new RewriteEngine(RewriteEngine.Context.PLAYBACK);
    }

    private void loadSessionConfig() {
        try {
            File configFile = DataDirectory.getConfigFile("session_config.json").toFile();
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
            objectMapper.writeValue(DataDirectory.getConfigFile("session_config.json").toFile(), sessionConfig);
        } catch (Exception e) {
            System.err.println("Error saving session config: " + e.getMessage());
        }
    }

    public void associateAudioFile(String sessionName, File audioFile) {
        try {
            // Copy or move audio file to audio directory if it's not already there
            Path targetPath = DataDirectory.getAudioFile(audioFile.getName());
            if (!audioFile.getAbsolutePath().equals(targetPath.toAbsolutePath().toString())) {
                java.nio.file.Files.copy(
                        audioFile.toPath(),
                        targetPath,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                );
            }

            sessionConfig.addSession(sessionName, audioFile.getName());
            saveSessionConfig();

            System.out.println("Associated audio file " + audioFile.getName()
                    + " with session " + sessionName);
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

    public void setOutputService(OSCOutputService outputService) {
        this.outputService = outputService;
    }

    public void playSession(String sessionName) {
        try {
            File file = DataDirectory.getRecordingFile(sessionName + ".json").toFile();
            if (!file.exists()) {
                System.err.println("Recording file not found: " + file.getAbsolutePath());
                return;
            }

            RecordingSession session = objectMapper.readValue(file, RecordingSession.class);

            if (session == null || session.getMessages() == null || session.getMessages().isEmpty()) {
                System.err.println("Invalid session data");
                return;
            }


            System.out.println("Playing session: " + sessionName
                    + " (" + session.getMessages().size() + " messages)");

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
                    System.out.println("Session start time: " + sessionStartTime);
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
                                File audioFile = DataDirectory.getAudioFile(audioFileName).toFile();
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
                            } else {
                                mediaReady = true;
                            }
                        }
                        if (firstMessage) {
                            System.out.println("Waiting for media to be ready");
                            while (!mediaReady) {
                                try {
                                    Thread.sleep(5);
                                } catch (Exception e) {
                                }
                            }
                            sessionStartTime = msg.getTimestamp();
                            firstMessage = false;
                            System.out.println("Playing first message");
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
                                
                                // Apply rewrite handlers based on playback mode
                                if (playbackMode == PlaybackMode.WITH_REWRITE) {
                                    oscMsg = rewriteEngine.processMessage(oscMsg);
                                    if (oscMsg == null) {
                                        // Message was cancelled by rewrite handler
                                        continue;
                                    }
                                }
                                
                                outputService.send(oscMsg);
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
     public void stopPlayback() {
        stopPlayback.set(true);
        if (playbackThread != null) {
            playbackThread.interrupt();
        }
    }

    /**
     * Get the current playback mode.
     * @return The playback mode
     */
    public PlaybackMode getPlaybackMode() {
        return playbackMode;
    }
    
    /**
     * Set the playback mode.
     * @param mode The playback mode
     */
    public void setPlaybackMode(PlaybackMode mode) {
        this.playbackMode = mode;
    }
    
    /**
     * Get the rewrite engine used for playback.
     * @return The rewrite engine
     */
    public RewriteEngine getRewriteEngine() {
        return rewriteEngine;
    }
    
    /**
     * Register a rewrite handler for playback.
     * @param handler The handler to register
     */
    public void registerRewriteHandler(RewriteHandler handler) {
        rewriteEngine.registerHandler(handler);
    }
    
    /**
     * Unregister a rewrite handler from playback.
     * @param handler The handler to unregister
     */
    public void unregisterRewriteHandler(RewriteHandler handler) {
        rewriteEngine.unregisterHandler(handler);
    }
    
    /**
     * Clear all rewrite handlers from playback.
     */
    public void clearRewriteHandlers() {
        rewriteEngine.clearHandlers();
    }
    
    /**
     * Set the list of rewrite handlers, replacing any existing handlers.
     * @param handlers The new list of handlers
     */
    public void setRewriteHandlers(List<RewriteHandler> handlers) {
        rewriteEngine.setHandlers(handlers);
    }
    
    @Override
    protected void finalize() throws Throwable {
        super.finalize();
    }
}