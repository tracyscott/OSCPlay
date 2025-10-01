package xyz.theforks.nodes;

import java.util.ArrayList;
import java.util.List;

import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCMessageInfo;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

public class IntToBangNode implements OSCNode {
    private String addressPattern;

    @Override
    public String getAddressPattern() {
        return addressPattern;
    }

    @Override
    public String label() {
        return "IntToBang";
    }

    @Override
    public String getHelp() {
        return "Converts OSC messages with integer argument 1 to argumentless messages, drops others";
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
            throw new IllegalArgumentException("IntToBangNode requires one argument");
        }
        addressPattern = args[0];
        return true;
    }

    @Override
    public OSCMessage process(OSCMessage message) {
        String addr = message.getAddress();
        if (addr.matches(addressPattern)) {
            List<Object> arguments = message.getArguments();

            // Check if message has exactly one argument and it's an integer
            if (arguments.size() == 1 && arguments.get(0) instanceof Integer) {
                Integer intValue = (Integer) arguments.get(0);

                // If integer is 1, forward message with no arguments
                if (intValue == 1) {
                    OSCMessage msg = new OSCMessage(addr, new ArrayList<>());
                    msg.setInfo(new OSCMessageInfo("i"));
                    return msg;
                } else {
                    // Drop the message by returning null
                    return null;
                }
            }
        }

        // Return original message if it doesn't match our criteria
        return message;
    }

    @Override
    public void showPreferences() {
        Stage stage = new Stage();
        stage.setTitle("IntToBang Node Preferences");

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
        Label helpLabel = new Label("Converts messages with integer argument 1 to argumentless messages.\n" +
                                   "Messages with other integer values are dropped.\n" +
                                   "Use patterns like '/trigger/.*' to match multiple addresses.");
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
