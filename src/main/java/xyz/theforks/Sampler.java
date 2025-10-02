package xyz.theforks;

import java.io.File;

import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import xyz.theforks.ui.Theme;
import xyz.theforks.service.OSCInputService;
import xyz.theforks.service.OSCOutputService;
import xyz.theforks.service.MIDIService;


public class Sampler {
    private Stage padWindow;
    private IntegerProperty[] messageCounters;
    private OSCInputService inputService;
    private OSCOutputService outputService;
    private TextArea logArea;
    private final String SAMPLER_DIR = "sampler";
    private MIDIService midiService;
    private ComboBox<String> midiDeviceSelector;
    private ToggleButton midiLearnButton;
    private int learnPadNumber = -1;
    private Button[] pads;
    private String[] midiMappings; // Store MIDI key for each pad

    public Sampler(OSCInputService inputService, OSCOutputService outputService, TextArea logArea) {
        this.inputService = inputService;
        this.outputService = outputService;
        this.logArea = logArea;
        this.messageCounters = new IntegerProperty[16];
        this.pads = new Button[16];
        this.midiMappings = new String[16];
        this.midiService = new MIDIService();

        // Initialize all message counters
        for (int i = 0; i < 16; i++) {
            messageCounters[i] = new SimpleIntegerProperty(0);
        }

        createDirectories();
        setStartRecordingCallback((padNumber, oscFilter) -> {
            inputService.addMessageHandlerWithFilter(oscFilter, (message) -> {
                if (messageCounters[padNumber] != null) {
                    messageCounters[padNumber].set(messageCounters[padNumber].get() + 1);
                }
                if (outputService != null) {
                    try {
                        outputService.send(message);
                    } catch (Exception e) {
                        log("Error sending message: " + e.getMessage());
                    }
                }
            });
            log("Recording started on pad " + padNumber);
        });
        setStopRecordingCallback((padNumber) -> {
            //inputService.stopRecording(padNumber);
            log("Recording stopped on pad " + padNumber);
        });
    }

    private void createDirectories() {
        new File(SAMPLER_DIR).mkdirs();
    }

    public void show() {
        if (padWindow == null) {
            createPadWindow();
        }
        padWindow.show();
    }

    public void hide() {
        if (padWindow != null) {
            padWindow.hide();
        }
    }

    public void resetMessageCount(int padNumber) {
        if (messageCounters[padNumber] != null) {
            messageCounters[padNumber].set(0);
        }
    }

    public interface RecordingStartCallback {
        void onRecordingStart(int padNumber, String oscFilter);
    }

    public interface RecordingStopCallback {
        void onRecordingStop(int padNumber);
    }

    private RecordingStartCallback startCallback;
    private RecordingStopCallback stopCallback;

    public void setStartRecordingCallback(RecordingStartCallback callback) {
        this.startCallback = callback;
    }

    public void setStopRecordingCallback(RecordingStopCallback callback) {
        this.stopCallback = callback;
    }

    public Stage createPadWindow() {
        padWindow = new Stage();
        padWindow.setTitle("Sampler Pads");

        VBox mainContainer = new VBox(10);
        mainContainer.setPadding(new Insets(10));

        // Create MIDI controls at the top
        HBox midiControls = createMIDIControls();

        GridPane padGrid = new GridPane();
        padGrid.setHgap(10);
        padGrid.setVgap(10);

        Color[] colors = {
            Color.RED, Color.BLUE, Color.GREEN, Color.PURPLE,
            Color.ORANGE, Color.CYAN, Color.MAGENTA, Color.YELLOW,
            Color.LIGHTBLUE, Color.LIGHTGREEN, Color.PINK, Color.CORAL,
            Color.DARKBLUE, Color.DARKGREEN, Color.DARKRED, Color.DARKORANGE
        };

        int colorIndex = 0;
        int padCount = 0;
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                VBox padContainer = createPadWithControls(colors[colorIndex++], padCount);
                padGrid.add(padContainer, col, row);
                padCount++;
            }
        }

        mainContainer.getChildren().addAll(midiControls);//, padGrid);

        Scene scene = new Scene(mainContainer);
        Theme.applyDark(scene);
        padWindow.setScene(scene);

        // Close MIDI device when window closes
        padWindow.setOnCloseRequest(e -> {
            if (midiService != null) {
                midiService.closeDevice();
            }
        });

        return padWindow;
    }

    private HBox createMIDIControls() {
        HBox midiBox = new HBox(10);
        midiBox.setAlignment(Pos.CENTER_LEFT);
        midiBox.setPadding(new Insets(5));

        Label midiLabel = new Label("MIDI Device:");
        midiLabel.setStyle("-fx-text-fill: white;");

        midiDeviceSelector = new ComboBox<>();
        midiDeviceSelector.setPromptText("Select MIDI Device");
        midiDeviceSelector.setPrefWidth(250);

        // Populate MIDI devices
        refreshMIDIDevices();

        // Handle device selection
        midiDeviceSelector.setOnAction(e -> {
            String selectedDevice = midiDeviceSelector.getValue();
            if (selectedDevice != null && !selectedDevice.isEmpty()) {
                if (midiService.openDevice(selectedDevice)) {
                    log("Connected to MIDI device: " + selectedDevice);
                    midiLearnButton.setDisable(false); // Enable learn button when device connected
                } else {
                    log("Failed to connect to MIDI device: " + selectedDevice);
                    midiLearnButton.setDisable(true);
                }
            }
        });

        Button refreshButton = new Button("Refresh");
        refreshButton.setOnAction(e -> refreshMIDIDevices());

        midiLearnButton = new ToggleButton("MIDI Learn");
        midiLearnButton.setStyle("""
            -fx-background-color: #404040;
            -fx-text-fill: white;
            -fx-font-size: 11pt;
            -fx-min-width: 100px;
            -fx-min-height: 28px;
            """);
        midiLearnButton.setDisable(true); // Initially disabled

        midiLearnButton.setOnAction(e -> {
            if (midiLearnButton.isSelected()) {
                midiLearnButton.setStyle("""
                    -fx-background-color: #ff6600;
                    -fx-text-fill: white;
                    -fx-font-size: 11pt;
                    -fx-min-width: 100px;
                    -fx-min-height: 28px;
                    -fx-effect: dropshadow(gaussian, rgba(255,102,0,0.6), 10, 0, 0, 0);
                    """);
                log("MIDI Learn mode: Click a pad, then press a MIDI button");
            } else {
                midiLearnButton.setStyle("""
                    -fx-background-color: #404040;
                    -fx-text-fill: white;
                    -fx-font-size: 11pt;
                    -fx-min-width: 100px;
                    -fx-min-height: 28px;
                    """);
                learnPadNumber = -1;
                log("MIDI Learn mode cancelled");
            }
        });

        midiBox.getChildren().addAll(midiLabel, midiDeviceSelector, refreshButton, midiLearnButton);
        return midiBox;
    }

    private void refreshMIDIDevices() {
        midiDeviceSelector.getItems().clear();
        midiDeviceSelector.getItems().addAll(midiService.getAvailableDevices());
    }

    private VBox createPadWithControls(Color baseColor, int padNumber) {
        // Create the main pad button
        Button pad = createPad(baseColor, padNumber);
        pads[padNumber] = pad;
        
        // Create record button
        ToggleButton recordButton = new ToggleButton("REC");
        recordButton.setStyle("""
            -fx-background-color: #808080;
            -fx-text-fill: white;
            -fx-font-size: 10pt;
            -fx-min-width: 45px;
            -fx-min-height: 24px;
            -fx-max-width: 45px;
            -fx-max-height: 24px;
            -fx-background-radius: 12px;
            -fx-padding: 2px 8px;
            """);

        // Create message counter label
        Label messageCounter = new Label("000000");
        messageCounter.setStyle("""
            -fx-font-family: monospace;
            -fx-font-size: 12pt;
            -fx-min-width: 70px;
            -fx-alignment: CENTER-RIGHT;
            -fx-padding: 0 5 0 5;
            -fx-background-color: #2b2b2b;
            -fx-text-fill: #00ff00;
            -fx-border-color: #404040;
            -fx-border-width: 1;
            -fx-border-radius: 3;
            """);

        // Bind the counter to the IntegerProperty
        messageCounter.textProperty().bind(messageCounters[padNumber].asString("%06d"));
            
        // Create OSC filter text field
        TextField oscFilterField = new TextField();
        oscFilterField.setPromptText("OSC Address Filter");
        oscFilterField.setPrefWidth(120);
        
        // Create container for record button, counter, and OSC filter
        HBox recordControls = new HBox(5);
        recordControls.setAlignment(Pos.CENTER);
        recordControls.getChildren().addAll(recordButton, messageCounter, oscFilterField);
        
        // Add listeners with callbacks
        recordButton.setOnAction(e -> {
            if (recordButton.isSelected()) {
                recordButton.setStyle("""
                    -fx-background-color: #ff0000;
                    -fx-text-fill: white;
                    -fx-font-size: 10pt;
                    -fx-min-width: 45px;
                    -fx-min-height: 24px;
                    -fx-max-width: 45px;
                    -fx-max-height: 24px;
                    -fx-background-radius: 12px;
                    -fx-padding: 2px 8px;
                    -fx-effect: dropshadow(gaussian, rgba(255,0,0,0.4), 10, 0, 0, 0);
                    """);
                resetMessageCount(padNumber);  // Reset counter when starting new recording
                if (startCallback != null) {
                    startCallback.onRecordingStart(padNumber, oscFilterField.getText());
                }
            } else {
                recordButton.setStyle("""
                    -fx-background-color: #808080;
                    -fx-text-fill: white;
                    -fx-font-size: 10pt;
                    -fx-min-width: 45px;
                    -fx-min-height: 24px;
                    -fx-max-width: 45px;
                    -fx-max-height: 24px;
                    -fx-background-radius: 12px;
                    -fx-padding: 2px 8px;
                    """);
                if (stopCallback != null) {
                    stopCallback.onRecordingStop(padNumber);
                }
            }
        });
        
        // Main container with new layout
        VBox container = new VBox(5);
        container.setAlignment(Pos.TOP_CENTER);
        container.getChildren().addAll(
            pad,             // Pad in the middle
            recordControls   // Record controls at the bottom
        );
        container.setPadding(new Insets(5));
        
        return container;
    }

    private Button createPad(Color baseColor, int padNumber) {
        Button pad = new Button();
        pad.setPrefSize(100, 100);
        pad.setMinSize(100, 100);

        String colorString = String.format("rgb(%d, %d, %d)",
            (int)(baseColor.getRed() * 255),
            (int)(baseColor.getGreen() * 255),
            (int)(baseColor.getBlue() * 255));

        Color brighterColor = baseColor.brighter();
        String brighterColorString = String.format("rgb(%d, %d, %d)",
            (int)(brighterColor.getRed() * 255),
            (int)(brighterColor.getGreen() * 255),
            (int)(brighterColor.getBlue() * 255));

        pad.setStyle(String.format("""
            -fx-background-color: %s;
            -fx-background-radius: 5;
            -fx-border-radius: 5;
            -fx-border-color: #333333;
            -fx-border-width: 2;
            """, colorString));

        // Handle pad press for MIDI learn mode
        pad.setOnMousePressed(e -> {
            pad.setStyle(String.format("""
                -fx-background-color: %s;
                -fx-background-radius: 5;
                -fx-border-radius: 5;
                -fx-border-color: #333333;
                -fx-border-width: 2;
                -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.8), 10, 0, 0, 0);
                """, brighterColorString));

            // If MIDI learn is active, set this pad as the learning target
            if (midiLearnButton != null && midiLearnButton.isSelected()) {
                learnPadNumber = padNumber;
                log("Pad " + padNumber + " selected. Now press a MIDI button...");

                // Enable learn mode in MIDI service
                midiService.enableLearnMode((messageType, midiNumber) -> {
                    Platform.runLater(() -> {
                        String midiKey = (messageType == 0 ? "note:" : "cc:") + midiNumber;
                        String messageTypeName = (messageType == 0 ? "Note" : "CC");

                        // Remove old mapping if exists
                        if (midiMappings[padNumber] != null) {
                            midiService.unregisterMessageHandler(midiMappings[padNumber]);
                        }

                        // Store new mapping
                        midiMappings[padNumber] = midiKey;

                        // Register handler to trigger pad
                        midiService.registerMessageHandler(midiKey, (num, vel) -> {
                            Platform.runLater(() -> triggerPad(padNumber));
                        });

                        log("Pad " + padNumber + " mapped to MIDI " + messageTypeName + " " + midiNumber);

                        // Reset learn mode
                        midiLearnButton.setSelected(false);
                        midiLearnButton.setStyle("""
                            -fx-background-color: #404040;
                            -fx-text-fill: white;
                            -fx-font-size: 11pt;
                            -fx-min-width: 100px;
                            -fx-min-height: 28px;
                            """);
                        learnPadNumber = -1;
                    });
                });
            }
        });

        pad.setOnMouseReleased(e ->
            pad.setStyle(String.format("""
                -fx-background-color: %s;
                -fx-background-radius: 5;
                -fx-border-radius: 5;
                -fx-border-color: #333333;
                -fx-border-width: 2;
                """, colorString))
        );

        return pad;
    }

    private void triggerPad(int padNumber) {
        if (pads[padNumber] != null) {
            Button pad = pads[padNumber];
            // Simulate visual press
            pad.fire();
            log("Pad " + padNumber + " triggered by MIDI");
        }
    }

    /**
     * Load MIDI mappings from project configuration.
     * @param mappings Map of pad number to MIDI key (e.g., "note:60" or "cc:7")
     */
    public void loadMidiMappings(java.util.Map<Integer, String> mappings) {
        if (mappings == null) {
            return;
        }

        // Clear existing mappings
        midiService.clearAllHandlers();

        // Load new mappings
        for (java.util.Map.Entry<Integer, String> entry : mappings.entrySet()) {
            int padNumber = entry.getKey();
            String midiKey = entry.getValue();

            if (padNumber >= 0 && padNumber < 16 && midiKey != null) {
                midiMappings[padNumber] = midiKey;

                // Register handler
                midiService.registerMessageHandler(midiKey, (num, vel) -> {
                    Platform.runLater(() -> triggerPad(padNumber));
                });

                log("Loaded MIDI mapping: Pad " + padNumber + " -> " + midiKey);
            }
        }
    }

    /**
     * Get current MIDI mappings for saving to project configuration.
     * @return Map of pad number to MIDI key
     */
    public java.util.Map<Integer, String> getMidiMappings() {
        java.util.Map<Integer, String> mappings = new java.util.HashMap<>();
        for (int i = 0; i < 16; i++) {
            if (midiMappings[i] != null) {
                mappings.put(i, midiMappings[i]);
            }
        }
        return mappings;
    }

    /**
     * Get the MIDI service instance.
     * @return MIDIService instance
     */
    public MIDIService getMidiService() {
        return midiService;
    }

    private void log(String message) {
        Platform.runLater(() -> {
            logArea.appendText(message + "\n");
            logArea.setScrollTop(Double.MAX_VALUE);
        });
    }
}
