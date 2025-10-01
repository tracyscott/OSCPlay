package xyz.theforks.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import xyz.theforks.service.ProjectManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Splash screen displayed on application startup for project selection.
 * Allows the user to select an existing project or create a new one.
 */
public class ProjectSplashScreen {

    private Stage dialog;
    private String selectedProjectName = null;
    private boolean cancelled = false;

    /**
     * Show the splash screen and wait for user selection.
     *
     * @return The selected or newly created project name, or null if cancelled
     */
    public String showAndWait() {
        dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("OSCPlay - Select Project");
        dialog.setResizable(false);

        VBox mainLayout = new VBox(20);
        mainLayout.setPadding(new Insets(20));
        mainLayout.setAlignment(Pos.CENTER);

        // Title
        Label titleLabel = new Label("Welcome to OSCPlay");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        Label subtitleLabel = new Label("Select or create a project to continue");
        subtitleLabel.setStyle("-fx-font-size: 12px;");

        // Project list
        ListView<String> projectListView = new ListView<>();
        projectListView.setPrefHeight(200);
        projectListView.setPrefWidth(400);
        loadExistingProjects(projectListView);

        // Select the first project by default if available
        if (!projectListView.getItems().isEmpty()) {
            projectListView.getSelectionModel().selectFirst();
        }

        // Buttons
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);

        Button newProjectButton = new Button("New Project");
        Button openButton = new Button("Open");
        Button cancelButton = new Button("Cancel");

        openButton.setDefaultButton(true);
        newProjectButton.setPrefWidth(100);
        openButton.setPrefWidth(100);
        cancelButton.setPrefWidth(100);

        // Disable open button if no project selected
        openButton.disableProperty().bind(
            projectListView.getSelectionModel().selectedItemProperty().isNull()
        );

        buttonBox.getChildren().addAll(newProjectButton, openButton, cancelButton);

        mainLayout.getChildren().addAll(
            titleLabel,
            subtitleLabel,
            new Label("Available Projects:"),
            projectListView,
            buttonBox
        );

        // Event handlers
        newProjectButton.setOnAction(e -> handleNewProject(projectListView));

        openButton.setOnAction(e -> {
            selectedProjectName = projectListView.getSelectionModel().getSelectedItem();
            if (selectedProjectName != null) {
                dialog.close();
            }
        });

        cancelButton.setOnAction(e -> {
            cancelled = true;
            dialog.close();
        });

        // Double-click to open
        projectListView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && projectListView.getSelectionModel().getSelectedItem() != null) {
                selectedProjectName = projectListView.getSelectionModel().getSelectedItem();
                dialog.close();
            }
        });

        Scene scene = new Scene(mainLayout);
        scene.setFill(Color.web("#121212"));
        Theme.applyDark(scene);
        dialog.setScene(scene);

        // Set application icon
        try {
            dialog.getIcons().addAll(
                new Image(getClass().getResourceAsStream("/icons/oscplay-16x16.png")),
                new Image(getClass().getResourceAsStream("/icons/oscplay-32x32.png")),
                new Image(getClass().getResourceAsStream("/icons/oscplay-48x48.png")),
                new Image(getClass().getResourceAsStream("/icons/oscplay-64x64.png")),
                new Image(getClass().getResourceAsStream("/icons/oscplay-128x128.png")),
                new Image(getClass().getResourceAsStream("/icons/oscplay-256x256.png"))
            );
        } catch (Exception ignored) {
            // Icons optional
        }

        dialog.showAndWait();

        return cancelled ? null : selectedProjectName;
    }

    /**
     * Load existing projects from the Projects directory.
     */
    private void loadExistingProjects(ListView<String> listView) {
        listView.getItems().clear();

        Path projectsDir = ProjectManager.getProjectsDir();
        if (!Files.exists(projectsDir)) {
            return;
        }

        try {
            List<String> projects = Files.list(projectsDir)
                .filter(Files::isDirectory)
                .map(path -> path.getFileName().toString())
                .sorted()
                .collect(Collectors.toList());

            listView.getItems().addAll(projects);
        } catch (IOException e) {
            showError("Error Loading Projects", "Failed to load project list: " + e.getMessage());
        }
    }

    /**
     * Handle the New Project button action.
     */
    private void handleNewProject(ListView<String> projectListView) {
        TextInputDialog inputDialog = new TextInputDialog();
        inputDialog.setTitle("New Project");
        inputDialog.setHeaderText("Create a new OSCPlay project");
        inputDialog.setContentText("Project name:");

        Optional<String> result = inputDialog.showAndWait();
        result.ifPresent(projectName -> {
            // Validate project name
            if (projectName.trim().isEmpty()) {
                showError("Invalid Name", "Project name cannot be empty.");
                return;
            }

            // Check if project already exists
            if (projectExists(projectName)) {
                showError("Project Exists",
                    "A project with the name '" + projectName + "' already exists.\nPlease choose a different name.");
                return;
            }

            // Create the project
            try {
                ProjectManager tempManager = new ProjectManager();
                tempManager.createProject(projectName);

                // Close dialog and return new project name
                selectedProjectName = projectName;
                dialog.close();
            } catch (IOException e) {
                showError("Error Creating Project", "Failed to create project: " + e.getMessage());
            }
        });
    }

    /**
     * Check if a project with the given name already exists.
     */
    private boolean projectExists(String projectName) {
        Path projectDir = ProjectManager.getProjectsDir().resolve(projectName);
        return Files.exists(projectDir);
    }

    /**
     * Show an error dialog.
     */
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
