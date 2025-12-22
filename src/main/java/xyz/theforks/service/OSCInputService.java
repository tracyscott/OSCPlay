package xyz.theforks.service;

import java.io.IOException;
import java.net.InetSocketAddress;

import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCPacket;
import com.illposed.osc.OSCSerializeException;
import com.illposed.osc.messageselector.OSCPatternAddressMessageSelector;
import com.illposed.osc.transport.NetworkProtocol;
import com.illposed.osc.transport.OSCPortIn;
import com.illposed.osc.transport.OSCPortInBuilder;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import xyz.theforks.transport.OSC10TCPTransport;

public class OSCInputService {

    public enum TcpFraming {
        NONE,       // Raw OSC packets (JavaOSC default)
        OSC_1_0,    // Size-prefix framing (4-byte length header)
        SLIP        // SLIP framing (OSC 1.1)
    }

    private OSCPortIn receiver;
    private OSC10TCPTransport osc10Transport;
    private Thread osc10ReceiverThread;
    private volatile boolean osc10Running;
    private String inHost = "127.0.0.1";
    private int inPort = 8000;
    private boolean useTcp = false;
    private TcpFraming tcpFraming = TcpFraming.OSC_1_0;  // Default to OSC 1.0 for TouchOSC compatibility
    private final IntegerProperty messageCount = new SimpleIntegerProperty(0);
    private MessageHandlerClass messageHandler;
    private boolean isStarted;

    public OSCInputService() {
        isStarted = false;
    }

    public String getInHost() {
        return inHost;
    }

    public int getInPort() {
        return inPort;
    }

    public void setInHost(String inHost) {
        this.inHost = inHost;
    }

    public void setInPort(int inPort) {
        this.inPort = inPort;
    }

    public boolean isUseTcp() {
        return useTcp;
    }

    public void setUseTcp(boolean useTcp) {
        this.useTcp = useTcp;
    }

    public TcpFraming getTcpFraming() {
        return tcpFraming;
    }

    public void setTcpFraming(TcpFraming tcpFraming) {
        this.tcpFraming = tcpFraming;
    }

    public void start() throws IOException {
        if (!isStarted) {
            InetSocketAddress localhostPort = new InetSocketAddress(inHost, inPort);

            if (useTcp && tcpFraming == TcpFraming.OSC_1_0) {
                // Use our custom OSC 1.0 transport with size-prefix framing
                startOSC10Receiver(localhostPort);
            } else {
                // Use standard JavaOSC receiver
                startStandardReceiver(localhostPort);
            }
            isStarted = true;
        }
    }

    private void startStandardReceiver(InetSocketAddress localhostPort) throws IOException {
        OSCPortInBuilder builder = new OSCPortInBuilder()
                .setPort(inPort)
                .setLocalSocketAddress(localhostPort);

        if (useTcp) {
            builder.setNetworkProtocol(NetworkProtocol.TCP);
            System.out.println("OSC input using TCP (raw) on " + inHost + ":" + inPort);
        } else {
            System.out.println("OSC input using UDP on " + inHost + ":" + inPort);
        }

        receiver = builder.build();

        receiver.getDispatcher().addListener(
                new OSCPatternAddressMessageSelector("//"),
                event -> {
                    System.out.println("OSC received: " + event.getMessage().getAddress());
                    handleMessage(event.getMessage());
                }
        );
        receiver.startListening();
    }

    private void startOSC10Receiver(InetSocketAddress localhostPort) throws IOException {
        System.out.println("OSC input using TCP with OSC 1.0 framing on " + inHost + ":" + inPort);

        osc10Transport = new OSC10TCPTransport(localhostPort, null);
        osc10Transport.connect();
        osc10Running = true;

        osc10ReceiverThread = new Thread(() -> {
            while (osc10Running) {
                try {
                    OSCPacket packet = osc10Transport.receive();
                    if (packet instanceof OSCMessage) {
                        OSCMessage msg = (OSCMessage) packet;
                        System.out.println("OSC received: " + msg.getAddress());
                        handleMessage(msg);
                    }
                } catch (Exception e) {
                    if (osc10Running) {
                        System.err.println("OSC10 receive error: " + e.getMessage());
                    }
                }
            }
        }, "OSC10-Receiver");
        osc10ReceiverThread.setDaemon(true);
        osc10ReceiverThread.start();
    }

    public void stop() {
        if (isStarted) {
            // Stop standard receiver
            if (receiver != null) {
                try {
                    receiver.close();
                } catch (IOException e) {
                    System.err.println("Error stopping input: " + e.getMessage());
                }
                receiver = null;
            }

            // Stop OSC 1.0 receiver
            if (osc10Running) {
                osc10Running = false;
                if (osc10Transport != null) {
                    try {
                        osc10Transport.close();
                    } catch (IOException e) {
                        System.err.println("Error stopping OSC10 transport: " + e.getMessage());
                    }
                    osc10Transport = null;
                }
                if (osc10ReceiverThread != null) {
                    osc10ReceiverThread.interrupt();
                    osc10ReceiverThread = null;
                }
            }

            isStarted = false;
        }
    }

    /**
     * Each pad can have a custom message filter so that only messages matching
     * the address are stored in the pad.
     */
    public void addMessageHandlerWithFilter(String filter, MessageHandler messageHandler) {
        receiver.getDispatcher().addListener(
                new OSCPatternAddressMessageSelector(filter),
                event -> {
                    messageHandler.handleMessage(event.getMessage());
                }
        );
    }

    // Add a listener interface so that classes using this class can be notified
    // of incoming messages.  
    public void setMessageHandler(MessageHandlerClass messageHandler) {
        this.messageHandler = messageHandler;
    }

    private void handleMessage(OSCMessage oscMessage) {
        try {
            if (messageHandler != null) {
                messageHandler.handleMessage(oscMessage);
            }
        } catch (IOException | OSCSerializeException e) {
            System.err.println("Error handling message: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
