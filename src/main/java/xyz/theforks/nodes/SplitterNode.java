package xyz.theforks.nodes;

import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCMessageInfo;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class SplitterNode implements OSCNode {
    private static final String MATCH_ALL = ".*";

    private String addressPattern = MATCH_ALL;

    @Override
    public String getAddressPattern() {
        return addressPattern;
    }

    @Override
    public String label() {
        return "Splitter";
    }

    @Override
    public String getHelp() {
        return "Splits multi-argument OSC messages matching the address pattern into separate single-argument messages with incremented addresses";
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

    /**
     * Configure with an optional address pattern. No args, or a blank pattern, splits
     * every message; chains saved before the pattern existed have no args.
     */
    @Override
    public boolean configure(String[] args) {
        if (args.length > 1) {
            throw new IllegalArgumentException("SplitterNode takes at most one argument: address pattern");
        }
        String pattern = args.length == 0 || args[0] == null || args[0].isBlank() ? MATCH_ALL : args[0];
        Pattern.compile(pattern); // Throws PatternSyntaxException (an IllegalArgumentException) if invalid
        addressPattern = pattern;
        return true;
    }

    @Override
    public void process(java.util.List<xyz.theforks.model.MessageRequest> requests) {
        OSCMessage message = inputMessage(requests);
        if (message == null) return;

        List<Object> arguments = message.getArguments();

        // Pass through messages that don't match, or that have 0 or 1 arguments
        if (!message.getAddress().matches(addressPattern) || arguments.size() <= 1) {
            return;
        }

        String baseAddress = message.getAddress();
        OSCMessageInfo info = message.getInfo();

        // Drop the original message
        dropMessage(requests);

        // Create new messages for each argument
        for (int i = 0; i < arguments.size(); i++) {
            String newAddress = baseAddress + (i + 1);
            Object arg = arguments.get(i);

            // Create message with single argument and appropriate type tag
            OSCMessage newMessage;
            if (info != null) {
                // Preserve OSC type tag information
                newMessage = new OSCMessage(newAddress, List.of(arg), info);
            } else {
                // No type tag info available, create simple message
                newMessage = new OSCMessage(newAddress, List.of(arg));
            }

            addMessage(requests, newMessage);
        }
    }

    @Override
    public void showPreferences() {
        Stage stage = new Stage();
        stage.setTitle("Splitter Node Preferences");

        GridPane grid = new GridPane();
        grid.setPadding(new Insets(10));
        grid.setHgap(10);
        grid.setVgap(10);

        // Address pattern input
        Label patternLabel = new Label("Address Pattern:");
        TextField patternField = new TextField(addressPattern);
        grid.add(patternLabel, 0, 0);
        grid.add(patternField, 1, 0);

        // Help text
        Label helpLabel = new Label(
            "Splits multi-argument OSC messages matching the pattern (regex, '.*' for all)\n" +
            "into separate single-argument messages.\n" +
            "Original address: /foo with args [1, 2, 3]\n" +
            "Creates:\n" +
            "  /foo1 with arg [1]\n" +
            "  /foo2 with arg [2]\n" +
            "  /foo3 with arg [3]\n\n" +
            "Messages with 0 or 1 arguments pass through unchanged."
        );
        helpLabel.setWrapText(true);
        grid.add(helpLabel, 0, 1, 2, 1);

        // Save button
        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: red;");
        Button saveButton = new Button("Save");
        saveButton.setOnAction(e -> {
            try {
                configure(new String[] { patternField.getText() });
                stage.close();
            } catch (IllegalArgumentException ex) {
                errorLabel.setText("Invalid pattern: " + ex.getMessage());
            }
        });
        grid.add(saveButton, 1, 2);
        grid.add(errorLabel, 0, 3, 2, 1);

        Scene scene = new Scene(grid);
        xyz.theforks.ui.Theme.applyDark(scene);
        stage.setScene(scene);
        stage.show();
    }
}
