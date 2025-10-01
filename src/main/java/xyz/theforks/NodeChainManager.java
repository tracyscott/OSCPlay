package xyz.theforks;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import xyz.theforks.Playback;
import xyz.theforks.nodes.OSCNode;
import xyz.theforks.nodes.NodeRegistry;
import xyz.theforks.service.OSCOutputService;
import xyz.theforks.service.OSCProxyService;
import xyz.theforks.service.ProjectManager;
import xyz.theforks.ui.Theme;
import xyz.theforks.util.DataDirectory;

public class NodeChainManager {
    private final OSCProxyService proxyService;
    private final TextArea logArea;
    private final Label statusBar;
    private ProjectManager projectManager;
    private Playback playback; // Optional playback instance for node synchronization
    private ListView<OSCNode> nodesListView;
    private ObservableList<OSCNode> activeNodes = FXCollections.observableArrayList();
    private String currentConfigFile = null;
    private FileChooser fileChooser;
    private Label nodesLabel;
    private String outputId = "default"; // Current output being managed
    private Runnable onNodeChainChanged; // Callback when node chain changes

    public NodeChainManager(OSCProxyService proxyService, TextArea logArea, Label statusBar) {
        this.proxyService = proxyService;
        this.logArea = logArea;
        this.statusBar = statusBar;
    }

    public void setProjectManager(ProjectManager projectManager) {
        this.projectManager = projectManager;
    }

    /**
     * Set a callback to be invoked when the node chain changes.
     */
    public void setOnNodeChainChanged(Runnable callback) {
        this.onNodeChainChanged = callback;
    }

    private void notifyNodeChainChanged() {
        if (onNodeChainChanged != null) {
            onNodeChainChanged.run();
        }
    }

    /**
     * Set the output ID for this node chain manager.
     * Changes which output's node chain is being managed.
     */
    public void setOutputId(String outputId) {
        this.outputId = outputId;
        // Reload nodes for the new output
        reloadNodesFromService();
    }

    /**
     * Set the playback instance for node synchronization.
     * When set, nodes will be synchronized between proxy and playback.
     * @param playback The playback instance
     */
    public void setPlayback(Playback playback) {
        this.playback = playback;
        // Note: Playback now uses all enabled outputs from proxyService directly
    }

    /**
     * Reload the active nodes list from the current output's node chain.
     */
    private void reloadNodesFromService() {
        syncNodesFromOutput();
    }

    /**
     * Sync the active nodes list from the current output's node chain.
     */
    private void syncNodesFromOutput() {
        activeNodes.clear();
        OSCOutputService output = proxyService.getOutput(outputId);
        if (output != null) {
            List<OSCNode> nodes = output.getNodeChain().getNodes();
            activeNodes.addAll(nodes);
        }
        updateNodesLabel();
    }

    public void createUI(GridPane grid, int startRow) {
        // Node chain section
        nodesLabel = new Label("Node Chain Config:");
        grid.add(nodesLabel, 0, startRow, GridPane.REMAINING, 1);  // Modified to span all columns

        // Node management buttons on their own row below the list
        HBox nodeButtons = new HBox(10);
        setupNodeButtons(nodeButtons);
        GridPane.setHgrow(nodeButtons, Priority.ALWAYS);
        grid.add(nodeButtons, 0, startRow + 1, GridPane.REMAINING, 1); // Span all columns

        nodesListView = new ListView<>(activeNodes);
        nodesListView.setMinHeight(150);
        nodesListView.setMaxHeight(Double.MAX_VALUE);
        setupNodesListView();

        GridPane.setHgrow(nodesListView, Priority.ALWAYS);
        GridPane.setVgrow(nodesListView, Priority.ALWAYS);
        grid.add(nodesListView, 0, startRow + 2, GridPane.REMAINING, 4);  // Span 4 rows

        // Load nodes from the current output's node chain
        syncNodesFromOutput();
    }

    private void updateNodesLabel() {
        String filename = currentConfigFile != null ?
            " (" + new File(currentConfigFile).getName() + ")" : "";
        nodesLabel.setText("Node Chain Config [" + outputId + "]:" + filename);
    }

    private void setupNodesListView() {
        nodesListView.setCellFactory(lv -> new ListCell<OSCNode>() {
            @Override
            protected void updateItem(OSCNode node, boolean empty) {
                super.updateItem(node, empty);
                if (empty || node == null) {
                    setGraphic(null);
                } else {
                    HBox cell = new HBox(10);
                    cell.setAlignment(Pos.CENTER_LEFT);

                    // Create cell controls
                    setupCellControls(cell, node, getIndex());
                    setGraphic(cell);
                }
            }
        });
    }

    private void setupCellControls(HBox cell, OSCNode node, int index) {
        // Create controls
        CheckBox enabledCheck = new CheckBox();
        Button upBtn = new Button("↑");
        Button downBtn = new Button("↓");
        Button prefsBtn = new Button("P");
        Label nameLabel = new Label(node.label());
        HBox argsBox = new HBox(5);
        Button updateBtn = new Button("Update");

        // Cell implementation moved from OSCProxyApp
        enabledCheck.setSelected(true);

        // Add event handler for checkbox to enable/disable node
        enabledCheck.setOnAction(e -> {
            if (enabledCheck.isSelected()) {
                proxyService.registerNode(outputId, node);
                log("Enabled " + node.label() + " for output " + outputId);
            } else {
                proxyService.unregisterNode(outputId, node);
                log("Disabled " + node.label() + " for output " + outputId);
            }
            notifyNodeChainChanged();
        });

        nameLabel.setPrefWidth(150);

        // Args setup
        String[] currentArgs = node.getArgs();
        TextField[] argFields = new TextField[node.getNumArgs()];
        String[] argNames = node.getArgNames();
        for (int i = 0; i < node.getNumArgs(); i++) {
            TextField field = new TextField();
            field.setPrefWidth(100);
            field.setMaxWidth(100);
            if (i < currentArgs.length && currentArgs[i] != null) {
                field.setText(currentArgs[i]);
                field.setStyle("-fx-font-size: 11px;");
            }

            // Updated hover handlers to use argument names
            final int argIndex = i;
            field.setOnMouseEntered(e ->
                statusBar.setText(String.format("%s: %s = %s",
                    node.label(), argNames[argIndex], field.getText())));
            field.setOnMouseExited(e ->
                statusBar.setText(""));

            argFields[i] = field;
            argsBox.getChildren().add(field);
        }
        argsBox.setMaxWidth(450);

        // Existing buttons
        // Update button handler:
        updateBtn.setOnAction(e -> {
            String[] args = new String[node.getNumArgs()];
            for (int i = 0; i < args.length; i++) {
                args[i] = argFields[i].getText();
            }
            try {
                if (node.configure(args)) {
                    if (enabledCheck.isSelected()) {
                        // Re-register node to apply new configuration
                        proxyService.unregisterNode(outputId, node);
                        proxyService.registerNode(outputId, node);
                    }
                    log("Updated configuration for " + node.label() + " on output " + outputId);
                    notifyNodeChainChanged();
                } else {
                    showError("Configuration Error", "Failed to configure node");
                }
            } catch (Exception ex) {
                showError("Configuration Error", ex.getMessage());
                log("Error configuring " + node.label() + ": " + ex.getMessage());
            }
        });

        upBtn.setDisable(index == 0);
        downBtn.setDisable(index == activeNodes.size() - 1);

        prefsBtn.setOnAction(e -> node.showPreferences());

        upBtn.setOnAction(e -> {
            if (index > 0) {
                // Update list
                OSCNode current = activeNodes.remove(index);
                activeNodes.add(index - 1, current);

                // Update service
                proxyService.unregisterNode(outputId, current);
                proxyService.registerNode(outputId, current);

                nodesListView.getSelectionModel().select(index - 1);
                notifyNodeChainChanged();
            }
        });

        downBtn.setOnAction(e -> {
            if (index < activeNodes.size() - 1) {
                // Update list
                OSCNode current = activeNodes.remove(index);
                activeNodes.add(index + 1, current);

                // Update service
                proxyService.unregisterNode(outputId, current);
                proxyService.registerNode(outputId, current);

                nodesListView.getSelectionModel().select(index + 1);
                notifyNodeChainChanged();
            }
        });

        // Add all controls to cell in new order
        cell.getChildren().addAll(
            enabledCheck,
            upBtn,
            downBtn,
            prefsBtn,
            nameLabel,
            argsBox,
            updateBtn
        );
    }

    private void setupNodeButtons(HBox nodeButtons) {
        Button newButton = new Button("New");
        Button loadButton = new Button("Load");
        Button addNode = new Button("Add");
        Button removeNode = new Button("Remove");
        Button saveButton = new Button("Save");
        Button saveAsButton = new Button("Save As");

        // Make all buttons the same width but not too wide
        double buttonWidth = 80;
        newButton.setPrefWidth(buttonWidth);
        loadButton.setPrefWidth(buttonWidth);
        addNode.setPrefWidth(buttonWidth);
        removeNode.setPrefWidth(buttonWidth);
        saveButton.setPrefWidth(buttonWidth);
        saveAsButton.setPrefWidth(buttonWidth);

        // Set button actions
        newButton.setOnAction(e -> {
            activeNodes.clear();
            currentConfigFile = null;
            updateNodesLabel();
            log("Created new configuration");
        });

        loadButton.setOnAction(e -> {
            if (fileChooser == null) {
                fileChooser = new FileChooser();
                fileChooser.setTitle("Load Node Configuration");
                fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("JSON Files", "*.json")
                );
                // Set initial directory to project node chains directory or config directory
                if (projectManager != null && projectManager.hasOpenProject()) {
                    fileChooser.setInitialDirectory(projectManager.getNodeChainsDir().toFile());
                } else {
                    fileChooser.setInitialDirectory(DataDirectory.getConfigDirFile());
                }
            }

            File file = fileChooser.showOpenDialog(null);
            if (file != null) {
                loadConfig(file.getAbsolutePath());
                log("Loaded configuration from " + file.getName());
            }
        });

        addNode.setOnAction(e -> showAddNodeDialog());
        removeNode.setOnAction(e -> removeSelectedNode());
        saveButton.setOnAction(e -> {
            if (currentConfigFile == null) {
                saveAsNode();
            } else {
                saveConfig(currentConfigFile);
            }
        });
        saveAsButton.setOnAction(e -> saveAsNode());

        // Create left-aligned container for Add/Remove buttons
        HBox leftButtons = new HBox(10);
        leftButtons.setAlignment(Pos.CENTER_LEFT);
        leftButtons.getChildren().addAll(addNode, removeNode);

        // Create right-aligned container for file operation buttons
        HBox rightButtons = new HBox(10);
        rightButtons.setAlignment(Pos.CENTER_RIGHT);
        rightButtons.getChildren().addAll(newButton, loadButton, saveButton, saveAsButton);

        // Add spacer region between left and right button groups
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Configure main container and add all components
        nodeButtons.setAlignment(Pos.CENTER);
        nodeButtons.setSpacing(0);
        nodeButtons.getChildren().addAll(leftButtons, spacer, rightButtons);
    }

    // Methods moved from OSCProxyApp
    private void showAddNodeDialog() {
        ChoiceDialog<String> dialog = new ChoiceDialog<>();
        dialog.setTitle("Add Node");
        dialog.setHeaderText("Select node type:");
        dialog.getItems().addAll(Arrays.asList(NodeRegistry.getNodeLabels()));

        // Apply dark theme when the dialog is shown
        dialog.setOnShown(e -> Theme.applyDark(dialog.getDialogPane().getScene()));

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(nodeLabel -> {
            for (OSCNode node : NodeRegistry.getNodes()) {
                if (node.label().equals(nodeLabel)) {
                    try {
                        // Create new instance of same node type
                        OSCNode newNode = node.getClass().getDeclaredConstructor().newInstance();
                        activeNodes.add(newNode);
                        notifyNodeChainChanged();
                    } catch (Exception ex) {
                        showError("Error", "Could not create node: " + ex.getMessage());
                    }
                    break;
                }
            }
        });
    }

    private void removeSelectedNode() {
        OSCNode selected = nodesListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            proxyService.unregisterNode(outputId, selected);
            activeNodes.remove(selected);
            notifyNodeChainChanged();
        }
    }

    private void saveAsNode() {
        if (fileChooser == null) {
            fileChooser = new FileChooser();
            fileChooser.setTitle("Save Node Configuration");
            fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("JSON Files", "*.json")
            );
            // Set initial directory to project node chains directory or config directory
            if (projectManager != null && projectManager.hasOpenProject()) {
                fileChooser.setInitialDirectory(projectManager.getNodeChainsDir().toFile());
            } else {
                fileChooser.setInitialDirectory(DataDirectory.getConfigDirFile());
            }
        }

        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            currentConfigFile = file.getAbsolutePath();
            saveConfig(currentConfigFile);
            // Note: No longer saving to old config.json - configs are in app_config.json
            updateNodesLabel();
        }
    }

    private void saveConfig(String filename) {
        JsonArray nodesArray = new JsonArray();

        for (OSCNode node : activeNodes) {
            JsonObject nodeObj = new JsonObject();
            nodeObj.addProperty("type", node.getClass().getName());
            nodeObj.addProperty("enabled", true);

            // Save node arguments
            JsonArray argsArray = new JsonArray();
            String[] args = node.getArgs();
            for (String arg : args) {
                argsArray.add(arg);
            }
            nodeObj.add("args", argsArray);

            nodesArray.add(nodeObj);
        }

        try (FileWriter writer = new FileWriter(filename)) {
            new Gson().toJson(nodesArray, writer);
        } catch (IOException ex) {
            showError("Save Error", "Could not save configuration: " + ex.getMessage());
        }
    }

    /**
     * Load node configuration from an external file (for import).
     * This is separate from the automatic config loading which now happens via ApplicationConfig.
     */
    private void loadConfig(String filename) {
        try {
            log("Loading configuration from: " + filename);

            // Check if file exists
            File configFile = new File(filename);
            if (!configFile.exists()) {
                throw new Exception("File does not exist: " + filename);
            }

            log("File exists, attempting to parse JSON");
            JsonArray nodesArray = new Gson().fromJson(new FileReader(configFile), JsonArray.class);
            activeNodes.clear();

            log("Found " + nodesArray.size() + " nodes in config");

            for (JsonElement elem : nodesArray) {
                JsonObject nodeObj = elem.getAsJsonObject();
                String type = nodeObj.get("type").getAsString();
                boolean enabled = nodeObj.get("enabled").getAsBoolean();

                log("Loading node: " + type + " (enabled: " + enabled + ")");

                // Load node arguments
                JsonArray argsArray = nodeObj.getAsJsonArray("args");
                String[] args = new String[argsArray.size()];
                for (int i = 0; i < args.length; i++) {
                    args[i] = argsArray.get(i).getAsString();
                }

                Class<?> nodeClass = Class.forName(type);
                OSCNode node = (OSCNode)nodeClass.getDeclaredConstructor().newInstance();

                if (!node.configure(args)) {
                    throw new Exception("Failed to configure node with saved arguments");
                }

                activeNodes.add(node);
                log("Added node to activeNodes list: " + node.label());

                if (enabled) {
                    proxyService.registerNode(outputId, node);
                    log("Registered node with output " + outputId + ": " + node.label());
                }
            }

            currentConfigFile = filename;
            updateNodesLabel();
            log("Configuration loaded successfully. Active nodes count: " + activeNodes.size());
        } catch (Exception ex) {
            String errorMsg = "Could not load configuration from: " + filename + "\nError: " + ex.getMessage();
            log("Error loading configuration: " + errorMsg);
            ex.printStackTrace();
            showError("Load Error", errorMsg);
        }
    }

    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void log(String message) {
        logArea.appendText(message + "\n");
        logArea.setScrollTop(Double.MAX_VALUE);
    }
}
