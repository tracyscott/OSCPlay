package xyz.theforks;

import java.io.File;
import java.io.IOException;

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
import javafx.scene.control.ToggleButton;
import javafx.scene.image.Image;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;
import xyz.theforks.service.OSCInputService;
import xyz.theforks.service.OSCOutputService;
import xyz.theforks.service.OSCProxyService;

public class OSCProxyApp extends Application {

    private OSCProxyService proxyService;

    // UI Components
    private TextField inHostField;
    private TextField inPortField;
    private TextField outHostField;
    private TextField outPortField;
    private Button startButton;
    private Button stopButton;
    private Button manageButton;
    private Button recordButton;
    private Label messageCountLabel;
    private ComboBox<String> sessionComboBox;
    private Button playButton;
    private Button stopPlaybackButton;
    private ProgressBar playbackProgress;
    private Label playbackStatusLabel;
    private Button selectAudioButton;
    private Label audioFileLabel;
    private TextArea logArea;
    private Button openPadsButton;
    private Stage padWindow;
    private Sampler sampler;
    private ToggleButton inEnableButton;
    private ToggleButton outEnableButton;
    private ToggleButton proxyToggleButton;
    private Label statusBar;
    private Playback playback;

    private boolean isRecording = false;

    // CLI mode fields
    private static String sessionToPlay = null;
    private static String outHost = "127.0.0.1";
    private static int outPort = 3030;
    private static boolean cliMode = false;

    // Add fields
    private RewriteHandlerManager handlerManager;

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
                        outHost = args[++i];
                    }
                    break;
                case "--port":
                    if (i + 1 < args.length) {
                        try {
                            outPort = Integer.parseInt(args[++i]);
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
        // Currently we can only have one playback operation at a time so we have a single instance
        // here.  The various playback/stop playback buttons will control this instance.
        playback = new Playback();
        proxyService = new OSCProxyService();

        // Create UI components
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(10));
        grid.setHgap(10);
        grid.setVgap(10);

        // Input configuration
        Label inLabel = new Label("In");
        inLabel.setMinWidth(20);
        grid.add(inLabel, 0, 0);
        inHostField = new TextField("127.0.0.1");
        inHostField.setMinWidth(500);  // Doubled from 250
        inHostField.setStyle("-fx-font-size: 11px;");
        grid.add(inHostField, 1, 0);
        inPortField = new TextField("8000");
        inPortField.setMaxWidth(200);  // Doubled from 100
        inPortField.setStyle("-fx-font-size: 11px;");
        grid.add(inPortField, 2, 0);
        inEnableButton = new ToggleButton("Enable");
        inEnableButton.setSelected(false);
        inEnableButton.setMinWidth(100);
        grid.add(inEnableButton, 3, 0);

        Label outLabel = new Label("Out");
        outLabel.setMinWidth(20);
        grid.add(outLabel, 0, 1);
        outHostField = new TextField(outHost);
        outHostField.setMinWidth(500);  // Doubled from 250
        outHostField.setStyle("-fx-font-size: 11px;");
        grid.add(outHostField, 1, 1);
        outPortField = new TextField("" + outPort);
        outPortField.setMaxWidth(200);  // Doubled from 100
        outPortField.setStyle("-fx-font-size: 11px;");
        grid.add(outPortField, 2, 1);

        outEnableButton = new ToggleButton("Enable");
        outEnableButton.setSelected(false);
        outEnableButton.setMinWidth(100);
        grid.add(outEnableButton, 3, 1);


        // Create the logArea so we can pass it to RewriteHandlerManager
        logArea = new TextArea();
        // Create the status bar so we can pass it to RewriteHandlerManager
        statusBar = new Label();
        
        // Add after out configuration section:
        // Rewrite handlers section
        handlerManager = new RewriteHandlerManager(proxyService, logArea, statusBar);
        handlerManager.createUI(grid);

        // Control buttons
        proxyToggleButton = new ToggleButton("Enable Proxy");
        proxyToggleButton.setSelected(false);
        proxyToggleButton.setMinWidth(120);
         // Move proxy toggle button down
        grid.add(proxyToggleButton, 0, 7, 2, 1);

        // Recording controls
        HBox recordingControls = new HBox(10);
        recordButton = new Button("Start Recording");
        messageCountLabel = new Label("Messages: 0");
        recordingControls.getChildren().addAll(recordButton, messageCountLabel);
        grid.add(recordingControls, 0, 8, 2, 1);

        // Playback controls
        VBox playbackControls = new VBox(10);

        // Session selection
        HBox sessionControls = new HBox(10);
        sessionComboBox = new ComboBox<>();
        sessionComboBox.setMaxWidth(Double.MAX_VALUE);
        playButton = new Button("Play");
        stopPlaybackButton = new Button("Stop");
        stopPlaybackButton.setDisable(true);
        manageButton = new Button("Manage");
        

        sessionControls.getChildren().addAll(
                new Label("Recordings:"),
                sessionComboBox,
                playButton,
                stopPlaybackButton,
                manageButton
        );

        // Progress bar
        playbackProgress = new ProgressBar(0);
        playbackProgress.setMaxWidth(Double.MAX_VALUE);
        playbackStatusLabel = new Label("Ready");

        // Audio controls
        HBox audioControls = new HBox(10);
        selectAudioButton = new Button("Select Audio File");
        audioFileLabel = new Label("No audio file selected");
        audioControls.getChildren().addAll(selectAudioButton, audioFileLabel);
        playbackControls.getChildren().addAll(
                sessionControls,
                playbackProgress,
                playbackStatusLabel,
                audioControls
        );
        grid.add(playbackControls, 0, 9, 2, 1);

       
       // Sampler pads
        openPadsButton = new Button("Sampler Pads");
        HBox openPadsControls = new HBox(10);
        openPadsControls.getChildren().add(openPadsButton);
        sampler = new Sampler(proxyService.getInputService(), proxyService.getOutputService(), logArea);
        openPadsButton.setOnAction(e -> sampler.show());
        // Disable sampler pad interface for now
        // grid.add(openPadsControls, 0, 10, 2, 1);

        // Log area
        logArea.setEditable(false);
        logArea.setPrefRowCount(6);
        logArea.setWrapText(true);
        logArea.setMaxWidth(Double.MAX_VALUE); // Make it fill width
        logArea.setMaxHeight(Double.MAX_VALUE); // Allow vertical expansion
        GridPane.setHgrow(logArea, Priority.ALWAYS); // Allow horizontal growth
        GridPane.setVgrow(logArea, Priority.ALWAYS); // Allow vertical growth
        grid.add(logArea, 0, 11, GridPane.REMAINING, 1); // Span all columns

        // Status bar
        
        statusBar.setMaxWidth(Double.MAX_VALUE);
        statusBar.setPadding(new Insets(5));
        statusBar.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #cccccc; -fx-border-width: 1 0 0 0;");
        grid.add(statusBar, 0, 12, GridPane.REMAINING, 1);

        // Load the icon image
        Image icon = new Image(getClass().getResourceAsStream("/oscplayicon.png"));

        // Set the icon on the primary stage
        primaryStage.getIcons().add(icon);

        primaryStage.setResizable(true);
        primaryStage.setMinWidth(500);
        primaryStage.setWidth(800);
        // Set up event handlers
        setupEventHandlers();

        // Bind properties
        proxyService.messageCountProperty().addListener((obs, oldVal, newVal)
                -> messageCountLabel.setText("Messages: " + newVal.intValue()));

        
        playback.playbackProgressProperty().addListener((obs, oldVal, newVal) -> {
            playbackProgress.setProgress(newVal.doubleValue());
            updatePlaybackStatus(newVal.doubleValue());
        });

        playback.isPlayingProperty().addListener((obs, oldVal, newVal) -> {
            playButton.setDisable(newVal);
            stopPlaybackButton.setDisable(!newVal);
            sessionComboBox.setDisable(newVal);
        });
         
        // Session selection listener
        /*
        sessionComboBox.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldVal, newVal) -> {
                if (newVal != null) {
                    String audioFile = proxyService.getAssociatedAudioFile(newVal);
                    audioFileLabel.setText(audioFile != null ? audioFile : "No audio file selected");
                }
            }
        );
         */
        // Make grid expand horizontally
        GridPane.setHgrow(grid, Priority.ALWAYS);

        // Make input/output fields expand
        GridPane.setHgrow(inHostField, Priority.ALWAYS);
        GridPane.setHgrow(outHostField, Priority.ALWAYS);
        
        // Make recording controls expand
        GridPane.setHgrow(recordingControls, Priority.ALWAYS);
        HBox.setHgrow(messageCountLabel, Priority.ALWAYS);

        // Make session controls expand
        GridPane.setHgrow(sessionControls, Priority.ALWAYS);
        HBox.setHgrow(sessionComboBox, Priority.ALWAYS);
        
        // Make playback controls expand
        GridPane.setHgrow(playbackControls, Priority.ALWAYS);
        
        // Make audio controls expand
        GridPane.setHgrow(audioControls, Priority.ALWAYS);
        HBox.setHgrow(audioFileLabel, Priority.ALWAYS);
        
        // Make sampler controls expand
        GridPane.setHgrow(openPadsControls, Priority.ALWAYS);

        // Create scene
        Scene scene = new Scene(grid);
        primaryStage.setTitle("OSC Play");
        primaryStage.setScene(scene);
        primaryStage.show();

        // Update sessions list
        updateSessionsList();
    }

    private void setupEventHandlers() {
        inEnableButton.setOnAction(e -> {
            boolean enabled = inEnableButton.isSelected();
            // if enabled, turn the button green, otherwise grey
            if (enabled) {
                inEnableButton.setStyle("-fx-base: lightgreen;");
                inEnableButton.setText("Disable");
                if (proxyService != null) {
                    OSCInputService inputService = proxyService.getInputService();
                    if (inputService != null) {
                        inputService.setInHost(inHostField.getText());
                        inputService.setInPort(Integer.parseInt(inPortField.getText()));
                        try {
                            inputService.start();
                            log("Started listening at " + inputService.getInHost() + ":" + inputService.getInPort());
                        } catch (IOException ex) {
                            showError("Error starting input", ex.getMessage());
                            log("Error: " + ex.getMessage());
                        }
                    }
                }
            } else {
                inEnableButton.setStyle("");
                inEnableButton.setText("Enable");
                if (proxyService != null) {
                    OSCInputService inputService = proxyService.getInputService();
                    if (inputService != null) {
                        inputService.stop();
                        log("Stopped listening at " + inputService.getInHost() + ":" + inputService.getInPort());
                    }
                }
            }
        });

        outEnableButton.setOnAction(e -> {
            boolean enabled = outEnableButton.isSelected();
            if (enabled) {
                outEnableButton.setStyle("-fx-base: lightgreen;");
                outEnableButton.setText("Disable");
                if (proxyService != null) {
                    OSCOutputService outputService = proxyService.getOutputService();
                    if (outputService != null) {
                        outputService.setOutHost(outHostField.getText());
                        outputService.setOutPort(Integer.parseInt(outPortField.getText()));
                        try {
                            outputService.start();
                            log("Output started to " + outHostField.getText() + ":" + outPortField.getText());
                        } catch (IOException ex) {
                            showError("Error starting output", ex.getMessage());
                            log("Error: " + ex.getMessage());
                        }
                    }
                }
            } else {
                outEnableButton.setStyle("");
                outEnableButton.setText("Enable");
                if (proxyService != null) {
                    OSCOutputService outputService = proxyService.getOutputService();
                    if (outputService != null) {
                        outputService.stop();
                        log("Output stopped to " + outHostField.getText() + ":" + outPortField.getText());
                    }
                }
            }
        });

        proxyToggleButton.setOnAction(e -> {
            boolean enabled = proxyToggleButton.isSelected();
            if (enabled) {
                proxyToggleButton.setStyle("-fx-base: lightgreen;");
                proxyToggleButton.setText("Disable Proxy");
                try {
                    proxyService.setInHost(inHostField.getText());
                    proxyService.setInPort(Integer.parseInt(inPortField.getText()));
                    proxyService.setOutHost(outHostField.getText());
                    proxyService.setOutPort(Integer.parseInt(outPortField.getText()));
                    proxyService.startProxy();
                    log("Proxy started");
                } catch (Exception ex) {
                    showError("Error starting proxy", ex.getMessage());
                    log("Error: " + ex.getMessage());
                    proxyToggleButton.setSelected(false);
                }
            } else {
                proxyToggleButton.setStyle("");
                proxyToggleButton.setText("Enable Proxy");
                proxyService.stopProxy();
                log("Proxy stopped");
            }
        });

        recordButton.setOnAction(e -> {
            if (!isRecording) {
                TextInputDialog dialog = new TextInputDialog();
                dialog.setTitle("New Recording");
                dialog.setHeaderText("Enter a name for this recording session:");
                dialog.showAndWait().ifPresent(name -> {
                    OSCInputService inputService = proxyService.getInputService();
                    if (inputService == null) {
                        showError("Error", "Input service not started");
                        return;
                    }
                    try {
                        inputService.startRecording(name);
                        isRecording = true;
                    } catch (IOException ioex) {
                        log("Error starting recording: " + ioex.getMessage());
                        return;
                    }

                    recordButton.setText("Stop Recording");
                    log("Started recording session: " + name);
                });
            } else {
                OSCInputService inputService = proxyService.getInputService();
                if (inputService == null) {
                    showError("Error", "Input service not started");
                    return;
                }
                inputService.stopRecording();
                isRecording = false;
                recordButton.setText("Start Recording");
                updateSessionsList();
                log("Stopped recording");
            }
        });

        playButton.setOnAction(e -> {
            OSCOutputService outputService = proxyService.getOutputService();
            outputService.setOutHost(outHostField.getText());
            outputService.setOutPort(Integer.parseInt(outPortField.getText()));
            try {
                outputService.start();
            } catch (IOException ioex) {
                log("Error starting output: " + ioex.getMessage());
            }
           
            playback.setOutputService(outputService);
            playback.playSession(sessionComboBox.getSelectionModel().getSelectedItem());
            log("Playing session: " + sessionComboBox.getSelectionModel().getSelectedItem());
        });

        stopPlaybackButton.setOnAction(e -> {
            playback.stopPlayback();
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
                playback.associateAudioFile(selectedSession, selectedFile);
                audioFileLabel.setText(selectedFile.getName());
                log("Associated audio file '" + selectedFile.getName() + 
                    "' with session '" + selectedSession + "'");
            }
        });

        // Add hover handlers to controls
        proxyToggleButton.setOnMouseEntered(e -> 
            statusBar.setText(proxyToggleButton.isSelected() ? "Disable proxy" : "Enable proxy"));
        proxyToggleButton.setOnMouseExited(e -> 
            statusBar.setText(""));

        inEnableButton.setOnMouseEntered(e -> 
            statusBar.setText(inEnableButton.isSelected() ? "Disable input" : "Enable input"));
        inEnableButton.setOnMouseExited(e -> 
            statusBar.setText(""));

        outEnableButton.setOnMouseEntered(e -> 
            statusBar.setText(outEnableButton.isSelected() ? "Disable output" : "Enable output"));
        outEnableButton.setOnMouseExited(e -> 
            statusBar.setText(""));

        playButton.setOnMouseEntered(e ->
            statusBar.setText("Play recorded session"));
        playButton.setOnMouseExited(e ->
            statusBar.setText(""));

        recordButton.setOnMouseEntered(e ->
            statusBar.setText(isRecording ? "Stop recording" : "Start recording"));
        recordButton.setOnMouseExited(e ->
            statusBar.setText(""));

        manageButton.setOnAction(e -> {
            ManageRecordings manager = new ManageRecordings(proxyService);
            manager.show();
        });

        // Add hover handlers for input fields
        inHostField.setOnMouseEntered(e -> 
            statusBar.setText("Input Host: " + inHostField.getText()));
        inHostField.setOnMouseExited(e -> 
            statusBar.setText(""));

        inPortField.setOnMouseEntered(e -> 
            statusBar.setText("Input Port: " + inPortField.getText()));
        inPortField.setOnMouseExited(e -> 
            statusBar.setText(""));

        outHostField.setOnMouseEntered(e -> 
            statusBar.setText("Output Host: " + outHostField.getText()));
        outHostField.setOnMouseExited(e -> 
            statusBar.setText(""));

        outPortField.setOnMouseEntered(e -> 
            statusBar.setText("Output Port: " + outPortField.getText()));
        outPortField.setOnMouseExited(e -> 
            statusBar.setText(""));
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
        System.exit(0);
    }

    @Override
    public void stop() {
        proxyService.stopProxy();
    }
}
