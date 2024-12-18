package xyz.theforks.service;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCSerializeException;
import com.illposed.osc.transport.OSCPortOut;
import com.illposed.osc.transport.OSCPortOutBuilder;

import xyz.theforks.rewrite.RewriteHandler;

public class OSCOutputService {
    private OSCPortOut sender;
    private String outHost; 
    private int outPort;
    private List<RewriteHandler> rewriteHandlers;

    public OSCOutputService() {
        this.rewriteHandlers = new ArrayList<>();
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

    public void registerRewriteHandler(RewriteHandler handler) {
        rewriteHandlers.add(handler);
    }

    public void unregisterRewriteHandler(RewriteHandler handler) {
        rewriteHandlers.remove(handler);
    }

    public void clearRewriteHandlers() {
        rewriteHandlers.clear();
    }

    public void send(OSCMessage message) throws IOException, OSCSerializeException {
        if (sender != null) {
            OSCMessage processedMessage = message;
            String address = message.getAddress();
            
            for (RewriteHandler handler : rewriteHandlers) {
                if (address.matches(handler.getAddressPattern())) {
                    //System.out.println("Processing matched message address: " + address + " with pattern: " + handler.getAddressPattern());
                    processedMessage = handler.process(processedMessage);
                    if (processedMessage == null) {
                        return; // Handler cancelled the message
                    }
                } else {
                    //System.out.println("No match for message address: " + address + " with pattern: " + handler.getAddressPattern());
                }
            }
            //System.out.println("Sending message: " + processedMessage.getAddress());
            sender.send(processedMessage);
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
