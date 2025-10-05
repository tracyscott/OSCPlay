package xyz.theforks;

import java.io.File;
import java.io.IOException;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TitledPane;
import javafx.scene.image.Image;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;
import xyz.theforks.model.ApplicationConfig;
import xyz.theforks.model.NodeChainConfig;
import xyz.theforks.model.OutputConfig;
import xyz.theforks.model.PlaybackMode;
import xyz.theforks.model.ProjectConfig;
import xyz.theforks.model.RecordingSession;
import xyz.theforks.nodes.OSCNode;
import xyz.theforks.nodes.ScriptNode;
import xyz.theforks.service.OSCInputService;
import xyz.theforks.service.OSCOutputService;
import xyz.theforks.service.OSCProxyService;
import xyz.theforks.service.ProjectManager;
import xyz.theforks.ui.ProjectSplashScreen;
import xyz.theforks.ui.RecordingEditorUI;
import xyz.theforks.ui.SamplerPadUI;
import xyz.theforks.ui.Theme;
import xyz.theforks.ui.MonitorWindow;
import xyz.theforks.util.DataDirectory;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

public class OSCProxyApp extends Application {

    private OSCProxyService proxyService;
    private static String appVersion = "unknown";

    // UI Components
    private TextField inHostField;
    private TextField inPortField;
    private Label inMessageCountLabel;
    private TextField outHostField;
    private TextField outPortField;
    private Button manageButton;
    private Button recordButton;
    private Label messageCountLabel;
    private ComboBox<String> sessionComboBox;
    private ComboBox<String> playbackOutputComboBox;
    private Button playButton;
    private Button stopPlaybackButton;
    private ProgressBar playbackProgress;
    private Label playbackStatusLabel;
    private Button selectAudioButton;
    private Label audioFileLabel;
    private TextArea logArea;

    private Label statusBar;
    private Playback playback;

    // Multi-output controls
    private ComboBox<String> outputComboBox;
    private Button manageOutputsButton;
    private CheckBox enableOutputCheckBox;
    private Button monitorButton;
    private MonitorWindow currentMonitorWindow;
    private String selectedOutputId = "default";
    
    
    // Application configuration
    private ApplicationConfig appConfig;
    private final ObjectMapper configMapper = new ObjectMapper();

    private boolean isRecording = false;

    // CLI mode fields
    private static String sessionToPlay = null;
    private static String outHost = "127.0.0.1";
    private static int outPort = 3030;
    private static boolean cliMode = false;

    // Add fields
    private NodeChainManager nodeChainManager;
    private ProjectManager projectManager;
    private Stage primaryStage;
    private SamplerPadUI samplerPadUI;
    private RecordingEditorUI recordingEditorUI;

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
        this.primaryStage = primaryStage;

        // Load application version
        loadAppVersion();

        // Show splash screen for project selection
        ProjectSplashScreen splashScreen = new ProjectSplashScreen();
        String selectedProjectName = splashScreen.showAndWait();

        // Exit if user cancelled
        if (selectedProjectName == null) {
            Platform.exit();
            return;
        }

        // Initialize project manager and load selected project
        projectManager = new ProjectManager();
        try {
            // Load the selected project
            Path projectDir = ProjectManager.getProjectsDir().resolve(selectedProjectName);
            File[] oppFiles = projectDir.toFile().listFiles((dir, name) -> name.endsWith(".opp"));
            if (oppFiles != null && oppFiles.length > 0) {
                projectManager.openProject(oppFiles[0]);
            } else {
                throw new IOException("Project file not found for: " + selectedProjectName);
            }

            // Set the recordings directory for RecordingSession
            if (projectManager.hasOpenProject()) {
                RecordingSession.setRecordingsDirectory(projectManager.getRecordingsDir());
            }

            // Set the project manager for ScriptNode instances
            ScriptNode.setProjectManager(projectManager);
        } catch (IOException e) {
            showError("Error loading project", e.getMessage());
            Platform.exit();
            return;
        }

        // Load application configuration
        loadApplicationConfig();

        // Currently we can only have one playback operation at a time so we have a single instance
        // here.  The various playback/stop playback buttons will control this instance.
        playback = new Playback();
        proxyService = new OSCProxyService(projectManager);

        // Note: Playback always rewrites messages through all enabled output node chains

        // Initialize outputs from project configuration (UI will be updated later)
        initializeOutputsFromProject();

        // Create menu bar
        MenuBar menuBar = createMenuBar(primaryStage);

        // Create UI components
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(4));
        grid.setHgap(10);
        grid.setVgap(2);

        // PROXY SECTION (encapsulates everything before Record/Playback)
        GridPane proxyGrid = new GridPane();
        proxyGrid.setHgap(10);
        proxyGrid.setVgap(2);
        proxyGrid.setPadding(new Insets(4));

        // Input configuration
        Label inLabel = new Label("In");
        inLabel.setMinWidth(20);
        proxyGrid.add(inLabel, 0, 0);

        // Load input host and port from project config
        ProjectConfig project = projectManager.getCurrentProject();
        String initialInHost = project != null ? project.getInHost() : "127.0.0.1";
        int initialInPort = project != null ? project.getInPort() : 8000;

        inHostField = new TextField(initialInHost);
        inHostField.setMinWidth(200);  // Doubled from 250
        inHostField.setStyle("-fx-font-size: 11px;");
        proxyGrid.add(inHostField, 1, 0);
        inPortField = new TextField(String.valueOf(initialInPort));
        inPortField.setMaxWidth(200);  // Doubled from 100
        inPortField.setStyle("-fx-font-size: 11px;");
        proxyGrid.add(inPortField, 2, 0);
        inMessageCountLabel = new Label("0");
        inMessageCountLabel.setStyle("-fx-text-fill: #888888; -fx-font-size: 11px;");
        inMessageCountLabel.setMinWidth(40);
        proxyGrid.add(inMessageCountLabel, 3, 0);

        // Output selection and management (row 1)
        HBox outputSelectionBox = new HBox(10);
        outputComboBox = new ComboBox<>();
        outputComboBox.setPromptText("Select Output");
        outputComboBox.setMinWidth(200);
        outputComboBox.setStyle("-fx-background-color: #121212; -fx-text-fill: white; -fx-border-color: #333333;");

        // Customize the cell factory to use black background for selected items
        outputComboBox.setCellFactory(lv -> new javafx.scene.control.ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setStyle("-fx-background-color: #121212; -fx-text-fill: white;");
                }
            }
        });
        manageOutputsButton = new Button("Manage Outputs");
        monitorButton = new Button("Monitor");
        outputSelectionBox.getChildren().addAll(
            outputComboBox,
            manageOutputsButton,
            monitorButton
        );
        proxyGrid.add(outputSelectionBox, 0, 1, GridPane.REMAINING, 1);
        GridPane.setValignment(outputSelectionBox, javafx.geometry.VPos.CENTER);

        // Output configuration (row 2)
        Label outLabel = new Label("Out");
        outLabel.setMinWidth(20);
        proxyGrid.add(outLabel, 0, 2);
        outHostField = new TextField(outHost);
        outHostField.setMinWidth(200);  // Doubled from 250
        outHostField.setStyle("-fx-font-size: 11px;");
        proxyGrid.add(outHostField, 1, 2);
        outPortField = new TextField("" + outPort);
        outPortField.setMaxWidth(200);  // Doubled from 100
        outPortField.setStyle("-fx-font-size: 11px;");
        proxyGrid.add(outPortField, 2, 2);
        enableOutputCheckBox = new CheckBox("Enabled");
        enableOutputCheckBox.setSelected(true);
        proxyGrid.add(enableOutputCheckBox, 3, 2);

        // Create the logArea so we can pass it to NodeChainManager
        logArea = new TextArea();
        // Create the status bar so we can pass it to NodeChainManager
        statusBar = new Label();


        // Node chain section (below output config, row 3)
        nodeChainManager = new NodeChainManager(proxyService, logArea, statusBar);
        nodeChainManager.setProjectManager(projectManager);
        nodeChainManager.createUI(proxyGrid, 3);

        // Connect the playback instance to the node chain manager for synchronization
        nodeChainManager.setPlayback(playback);

        // Set callback to save config when node chain changes
        nodeChainManager.setOnNodeChainChanged(this::saveOutputsToConfig);

        // Configure column constraints to make the middle column expand
        ColumnConstraints col0 = new ColumnConstraints(); // Label column
        ColumnConstraints col1 = new ColumnConstraints(); // Host field column (expandable)
        ColumnConstraints col2 = new ColumnConstraints(); // Port field column
        ColumnConstraints col3 = new ColumnConstraints(); // Checkbox column

        col1.setHgrow(Priority.ALWAYS);

        proxyGrid.getColumnConstraints().addAll(col0, col1, col2, col3);

        TitledPane proxyPane = new TitledPane("Proxy", proxyGrid);
        proxyPane.setCollapsible(true);
        grid.add(proxyPane, 0, 0, GridPane.REMAINING, 1);

        // Create TabPane for Record, Playback, Sampler, and Edit
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Recording tab - Recording controls (always records raw input)
        VBox recordingSection = new VBox(10);
        recordingSection.setPadding(new Insets(10));
        HBox recordingControls = new HBox(10);
        recordButton = new Button("Start Recording");
        messageCountLabel = new Label("Messages: 0");
        recordingControls.getChildren().addAll(recordButton, messageCountLabel);
        recordingSection.getChildren().add(recordingControls);

        Tab recordTab = new Tab("Record", recordingSection);

        // Playback tab - Playback controls
        VBox playbackSection = new VBox(10);
        playbackSection.setPadding(new Insets(10));

        // Output routing selector
        HBox playbackRoutingBox = new HBox(10);
        playbackRoutingBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label playbackRoutingLabel = new Label("Route to Output:");
        playbackRoutingLabel.setStyle("-fx-text-fill: white;");
        playbackOutputComboBox = new ComboBox<>();
        playbackOutputComboBox.getItems().add("Proxy");
        playbackOutputComboBox.setValue("Proxy");
        playbackOutputComboBox.setMinWidth(200);
        playbackRoutingBox.getChildren().addAll(playbackRoutingLabel, playbackOutputComboBox);

        // Session selection
        HBox sessionControls = new HBox(10);
        sessionComboBox = new ComboBox<>();
        sessionComboBox.setMaxWidth(200);
        playButton = new Button("Play");
        stopPlaybackButton = new Button("Stop");
        stopPlaybackButton.setDisable(true);
        manageButton = new Button("Manage");

        sessionControls.getChildren().addAll(
                new Label("Recordings:"),
                sessionComboBox,
                playButton,
                stopPlaybackButton
        );

        // Progress bar
        playbackProgress = new ProgressBar(0);
        playbackProgress.setMaxWidth(Double.MAX_VALUE);
        playbackStatusLabel = new Label("Ready");

        // Audio controls
        HBox audioControls = new HBox(10);
        selectAudioButton = new Button("Select Audio File");
        audioFileLabel = new Label("No Audio");
        audioControls.getChildren().addAll(selectAudioButton, audioFileLabel);

        playbackSection.getChildren().addAll(
                playbackRoutingBox,
                sessionControls,
                playbackProgress,
                playbackStatusLabel,
                audioControls
        );

        Tab playbackTab = new Tab("Playback", playbackSection);

        // Create Sampler tab content
        samplerPadUI = new SamplerPadUI(proxyService, playback, logArea, projectManager);
        // Connect sampler pad UI to proxy service for OSC command handling
        proxyService.setSamplerPadUI(samplerPadUI);
        Tab samplerTab = new Tab("Sampler", samplerPadUI);

        // Create Edit tab content
        recordingEditorUI = new RecordingEditorUI(proxyService, logArea, this::updateSessionsList);
        Tab editTab = new Tab("Edit", recordingEditorUI);

        // Add tabs to TabPane in order: Record, Playback, Sampler, Edit
        tabPane.getTabs().addAll(recordTab, playbackTab, samplerTab, editTab);

        grid.add(tabPane, 0, 1, GridPane.REMAINING, 1);

        // Log area
        logArea.setEditable(false);
        logArea.setPrefRowCount(6);
        logArea.setWrapText(true);
        logArea.setMaxWidth(Double.MAX_VALUE); // Make it fill width
        logArea.setMaxHeight(Double.MAX_VALUE); // Allow vertical expansion
        GridPane.setHgrow(logArea, Priority.ALWAYS); // Allow horizontal growth
        GridPane.setVgrow(logArea, Priority.ALWAYS); // Allow vertical growth
        grid.add(logArea, 0, 2, GridPane.REMAINING, 1); // Span all columns

        // Status bar
        statusBar.setMaxWidth(Double.MAX_VALUE);
        statusBar.setPadding(new Insets(4));
        statusBar.getStyleClass().add("status-bar");
        grid.add(statusBar, 0, 3, GridPane.REMAINING, 1);

        primaryStage.getIcons().addAll(
            new Image(getClass().getResourceAsStream("/icons/oscplay-16x16.png")),
            new Image(getClass().getResourceAsStream("/icons/oscplay-32x32.png")),
            new Image(getClass().getResourceAsStream("/icons/oscplay-48x48.png")),
            new Image(getClass().getResourceAsStream("/icons/oscplay-64x64.png")),
            new Image(getClass().getResourceAsStream("/icons/oscplay-128x128.png")),
            new Image(getClass().getResourceAsStream("/icons/oscplay-256x256.png"))
        );

        primaryStage.setResizable(true);
        primaryStage.setMinWidth(500);
        primaryStage.setWidth(1024);
        // Set up event handlers
        setupEventHandlers();

        // Bind properties
        proxyService.messageCountProperty().addListener((obs, oldVal, newVal)
                -> messageCountLabel.setText("Messages: " + newVal.intValue()));

        // Bind total message count to the input message count label
        proxyService.totalMessageCountProperty().addListener((obs, oldVal, newVal)
                -> inMessageCountLabel.setText(String.valueOf(newVal.intValue())));

        
        playback.playbackProgressProperty().addListener((obs, oldVal, newVal) -> {
            playbackProgress.setProgress(newVal.doubleValue());
            updatePlaybackStatus(newVal.doubleValue());
        });

        playback.isPlayingProperty().addListener((obs, oldVal, newVal) -> {
            playButton.setDisable(newVal);
            stopPlaybackButton.setDisable(!newVal);
            sessionComboBox.setDisable(newVal);
        });
         
        // Session selection listener: update audio label or reset when unbound
        sessionComboBox.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldVal, newVal) -> {
                if (newVal != null) {
                    String audioFile = playback.getAssociatedAudioFile(newVal);
                    audioFileLabel.setText(audioFile != null ? audioFile : "No Audio");
                } else {
                    audioFileLabel.setText("No Audio");
                }
            }
        );
        // Make grid expand horizontally
        GridPane.setHgrow(grid, Priority.ALWAYS);

        // Make input/output fields expand
        GridPane.setHgrow(inHostField, Priority.ALWAYS);
        GridPane.setHgrow(outHostField, Priority.ALWAYS);
        
        // Make recording controls expand
        HBox.setHgrow(messageCountLabel, Priority.ALWAYS);

        // Make session controls expand
        HBox.setHgrow(sessionComboBox, Priority.ALWAYS);

        // Make audio controls expand
        HBox.setHgrow(audioFileLabel, Priority.ALWAYS);

        // Configure main grid column constraints to make it expand to window width
        ColumnConstraints mainCol = new ColumnConstraints();
        mainCol.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().add(mainCol);

        // Create scene
        // Apply loaded configuration to UI controls
        applyConfigurationToUI();

        // Create root layout with menu bar and main content
        VBox root = new VBox();
        root.getChildren().addAll(menuBar, grid);
        VBox.setVgrow(grid, Priority.ALWAYS);

        Scene scene = new Scene(root);
        scene.setFill(Color.web("#121212"));
        Theme.applyDark(scene);
        updateWindowTitle(primaryStage);
        primaryStage.setScene(scene);
        primaryStage.show();

        // Update sessions list
        updateSessionsList();

        // Initialize outputs list and select default
        updateOutputsList();
        outputComboBox.getSelectionModel().select("default");
        updateOutputFields();

        // Start proxy automatically
        try {
            proxyService.setInHost(inHostField.getText());
            proxyService.setInPort(Integer.parseInt(inPortField.getText()));
            proxyService.setOutHost(outHostField.getText());
            proxyService.setOutPort(Integer.parseInt(outPortField.getText()));
            proxyService.startProxy();
            log("Proxy started automatically");
        } catch (Exception ex) {
            showError("Error starting proxy", ex.getMessage());
            log("Error: " + ex.getMessage());
        }
    }

    /**
     * Create the application menu bar.
     */
    private MenuBar createMenuBar(Stage primaryStage) {
        MenuBar menuBar = new MenuBar();

        // File menu
        Menu fileMenu = new Menu("File");

        MenuItem newProjectItem = new MenuItem("New");
        newProjectItem.setOnAction(e -> handleNewProject());

        MenuItem openProjectItem = new MenuItem("Open...");
        openProjectItem.setOnAction(e -> handleOpenProject(primaryStage));

        MenuItem saveProjectItem = new MenuItem("Save");
        saveProjectItem.setOnAction(e -> handleSaveProject());

        MenuItem saveAsProjectItem = new MenuItem("Save As...");
        saveAsProjectItem.setOnAction(e -> handleSaveAsProject());

        MenuItem quitItem = new MenuItem("Quit");
        quitItem.setOnAction(e -> Platform.exit());

        fileMenu.getItems().addAll(newProjectItem, openProjectItem, saveProjectItem, saveAsProjectItem,
                new SeparatorMenuItem(), quitItem);
        menuBar.getMenus().add(fileMenu);

        // Help menu
        Menu helpMenu = new Menu("Help");

        MenuItem aboutItem = new MenuItem("About");
        aboutItem.setOnAction(e -> showAboutDialog());

        helpMenu.getItems().add(aboutItem);
        menuBar.getMenus().add(helpMenu);

        return menuBar;
    }

    /**
     * Handle New Project menu action.
     */
    private void handleNewProject() {
        log("handleNewProject: Menu item clicked");
        TextInputDialog dialog = new TextInputDialog("Untitled");
        dialog.setTitle("New Project");
        dialog.setHeaderText("Create a new OSCPlay project");
        dialog.setContentText("Project name:");

        log("handleNewProject: Showing dialog...");
        dialog.showAndWait().ifPresent(name -> {
            log("handleNewProject: Dialog returned with name: " + name);
            try {
                log("Starting New Project creation: " + name);

                // Stop the proxy before switching projects
                if (proxyService != null) {
                    proxyService.stopProxy();
                    log("Proxy stopped");
                }

                projectManager.createProject(name);
                log("Project created in ProjectManager");

                // Update recordings directory for the new project
                if (projectManager.hasOpenProject()) {
                    RecordingSession.setRecordingsDirectory(projectManager.getRecordingsDir());
                    log("Recordings directory set: " + projectManager.getRecordingsDir());
                }

                log("Created new project: " + name);

                // Reload UI with new project settings
                log("Calling loadProjectConfiguration()...");
                loadProjectConfiguration();
                log("loadProjectConfiguration() completed");

                updateWindowTitle(primaryStage);

                // Restart proxy with new settings
                log("Restarting proxy...");
                restartProxyAfterProjectChange();
                log("Proxy restarted");
            } catch (IOException ex) {
                showError("Error creating project", ex.getMessage());
                log("ERROR creating project: " + ex.getMessage());
            }
        });
    }

    /**
     * Handle Open Project menu action.
     */
    private void handleOpenProject(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Project");
        fileChooser.setInitialDirectory(ProjectManager.getProjectsDir().toFile());
        fileChooser.getExtensionFilters().add(
            new ExtensionFilter("OSCPlay Projects", "*.opp")
        );

        File selectedFile = fileChooser.showOpenDialog(stage);
        if (selectedFile != null) {
            try {
                // Stop the proxy before switching projects
                if (proxyService != null) {
                    proxyService.stopProxy();
                }

                projectManager.openProject(selectedFile);

                // Update recordings directory for the new project
                if (projectManager.hasOpenProject()) {
                    RecordingSession.setRecordingsDirectory(projectManager.getRecordingsDir());
                }

                log("Opened project: " + selectedFile.getName());

                // Reload UI with project settings
                loadProjectConfiguration();
                updateWindowTitle(primaryStage);

                // Restart proxy with new settings
                restartProxyAfterProjectChange();
            } catch (IOException ex) {
                showError("Error opening project", ex.getMessage());
            }
        }
    }

    /**
     * Handle Save Project menu action.
     */
    private void handleSaveProject() {
        try {
            saveProjectConfiguration();
            projectManager.saveProject();
            log("Saved project: " + projectManager.getCurrentProjectName());
        } catch (IOException ex) {
            showError("Error saving project", ex.getMessage());
        }
    }

    /**
     * Handle Save As Project menu action.
     */
    private void handleSaveAsProject() {
        TextInputDialog dialog = new TextInputDialog(projectManager.getCurrentProjectName());
        dialog.setTitle("Save Project As");
        dialog.setHeaderText("Save project with a new name");
        dialog.setContentText("Project name:");

        dialog.showAndWait().ifPresent(name -> {
            try {
                saveProjectConfiguration();
                projectManager.saveProjectAs(name);
                log("Saved project as: " + name);
                updateWindowTitle(primaryStage);
            } catch (IOException ex) {
                showError("Error saving project", ex.getMessage());
            }
        });
    }

    /**
     * Initialize outputs from project configuration without updating UI.
     * Called during startup before UI components are created.
     */
    private void initializeOutputsFromProject() {
        ProjectConfig project = projectManager.getCurrentProject();
        if (project != null) {
            // Set the recordings directory for RecordingSession
            if (projectManager.hasOpenProject()) {
                RecordingSession.setRecordingsDirectory(projectManager.getRecordingsDir());
            }

            // Clear all outputs first to remove any previously loaded data
            log("initializeOutputsFromProject: Clearing all outputs...");
            proxyService.clearAllOutputs();
            log("initializeOutputsFromProject: Outputs cleared. Remaining outputs: " + proxyService.getOutputs().size());

            // Load outputs from project configuration
            log("initializeOutputsFromProject: Project has " + project.getOutputs().size() + " outputs configured");
            for (OutputConfig outputConfig : project.getOutputs()) {
                log("initializeOutputsFromProject: Loading output: " + outputConfig.getId());
                if ("default".equals(outputConfig.getId())) {
                    // Update the existing default output
                    OSCOutputService defaultOutput = proxyService.getOutput("default");
                    if (defaultOutput != null) {
                        defaultOutput.setOutHost(outputConfig.getHost());
                        defaultOutput.setOutPort(outputConfig.getPort());
                        defaultOutput.setEnabled(outputConfig.isEnabled());
                        loadNodeChainForOutput(defaultOutput, outputConfig.getNodeChain());
                    }
                } else {
                    OSCOutputService output = new OSCOutputService(outputConfig.getId());
                    output.setOutHost(outputConfig.getHost());
                    output.setOutPort(outputConfig.getPort());
                    output.setEnabled(outputConfig.isEnabled());
                    proxyService.addOutput(output);
                    loadNodeChainForOutput(output, outputConfig.getNodeChain());
                }
            }
            log("initializeOutputsFromProject: Final output count: " + proxyService.getOutputs().size());
        }
    }

    /**
     * Clear UI state when loading a new project.
     */
    private void clearUIState() {
        // Reset proxy service message counters
        if (proxyService != null) {
            proxyService.resetMessageCounters();
        }

        // Clear message counters (will be updated by property bindings)
        if (inMessageCountLabel != null) {
            inMessageCountLabel.setText("0");
        }
        if (messageCountLabel != null) {
            messageCountLabel.setText("Messages: 0");
        }

        // Reset recording state
        isRecording = false;
        if (recordButton != null) {
            recordButton.setText("Start Recording");
        }

        // Clear playback state
        if (playbackProgress != null) {
            playbackProgress.setProgress(0);
        }
        if (playbackStatusLabel != null) {
            playbackStatusLabel.setText("Ready");
        }
        if (audioFileLabel != null) {
            audioFileLabel.setText("No Audio");
        }

        // Clear log area
        if (logArea != null) {
            logArea.clear();
        }

        // Clear status bar
        if (statusBar != null) {
            statusBar.setText("");
        }

        // Reset playback output selector
        if (playbackOutputComboBox != null) {
            playbackOutputComboBox.setValue("Proxy");
        }

        // Clear session selection
        if (sessionComboBox != null) {
            sessionComboBox.getSelectionModel().clearSelection();
        }

        // Reset selected output to default
        selectedOutputId = "default";

        // Clear node chain for default output
        OSCOutputService defaultOutput = proxyService.getOutput("default");
        if (defaultOutput != null) {
            defaultOutput.getNodeChain().clearNodes();
        }

        // Reset sampler bank output routes to "Proxy"
        if (samplerPadUI != null) {
            for (int bank = 0; bank < 4; bank++) {
                ComboBox<String> routeCombo = samplerPadUI.getBankOutputRoute(bank);
                if (routeCombo != null) {
                    routeCombo.setValue("Proxy");
                }
            }
            // Clear all sampler pads
            samplerPadUI.clearAllPads();
        }

        // Reset recording editor to "New" with empty table
        if (recordingEditorUI != null) {
            recordingEditorUI.resetToNew();
        }
    }

    /**
     * Load project configuration into the application and update UI.
     * Called when switching projects after UI has been created.
     */
    private void loadProjectConfiguration() {
        log("loadProjectConfiguration: Starting...");

        // Clear UI state first
        log("loadProjectConfiguration: Calling clearUIState()");
        clearUIState();
        log("loadProjectConfiguration: clearUIState() completed");

        // Initialize outputs from project (clears old outputs, creates new ones from project config)
        log("loadProjectConfiguration: Calling initializeOutputsFromProject()");
        initializeOutputsFromProject();
        log("loadProjectConfiguration: initializeOutputsFromProject() completed");

        // Update UI components (only if they exist)
        if (outputComboBox != null) {
            log("loadProjectConfiguration: Updating outputs list");
            updateOutputsList();
            // Ensure default is selected
            outputComboBox.getSelectionModel().select("default");
            selectedOutputId = "default";
            log("loadProjectConfiguration: Selected output: " + selectedOutputId);
        }

        // Load input host and port from project
        ProjectConfig project = projectManager.getCurrentProject();
        if (project != null) {
            log("loadProjectConfiguration: Setting input fields - host:" + project.getInHost() + " port:" + project.getInPort());
            if (inHostField != null) {
                inHostField.setText(project.getInHost());
            }
            if (inPortField != null) {
                inPortField.setText(String.valueOf(project.getInPort()));
            }
        }

        // Update output fields for the selected output
        if (outHostField != null && outPortField != null) {
            log("loadProjectConfiguration: Updating output fields");
            updateOutputFields();
            log("loadProjectConfiguration: Output fields - host:" + outHostField.getText() + " port:" + outPortField.getText());
        }

        // Update sessions list
        if (sessionComboBox != null) {
            log("loadProjectConfiguration: Updating sessions list");
            updateSessionsList();
            log("loadProjectConfiguration: Sessions count: " + sessionComboBox.getItems().size());
        }

        // Update NodeChainManager to show the current output's node chain
        if (nodeChainManager != null && selectedOutputId != null) {
            log("loadProjectConfiguration: Setting NodeChainManager outputId to: " + selectedOutputId);
            nodeChainManager.setOutputId(selectedOutputId);
        }

        // Reload sampler configuration from the new project
        if (samplerPadUI != null) {
            log("loadProjectConfiguration: Reloading sampler configuration");
            samplerPadUI.reloadConfiguration();
        }

        log("loadProjectConfiguration: Completed");
    }

    /**
     * Save current configuration to the project.
     */
    private void saveProjectConfiguration() {
        ProjectConfig project = projectManager.getCurrentProject();
        if (project != null) {
            // Save playback mode (always WITH_REWRITE now)
            project.setPlaybackMode(PlaybackMode.WITH_REWRITE);

            // Save input host and port
            project.setInHost(inHostField.getText());
            try {
                project.setInPort(Integer.parseInt(inPortField.getText()));
            } catch (NumberFormatException e) {
                // Keep existing port if field has invalid value
            }

            // Save outputs
            project.getOutputs().clear();
            for (OSCOutputService output : proxyService.getOutputs()) {
                OutputConfig outputConfig = new OutputConfig(
                        output.getId(),
                        output.getOutHost(),
                        output.getOutPort(),
                        output.isEnabled(),
                        saveNodeChainForOutput(output)
                );
                project.addOrUpdateOutput(outputConfig);
            }
        }
    }

    private void setupEventHandlers() {
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
                    proxyService.startRecording(name);
                    isRecording = true;

                    recordButton.setText("Stop Recording");
                    log("Started recording session: " + name);
                });
            } else {
                OSCInputService inputService = proxyService.getInputService();
                if (inputService == null) {
                    showError("Error", "Input service not started");
                    return;
                }
                proxyService.stopRecording();
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

            playback.setProxyService(proxyService);

            // Set output routing based on playback output selector
            String outputRoute = playbackOutputComboBox.getValue();
            if (outputRoute != null && !outputRoute.equals("Proxy")) {
                // Route to specific output
                playback.setTargetOutputId(outputRoute);
                log("Playing session: " + sessionComboBox.getSelectionModel().getSelectedItem() + " -> " + outputRoute);
            } else {
                // Route to all enabled outputs (Proxy mode)
                playback.setTargetOutputId(null);
                log("Playing session: " + sessionComboBox.getSelectionModel().getSelectedItem() + " -> Proxy (all enabled)");
            }

            playback.playSession(sessionComboBox.getSelectionModel().getSelectedItem());
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

        // Add visual feedback for field editing
        setupFieldEditFeedback(inHostField);
        setupFieldEditFeedback(inPortField);
        setupFieldEditFeedback(outHostField);
        setupFieldEditFeedback(outPortField);

        // Output selection handler
        outputComboBox.setOnAction(e -> {
            String selected = outputComboBox.getSelectionModel().getSelectedItem();
            if (selected != null) {
                selectedOutputId = selected;
                updateOutputFields();
                nodeChainManager.setOutputId(selectedOutputId);
                log("Selected output: " + selected);
            }
        });

        // Manage outputs button handler
        manageOutputsButton.setOnAction(e -> {
            MultiOutputManager manager = new MultiOutputManager(
                    proxyService,
                    this::updateOutputsList,
                    this::saveOutputsToConfig
            );
            manager.show();
        });

        // Enable/disable output handler
        enableOutputCheckBox.setOnAction(e -> {
            OSCOutputService output = proxyService.getOutput(selectedOutputId);
            if (output != null) {
                output.setEnabled(enableOutputCheckBox.isSelected());
                log("Output '" + selectedOutputId + "' " +
                    (output.isEnabled() ? "enabled" : "disabled"));
                saveOutputsToConfig();
            }
        });

        // Monitor button handler
        monitorButton.setOnAction(e -> {
            if (currentMonitorWindow != null && currentMonitorWindow.isOpen()) {
                currentMonitorWindow.close();
            }
            currentMonitorWindow = new MonitorWindow(selectedOutputId);

            // Set the monitor window on the output service
            OSCOutputService output = proxyService.getOutput(selectedOutputId);
            if (output != null) {
                output.setMonitorWindow(currentMonitorWindow);
            }

            currentMonitorWindow.show();
            log("Opened monitor for output: " + selectedOutputId);
        });
    }

    /**
     * Sets up visual feedback for a text field - changes border color when edited
     * and reverts to default when Enter is pressed.
     */
    private void setupFieldEditFeedback(TextField field) {
        final String defaultStyle = field.getStyle();
        final String editedStyle = defaultStyle + " -fx-border-color: #FFA500; -fx-border-width: 2px;";

        field.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!field.getStyle().contains("-fx-border-color")) {
                field.setStyle(editedStyle);
            }
        });

        field.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                // Always attempt to restart proxy for proxy config fields, regardless of toggle state
                // This allows recovery from error states
                if (field == inHostField || field == inPortField ||
                    field == outHostField || field == outPortField) {
                    restartProxyWithNewSettings(field);
                } else {
                    field.setStyle(defaultStyle);
                }
            }
        });
    }

    /**
     * Restart the proxy after switching projects.
     * Uses the current UI field values (which have been updated from the project config).
     */
    private void restartProxyAfterProjectChange() {
        try {
            // Update input settings from UI fields
            proxyService.setInHost(inHostField.getText());
            proxyService.setInPort(Integer.parseInt(inPortField.getText()));

            // Update selected output settings from UI fields
            OSCOutputService output = proxyService.getOutput(selectedOutputId);
            if (output != null) {
                output.setOutHost(outHostField.getText());
                output.setOutPort(Integer.parseInt(outPortField.getText()));
            }

            // Start the proxy
            proxyService.startProxy();

            log("Proxy started - In: " + inHostField.getText() + ":" + inPortField.getText() +
                " Out[" + selectedOutputId + "]: " + outHostField.getText() + ":" + outPortField.getText());
        } catch (Exception ex) {
            String errorMsg = "Failed to start proxy: " + ex.getMessage();
            log("ERROR: " + errorMsg);
            statusBar.setText(errorMsg);
        }
    }

    /**
     * Restarts the proxy with new settings from the input fields.
     * Shows error indication on the field if restart fails.
     */
    private void restartProxyWithNewSettings(TextField changedField) {
        final String baseStyle = "-fx-font-size: 11px;";
        final String errorStyle = baseStyle + " -fx-border-color: #FF0000; -fx-border-width: 2px;";

        try {
            // Stop the current proxy (safe to call even if not running)
            proxyService.stopProxy();

            // Update input settings
            proxyService.setInHost(inHostField.getText());
            proxyService.setInPort(Integer.parseInt(inPortField.getText()));

            // Update selected output settings
            OSCOutputService output = proxyService.getOutput(selectedOutputId);
            if (output != null) {
                output.setOutHost(outHostField.getText());
                output.setOutPort(Integer.parseInt(outPortField.getText()));
            }

            proxyService.startProxy();

            log("Proxy started - In: " + inHostField.getText() + ":" + inPortField.getText() +
                " Out[" + selectedOutputId + "]: " + outHostField.getText() + ":" + outPortField.getText());

            // Clear any error styling on all proxy fields
            inHostField.setStyle(baseStyle);
            inPortField.setStyle(baseStyle);
            outHostField.setStyle(baseStyle);
            outPortField.setStyle(baseStyle);

            statusBar.setText("");

            // Save updated configuration
            saveOutputsToConfig();
        } catch (NumberFormatException ex) {
            String errorMsg = "Invalid port number: " + changedField.getText();
            log("ERROR: " + errorMsg);
            statusBar.setText(errorMsg);
            changedField.setStyle(errorStyle);
        } catch (Exception ex) {
            String errorMsg = "Failed to start proxy: " + ex.getMessage();
            log("ERROR: " + errorMsg);
            statusBar.setText(errorMsg);
            changedField.setStyle(errorStyle);
        }
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

    /**
     * Updates the outputs ComboBox with current outputs from the service.
     */
    private void updateOutputsList() {
        String currentSelection = outputComboBox.getSelectionModel().getSelectedItem();
        outputComboBox.getItems().clear();

        for (OSCOutputService output : proxyService.getOutputs()) {
            outputComboBox.getItems().add(output.getId());
        }

        // Restore selection if still exists, otherwise select first
        if (currentSelection != null && outputComboBox.getItems().contains(currentSelection)) {
            outputComboBox.getSelectionModel().select(currentSelection);
        } else if (!outputComboBox.getItems().isEmpty()) {
            outputComboBox.getSelectionModel().selectFirst();
            selectedOutputId = outputComboBox.getItems().get(0);
        }

        // Update playback output combo box
        if (playbackOutputComboBox != null) {
            String playbackSelection = playbackOutputComboBox.getSelectionModel().getSelectedItem();
            // Clear and re-add items (keep "Proxy" as first item)
            playbackOutputComboBox.getItems().clear();
            playbackOutputComboBox.getItems().add("Proxy");

            for (OSCOutputService output : proxyService.getOutputs()) {
                playbackOutputComboBox.getItems().add(output.getId());
            }

            // Restore previous selection if it still exists
            if (playbackSelection != null && playbackOutputComboBox.getItems().contains(playbackSelection)) {
                playbackOutputComboBox.getSelectionModel().select(playbackSelection);
            } else {
                playbackOutputComboBox.getSelectionModel().select("Proxy");
            }
        }
    }

    /**
     * Updates the output fields (host/port/enabled) based on selected output.
     */
    private void updateOutputFields() {
        final String baseStyle = "-fx-font-size: 11px;";

        OSCOutputService output = proxyService.getOutput(selectedOutputId);
        if (output != null) {
            outHostField.setText(output.getOutHost() != null ? output.getOutHost() : "127.0.0.1");
            outPortField.setText(String.valueOf(output.getOutPort()));
            enableOutputCheckBox.setSelected(output.isEnabled());

            // Clear any "edited" styling when switching outputs
            outHostField.setStyle(baseStyle);
            outPortField.setStyle(baseStyle);
        }
    }

    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }

    /**
     * Show the About dialog with application information.
     */
    private void showAboutDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About OSCPlay");
        alert.setHeaderText("OSCPlay " + appVersion);
        alert.setContentText("Copyright © Tracy Scott");
        alert.showAndWait();
    }

    private void log(String message) {
        Platform.runLater(() -> {
            logArea.appendText(message + "\n");
            logArea.setScrollTop(Double.MAX_VALUE); // Scroll to bottom
        });
    }

    /**
     * Load application version from properties file.
     */
    private void loadAppVersion() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            if (input != null) {
                Properties prop = new Properties();
                prop.load(input);
                appVersion = prop.getProperty("app.version", "unknown");
            }
        } catch (Exception e) {
            System.err.println("Error loading application version: " + e.getMessage());
        }
    }

    /**
     * Update the window title to show the current project name.
     */
    private void updateWindowTitle(Stage stage) {
        String projectName = projectManager != null && projectManager.getCurrentProjectName() != null
                ? projectManager.getCurrentProjectName()
                : "Untitled";
        stage.setTitle("OSCPlay " + appVersion + " - " + projectName);
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

    /**
     * Load application configuration from file or create default if not found.
     */
    private void loadApplicationConfig() {
        try {
            File configFile = DataDirectory.getConfigFile("app_config.json").toFile();
            if (configFile.exists()) {
                appConfig = configMapper.readValue(configFile, ApplicationConfig.class);
            } else {
                appConfig = new ApplicationConfig();
                saveApplicationConfig(); // Save default config
            }
        } catch (Exception e) {
            System.err.println("Error loading application config: " + e.getMessage());
            appConfig = new ApplicationConfig(); // Use defaults
        }
    }

    /**
     * Save application configuration to file.
     */
    private void saveApplicationConfig() {
        try {
            configMapper.writeValue(DataDirectory.getConfigFile("app_config.json").toFile(), appConfig);
        } catch (Exception e) {
            System.err.println("Error saving application config: " + e.getMessage());
        }
    }
    
    /**
     * Apply loaded configuration to UI controls.
     */
    private void applyConfigurationToUI() {
        // No UI controls to apply currently
    }

    /**
     * Load outputs from application config and apply to proxy service.
     */
    private void loadOutputsFromConfig() {
        for (OutputConfig outputConfig : appConfig.getOutputs()) {
            // Skip default - it already exists
            if ("default".equals(outputConfig.getId())) {
                // Update default output settings
                OSCOutputService defaultOutput = proxyService.getOutput("default");
                if (defaultOutput != null) {
                    defaultOutput.setOutHost(outputConfig.getHost());
                    defaultOutput.setOutPort(outputConfig.getPort());
                    defaultOutput.setEnabled(outputConfig.isEnabled());
                    // Load node chain for default output
                    loadNodeChainForOutput(defaultOutput, outputConfig.getNodeChain());
                }
            } else {
                // Create new output
                OSCOutputService output = new OSCOutputService(outputConfig.getId());
                output.setOutHost(outputConfig.getHost());
                output.setOutPort(outputConfig.getPort());
                output.setEnabled(outputConfig.isEnabled());
                proxyService.addOutput(output);
                // Load node chain for this output
                loadNodeChainForOutput(output, outputConfig.getNodeChain());
            }
        }
    }

    /**
     * Load and apply a node chain configuration to an output.
     */
    private void loadNodeChainForOutput(OSCOutputService output, NodeChainConfig chainConfig) {
        if (chainConfig == null || chainConfig.getNodes() == null) {
            return;
        }

        for (NodeChainConfig.NodeConfig nodeConfig : chainConfig.getNodes()) {
            try {
                // Instantiate node
                Class<?> nodeClass = Class.forName(nodeConfig.getType());
                OSCNode node = (OSCNode) nodeClass.getDeclaredConstructor().newInstance();

                // Configure node with args
                String[] args = nodeConfig.getArgs().toArray(new String[0]);
                if (node.configure(args)) {
                    // Register if enabled
                    if (nodeConfig.isEnabled()) {
                        proxyService.registerNode(output.getId(), node);
                    }
                } else {
                    log("Warning: Failed to configure node " + nodeConfig.getType());
                }
            } catch (Exception e) {
                log("Error loading node " + nodeConfig.getType() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Save current outputs configuration to app config.
     */
    public void saveOutputsToConfig() {
        appConfig.getOutputs().clear();

        for (OSCOutputService output : proxyService.getOutputs()) {
            OutputConfig outputConfig = new OutputConfig(
                    output.getId(),
                    output.getOutHost(),
                    output.getOutPort(),
                    output.isEnabled(),
                    saveNodeChainForOutput(output)
            );
            appConfig.addOrUpdateOutput(outputConfig);
        }

        saveApplicationConfig();
    }

    /**
     * Save the node chain of an output to configuration format.
     */
    private NodeChainConfig saveNodeChainForOutput(OSCOutputService output) {
        NodeChainConfig chainConfig = new NodeChainConfig();
        List<OSCNode> nodes = output.getNodeChain().getNodes();

        for (OSCNode node : nodes) {
            NodeChainConfig.NodeConfig nodeConfig = new NodeChainConfig.NodeConfig();
            nodeConfig.setType(node.getClass().getName());
            nodeConfig.setEnabled(true); // Nodes in the chain are considered enabled
            nodeConfig.setArgs(java.util.Arrays.asList(node.getArgs()));
            chainConfig.getNodes().add(nodeConfig);
        }

        return chainConfig;
    }

    @Override
    public void stop() {
        proxyService.stopProxy();
        saveOutputsToConfig(); // Save outputs and their node chains
        saveApplicationConfig(); // Save config on exit
    }
}
