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
 * UI component for a 4x4 sampler pad grid with 4 banks.
 */
public class SamplerPadUI extends VBox {
    private static final int GRID_SIZE = 4;
    private static final int NUM_BANKS = 4;
    private static final int PADS_PER_BANK = 16;
    private static final String CONFIG_FILE = "sampler_pads.json";

    private final OSCProxyService proxyService;
    private final Playback playback;
    private final TextArea logArea;
    private final ProjectManager projectManager;
    private final Map<Integer, GridPane> bankGrids; // Maps bank index to GridPane
    private final Map<Integer, Map<Integer, SamplerPad>> bankPads; // Maps bank -> (padIndex -> SamplerPad)
    private final Map<Integer, Map<Integer, Button>> bankPadButtons; // Maps bank -> (padIndex -> Button)
    private final Map<Integer, ComboBox<String>> bankOutputRoutes; // Maps bank -> output routing ComboBox
    private final Map<String, Integer> activePads; // Maps "bank:padIndex" to playing state
    private final ObjectMapper mapper;
    private final MIDIService midiService;
    private final Map<String, String> midiMappings; // Maps "bank:padIndex" to MIDI key
    private ComboBox<String> midiDeviceSelector;
    private ToggleButton midiLearnButton;
    private int learnBankNumber = -1;
    private int learnPadNumber = -1;
    private boolean isLoading = false;

    public SamplerPadUI(OSCProxyService proxyService, Playback playback, TextArea logArea, ProjectManager projectManager) {
        this.proxyService = proxyService;
        this.playback = playback;
        this.logArea = logArea;
        this.projectManager = projectManager;
        this.bankGrids = new HashMap<>();
        this.bankPads = new HashMap<>();
        this.bankPadButtons = new HashMap<>();
        this.bankOutputRoutes = new HashMap<>();
        this.activePads = new HashMap<>();
        this.mapper = new ObjectMapper();
        this.midiService = new MIDIService();
        this.midiMappings = new HashMap<>();

        // Listen to playback state changes
        playback.isPlayingProperty().addListener((obs, wasPlaying, isPlaying) -> {
            if (!isPlaying) {
                // Playback stopped - restore all active pad colors
                for (String padKey : new HashMap<>(activePads).keySet()) {
                    String[] parts = padKey.split(":");
                    int bank = Integer.parseInt(parts[0]);
                    int padIndex = Integer.parseInt(parts[1]);
                    restorePadColor(bank, padIndex);
                }
            }
        });

        setPadding(new Insets(10));
        setSpacing(10);

        // Create MIDI controls
        HBox midiControls = createMIDIControls();

        // Create TabPane for banks
        TabPane bankTabPane = new TabPane();
        bankTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Initialize banks
        for (int bank = 0; bank < NUM_BANKS; bank++) {
            bankPads.put(bank, new HashMap<>());
            bankPadButtons.put(bank, new HashMap<>());

            // Create container for routing selector and pad grid
            VBox bankContainer = new VBox(10);
            bankContainer.setPadding(new Insets(10));

            // Create output routing selector
            HBox routingBox = new HBox(10);
            routingBox.setAlignment(Pos.CENTER_LEFT);
            Label routingLabel = new Label("Route to Output:");
            routingLabel.setStyle("-fx-text-fill: white;");
            ComboBox<String> outputRouteCombo = new ComboBox<>();
            outputRouteCombo.getItems().add("Proxy");
            updateOutputRouteComboBox(outputRouteCombo);
            outputRouteCombo.setValue("Proxy");
            outputRouteCombo.setMinWidth(200);
            bankOutputRoutes.put(bank, outputRouteCombo);

            // Save configuration when output route changes (but not during initial load)
            final int bankIndex = bank;
            outputRouteCombo.setOnAction(e -> {
                if (!isLoading) {
                    String route = outputRouteCombo.getValue();
                    log("Bank " + (bankIndex + 1) + " output route changed to: " + route);
                    saveConfiguration();
                }
            });

            routingBox.getChildren().addAll(routingLabel, outputRouteCombo);

            // Create pad grid for this bank
            GridPane padGrid = new GridPane();
            padGrid.setHgap(5);
            padGrid.setVgap(5);
            padGrid.setPadding(new Insets(10));
            bankGrids.put(bank, padGrid);

            // Initialize pads for this bank
            initializePadsForBank(bank, padGrid);

            bankContainer.getChildren().addAll(routingBox, padGrid);

            // Create tab for this bank
            Tab bankTab = new Tab("Bank " + (bank + 1), bankContainer);
            bankTabPane.getTabs().add(bankTab);
        }

        // Load saved configuration
        loadConfiguration();

        // Add controls and bank tabs to container
        getChildren().addAll(midiControls, bankTabPane);
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

    private void initializePadsForBank(int bank, GridPane padGrid) {
        Map<Integer, Button> padButtons = bankPadButtons.get(bank);

        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                int padIndex = row * GRID_SIZE + col;
                Button padButton = createPadButton(bank, padIndex);
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

    private Button createPadButton(int bank, int padIndex) {
        Button button = new Button("Empty");
        button.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        button.setPrefSize(150, 150);
        button.setStyle("-fx-background-color: #888888; -fx-text-fill: white; -fx-font-size: 14px;");

        // Left click: play pad or handle MIDI learn
        button.setOnAction(e -> {
            if (midiLearnButton != null && midiLearnButton.isSelected()) {
                handleMIDILearn(bank, padIndex);
            } else {
                playPad(bank, padIndex);
            }
        });

        // Right click: configure pad
        button.setOnContextMenuRequested(e -> {
            ContextMenu contextMenu = new ContextMenu();

            MenuItem configureItem = new MenuItem("Configure Pad...");
            configureItem.setOnAction(ev -> configurePad(bank, padIndex));

            MenuItem clearItem = new MenuItem("Clear Pad");
            clearItem.setOnAction(ev -> clearPad(bank, padIndex));

            MenuItem clearMidiItem = new MenuItem("Clear MIDI Mapping");
            String padKey = bank + ":" + padIndex;
            clearMidiItem.setOnAction(ev -> clearMIDIMapping(bank, padIndex));
            clearMidiItem.setDisable(!midiMappings.containsKey(padKey));

            contextMenu.getItems().addAll(configureItem, clearItem, new SeparatorMenuItem(), clearMidiItem);
            contextMenu.show(button, e.getScreenX(), e.getScreenY());
        });

        return button;
    }

    private void handleMIDILearn(int bank, int padIndex) {
        learnBankNumber = bank;
        learnPadNumber = padIndex;
        log("Bank " + (bank + 1) + " Pad " + padIndex + " selected. Now press a MIDI button...");

        // Enable learn mode in MIDI service
        midiService.enableLearnMode((messageType, midiNumber) -> {
            Platform.runLater(() -> {
                String midiKey = (messageType == 0 ? "note:" : "cc:") + midiNumber;
                String messageTypeName = (messageType == 0 ? "Note" : "CC");
                String padKey = bank + ":" + padIndex;

                // Remove old mapping if exists
                if (midiMappings.containsKey(padKey)) {
                    midiService.unregisterMessageHandler(midiMappings.get(padKey));
                }

                // Store new mapping
                midiMappings.put(padKey, midiKey);

                // Register handler to trigger pad
                midiService.registerMessageHandler(midiKey, (num, vel) -> {
                    Platform.runLater(() -> triggerPad(bank, padIndex));
                });

                log("Bank " + (bank + 1) + " Pad " + padIndex + " mapped to MIDI " + messageTypeName + " " + midiNumber);
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
                learnBankNumber = -1;
                learnPadNumber = -1;
            });
        });
    }

    private void triggerPad(int bank, int padIndex) {
        playPad(bank, padIndex);
    }

    /**
     * Public method to trigger a pad from OSC messages.
     * @param bankNumber Bank number (1-4)
     * @param padNumber Pad number (1-16)
     */
    public void triggerPadFromOSC(int bankNumber, int padNumber) {
        // Convert 1-based bank number to 0-based bank index
        int bank = bankNumber - 1;
        // Convert 1-based pad number to 0-based pad index
        int padIndex = padNumber - 1;

        // Validate bank and pad numbers
        if (bank < 0 || bank >= NUM_BANKS) {
            log("Invalid bank number: " + bankNumber + " (must be 1-" + NUM_BANKS + ")");
            return;
        }
        if (padIndex < 0 || padIndex >= PADS_PER_BANK) {
            log("Invalid pad number: " + padNumber + " (must be 1-" + PADS_PER_BANK + ")");
            return;
        }

        // Trigger the pad on the JavaFX thread
        javafx.application.Platform.runLater(() -> triggerPad(bank, padIndex));
    }

    private void clearMIDIMapping(int bank, int padIndex) {
        String padKey = bank + ":" + padIndex;
        String midiKey = midiMappings.remove(padKey);
        if (midiKey != null) {
            midiService.unregisterMessageHandler(midiKey);
            log("Cleared MIDI mapping for Bank " + (bank + 1) + " Pad " + padIndex);
            saveConfiguration();
        }
    }

    private void playPad(int bank, int padIndex) {
        String padKey = bank + ":" + padIndex;

        // Check if this pad is currently playing - if so, stop it
        if (activePads.containsKey(padKey)) {
            playback.stopPlayback();
            restorePadColor(bank, padIndex);
            log("Stopped Bank " + (bank + 1) + " Pad " + padIndex);
            return;
        }

        Map<Integer, SamplerPad> pads = bankPads.get(bank);
        SamplerPad pad = pads.get(padIndex);
        if (pad == null || pad.isEmpty()) {
            log("Bank " + (bank + 1) + " Pad " + padIndex + " is empty");
            return;
        }

        // Change button to dark red
        Map<Integer, Button> padButtons = bankPadButtons.get(bank);
        Button button = padButtons.get(padIndex);
        activePads.put(padKey, padIndex);
        button.setStyle("-fx-background-color: #8B0000; -fx-text-fill: white; -fx-font-size: 14px;");

        playback.setProxyService(proxyService);

        // Set output routing based on bank's outputRoute (not pad's)
        ComboBox<String> routeCombo = bankOutputRoutes.get(bank);
        String outputRoute = (routeCombo != null && routeCombo.getValue() != null)
            ? routeCombo.getValue()
            : "Proxy";

        log("DEBUG: Bank " + (bank + 1) + " outputRoute = '" + outputRoute + "'");
        if (outputRoute != null && !outputRoute.equals("Proxy")) {
            // Route to specific output
            playback.setTargetOutputId(outputRoute);
            log("Playing Bank " + (bank + 1) + " Pad " + (padIndex + 1) + ": " + pad.getSessionName() + " -> " + outputRoute);
        } else {
            // Route to all enabled outputs (Proxy mode)
            playback.setTargetOutputId(null);
            log("Playing Bank " + (bank + 1) + " Pad " + (padIndex + 1) + ": " + pad.getSessionName() + " -> Proxy (all enabled)");
        }

        playback.playSession(pad.getSessionName());
    }

    private void restorePadColor(int bank, int padIndex) {
        String padKey = bank + ":" + padIndex;
        activePads.remove(padKey);

        Map<Integer, SamplerPad> pads = bankPads.get(bank);
        Map<Integer, Button> padButtons = bankPadButtons.get(bank);
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

    private void configurePad(int bank, int padIndex) {
        Dialog<SamplerPad> dialog = new Dialog<>();
        dialog.setTitle("Configure Bank " + (bank + 1) + " Pad " + padIndex);
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
        Map<Integer, SamplerPad> pads = bankPads.get(bank);
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
                    // Output route is stored at bank level, not pad level
                    SamplerPad newPad = new SamplerPad(sessionName, label, colorHex, null, null);
                    return newPad;
                }
            }
            return null;
        });

        dialog.showAndWait().ifPresent(pad -> {
            pads.put(padIndex, pad);
            updatePadButton(bank, padIndex, pad);
            saveConfiguration();
        });
    }

    private void clearPad(int bank, int padIndex) {
        Map<Integer, SamplerPad> pads = bankPads.get(bank);
        Map<Integer, Button> padButtons = bankPadButtons.get(bank);
        pads.remove(padIndex);
        Button button = padButtons.get(padIndex);
        button.setText("Empty");
        button.setStyle("-fx-background-color: #888888; -fx-text-fill: white; -fx-font-size: 14px;");
        saveConfiguration();
        log("Cleared Bank " + (bank + 1) + " Pad " + padIndex);
    }

    private void updatePadButton(int bank, int padIndex, SamplerPad pad) {
        Map<Integer, Button> padButtons = bankPadButtons.get(bank);
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

            // Save pad configuration (convert to flat structure for serialization)
            Map<String, Object> config = new HashMap<>();
            Map<String, SamplerPad> flatPads = new HashMap<>();
            Map<String, String> bankRoutes = new HashMap<>();

            for (Map.Entry<Integer, Map<Integer, SamplerPad>> bankEntry : bankPads.entrySet()) {
                int bank = bankEntry.getKey();
                for (Map.Entry<Integer, SamplerPad> padEntry : bankEntry.getValue().entrySet()) {
                    int padIndex = padEntry.getKey();
                    String key = bank + ":" + padIndex;
                    flatPads.put(key, padEntry.getValue());
                }

                // Save bank output routing
                ComboBox<String> routeCombo = bankOutputRoutes.get(bank);
                if (routeCombo != null && routeCombo.getValue() != null) {
                    String route = routeCombo.getValue();
                    bankRoutes.put(String.valueOf(bank), route);
                    log("Saving Bank " + (bank + 1) + " route: '" + route + "'");
                }
            }

            config.put("pads", flatPads);
            config.put("bankRoutes", bankRoutes);

            Path configFile = projectManager.getProjectDir().resolve(CONFIG_FILE);
            mapper.writeValue(configFile.toFile(), config);
            log("Saved configuration with " + bankRoutes.size() + " bank routes");

            // Save MIDI mappings to project config
            xyz.theforks.model.ProjectConfig projectConfig = projectManager.getCurrentProject();
            if (projectConfig != null) {
                // Convert String keys to Integer keys for backward compatibility
                Map<Integer, String> legacyMappings = new HashMap<>();
                for (Map.Entry<String, String> entry : midiMappings.entrySet()) {
                    // Store with composite key as a string in the project config
                    // We'll use a negative hash to avoid collisions with old single-bank format
                    int key = entry.getKey().hashCode();
                    legacyMappings.put(key, entry.getValue() + "|" + entry.getKey());
                }
                projectConfig.setMidiMappings(legacyMappings);
                projectManager.saveProject();
            }
        } catch (Exception e) {
            log("Error saving sampler configuration: " + e.getMessage());
        }
    }

    private void loadConfiguration() {
        isLoading = true;
        try {
            if (projectManager == null || !projectManager.hasOpenProject()) {
                // No project open, skip loading
                return;
            }

            // Load pad configuration
            Path configFile = projectManager.getProjectDir().resolve(CONFIG_FILE);
            if (configFile.toFile().exists()) {
                // Try to load new format with pads and bankRoutes
                Map<String, Object> config = mapper.readValue(
                        configFile.toFile(),
                        mapper.getTypeFactory().constructMapType(HashMap.class, String.class, Object.class));

                // Load pads
                Object padsObj = config.get("pads");
                Map<String, SamplerPad> loadedPads;
                if (padsObj != null) {
                    loadedPads = mapper.convertValue(padsObj,
                            mapper.getTypeFactory().constructMapType(HashMap.class, String.class, SamplerPad.class));
                } else {
                    // Legacy format: entire config is just pads
                    loadedPads = mapper.convertValue(config,
                            mapper.getTypeFactory().constructMapType(HashMap.class, String.class, SamplerPad.class));
                }

                // Parse keys as "bank:padIndex" or legacy integer format
                for (Map.Entry<String, SamplerPad> entry : loadedPads.entrySet()) {
                    String key = entry.getKey();
                    SamplerPad pad = entry.getValue();

                    // Check if key contains ":" (new format)
                    if (key.contains(":")) {
                        String[] parts = key.split(":");
                        int bank = Integer.parseInt(parts[0]);
                        int padIndex = Integer.parseInt(parts[1]);
                        Map<Integer, SamplerPad> pads = bankPads.get(bank);
                        if (pads != null) {
                            pads.put(padIndex, pad);
                            updatePadButton(bank, padIndex, pad);
                            log("Loaded pad Bank " + (bank + 1) + " Pad " + (padIndex + 1) +
                                " outputRoute: '" + pad.getOutputRoute() + "'");
                        }
                    } else {
                        // Legacy format: assume bank 0
                        int padIndex = Integer.parseInt(key);
                        Map<Integer, SamplerPad> pads = bankPads.get(0);
                        if (pads != null) {
                            pads.put(padIndex, pad);
                            updatePadButton(0, padIndex, pad);
                            log("Loaded pad Bank 1 Pad " + (padIndex + 1) +
                                " outputRoute: '" + pad.getOutputRoute() + "' (legacy)");
                        }
                    }
                }

                // Load bank output routing
                Object routesObj = config.get("bankRoutes");
                if (routesObj != null) {
                    Map<String, String> bankRoutes = mapper.convertValue(routesObj,
                            mapper.getTypeFactory().constructMapType(HashMap.class, String.class, String.class));

                    for (Map.Entry<String, String> routeEntry : bankRoutes.entrySet()) {
                        int bank = Integer.parseInt(routeEntry.getKey());
                        String route = routeEntry.getValue();
                        ComboBox<String> routeCombo = bankOutputRoutes.get(bank);
                        if (routeCombo != null) {
                            // Make sure the route exists in the combo box
                            if (!routeCombo.getItems().contains(route)) {
                                routeCombo.getItems().add(route);
                            }
                            routeCombo.setValue(route);
                            log("Loaded Bank " + (bank + 1) + " output route: '" + route + "'");
                        }
                    }
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
                        String value = entry.getValue();

                        // New format: "midiKey|bank:padIndex"
                        if (value.contains("|")) {
                            String[] parts = value.split("\\|");
                            String midiKey = parts[0];
                            String padKey = parts[1];

                            midiMappings.put(padKey, midiKey);

                            // Parse bank and pad from padKey
                            String[] padParts = padKey.split(":");
                            int bank = Integer.parseInt(padParts[0]);
                            int padIndex = Integer.parseInt(padParts[1]);

                            // Register handler
                            midiService.registerMessageHandler(midiKey, (num, vel) -> {
                                Platform.runLater(() -> triggerPad(bank, padIndex));
                            });

                            log("Loaded MIDI mapping: Bank " + (bank + 1) + " Pad " + padIndex + " -> " + midiKey);
                        } else {
                            // Legacy format: assume bank 0
                            int padIndex = entry.getKey();
                            String midiKey = value;
                            String padKey = "0:" + padIndex;

                            midiMappings.put(padKey, midiKey);

                            // Register handler
                            midiService.registerMessageHandler(midiKey, (num, vel) -> {
                                Platform.runLater(() -> triggerPad(0, padIndex));
                            });

                            log("Loaded MIDI mapping (legacy): Bank 1 Pad " + padIndex + " -> " + midiKey);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log("Error loading sampler configuration: " + e.getMessage());
        } finally {
            isLoading = false;
        }
    }

    private void updateOutputRouteComboBox(ComboBox<String> comboBox) {
        // Add all available outputs from the proxy service
        for (xyz.theforks.service.OSCOutputService output : proxyService.getOutputs()) {
            if (!comboBox.getItems().contains(output.getId())) {
                comboBox.getItems().add(output.getId());
            }
        }
    }

    /**
     * Clear all sampler pad configurations and reset to empty state.
     * This includes clearing all pad assignments, bank output routes, and MIDI mappings.
     */
    public void clearAllPads() {
        Platform.runLater(() -> {
            // Clear all pad configurations
            for (Map.Entry<Integer, Map<Integer, SamplerPad>> bankEntry : bankPads.entrySet()) {
                int bank = bankEntry.getKey();
                Map<Integer, SamplerPad> pads = bankEntry.getValue();

                for (Map.Entry<Integer, SamplerPad> padEntry : pads.entrySet()) {
                    int padIndex = padEntry.getKey();

                    // Replace with empty pad (SamplerPad is immutable)
                    pads.put(padIndex, new SamplerPad());

                    // Update button appearance
                    Button button = bankPadButtons.get(bank).get(padIndex);
                    if (button != null) {
                        button.setText("Empty");
                        button.setStyle("-fx-background-color: #888888; -fx-text-fill: white; -fx-font-size: 14px;");
                    }
                }
            }

            // Reset all bank output routes to "Proxy"
            for (ComboBox<String> routeCombo : bankOutputRoutes.values()) {
                if (routeCombo != null) {
                    routeCombo.setValue("Proxy");
                }
            }

            // Clear MIDI mappings
            midiMappings.clear();

            // Clear active pads
            activePads.clear();

            // Save the cleared configuration
            saveConfiguration();

            log("Cleared all sampler pads and configurations");
        });
    }

    /**
     * Reload the sampler configuration from the current project.
     * Should be called when switching projects.
     */
    public void reloadConfiguration() {
        loadConfiguration();
    }

    /**
     * Get the output route ComboBox for a specific bank.
     * @param bank The bank index (0-3)
     * @return The ComboBox for the bank's output route, or null if bank is invalid
     */
    public ComboBox<String> getBankOutputRoute(int bank) {
        return bankOutputRoutes.get(bank);
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
