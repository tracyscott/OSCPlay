package xyz.theforks.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import xyz.theforks.Playback;
import xyz.theforks.model.SamplerPad;
import xyz.theforks.service.MIDIService;
import xyz.theforks.service.OSCProxyService;
import xyz.theforks.service.ProjectManager;
import xyz.theforks.util.DataDirectory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * UI component for a 4x4 sampler pad grid.
 */
public class SamplerPadUI extends VBox {
    private static final int GRID_SIZE = 4;
    private static final String CONFIG_FILE = "sampler_pads.json";

    private final OSCProxyService proxyService;
    private final Playback playback;
    private final TextArea logArea;
    private final ProjectManager projectManager;
    private final GridPane padGrid;
    private final Map<Integer, SamplerPad> pads;
    private final Map<Integer, Button> padButtons;
    private final Map<Integer, Integer> activePads; // Maps padIndex to playing state
    private final ObjectMapper mapper;
    private final MIDIService midiService;
    private final Map<Integer, String> midiMappings; // Maps padIndex to MIDI key
    private ComboBox<String> midiDeviceSelector;
    private ToggleButton midiLearnButton;
    private int learnPadNumber = -1;

    public SamplerPadUI(OSCProxyService proxyService, Playback playback, TextArea logArea, ProjectManager projectManager) {
        this.proxyService = proxyService;
        this.playback = playback;
        this.logArea = logArea;
        this.projectManager = projectManager;
        this.pads = new HashMap<>();
        this.padButtons = new HashMap<>();
        this.activePads = new HashMap<>();
        this.mapper = new ObjectMapper();
        this.midiService = new MIDIService();
        this.midiMappings = new HashMap<>();

        // Listen to playback state changes
        playback.isPlayingProperty().addListener((obs, wasPlaying, isPlaying) -> {
            if (!isPlaying) {
                // Playback stopped - restore all active pad colors
                for (Integer padIndex : new HashMap<>(activePads).keySet()) {
                    restorePadColor(padIndex);
                }
            }
        });

        setPadding(new Insets(10));
        setSpacing(10);

        // Create MIDI controls
        HBox midiControls = createMIDIControls();

        // Create pad grid
        padGrid = new GridPane();
        padGrid.setHgap(5);
        padGrid.setVgap(5);
        padGrid.setPadding(new Insets(10));

        // Initialize pads
        initializePads();

        // Load saved configuration
        loadConfiguration();

        // Add controls and grid to container
        getChildren().addAll(midiControls, padGrid);
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
                    midiLearnButton.setDisable(false);

                    // Save device selection to project config
                    try {
                        xyz.theforks.model.ProjectConfig projectConfig = projectManager.getCurrentProject();
                        if (projectConfig != null) {
                            projectConfig.setMidiDeviceName(selectedDevice);
                            projectManager.saveProject();
                        }
                    } catch (Exception ex) {
                        log("Error saving MIDI device selection: " + ex.getMessage());
                    }
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
        midiLearnButton.setDisable(true);

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

    private void initializePads() {
        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                int padIndex = row * GRID_SIZE + col;
                Button padButton = createPadButton(padIndex);
                padButtons.put(padIndex, padButton);
                padGrid.add(padButton, col, row);

                // Configure column/row constraints for uniform sizing
                if (row == 0) {
                    ColumnConstraints colConstraints = new ColumnConstraints();
                    colConstraints.setPercentWidth(100.0 / GRID_SIZE);
                    colConstraints.setHgrow(Priority.ALWAYS);
                    padGrid.getColumnConstraints().add(colConstraints);
                }

                if (col == 0) {
                    RowConstraints rowConstraints = new RowConstraints();
                    rowConstraints.setPercentHeight(100.0 / GRID_SIZE);
                    rowConstraints.setVgrow(Priority.ALWAYS);
                    padGrid.getRowConstraints().add(rowConstraints);
                }
            }
        }
    }

    private Button createPadButton(int padIndex) {
        Button button = new Button("Empty");
        button.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        button.setPrefSize(150, 150);
        button.setStyle("-fx-background-color: #888888; -fx-text-fill: white; -fx-font-size: 14px;");

        // Left click: play pad or handle MIDI learn
        button.setOnAction(e -> {
            if (midiLearnButton != null && midiLearnButton.isSelected()) {
                handleMIDILearn(padIndex);
            } else {
                playPad(padIndex);
            }
        });

        // Right click: configure pad
        button.setOnContextMenuRequested(e -> {
            ContextMenu contextMenu = new ContextMenu();

            MenuItem configureItem = new MenuItem("Configure Pad...");
            configureItem.setOnAction(ev -> configurePad(padIndex));

            MenuItem clearItem = new MenuItem("Clear Pad");
            clearItem.setOnAction(ev -> clearPad(padIndex));

            MenuItem clearMidiItem = new MenuItem("Clear MIDI Mapping");
            clearMidiItem.setOnAction(ev -> clearMIDIMapping(padIndex));
            clearMidiItem.setDisable(!midiMappings.containsKey(padIndex));

            contextMenu.getItems().addAll(configureItem, clearItem, new SeparatorMenuItem(), clearMidiItem);
            contextMenu.show(button, e.getScreenX(), e.getScreenY());
        });

        return button;
    }

    private void handleMIDILearn(int padIndex) {
        learnPadNumber = padIndex;
        log("Pad " + padIndex + " selected. Now press a MIDI button...");

        // Enable learn mode in MIDI service
        midiService.enableLearnMode((messageType, midiNumber) -> {
            Platform.runLater(() -> {
                String midiKey = (messageType == 0 ? "note:" : "cc:") + midiNumber;
                String messageTypeName = (messageType == 0 ? "Note" : "CC");

                // Remove old mapping if exists
                if (midiMappings.containsKey(padIndex)) {
                    midiService.unregisterMessageHandler(midiMappings.get(padIndex));
                }

                // Store new mapping
                midiMappings.put(padIndex, midiKey);

                // Register handler to trigger pad
                midiService.registerMessageHandler(midiKey, (num, vel) -> {
                    Platform.runLater(() -> triggerPad(padIndex));
                });

                log("Pad " + padIndex + " mapped to MIDI " + messageTypeName + " " + midiNumber);
                saveConfiguration();

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

    private void triggerPad(int padIndex) {
        playPad(padIndex);
    }

    private void clearMIDIMapping(int padIndex) {
        String midiKey = midiMappings.remove(padIndex);
        if (midiKey != null) {
            midiService.unregisterMessageHandler(midiKey);
            log("Cleared MIDI mapping for pad " + padIndex);
            saveConfiguration();
        }
    }

    private void playPad(int padIndex) {
        // Check if this pad is currently playing - if so, stop it
        if (activePads.containsKey(padIndex)) {
            playback.stopPlayback();
            restorePadColor(padIndex);
            log("Stopped pad " + padIndex);
            return;
        }

        SamplerPad pad = pads.get(padIndex);
        if (pad == null || pad.isEmpty()) {
            log("Pad " + padIndex + " is empty");
            return;
        }

        // Change button to dark red
        Button button = padButtons.get(padIndex);
        activePads.put(padIndex, padIndex);
        button.setStyle("-fx-background-color: #8B0000; -fx-text-fill: white; -fx-font-size: 14px;");

        playback.setProxyService(proxyService);
        playback.playSession(pad.getSessionName());
        log("Playing pad " + padIndex + ": " + pad.getSessionName());
    }

    private void restorePadColor(int padIndex) {
        activePads.remove(padIndex);
        SamplerPad pad = pads.get(padIndex);
        Button button = padButtons.get(padIndex);

        if (pad != null && !pad.isEmpty()) {
            button.setStyle(String.format(
                    "-fx-background-color: %s; -fx-text-fill: white; -fx-font-size: 14px;",
                    pad.getColor()));
        } else {
            button.setStyle("-fx-background-color: #888888; -fx-text-fill: white; -fx-font-size: 14px;");
        }
    }

    private void configurePad(int padIndex) {
        Dialog<SamplerPad> dialog = new Dialog<>();
        dialog.setTitle("Configure Pad " + padIndex);
        dialog.setHeaderText("Map a recording to this pad");

        // Create dialog content
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        ComboBox<String> sessionCombo = new ComboBox<>();
        sessionCombo.getItems().addAll(proxyService.getRecordedSessions());
        sessionCombo.setMaxWidth(Double.MAX_VALUE);

        TextField labelField = new TextField();
        labelField.setPromptText("Optional display label");

        ColorPicker colorPicker = new ColorPicker(Color.web("#888888"));

        // Set current values if pad is configured
        SamplerPad currentPad = pads.get(padIndex);
        if (currentPad != null && !currentPad.isEmpty()) {
            sessionCombo.setValue(currentPad.getSessionName());
            labelField.setText(currentPad.getLabel());
            colorPicker.setValue(Color.web(currentPad.getColor()));
        }

        grid.add(new Label("Recording:"), 0, 0);
        grid.add(sessionCombo, 1, 0);
        grid.add(new Label("Label:"), 0, 1);
        grid.add(labelField, 1, 1);
        grid.add(new Label("Color:"), 0, 2);
        grid.add(colorPicker, 1, 2);

        dialog.getDialogPane().setContent(grid);

        ButtonType saveButton = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButton, ButtonType.CANCEL);

        dialog.setResultConverter(button -> {
            if (button == saveButton) {
                String sessionName = sessionCombo.getValue();
                if (sessionName != null && !sessionName.isEmpty()) {
                    String label = labelField.getText();
                    if (label == null || label.isEmpty()) {
                        label = sessionName;
                    }
                    String colorHex = String.format("#%02X%02X%02X",
                            (int) (colorPicker.getValue().getRed() * 255),
                            (int) (colorPicker.getValue().getGreen() * 255),
                            (int) (colorPicker.getValue().getBlue() * 255));
                    return new SamplerPad(sessionName, label, colorHex, null);
                }
            }
            return null;
        });

        dialog.showAndWait().ifPresent(pad -> {
            pads.put(padIndex, pad);
            updatePadButton(padIndex, pad);
            saveConfiguration();
        });
    }

    private void clearPad(int padIndex) {
        pads.remove(padIndex);
        Button button = padButtons.get(padIndex);
        button.setText("Empty");
        button.setStyle("-fx-background-color: #888888; -fx-text-fill: white; -fx-font-size: 14px;");
        saveConfiguration();
        log("Cleared pad " + padIndex);
    }

    private void updatePadButton(int padIndex, SamplerPad pad) {
        Button button = padButtons.get(padIndex);
        button.setText(pad.getLabel());
        button.setStyle(String.format(
                "-fx-background-color: %s; -fx-text-fill: white; -fx-font-size: 14px;",
                pad.getColor()));
    }

    private void saveConfiguration() {
        try {
            if (projectManager == null || !projectManager.hasOpenProject()) {
                log("Warning: No project open, cannot save sampler configuration");
                return;
            }

            // Save pad configuration
            Path configFile = projectManager.getProjectDir().resolve(CONFIG_FILE);
            mapper.writeValue(configFile.toFile(), pads);

            // Save MIDI mappings to project config
            xyz.theforks.model.ProjectConfig projectConfig = projectManager.getCurrentProject();
            if (projectConfig != null) {
                projectConfig.setMidiMappings(new HashMap<>(midiMappings));
                projectManager.saveProject();
            }
        } catch (Exception e) {
            log("Error saving sampler configuration: " + e.getMessage());
        }
    }

    private void loadConfiguration() {
        try {
            if (projectManager == null || !projectManager.hasOpenProject()) {
                // No project open, skip loading
                return;
            }

            // Load pad configuration
            Path configFile = projectManager.getProjectDir().resolve(CONFIG_FILE);
            if (configFile.toFile().exists()) {
                Map<String, SamplerPad> loadedPads = mapper.readValue(
                        configFile.toFile(),
                        mapper.getTypeFactory().constructMapType(HashMap.class, String.class, SamplerPad.class));

                // Convert String keys to Integer
                for (Map.Entry<String, SamplerPad> entry : loadedPads.entrySet()) {
                    int padIndex = Integer.parseInt(entry.getKey());
                    SamplerPad pad = entry.getValue();
                    pads.put(padIndex, pad);
                    updatePadButton(padIndex, pad);
                }
            }

            // Load MIDI mappings from project config
            xyz.theforks.model.ProjectConfig projectConfig = projectManager.getCurrentProject();
            if (projectConfig != null) {
                // Restore MIDI device selection
                String savedDeviceName = projectConfig.getMidiDeviceName();
                if (savedDeviceName != null && !savedDeviceName.isEmpty()) {
                    // Try to reconnect to the saved device
                    if (midiService.openDevice(savedDeviceName)) {
                        // Set the combo box value
                        Platform.runLater(() -> {
                            midiDeviceSelector.setValue(savedDeviceName);
                            midiLearnButton.setDisable(false);
                        });
                        log("Auto-reconnected to MIDI device: " + savedDeviceName);
                    } else {
                        log("Could not reconnect to saved MIDI device: " + savedDeviceName);
                        // Still set it in the combo box so user can see what was previously selected
                        Platform.runLater(() -> {
                            midiDeviceSelector.setValue(savedDeviceName);
                        });
                    }
                }

                // Load MIDI mappings
                Map<Integer, String> loadedMappings = projectConfig.getMidiMappings();
                if (loadedMappings != null) {
                    midiMappings.clear();
                    midiService.clearAllHandlers();

                    for (Map.Entry<Integer, String> entry : loadedMappings.entrySet()) {
                        int padIndex = entry.getKey();
                        String midiKey = entry.getValue();

                        midiMappings.put(padIndex, midiKey);

                        // Register handler
                        midiService.registerMessageHandler(midiKey, (num, vel) -> {
                            Platform.runLater(() -> triggerPad(padIndex));
                        });

                        log("Loaded MIDI mapping: Pad " + padIndex + " -> " + midiKey);
                    }
                }
            }
        } catch (Exception e) {
            log("Error loading sampler configuration: " + e.getMessage());
        }
    }

    private void log(String message) {
        if (logArea != null) {
            javafx.application.Platform.runLater(() -> {
                logArea.appendText(message + "\n");
                logArea.setScrollTop(Double.MAX_VALUE);
            });
        }
    }
}
