package xyz.theforks.service;

import java.io.IOException;
import java.net.InetSocketAddress;

import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCSerializeException;
import com.illposed.osc.transport.OSCPortOut;
import com.illposed.osc.transport.OSCPortOutBuilder;

public class OSCOutputService {
    private OSCPortOut sender;
    private String outHost; 
    private int outPort;

    public OSCOutputService() {
        // No initialization needed
    }

    public void setOutHost(String outHost) {
        this.outHost = outHost;
    }

    public void setOutPort(int outPort) {
        this.outPort = outPort;
    }
    
    public void start() throws IOException {
        sender = new OSCPortOutBuilder()
                .setRemoteSocketAddress(new InetSocketAddress(outHost, outPort))
                .build();
    }


    public void send(OSCMessage message) throws IOException, OSCSerializeException {
        if (sender != null && message != null) {
            sender.send(message);
        }
    }

    public void stop() {
        if (sender != null) {
            try {
                sender.close();
            } catch (IOException e) {
                System.err.println("Error stopping output: " + e.getMessage());
            }
        }
    }
}
