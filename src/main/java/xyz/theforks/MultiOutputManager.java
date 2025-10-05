package xyz.theforks;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import xyz.theforks.service.OSCOutputService;
import xyz.theforks.service.OSCProxyService;
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
        for (OSCOutputService output : proxyService.getOutputs()) {
            String displayName = output.getId();
            if (!output.isEnabled()) {
                displayName += " [DISABLED]";
            }
            outputsList.getItems().add(displayName);
        }
    }

    private void addOutput() {
        Dialog<OSCOutputService> dialog = new Dialog<>();
        dialog.setTitle("Add Output");
        dialog.setHeaderText("Configure new OSC output");

        // Set up dialog buttons
        ButtonType addButtonType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        // Create input fields
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField idField = new TextField();
        idField.setPromptText("unique-id");
        TextField hostField = new TextField("127.0.0.1");
        TextField portField = new TextField("3030");
        CheckBox enabledCheckBox = new CheckBox("Proxy");
        enabledCheckBox.setSelected(true);

        grid.add(new Label("ID:"), 0, 0);
        grid.add(idField, 1, 0);
        grid.add(new Label("Host:"), 0, 1);
        grid.add(hostField, 1, 1);
        grid.add(new Label("Port:"), 0, 2);
        grid.add(portField, 1, 2);
        grid.add(enabledCheckBox, 1, 3);

        dialog.getDialogPane().setContent(grid);
        Theme.applyDark(dialog.getDialogPane().getScene());

        // Request focus on ID field
        idField.requestFocus();

        // Convert result when Add button is clicked
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType) {
                try {
                    String id = idField.getText().trim();
                    String host = hostField.getText().trim();
                    int port = Integer.parseInt(portField.getText().trim());

                    if (id.isEmpty()) {
                        showError("ID cannot be empty");
                        return null;
                    }

                    OSCOutputService output = new OSCOutputService(id);
                    output.setOutHost(host);
                    output.setOutPort(port);
                    output.setEnabled(enabledCheckBox.isSelected());
                    return output;
                } catch (NumberFormatException e) {
                    showError("Invalid port number");
                    return null;
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

        // Extract ID from display string (remove [DISABLED] suffix if present)
        String selectedId = selectedDisplay.replace(" [DISABLED]", "");

        OSCOutputService output = proxyService.getOutput(selectedId);
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

        TextField hostField = new TextField(output.getOutHost());
        TextField portField = new TextField(String.valueOf(output.getOutPort()));
        CheckBox enabledCheckBox = new CheckBox("Proxy");
        enabledCheckBox.setSelected(output.isEnabled());

        grid.add(new Label("ID:"), 0, 0);
        grid.add(new Label(output.getId()), 1, 0);
        grid.add(new Label("Host:"), 0, 1);
        grid.add(hostField, 1, 1);
        grid.add(new Label("Port:"), 0, 2);
        grid.add(portField, 1, 2);
        grid.add(enabledCheckBox, 1, 3);

        dialog.getDialogPane().setContent(grid);
        Theme.applyDark(dialog.getDialogPane().getScene());

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                try {
                    String host = hostField.getText().trim();
                    int port = Integer.parseInt(portField.getText().trim());

                    output.setOutHost(host);
                    output.setOutPort(port);
                    output.setEnabled(enabledCheckBox.isSelected());

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

        dialog.showAndWait();
    }

    private void removeOutput() {
        String selectedDisplay = outputsList.getSelectionModel().getSelectedItem();
        if (selectedDisplay == null) {
            showError("Please select an output to remove");
            return;
        }

        // Extract ID from display string (remove [DISABLED] suffix if present)
        String selectedId = selectedDisplay.replace(" [DISABLED]", "");

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
