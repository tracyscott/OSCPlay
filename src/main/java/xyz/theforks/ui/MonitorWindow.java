package xyz.theforks.ui;

import com.illposed.osc.OSCMessage;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Window for monitoring OSC messages being sent through an output.
 * Displays timestamp, address, and arguments in a grid format.
 */
public class MonitorWindow {

    private Stage stage;
    private TableView<MessageRow> tableView;
    private ObservableList<MessageRow> messages;
    private final String outputId;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS");
    private boolean isOpen = false;

    /**
     * Create a monitor window for a specific output.
     * @param outputId The ID of the output to monitor
     */
    public MonitorWindow(String outputId) {
        this.outputId = outputId;
        this.messages = FXCollections.observableArrayList();
    }

    /**
     * Show the monitor window.
     */
    public void show() {
        if (isOpen) {
            stage.toFront();
            return;
        }

        stage = new Stage();
        stage.setTitle("Monitor - Output: " + outputId);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        // Create table
        tableView = new TableView<>();
        tableView.setItems(messages);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // Timestamp column
        TableColumn<MessageRow, String> timeCol = new TableColumn<>("Timestamp");
        timeCol.setCellValueFactory(new PropertyValueFactory<>("timestamp"));
        timeCol.setPrefWidth(100);
        timeCol.setMinWidth(100);
        timeCol.setMaxWidth(120);

        // Address column
        TableColumn<MessageRow, String> addressCol = new TableColumn<>("Address");
        addressCol.setCellValueFactory(new PropertyValueFactory<>("address"));
        addressCol.setPrefWidth(200);
        addressCol.setMinWidth(150);

        // Arguments column
        TableColumn<MessageRow, String> argsCol = new TableColumn<>("Arguments");
        argsCol.setCellValueFactory(new PropertyValueFactory<>("arguments"));
        argsCol.setPrefWidth(300);
        argsCol.setMinWidth(200);

        tableView.getColumns().add(timeCol);
        tableView.getColumns().add(addressCol);
        tableView.getColumns().add(argsCol);

        root.setCenter(tableView);

        // Control buttons
        HBox controls = new HBox(10);
        controls.setPadding(new Insets(10, 0, 0, 0));

        Button clearButton = new Button("Clear");
        clearButton.setOnAction(e -> messages.clear());

        Button closeButton = new Button("Close");
        closeButton.setOnAction(e -> close());

        controls.getChildren().addAll(clearButton, closeButton);
        root.setBottom(controls);

        Scene scene = new Scene(root, 700, 500);
        Theme.applyDark(scene);
        stage.setScene(scene);

        stage.setOnCloseRequest(e -> {
            isOpen = false;
        });

        isOpen = true;
        stage.show();
    }

    /**
     * Close the monitor window.
     */
    public void close() {
        if (stage != null) {
            isOpen = false;
            stage.close();
        }
    }

    /**
     * Check if the monitor window is currently open.
     */
    public boolean isOpen() {
        return isOpen;
    }

    /**
     * Add a message to the monitor display.
     * @param message The OSC message to display
     */
    public void addMessage(OSCMessage message) {
        if (!isOpen) {
            return;
        }

        Platform.runLater(() -> {
            String timestamp = timeFormat.format(new Date());
            String address = message.getAddress();
            String arguments = formatArguments(message.getArguments());

            messages.add(new MessageRow(timestamp, address, arguments));

            // Auto-scroll to bottom
            if (!messages.isEmpty()) {
                tableView.scrollTo(messages.size() - 1);
            }

            // Limit to 1000 messages to prevent memory issues
            if (messages.size() > 1000) {
                messages.remove(0);
            }
        });
    }

    /**
     * Format message arguments for display.
     */
    private String formatArguments(List<Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arguments.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            Object arg = arguments.get(i);
            if (arg instanceof Float) {
                sb.append(String.format("%.4f", (Float) arg));
            } else if (arg instanceof Double) {
                sb.append(String.format("%.4f", (Double) arg));
            } else {
                sb.append(arg.toString());
            }
        }
        return sb.toString();
    }

    /**
     * Data class for table rows.
     */
    public static class MessageRow {
        private final String timestamp;
        private final String address;
        private final String arguments;

        public MessageRow(String timestamp, String address, String arguments) {
            this.timestamp = timestamp;
            this.address = address;
            this.arguments = arguments;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public String getAddress() {
            return address;
        }

        public String getArguments() {
            return arguments;
        }
    }
}
