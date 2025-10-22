package xyz.theforks;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import xyz.theforks.model.OutputType;
import xyz.theforks.service.MIDIOutputService;
import xyz.theforks.service.OSCOutputService;
import xyz.theforks.service.OSCProxyService;
import xyz.theforks.service.OutputService;
import xyz.theforks.ui.Theme;

/**
 * Dialog for managing multiple OSC outputs.
 * Allows adding, editing, and removing output configurations.
 */
public class MultiOutputManager {
    private final OSCProxyService proxyService;
    private final Runnable onOutputsChanged;
    private final Runnable onSaveConfig;
    private Stage stage;
    private ListView<String> outputsList;

    public MultiOutputManager(OSCProxyService proxyService, Runnable onOutputsChanged) {
        this(proxyService, onOutputsChanged, null);
    }

    public MultiOutputManager(OSCProxyService proxyService, Runnable onOutputsChanged, Runnable onSaveConfig) {
        this.proxyService = proxyService;
        this.onOutputsChanged = onOutputsChanged;
        this.onSaveConfig = onSaveConfig;
    }

    public void show() {
        stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Manage Outputs");

        VBox root = new VBox(10);
        root.setPadding(new Insets(10));

        // Output list
        Label listLabel = new Label("Configured Outputs:");
        outputsList = new ListView<>();
        outputsList.setPrefHeight(200);
        updateOutputsList();

        // Buttons
        HBox buttonBox = new HBox(10);
        Button addButton = new Button("Add Output");
        Button editButton = new Button("Edit");
        Button removeButton = new Button("Remove");
        Button closeButton = new Button("Close");

        buttonBox.getChildren().addAll(addButton, editButton, removeButton, closeButton);

        root.getChildren().addAll(listLabel, outputsList, buttonBox);

        // Event handlers
        addButton.setOnAction(e -> addOutput());
        editButton.setOnAction(e -> editOutput());
        removeButton.setOnAction(e -> removeOutput());
        closeButton.setOnAction(e -> stage.close());

        // Disable edit/remove for default output
        outputsList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                String id = newVal.replace(" [DISABLED]", "");
                boolean isDefault = "default".equals(id);
                removeButton.setDisable(isDefault);
            }
        });

        Scene scene = new Scene(root, 400, 300);
        Theme.applyDark(scene);
        stage.setScene(scene);
        stage.show();
    }

    private void updateOutputsList() {
        outputsList.getItems().clear();
        for (OutputService output : proxyService.getOutputs()) {
            String displayName = output.getId();

            // Add type indicator
            if (output instanceof MIDIOutputService) {
                displayName += " (MIDI)";
            } else if (output instanceof OSCOutputService) {
                displayName += " (OSC)";
            }

            if (!output.isEnabled()) {
                displayName += " [DISABLED]";
            }
            outputsList.getItems().add(displayName);
        }
    }

    private void addOutput() {
        Dialog<OutputService> dialog = new Dialog<>();
        dialog.setTitle("Add Output");
        dialog.setHeaderText("Configure new output");

        // Set up dialog buttons
        ButtonType addButtonType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        // Create input fields
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        // Output type selection
        ToggleGroup typeGroup = new ToggleGroup();
        RadioButton oscRadio = new RadioButton("OSC");
        RadioButton midiRadio = new RadioButton("MIDI");
        oscRadio.setToggleGroup(typeGroup);
        midiRadio.setToggleGroup(typeGroup);
        oscRadio.setSelected(true);

        HBox typeBox = new HBox(10, oscRadio, midiRadio);

        // Common fields
        TextField idField = new TextField();
        idField.setPromptText("unique-id");
        CheckBox enabledCheckBox = new CheckBox("Enabled");
        enabledCheckBox.setSelected(true);

        // OSC-specific fields
        TextField hostField = new TextField("127.0.0.1");
        TextField portField = new TextField("3030");
        Label hostLabel = new Label("Host:");
        Label portLabel = new Label("Port:");

        // MIDI-specific fields
        ComboBox<String> midiDeviceCombo = new ComboBox<>();
        midiDeviceCombo.getItems().addAll(MIDIOutputService.getAvailableMIDIDevices());
        if (!midiDeviceCombo.getItems().isEmpty()) {
            midiDeviceCombo.getSelectionModel().selectFirst();
        }
        Label midiDeviceLabel = new Label("MIDI Device:");

        // Add common fields to grid
        int row = 0;
        grid.add(new Label("Type:"), 0, row);
        grid.add(typeBox, 1, row++);
        grid.add(new Label("ID:"), 0, row);
        grid.add(idField, 1, row++);

        // Initially show OSC fields
        grid.add(hostLabel, 0, row);
        grid.add(hostField, 1, row++);
        grid.add(portLabel, 0, row);
        grid.add(portField, 1, row++);
        grid.add(enabledCheckBox, 1, row++);

        // Handle type selection changes
        typeGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == oscRadio) {
                // Show OSC fields, hide MIDI fields
                grid.getChildren().removeAll(midiDeviceLabel, midiDeviceCombo);
                if (!grid.getChildren().contains(hostLabel)) {
                    grid.add(hostLabel, 0, 2);
                    grid.add(hostField, 1, 2);
                    grid.add(portLabel, 0, 3);
                    grid.add(portField, 1, 3);
                }
            } else if (newVal == midiRadio) {
                // Show MIDI fields, hide OSC fields
                grid.getChildren().removeAll(hostLabel, hostField, portLabel, portField);
                if (!grid.getChildren().contains(midiDeviceLabel)) {
                    grid.add(midiDeviceLabel, 0, 2);
                    grid.add(midiDeviceCombo, 1, 2);
                }
            }
        });

        dialog.getDialogPane().setContent(grid);
        Theme.applyDark(dialog.getDialogPane().getScene());

        // Request focus on ID field
        idField.requestFocus();

        // Convert result when Add button is clicked
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType) {
                String id = idField.getText().trim();

                if (id.isEmpty()) {
                    showError("ID cannot be empty");
                    return null;
                }

                if (oscRadio.isSelected()) {
                    try {
                        String host = hostField.getText().trim();
                        int port = Integer.parseInt(portField.getText().trim());

                        OSCOutputService output = new OSCOutputService(id);
                        output.setOutHost(host);
                        output.setOutPort(port);
                        output.setEnabled(enabledCheckBox.isSelected());
                        return output;
                    } catch (NumberFormatException e) {
                        showError("Invalid port number");
                        return null;
                    }
                } else {
                    // MIDI output
                    String deviceName = midiDeviceCombo.getSelectionModel().getSelectedItem();
                    if (deviceName == null || deviceName.isEmpty()) {
                        showError("Please select a MIDI device");
                        return null;
                    }

                    MIDIOutputService output = new MIDIOutputService(id);
                    output.setMidiDeviceName(deviceName);
                    output.setEnabled(enabledCheckBox.isSelected());
                    return output;
                }
            }
            return null;
        });

        dialog.showAndWait().ifPresent(output -> {
            if (proxyService.addOutput(output)) {
                updateOutputsList();
                if (onOutputsChanged != null) {
                    onOutputsChanged.run();
                }
                if (onSaveConfig != null) {
                    onSaveConfig.run();
                }
            } else {
                showError("Output with ID '" + output.getId() + "' already exists");
            }
        });
    }

    private void editOutput() {
        String selectedDisplay = outputsList.getSelectionModel().getSelectedItem();
        if (selectedDisplay == null) {
            showError("Please select an output to edit");
            return;
        }

        // Extract ID from display string (remove type indicator and [DISABLED] suffix)
        String selectedId = selectedDisplay.replace(" (OSC)", "")
                                          .replace(" (MIDI)", "")
                                          .replace(" [DISABLED]", "");

        OutputService output = proxyService.getOutput(selectedId);
        if (output == null) {
            return;
        }

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Edit Output");
        dialog.setHeaderText("Edit output: " + output.getId());

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        int row = 0;

        // ID (non-editable)
        grid.add(new Label("ID:"), 0, row);
        grid.add(new Label(output.getId()), 1, row++);

        // Type (non-editable)
        String typeStr = output instanceof MIDIOutputService ? "MIDI" : "OSC";
        grid.add(new Label("Type:"), 0, row);
        grid.add(new Label(typeStr), 1, row++);

        CheckBox enabledCheckBox = new CheckBox("Enabled");
        enabledCheckBox.setSelected(output.isEnabled());

        if (output instanceof OSCOutputService) {
            OSCOutputService oscOutput = (OSCOutputService) output;

            TextField hostField = new TextField(oscOutput.getOutHost());
            TextField portField = new TextField(String.valueOf(oscOutput.getOutPort()));

            grid.add(new Label("Host:"), 0, row);
            grid.add(hostField, 1, row++);
            grid.add(new Label("Port:"), 0, row);
            grid.add(portField, 1, row++);
            grid.add(enabledCheckBox, 1, row++);

            dialog.getDialogPane().setContent(grid);
            Theme.applyDark(dialog.getDialogPane().getScene());

            dialog.setResultConverter(dialogButton -> {
                if (dialogButton == saveButtonType) {
                    try {
                        String host = hostField.getText().trim();
                        int port = Integer.parseInt(portField.getText().trim());

                        oscOutput.setOutHost(host);
                        oscOutput.setOutPort(port);
                        oscOutput.setEnabled(enabledCheckBox.isSelected());

                        updateOutputsList();
                        if (onOutputsChanged != null) {
                            onOutputsChanged.run();
                        }
                        if (onSaveConfig != null) {
                            onSaveConfig.run();
                        }
                    } catch (NumberFormatException e) {
                        showError("Invalid port number");
                    }
                }
                return null;
            });
        } else if (output instanceof MIDIOutputService) {
            MIDIOutputService midiOutput = (MIDIOutputService) output;

            ComboBox<String> midiDeviceCombo = new ComboBox<>();
            midiDeviceCombo.getItems().addAll(MIDIOutputService.getAvailableMIDIDevices());
            midiDeviceCombo.getSelectionModel().select(midiOutput.getMidiDeviceName());

            grid.add(new Label("MIDI Device:"), 0, row);
            grid.add(midiDeviceCombo, 1, row++);
            grid.add(enabledCheckBox, 1, row++);

            dialog.getDialogPane().setContent(grid);
            Theme.applyDark(dialog.getDialogPane().getScene());

            dialog.setResultConverter(dialogButton -> {
                if (dialogButton == saveButtonType) {
                    String deviceName = midiDeviceCombo.getSelectionModel().getSelectedItem();
                    if (deviceName == null || deviceName.isEmpty()) {
                        showError("Please select a MIDI device");
                        return null;
                    }

                    midiOutput.setMidiDeviceName(deviceName);
                    midiOutput.setEnabled(enabledCheckBox.isSelected());

                    updateOutputsList();
                    if (onOutputsChanged != null) {
                        onOutputsChanged.run();
                    }
                    if (onSaveConfig != null) {
                        onSaveConfig.run();
                    }
                }
                return null;
            });
        }

        dialog.showAndWait();
    }

    private void removeOutput() {
        String selectedDisplay = outputsList.getSelectionModel().getSelectedItem();
        if (selectedDisplay == null) {
            showError("Please select an output to remove");
            return;
        }

        // Extract ID from display string (remove type indicator and [DISABLED] suffix)
        String selectedId = selectedDisplay.replace(" (OSC)", "")
                                          .replace(" (MIDI)", "")
                                          .replace(" [DISABLED]", "");

        if ("default".equals(selectedId)) {
            showError("Cannot remove default output");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Removal");
        confirm.setHeaderText("Remove output: " + selectedId);
        confirm.setContentText("Are you sure you want to remove this output?");
        Theme.applyDark(confirm.getDialogPane().getScene());

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                if (proxyService.removeOutput(selectedId)) {
                    updateOutputsList();
                    if (onOutputsChanged != null) {
                        onOutputsChanged.run();
                    }
                    if (onSaveConfig != null) {
                        onSaveConfig.run();
                    }
                }
            }
        });
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setContentText(message);
        Theme.applyDark(alert.getDialogPane().getScene());
        alert.showAndWait();
    }
}
