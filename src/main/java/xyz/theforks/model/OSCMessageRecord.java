package xyz.theforks.model;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OSCMessageRecord {
    private String address;
    private Object[] arguments;
    private long timestamp;
    private String types;  // OSC Type Tag String (e.g., ",f", ",fs", ",I")

    public OSCMessageRecord() {
        // Default constructor for Jackson
    }

    public OSCMessageRecord(String address, Object[] arguments) {
        this.address = address;
        this.arguments = arguments;
        this.timestamp = Instant.now().toEpochMilli();
        this.types = generateTypeTagString(arguments);
    }

    public OSCMessageRecord(String address, Object[] arguments, String types) {
        this.address = address;
        this.arguments = arguments;
        this.timestamp = Instant.now().toEpochMilli();
        this.types = types;
    }

    /**
     * Generate OSC Type Tag String from arguments.
     * Standard types: i (int32), f (float32), s (string), b (blob)
     * Nonstandard: I (Infinitum/Impulse), T (True), F (False), N (Nil), etc.
     */
    private String generateTypeTagString(Object[] args) {
        if (args == null || args.length == 0) {
            return ",";
        }

        StringBuilder sb = new StringBuilder(",");
        for (Object arg : args) {
            if (arg == null) {
                sb.append("I");  // Infinitum (Impulse)
            } else if (arg instanceof Integer) {
                sb.append("i");
            } else if (arg instanceof Float) {
                sb.append("f");
            } else if (arg instanceof Double) {
                sb.append("d");
            } else if (arg instanceof String) {
                sb.append("s");
            } else if (arg instanceof Long) {
                sb.append("h");
            } else if (arg instanceof byte[]) {
                sb.append("b");
            } else if (arg instanceof Boolean) {
                sb.append((Boolean) arg ? "T" : "F");
            } else {
                // Default to string for unknown types
                sb.append("s");
            }
        }
        return sb.toString();
    }

    // Getters and setters
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public Object[] getArguments() { return arguments; }
    public void setArguments(Object[] arguments) {
        this.arguments = arguments;
        // Regenerate type tag string when arguments change
        this.types = generateTypeTagString(arguments);
    }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public String getTypes() { return types; }
    public void setTypes(String types) { this.types = types; }
}
