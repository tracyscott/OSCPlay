package xyz.theforks.nodes;

import java.util.ArrayList;
import java.util.List;

import com.illposed.osc.OSCMessage;

/**
 * Linearly remaps numeric arguments from one range to another, emitting float32.
 *
 * Every numeric argument (float32, float64, int32 or int64) is mapped from the
 * input range onto the output range and written out as float32, so downstream
 * nodes that expect floats, such as MovingAvgNode, keep working. Non-numeric
 * arguments pass through untouched.
 */
public class RemapNodeF implements OSCNode {
    private String addressPattern;
    private float inMin;
    private float inMax;
    private float outMin;
    private float outMax;
    private boolean clamp;

    @Override
    public String getAddressPattern() {
        return addressPattern;
    }

    @Override
    public String label() {
        return "Remap Float";
    }

    @Override
    public String getHelp() {
        return "Remaps numeric arguments from one range to another as float32";
    }

    @Override
    public int getNumArgs() {
        return 6;
    }

    @Override
    public boolean configure(String[] args) {
        if (args.length != 6) {
            throw new IllegalArgumentException("RemapNodeF requires six arguments");
        }
        addressPattern = args[0];
        inMin = parseFloat(args[1], "In Min");
        inMax = parseFloat(args[2], "In Max");
        outMin = parseFloat(args[3], "Out Min");
        outMax = parseFloat(args[4], "Out Max");
        clamp = Boolean.parseBoolean(args[5]);
        return true;
    }

    private static float parseFloat(String value, String name) {
        try {
            float parsed = Float.parseFloat(value);
            if (!Float.isFinite(parsed)) {
                throw new IllegalArgumentException(name + " must be a finite number");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be a number");
        }
    }

    /**
     * Map one value from the input range onto the output range.
     */
    float remap(float value) {
        float span = inMax - inMin;
        // A zero-width input range has no meaningful mapping; pin to the low end.
        float t = (span == 0f) ? 0f : (value - inMin) / span;
        if (clamp) {
            t = Math.max(0f, Math.min(1f, t));
        }
        return outMin + t * (outMax - outMin);
    }

    @Override
    public void process(java.util.List<xyz.theforks.model.MessageRequest> requests) {
        OSCMessage message = inputMessage(requests);
        if (message == null) return;

        if (!message.getAddress().matches(addressPattern)) {
            return; // Pass through unchanged
        }

        List<Object> arguments = message.getArguments();
        List<Object> remapped = new ArrayList<>(arguments.size());
        boolean changed = false;
        for (Object argument : arguments) {
            if (argument instanceof Number) {
                remapped.add(remap(((Number) argument).floatValue()));
                changed = true;
            } else {
                remapped.add(argument);
            }
        }

        if (!changed) {
            return; // Nothing numeric to remap
        }

        replaceMessage(requests, new OSCMessage(message.getAddress(), remapped));
    }

    @Override
    public void showPreferences() {
        // No preferences UI needed
    }

    @Override
    public String[] getArgs() {
        return new String[] { addressPattern, String.valueOf(inMin), String.valueOf(inMax),
            String.valueOf(outMin), String.valueOf(outMax), String.valueOf(clamp) };
    }

    @Override
    public String[] getArgNames() {
        return new String[] { "Address Pattern", "In Min", "In Max", "Out Min", "Out Max", "Clamp" };
    }
}
