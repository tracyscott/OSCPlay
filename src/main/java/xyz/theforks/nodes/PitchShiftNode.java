package xyz.theforks.nodes;

import java.util.Arrays;

import com.illposed.osc.OSCMessage;

public class PitchShiftNode implements OSCNode {

    private String addressPattern;

    @Override
    public String getAddressPattern() {
        // /note/.* matches all note messages
        return addressPattern;
    }

    @Override
    public void process(java.util.List<xyz.theforks.model.MessageRequest> requests) {
        OSCMessage message = inputMessage(requests);
        if (message == null) return;

        Object[] arguments = message.getArguments().toArray();
        if (arguments.length > 0 && arguments[0] instanceof Integer) {
            arguments[0] = (Integer)arguments[0] + 12; // Shift up one octave
            OSCMessage shiftedMessage = new OSCMessage(message.getAddress(), Arrays.asList(arguments));
            replaceMessage(requests, shiftedMessage);
        }
        // Otherwise pass through unchanged
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
            throw new IllegalArgumentException("PitchShiftNode requires one argument");
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
