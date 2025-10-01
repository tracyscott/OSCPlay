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
 * Window for debugging node chain processing.
 * Displays each step of message processing through the node chain.
 */
public class NodeChainDebugWindow {

    private Stage stage;
    private TableView<DebugRow> tableView;
    private ObservableList<DebugRow> debugEntries;
    private final String outputId;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS");
    private boolean isOpen = false;

    /**
     * Create a debug window for a specific output's node chain.
     * @param outputId The ID of the output to debug
     */
    public NodeChainDebugWindow(String outputId) {
        this.outputId = outputId;
        this.debugEntries = FXCollections.observableArrayList();
    }

    /**
     * Show the debug window.
     */
    public void show() {
        if (isOpen) {
            stage.toFront();
            return;
        }

        stage = new Stage();
        stage.setTitle("Node Chain Debug - Output: " + outputId);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        // Create table
        tableView = new TableView<>();
        tableView.setItems(debugEntries);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // Timestamp column
        TableColumn<DebugRow, String> timeCol = new TableColumn<>("Timestamp");
        timeCol.setCellValueFactory(new PropertyValueFactory<>("timestamp"));
        timeCol.setPrefWidth(100);
        timeCol.setMinWidth(100);
        timeCol.setMaxWidth(120);

        // Step column (Raw, Node Name)
        TableColumn<DebugRow, String> stepCol = new TableColumn<>("Step");
        stepCol.setCellValueFactory(new PropertyValueFactory<>("step"));
        stepCol.setPrefWidth(150);
        stepCol.setMinWidth(120);

        // Address column
        TableColumn<DebugRow, String> addressCol = new TableColumn<>("Address");
        addressCol.setCellValueFactory(new PropertyValueFactory<>("address"));
        addressCol.setPrefWidth(200);
        addressCol.setMinWidth(150);

        // Arguments column
        TableColumn<DebugRow, String> argsCol = new TableColumn<>("Arguments");
        argsCol.setCellValueFactory(new PropertyValueFactory<>("arguments"));
        argsCol.setPrefWidth(300);
        argsCol.setMinWidth(200);

        tableView.getColumns().add(timeCol);
        tableView.getColumns().add(stepCol);
        tableView.getColumns().add(addressCol);
        tableView.getColumns().add(argsCol);

        root.setCenter(tableView);

        // Control buttons
        HBox controls = new HBox(10);
        controls.setPadding(new Insets(10, 0, 0, 0));

        Button clearButton = new Button("Clear");
        clearButton.setOnAction(e -> debugEntries.clear());

        Button closeButton = new Button("Close");
        closeButton.setOnAction(e -> close());

        controls.getChildren().addAll(clearButton, closeButton);
        root.setBottom(controls);

        Scene scene = new Scene(root, 800, 600);
        Theme.applyDark(scene);
        stage.setScene(scene);

        stage.setOnCloseRequest(e -> {
            isOpen = false;
        });

        isOpen = true;
        stage.show();
    }

    /**
     * Close the debug window.
     */
    public void close() {
        if (stage != null) {
            isOpen = false;
            stage.close();
        }
    }

    /**
     * Check if the debug window is currently open.
     */
    public boolean isOpen() {
        return isOpen;
    }

    /**
     * Add a debug entry showing the raw input message.
     * @param message The raw OSC message before processing
     */
    public void addRawMessage(OSCMessage message) {
        if (!isOpen || message == null) {
            return;
        }

        Platform.runLater(() -> {
            String timestamp = timeFormat.format(new Date());
            String address = message.getAddress();
            String arguments = formatArguments(message.getArguments());

            debugEntries.add(new DebugRow(timestamp, "Raw Input", address, arguments));
            scrollToBottom();
            limitEntries();
        });
    }

    /**
     * Add a debug entry showing the output of a specific node.
     * @param nodeName The name of the node
     * @param message The message after processing by this node (or null if dropped)
     */
    public void addNodeOutput(String nodeName, OSCMessage message) {
        if (!isOpen) {
            return;
        }

        Platform.runLater(() -> {
            String timestamp = timeFormat.format(new Date());
            String address = message != null ? message.getAddress() : "(dropped)";
            String arguments = message != null ? formatArguments(message.getArguments()) : "";

            debugEntries.add(new DebugRow(timestamp, nodeName, address, arguments));
            scrollToBottom();
            limitEntries();
        });
    }

    /**
     * Auto-scroll to bottom of table.
     */
    private void scrollToBottom() {
        if (!debugEntries.isEmpty()) {
            tableView.scrollTo(debugEntries.size() - 1);
        }
    }

    /**
     * Limit entries to prevent memory issues.
     */
    private void limitEntries() {
        if (debugEntries.size() > 1000) {
            debugEntries.remove(0);
        }
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
    public static class DebugRow {
        private final String timestamp;
        private final String step;
        private final String address;
        private final String arguments;

        public DebugRow(String timestamp, String step, String address, String arguments) {
            this.timestamp = timestamp;
            this.step = step;
            this.address = address;
            this.arguments = arguments;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public String getStep() {
            return step;
        }

        public String getAddress() {
            return address;
        }

        public String getArguments() {
            return arguments;
        }
    }
}
