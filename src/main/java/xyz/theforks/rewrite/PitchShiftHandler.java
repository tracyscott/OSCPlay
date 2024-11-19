package xyz.theforks.rewrite;

import java.util.Arrays;

import com.illposed.osc.OSCMessage;

public class PitchShiftHandler implements RewriteHandler {

    private String addressPattern;
    
    @Override
    public String getAddressPattern() {
        // /note/.* matches all note messages
        return addressPattern;
    }

    @Override
    public OSCMessage process(OSCMessage message) {
        Object[] arguments = message.getArguments().toArray();
        if (arguments.length > 0 && arguments[0] instanceof Integer) {
            arguments[0] = (Integer)arguments[0] + 12; // Shift up one octave
            return new OSCMessage(message.getAddress(), Arrays.asList(arguments));
        }
        return message;
    }

    @Override
    public String label() {
        return "Pitch Shift";
    }

    @Override
    public String getHelp() {
        return "Shifts the pitch of incoming notes up one octave";
    }

    @Override
    public int getNumArgs() {
        return 1;
    }

    @Override
    public String[] getArgNames() {
        return new String[] { "Address Pattern" };
    }

    @Override
    public boolean configure(String[] args) {
        // Requires one argument that is the address pattern to match.
        if (args.length != 1) {
            throw new IllegalArgumentException("PitchShiftHandler requires one argument");
        }
        addressPattern = args[0];
        return true;
    }

    @Override
    public void showPreferences() {
        // Nothing to do
    }

    @Override
    public String[] getArgs() {
        return new String[] { addressPattern };
    }
}

