package xyz.theforks.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import xyz.theforks.Playback;
import xyz.theforks.model.SamplerPad;
import xyz.theforks.service.OSCOutputService;
import xyz.theforks.service.OSCProxyService;
import xyz.theforks.util.DataDirectory;

import java.io.File;
import java.io.IOException;
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
    private final GridPane padGrid;
    private final Map<Integer, SamplerPad> pads;
    private final Map<Integer, Button> padButtons;
    private final ObjectMapper mapper;

    public SamplerPadUI(OSCProxyService proxyService, Playback playback, TextArea logArea) {
        this.proxyService = proxyService;
        this.playback = playback;
        this.logArea = logArea;
        this.pads = new HashMap<>();
        this.padButtons = new HashMap<>();
        this.mapper = new ObjectMapper();

        setPadding(new Insets(10));
        setSpacing(10);

        // Create pad grid
        padGrid = new GridPane();
        padGrid.setHgap(5);
        padGrid.setVgap(5);
        padGrid.setPadding(new Insets(10));

        // Initialize pads
        initializePads();

        // Load saved configuration
        loadConfiguration();

        // Add grid to container
        getChildren().add(padGrid);
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

        // Left click: play pad
        button.setOnAction(e -> playPad(padIndex));

        // Right click: configure pad
        button.setOnContextMenuRequested(e -> {
            ContextMenu contextMenu = new ContextMenu();

            MenuItem configureItem = new MenuItem("Configure Pad...");
            configureItem.setOnAction(ev -> configurePad(padIndex));

            MenuItem clearItem = new MenuItem("Clear Pad");
            clearItem.setOnAction(ev -> clearPad(padIndex));

            contextMenu.getItems().addAll(configureItem, clearItem);
            contextMenu.show(button, e.getScreenX(), e.getScreenY());
        });

        return button;
    }

    private void playPad(int padIndex) {
        SamplerPad pad = pads.get(padIndex);
        if (pad == null || pad.isEmpty()) {
            log("Pad " + padIndex + " is empty");
            return;
        }

        OSCOutputService outputService = proxyService.getOutputService();
        if (outputService == null) {
            log("Output service not available");
            return;
        }

        playback.setOutputService(outputService);
        playback.playSession(pad.getSessionName());
        log("Playing pad " + padIndex + ": " + pad.getSessionName());
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
                    return new SamplerPad(sessionName, label, colorHex);
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
            File configFile = DataDirectory.getConfigFile(CONFIG_FILE).toFile();
            mapper.writeValue(configFile, pads);
        } catch (Exception e) {
            log("Error saving sampler configuration: " + e.getMessage());
        }
    }

    private void loadConfiguration() {
        try {
            File configFile = DataDirectory.getConfigFile(CONFIG_FILE).toFile();
            if (configFile.exists()) {
                Map<String, SamplerPad> loadedPads = mapper.readValue(
                        configFile,
                        mapper.getTypeFactory().constructMapType(HashMap.class, String.class, SamplerPad.class));

                // Convert String keys to Integer
                for (Map.Entry<String, SamplerPad> entry : loadedPads.entrySet()) {
                    int padIndex = Integer.parseInt(entry.getKey());
                    SamplerPad pad = entry.getValue();
                    pads.put(padIndex, pad);
                    updatePadButton(padIndex, pad);
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
