package xyz.theforks.nodes;

import com.illposed.osc.OSCMessage;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import xyz.theforks.model.MessageRequest;

import java.util.List;

public class DelayNode implements OSCNode {
    private String addressPattern;
    private long delayMs;

    @Override
    public String getAddressPattern() {
        return addressPattern;
    }

    @Override
    public String label() {
        return "Delay";
    }

    @Override
    public String getHelp() {
        return "Delays messages by a specified number of milliseconds";
    }

    @Override
    public int getNumArgs() {
        return 2;
    }

    @Override
    public String[] getArgs() {
        return new String[] { addressPattern, String.valueOf(delayMs) };
    }

    @Override
    public String[] getArgNames() {
        return new String[] { "Address Pattern", "Delay (ms)" };
    }

    @Override
    public boolean configure(String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("DelayNode requires two arguments: address pattern and delay in milliseconds");
        }
        addressPattern = args[0];
        try {
            delayMs = Long.parseLong(args[1]);
            if (delayMs < 0) {
                throw new IllegalArgumentException("Delay must be non-negative");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Delay must be a valid number: " + args[1]);
        }
        return true;
    }

    @Override
    public void process(List<MessageRequest> requests) {
        if (requests.isEmpty()) return;

        MessageRequest currentRequest = requests.get(0);
        OSCMessage message = currentRequest.getMessage();
        if (message == null) return;

        String addr = message.getAddress();
        if (addr.matches(addressPattern)) {
            // Check if this message was previously delayed (to prevent infinite loop)
            if (currentRequest.wasPreviouslyDelayed()) {
                //System.out.println("DelayNode: Message " + addr + " was previously delayed (" +
                //    currentRequest.getPreviousDelay() + "ms), passing through without re-delaying");
                // Pass through unchanged - don't apply delay again
            } else {
                // System.out.println("DelayNode: Applying " + delayMs + "ms delay to " + addr);
                // Apply the delay
                MessageRequest delayedRequest = new MessageRequest(message, delayMs);
                replaceMessage(requests, delayedRequest);
            }
        }
        // Pass through unchanged if doesn't match pattern
    }

    @Override
    public void showPreferences() {
        Stage stage = new Stage();
        stage.setTitle("Delay Node Preferences");

        GridPane grid = new GridPane();
        grid.setPadding(new Insets(10));
        grid.setHgap(10);
        grid.setVgap(10);

        // Address Pattern input
        Label patternLabel = new Label("Address Pattern:");
        TextField patternField = new TextField(addressPattern);
        grid.add(patternLabel, 0, 0);
        grid.add(patternField, 1, 0);

        // Delay input
        Label delayLabel = new Label("Delay (ms):");
        TextField delayField = new TextField(String.valueOf(delayMs));
        grid.add(delayLabel, 0, 1);
        grid.add(delayField, 1, 1);

        // Result/error label
        Label resultLabel = new Label();
        resultLabel.setWrapText(true);
        grid.add(resultLabel, 0, 3, 2, 1);

        // Save button
        Button saveButton = new Button("Save");
        saveButton.setOnAction(e -> {
            try {
                long newDelay = Long.parseLong(delayField.getText());
                if (newDelay < 0) {
                    resultLabel.setText("Error: Delay must be non-negative");
                    return;
                }
                addressPattern = patternField.getText();
                delayMs = newDelay;
                stage.close();
            } catch (NumberFormatException ex) {
                resultLabel.setText("Error: Delay must be a valid number");
            }
        });
        grid.add(saveButton, 1, 2);

        // Cancel button
        Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(e -> stage.close());
        grid.add(cancelButton, 0, 2);

        Scene scene = new Scene(grid);
        stage.setScene(scene);
        stage.show();
    }
}
