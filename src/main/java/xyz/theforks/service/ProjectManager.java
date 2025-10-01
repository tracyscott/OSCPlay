package xyz.theforks.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import xyz.theforks.model.AppSettings;
import xyz.theforks.model.ProjectConfig;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Manages OSCPlay projects and their file structure.
 * Projects are stored in ~/Documents/OSCPlay/Projects/{ProjectName}/
 * Each project contains:
 * - {ProjectName}.opp - project configuration file
 * - Recordings/ - directory for recorded sessions
 * - NodeChains/ - directory for node chain configurations
 * - Scripts/ - directory for JavaScript files used by ScriptNode
 */
public class ProjectManager {

    private static final String OSCPLAY_DIR = "OSCPlay";
    private static final String PROJECTS_DIR = "Projects";
    private static final String SETTINGS_FILE = "oscplay.cfg";
    private static final String RECORDINGS_DIR = "Recordings";
    private static final String NODECHAINS_DIR = "NodeChains";
    private static final String SCRIPTS_DIR = "Scripts";

    private final ObjectMapper mapper = new ObjectMapper();
    private AppSettings appSettings;
    private ProjectConfig currentProject;
    private Path currentProjectPath;

    public ProjectManager() {
        loadAppSettings();
    }

    /**
     * Get the base OSCPlay directory path.
     */
    public static Path getOSCPlayDir() {
        String userHome = System.getProperty("user.home");
        return Paths.get(userHome, "Documents", OSCPLAY_DIR);
    }

    /**
     * Get the projects directory path.
     */
    public static Path getProjectsDir() {
        return getOSCPlayDir().resolve(PROJECTS_DIR);
    }

    /**
     * Get the settings file path.
     */
    private static Path getSettingsPath() {
        return getOSCPlayDir().resolve(SETTINGS_FILE);
    }

    /**
     * Load application settings from oscplay.cfg
     */
    private void loadAppSettings() {
        try {
            Path settingsPath = getSettingsPath();
            if (Files.exists(settingsPath)) {
                appSettings = mapper.readValue(settingsPath.toFile(), AppSettings.class);
            } else {
                appSettings = new AppSettings();
            }
        } catch (IOException e) {
            System.err.println("Error loading app settings: " + e.getMessage());
            appSettings = new AppSettings();
        }
    }

    /**
     * Save application settings to oscplay.cfg
     */
    private void saveAppSettings() throws IOException {
        Path settingsPath = getSettingsPath();
        Files.createDirectories(settingsPath.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(settingsPath.toFile(), appSettings);
    }

    /**
     * Create a new project with the given name.
     */
    public void createProject(String projectName) throws IOException {
        Path projectDir = getProjectsDir().resolve(projectName);

        if (Files.exists(projectDir)) {
            throw new IOException("Project already exists: " + projectName);
        }

        // Create project directory structure
        Files.createDirectories(projectDir);
        Files.createDirectories(projectDir.resolve(RECORDINGS_DIR));
        Files.createDirectories(projectDir.resolve(NODECHAINS_DIR));
        Files.createDirectories(projectDir.resolve(SCRIPTS_DIR));

        // Create project config file
        ProjectConfig config = new ProjectConfig();
        config.setProjectName(projectName);
        Path configPath = projectDir.resolve(projectName + ".opp");
        mapper.writerWithDefaultPrettyPrinter().writeValue(configPath.toFile(), config);

        // Set as current project
        currentProject = config;
        currentProjectPath = projectDir;
        appSettings.setCurrentProjectPath(projectDir.toString());
        saveAppSettings();
    }

    /**
     * Open an existing project from a .opp file.
     */
    public void openProject(File oppFile) throws IOException {
        if (!oppFile.exists() || !oppFile.getName().endsWith(".opp")) {
            throw new IOException("Invalid project file: " + oppFile.getAbsolutePath());
        }

        currentProject = mapper.readValue(oppFile, ProjectConfig.class);
        currentProjectPath = oppFile.getParentFile().toPath();
        appSettings.setCurrentProjectPath(currentProjectPath.toString());
        saveAppSettings();

        // Ensure required directories exist
        ensureProjectDirectories();
    }

    /**
     * Save the current project configuration.
     */
    public void saveProject() throws IOException {
        if (currentProject == null || currentProjectPath == null) {
            throw new IOException("No project is currently open");
        }

        Path configPath = currentProjectPath.resolve(currentProject.getProjectName() + ".opp");
        mapper.writerWithDefaultPrettyPrinter().writeValue(configPath.toFile(), currentProject);
    }

    /**
     * Save the current project to a new location (Save As).
     */
    public void saveProjectAs(String newProjectName) throws IOException {
        if (currentProject == null) {
            throw new IOException("No project is currently open");
        }

        Path newProjectDir = getProjectsDir().resolve(newProjectName);

        if (Files.exists(newProjectDir)) {
            throw new IOException("Project already exists: " + newProjectName);
        }

        // Create new project directory structure
        Files.createDirectories(newProjectDir);
        Files.createDirectories(newProjectDir.resolve(RECORDINGS_DIR));
        Files.createDirectories(newProjectDir.resolve(NODECHAINS_DIR));
        Files.createDirectories(newProjectDir.resolve(SCRIPTS_DIR));

        // Update project name and save
        currentProject.setProjectName(newProjectName);
        currentProjectPath = newProjectDir;
        Path configPath = newProjectDir.resolve(newProjectName + ".opp");
        mapper.writerWithDefaultPrettyPrinter().writeValue(configPath.toFile(), currentProject);

        // Update app settings
        appSettings.setCurrentProjectPath(newProjectDir.toString());
        saveAppSettings();
    }

    /**
     * Load the last opened project from settings.
     */
    public void loadLastProject() throws IOException {
        String lastProjectPath = appSettings.getCurrentProjectPath();
        if (lastProjectPath != null) {
            Path projectDir = Paths.get(lastProjectPath);
            if (Files.exists(projectDir)) {
                // Find .opp file in directory
                File[] oppFiles = projectDir.toFile().listFiles((dir, name) -> name.endsWith(".opp"));
                if (oppFiles != null && oppFiles.length > 0) {
                    openProject(oppFiles[0]);
                    return;
                }
            }
        }
        // If no last project or it doesn't exist, create a default project
        createDefaultProject();
    }

    /**
     * Create a default project if none exists.
     */
    private void createDefaultProject() throws IOException {
        createProject("Default");
    }

    /**
     * Ensure all required project directories exist.
     */
    private void ensureProjectDirectories() throws IOException {
        if (currentProjectPath == null) {
            return;
        }
        Files.createDirectories(currentProjectPath.resolve(RECORDINGS_DIR));
        Files.createDirectories(currentProjectPath.resolve(NODECHAINS_DIR));
        Files.createDirectories(currentProjectPath.resolve(SCRIPTS_DIR));
    }

    /**
     * Get the recordings directory for the current project.
     */
    public Path getRecordingsDir() {
        if (currentProjectPath == null) {
            throw new IllegalStateException("No project is currently open");
        }
        return currentProjectPath.resolve(RECORDINGS_DIR);
    }

    /**
     * Get the node chains directory for the current project.
     */
    public Path getNodeChainsDir() {
        if (currentProjectPath == null) {
            throw new IllegalStateException("No project is currently open");
        }
        return currentProjectPath.resolve(NODECHAINS_DIR);
    }

    /**
     * Get the current project directory.
     */
    public Path getProjectDir() {
        return currentProjectPath;
    }

    /**
     * Get the current project configuration.
     */
    public ProjectConfig getCurrentProject() {
        return currentProject;
    }

    /**
     * Check if a project is currently open.
     */
    public boolean hasOpenProject() {
        return currentProject != null && currentProjectPath != null;
    }

    /**
     * Get the current project name.
     */
    public String getCurrentProjectName() {
        return currentProject != null ? currentProject.getProjectName() : null;
    }

    /**
     * Get the scripts directory for the current project.
     */
    public Path getScriptsDir() {
        if (currentProjectPath == null) {
            throw new IllegalStateException("No project is currently open");
        }
        return currentProjectPath.resolve(SCRIPTS_DIR);
    }
}
