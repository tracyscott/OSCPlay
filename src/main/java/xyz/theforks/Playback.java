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
import xyz.theforks.model.RecordingSession;
import xyz.theforks.model.SessionSettings;
import xyz.theforks.service.OSCOutputService;
import xyz.theforks.service.OSCProxyService;
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
    private MediaPlayer mediaPlayer;
    private boolean mediaReady = false;
    private OSCProxyService proxyService;
    private String targetOutputId = null; // null means all enabled outputs

    public Playback() {
        DataDirectory.createDirectories();
    }

    public void associateAudioFile(String sessionName, File audioFile) {
        try {
            // Create session directory if it doesn't exist
            File sessionDir = DataDirectory.getSessionDir(sessionName).toFile();
            if (!sessionDir.exists()) {
                sessionDir.mkdirs();
            }

            // Copy audio file to session directory
            Path targetPath = DataDirectory.getSessionFile(sessionName, audioFile.getName());
            if (!audioFile.getAbsolutePath().equals(targetPath.toAbsolutePath().toString())) {
                java.nio.file.Files.copy(
                        audioFile.toPath(),
                        targetPath,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                );
            }

            // Save settings with audio file name
            SessionSettings settings = new SessionSettings(audioFile.getName());
            RecordingSession session = new RecordingSession(sessionName);
            session.saveSettings(settings);

            System.out.println("Associated audio file " + audioFile.getName()
                    + " with session " + sessionName);
        } catch (Exception e) {
            System.err.println("Error associating audio file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public String getAssociatedAudioFile(String sessionName) {
        try {
            SessionSettings settings = RecordingSession.loadSettings(sessionName);
            if (settings != null && settings.getAudioFileName() != null) {
                return settings.getAudioFileName();
            }
            return null;
        } catch (IOException e) {
            System.err.println("Error loading audio file association: " + e.getMessage());
            return null;
        }
    }

    public DoubleProperty playbackProgressProperty() {
        return playbackProgress;
    }

    public BooleanProperty isPlayingProperty() {
        return isPlaying;
    }

    /**
     * Set the proxy service which manages all outputs.
     * @param proxyService The proxy service
     */
    public void setProxyService(OSCProxyService proxyService) {
        this.proxyService = proxyService;
    }

    /**
     * Set the target output ID for playback routing.
     * @param outputId The output ID to route to, or null for all enabled outputs
     */
    public void setTargetOutputId(String outputId) {
        this.targetOutputId = outputId;
    }

    public void playSession(String sessionName) {
        try {
            RecordingSession session = RecordingSession.loadSession(sessionName);

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
                                File audioFile = DataDirectory.getSessionFile(sessionName, audioFileName).toFile();
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
                                } else {
                                    System.err.println("Audio file not found: " + audioFile.getAbsolutePath());
                                    mediaReady = true;
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

                                // Send to specific output or all enabled outputs
                                if (proxyService != null) {
                                    if (targetOutputId != null) {
                                        // Route to specific output (bypass enabled check)
                                        OSCOutputService targetOutput = proxyService.getOutput(targetOutputId);
                                        if (targetOutput != null) {
                                            System.out.println("DEBUG: Sending message to output '" + targetOutputId + "': " + oscMsg.getAddress());
                                            targetOutput.send(oscMsg, true);  // bypass enabled check
                                            System.out.println("DEBUG: Message sent successfully");
                                        } else {
                                            System.err.println("DEBUG: Target output '" + targetOutputId + "' not found!");
                                        }
                                    } else {
                                        // Route to all enabled outputs (respect enabled check)
                                        for (OSCOutputService output : proxyService.getOutputs()) {
                                            if (output.isEnabled()) {
                                                output.send(oscMsg);
                                            }
                                        }
                                    }
                                }
                                //System.out.println("Played back message " + (i + 1) + "/" + totalMessages +
                                //                 ": " + msg.getAddress());
                            }
                        } catch (InterruptedException e) {
                            break;
                        } catch (Exception e) {
                            System.err.println("Error playing message: " + e.getMessage());
                            e.printStackTrace();
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
        if (mediaPlayer != null) {
            Platform.runLater(() -> {
                mediaPlayer.stop();
                mediaPlayer.dispose();
                mediaPlayer = null;
            });
        }
    }

    
    @Override
    protected void finalize() throws Throwable {
        super.finalize();
    }
}