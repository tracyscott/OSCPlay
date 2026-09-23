package xyz.theforks.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Labeled;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import xyz.theforks.CalibrationViewer;
import xyz.theforks.calibration.Calibration;
import xyz.theforks.calibration.CalibrationBuilder;
import xyz.theforks.calibration.CalibrationSample;
import xyz.theforks.calibration.CalibrationStore;
import xyz.theforks.calibration.CurveModel;
import xyz.theforks.calibration.HoldDetector;
import xyz.theforks.calibration.LinearModel;
import xyz.theforks.calibration.SampleExtractor;
import xyz.theforks.model.OSCMessageRecord;
import xyz.theforks.model.RecordingSession;
import xyz.theforks.nodes.InterlaceMagNode;
import xyz.theforks.service.OSCProxyService;
import xyz.theforks.service.ProjectManager;

/**
 * Builds a sensor calibration from a recording: pick the recording and sensor address, check
 * the detected holds against the calibration marks, preview the fit and save it to the project.
 * Saving reloads any node already using the calibration.
 */
public class CalibrationWindow {
    private static final String CUSTOM = "Custom";
    private static final String OK_STYLE = "-fx-text-fill: #51cf66;";
    private static final String WARN_STYLE = "-fx-text-fill: #fcc419;";
    private static final String ERROR_STYLE = "-fx-text-fill: #ff6b6b;";

    private final ProjectManager projectManager;
    private final OSCProxyService proxyService;
    private final Map<String, CalibrationBuilder> presets = new LinkedHashMap<>();

    private Stage stage;
    private final ComboBox<String> presetBox = new ComboBox<>();
    private final TextField nameField = new TextField();
    private final ComboBox<String> recordingBox = new ComboBox<>();
    private final ComboBox<String> addressBox = new ComboBox<>();
    private final Spinner<Integer> dimsSpinner = new Spinner<>(1, 16, 1);
    private final ComboBox<String> modelBox = new ComboBox<>(
        FXCollections.observableArrayList(LinearModel.TYPE, CurveModel.TYPE));
    private final CheckBox marksCheck = new CheckBox("Calibration marks");
    private final TextField startField = new TextField("0");
    private final TextField endField = new TextField("100");
    private final TextField stepField = new TextField("10");
    private final TextField minHoldField = new TextField(String.valueOf(HoldDetector.DEFAULT_MIN_HOLD_MS));
    private final TextField thresholdField = new TextField();
    private final CalibrationSignalPlot plot = new CalibrationSignalPlot();
    private final TableView<HoldRow> holdsTable = new TableView<>();
    private final Label statusLabel = new Label();
    private final Button previewButton = new Button("Preview 3D");
    private final Button saveButton = new Button("Save");

    private List<OSCMessageRecord> messages = new ArrayList<>();
    private Map<String, Integer> addressDimensions = new LinkedHashMap<>();
    private Calibration calibration;
    // Suppresses re-analysis while several fields are set at once
    private boolean updating = false;

    public CalibrationWindow(ProjectManager projectManager, OSCProxyService proxyService) {
        this.projectManager = projectManager;
        this.proxyService = proxyService;
        for (int mag = 1; mag <= 3; mag++) {
            presets.put("Interlace Mag " + mag, InterlaceMagNode.calibrationBuilder(mag));
        }
    }

    public boolean isShowing() {
        return stage != null && stage.isShowing();
    }

    /**
     * Show the window, or bring it to the front if it's already open. Each instance is shown
     * once; create a new one after it has been closed.
     */
    public void show() {
        if (stage != null) {
            stage.toFront();
            return;
        }
        stage = new Stage();
        String project = projectManager.getCurrentProjectName();
        stage.setTitle(project != null ? "Sensor Calibration - " + project : "Sensor Calibration");

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));
        root.setTop(createForm());

        createHoldsTable();
        SplitPane split = new SplitPane(plot, holdsTable);
        split.setOrientation(Orientation.VERTICAL);
        split.setDividerPositions(0.6);
        BorderPane.setMargin(split, new Insets(10, 0, 10, 0));
        root.setCenter(split);

        Button closeButton = new Button("Close");
        closeButton.setOnAction(e -> stage.close());
        previewButton.setOnAction(e -> preview());
        saveButton.setOnAction(e -> save());
        statusLabel.setWrapText(true);
        statusLabel.setMinWidth(0);
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(statusLabel, Priority.ALWAYS);
        HBox bottom = new HBox(10, statusLabel, previewButton, saveButton, closeButton);
        bottom.setAlignment(Pos.CENTER_LEFT);
        keepNaturalWidth(bottom);
        root.setBottom(bottom);

        refreshRecordings();
        analyze();

        Scene scene = new Scene(root, 900, 700);
        Theme.applyDark(scene);
        stage.setScene(scene);
        stage.show();
    }

    private GridPane createForm() {
        presetBox.getItems().add(CUSTOM);
        presetBox.getItems().addAll(presets.keySet());
        presetBox.setValue(CUSTOM);
        presetBox.setOnAction(e -> applyPreset());

        addressBox.setEditable(true);
        addressBox.setPrefWidth(280);
        addressBox.setPromptText("Address pattern (regex)");
        addressBox.valueProperty().addListener((obs, o, address) -> {
            Integer dims = address != null ? addressDimensions.get(address) : null;
            if (dims != null && !updating) {
                updating = true;
                dimsSpinner.getValueFactory().setValue(dims);
                updating = false;
            }
            analyze();
        });

        recordingBox.setPrefWidth(220);
        recordingBox.setOnAction(e -> loadRecording());
        Button refreshButton = new Button("Refresh");
        refreshButton.setOnAction(e -> refreshRecordings());

        modelBox.setValue(LinearModel.TYPE);
        nameField.setPromptText("Calibration name");
        thresholdField.setPromptText("auto");
        for (TextField field : new TextField[]{startField, endField, stepField, minHoldField, thresholdField}) {
            field.setPrefColumnCount(6);
        }
        marksCheck.setSelected(true);

        dimsSpinner.setPrefWidth(70);
        dimsSpinner.valueProperty().addListener((obs, o, n) -> analyze());
        modelBox.setOnAction(e -> analyze());
        marksCheck.setOnAction(e -> analyze());
        for (TextField field : new TextField[]{nameField, startField, endField, stepField, minHoldField, thresholdField}) {
            field.textProperty().addListener((obs, o, n) -> analyze());
        }

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.addRow(0, new Label("Preset"), presetBox, new Label("Name"), nameField);
        grid.addRow(1, new Label("Recording"), new HBox(6, recordingBox, refreshButton),
            new Label("Address"), new HBox(6, addressBox, new Label("Values"), dimsSpinner));
        grid.addRow(2, new Label("Model"), modelBox, marksCheck,
            new HBox(6, new Label("From"), startField, new Label("to"), endField, new Label("step"), stepField));
        grid.addRow(3, new Label("Min hold (ms)"), minHoldField, new Label("Stillness"), thresholdField);
        keepNaturalWidth(grid);
        return grid;
    }

    /**
     * Stop labels, buttons and check boxes under a node from being squeezed into "...".
     */
    private void keepNaturalWidth(Parent parent) {
        for (Node node : parent.getChildrenUnmodifiable()) {
            if ((node instanceof Labeled && node != statusLabel)) {
                ((Region) node).setMinWidth(Region.USE_PREF_SIZE);
            } else if (node instanceof Pane) {
                keepNaturalWidth((Parent) node);
            }
        }
    }

    private void createHoldsTable() {
        holdsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        holdsTable.setPlaceholder(new Label("No holds detected"));
        holdsTable.getColumns().add(column("#", r -> r.index, 40));
        holdsTable.getColumns().add(column("Start (s)", r -> r.start, 80));
        holdsTable.getColumns().add(column("End (s)", r -> r.end, 80));
        holdsTable.getColumns().add(column("Label", r -> r.label, 80));
        holdsTable.getColumns().add(column("Reading", r -> r.reading, 300));
    }

    private static TableColumn<HoldRow, String> column(String title, java.util.function.Function<HoldRow, String> value, double width) {
        TableColumn<HoldRow, String> column = new TableColumn<>(title);
        column.setCellValueFactory(c -> new SimpleStringProperty(value.apply(c.getValue())));
        column.setPrefWidth(width);
        column.setSortable(false);
        return column;
    }

    private void refreshRecordings() {
        String selected = recordingBox.getValue();
        List<String> recordings = new ArrayList<>(proxyService.getRecordedSessions());
        recordings.sort(String.CASE_INSENSITIVE_ORDER);
        recordingBox.getItems().setAll(recordings);
        if (selected != null && recordings.contains(selected)) {
            recordingBox.setValue(selected);
        }
    }

    private void loadRecording() {
        String name = recordingBox.getValue();
        messages = new ArrayList<>();
        if (name != null) {
            try {
                RecordingSession session = RecordingSession.loadSession(name);
                if (session != null) {
                    messages = session.getMessages();
                }
            } catch (IOException e) {
                setStatus("Can't load recording " + name + ": " + e.getMessage(), ERROR_STYLE);
            }
        }
        addressDimensions = SampleExtractor.numericAddresses(messages);

        updating = true;
        String address = addressBox.getValue();
        addressBox.getItems().setAll(addressDimensions.keySet());
        if ((address == null || address.isEmpty()) && !addressDimensions.isEmpty()) {
            address = addressDimensions.keySet().iterator().next();
            dimsSpinner.getValueFactory().setValue(addressDimensions.get(address));
        }
        addressBox.setValue(address);
        updating = false;
        analyze();
    }

    private void applyPreset() {
        CalibrationBuilder preset = presets.get(presetBox.getValue());
        if (preset == null) {
            return;
        }
        updating = true;
        nameField.setText(preset.getName());
        addressBox.setValue(preset.getAddress());
        dimsSpinner.getValueFactory().setValue(preset.getDimensions());
        modelBox.setValue(preset.getModel());
        List<Double> labels = preset.getLabels();
        marksCheck.setSelected(labels != null);
        if (labels != null && labels.size() >= 2) {
            startField.setText(format(labels.get(0)));
            endField.setText(format(labels.get(labels.size() - 1)));
            stepField.setText(format(labels.get(1) - labels.get(0)));
        }
        updating = false;
        analyze();
    }

    /**
     * Builder for the current form values.
     *
     * @throws IllegalArgumentException if a field can't be parsed
     */
    private CalibrationBuilder currentBuilder() {
        CalibrationBuilder builder = new CalibrationBuilder()
            .name(nameField.getText().trim())
            .address(addressBox.getValue() != null ? addressBox.getValue().trim() : "")
            .dimensions(dimsSpinner.getValue())
            .model(modelBox.getValue())
            .minHoldMs(parseLong(minHoldField, "Min hold"));
        String threshold = thresholdField.getText().trim();
        builder.threshold(threshold.isEmpty() ? Double.NaN : parseDouble(thresholdField, "Stillness"));
        if (marksCheck.isSelected()) {
            builder.labels(CalibrationBuilder.labelRange(
                parseDouble(startField, "From"), parseDouble(endField, "To"), parseDouble(stepField, "Step")));
        }
        return builder;
    }

    /**
     * Re-run hold detection and fitting for the current settings and update the display.
     */
    private void analyze() {
        if (updating || stage == null) {
            return;
        }
        calibration = null;
        List<CalibrationSample> samples = new ArrayList<>();
        List<HoldDetector.Hold> holds = new ArrayList<>();
        List<Double> labels = null;

        try {
            CalibrationBuilder builder = currentBuilder();
            labels = builder.getLabels();
            if (builder.getAddress().isEmpty()) {
                setStatus("Choose a recording and the sensor address to calibrate.", WARN_STYLE);
            } else {
                samples = builder.extractSamples(messages);
                if (samples.isEmpty()) {
                    setStatus("No messages on " + builder.getAddress() + " with " + builder.getDimensions()
                        + " numeric value(s) in this recording.", WARN_STYLE);
                } else {
                    HoldDetector detector = builder.holdDetector(samples);
                    holds = detector.detect(samples);
                    thresholdField.setPromptText(String.format("auto: %.0f", detector.getThreshold()));
                    String summary = String.format("%d samples, noise %.1f, stillness %.1f. ", samples.size(),
                        HoldDetector.estimateNoise(samples), detector.getThreshold());

                    if (labels != null && holds.size() != labels.size()) {
                        setStatus(summary + "Found " + holds.size() + " holds for " + labels.size()
                            + " marks. Adjust the stillness or minimum hold, or re-record visiting every mark.",
                            WARN_STYLE);
                    } else {
                        calibration = builder.buildFromSamples(samples).getCalibration();
                        calibration.setSourceRecording(recordingBox.getValue());
                        String ready = labels != null
                            ? "All " + labels.size() + " marks found."
                            : "Fit without marks: output is 0-1 along the recording.";
                        setStatus(summary + ready + (builder.getName().isEmpty() ? " Enter a name to save." : ""),
                            OK_STYLE);
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            setStatus(e.getMessage(), ERROR_STYLE);
            calibration = null;
        }

        plot.setData(samples, holds, labels);
        List<HoldRow> rows = new ArrayList<>();
        for (int i = 0; i < holds.size(); i++) {
            rows.add(new HoldRow(i, holds.get(i), labels));
        }
        holdsTable.getItems().setAll(rows);

        boolean canSave = calibration != null && !nameField.getText().trim().isEmpty()
            && projectManager.hasOpenProject();
        saveButton.setDisable(!canSave);
        previewButton.setDisable(calibration == null || calibration.getDimensions() != 3);
    }

    private void preview() {
        if (calibration != null) {
            new CalibrationViewer().show(calibration, "Calibration preview - " + calibration.getName());
        }
    }

    private void save() {
        if (calibration == null) {
            return;
        }
        String name = calibration.getName();
        CalibrationStore store = new CalibrationStore(projectManager.getCalibrationsDir());
        if (store.exists(name)) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Replace the existing calibration \"" + name + "\"?", ButtonType.OK, ButtonType.CANCEL);
            confirm.setHeaderText(null);
            Theme.applyDark(confirm.getDialogPane().getScene());
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                return;
            }
        }
        try {
            store.save(calibration);
            int reloaded = proxyService.reloadCalibration(name);
            setStatus("Saved " + store.pathFor(name) + (reloaded > 0
                ? ". Updated " + reloaded + " node(s) using it." : "."), OK_STYLE);
        } catch (IOException e) {
            setStatus("Can't save: " + e.getMessage(), ERROR_STYLE);
        }
    }

    private void setStatus(String text, String style) {
        statusLabel.setText(text);
        statusLabel.setStyle(style);
    }

    private static double parseDouble(TextField field, String name) {
        try {
            return Double.parseDouble(field.getText().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be a number");
        }
    }

    private static long parseLong(TextField field, String name) {
        try {
            return Long.parseLong(field.getText().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be a whole number");
        }
    }

    private static String format(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    private static class HoldRow {
        final String index;
        final String start;
        final String end;
        final String label;
        final String reading;

        HoldRow(int i, HoldDetector.Hold hold, List<Double> labels) {
            index = String.valueOf(i + 1);
            start = String.format("%.2f", hold.getStartTime() / 1000.0);
            end = String.format("%.2f", hold.getEndTime() / 1000.0);
            label = labels == null ? "" : i < labels.size() ? format(labels.get(i)) : "(none)";
            StringBuilder sb = new StringBuilder();
            for (double v : hold.getMean()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(String.format("%.1f", v));
            }
            reading = sb.toString();
        }
    }
}
