# Project Loading Process

This document describes the two different processes for loading a project in OSCPlay and highlights the differences between them.

## 1. Loading from Splash Screen (Application Startup)

This happens when the application first starts via `OSCProxyApp.start()`.

### Pseudocode:

```
FUNCTION start(primaryStage):
    // 1. Show project selection dialog
    selectedProjectName = ProjectSplashScreen.showAndWait()
    IF selectedProjectName == null:
        EXIT application

    // 2. Initialize project manager
    projectManager = new ProjectManager()
    projectManager.openProject(selectedProjectName)
    RecordingSession.setRecordingsDirectory(projectManager.getRecordingsDir())
    ScriptNode.setProjectManager(projectManager)

    // 3. Load application config
    loadApplicationConfig()

    // 4. Create services
    playback = new Playback()
    proxyService = new OSCProxyService(projectManager)

    // 5. Initialize outputs from project
    initializeOutputsFromProject():
        - Clear all outputs in proxyService
        - For each output in project config:
            - Create OSCOutputService
            - Set host, port, enabled state
            - Load node chain for output

    // 6. Build UI components (includes creating SamplerPadUI)
    buildUI():
        ...
        // Create Sampler tab (line 394)
        samplerPadUI = new SamplerPadUI(proxyService, playback, logArea, projectManager)

        // SamplerPadUI constructor does:
        SamplerPadUI.constructor():
            - Initialize data structures (bankPads, bankPadButtons, bankOutputRoutes, etc.)
            - Create UI elements (MIDI controls, bank tabs, pad grids)
            - For each bank (0-3):
                - Create output route ComboBox with "Proxy" as default
                - Add setOnAction handler that saves config when !isLoading
                - Create pad buttons
            - Call loadConfiguration()  // LINE 140 - THIS LOADS THE SAMPLER CONFIG
        ...

    // 7. Apply configuration to UI
    applyConfigurationToUI()

    // 8. Update sessions list
    updateSessionsList()

    // 9. Update outputs list and select default
    updateOutputsList()
    outputComboBox.select("default")
    updateOutputFields()

    // 10. Start proxy automatically
    proxyService.startProxy()
```

### Key Points:
- **SamplerPadUI is created ONCE** during UI construction (line 394)
- **loadConfiguration() is called in the SamplerPadUI constructor** (line 140)
- This happens AFTER outputs are initialized from project config
- The sampler config loads successfully because:
  - Outputs are already loaded in proxyService
  - isLoading = true during constructor's loadConfiguration() call
  - No clearAllPads() is called before loading

---

## 2. Loading via File...Open Menu

This happens when user selects File > Open while the application is already running, via `handleOpenProject()`.

### Pseudocode:

```
FUNCTION handleOpenProject(stage):
    // 1. Show file chooser
    selectedFile = FileChooser.showOpenDialog(stage)
    IF selectedFile == null:
        RETURN

    // 2. Stop current proxy
    proxyService.stopProxy()

    // 3. Open new project
    projectManager.openProject(selectedFile)
    RecordingSession.setRecordingsDirectory(projectManager.getRecordingsDir())

    // 4. Reload UI with project settings
    loadProjectConfiguration():

        // 4a. Clear UI state
        clearUIState():
            - proxyService.resetMessageCounters()
            - Reset UI labels and counters
            - Clear playback state
            - Clear log area and status bar
            - Reset playback output selector
            - Clear session selection
            - selectedOutputId = "default"
            - defaultOutput.getNodeChain().clearNodes()

            // *** THE PROBLEM: Clear sampler pads ***
            samplerPadUI.clearAllPadsWithoutSaving():
                - isLoading = true  // Set BEFORE Platform.runLater
                - Platform.runLater():
                    - Clear all pad data structures
                    - Update buttons to "Empty"
                    - Set ALL combo boxes to "Proxy"  // *** TRIGGERS setOnAction ***
                    - Clear MIDI mappings
                    - Clear active pads
                    // isLoading is still true, so setOnAction SHOULD not save

            - recordingEditorUI.resetToNew()

        // 4b. Initialize outputs from new project
        initializeOutputsFromProject():
            - Clear all outputs in proxyService
            - Load outputs from new project config

        // 4c. Update UI components
        updateOutputsList()
        outputComboBox.select("default")

        // 4d. Load input host/port from project
        inHostField.setText(project.getInHost())
        inPortField.setText(project.getInPort())

        // 4e. Update output fields
        updateOutputFields()

        // 4f. Update sessions list
        updateSessionsList()

        // 4g. Update NodeChainManager
        nodeChainManager.setOutputId(selectedOutputId)

        // 4h. Reload sampler configuration
        samplerPadUI.reloadConfiguration():
            - For each bank combo box:
                - updateOutputRouteComboBox(comboBox)  // Add new outputs to combo
            - loadConfiguration():
                - isLoading = true  // Set at start
                - TRY:
                    - Load sampler_pads.json from project directory
                    - For each pad in config:
                        - Restore pad to bankPads map
                        - updatePadButton() to show label/color
                    - For each bank route in config:
                        - comboBox.setValue(route)  // *** TRIGGERS setOnAction ***
                - FINALLY:
                    - isLoading = false

    // 5. Update window title
    updateWindowTitle(primaryStage)

    // 6. Restart proxy
    restartProxyAfterProjectChange()
```

### Key Points:
- **SamplerPadUI already exists** - it was created during initial startup
- **clearAllPadsWithoutSaving() is called** which:
  - Sets isLoading = true
  - Clears all pad data in Platform.runLater()
  - Sets combo boxes to "Proxy" which triggers setOnAction
- **The problem**: There's a race condition or timing issue where:
  - Platform.runLater() schedules the clearing asynchronously
  - loadConfiguration() might run before/during/after the clearing
  - The setOnAction handlers might fire when isLoading is false

---

## The Bug

The issue is in the timing and ordering of async operations:

1. `clearAllPadsWithoutSaving()` sets `isLoading = true` synchronously
2. `clearAllPadsInternal()` schedules work in `Platform.runLater()` (async)
3. `reloadConfiguration()` calls `loadConfiguration()` synchronously
4. `loadConfiguration()` sets `isLoading = true` again (redundant)
5. `loadConfiguration()` loads the config and sets combo box values
6. `loadConfiguration()` sets `isLoading = false` in finally block
7. **THEN** the `Platform.runLater()` from step 2 executes
8. The combo boxes are set to "Proxy", triggering `setOnAction`
9. Since `isLoading = false` (from step 6), it calls `saveConfiguration()`
10. The empty/Proxy configuration overwrites the file!

### Root Cause

The `Platform.runLater()` in `clearAllPadsInternal()` executes **after** `loadConfiguration()` completes, so the `isLoading` flag is already false when the clearing happens and triggers the save.

### Solution Ideas

1. **Don't clear at all** - Just reload directly over existing data
2. **Synchronous clearing** - Don't use Platform.runLater() for clearing
3. **Better flag management** - Ensure isLoading remains true throughout the entire reload process
4. **Remove setOnAction during reload** - Temporarily remove/disable the event handlers
