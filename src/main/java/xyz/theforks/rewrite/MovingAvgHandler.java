package xyz.theforks.rewrite;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import com.illposed.osc.OSCMessage;

public class MovingAvgHandler implements RewriteHandler {
    private String addressPattern;
    private int windowSize;
    private Map<String, Queue<Float>> windows = new HashMap<>();

    @Override
    public String getAddressPattern() {
        return addressPattern;
    }

    @Override
    public String label() {
        return "Moving Average";
    }

    @Override
    public String getHelp() {
        return "Applies a moving average filter with configurable window size";
    }

    @Override
    public int getNumArgs() {
        return 2;
    }

    @Override
    public boolean configure(String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("MovingAvgHandler requires two arguments");
        }
        addressPattern = args[0];
        try {
            windowSize = Integer.parseInt(args[1]);
            if (windowSize < 1) {
                throw new IllegalArgumentException("Window size must be positive");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Window size must be an integer");
        }
        return true;
    }

    @Override
    public OSCMessage process(OSCMessage message) {
        List<Object> arguments = message.getArguments();
        if (arguments.size() != 1 || !(arguments.get(0) instanceof Float)) {
            return message;
        }

        String addr = message.getAddress();
        float value = (Float) arguments.get(0);

        Queue<Float> window = windows.computeIfAbsent(addr, k -> new LinkedList<>());
        window.offer(value);
        
        if (window.size() > windowSize) {
            window.poll();
        }

        float sum = 0;
        for (float v : window) {
            sum += v;
        }
        float average = sum / window.size();

        return new OSCMessage(addr, List.of(average));
    }

    @Override
    public void showPreferences() {
        // No preferences UI needed
    }

    @Override
    public String[] getArgs() {
        return new String[] { addressPattern, String.valueOf(windowSize) };
    }

    @Override
	public String[] getArgNames() {
        return new String[] { "Address Pattern", "Window Size" };
    }
}


