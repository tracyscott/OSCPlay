package xyz.theforks.rewrite;

import java.util.ArrayList;
import java.util.List;

import com.illposed.osc.OSCMessage;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

public class PathTrimHandler implements RewriteHandler {
    private String addressPattern;

    @Override
    public String getAddressPattern() {
        return addressPattern;
    }

    @Override
    public String label() {
        return "PathTrim";
    }

    @Override
    public String getHelp() {
        return "Removes the last component from the OSC path and adds it as a String argument";
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
            throw new IllegalArgumentException("PathTrimHandler requires one argument");
        }
        addressPattern = args[0];
        return true;
    }

    @Override
    public OSCMessage process(OSCMessage message) {
        String addr = message.getAddress();
        if (addr.matches(addressPattern)) {
            // Find the last slash in the address
            int lastSlashIndex = addr.lastIndexOf('/');
            
            if (lastSlashIndex > 0) { // Don't trim if it's the root slash
                String trimmedPath = addr.substring(0, lastSlashIndex);
                String lastComponent = addr.substring(lastSlashIndex + 1);
                
                // Create new argument list with the last component added as first argument
                List<Object> newArguments = new ArrayList<>();
                newArguments.add(lastComponent);
                newArguments.addAll(message.getArguments());
                
                return new OSCMessage(trimmedPath, newArguments);
            }
        }
        
        // Return original message if it doesn't match our criteria or has no trimmable path
        return message;
    }

    @Override
    public void showPreferences() {
        Stage stage = new Stage();
        stage.setTitle("PathTrim Handler Preferences");

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
        Label helpLabel = new Label("Removes the last component from OSC paths and adds it as a String argument.\n" +
                                   "Example: '/synth/osc1/freq' with args [440.0] becomes\n" +
                                   "         '/synth/osc1' with args ['freq', 440.0]\n" +
                                   "Use patterns like '/synth/.*/.*' to match nested paths.");
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