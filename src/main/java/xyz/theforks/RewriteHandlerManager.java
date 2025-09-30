package xyz.theforks;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
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
import xyz.theforks.rewrite.RewriteHandler;
import xyz.theforks.rewrite.RewriteRegistry;
import xyz.theforks.service.OSCProxyService;
import xyz.theforks.ui.Theme;
import xyz.theforks.util.DataDirectory;

public class RewriteHandlerManager {
    private final OSCProxyService proxyService;
    private final TextArea logArea;
    private final Label statusBar;
    private Playback playback; // Optional playback instance for handler synchronization
    private ListView<RewriteHandler> handlersListView;
    private ObservableList<RewriteHandler> activeHandlers = FXCollections.observableArrayList();
    private String currentConfigFile = null;
    private FileChooser fileChooser;
    private Label handlersLabel;  // Add this field

    public RewriteHandlerManager(OSCProxyService proxyService, TextArea logArea, Label statusBar) {
        this.proxyService = proxyService;
        this.logArea = logArea;
        this.statusBar = statusBar;
    }
    
    /**
     * Set the playback instance for handler synchronization.
     * When set, rewrite handlers will be synchronized between proxy and playback.
     * @param playback The playback instance
     */
    public void setPlayback(Playback playback) {
        this.playback = playback;
        // Sync existing handlers to playback if any
        if (playback != null && !activeHandlers.isEmpty()) {
            playback.setRewriteHandlers(new java.util.ArrayList<>(activeHandlers));
        }
    }

    public void createUI(GridPane grid, int startRow) {
        // Rewrite handlers section
        handlersLabel = new Label("Rewrite Handler Config:");
        grid.add(handlersLabel, 0, startRow, GridPane.REMAINING, 1);  // Modified to span all columns

        // Handler management buttons on their own row below the list
        HBox handlerButtons = new HBox(10);
        setupHandlerButtons(handlerButtons);
        GridPane.setHgrow(handlerButtons, Priority.ALWAYS);
        grid.add(handlerButtons, 0, startRow + 1, GridPane.REMAINING, 1); // Span all columns

        handlersListView = new ListView<>(activeHandlers);
        handlersListView.setMinHeight(150);
        handlersListView.setMaxHeight(Double.MAX_VALUE);
        setupHandlersListView();

        GridPane.setHgrow(handlersListView, Priority.ALWAYS);
        GridPane.setVgrow(handlersListView, Priority.ALWAYS);
        grid.add(handlersListView, 0, startRow + 2, GridPane.REMAINING, 4);  // Span 4 rows

        loadLastConfig();
    }

    private void updateHandlersLabel() {
        String filename = currentConfigFile != null ? 
            " (" + new File(currentConfigFile).getName() + ")" : "";
        handlersLabel.setText("Rewrite Handler Config:" + filename);
    }

    private void setupHandlersListView() {
        handlersListView.setCellFactory(lv -> new ListCell<RewriteHandler>() {
            @Override
            protected void updateItem(RewriteHandler handler, boolean empty) {
                super.updateItem(handler, empty);
                if (empty || handler == null) {
                    setGraphic(null);
                } else {
                    HBox cell = new HBox(10);
                    cell.setAlignment(Pos.CENTER_LEFT);
                    
                    // Create cell controls
                    setupCellControls(cell, handler, getIndex());
                    setGraphic(cell);
                }
            }
        });
    }

    private void setupCellControls(HBox cell, RewriteHandler handler, int index) {
        // Create controls
        CheckBox enabledCheck = new CheckBox();
        Button upBtn = new Button("↑");
        Button downBtn = new Button("↓");
        Button prefsBtn = new Button("P");
        Label nameLabel = new Label(handler.label());
        HBox argsBox = new HBox(5);
        Button updateBtn = new Button("Update");

        // Cell implementation moved from OSCProxyApp
        enabledCheck.setSelected(true);

        // Add event handler for checkbox to enable/disable handler
        enabledCheck.setOnAction(e -> {
            if (enabledCheck.isSelected()) {
                proxyService.registerRewriteHandler(handler);
                log("Enabled " + handler.label());
            } else {
                proxyService.unregisterRewriteHandler(handler);
                log("Disabled " + handler.label());
            }
        });

        nameLabel.setPrefWidth(150);

        // Args setup
        String[] currentArgs = handler.getArgs();
        TextField[] argFields = new TextField[handler.getNumArgs()];
        String[] argNames = handler.getArgNames();
        for (int i = 0; i < handler.getNumArgs(); i++) {
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
                    handler.label(), argNames[argIndex], field.getText())));
            field.setOnMouseExited(e ->
                statusBar.setText(""));

            argFields[i] = field;
            argsBox.getChildren().add(field);
        }
        argsBox.setMaxWidth(450);

        // Existing buttons
        // Update button handler:
        updateBtn.setOnAction(e -> {
            String[] args = new String[handler.getNumArgs()];
            for (int i = 0; i < args.length; i++) {
                args[i] = argFields[i].getText();
            }
            try {
                if (handler.configure(args)) {
                    if (enabledCheck.isSelected()) {
                        // Re-register handler to apply new configuration
                        proxyService.unregisterRewriteHandler(handler);
                        proxyService.registerRewriteHandler(handler);
                    }
                    log("Updated configuration for " + handler.label());
                } else {
                    showError("Configuration Error", "Failed to configure handler");
                }
            } catch (Exception ex) {
                showError("Configuration Error", ex.getMessage());
                log("Error configuring " + handler.label() + ": " + ex.getMessage());
            }
        });

        upBtn.setDisable(index == 0);
        downBtn.setDisable(index == activeHandlers.size() - 1);

        prefsBtn.setOnAction(e -> handler.showPreferences());

        upBtn.setOnAction(e -> {
            if (index > 0) {
                // Update list
                RewriteHandler current = activeHandlers.remove(index);
                activeHandlers.add(index - 1, current);

                // Update service
                proxyService.unregisterRewriteHandler(current);
                proxyService.registerRewriteHandler(current);

                handlersListView.getSelectionModel().select(index - 1);
            }
        });

        downBtn.setOnAction(e -> {
            if (index < activeHandlers.size() - 1) {
                // Update list
                RewriteHandler current = activeHandlers.remove(index);
                activeHandlers.add(index + 1, current);

                // Update service
                proxyService.unregisterRewriteHandler(current);
                proxyService.registerRewriteHandler(current);

                handlersListView.getSelectionModel().select(index + 1);
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

    private void setupHandlerButtons(HBox handlerButtons) {
        Button newButton = new Button("New");
        Button loadButton = new Button("Load");
        Button addHandler = new Button("Add");
        Button removeHandler = new Button("Remove");
        Button saveButton = new Button("Save");
        Button saveAsButton = new Button("Save As");

        // Make all buttons the same width but not too wide
        double buttonWidth = 80;
        newButton.setPrefWidth(buttonWidth);
        loadButton.setPrefWidth(buttonWidth);
        addHandler.setPrefWidth(buttonWidth);
        removeHandler.setPrefWidth(buttonWidth);
        saveButton.setPrefWidth(buttonWidth);
        saveAsButton.setPrefWidth(buttonWidth);

        // Set button actions
        newButton.setOnAction(e -> {
            activeHandlers.clear();
            currentConfigFile = null;
            updateHandlersLabel();
            log("Created new configuration");
        });

        loadButton.setOnAction(e -> {
            if (fileChooser == null) {
                fileChooser = new FileChooser();
                fileChooser.setTitle("Load Handler Configuration");
                fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("JSON Files", "*.json")
                );
                // Set initial directory to config directory
                fileChooser.setInitialDirectory(DataDirectory.getConfigDirFile());
            }

            File file = fileChooser.showOpenDialog(null);
            if (file != null) {
                loadConfig(file.getAbsolutePath());
                log("Loaded configuration from " + file.getName());
            }
        });

        addHandler.setOnAction(e -> showAddHandlerDialog());
        removeHandler.setOnAction(e -> removeSelectedHandler());
        saveButton.setOnAction(e -> {
            if (currentConfigFile == null) {
                saveAsHandler();
            } else {
                saveConfig(currentConfigFile);
            }
        });
        saveAsButton.setOnAction(e -> saveAsHandler());

        // Create left-aligned container for Add/Remove buttons
        HBox leftButtons = new HBox(10);
        leftButtons.setAlignment(Pos.CENTER_LEFT);
        leftButtons.getChildren().addAll(addHandler, removeHandler);
        
        // Create right-aligned container for file operation buttons  
        HBox rightButtons = new HBox(10);
        rightButtons.setAlignment(Pos.CENTER_RIGHT);
        rightButtons.getChildren().addAll(newButton, loadButton, saveButton, saveAsButton);
        
        // Add spacer region between left and right button groups
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        // Configure main container and add all components
        handlerButtons.setAlignment(Pos.CENTER);
        handlerButtons.setSpacing(0);
        handlerButtons.getChildren().addAll(leftButtons, spacer, rightButtons);
    }

    // Methods moved from OSCProxyApp
    private void showAddHandlerDialog() {
        ChoiceDialog<String> dialog = new ChoiceDialog<>();
        dialog.setTitle("Add Rewrite Handler");
        dialog.setHeaderText("Select handler type:");
        dialog.getItems().addAll(Arrays.asList(RewriteRegistry.getHandlerLabels()));

        // Apply dark theme when the dialog is shown
        dialog.setOnShown(e -> Theme.applyDark(dialog.getDialogPane().getScene()));

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(handlerLabel -> {
            for (RewriteHandler handler : RewriteRegistry.getHandlers()) {
                if (handler.label().equals(handlerLabel)) {
                    try {
                        // Create new instance of same handler type
                        RewriteHandler newHandler = handler.getClass().getDeclaredConstructor().newInstance();
                        activeHandlers.add(newHandler);
                    } catch (Exception ex) {
                        showError("Error", "Could not create handler: " + ex.getMessage());
                    }
                    break;
                }
            }
        });
    }

    private void removeSelectedHandler() {
        RewriteHandler selected = handlersListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            proxyService.unregisterRewriteHandler(selected);
            activeHandlers.remove(selected);
        }
    }

    private void saveAsHandler() {
        if (fileChooser == null) {
            fileChooser = new FileChooser();
            fileChooser.setTitle("Save Handler Configuration");
            fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("JSON Files", "*.json")
            );
            // Set initial directory to config directory
            fileChooser.setInitialDirectory(DataDirectory.getConfigDirFile());
        }

        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            currentConfigFile = file.getAbsolutePath();
            saveConfig(currentConfigFile);
            saveLastConfig(currentConfigFile);
            updateHandlersLabel();
        }
    }

    private void saveConfig(String filename) {
        JsonArray handlersArray = new JsonArray();
        
        for (RewriteHandler handler : activeHandlers) {
            JsonObject handlerObj = new JsonObject();
            handlerObj.addProperty("type", handler.getClass().getName());
            handlerObj.addProperty("enabled", true);
            
            // Save handler arguments
            JsonArray argsArray = new JsonArray();
            String[] args = handler.getArgs();
            for (String arg : args) {
                argsArray.add(arg);
            }
            handlerObj.add("args", argsArray);
            
            handlersArray.add(handlerObj);
        }
        
        try (FileWriter writer = new FileWriter(filename)) {
            new Gson().toJson(handlersArray, writer);
        } catch (IOException ex) {
            showError("Save Error", "Could not save configuration: " + ex.getMessage());
        }
    }

    private void saveLastConfig(String filename) {
        JsonObject config = new JsonObject();
        config.addProperty("lastConfig", filename);

        try (FileWriter writer = new FileWriter(DataDirectory.getConfigFile("config.json").toFile())) {
            new Gson().toJson(config, writer);
        } catch (IOException ex) {
            System.err.println("Could not save config.json: " + ex.getMessage());
        }
    }

    private void loadLastConfig() {
        try {
            File configFile = DataDirectory.getConfigFile("config.json").toFile();
            if (configFile.exists()) {
                log("Found config.json, attempting to load last configuration");
                JsonObject config = new Gson().fromJson(new FileReader(configFile), JsonObject.class);
                String lastConfig = config.get("lastConfig").getAsString();
                if (lastConfig != null && !lastConfig.isEmpty()) {
                    log("Last config file: " + lastConfig);
                    loadConfig(lastConfig);
                } else {
                    log("No last config file specified in config.json");
                }
            } else {
                log("No config.json found, skipping auto-load");
            }
        } catch (Exception ex) {
            log("Could not load last config: " + ex.getMessage());
            System.err.println("Could not load config.json: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void loadConfig(String filename) {
        try {
            log("Loading configuration from: " + filename);

            // Check if file exists
            File configFile = new File(filename);
            if (!configFile.exists()) {
                throw new Exception("File does not exist: " + filename);
            }

            log("File exists, attempting to parse JSON");
            JsonArray handlersArray = new Gson().fromJson(new FileReader(configFile), JsonArray.class);
            activeHandlers.clear();

            log("Found " + handlersArray.size() + " handlers in config");

            for (JsonElement elem : handlersArray) {
                JsonObject handlerObj = elem.getAsJsonObject();
                String type = handlerObj.get("type").getAsString();
                boolean enabled = handlerObj.get("enabled").getAsBoolean();

                log("Loading handler: " + type + " (enabled: " + enabled + ")");

                // Load handler arguments
                JsonArray argsArray = handlerObj.getAsJsonArray("args");
                String[] args = new String[argsArray.size()];
                for (int i = 0; i < args.length; i++) {
                    args[i] = argsArray.get(i).getAsString();
                }

                Class<?> handlerClass = Class.forName(type);
                RewriteHandler handler = (RewriteHandler)handlerClass.getDeclaredConstructor().newInstance();

                if (!handler.configure(args)) {
                    throw new Exception("Failed to configure handler with saved arguments");
                }

                activeHandlers.add(handler);
                log("Added handler to activeHandlers list: " + handler.label());

                if (enabled) {
                    proxyService.registerRewriteHandler(handler);
                    log("Registered handler with proxy service: " + handler.label());
                }
            }

            currentConfigFile = filename;
            updateHandlersLabel();
            log("Configuration loaded successfully. Active handlers count: " + activeHandlers.size());
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