package xyz.theforks;


import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.PriorityQueue;
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
import xyz.theforks.model.MessageRequest;
import xyz.theforks.model.OSCMessageRecord;
import xyz.theforks.model.RecordingSession;
import xyz.theforks.model.SessionSettings;
import xyz.theforks.nodes.PlaybackContext;
import xyz.theforks.service.OSCOutputService;
import xyz.theforks.service.OSCProxyService;
import xyz.theforks.util.DataDirectory;

/**
 * Encapsulates the playback of a recorded session.  For mult-trigger, there will be multiple instances
 * of this class, one for each trigger.
 */

public class Playback implements PlaybackContext {

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

    // Fields for delayed message handling
    private PriorityQueue<ScheduledMessage> messageQueue;
    private long playbackStartTime;
    private long sessionStartTime;

    /**
     * Internal class for scheduled messages with routing info
     */
    private static class ScheduledMessage implements Comparable<ScheduledMessage> {
        final OSCMessageRecord record;
        final long absoluteTimestamp;
        final String targetOutputId;  // null = all enabled, specific = route to this output only

        ScheduledMessage(OSCMessageRecord record, long absoluteTimestamp, String targetOutputId) {
            this.record = record;
            this.absoluteTimestamp = absoluteTimestamp;
            this.targetOutputId = targetOutputId;
        }

        @Override
        public int compareTo(ScheduledMessage other) {
            return Long.compare(this.absoluteTimestamp, other.absoluteTimestamp);
        }
    }

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

    // ========== PlaybackContext Implementation ==========

    @Override
    public void scheduleDelayedMessage(MessageRequest request, String outputId) {
        long absoluteTime = sessionStartTime + getCurrentPlaybackTime() + request.getDelayMs();

        // Create a new OSCMessageRecord from the request
        OSCMessageRecord record = new OSCMessageRecord(
            request.getMessage().getAddress(),
            request.getMessage().getArguments().toArray()
        );
        record.setTimestamp(absoluteTime);

        // Determine target output: use request's target if specified, otherwise the current output
        String targetOutput = request.hasTargetOutput() ?
            request.getTargetOutputId() : outputId;

        ScheduledMessage scheduled = new ScheduledMessage(record, absoluteTime, targetOutput);

        synchronized(messageQueue) {
            messageQueue.offer(scheduled);
        }

        System.out.println("Scheduled delayed message: " + record.getAddress() +
                         " delay=" + request.getDelayMs() + "ms output=" + targetOutput);
    }

    @Override
    public long getCurrentPlaybackTime() {
        return System.currentTimeMillis() - playbackStartTime;
    }

    // ========== Playback Methods ==========

    public void playSession(String sessionName) {
        try {
            RecordingSession session = RecordingSession.loadSession(sessionName);

            if (session == null || session.getMessages() == null || session.getMessages().isEmpty()) {
                System.err.println("Invalid session data");
                return;
            }

            // Initialize message queue with all session messages
            messageQueue = new PriorityQueue<>();
            for (OSCMessageRecord msg : session.getMessages()) {
                messageQueue.offer(new ScheduledMessage(msg, msg.getTimestamp(), null));
            }

            System.out.println("Playing session: " + sessionName
                    + " (" + messageQueue.size() + " messages)");

            // Reset control flags
            stopPlayback.set(false);

            Platform.runLater(() -> {
                playbackProgress.set(0);
                isPlaying.set(true);
            });

            // Create and start playback thread
            playbackThread = new Thread(() -> {
                try {
                    int totalMessages = session.getMessages().size();
                    int processedCount = 0;
                    boolean firstMessage = true;

                    while (!messageQueue.isEmpty() && !stopPlayback.get()) {
                        ScheduledMessage scheduled;

                        synchronized(messageQueue) {
                            scheduled = messageQueue.poll();
                        }

                        if (scheduled == null) break;

                        if (firstMessage) {
                            // Start audio if associated
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

                            System.out.println("Waiting for media to be ready");
                            while (!mediaReady) {
                                try {
                                    Thread.sleep(5);
                                } catch (Exception e) {
                                }
                            }

                            sessionStartTime = scheduled.absoluteTimestamp;
                            playbackStartTime = System.currentTimeMillis();
                            firstMessage = false;
                            System.out.println("Playing first message");
                        }

                        // Update progress
                        processedCount++;
                        final double progress = (double) processedCount / totalMessages;
                        Platform.runLater(() -> playbackProgress.set(progress));

                        try {
                            if (scheduled.record != null && scheduled.record.getAddress() != null && scheduled.record.getArguments() != null) {
                                // Calculate delay and sleep
                                long delay = scheduled.absoluteTimestamp - sessionStartTime;
                                Thread.sleep(Math.max(0, delay - (System.currentTimeMillis() - playbackStartTime)));

                                // Create OSC message
                                OSCMessage oscMsg = new OSCMessage(
                                    scheduled.record.getAddress(),
                                    List.of(scheduled.record.getArguments())
                                );

                                // Route based on targetOutputId
                                if (scheduled.targetOutputId != null) {
                                    // Send to specific output only
                                    OSCOutputService targetOutput = proxyService.getOutput(scheduled.targetOutputId);
                                    if (targetOutput != null) {
                                        sendToOutput(targetOutput, oscMsg, scheduled.targetOutputId);
                                    }
                                } else if (targetOutputId != null) {
                                    // Playback is configured for specific output
                                    OSCOutputService targetOutput = proxyService.getOutput(targetOutputId);
                                    if (targetOutput != null) {
                                        sendToOutput(targetOutput, oscMsg, targetOutputId);
                                    }
                                } else {
                                    // Send to all enabled outputs
                                    for (OSCOutputService output : proxyService.getOutputs()) {
                                        if (output.isEnabled()) {
                                            sendToOutput(output, oscMsg, output.getId());
                                        }
                                    }
                                }
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

    /**
     * Send message to a specific output, processing through its node chain with playback context.
     */
    private void sendToOutput(OSCOutputService output, OSCMessage message, String outputId) {
        try {
            // Process through output's node chain with playback context
            List<MessageRequest> requests = output.getNodeChain().processMessage(message, this);

            for (MessageRequest req : requests) {
                if (req.isImmediate()) {
                    // Send immediately
                    output.send(req.getMessage(), true);  // bypass enabled check
                } else {
                    // Schedule delayed message
                    scheduleDelayedMessage(req, outputId);
                }
            }
        } catch (Exception e) {
            System.err.println("Error sending to output: " + e.getMessage());
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