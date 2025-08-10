package xyz.theforks;

import java.io.File;

import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import xyz.theforks.ui.Theme;
import xyz.theforks.service.OSCInputService;
import xyz.theforks.service.OSCOutputService;


public class Sampler {
    private Stage padWindow;
    private IntegerProperty[] messageCounters;
    private OSCInputService inputService;
    private OSCOutputService outputService;  
    private TextArea logArea;
    private final String SAMPLER_DIR = "sampler";

    public Sampler(OSCInputService inputService, OSCOutputService outputService, TextArea logArea) {
        this.inputService = inputService;
        this.outputService = outputService;
        this.logArea = logArea;
        this.messageCounters = new IntegerProperty[16];
        createDirectories();
        setStartRecordingCallback((padNumber, oscFilter) -> {
            inputService.addMessageHandlerWithFilter(oscFilter, (message) -> {
                if (messageCounters[padNumber] != null) {
                    messageCounters[padNumber].set(messageCounters[padNumber].get() + 1);
                }
                if (outputService != null) {
                    try {
                        outputService.send(message);
                    } catch (Exception e) {
                        log("Error sending message: " + e.getMessage());
                    }
                }
            });
            log("Recording started on pad " + padNumber);
        });
        setStopRecordingCallback((padNumber) -> {
            //inputService.stopRecording(padNumber);
            log("Recording stopped on pad " + padNumber);
        });
    }

    private void createDirectories() {
        new File(SAMPLER_DIR).mkdirs();
    }

    public void show() {
        if (padWindow == null) {
            createPadWindow();
        }
        padWindow.show();
    }

    public void hide() {
        if (padWindow != null) {
            padWindow.hide();
        }
    }

    public void resetMessageCount(int padNumber) {
        if (messageCounters[padNumber] != null) {
            messageCounters[padNumber].set(0);
        }
    }

    public interface RecordingStartCallback {
        void onRecordingStart(int padNumber, String oscFilter);
    }

    public interface RecordingStopCallback {
        void onRecordingStop(int padNumber);
    }

    private RecordingStartCallback startCallback;
    private RecordingStopCallback stopCallback;

    public void setStartRecordingCallback(RecordingStartCallback callback) {
        this.startCallback = callback;
    }

    public void setStopRecordingCallback(RecordingStopCallback callback) {
        this.stopCallback = callback;
    }

    public Stage createPadWindow() {
        padWindow = new Stage();
        padWindow.setTitle("Sampler Pads");
        
        GridPane padGrid = new GridPane();
        padGrid.setHgap(10);
        padGrid.setVgap(10);
        padGrid.setPadding(new Insets(10));

        Color[] colors = {
            Color.RED, Color.BLUE, Color.GREEN, Color.PURPLE,
            Color.ORANGE, Color.CYAN, Color.MAGENTA, Color.YELLOW,
            Color.LIGHTBLUE, Color.LIGHTGREEN, Color.PINK, Color.CORAL,
            Color.DARKBLUE, Color.DARKGREEN, Color.DARKRED, Color.DARKORANGE
        };

        int colorIndex = 0;
        int padCount = 0;
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                VBox padContainer = createPadWithControls(colors[colorIndex++], padCount);
                padGrid.add(padContainer, col, row);
                padCount++;
            }
        }

        Scene scene = new Scene(padGrid);
        Theme.applyDark(scene);
        padWindow.setScene(scene);
        return padWindow;
    }

    private VBox createPadWithControls(Color baseColor, int padNumber) {
        // Create the main pad button
        Button pad = createPad(baseColor);
        
        // Create record button
        ToggleButton recordButton = new ToggleButton("REC");
        recordButton.setStyle("""
            -fx-background-color: #808080;
            -fx-text-fill: white;
            -fx-font-size: 10pt;
            -fx-min-width: 45px;
            -fx-min-height: 24px;
            -fx-max-width: 45px;
            -fx-max-height: 24px;
            -fx-background-radius: 12px;
            -fx-padding: 2px 8px;
            """);

        // Create message counter label
        Label messageCounter = new Label("000000");
        messageCounter.setStyle("""
            -fx-font-family: monospace;
            -fx-font-size: 12pt;
            -fx-min-width: 70px;
            -fx-alignment: CENTER-RIGHT;
            -fx-padding: 0 5 0 5;
            -fx-background-color: #2b2b2b;
            -fx-text-fill: #00ff00;
            -fx-border-color: #404040;
            -fx-border-width: 1;
            -fx-border-radius: 3;
            """);

        // Bind the counter to the IntegerProperty
        messageCounter.textProperty().bind(messageCounters[padNumber].asString("%06d"));
            
        // Create OSC filter text field
        TextField oscFilterField = new TextField();
        oscFilterField.setPromptText("OSC Address Filter");
        oscFilterField.setPrefWidth(120);
        
        // Create container for record button, counter, and OSC filter
        HBox recordControls = new HBox(5);
        recordControls.setAlignment(Pos.CENTER);
        recordControls.getChildren().addAll(recordButton, messageCounter, oscFilterField);
        
        // Add listeners with callbacks
        recordButton.setOnAction(e -> {
            if (recordButton.isSelected()) {
                recordButton.setStyle("""
                    -fx-background-color: #ff0000;
                    -fx-text-fill: white;
                    -fx-font-size: 10pt;
                    -fx-min-width: 45px;
                    -fx-min-height: 24px;
                    -fx-max-width: 45px;
                    -fx-max-height: 24px;
                    -fx-background-radius: 12px;
                    -fx-padding: 2px 8px;
                    -fx-effect: dropshadow(gaussian, rgba(255,0,0,0.4), 10, 0, 0, 0);
                    """);
                resetMessageCount(padNumber);  // Reset counter when starting new recording
                if (startCallback != null) {
                    startCallback.onRecordingStart(padNumber, oscFilterField.getText());
                }
            } else {
                recordButton.setStyle("""
                    -fx-background-color: #808080;
                    -fx-text-fill: white;
                    -fx-font-size: 10pt;
                    -fx-min-width: 45px;
                    -fx-min-height: 24px;
                    -fx-max-width: 45px;
                    -fx-max-height: 24px;
                    -fx-background-radius: 12px;
                    -fx-padding: 2px 8px;
                    """);
                if (stopCallback != null) {
                    stopCallback.onRecordingStop(padNumber);
                }
            }
        });
        
        // Main container with new layout
        VBox container = new VBox(5);
        container.setAlignment(Pos.TOP_CENTER);
        container.getChildren().addAll(
            pad,             // Pad in the middle
            recordControls   // Record controls at the bottom
        );
        container.setPadding(new Insets(5));
        
        return container;
    }

    private Button createPad(Color baseColor) {
        Button pad = new Button();
        pad.setPrefSize(100, 100);
        pad.setMinSize(100, 100);
        
        String colorString = String.format("rgb(%d, %d, %d)", 
            (int)(baseColor.getRed() * 255),
            (int)(baseColor.getGreen() * 255),
            (int)(baseColor.getBlue() * 255));
        
        Color brighterColor = baseColor.brighter();
        String brighterColorString = String.format("rgb(%d, %d, %d)", 
            (int)(brighterColor.getRed() * 255),
            (int)(brighterColor.getGreen() * 255),
            (int)(brighterColor.getBlue() * 255));

        pad.setStyle(String.format("""
            -fx-background-color: %s;
            -fx-background-radius: 5;
            -fx-border-radius: 5;
            -fx-border-color: #333333;
            -fx-border-width: 2;
            """, colorString));

        pad.setOnMousePressed(e -> 
            pad.setStyle(String.format("""
                -fx-background-color: %s;
                -fx-background-radius: 5;
                -fx-border-radius: 5;
                -fx-border-color: #333333;
                -fx-border-width: 2;
                -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.8), 10, 0, 0, 0);
                """, brighterColorString))
        );

        pad.setOnMouseReleased(e -> 
            pad.setStyle(String.format("""
                -fx-background-color: %s;
                -fx-background-radius: 5;
                -fx-border-radius: 5;
                -fx-border-color: #333333;
                -fx-border-width: 2;
                """, colorString))
        );

        return pad;
    }

    private void log(String message) {
        Platform.runLater(() -> {
            logArea.appendText(message + "\n");
            logArea.setScrollTop(Double.MAX_VALUE);
        });
    }
}
