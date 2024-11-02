package xyz.theforks;

import java.io.File;
import java.util.concurrent.CountDownLatch;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.Image;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;
import xyz.theforks.service.OSCProxyService;

public class OSCProxyApp extends Application {
    private OSCProxyService proxyService;
    
    // UI Components
    private TextField listenPortField;
    private TextField forwardHostField;
    private TextField forwardPortField;
    private Button startButton;
    private Button stopButton;
    private Button recordButton;
    private Label messageCountLabel;
    private ComboBox<String> sessionComboBox;
    private Button playButton;
    private Button stopPlaybackButton;
    private TextField playbackHostField;
    private TextField playbackPortField;
    private ProgressBar playbackProgress;
    private Label playbackStatusLabel;
    private Button selectAudioButton;
    private Label audioFileLabel;
    private TextArea logArea;
    private boolean isRecording = false;

    // CLI mode fields
    private static String sessionToPlay = null;
    private static String playbackHost = "127.0.0.1";
    private static int playbackPort = 9000;
    private static boolean cliMode = false;

    public static void main(String[] args) {
        // Parse command line arguments
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--session":
                    if (i + 1 < args.length) {
                        sessionToPlay = args[++i];
                        cliMode = true;
                    }
                    break;
                case "--host":
                    if (i + 1 < args.length) {
                        playbackHost = args[++i];
                    }
                    break;
                case "--port":
                    if (i + 1 < args.length) {
                        try {
                            playbackPort = Integer.parseInt(args[++i]);
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid port number: " + args[i]);
                            System.exit(1);
                        }
                    }
                    break;
                case "--help":
                    printUsage();
                    System.exit(0);
                    break;
            }
        }

        if (cliMode) {
            runCliMode();
        } else {
            launch(args);
        }
    }

    @Override
    public void start(Stage primaryStage) {
        proxyService = new OSCProxyService();
        proxyService.setForwardHost(playbackHost);
        proxyService.setForwardPort(playbackPort);

        // Create UI components
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(10));
        grid.setHgap(10);
        grid.setVgap(10);

        // Port configuration
        grid.add(new Label("Listen Port:"), 0, 0);
        listenPortField = new TextField("8000");
        grid.add(listenPortField, 1, 0);

        grid.add(new Label("Forward Host:"), 0, 1);
        forwardHostField = new TextField("127.0.0.1");
        grid.add(forwardHostField, 1, 1);

        grid.add(new Label("Forward Port:"), 0, 2);
        forwardPortField = new TextField("9000");
        grid.add(forwardPortField, 1, 2);

        // Control buttons
        HBox controlButtons = new HBox(10);
        startButton = new Button("Start Proxy");
        stopButton = new Button("Stop Proxy");
        stopButton.setDisable(true);
        controlButtons.getChildren().addAll(startButton, stopButton);
        grid.add(controlButtons, 0, 3, 2, 1);

        // Recording controls
        HBox recordingControls = new HBox(10);
        recordButton = new Button("Start Recording");
        messageCountLabel = new Label("Messages: 0");
        recordingControls.getChildren().addAll(recordButton, messageCountLabel);
        grid.add(recordingControls, 0, 4, 2, 1);

        // Playback controls
        VBox playbackControls = new VBox(10);
        
        // Session selection
        HBox sessionControls = new HBox(10);
        sessionComboBox = new ComboBox<>();
        sessionComboBox.setMaxWidth(Double.MAX_VALUE);
        playButton = new Button("Play");
        stopPlaybackButton = new Button("Stop");
        stopPlaybackButton.setDisable(true);
        
        sessionControls.getChildren().addAll(
            new Label("Sessions:"),
            sessionComboBox,
            playButton,
            stopPlaybackButton
        );

        HBox playbackParams = new HBox(10);
        playbackHostField = new TextField(playbackHost);
        playbackHostField.setPrefWidth(100);
        playbackPortField = new TextField(Integer.toString(playbackPort));
        playbackPortField.setPrefWidth(60);
        playbackParams.getChildren().addAll(
            new Label("Playback Host:"),
            playbackHostField,
            new Label("Playback Port:"),
            playbackPortField
        );

        // Progress bar
        playbackProgress = new ProgressBar(0);
        playbackProgress.setMaxWidth(Double.MAX_VALUE);
        playbackStatusLabel = new Label("Ready");

        playbackControls.getChildren().addAll(
            sessionControls,
            playbackParams,
            playbackProgress,
            playbackStatusLabel
        );
        grid.add(playbackControls, 0, 5, 2, 1);

        // Audio controls
        HBox audioControls = new HBox(10);
        selectAudioButton = new Button("Select Audio File");
        audioFileLabel = new Label("No audio file selected");
        audioControls.getChildren().addAll(selectAudioButton, audioFileLabel);
        grid.add(audioControls, 0, 6, 2, 1);

        // Log area
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefRowCount(6);
        logArea.setWrapText(true);
        grid.add(logArea, 0, 7, 2, 1);

        // Load the icon image
        Image icon = new Image(getClass().getResourceAsStream("/oscplayicon.png"));
        
        // Set the icon on the primary stage
        primaryStage.getIcons().add(icon);
        
        primaryStage.setResizable(true);
        primaryStage.setMinWidth(500);
        primaryStage.setWidth(500);
        // Set up event handlers
        setupEventHandlers();

        // Bind properties
        proxyService.messageCountProperty().addListener((obs, oldVal, newVal) -> 
            messageCountLabel.setText("Messages: " + newVal.intValue()));

        proxyService.playbackProgressProperty().addListener((obs, oldVal, newVal) -> {
            playbackProgress.setProgress(newVal.doubleValue());
            updatePlaybackStatus(newVal.doubleValue());
        });

        proxyService.isPlayingProperty().addListener((obs, oldVal, newVal) -> {
            playButton.setDisable(newVal);
            stopPlaybackButton.setDisable(!newVal);
            sessionComboBox.setDisable(newVal);
        });

        // Session selection listener
        sessionComboBox.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldVal, newVal) -> {
                if (newVal != null) {
                    String audioFile = proxyService.getAssociatedAudioFile(newVal);
                    audioFileLabel.setText(audioFile != null ? audioFile : "No audio file selected");
                }
            }
        );

        // Create scene
        Scene scene = new Scene(grid);
        primaryStage.setTitle("OSC Play");
        primaryStage.setScene(scene);
        primaryStage.show();

        // Update sessions list
        updateSessionsList();
    }

    private void setupEventHandlers() {
        startButton.setOnAction(e -> {
            try {
                int listenPort = Integer.parseInt(listenPortField.getText());
                String forwardHost = forwardHostField.getText();
                int forwardPort = Integer.parseInt(forwardPortField.getText());
                proxyService.setForwardHost(forwardHost);
                proxyService.setForwardPort(forwardPort);
                proxyService.setListenPort(listenPort);
                proxyService.startProxy();
                startButton.setDisable(true);
                stopButton.setDisable(false);
                log("Proxy started - listening on port " + listenPort + 
                    " and forwarding to port " + forwardPort);
            } catch (Exception ex) {
                showError("Error starting proxy", ex.getMessage());
                log("Error: " + ex.getMessage());
            }
        });

        stopButton.setOnAction(e -> {
            proxyService.stopProxy();
            startButton.setDisable(false);
            stopButton.setDisable(true);
            log("Proxy stopped");
        });

        recordButton.setOnAction(e -> {
            if (!isRecording) {
                TextInputDialog dialog = new TextInputDialog();
                dialog.setTitle("New Recording");
                dialog.setHeaderText("Enter a name for this recording session:");
                dialog.showAndWait().ifPresent(name -> {
                    proxyService.startRecording(name);
                    isRecording = true;
                    recordButton.setText("Stop Recording");
                    log("Started recording session: " + name);
                });
            } else {
                proxyService.stopRecording();
                isRecording = false;
                recordButton.setText("Start Recording");
                updateSessionsList();
                log("Stopped recording");
            }
        });

        playButton.setOnAction(e -> {
            playbackHost = playbackHostField.getText();
            playbackPort = Integer.parseInt(playbackPortField.getText());
            proxyService.setPlaybackHost(playbackHost);
            proxyService.setPlaybackPort(playbackPort);
            String selected = sessionComboBox.getSelectionModel().getSelectedItem();
            if (selected != null) {
                proxyService.playSession(selected);
                log("Playing session: " + selected);
            }
        });

        stopPlaybackButton.setOnAction(e -> {
            proxyService.stopPlayback();
            log("Playback stopped");
        });

        selectAudioButton.setOnAction(e -> {
            String selectedSession = sessionComboBox.getSelectionModel().getSelectedItem();
            if (selectedSession == null) {
                showError("No Session Selected", "Please select a session first");
                return;
            }

            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select Audio File");
            fileChooser.getExtensionFilters().addAll(
                new ExtensionFilter("Audio Files", "*.wav", "*.mp3", "*.aac"),
                new ExtensionFilter("All Files", "*.*")
            );

            File selectedFile = fileChooser.showOpenDialog(selectAudioButton.getScene().getWindow());
            if (selectedFile != null) {
                proxyService.associateAudioFile(selectedSession, selectedFile);
                audioFileLabel.setText(selectedFile.getName());
                log("Associated audio file '" + selectedFile.getName() + 
                    "' with session '" + selectedSession + "'");
            }
        });
    }

    private void updatePlaybackStatus(double progress) {
        if (progress <= 0) {
            playbackStatusLabel.setText("Ready");
        } else if (progress >= 1.0) {
            playbackStatusLabel.setText("Completed");
        } else {
            playbackStatusLabel.setText(String.format("Playing: %.1f%%", progress * 100));
        }
    }

    private void updateSessionsList() {
        sessionComboBox.getItems().clear();
        sessionComboBox.getItems().addAll(proxyService.getRecordedSessions());
    }

    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void log(String message) {
        Platform.runLater(() -> {
            logArea.appendText(message + "\n");
            logArea.setScrollTop(Double.MAX_VALUE); // Scroll to bottom
        });
    }

    private static void printUsage() {
        System.out.println("Usage: java -jar osc-play.jar [options]");
        System.out.println("Options:");
        System.out.println("  --session <name>    Play specified session and exit");
        System.out.println("  --host <hostname>   Playback host (default: 127.0.0.1)");
        System.out.println("  --port <port>       Playback port (default: 9000)");
        System.out.println("  --help              Show this help message");
    }

    private static void runCliMode() {
        if (sessionToPlay == null) {
            System.err.println("No session specified");
            System.exit(1);
        }

        CountDownLatch playbackComplete = new CountDownLatch(1);
        OSCProxyService cliProxyService = new OSCProxyService();

        cliProxyService.setPlaybackHost(playbackHost);
        cliProxyService.setPlaybackPort(playbackPort);

        cliProxyService.isPlayingProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) {
                playbackComplete.countDown();
            }
        });

        cliProxyService.playbackProgressProperty().addListener((obs, oldVal, newVal) -> {
            //System.out.printf("Playback progress: %.1f%%\n", newVal.doubleValue() * 100);
        });

        try {
            System.out.println("Playing session: " + sessionToPlay);
            cliProxyService.playSession(sessionToPlay);
            playbackComplete.await();
            Thread.sleep(1000);
        } catch (Exception e) {
            System.err.println("Error during playback: " + e.getMessage());
            System.exit(1);
        }

        System.exit(0);
    }

    @Override
    public void stop() {
        proxyService.stopProxy();
    }
}