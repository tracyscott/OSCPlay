package xyz.theforks.nodes;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import javax.script.Bindings;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import com.illposed.osc.OSCMessage;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import xyz.theforks.service.ProjectManager;

/**
 * OSC Node that executes JavaScript code to process OSC messages.
 * Scripts are loaded from files and can be hot-reloaded when modified.
 */
public class ScriptNode implements OSCNode {
    private String addressPattern;
    private String scriptPath;
    private ScriptEngine engine;
    private Invocable invocable;
    private FileTime lastModified;
    private String lastError;

    // Default test message
    private String testAddress = "/test/message";
    private ObservableList<TestArgument> testArguments = FXCollections.observableArrayList();

    // Project manager for accessing project scripts directory
    private static ProjectManager projectManager;

    /**
     * Set the project manager for all ScriptNode instances.
     * This should be called during application initialization.
     */
    public static void setProjectManager(ProjectManager pm) {
        projectManager = pm;
    }

    @Override
    public String getAddressPattern() {
        return addressPattern;
    }

    @Override
    public String label() {
        return "Script";
    }

    @Override
    public String getHelp() {
        return "Executes a JavaScript file to process OSC messages";
    }

    @Override
    public int getNumArgs() {
        return 2;
    }

    @Override
    public String[] getArgs() {
        return new String[] { addressPattern, scriptPath };
    }

    @Override
    public String[] getArgNames() {
        return new String[] { "Address Pattern", "Script Path" };
    }

    @Override
    public boolean configure(String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("ScriptNode requires two arguments: address pattern and script path");
        }

        addressPattern = args[0];
        scriptPath = args[1];

        // Initialize default test arguments if empty
        if (testArguments.isEmpty()) {
            testArguments.add(new TestArgument("Int", "1"));
            testArguments.add(new TestArgument("Float", "2.5"));
            testArguments.add(new TestArgument("String", "test"));
        }

        // Initialize the script engine
        return loadScript();
    }

    @Override
    public void process(java.util.List<xyz.theforks.model.MessageRequest> requests) {
        if (engine == null || invocable == null) {
            // Script failed to load - pass through unchanged
            return;
        }

        OSCMessage message = inputMessage(requests);
        if (message == null) return;

        // Check if script file has been modified and reload if necessary
        if (hasScriptChanged()) {
            loadScript();
        }

        try {
            // Call the process function in the JavaScript
            Object result = invocable.invokeFunction("process", message);

            // Handle different return types
            if (result == null || (result instanceof Boolean && !(Boolean) result)) {
                // Drop the message
                dropMessage(requests);
            } else if (result instanceof xyz.theforks.model.MessageRequest) {
                replaceMessage(requests, (xyz.theforks.model.MessageRequest) result);
            } else if (result instanceof OSCMessage) {
                // Backward compatible: wrap in immediate request
                replaceMessage(requests, (OSCMessage) result);
            } else if (result instanceof java.util.List) {
                // Handle array of messages or requests
                java.util.List<?> resultList = (java.util.List<?>) result;
                java.util.List<xyz.theforks.model.MessageRequest> newRequests = new java.util.ArrayList<>();

                for (Object item : resultList) {
                    if (item instanceof xyz.theforks.model.MessageRequest) {
                        newRequests.add((xyz.theforks.model.MessageRequest) item);
                    } else if (item instanceof OSCMessage) {
                        newRequests.add(new xyz.theforks.model.MessageRequest((OSCMessage) item));
                    }
                }

                replaceWithMultiple(requests, newRequests);
            }
            // else: unknown type - pass through unchanged (do nothing)

        } catch (ScriptException | NoSuchMethodException e) {
            // Log error and pass through original message
            if (lastError == null || !lastError.equals(e.getMessage())) {
                lastError = e.getMessage();
                System.err.println("ScriptNode error in " + scriptPath + ": " + e.getMessage());
            }
            // Pass through unchanged (do nothing)
        }
    }

    @Override
    public void showPreferences() {
        Stage stage = new Stage();
        stage.setTitle("Script Node Preferences");

        GridPane grid = new GridPane();
        grid.setPadding(new Insets(10));
        grid.setHgap(10);
        grid.setVgap(10);

        // Script path input
        Label pathLabel = new Label("Script Path:");
        TextField pathField = new TextField(scriptPath);
        pathField.setPrefWidth(400);
        grid.add(pathLabel, 0, 0);
        grid.add(pathField, 1, 0);

        // Script content editor
        Label contentLabel = new Label("Script Content:");
        TextArea contentArea = new TextArea();
        contentArea.setEditable(true);
        contentArea.setPrefRowCount(20);
        contentArea.setPrefWidth(600);
        GridPane.setHgrow(contentArea, Priority.ALWAYS);
        GridPane.setVgrow(contentArea, Priority.ALWAYS);
        grid.add(contentLabel, 0, 1);
        grid.add(contentArea, 0, 2, 2, 1);

        // Load script content
        loadScriptContent(contentArea);

        // Test message row
        Label testLabel = new Label("Test Message:");
        grid.add(testLabel, 0, 3);

        HBox testMessageRow = new HBox(10);
        testMessageRow.setAlignment(Pos.CENTER_LEFT);

        // Address field
        TextField testAddressField = new TextField(testAddress);
        testAddressField.setPrefWidth(200);
        testAddressField.setPromptText("OSC Address");
        testMessageRow.getChildren().add(testAddressField);

        // Arguments container
        HBox argsContainer = new HBox(5);
        argsContainer.setAlignment(Pos.CENTER_LEFT);
        updateArgumentsUI(argsContainer);
        testMessageRow.getChildren().add(argsContainer);

        grid.add(testMessageRow, 0, 4, 2, 1);

        // Status label
        Label statusLabel = new Label();
        statusLabel.setWrapText(true);
        grid.add(statusLabel, 0, 5, 2, 1);

        // Button row
        GridPane buttonGrid = new GridPane();
        buttonGrid.setHgap(10);

        // Reload button
        Button reloadButton = new Button("Reload from File");
        reloadButton.setOnAction(e -> {
            if (loadScript()) {
                loadScriptContent(contentArea);
                statusLabel.setText("Script reloaded successfully from file");
                statusLabel.setStyle("-fx-text-fill: green;");
            } else {
                statusLabel.setText("Error loading script: " + lastError);
                statusLabel.setStyle("-fx-text-fill: red;");
            }
        });
        buttonGrid.add(reloadButton, 0, 0);

        // Save Script button
        Button saveScriptButton = new Button("Save Script");
        saveScriptButton.setOnAction(e -> {
            try {
                Path path = resolveScriptPath();
                Files.writeString(path, contentArea.getText());
                if (loadScript()) {
                    statusLabel.setText("Script saved successfully to file");
                    statusLabel.setStyle("-fx-text-fill: green;");
                } else {
                    statusLabel.setText("Script saved but reload failed: " + lastError);
                    statusLabel.setStyle("-fx-text-fill: orange;");
                }
            } catch (IOException ex) {
                statusLabel.setText("Error saving script: " + ex.getMessage());
                statusLabel.setStyle("-fx-text-fill: red;");
            }
        });
        buttonGrid.add(saveScriptButton, 1, 0);

        // Test button
        Button testButton = new Button("Test");
        testButton.setOnAction(e -> {
            try {
                // Update test address from field
                testAddress = testAddressField.getText();

                // Build test message from UI fields
                java.util.List<Object> args = new java.util.ArrayList<>();
                for (TestArgument arg : testArguments) {
                    switch (arg.getType()) {
                        case "Int":
                            try {
                                args.add(Integer.parseInt(arg.getValue()));
                            } catch (NumberFormatException ex) {
                                args.add(0);
                            }
                            break;
                        case "Float":
                            try {
                                args.add(Float.parseFloat(arg.getValue()));
                            } catch (NumberFormatException ex) {
                                args.add(0.0f);
                            }
                            break;
                        case "Bool":
                            args.add(Boolean.parseBoolean(arg.getValue()));
                            break;
                        case "String":
                            args.add(arg.getValue());
                            break;
                        case "Infinitum":
                            args.add(null);
                            break;
                    }
                }

                OSCMessage testMsg = new OSCMessage(testAddress, args);
                java.util.List<xyz.theforks.model.MessageRequest> requests = new java.util.ArrayList<>();
                requests.add(new xyz.theforks.model.MessageRequest(testMsg));

                process(requests);

                if (requests.isEmpty()) {
                    statusLabel.setText("Test result: Message dropped (empty list)");
                } else {
                    StringBuilder sb = new StringBuilder("Test results:");
                    for (xyz.theforks.model.MessageRequest req : requests) {
                        sb.append("- ").append(req.getMessage().getAddress())
                          .append(" args=").append(req.getMessage().getArguments());
                        if (!req.isImmediate()) {
                            sb.append(" (delayed ").append(req.getDelayMs()).append("ms)");
                        }
                        sb.append("\n");
                    }
                    statusLabel.setText(sb.toString());
                }
                statusLabel.setStyle("-fx-text-fill: blue;");
            } catch (Exception ex) {
                statusLabel.setText("Test error: " + ex.getMessage());
                statusLabel.setStyle("-fx-text-fill: red;");
            }
        });
        buttonGrid.add(testButton, 2, 0);

        // Close button
        Button closeButton = new Button("Close");
        closeButton.setOnAction(e -> stage.close());
        buttonGrid.add(closeButton, 4, 0);

        grid.add(buttonGrid, 0, 6, 2, 1);

        Scene scene = new Scene(grid, 700, 500);
        xyz.theforks.ui.Theme.applyDark(scene);
        stage.setScene(scene);
        stage.show();
    }

    /**
     * Load script content into the text area for viewing
     */
    private void loadScriptContent(TextArea contentArea) {
        try {
            Path path = resolveScriptPath();
            String content = Files.readString(path);
            contentArea.setText(content);
        } catch (IOException e) {
            contentArea.setText("Error loading script: " + e.getMessage());
        }
    }

    /**
     * Resolve the script path relative to the project scripts directory
     */
    private Path resolveScriptPath() {
        Path path = Paths.get(scriptPath);

        // If path is absolute, use it directly
        if (path.isAbsolute()) {
            return path;
        }

        // Use project manager to get scripts directory if available
        if (projectManager != null && projectManager.hasOpenProject()) {
            return projectManager.getScriptsDir().resolve(scriptPath);
        }

        // Fallback to current directory if no project manager
        return Paths.get(scriptPath);
    }

    /**
     * Check if the script file has been modified since last load
     */
    private boolean hasScriptChanged() {
        if (lastModified == null) {
            return true;
        }

        try {
            Path path = resolveScriptPath();
            FileTime currentModified = Files.getLastModifiedTime(path);
            return currentModified.compareTo(lastModified) > 0;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Load or reload the script file
     */
    private boolean loadScript() {
        try {
            Path path = resolveScriptPath();

            if (!Files.exists(path)) {
                // Create boilerplate script if it doesn't exist
                if (!createBoilerplateScript(path)) {
                    lastError = "Script file not found and could not be created: " + path;
                    System.err.println("ScriptNode: " + lastError);
                    return false;
                }
                System.out.println("ScriptNode: Created boilerplate script at " + path);
            }

            // Create new engine instance
            ScriptEngineManager manager = new ScriptEngineManager();
            engine = manager.getEngineByName("graal.js");

            if (engine == null) {
                lastError = "GraalVM JavaScript engine not available";
                System.err.println("ScriptNode: " + lastError);
                return false;
            }

            // Enable polyglot access BEFORE any eval() calls
            Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
            bindings.put("polyglot.js.allowHostAccess", true);
            bindings.put("polyglot.js.allowHostClassLookup", (Predicate<String>) s -> true);

            // Set up the engine context - expose OSCMessage and MessageRequest classes
            engine.put("OSCMessage", OSCMessage.class);
            engine.put("MessageRequest", xyz.theforks.model.MessageRequest.class);

            // Add helper function for creating new messages
            engine.eval("function createMessage(address, args) { " +
                       "  var javaList = Java.type('java.util.Arrays').asList(args); " +
                       "  return new OSCMessage(address, javaList); " +
                       "}");

            // Add helper function for creating message requests with delay/routing
            engine.eval("function createMessageRequest(message, delay, outputId) { " +
                       "  delay = delay || 0; " +
                       "  outputId = outputId || null; " +
                       "  var MessageRequest = Java.type('xyz.theforks.model.MessageRequest'); " +
                       "  return new MessageRequest(message, delay, outputId); " +
                       "}");

            // Load and evaluate the script
            String scriptContent = Files.readString(path);
            engine.eval(scriptContent);

            // Get the invocable interface
            invocable = (Invocable) engine;

            // Update last modified time
            lastModified = Files.getLastModifiedTime(path);

            // Clear any previous errors
            lastError = null;

            System.out.println("ScriptNode: Loaded script from " + path);
            return true;

        } catch (ScriptException | IOException e) {
            lastError = e.getMessage();
            System.err.println("ScriptNode: Error loading script: " + lastError);
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Update the arguments UI container with current test arguments
     */
    private void updateArgumentsUI(HBox argsContainer) {
        argsContainer.getChildren().clear();

        for (int i = 0; i < testArguments.size(); i++) {
            final int index = i;
            TestArgument arg = testArguments.get(i);

            ComboBox<String> typeCombo = new ComboBox<>();
            typeCombo.getItems().addAll("Int", "Float", "Bool", "String", "Infinitum");
            typeCombo.setValue(arg.getType());
            typeCombo.setMinWidth(70);
            typeCombo.setMaxWidth(70);

            TextField valueField = new TextField(arg.getValue());
            valueField.setPromptText("Value");
            valueField.setMinWidth(60);
            valueField.setMaxWidth(60);

            // ComboBox for Boolean values
            ComboBox<String> boolCombo = new ComboBox<>();
            boolCombo.getItems().addAll("true", "false");
            boolCombo.setValue(arg.getValue().isEmpty() ? "true" : arg.getValue());
            boolCombo.setMinWidth(60);
            boolCombo.setMaxWidth(60);
            boolCombo.setOnAction(e -> arg.setValue(boolCombo.getValue()));

            Button removeBtn = new Button("-");
            removeBtn.setOnAction(e -> {
                testArguments.remove(index);
                updateArgumentsUI(argsContainer);
            });

            // Update value field/combobox visibility based on type
            typeCombo.setOnAction(e -> {
                arg.setType(typeCombo.getValue());
                String selectedType = typeCombo.getValue();

                valueField.setVisible(false);
                valueField.setManaged(false);
                boolCombo.setVisible(false);
                boolCombo.setManaged(false);

                if ("Bool".equals(selectedType)) {
                    boolCombo.setVisible(true);
                    boolCombo.setManaged(true);
                    if (!arg.getValue().equals("true") && !arg.getValue().equals("false")) {
                        arg.setValue("true");
                        boolCombo.setValue("true");
                    }
                } else if (!"Infinitum".equals(selectedType)) {
                    valueField.setVisible(true);
                    valueField.setManaged(true);
                }
            });

            // Listen to value changes
            valueField.textProperty().addListener((obs, oldVal, newVal) -> arg.setValue(newVal));

            // Set initial visibility
            valueField.setVisible(false);
            valueField.setManaged(false);
            boolCombo.setVisible(false);
            boolCombo.setManaged(false);

            if ("Bool".equals(arg.getType())) {
                boolCombo.setVisible(true);
                boolCombo.setManaged(true);
            } else if (!"Infinitum".equals(arg.getType())) {
                valueField.setVisible(true);
                valueField.setManaged(true);
            }

            argsContainer.getChildren().addAll(typeCombo, valueField, boolCombo, removeBtn);
        }

        // Add + button
        Button addBtn = new Button("+");
        addBtn.setOnAction(e -> {
            testArguments.add(new TestArgument("Float", "0.0"));
            updateArgumentsUI(argsContainer);
        });
        argsContainer.getChildren().add(addBtn);
    }

    /**
     * Create a boilerplate script file with basic structure.
     * @param path The path where the script should be created
     * @return true if script was created successfully, false otherwise
     */
    private boolean createBoilerplateScript(Path path) {
        try {
            // Ensure parent directory exists
            Path parentDir = path.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            // Create boilerplate script content
            String boilerplate =
                "/**\n" +
                " * OSCPlay Script Node\n" +
                " *\n" +
                " * This script processes OSC messages. Modify the process() function\n" +
                " * to transform, filter, or pass through messages.\n" +
                " *\n" +
                " * Available API:\n" +
                " * - message.getAddress()     - Get OSC address string\n" +
                " * - message.getArguments()   - Get arguments list\n" +
                " * - createMessage(addr, args) - Create new OSC message\n" +
                " *\n" +
                " * Return values:\n" +
                " * - OSCMessage: Send the message\n" +
                " * - null or false: Drop the message\n" +
                " */\n\n" +
                "function process(message) {\n" +
                "    // Pass through all messages unchanged\n" +
                "    return message;\n" +
                "}\n";

            Files.writeString(path, boilerplate);
            return true;

        } catch (IOException e) {
            System.err.println("ScriptNode: Failed to create boilerplate script: " + e.getMessage());
            return false;
        }
    }

    /**
     * Simple class to hold test argument type and value
     */
    private static class TestArgument {
        private String type;
        private String value;

        public TestArgument(String type, String value) {
            this.type = type;
            this.value = value;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}
