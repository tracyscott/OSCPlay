package xyz.theforks;

import com.illposed.osc.OSCMessage;
import com.illposed.osc.transport.NetworkProtocol;
import com.illposed.osc.transport.OSCPortOut;
import com.illposed.osc.transport.OSCPortOutBuilder;

import java.net.InetSocketAddress;

/**
 * Simple test client to verify TCP OSC connectivity.
 * Run OSCPlay with --tcp first, then run this test.
 */
public class TcpClientTest {
    public static void main(String[] args) throws Exception {
        String host = "127.0.0.1";
        int port = 8000;

        System.out.println("Connecting to TCP OSC server at " + host + ":" + port);

        OSCPortOut sender = new OSCPortOutBuilder()
                .setRemoteSocketAddress(new InetSocketAddress(host, port))
                .setNetworkProtocol(NetworkProtocol.TCP)
                .build();

        System.out.println("Connected. Sending message...");

        OSCMessage msg = new OSCMessage("/test/message", java.util.Arrays.asList(0.5f));
        sender.send(msg);

        System.out.println("Message sent: /test/message 0.5");

        // Give time for data to be flushed before closing
        Thread.sleep(100);
        sender.close();
        System.out.println("Done.");
    }
}
