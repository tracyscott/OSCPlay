package xyz.theforks.ui;

import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import xyz.theforks.model.OSCMessageRecord;
import xyz.theforks.model.RecordingSession;
import xyz.theforks.service.OSCProxyService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RecordingEditorUI extends VBox {

    private final OSCProxyService proxyService;
    private final TextArea logArea;
    private final Runnable onSessionsChanged;

    private ComboBox<String> recordingComboBox;
    private Button saveButton;
    private TableView<MessageRow> messagesTable;
    private ObservableList<MessageRow> messageData;

    private RecordingSession currentSession;
    private String currentSessionName;

    public RecordingEditorUI(OSCProxyService proxyService, TextArea logArea, Runnable onSessionsChanged) {
        this.proxyService = proxyService;
        this.logArea = logArea;
        this.onSessionsChanged = onSessionsChanged;

        setPadding(new Insets(10));
        setSpacing(10);

        createUI();
    }

    private void createUI() {
        // Top controls: Recording selector and Save button
        HBox topControls = new HBox(10);
        topControls.setAlignment(Pos.CENTER_LEFT);

        Label selectLabel = new Label("Recording:");
        recordingComboBox = new ComboBox<>();
        recordingComboBox.setPromptText("Select Recording");
        recordingComboBox.setMinWidth(200);
        recordingComboBox.getItems().add("New");

        saveButton = new Button("Save");
        saveButton.setOnAction(e -> handleSave());

        topControls.getChildren().addAll(selectLabel, recordingComboBox, saveButton);
        HBox.setHgrow(recordingComboBox, Priority.ALWAYS);

        // Messages table
        messagesTable = new TableView<>();
        messageData = FXCollections.observableArrayList();
        messagesTable.setItems(messageData);
        messagesTable.setEditable(true);
        messagesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // Timestamp column (editable)
        TableColumn<MessageRow, String> timestampCol = new TableColumn<>("Timestamp (ms)");
        timestampCol.setCellValueFactory(new PropertyValueFactory<>("timestamp"));
        timestampCol.setCellFactory(col -> new EditableTextCell());
        timestampCol.setOnEditCommit(event -> {
            MessageRow row = event.getRowValue();
            try {
                row.setTimestamp(event.getNewValue());
            } catch (NumberFormatException ex) {
                log("Invalid timestamp: " + event.getNewValue());
            }
        });
        timestampCol.setPrefWidth(120);

        // Address column (editable)
        TableColumn<MessageRow, String> addressCol = new TableColumn<>("Address");
        addressCol.setCellValueFactory(new PropertyValueFactory<>("address"));
        addressCol.setCellFactory(col -> new EditableTextCell());
        addressCol.setOnEditCommit(event -> {
            event.getRowValue().setAddress(event.getNewValue());
        });
        addressCol.setPrefWidth(200);

        // Arguments column (custom rendering)
        TableColumn<MessageRow, MessageRow> argsCol = new TableColumn<>("Arguments");
        argsCol.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue()));
        argsCol.setCellFactory(col -> new ArgumentsCell());
        argsCol.setPrefWidth(400);

        // Actions column (delete button)
        TableColumn<MessageRow, MessageRow> actionsCol = new TableColumn<>("Actions");
        actionsCol.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue()));
        actionsCol.setCellFactory(col -> new ActionsCell());
        actionsCol.setPrefWidth(80);

        messagesTable.getColumns().addAll(timestampCol, addressCol, argsCol, actionsCol);

        // Add message button
        Button addMessageButton = new Button("Add Message");
        addMessageButton.setOnAction(e -> handleAddMessage());

        getChildren().addAll(topControls, messagesTable, addMessageButton);
        VBox.setVgrow(messagesTable, Priority.ALWAYS);

        // Set up event handlers
        recordingComboBox.setOnAction(e -> handleRecordingSelection());

        // Load available recordings
        refreshRecordingsList();
    }

    public void refreshRecordingsList() {
        String currentSelection = recordingComboBox.getValue();
        recordingComboBox.getItems().clear();
        recordingComboBox.getItems().add("New");
        recordingComboBox.getItems().addAll(proxyService.getRecordedSessions());

        if (currentSelection != null && recordingComboBox.getItems().contains(currentSelection)) {
            recordingComboBox.setValue(currentSelection);
        } else {
            recordingComboBox.setValue("New");
        }
    }

    private void handleRecordingSelection() {
        String selected = recordingComboBox.getValue();
        if (selected == null) {
            return;
        }

        if ("New".equals(selected)) {
            // Clear table for new recording
            currentSession = null;
            currentSessionName = null;
            messageData.clear();
        } else {
            // Load selected recording
            try {
                currentSession = RecordingSession.loadSession(selected);
                currentSessionName = selected;
                loadMessages();
                log("Loaded recording: " + selected);
            } catch (IOException ex) {
                log("Error loading recording: " + ex.getMessage());
                showError("Error Loading Recording", ex.getMessage());
            }
        }
    }

    private void loadMessages() {
        messageData.clear();
        if (currentSession != null) {
            for (OSCMessageRecord msg : currentSession.getMessages()) {
                messageData.add(new MessageRow(msg));
            }
        }
    }

    private void handleAddMessage() {
        MessageRow newRow = new MessageRow();
        messageData.add(newRow);
        messagesTable.scrollTo(newRow);
    }

    private void handleSave() {
        String sessionName = currentSessionName;

        // If "New" is selected, prompt for name
        if (sessionName == null || "New".equals(recordingComboBox.getValue())) {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Save Recording");
            dialog.setHeaderText("Enter a name for this recording:");
            dialog.setContentText("Name:");

            Optional<String> result = dialog.showAndWait();
            if (!result.isPresent() || result.get().trim().isEmpty()) {
                return;
            }
            sessionName = result.get().trim();
        }

        // Create session from table data
        RecordingSession session = new RecordingSession(sessionName);
        for (MessageRow row : messageData) {
            OSCMessageRecord msg = row.toOSCMessageRecord();
            session.addMessage(msg);
        }

        // Save session
        try {
            session.save();
            currentSession = session;
            currentSessionName = sessionName;
            log("Saved recording: " + sessionName);

            // Refresh recordings list
            refreshRecordingsList();
            recordingComboBox.setValue(sessionName);

            // Notify parent of changes
            if (onSessionsChanged != null) {
                onSessionsChanged.run();
            }
        } catch (IOException ex) {
            log("Error saving recording: " + ex.getMessage());
            showError("Error Saving Recording", ex.getMessage());
        }
    }

    private void log(String message) {
        if (logArea != null) {
            Platform.runLater(() -> {
                logArea.appendText(message + "\n");
                logArea.setScrollTop(Double.MAX_VALUE);
            });
        }
    }

    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }

    // Inner class for table rows
    public static class MessageRow {
        private final SimpleStringProperty timestamp;
        private final SimpleStringProperty address;
        private final ObservableList<ArgumentData> arguments;

        public MessageRow() {
            this.timestamp = new SimpleStringProperty("0");
            this.address = new SimpleStringProperty("/default");
            this.arguments = FXCollections.observableArrayList();
        }

        public MessageRow(OSCMessageRecord msg) {
            this.timestamp = new SimpleStringProperty(String.valueOf(msg.getTimestamp()));
            this.address = new SimpleStringProperty(msg.getAddress());
            this.arguments = FXCollections.observableArrayList();

            if (msg.getArguments() != null) {
                for (Object arg : msg.getArguments()) {
                    if (arg instanceof Integer) {
                        arguments.add(new ArgumentData("Int", String.valueOf(arg)));
                    } else if (arg instanceof Float || arg instanceof Double) {
                        arguments.add(new ArgumentData("Float", String.valueOf(arg)));
                    } else if (arg instanceof Boolean) {
                        arguments.add(new ArgumentData("Bool", String.valueOf(arg)));
                    } else if (arg instanceof String) {
                        arguments.add(new ArgumentData("String", (String) arg));
                    } else if (arg == null) {
                        arguments.add(new ArgumentData("Infinitum", ""));
                    }
                }
            }
        }

        public String getTimestamp() { return timestamp.get(); }
        public void setTimestamp(String value) { timestamp.set(value); }
        public SimpleStringProperty timestampProperty() { return timestamp; }

        public String getAddress() { return address.get(); }
        public void setAddress(String value) { address.set(value); }
        public SimpleStringProperty addressProperty() { return address; }

        public ObservableList<ArgumentData> getArguments() { return arguments; }

        public OSCMessageRecord toOSCMessageRecord() {
            OSCMessageRecord msg = new OSCMessageRecord();
            msg.setTimestamp(Long.parseLong(timestamp.get()));
            msg.setAddress(address.get());

            List<Object> args = new ArrayList<>();
            StringBuilder typeTagString = new StringBuilder(",");
            for (ArgumentData arg : arguments) {
                switch (arg.getType()) {
                    case "Int":
                        try {
                            args.add(Integer.parseInt(arg.getValue()));
                            typeTagString.append("i");
                        } catch (NumberFormatException e) {
                            args.add(0);
                            typeTagString.append("i");
                        }
                        break;
                    case "Float":
                        try {
                            args.add(Float.parseFloat(arg.getValue()));
                            typeTagString.append("f");
                        } catch (NumberFormatException e) {
                            args.add(0.0f);
                            typeTagString.append("f");
                        }
                        break;
                    case "Bool":
                        boolean boolVal = Boolean.parseBoolean(arg.getValue());
                        args.add(boolVal);
                        typeTagString.append(boolVal ? "T" : "F");
                        break;
                    case "String":
                        args.add(arg.getValue());
                        typeTagString.append("s");
                        break;
                    case "Infinitum":
                        args.add(null);
                        typeTagString.append("I");
                        break;
                }
            }
            msg.setArguments(args.toArray());
            msg.setTypes(typeTagString.toString());

            return msg;
        }
    }

    // Argument data class
    public static class ArgumentData {
        private final SimpleStringProperty type;
        private final SimpleStringProperty value;

        public ArgumentData(String type, String value) {
            this.type = new SimpleStringProperty(type);
            this.value = new SimpleStringProperty(value);
        }

        public String getType() { return type.get(); }
        public void setType(String value) { type.set(value); }
        public SimpleStringProperty typeProperty() { return type; }

        public String getValue() { return value.get(); }
        public void setValue(String value) { this.value.set(value); }
        public SimpleStringProperty valueProperty() { return value; }
    }

    // Custom cell for editable text with highlighting
    private class EditableTextCell extends TableCell<MessageRow, String> {
        private final TextField textField;
        private final String baseStyle = "-fx-font-size: 11px;";
        private final String editedStyle = baseStyle + " -fx-border-color: #FFA500; -fx-border-width: 2px;";

        public EditableTextCell() {
            textField = new TextField();
            textField.setStyle(baseStyle);

            textField.setOnKeyPressed(e -> {
                if (e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                    commitEdit(textField.getText());
                    textField.setStyle(baseStyle);
                } else if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                    cancelEdit();
                    textField.setStyle(baseStyle);
                }
            });

            textField.textProperty().addListener((obs, oldVal, newVal) -> {
                if (!textField.getStyle().contains("-fx-border-color")) {
                    textField.setStyle(editedStyle);
                }
            });
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);

            if (empty) {
                setText(null);
                setGraphic(null);
            } else {
                if (isEditing()) {
                    textField.setText(item);
                    setGraphic(textField);
                    setText(null);
                    textField.requestFocus();
                } else {
                    setText(item);
                    setGraphic(null);
                }
            }
        }

        @Override
        public void startEdit() {
            super.startEdit();
            textField.setText(getItem());
            textField.setStyle(baseStyle);
            setGraphic(textField);
            setText(null);
            textField.selectAll();
            textField.requestFocus();
        }

        @Override
        public void cancelEdit() {
            super.cancelEdit();
            setText(getItem());
            setGraphic(null);
        }
    }

    // Custom cell for arguments with +/- buttons and type selection
    private class ArgumentsCell extends TableCell<MessageRow, MessageRow> {
        private final HBox container;

        public ArgumentsCell() {
            container = new HBox(5);
            container.setAlignment(Pos.CENTER_LEFT);
        }

        @Override
        protected void updateItem(MessageRow row, boolean empty) {
            super.updateItem(row, empty);

            if (empty || row == null) {
                setGraphic(null);
            } else {
                container.getChildren().clear();

                // Add argument controls
                for (int i = 0; i < row.getArguments().size(); i++) {
                    final int index = i;
                    ArgumentData arg = row.getArguments().get(i);

                    ComboBox<String> typeCombo = new ComboBox<>();
                    typeCombo.getItems().addAll("Int", "Float", "Bool", "String", "Infinitum");
                    typeCombo.setValue(arg.getType());
                    typeCombo.setMinWidth(90);

                    TextField valueField = new TextField(arg.getValue());
                    valueField.setPromptText("Value");
                    valueField.setMinWidth(100);
                    setupFieldEditFeedback(valueField, arg);

                    // ComboBox for Boolean values (true/false)
                    ComboBox<String> boolCombo = new ComboBox<>();
                    boolCombo.getItems().addAll("true", "false");
                    boolCombo.setValue(arg.getValue().isEmpty() ? "true" : arg.getValue());
                    boolCombo.setMinWidth(100);
                    boolCombo.setOnAction(e -> {
                        arg.setValue(boolCombo.getValue());
                    });

                    Button removeBtn = new Button("-");
                    removeBtn.setOnAction(e -> {
                        row.getArguments().remove(index);
                        updateItem(row, false);
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
                            // Initialize boolean value if needed
                            if (!arg.getValue().equals("true") && !arg.getValue().equals("false")) {
                                arg.setValue("true");
                                boolCombo.setValue("true");
                            }
                        } else if (!"Infinitum".equals(selectedType)) {
                            valueField.setVisible(true);
                            valueField.setManaged(true);
                        }
                    });

                    // Set initial visibility based on type
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

                    container.getChildren().addAll(typeCombo, valueField, boolCombo, removeBtn);
                }

                // Add + button
                Button addBtn = new Button("+");
                addBtn.setOnAction(e -> {
                    row.getArguments().add(new ArgumentData("Float", "0.0"));
                    updateItem(row, false);
                });
                container.getChildren().add(addBtn);

                setGraphic(container);
            }
        }

        private void setupFieldEditFeedback(TextField field, ArgumentData arg) {
            final String baseStyle = "-fx-font-size: 11px;";
            final String editedStyle = baseStyle + " -fx-border-color: #FFA500; -fx-border-width: 2px;";

            field.textProperty().addListener((obs, oldVal, newVal) -> {
                if (!field.getStyle().contains("-fx-border-color")) {
                    field.setStyle(editedStyle);
                }
            });

            field.setOnKeyPressed(e -> {
                if (e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                    arg.setValue(field.getText());
                    field.setStyle(baseStyle);
                }
            });
        }
    }

    // Custom cell for action buttons
    private class ActionsCell extends TableCell<MessageRow, MessageRow> {
        private final Button deleteBtn;

        public ActionsCell() {
            deleteBtn = new Button("Delete");
            deleteBtn.setOnAction(e -> {
                MessageRow row = getTableRow().getItem();
                if (row != null) {
                    messageData.remove(row);
                }
            });
        }

        @Override
        protected void updateItem(MessageRow row, boolean empty) {
            super.updateItem(row, empty);

            if (empty || row == null) {
                setGraphic(null);
            } else {
                setGraphic(deleteBtn);
            }
        }
    }
}
