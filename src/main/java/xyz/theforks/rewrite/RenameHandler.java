package xyz.theforks.rewrite;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.illposed.osc.OSCMessage;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class RenameHandler implements RewriteHandler {
    private String addressPattern;
    private String regex;
    private Pattern regexPattern;
    private String replaceString;

    @Override
    public String getAddressPattern() {
        // /note/.* matches all note messages
        return addressPattern;
    }

    @Override
    public String label() {
        return "Rename";
    }

    @Override
    public String getHelp() {
        return "Renames an address based on a regular expression";
    }

    @Override
    public int getNumArgs() {
        return 3;
    }

	@Override
	public String[] getArgs() {
		return new String[] { addressPattern, regex, replaceString };
	}

	@Override
	public String[] getArgNames() {
        return new String[] { "Address Pattern", "Regex", "Replace With" };
    }

    @Override
    public boolean configure(String[] args) {
        // Requires one argument that is the address pattern to match.
        if (args.length != 3) {
            throw new IllegalArgumentException("RenameHandler requires three arguments");
        }
        addressPattern = args[0];
        regex = args[1];
        replaceString = args[2];
        // Use java.util.regex.Pattern to validate the regex
        regexPattern = Pattern.compile(regex);

        return true;
    }

    @Override
    public OSCMessage process(OSCMessage message) {
        Object[] arguments = message.getArguments().toArray();
        String addr = message.getAddress();
        if (addr.matches(addressPattern)) {
            addr = regexPattern.matcher(addr).replaceAll(replaceString);
            return new OSCMessage(addr, Arrays.asList(arguments));
        }
        return message;
    }

	@Override
	public void showPreferences() {
	    Stage stage = new Stage();
	    stage.setTitle("Rename Handler Preferences");

	    GridPane grid = new GridPane();
	    grid.setPadding(new Insets(10));
	    grid.setHgap(10);
	    grid.setVgap(10);

	    // Regex input
	    Label regexLabel = new Label("Regex Pattern:");
	    TextField regexField = new TextField(regexPattern.pattern());
	    grid.add(regexLabel, 0, 0);
	    grid.add(regexField, 1, 0);

	    // Test string input
	    Label testLabel = new Label("Test String:");
	    TextField testField = new TextField("/test/input/123");
	    grid.add(testLabel, 0, 1);
	    grid.add(testField, 1, 1);

	    // Replacement string input
	    Label replaceLabel = new Label("Replace With:");
	    TextField replaceField = new TextField(replaceString);
	    grid.add(replaceLabel, 0, 2);
	    grid.add(replaceField, 1, 2);

	    // Result label
	    Label resultLabel = new Label();
	    resultLabel.setWrapText(true);
	    grid.add(resultLabel, 0, 4, 2, 1);

	    // Match button
	    Button matchButton = new Button("Match");
	    matchButton.setOnAction(e -> {
	        try {
	            Pattern pattern = Pattern.compile(regexField.getText());
	            String result = pattern.matcher(testField.getText())
	                                 .replaceAll(replaceField.getText());
	            resultLabel.setText("Result: " + result);
	            resultLabel.setTextFill(Color.BLACK);
	        } catch (PatternSyntaxException ex) {
	            resultLabel.setText("Invalid regex pattern: " + ex.getMessage());
	            resultLabel.setTextFill(Color.RED);
	        }
	    });
	    grid.add(matchButton, 0, 3);

	    // Save button
	    Button saveButton = new Button("Save");
	    saveButton.setOnAction(e -> {
	        try {
	            Pattern pattern = Pattern.compile(regexField.getText());
	            regexPattern = pattern;
	            replaceString = replaceField.getText();
	            stage.close();
	        } catch (PatternSyntaxException ex) {
	            resultLabel.setText("Invalid regex pattern: " + ex.getMessage());
	            resultLabel.setTextFill(Color.RED);
	        }
	    });
	    grid.add(saveButton, 1, 3);

	    Scene scene = new Scene(grid);
	    stage.setScene(scene);
	    stage.show();
	}
}
