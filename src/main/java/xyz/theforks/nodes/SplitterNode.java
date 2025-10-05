package xyz.theforks.nodes;

import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCMessageInfo;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

public class SplitterNode implements OSCNode {

    @Override
    public String getAddressPattern() {
        return ".*";
    }

    @Override
    public String label() {
        return "Splitter";
    }

    @Override
    public String getHelp() {
        return "Splits multi-argument OSC messages into separate single-argument messages with incremented addresses";
    }

    @Override
    public int getNumArgs() {
        return 0;
    }

    @Override
    public String[] getArgs() {
        return new String[0];
    }

    @Override
    public String[] getArgNames() {
        return new String[0];
    }

    @Override
    public boolean configure(String[] args) {
        if (args.length != 0) {
            throw new IllegalArgumentException("SplitterNode requires no arguments");
        }
        return true;
    }

    @Override
    public void process(java.util.List<xyz.theforks.model.MessageRequest> requests) {
        OSCMessage message = inputMessage(requests);
        if (message == null) return;

        List<Object> arguments = message.getArguments();

        // If message has 0 or 1 arguments, pass through unchanged
        if (arguments.size() <= 1) {
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

        // Help text
        Label helpLabel = new Label(
            "Splits multi-argument OSC messages into separate single-argument messages.\n" +
            "Original address: /foo with args [1, 2, 3]\n" +
            "Creates:\n" +
            "  /foo1 with arg [1]\n" +
            "  /foo2 with arg [2]\n" +
            "  /foo3 with arg [3]\n\n" +
            "Messages with 0 or 1 arguments pass through unchanged."
        );
        helpLabel.setWrapText(true);
        grid.add(helpLabel, 0, 0);

        // Close button
        Button closeButton = new Button("Close");
        closeButton.setOnAction(e -> stage.close());
        grid.add(closeButton, 0, 1);

        Scene scene = new Scene(grid);
        xyz.theforks.ui.Theme.applyDark(scene);
        stage.setScene(scene);
        stage.show();
    }
}
