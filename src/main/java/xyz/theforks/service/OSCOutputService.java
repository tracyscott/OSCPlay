package xyz.theforks.service;

import java.io.IOException;
import java.net.InetSocketAddress;

import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCSerializeException;
import com.illposed.osc.transport.OSCPortOut;
import com.illposed.osc.transport.OSCPortOutBuilder;
import xyz.theforks.nodes.NodeChain;

public class OSCOutputService {
    private final String id;
    private OSCPortOut sender;
    private String outHost;
    private int outPort;
    private final NodeChain nodeChain;
    private boolean enabled = true;

    public OSCOutputService(String id) {
        this.id = id;
        this.nodeChain = new NodeChain(NodeChain.Context.PROXY);
    }

    public OSCOutputService() {
        this("default");
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
        if (!enabled || sender == null || message == null) {
            return;
        }

        // Apply node chain to message
        OSCMessage processedMessage = nodeChain.processMessage(message);
        if (processedMessage != null) {
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

    public String getId() {
        return id;
    }

    public String getOutHost() {
        return outHost;
    }

    public int getOutPort() {
        return outPort;
    }

    public NodeChain getNodeChain() {
        return nodeChain;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isStarted() {
        return sender != null;
    }
}
