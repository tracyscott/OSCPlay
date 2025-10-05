package xyz.theforks.nodes;

import com.illposed.osc.OSCMessage;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

public class DropNode implements OSCNode {
    private String addressPattern;

    @Override
    public String getAddressPattern() {
        return addressPattern;
    }

    @Override
    public String label() {
        return "Drop";
    }

    @Override
    public String getHelp() {
        return "Drops (filters out) OSC messages that match the given address pattern";
    }

    @Override
    public int getNumArgs() {
        return 1;
    }

    @Override
    public String[] getArgs() {
        return new String[] { addressPattern };
    }

    @Override
    public String[] getArgNames() {
        return new String[] { "Address Pattern" };
    }

    @Override
    public boolean configure(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("DropNode requires one argument");
        }
        addressPattern = args[0];
        return true;
    }

    @Override
    public void process(java.util.List<xyz.theforks.model.MessageRequest> requests) {
        OSCMessage message = inputMessage(requests);
        if (message == null) return;

        String addr = message.getAddress();
        if (addr.matches(addressPattern)) {
            // Drop messages that match
            dropMessage(requests);
        }
        // Otherwise pass through unchanged (do nothing)
    }

    @Override
    public void showPreferences() {
        Stage stage = new Stage();
        stage.setTitle("Drop Node Preferences");

        GridPane grid = new GridPane();
        grid.setPadding(new Insets(10));
        grid.setHgap(10);
        grid.setVgap(10);

        // Address pattern input
        Label patternLabel = new Label("Address Pattern:");
        TextField patternField = new TextField(addressPattern != null ? addressPattern : "");
        grid.add(patternLabel, 0, 0);
        grid.add(patternField, 1, 0);

        // Help text
        Label helpLabel = new Label("Drops messages matching the pattern (regex).\n" +
                                   "Example: '/debug/.*' drops all messages under /debug\n" +
                                   "         '/synth/osc[12]/.*' drops messages for osc1 and osc2");
        helpLabel.setWrapText(true);
        grid.add(helpLabel, 0, 1, 2, 1);

        // Save button
        Button saveButton = new Button("Save");
        saveButton.setOnAction(e -> {
            addressPattern = patternField.getText();
            stage.close();
        });
        grid.add(saveButton, 1, 2);

        Scene scene = new Scene(grid);
        stage.setScene(scene);
        stage.show();
    }
}
