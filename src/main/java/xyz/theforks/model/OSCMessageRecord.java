package xyz.theforks.model;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OSCMessageRecord {
    private String address;
    private Object[] arguments;
    private long timestamp;

    public OSCMessageRecord() {
        // Default constructor for Jackson
    }

    public OSCMessageRecord(String address, Object[] arguments) {
        this.address = address;
        this.arguments = arguments;
        this.timestamp = Instant.now().toEpochMilli();
    }

    // Getters and setters
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public Object[] getArguments() { return arguments; }
    public void setArguments(Object[] arguments) { this.arguments = arguments; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
