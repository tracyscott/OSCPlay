package xyz.theforks.nodes;

import com.illposed.osc.OSCMessage;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

public class PassNode implements OSCNode {
    private String addressPattern;

    @Override
    public String getAddressPattern() {
        return addressPattern;
    }

    @Override
    public String label() {
        return "Pass";
    }

    @Override
    public String getHelp() {
        return "Passes only OSC messages that match the given address pattern, drops all others";
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
            throw new IllegalArgumentException("PassNode requires one argument");
        }
        addressPattern = args[0];
        return true;
    }

    @Override
    public OSCMessage process(OSCMessage message) {
        String addr = message.getAddress();
        if (addr.matches(addressPattern)) {
            // Pass the message through
            return message;
        }
        // Drop the message by returning null
        return null;
    }

    @Override
    public void showPreferences() {
        Stage stage = new Stage();
        stage.setTitle("Pass Node Preferences");

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
        Label helpLabel = new Label("Passes only messages matching the pattern (regex), drops all others.\n" +
                                   "Example: '/synth/.*' passes only messages under /synth\n" +
                                   "         '/control/(volume|pan)' passes only volume and pan controls");
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
