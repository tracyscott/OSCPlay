package xyz.theforks;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import xyz.theforks.model.RecordingSession;
import xyz.theforks.service.OSCProxyService;
import xyz.theforks.ui.Theme;

public class ManageRecordings {
    private final Stage stage;
    private final OSCProxyService proxyService;
    private final ListView<String> recordingsList;
    // Add new fields for threshold storage
    private double savedForwardThreshold = 100;
    private double savedReverseThreshold = 100;
    private int savedWindowSize = 10;
    private double savedForwardThresholdTime = -1;
    private double savedReverseThresholdTime = -1;

    public ManageRecordings(OSCProxyService proxyService) {
        this.proxyService = proxyService;
        this.stage = new Stage();
        stage.setTitle("Manage Recordings");

        recordingsList = new ListView<>();
        updateRecordingsList();

        VBox root = new VBox(10);
        recordingsList.getItems().addAll(proxyService.getRecordedSessions());

        recordingsList.setCellFactory(param -> new javafx.scene.control.ListCell<>() {
            private final HBox container = new HBox(10);
            private final Label nameLabel = new Label();
            private final Button viewButton = new Button("View");
            private final Button exportButton = new Button("Export");

            {
                nameLabel.setPrefWidth(150);
                container.setAlignment(Pos.CENTER_LEFT);
                container.getChildren().addAll(nameLabel, viewButton, exportButton);
                
                viewButton.setOnAction(e -> viewSession(getItem()));
                exportButton.setOnAction(e -> exportCalibration(getItem()));
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    nameLabel.setText(item);
                    setGraphic(container);
                }
            }
        });

        root.getChildren().add(recordingsList);
        
        Scene scene = new Scene(root, 400, 300);
        Theme.applyDark(scene);
        stage.setScene(scene);
    }

    private void updateRecordingsList() {
        recordingsList.getItems().clear();
        recordingsList.getItems().addAll(proxyService.getRecordedSessions());
    }

    private void exportCalibration(String sessionName) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Calibration");
        fileChooser.setInitialFileName(sessionName + ".csv");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("CSV File", "*.csv")
        );

        File file = fileChooser.showSaveDialog(stage);
        
        if (file != null) {
            try {
                RecordingSession session = RecordingSession.loadSession(sessionName);
                if (session != null) {
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                        // Write CSV header
                        writer.write("timestamp,magx,magy,magz\n");
                        
                        // Get start time for normalization
                        long startTime = session.getMessages().get(0).getTimestamp();
                        
                        session.getMessages().forEach(record -> {
                            try {
                                double timeInSeconds = (record.getTimestamp() - startTime) / 1000.0;
                                // Only write records between the saved threshold times
                                if ((savedForwardThresholdTime < 0 || timeInSeconds >= savedForwardThresholdTime) && 
                                    (savedReverseThresholdTime < 0 || timeInSeconds <= savedReverseThresholdTime)) {
                                    writer.write(String.format("%d,%d,%d,%d\n",
                                        record.getTimestamp(),
                                        ((Double)record.getArguments()[0]).intValue(),
                                        ((Double)record.getArguments()[1]).intValue(),
                                        ((Double)record.getArguments()[2]).intValue()
                                    ));
                                }
                            } catch (IOException e) {
                                System.err.println("Error writing record to CSV: " + e.getMessage());
                            }
                        });
                    }
                }
            } catch (IOException ioex) {
                System.err.println("Error loading session recording: " + sessionName);
            }
        }
    }

    private void viewSession(String sessionName) {
        try {
            RecordingSession session = RecordingSession.loadSession(sessionName);
            if (session != null && !session.getMessages().isEmpty()) {
                Stage viewStage = new Stage();
                viewStage.setTitle("View Session: " + sessionName);

                VBox chartsContainer = new VBox(10);
                
                // Get start time for normalization
                long startTime = session.getMessages().get(0).getTimestamp();
                
                // Create three charts for X, Y, Z
                LineChart<Number, Number> chartX = createChart("Magnetometer X", "Time (seconds)", "X Value");
                LineChart<Number, Number> chartY = createChart("Magnetometer Y", "Time (seconds)", "Y Value");
                LineChart<Number, Number> chartZ = createChart("Magnetometer Z", "Time (seconds)", "Z Value");

                // Populate data
                XYChart.Series<Number, Number> seriesX = new XYChart.Series<>();
                XYChart.Series<Number, Number> seriesY = new XYChart.Series<>();
                XYChart.Series<Number, Number> seriesZ = new XYChart.Series<>();

                // Create series for threshold markers
                XYChart.Series<Number, Number> thresholdX = new XYChart.Series<>();
                XYChart.Series<Number, Number> thresholdY = new XYChart.Series<>();
                XYChart.Series<Number, Number> thresholdZ = new XYChart.Series<>();
                XYChart.Series<Number, Number> reverseThresholdX = new XYChart.Series<>();
                XYChart.Series<Number, Number> reverseThresholdY = new XYChart.Series<>();
                XYChart.Series<Number, Number> reverseThresholdZ = new XYChart.Series<>();
                thresholdX.setName("Forward Threshold");
                thresholdY.setName("Forward Threshold");
                thresholdZ.setName("Forward Threshold");
                reverseThresholdX.setName("Reverse Threshold");
                reverseThresholdY.setName("Reverse Threshold");
                reverseThresholdZ.setName("Reverse Threshold");

                session.getMessages().forEach(record -> {
                    double timeInSeconds = (record.getTimestamp() - startTime) / 1000.0;
                    seriesX.getData().add(new XYChart.Data<>(timeInSeconds, ((Double)record.getArguments()[0]).intValue()));
                    seriesY.getData().add(new XYChart.Data<>(timeInSeconds, ((Double)record.getArguments()[1]).intValue()));
                    seriesZ.getData().add(new XYChart.Data<>(timeInSeconds, ((Double)record.getArguments()[2]).intValue()));
                });

                chartX.getData().addAll(seriesX, thresholdX, reverseThresholdX);
                chartY.getData().addAll(seriesY, thresholdY, reverseThresholdY);
                chartZ.getData().addAll(seriesZ, thresholdZ, reverseThresholdZ);

                // Add threshold controls
                HBox controls = new HBox(10);
                controls.setAlignment(Pos.CENTER);
                
                Label forwardThresholdLabel = new Label("Forward Threshold:");
                TextField forwardThresholdInput = new TextField(String.valueOf(savedForwardThreshold));
                Label reverseThresholdLabel = new Label("Reverse Threshold:");
                TextField reverseThresholdInput = new TextField(String.valueOf(savedReverseThreshold));
                Label windowLabel = new Label("Window Size:");
                TextField windowInput = new TextField(String.valueOf(savedWindowSize));
                Button showButton = new Button("Show Threshold Points");
                Button saveButton = new Button("Save Thresholds");

                controls.getChildren().addAll(
                    forwardThresholdLabel, forwardThresholdInput,
                    reverseThresholdLabel, reverseThresholdInput,
                    windowLabel, windowInput,
                    showButton, saveButton
                );

                // Style the threshold points
                String thresholdPointStyle = 
                    "-fx-background-color: red;" +
                    "-fx-background-radius: 5px;" +
                    "-fx-padding: 5px;" +
                    "-fx-shape: \"M0,0 L1,0 L1,1 L0,1 Z\";" +
                    "-fx-min-width: 10px;" +
                    "-fx-min-height: 10px;";

                showButton.setOnAction(e -> {
                    try {
                        double forwardThreshold = Double.parseDouble(forwardThresholdInput.getText());
                        double reverseThreshold = Double.parseDouble(reverseThresholdInput.getText());
                        int windowSize = Integer.parseInt(windowInput.getText());
                        
                        double forwardThresholdTime = findMovementStart(session, forwardThreshold, windowSize, startTime);
                        double reverseThresholdTime = findMovementStartReverse(session, reverseThreshold, windowSize, startTime);
                        
                        // Clear and add threshold points
                        thresholdX.getData().clear();
                        thresholdY.getData().clear();
                        thresholdZ.getData().clear();
                        reverseThresholdX.getData().clear();
                        reverseThresholdY.getData().clear();
                        reverseThresholdZ.getData().clear();
                        
                        if (forwardThresholdTime >= 0) {
                            int idx = (int)(forwardThresholdTime * 1000 / session.getMessages().get(1).getTimestamp());
                            if (idx < session.getMessages().size()) {
                                var msg = session.getMessages().get(idx);
                                addThresholdPoint(thresholdX, forwardThresholdTime, ((Double)msg.getArguments()[0]).intValue());
                                addThresholdPoint(thresholdY, forwardThresholdTime, ((Double)msg.getArguments()[1]).intValue());
                                addThresholdPoint(thresholdZ, forwardThresholdTime, ((Double)msg.getArguments()[2]).intValue());
                            }
                        }
                        
                        if (reverseThresholdTime >= 0) {
                            int idx = (int)(reverseThresholdTime * 1000 / session.getMessages().get(1).getTimestamp());
                            if (idx < session.getMessages().size()) {
                                var msg = session.getMessages().get(idx);
                                addThresholdPoint(reverseThresholdX, reverseThresholdTime, ((Double)msg.getArguments()[0]).intValue());
                                addThresholdPoint(reverseThresholdY, reverseThresholdTime, ((Double)msg.getArguments()[1]).intValue());
                                addThresholdPoint(reverseThresholdZ, reverseThresholdTime, ((Double)msg.getArguments()[2]).intValue());
                            }
                        }
                    } catch (NumberFormatException ex) {
                        System.err.println("Invalid threshold or window size");
                    }
                });

                saveButton.setOnAction(e -> {
                    try {
                        savedForwardThreshold = Double.parseDouble(forwardThresholdInput.getText());
                        savedReverseThreshold = Double.parseDouble(reverseThresholdInput.getText());
                        savedWindowSize = Integer.parseInt(windowInput.getText());
                        savedForwardThresholdTime = findMovementStart(session, savedForwardThreshold, savedWindowSize, startTime);
                        savedReverseThresholdTime = findMovementStartReverse(session, savedReverseThreshold, savedWindowSize, startTime);
                    } catch (NumberFormatException ex) {
                        System.err.println("Invalid values for saving");
                    }
                });

                chartsContainer.getChildren().addAll(chartX, chartY, chartZ, controls);
                Scene viewScene = new Scene(chartsContainer, 800, 600);
                viewStage.setScene(viewScene);
                viewStage.show();
            }
        } catch (IOException e) {
            System.err.println("Error loading session for viewing: " + sessionName);
        }
    }

    private void addThresholdPoint(XYChart.Series<Number, Number> series, double x, double y) {
        XYChart.Data<Number, Number> point = new XYChart.Data<>(x, y);
        series.getData().add(point);
        
        // Style the point after it's added to the chart
        Platform.runLater(() -> {
            if (point.getNode() != null) {
                point.getNode().setStyle(
                    "-fx-background-color: red;" +
                    "-fx-background-radius: 5px;" +
                    "-fx-padding: 5px;" +
                    "-fx-shape: \"M0,0 L1,0 L1,1 L0,1 Z\";" +
                    "-fx-min-width: 10px;" +
                    "-fx-min-height: 10px;"
                );
            }
        });
    }

    private double findMovementStart(RecordingSession session, double threshold, int windowSize, long startTime) {
        if (windowSize >= session.getMessages().size()) return -1;
        
        for (int i = windowSize; i < session.getMessages().size(); i++) {
            double sumDeltaX = 0, sumDeltaY = 0, sumDeltaZ = 0;
            
            // Calculate average change over window
            for (int j = 0; j < windowSize; j++) {
                var current = session.getMessages().get(i - j);
                var previous = session.getMessages().get(i - j - 1);
                
                sumDeltaX += Math.abs(((Double)current.getArguments()[0]).intValue() - 
                                    ((Double)previous.getArguments()[0]).intValue());
                sumDeltaY += Math.abs(((Double)current.getArguments()[1]).intValue() - 
                                    ((Double)previous.getArguments()[1]).intValue());
                sumDeltaZ += Math.abs(((Double)current.getArguments()[2]).intValue() - 
                                    ((Double)previous.getArguments()[2]).intValue());
            }
            
            double avgDelta = (sumDeltaX + sumDeltaY + sumDeltaZ) / (3 * windowSize);
            
            if (avgDelta > threshold) {
                return (session.getMessages().get(i).getTimestamp() - startTime) / 1000.0;
            }
        }
        
        return -1;
    }

    private double findMovementStartReverse(RecordingSession session, double threshold, int windowSize, long startTime) {
        if (windowSize >= session.getMessages().size()) return -1;
        
        for (int i = session.getMessages().size() - windowSize - 1; i >= 0; i--) {
            double sumDeltaX = 0, sumDeltaY = 0, sumDeltaZ = 0;
            
            // Calculate average change over window
            for (int j = 0; j < windowSize; j++) {
                var current = session.getMessages().get(i + j);
                var previous = session.getMessages().get(i + j + 1);
                
                sumDeltaX += Math.abs(((Double)current.getArguments()[0]).intValue() - 
                                    ((Double)previous.getArguments()[0]).intValue());
                sumDeltaY += Math.abs(((Double)current.getArguments()[1]).intValue() - 
                                    ((Double)previous.getArguments()[1]).intValue());
                sumDeltaZ += Math.abs(((Double)current.getArguments()[2]).intValue() - 
                                    ((Double)previous.getArguments()[2]).intValue());
            }
            
            double avgDelta = (sumDeltaX + sumDeltaY + sumDeltaZ) / (3 * windowSize);
            
            if (avgDelta > threshold) {
                return (session.getMessages().get(i).getTimestamp() - startTime) / 1000.0;
            }
        }
        
        return -1;
    }

    private LineChart<Number, Number> createChart(String title, String xLabel, String yLabel) {
        NumberAxis xAxis = new NumberAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel(xLabel);
        yAxis.setLabel(yLabel);
        
        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle(title);
        chart.setCreateSymbols(false);
        chart.setPrefHeight(200);
        
        return chart;
    }

    public void show() {
        stage.show();
    }

    // Add getters for the saved values
    public double getSavedForwardThreshold() { return savedForwardThreshold; }
    public double getSavedReverseThreshold() { return savedReverseThreshold; }
    public int getSavedWindowSize() { return savedWindowSize; }
    public double getSavedForwardThresholdTime() { return savedForwardThresholdTime; }
    public double getSavedReverseThresholdTime() { return savedReverseThresholdTime; }
}